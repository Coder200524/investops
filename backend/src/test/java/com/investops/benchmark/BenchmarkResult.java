package com.investops.benchmark;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.List;
import java.util.Map;

/**
 * Encapsulates measured performance metrics for a specific benchmark run.
 */
@Getter
@Builder
@ToString
public class BenchmarkResult {
    private final String benchmarkName;
    private final int targetTradesPerSource;
    private final int tradesInSourceA;
    private final int tradesInSourceB;
    private final int totalInputTrades;
    private final int totalResults;
    private final int matchedTrades;
    private final int totalDiscrepancies;
    private final Map<String, Integer> discrepanciesByType;

    private final int warmupIterations;
    private final int measuredIterations;
    private final List<Long> iterationTimesMs;
    private final double medianTimeMs;
    private final double minTimeMs;
    private final double maxTimeMs;
    private final double throughputTradesPerSec;

    private final long memoryUsedBeforeMb;
    private final long memoryUsedAfterMb;
    private final long memoryDeltaMb;

    public String toSummaryString() {
        return String.format(
                "[%s | %s trades/source] Input: %d (A=%d, B=%d) | Results: %d (Matched: %d, Breaks: %d) | " +
                "Median Time: %.2f ms | Throughput: %,.0f trades/sec | Heap Delta: %d MB",
                benchmarkName,
                String.format("%,d", targetTradesPerSource),
                totalInputTrades,
                tradesInSourceA,
                tradesInSourceB,
                totalResults,
                matchedTrades,
                totalDiscrepancies,
                medianTimeMs,
                throughputTradesPerSec,
                memoryDeltaMb
        );
    }
}
