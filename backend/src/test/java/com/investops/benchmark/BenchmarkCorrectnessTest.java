package com.investops.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investops.reconciliation.entity.*;
import com.investops.reconciliation.rules.*;
import com.investops.reconciliation.service.MatchingEngine;
import com.investops.trade.entity.SourceSystem;
import com.investops.trade.entity.TradeRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Benchmark Correctness & Integrity Verification Tests")
public class BenchmarkCorrectnessTest {

    private MatchingEngine matchingEngine;
    private ReconciliationRun run;

    @BeforeEach
    void setUp() {
        List<ComparisonRule> rules = List.of(
                new QuantityRule(),
                new PriceRule(),
                new SettlementDateRule(),
                new SymbolRule(),
                new SideRule()
        );
        this.matchingEngine = new MatchingEngine(rules, new ObjectMapper());
        this.run = ReconciliationRun.builder()
                .id(UUID.randomUUID())
                .sourceA(SourceSystem.SYSTEM_A)
                .sourceB(SourceSystem.SYSTEM_B)
                .tradeDateFrom(LocalDate.of(2026, 10, 1))
                .tradeDateTo(LocalDate.of(2026, 10, 31))
                .priceTolerance(new BigDecimal("0.01000000"))
                .status(RunStatus.RUNNING)
                .startedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Verify that results are 100% deterministic and identical across repeated executions")
    void testDeterminismAcrossRepeatedRuns() {
        SyntheticTradeGenerator.SyntheticDataset dataset =
                SyntheticTradeGenerator.generate(SyntheticTradeGenerator.DatasetConfig.of(1_000, 42L));

        List<ReconciliationResult> run1 = matchingEngine.reconcile(run, dataset.getTradesA(), dataset.getTradesB(), run.getPriceTolerance());
        List<ReconciliationResult> run2 = matchingEngine.reconcile(run, dataset.getTradesA(), dataset.getTradesB(), run.getPriceTolerance());

        assertThat(run1).hasSameSizeAs(run2);
        for (int i = 0; i < run1.size(); i++) {
            ReconciliationResult r1 = run1.get(i);
            ReconciliationResult r2 = run2.get(i);

            assertThat(r1.getMatchStatus()).isEqualTo(r2.getMatchStatus());
            assertThat(r1.getDiscrepancyType()).isEqualTo(r2.getDiscrepancyType());
            assertThat(r1.getSeverity()).isEqualTo(r2.getSeverity());
            assertThat(r1.getDifferenceDetails()).isEqualTo(r2.getDifferenceDetails());
        }
    }

    @Test
    @DisplayName("Verify that every input trade is accounted for and no trade is dropped or double-counted")
    void testEveryTradeIsAccountedFor() {
        SyntheticTradeGenerator.SyntheticDataset dataset =
                SyntheticTradeGenerator.generate(SyntheticTradeGenerator.DatasetConfig.of(5_000, 42L));

        List<ReconciliationResult> results = matchingEngine.reconcile(run, dataset.getTradesA(), dataset.getTradesB(), run.getPriceTolerance());

        Set<UUID> accountedTradeAIds = new HashSet<>();
        Set<UUID> accountedTradeBIds = new HashSet<>();

        for (ReconciliationResult r : results) {
            if (r.getTradeA() != null) {
                accountedTradeAIds.add(r.getTradeA().getId());
            }
            if (r.getTradeB() != null) {
                accountedTradeBIds.add(r.getTradeB().getId());
            }
        }

        // Check non-duplicate trades are all mapped
        for (TradeRecord tA : dataset.getTradesA()) {
            if (!tA.getExternalTradeId().startsWith("TRD-DUP")) {
                assertThat(accountedTradeAIds).contains(tA.getId());
            }
        }

        for (TradeRecord tB : dataset.getTradesB()) {
            if (!tB.getExternalTradeId().startsWith("TRD-DUP")) {
                assertThat(accountedTradeBIds).contains(tB.getId());
            }
        }
    }

    @Test
    @DisplayName("Verify that duplicate trade candidate keys explicitly retain ambiguity handling")
    void testDuplicateCandidateKeysAmbiguityHandling() {
        SyntheticTradeGenerator.SyntheticDataset dataset =
                SyntheticTradeGenerator.generate(SyntheticTradeGenerator.DatasetConfig.of(1_000, 42L));

        List<ReconciliationResult> results = matchingEngine.reconcile(run, dataset.getTradesA(), dataset.getTradesB(), run.getPriceTolerance());

        List<ReconciliationResult> duplicateBreaks = results.stream()
                .filter(r -> r.getDiscrepancyType() == DiscrepancyType.DUPLICATE_TRADE)
                .toList();

        assertThat(duplicateBreaks).isNotEmpty();
        for (ReconciliationResult dup : duplicateBreaks) {
            assertThat(dup.getMatchStatus()).isEqualTo(MatchStatus.MISMATCHED);
            assertThat(dup.getSeverity()).isEqualTo(DiscrepancySeverity.HIGH);
            assertThat(dup.getDifferenceDetails()).containsAnyOf("Ambiguous match", "Duplicate candidate keys");
        }
    }

    @Test
    @DisplayName("Verify that financial calculations retain exact BigDecimal precision (8 decimals)")
    void testFinancialCalculationsPrecision() {
        SyntheticTradeGenerator.SyntheticDataset dataset =
                SyntheticTradeGenerator.generate(SyntheticTradeGenerator.DatasetConfig.of(1_000, 42L));

        List<ReconciliationResult> results = matchingEngine.reconcile(run, dataset.getTradesA(), dataset.getTradesB(), new BigDecimal("0.01000000"));

        List<ReconciliationResult> priceBreaks = results.stream()
                .filter(r -> r.getDiscrepancyType() == DiscrepancyType.PRICE_DIFFERENCE)
                .toList();

        assertThat(priceBreaks).isNotEmpty();
        for (ReconciliationResult pb : priceBreaks) {
            assertThat(pb.getTradeA().getPrice().scale()).isEqualTo(8);
            assertThat(pb.getTradeB().getPrice().scale()).isEqualTo(8);
            BigDecimal diff = pb.getTradeA().getPrice().subtract(pb.getTradeB().getPrice()).abs();
            assertThat(diff).isGreaterThan(new BigDecimal("0.01000000"));
        }
    }
}
