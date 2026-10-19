package com.investops.benchmark;

import com.investops.exceptionmgmt.repository.ExceptionAuditLogRepository;
import com.investops.exceptionmgmt.repository.ExceptionRepository;
import com.investops.exceptionmgmt.service.ExceptionService;
import com.investops.reconciliation.entity.*;
import com.investops.reconciliation.repository.ReconciliationResultRepository;
import com.investops.reconciliation.repository.ReconciliationRunRepository;
import com.investops.reconciliation.service.MatchingEngine;
import com.investops.trade.entity.ImportBatch;
import com.investops.trade.entity.SourceSystem;
import com.investops.trade.entity.TradeRecord;
import com.investops.trade.repository.ImportBatchRepository;
import com.investops.trade.repository.TradeRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end database reconciliation performance benchmark.
 * Measures database load time, engine execution time, result persistence time,
 * and exception generation time across varying dataset sizes.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("End-to-End Reconciliation Performance Benchmarks")
public class EndToEndReconciliationBenchmarkTest {

    @Autowired
    private TradeRecordRepository tradeRecordRepository;

    @Autowired
    private ImportBatchRepository importBatchRepository;

    @Autowired
    private ReconciliationRunRepository runRepository;

    @Autowired
    private ReconciliationResultRepository resultRepository;

    @Autowired
    private ExceptionRepository exceptionRepository;

    @Autowired
    private ExceptionAuditLogRepository auditLogRepository;

    @Autowired
    private MatchingEngine matchingEngine;

    @Autowired
    private ExceptionService exceptionService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private ImportBatch batchA;
    private ImportBatch batchB;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        exceptionRepository.deleteAll();
        resultRepository.deleteAll();
        runRepository.deleteAll();
        tradeRecordRepository.deleteAll();
        importBatchRepository.deleteAll();

        batchA = importBatchRepository.save(ImportBatch.builder()
                .filename("benchmark_source_a.csv")
                .fileHash("hash_benchmark_source_a_1234567890")
                .sourceSystem(SourceSystem.SYSTEM_A)
                .build());

        batchB = importBatchRepository.save(ImportBatch.builder()
                .filename("benchmark_source_b.csv")
                .fileHash("hash_benchmark_source_b_1234567890")
                .sourceSystem(SourceSystem.SYSTEM_B)
                .build());
    }

    @Test
    @DisplayName("Benchmark End-to-End: 1,000 trades per source")
    void benchmarkEndToEnd_1k() {
        runEndToEndBenchmark(1_000, 42L);
    }

    @Test
    @DisplayName("Benchmark End-to-End: 10,000 trades per source")
    void benchmarkEndToEnd_10k() {
        runEndToEndBenchmark(10_000, 42L);
    }

    private void runEndToEndBenchmark(int countPerSource, long seed) {
        System.out.println(String.format("\n================================================================================"));
        System.out.println(String.format("  End-to-End Database Benchmark: %,d trades per source", countPerSource));
        System.out.println("================================================================================");

        // 1. Generate Dataset
        SyntheticTradeGenerator.SyntheticDataset dataset =
                SyntheticTradeGenerator.generate(SyntheticTradeGenerator.DatasetConfig.of(countPerSource, seed));

        LocalDate tradeDateFrom = LocalDate.of(2026, 10, 1);
        LocalDate tradeDateTo = LocalDate.of(2026, 10, 31);

        // Assign persisted batches
        List<TradeRecord> tradesA = dataset.getTradesA();
        tradesA.forEach(t -> t.setImportBatch(batchA));
        List<TradeRecord> tradesB = dataset.getTradesB();
        tradesB.forEach(t -> t.setImportBatch(batchB));

        // 2. Persist trades in batches of 1,000 to avoid huge single transaction overhead
        long insertStart = System.currentTimeMillis();
        saveInChunks(tradesA, 1_000);
        saveInChunks(tradesB, 1_000);
        long insertTimeMs = System.currentTimeMillis() - insertStart;
        System.out.println(String.format("Phase 1 [Data Ingestion]: Saved %,d trades into DB in %,d ms",
                tradesA.size() + tradesB.size(), insertTimeMs));

        // 3. Create ReconciliationRun
        ReconciliationRun run = runRepository.save(ReconciliationRun.builder()
                .sourceA(SourceSystem.SYSTEM_A)
                .sourceB(SourceSystem.SYSTEM_B)
                .tradeDateFrom(tradeDateFrom)
                .tradeDateTo(tradeDateTo)
                .priceTolerance(new BigDecimal("0.01000000"))
                .status(RunStatus.RUNNING)
                .startedAt(Instant.now())
                .build());

        // 4. Measure End-to-End Reconciliation Phases
        long totalStart = System.currentTimeMillis();

        // Phase A: Database Query (Fetch candidate trades)
        long fetchStart = System.currentTimeMillis();
        List<TradeRecord> fetchedA = tradeRecordRepository.findBySourceSystemAndTradeDateBetween(
                SourceSystem.SYSTEM_A, tradeDateFrom, tradeDateTo);
        List<TradeRecord> fetchedB = tradeRecordRepository.findBySourceSystemAndTradeDateBetween(
                SourceSystem.SYSTEM_B, tradeDateFrom, tradeDateTo);
        long fetchTimeMs = System.currentTimeMillis() - fetchStart;

        assertThat(fetchedA).hasSize(tradesA.size());
        assertThat(fetchedB).hasSize(tradesB.size());

        // Phase B: Pure In-Memory Matching Engine Execution
        long matchStart = System.currentTimeMillis();
        List<ReconciliationResult> results = matchingEngine.reconcile(
                run, fetchedA, fetchedB, run.getPriceTolerance());
        long matchTimeMs = System.currentTimeMillis() - matchStart;

        // Phase C: Database Batch Persistence of Results
        long persistStart = System.currentTimeMillis();
        List<ReconciliationResult> savedResults = saveResultsInChunks(results, 1_000);
        long persistTimeMs = System.currentTimeMillis() - persistStart;

        // Phase D: Exception Generation and Persistence for Breaks
        long exceptionStart = System.currentTimeMillis();
        exceptionService.createExceptionsForResults(savedResults);
        long exceptionTimeMs = System.currentTimeMillis() - exceptionStart;

        // Phase E: Update Run Status & Summary Counts
        long totalEndToEndMs = System.currentTimeMillis() - totalStart;

        int matchedCount = (int) results.stream().filter(r -> r.getMatchStatus() == MatchStatus.MATCHED).count();
        int breakCount = results.size() - matchedCount;

        run.setTotalCompared(results.size());
        run.setMatchedCount(matchedCount);
        run.setBreakCount(breakCount);
        run.setStatus(RunStatus.COMPLETED);
        run.setCompletedAt(Instant.now());
        runRepository.save(run);

        int totalInputTrades = fetchedA.size() + fetchedB.size();
        double engineThroughput = (matchTimeMs > 0) ? (totalInputTrades / (matchTimeMs / 1000.0)) : 0;
        double endToEndThroughput = (totalEndToEndMs > 0) ? (totalInputTrades / (totalEndToEndMs / 1000.0)) : 0;

        System.out.println("\n---------------- Performance Breakdown ----------------");
        System.out.println(String.format("  Total Input Trades:           %,d (Source A: %,d, Source B: %,d)", totalInputTrades, fetchedA.size(), fetchedB.size()));
        System.out.println(String.format("  Reconciliation Results:       %,d (Matched: %,d, Breaks: %,d)", results.size(), matchedCount, breakCount));
        System.out.println(String.format("  Exceptions Created:           %,d", exceptionRepository.count()));
        System.out.println(String.format("  [Phase A] DB Fetch Time:       %,d ms", fetchTimeMs));
        System.out.println(String.format("  [Phase B] Engine Match Time:   %,d ms  (%,.0f trades/sec)", matchTimeMs, engineThroughput));
        System.out.println(String.format("  [Phase C] Results DB Persist:  %,d ms", persistTimeMs));
        System.out.println(String.format("  [Phase D] Exception Creation:  %,d ms", exceptionTimeMs));
        System.out.println(String.format("  TOTAL End-to-End Time:        %,d ms", totalEndToEndMs));
        System.out.println(String.format("  TOTAL End-to-End Throughput:  %,.0f trades/sec", endToEndThroughput));
        System.out.println("-------------------------------------------------------\n");

        // Verification assertions
        assertThat(run.getStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(run.getTotalCompared()).isEqualTo(results.size());
        assertThat(run.getMatchedCount()).isEqualTo(matchedCount);
        assertThat(run.getBreakCount()).isEqualTo(breakCount);
    }

    private void saveInChunks(List<TradeRecord> trades, int chunkSize) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        for (int i = 0; i < trades.size(); i += chunkSize) {
            final int from = i;
            final int to = Math.min(i + chunkSize, trades.size());
            tx.execute(status -> {
                tradeRecordRepository.saveAll(trades.subList(from, to));
                return null;
            });
        }
    }

    private List<ReconciliationResult> saveResultsInChunks(List<ReconciliationResult> results, int chunkSize) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        List<ReconciliationResult> allSaved = new ArrayList<>(results.size());
        for (int i = 0; i < results.size(); i += chunkSize) {
            final int from = i;
            final int to = Math.min(i + chunkSize, results.size());
            List<ReconciliationResult> saved = tx.execute(status ->
                    resultRepository.saveAll(results.subList(from, to))
            );
            if (saved != null) {
                allSaved.addAll(saved);
            }
        }
        return allSaved;
    }
}
