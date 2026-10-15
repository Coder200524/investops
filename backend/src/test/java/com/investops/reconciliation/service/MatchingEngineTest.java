package com.investops.reconciliation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investops.reconciliation.entity.*;
import com.investops.reconciliation.rules.*;
import com.investops.trade.entity.ImportBatch;
import com.investops.trade.entity.SourceSystem;
import com.investops.trade.entity.TradeRecord;
import com.investops.trade.entity.TradeSide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MatchingEngine Tests")
class MatchingEngineTest {

    private MatchingEngine matchingEngine;
    private ReconciliationRun run;
    private ImportBatch batchA;
    private ImportBatch batchB;

    @BeforeEach
    void setUp() {
        List<ComparisonRule> rules = List.of(
                new QuantityRule(),
                new PriceRule(),
                new SettlementDateRule(),
                new SymbolRule(),
                new SideRule()
        );
        matchingEngine = new MatchingEngine(rules, new ObjectMapper());

        run = ReconciliationRun.builder()
                .id(UUID.randomUUID())
                .tradeDateFrom(LocalDate.of(2026, 10, 1))
                .tradeDateTo(LocalDate.of(2026, 10, 15))
                .sourceA(SourceSystem.SYSTEM_A)
                .sourceB(SourceSystem.SYSTEM_B)
                .priceTolerance(new BigDecimal("0.01000000"))
                .status(RunStatus.RUNNING)
                .startedAt(Instant.now())
                .build();

        batchA = ImportBatch.builder().id(UUID.randomUUID()).build();
        batchB = ImportBatch.builder().id(UUID.randomUUID()).build();
    }

    private TradeRecord buildTrade(SourceSystem source, String externalId, String symbol,
                                   TradeSide side, String qty, String price,
                                   LocalDate tradeDate, LocalDate settlDate) {
        return TradeRecord.builder()
                .id(UUID.randomUUID())
                .externalTradeId(externalId)
                .sourceSystem(source)
                .symbol(symbol)
                .side(side)
                .quantity(new BigDecimal(qty))
                .price(new BigDecimal(price))
                .counterparty("JPMORGAN")
                .tradeDate(tradeDate)
                .settlementDate(settlDate)
                .importBatch(source == SourceSystem.SYSTEM_A ? batchA : batchB)
                .build();
    }

    @Nested
    @DisplayName("Exact Match Tests")
    class ExactMatchTests {

        @Test
        @DisplayName("Identical trades across SYSTEM_A and SYSTEM_B should be MATCHED with no breaks")
        void testExactMatch() {
            LocalDate tradeDate = LocalDate.of(2026, 10, 5);
            LocalDate settlDate = LocalDate.of(2026, 10, 7);

            TradeRecord tradeA = buildTrade(SourceSystem.SYSTEM_A, "TRD-101", "AAPL", TradeSide.BUY,
                    "100.00000000", "150.25000000", tradeDate, settlDate);
            TradeRecord tradeB = buildTrade(SourceSystem.SYSTEM_B, "TRD-101", "AAPL", TradeSide.BUY,
                    "100.00000000", "150.25000000", tradeDate, settlDate);

            List<ReconciliationResult> results = matchingEngine.reconcile(
                    run, List.of(tradeA), List.of(tradeB), new BigDecimal("0.01000000"));

            assertThat(results).hasSize(1);
            ReconciliationResult result = results.get(0);
            assertThat(result.getMatchStatus()).isEqualTo(MatchStatus.MATCHED);
            assertThat(result.getDiscrepancyType()).isEqualTo(DiscrepancyType.NONE);
            assertThat(result.getSeverity()).isNull();
            assertThat(result.getTradeA()).isEqualTo(tradeA);
            assertThat(result.getTradeB()).isEqualTo(tradeB);
        }

        @Test
        @DisplayName("Trades with normalized key variations (e.g. TRD-00042 vs TRD-42) should MATCH")
        void testKeyNormalizationMatch() {
            LocalDate tradeDate = LocalDate.of(2026, 10, 5);
            LocalDate settlDate = LocalDate.of(2026, 10, 7);

            TradeRecord tradeA = buildTrade(SourceSystem.SYSTEM_A, "TRD-00042", "MSFT", TradeSide.SELL,
                    "50.00000000", "320.00000000", tradeDate, settlDate);
            TradeRecord tradeB = buildTrade(SourceSystem.SYSTEM_B, "trd-42", "MSFT", TradeSide.SELL,
                    "50.00000000", "320.00000000", tradeDate, settlDate);

            List<ReconciliationResult> results = matchingEngine.reconcile(
                    run, List.of(tradeA), List.of(tradeB), new BigDecimal("0.01000000"));

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getMatchStatus()).isEqualTo(MatchStatus.MATCHED);
        }
    }

    @Nested
    @DisplayName("Missing Trades Tests")
    class MissingTradesTests {

        @Test
        @DisplayName("Trade present only in Source A should be classified as MISSING_IN_SOURCE_B with HIGH severity")
        void testMissingInSourceB() {
            LocalDate date = LocalDate.of(2026, 10, 5);
            TradeRecord tradeA = buildTrade(SourceSystem.SYSTEM_A, "TRD-201", "GOOGL", TradeSide.BUY,
                    "10.00000000", "140.00000000", date, date.plusDays(2));

            List<ReconciliationResult> results = matchingEngine.reconcile(
                    run, List.of(tradeA), List.of(), new BigDecimal("0.01000000"));

            assertThat(results).hasSize(1);
            ReconciliationResult r = results.get(0);
            assertThat(r.getMatchStatus()).isEqualTo(MatchStatus.MISSING);
            assertThat(r.getDiscrepancyType()).isEqualTo(DiscrepancyType.MISSING_IN_SOURCE_B);
            assertThat(r.getSeverity()).isEqualTo(DiscrepancySeverity.HIGH);
            assertThat(r.getTradeA()).isNotNull();
            assertThat(r.getTradeB()).isNull();
        }

        @Test
        @DisplayName("Trade present only in Source B should be classified as MISSING_IN_SOURCE_A with HIGH severity")
        void testMissingInSourceA() {
            LocalDate date = LocalDate.of(2026, 10, 5);
            TradeRecord tradeB = buildTrade(SourceSystem.SYSTEM_B, "TRD-202", "AMZN", TradeSide.SELL,
                    "20.00000000", "180.00000000", date, date.plusDays(2));

            List<ReconciliationResult> results = matchingEngine.reconcile(
                    run, List.of(), List.of(tradeB), new BigDecimal("0.01000000"));

            assertThat(results).hasSize(1);
            ReconciliationResult r = results.get(0);
            assertThat(r.getMatchStatus()).isEqualTo(MatchStatus.MISSING);
            assertThat(r.getDiscrepancyType()).isEqualTo(DiscrepancyType.MISSING_IN_SOURCE_A);
            assertThat(r.getSeverity()).isEqualTo(DiscrepancySeverity.HIGH);
            assertThat(r.getTradeA()).isNull();
            assertThat(r.getTradeB()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Quantity Discrepancy Tests")
    class QuantityDiscrepancyTests {

        @Test
        @DisplayName("Quantity mismatch <= 10% difference should be HIGH severity")
        void testQuantityMismatchHighSeverity() {
            LocalDate date = LocalDate.of(2026, 10, 5);
            TradeRecord tradeA = buildTrade(SourceSystem.SYSTEM_A, "TRD-301", "NVDA", TradeSide.BUY,
                    "100.00000000", "450.00000000", date, date.plusDays(2));
            TradeRecord tradeB = buildTrade(SourceSystem.SYSTEM_B, "TRD-301", "NVDA", TradeSide.BUY,
                    "95.00000000", "450.00000000", date, date.plusDays(2));

            List<ReconciliationResult> results = matchingEngine.reconcile(
                    run, List.of(tradeA), List.of(tradeB), new BigDecimal("0.01000000"));

            assertThat(results).hasSize(1);
            ReconciliationResult r = results.get(0);
            assertThat(r.getMatchStatus()).isEqualTo(MatchStatus.MISMATCHED);
            assertThat(r.getDiscrepancyType()).isEqualTo(DiscrepancyType.QUANTITY_MISMATCH);
            assertThat(r.getSeverity()).isEqualTo(DiscrepancySeverity.HIGH);
        }

        @Test
        @DisplayName("Quantity mismatch > 10% difference should be CRITICAL severity")
        void testQuantityMismatchCriticalSeverity() {
            LocalDate date = LocalDate.of(2026, 10, 5);
            TradeRecord tradeA = buildTrade(SourceSystem.SYSTEM_A, "TRD-302", "NVDA", TradeSide.BUY,
                    "100.00000000", "450.00000000", date, date.plusDays(2));
            TradeRecord tradeB = buildTrade(SourceSystem.SYSTEM_B, "TRD-302", "NVDA", TradeSide.BUY,
                    "80.00000000", "450.00000000", date, date.plusDays(2));

            List<ReconciliationResult> results = matchingEngine.reconcile(
                    run, List.of(tradeA), List.of(tradeB), new BigDecimal("0.01000000"));

            assertThat(results).hasSize(1);
            ReconciliationResult r = results.get(0);
            assertThat(r.getMatchStatus()).isEqualTo(MatchStatus.MISMATCHED);
            assertThat(r.getDiscrepancyType()).isEqualTo(DiscrepancyType.QUANTITY_MISMATCH);
            assertThat(r.getSeverity()).isEqualTo(DiscrepancySeverity.CRITICAL);
        }
    }

    @Nested
    @DisplayName("Price Tolerance Boundary Tests")
    class PriceToleranceTests {

        @Test
        @DisplayName("Price difference within tolerance (diff <= tol) should be MATCHED")
        void testPriceWithinTolerance() {
            LocalDate date = LocalDate.of(2026, 10, 5);
            // diff is 0.005, tolerance is 0.01
            TradeRecord tradeA = buildTrade(SourceSystem.SYSTEM_A, "TRD-401", "META", TradeSide.BUY,
                    "100.00000000", "300.00000000", date, date.plusDays(2));
            TradeRecord tradeB = buildTrade(SourceSystem.SYSTEM_B, "TRD-401", "META", TradeSide.BUY,
                    "100.00000000", "300.00500000", date, date.plusDays(2));

            List<ReconciliationResult> results = matchingEngine.reconcile(
                    run, List.of(tradeA), List.of(tradeB), new BigDecimal("0.01000000"));

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getMatchStatus()).isEqualTo(MatchStatus.MATCHED);
        }

        @Test
        @DisplayName("Price difference exactly at tolerance boundary should be MATCHED")
        void testPriceAtToleranceBoundary() {
            LocalDate date = LocalDate.of(2026, 10, 5);
            // diff is exactly 0.01
            TradeRecord tradeA = buildTrade(SourceSystem.SYSTEM_A, "TRD-402", "META", TradeSide.BUY,
                    "100.00000000", "300.00000000", date, date.plusDays(2));
            TradeRecord tradeB = buildTrade(SourceSystem.SYSTEM_B, "TRD-402", "META", TradeSide.BUY,
                    "100.00000000", "300.01000000", date, date.plusDays(2));

            List<ReconciliationResult> results = matchingEngine.reconcile(
                    run, List.of(tradeA), List.of(tradeB), new BigDecimal("0.01000000"));

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getMatchStatus()).isEqualTo(MatchStatus.MATCHED);
        }

        @Test
        @DisplayName("Price difference exceeding tolerance but <= 0.05 should be MEDIUM severity")
        void testPriceDifferenceMediumSeverity() {
            LocalDate date = LocalDate.of(2026, 10, 5);
            // diff is 0.03, tolerance is 0.01
            TradeRecord tradeA = buildTrade(SourceSystem.SYSTEM_A, "TRD-403", "META", TradeSide.BUY,
                    "100.00000000", "300.00000000", date, date.plusDays(2));
            TradeRecord tradeB = buildTrade(SourceSystem.SYSTEM_B, "TRD-403", "META", TradeSide.BUY,
                    "100.00000000", "300.03000000", date, date.plusDays(2));

            List<ReconciliationResult> results = matchingEngine.reconcile(
                    run, List.of(tradeA), List.of(tradeB), new BigDecimal("0.01000000"));

            assertThat(results).hasSize(1);
            ReconciliationResult r = results.get(0);
            assertThat(r.getMatchStatus()).isEqualTo(MatchStatus.MISMATCHED);
            assertThat(r.getDiscrepancyType()).isEqualTo(DiscrepancyType.PRICE_DIFFERENCE);
            assertThat(r.getSeverity()).isEqualTo(DiscrepancySeverity.MEDIUM);
        }

        @Test
        @DisplayName("Price difference > 0.05 should be HIGH severity")
        void testPriceDifferenceHighSeverity() {
            LocalDate date = LocalDate.of(2026, 10, 5);
            // diff is 0.08
            TradeRecord tradeA = buildTrade(SourceSystem.SYSTEM_A, "TRD-404", "META", TradeSide.BUY,
                    "100.00000000", "300.00000000", date, date.plusDays(2));
            TradeRecord tradeB = buildTrade(SourceSystem.SYSTEM_B, "TRD-404", "META", TradeSide.BUY,
                    "100.00000000", "300.08000000", date, date.plusDays(2));

            List<ReconciliationResult> results = matchingEngine.reconcile(
                    run, List.of(tradeA), List.of(tradeB), new BigDecimal("0.01000000"));

            assertThat(results).hasSize(1);
            ReconciliationResult r = results.get(0);
            assertThat(r.getMatchStatus()).isEqualTo(MatchStatus.MISMATCHED);
            assertThat(r.getDiscrepancyType()).isEqualTo(DiscrepancyType.PRICE_DIFFERENCE);
            assertThat(r.getSeverity()).isEqualTo(DiscrepancySeverity.HIGH);
        }
    }

    @Nested
    @DisplayName("Settlement Date Mismatch Tests")
    class SettlementDateMismatchTests {

        @Test
        @DisplayName("Settlement date difference of 1 day should be MEDIUM severity")
        void testSettlementDateDifference1Day() {
            LocalDate tradeDate = LocalDate.of(2026, 10, 5);
            TradeRecord tradeA = buildTrade(SourceSystem.SYSTEM_A, "TRD-501", "TSLA", TradeSide.BUY,
                    "50.00000000", "200.00000000", tradeDate, tradeDate.plusDays(2));
            TradeRecord tradeB = buildTrade(SourceSystem.SYSTEM_B, "TRD-501", "TSLA", TradeSide.BUY,
                    "50.00000000", "200.00000000", tradeDate, tradeDate.plusDays(3));

            List<ReconciliationResult> results = matchingEngine.reconcile(
                    run, List.of(tradeA), List.of(tradeB), new BigDecimal("0.01000000"));

            assertThat(results).hasSize(1);
            ReconciliationResult r = results.get(0);
            assertThat(r.getMatchStatus()).isEqualTo(MatchStatus.MISMATCHED);
            assertThat(r.getDiscrepancyType()).isEqualTo(DiscrepancyType.SETTLEMENT_DATE_MISMATCH);
            assertThat(r.getSeverity()).isEqualTo(DiscrepancySeverity.MEDIUM);
        }

        @Test
        @DisplayName("Settlement date difference > 1 day should be HIGH severity")
        void testSettlementDateDifferenceMultipleDays() {
            LocalDate tradeDate = LocalDate.of(2026, 10, 5);
            TradeRecord tradeA = buildTrade(SourceSystem.SYSTEM_A, "TRD-502", "TSLA", TradeSide.BUY,
                    "50.00000000", "200.00000000", tradeDate, tradeDate.plusDays(2));
            TradeRecord tradeB = buildTrade(SourceSystem.SYSTEM_B, "TRD-502", "TSLA", TradeSide.BUY,
                    "50.00000000", "200.00000000", tradeDate, tradeDate.plusDays(5));

            List<ReconciliationResult> results = matchingEngine.reconcile(
                    run, List.of(tradeA), List.of(tradeB), new BigDecimal("0.01000000"));

            assertThat(results).hasSize(1);
            ReconciliationResult r = results.get(0);
            assertThat(r.getMatchStatus()).isEqualTo(MatchStatus.MISMATCHED);
            assertThat(r.getDiscrepancyType()).isEqualTo(DiscrepancyType.SETTLEMENT_DATE_MISMATCH);
            assertThat(r.getSeverity()).isEqualTo(DiscrepancySeverity.HIGH);
        }
    }

    @Nested
    @DisplayName("Duplicate Keys and Ambiguous Match Tests")
    class DuplicateKeyTests {

        @Test
        @DisplayName("Duplicate candidate keys in Source A must never silently match an arbitrary trade")
        void testDuplicateKeysInSourceA() {
            LocalDate date = LocalDate.of(2026, 10, 5);
            TradeRecord tradeA1 = buildTrade(SourceSystem.SYSTEM_A, "TRD-600", "NFLX", TradeSide.BUY,
                    "10.00000000", "400.00000000", date, date.plusDays(2));
            TradeRecord tradeA2 = buildTrade(SourceSystem.SYSTEM_A, "TRD-600", "NFLX", TradeSide.BUY,
                    "10.00000000", "400.00000000", date, date.plusDays(2));
            TradeRecord tradeB = buildTrade(SourceSystem.SYSTEM_B, "TRD-600", "NFLX", TradeSide.BUY,
                    "10.00000000", "400.00000000", date, date.plusDays(2));

            List<ReconciliationResult> results = matchingEngine.reconcile(
                    run, List.of(tradeA1, tradeA2), List.of(tradeB), new BigDecimal("0.01000000"));

            assertThat(results).hasSize(1);
            ReconciliationResult r = results.get(0);
            assertThat(r.getMatchStatus()).isEqualTo(MatchStatus.MISMATCHED);
            assertThat(r.getDiscrepancyType()).isEqualTo(DiscrepancyType.DUPLICATE_TRADE);
            assertThat(r.getSeverity()).isEqualTo(DiscrepancySeverity.HIGH);
            assertThat(r.getDifferenceDetails()).contains("Ambiguous match: 2 candidate trade(s) in SYSTEM_A");
        }

        @Test
        @DisplayName("Duplicate candidate keys in Source B with none in Source A")
        void testDuplicateKeysInSourceBOnly() {
            LocalDate date = LocalDate.of(2026, 10, 5);
            TradeRecord tradeB1 = buildTrade(SourceSystem.SYSTEM_B, "TRD-601", "NFLX", TradeSide.BUY,
                    "10.00000000", "400.00000000", date, date.plusDays(2));
            TradeRecord tradeB2 = buildTrade(SourceSystem.SYSTEM_B, "TRD-601", "NFLX", TradeSide.BUY,
                    "10.00000000", "400.00000000", date, date.plusDays(2));

            List<ReconciliationResult> results = matchingEngine.reconcile(
                    run, List.of(), List.of(tradeB1, tradeB2), new BigDecimal("0.01000000"));

            assertThat(results).hasSize(1);
            ReconciliationResult r = results.get(0);
            assertThat(r.getMatchStatus()).isEqualTo(MatchStatus.MISMATCHED);
            assertThat(r.getDiscrepancyType()).isEqualTo(DiscrepancyType.DUPLICATE_TRADE);
            assertThat(r.getSeverity()).isEqualTo(DiscrepancySeverity.HIGH);
        }
    }

    @Nested
    @DisplayName("Multi-Field Mismatch Tests")
    class MultiFieldMismatchTests {

        @Test
        @DisplayName("Symbol mismatch produces MULTI_FIELD_MISMATCH with CRITICAL severity")
        void testSymbolMismatch() {
            LocalDate date = LocalDate.of(2026, 10, 5);
            TradeRecord tradeA = buildTrade(SourceSystem.SYSTEM_A, "TRD-701", "AAPL", TradeSide.BUY,
                    "100.00000000", "150.00000000", date, date.plusDays(2));
            TradeRecord tradeB = buildTrade(SourceSystem.SYSTEM_B, "TRD-701", "MSFT", TradeSide.BUY,
                    "100.00000000", "150.00000000", date, date.plusDays(2));

            List<ReconciliationResult> results = matchingEngine.reconcile(
                    run, List.of(tradeA), List.of(tradeB), new BigDecimal("0.01000000"));

            assertThat(results).hasSize(1);
            ReconciliationResult r = results.get(0);
            assertThat(r.getMatchStatus()).isEqualTo(MatchStatus.MISMATCHED);
            assertThat(r.getDiscrepancyType()).isEqualTo(DiscrepancyType.MULTI_FIELD_MISMATCH);
            assertThat(r.getSeverity()).isEqualTo(DiscrepancySeverity.CRITICAL);
        }

        @Test
        @DisplayName("Side mismatch (BUY vs SELL) produces MULTI_FIELD_MISMATCH with CRITICAL severity")
        void testSideMismatch() {
            LocalDate date = LocalDate.of(2026, 10, 5);
            TradeRecord tradeA = buildTrade(SourceSystem.SYSTEM_A, "TRD-702", "AAPL", TradeSide.BUY,
                    "100.00000000", "150.00000000", date, date.plusDays(2));
            TradeRecord tradeB = buildTrade(SourceSystem.SYSTEM_B, "TRD-702", "AAPL", TradeSide.SELL,
                    "100.00000000", "150.00000000", date, date.plusDays(2));

            List<ReconciliationResult> results = matchingEngine.reconcile(
                    run, List.of(tradeA), List.of(tradeB), new BigDecimal("0.01000000"));

            assertThat(results).hasSize(1);
            ReconciliationResult r = results.get(0);
            assertThat(r.getMatchStatus()).isEqualTo(MatchStatus.MISMATCHED);
            assertThat(r.getDiscrepancyType()).isEqualTo(DiscrepancyType.MULTI_FIELD_MISMATCH);
            assertThat(r.getSeverity()).isEqualTo(DiscrepancySeverity.CRITICAL);
        }

        @Test
        @DisplayName("Simultaneous quantity and price mismatch produces MULTI_FIELD_MISMATCH with max severity")
        void testMultiFieldQuantityAndPrice() {
            LocalDate date = LocalDate.of(2026, 10, 5);
            TradeRecord tradeA = buildTrade(SourceSystem.SYSTEM_A, "TRD-703", "AAPL", TradeSide.BUY,
                    "100.00000000", "150.00000000", date, date.plusDays(2));
            // qty differs (>10% -> CRITICAL), price differs (>0.05 -> HIGH)
            TradeRecord tradeB = buildTrade(SourceSystem.SYSTEM_B, "TRD-703", "AAPL", TradeSide.BUY,
                    "50.00000000", "160.00000000", date, date.plusDays(2));

            List<ReconciliationResult> results = matchingEngine.reconcile(
                    run, List.of(tradeA), List.of(tradeB), new BigDecimal("0.01000000"));

            assertThat(results).hasSize(1);
            ReconciliationResult r = results.get(0);
            assertThat(r.getMatchStatus()).isEqualTo(MatchStatus.MISMATCHED);
            assertThat(r.getDiscrepancyType()).isEqualTo(DiscrepancyType.MULTI_FIELD_MISMATCH);
            assertThat(r.getSeverity()).isEqualTo(DiscrepancySeverity.CRITICAL);
        }
    }

    @Nested
    @DisplayName("Empty Inputs and Repeatability Tests")
    class EdgeCasesAndDeterminismTests {

        @Test
        @DisplayName("Reconciliation with empty inputs produces 0 results without errors")
        void testEmptyInputs() {
            List<ReconciliationResult> results = matchingEngine.reconcile(
                    run, List.of(), List.of(), new BigDecimal("0.01000000"));

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("Deterministic processing produces identical order and results regardless of input list permutation")
        void testDeterminismAcrossPermutations() {
            LocalDate date = LocalDate.of(2026, 10, 5);
            TradeRecord t1A = buildTrade(SourceSystem.SYSTEM_A, "TRD-003", "AAPL", TradeSide.BUY, "10", "100", date, date.plusDays(2));
            TradeRecord t2A = buildTrade(SourceSystem.SYSTEM_A, "TRD-001", "MSFT", TradeSide.BUY, "20", "200", date, date.plusDays(2));
            TradeRecord t3A = buildTrade(SourceSystem.SYSTEM_A, "TRD-002", "GOOGL", TradeSide.BUY, "30", "300", date, date.plusDays(2));

            TradeRecord t1B = buildTrade(SourceSystem.SYSTEM_B, "TRD-3", "AAPL", TradeSide.BUY, "10", "100", date, date.plusDays(2));
            TradeRecord t2B = buildTrade(SourceSystem.SYSTEM_B, "TRD-1", "MSFT", TradeSide.BUY, "20", "200", date, date.plusDays(2));
            TradeRecord t3B = buildTrade(SourceSystem.SYSTEM_B, "TRD-2", "GOOGL", TradeSide.BUY, "30", "300", date, date.plusDays(2));

            // Order 1: t1, t2, t3
            List<ReconciliationResult> run1 = matchingEngine.reconcile(
                    run, List.of(t1A, t2A, t3A), List.of(t1B, t2B, t3B), new BigDecimal("0.01"));

            // Order 2: reversed / permuted
            List<ReconciliationResult> run2 = matchingEngine.reconcile(
                    run, List.of(t3A, t1A, t2A), List.of(t2B, t3B, t1B), new BigDecimal("0.01"));

            assertThat(run1).hasSize(3);
            assertThat(run2).hasSize(3);

            for (int i = 0; i < 3; i++) {
                assertThat(run1.get(i).getMatchStatus()).isEqualTo(run2.get(i).getMatchStatus());
                assertThat(run1.get(i).getDiscrepancyType()).isEqualTo(run2.get(i).getDiscrepancyType());
                assertThat(run1.get(i).getTradeA().getExternalTradeId()).isEqualTo(run2.get(i).getTradeA().getExternalTradeId());
            }

            // Verify order is strictly sorted by normalized key: TRD-1, TRD-2, TRD-3
            assertThat(run1.get(0).getTradeA().getExternalTradeId()).isEqualTo("TRD-001");
            assertThat(run1.get(1).getTradeA().getExternalTradeId()).isEqualTo("TRD-002");
            assertThat(run1.get(2).getTradeA().getExternalTradeId()).isEqualTo("TRD-003");
        }
    }
}
