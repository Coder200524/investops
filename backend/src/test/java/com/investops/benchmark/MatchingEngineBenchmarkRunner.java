package com.investops.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investops.reconciliation.entity.*;
import com.investops.reconciliation.rules.*;
import com.investops.reconciliation.service.MatchingEngine;
import com.investops.trade.entity.SourceSystem;
import com.investops.trade.entity.TradeRecord;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Benchmark runner measuring pure matching engine execution time, throughput, and memory usage.
 * Distinguishes engine-only execution time from database processing time.
 */
public class MatchingEngineBenchmarkRunner {

    private final MatchingEngine matchingEngine;
    private final ReconciliationRun dummyRun;

    public MatchingEngineBenchmarkRunner() {
        List<ComparisonRule> rules = List.of(
                new QuantityRule(),
                new PriceRule(),
                new SettlementDateRule(),
                new SymbolRule(),
                new SideRule()
        );
        this.matchingEngine = new MatchingEngine(rules, new ObjectMapper());
        this.dummyRun = ReconciliationRun.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .sourceA(SourceSystem.SYSTEM_A)
                .sourceB(SourceSystem.SYSTEM_B)
                .tradeDateFrom(LocalDate.of(2026, 10, 1))
                .tradeDateTo(LocalDate.of(2026, 10, 31))
                .priceTolerance(new BigDecimal("0.01000000"))
                .status(RunStatus.RUNNING)
                .startedAt(Instant.now())
                .build();
    }

    /**
     * Warms up the JVM JIT compiler to ensure realistic steady-state measurements.
     */
    public void warmup(int warmupIterations, int warmupDatasetSize) {
        System.out.println(String.format("Starting JVM warm-up (%d iterations with %,d trades/source)...", warmupIterations, warmupDatasetSize));
        SyntheticTradeGenerator.SyntheticDataset warmupData =
                SyntheticTradeGenerator.generate(SyntheticTradeGenerator.DatasetConfig.of(warmupDatasetSize, 999L));

        for (int i = 0; i < warmupIterations; i++) {
            matchingEngine.reconcile(dummyRun, warmupData.getTradesA(), warmupData.getTradesB(), dummyRun.getPriceTolerance());
        }
        System.gc();
        System.out.println("JVM warm-up complete.\n");
    }

    /**
     * Runs benchmark for a specific target size.
     */
    public BenchmarkResult runBenchmark(int targetTradesPerSource, int measuredIterations, long seed) {
        SyntheticTradeGenerator.SyntheticDataset dataset =
                SyntheticTradeGenerator.generate(SyntheticTradeGenerator.DatasetConfig.of(targetTradesPerSource, seed));

        List<TradeRecord> tradesA = dataset.getTradesA();
        List<TradeRecord> tradesB = dataset.getTradesB();
        int totalInputTrades = tradesA.size() + tradesB.size();

        // Trigger GC before measurement to get clean baseline memory reading
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}

        long memoryBefore = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);

        List<Long> iterationTimesMs = new ArrayList<>();
        List<ReconciliationResult> lastResults = null;

        for (int iter = 0; iter < measuredIterations; iter++) {
            long startNanos = System.nanoTime();
            lastResults = matchingEngine.reconcile(dummyRun, tradesA, tradesB, dummyRun.getPriceTolerance());
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            iterationTimesMs.add(durationMs);
        }

        long memoryAfter = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
        long memoryDelta = Math.max(0, memoryAfter - memoryBefore);

        // Compute median, min, max
        List<Long> sortedTimes = new ArrayList<>(iterationTimesMs);
        Collections.sort(sortedTimes);
        double medianTimeMs = sortedTimes.get(sortedTimes.size() / 2);
        double minTimeMs = sortedTimes.get(0);
        double maxTimeMs = sortedTimes.get(sortedTimes.size() - 1);

        double throughput = (medianTimeMs > 0)
                ? (totalInputTrades / (medianTimeMs / 1000.0))
                : 0.0;

        // Verify result counts and classifications
        int matchedTrades = 0;
        int totalDiscrepancies = 0;
        Map<String, Integer> discrepanciesByType = new TreeMap<>();

        if (lastResults != null) {
            for (ReconciliationResult r : lastResults) {
                if (r.getMatchStatus() == MatchStatus.MATCHED) {
                    matchedTrades++;
                } else {
                    totalDiscrepancies++;
                    String typeName = r.getDiscrepancyType() != null ? r.getDiscrepancyType().name() : "UNKNOWN";
                    discrepanciesByType.put(typeName, discrepanciesByType.getOrDefault(typeName, 0) + 1);
                }
            }
        }

        return BenchmarkResult.builder()
                .benchmarkName("MatchingEngine (In-Memory)")
                .targetTradesPerSource(targetTradesPerSource)
                .tradesInSourceA(tradesA.size())
                .tradesInSourceB(tradesB.size())
                .totalInputTrades(totalInputTrades)
                .totalResults(lastResults != null ? lastResults.size() : 0)
                .matchedTrades(matchedTrades)
                .totalDiscrepancies(totalDiscrepancies)
                .discrepanciesByType(discrepanciesByType)
                .warmupIterations(3)
                .measuredIterations(measuredIterations)
                .iterationTimesMs(iterationTimesMs)
                .medianTimeMs(medianTimeMs)
                .minTimeMs(minTimeMs)
                .maxTimeMs(maxTimeMs)
                .throughputTradesPerSec(throughput)
                .memoryUsedBeforeMb(memoryBefore)
                .memoryUsedAfterMb(memoryAfter)
                .memoryDeltaMb(memoryDelta)
                .build();
    }

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("  InvestOps Phase 6A — Performance Benchmarking: Matching Engine (In-Memory)");
        System.out.println("================================================================================\n");

        MatchingEngineBenchmarkRunner runner = new MatchingEngineBenchmarkRunner();

        // 1. Warm-up JVM JIT compiler
        runner.warmup(4, 5_000);

        int[] datasetSizes = {1_000, 10_000, 50_000, 100_000};
        List<BenchmarkResult> results = new ArrayList<>();

        for (int size : datasetSizes) {
            System.out.println(String.format("Executing benchmark for %,d trades per source (5 iterations)...", size));
            BenchmarkResult result = runner.runBenchmark(size, 5, 42L);
            results.add(result);
            System.out.println("Result: " + result.toSummaryString());
            System.out.println("  Iteration times (ms): " + result.getIterationTimesMs());
            System.out.println("  Discrepancies: " + result.getDiscrepanciesByType());
            System.out.println();
        }

        // Print final summary table
        System.out.println("============================================================================================================================");
        System.out.println(String.format("%-12s | %-12s | %-12s | %-10s | %-10s | %-14s | %-18s | %-12s",
                "Size/Source", "Total Input", "Results", "Matched", "Breaks", "Median Time", "Throughput", "Heap Delta"));
        System.out.println("----------------------------------------------------------------------------------------------------------------------------");
        for (BenchmarkResult r : results) {
            System.out.println(String.format("%-12s | %-12s | %-12s | %-10s | %-10s | %-11.2f ms | %,14.0f tr/s | %-9d MB",
                    String.format("%,d", r.getTargetTradesPerSource()),
                    String.format("%,d", r.getTotalInputTrades()),
                    String.format("%,d", r.getTotalResults()),
                    String.format("%,d", r.getMatchedTrades()),
                    String.format("%,d", r.getTotalDiscrepancies()),
                    r.getMedianTimeMs(),
                    r.getThroughputTradesPerSec(),
                    r.getMemoryDeltaMb()));
        }
        System.out.println("============================================================================================================================\n");
    }
}
