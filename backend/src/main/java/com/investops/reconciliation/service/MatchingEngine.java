package com.investops.reconciliation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.investops.reconciliation.entity.*;
import com.investops.reconciliation.rules.ComparisonBreak;
import com.investops.reconciliation.rules.ComparisonRule;
import com.investops.trade.entity.TradeRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Deterministic two-pass reconciliation engine.
 * Targets O(n + m) time complexity using hash-indexed candidate sets.
 */
@Service
@Slf4j
public class MatchingEngine {

    private final List<ComparisonRule> comparisonRules;
    private final ObjectMapper objectMapper;

    public MatchingEngine(List<ComparisonRule> comparisonRules, ObjectMapper objectMapper) {
        this.comparisonRules = comparisonRules;
        this.objectMapper = objectMapper;
    }

    /**
     * Executes deterministic two-pass reconciliation on trades from Source A and Source B.
     *
     * @param run the reconciliation run metadata
     * @param tradesA trade records from Source System A
     * @param tradesB trade records from Source System B
     * @param priceTolerance configurable price tolerance for comparison
     * @return list of deterministic ReconciliationResult entities
     */
    public List<ReconciliationResult> reconcile(
            ReconciliationRun run,
            List<TradeRecord> tradesA,
            List<TradeRecord> tradesB,
            BigDecimal priceTolerance) {

        log.info("Starting reconciliation run {} with {} trades from Source A and {} trades from Source B. Price tolerance: {}",
                run.getId(), tradesA.size(), tradesB.size(), priceTolerance);

        // =========================================================================
        // PASS 1 — BUILD MATCH INDEX (O(n + m))
        // =========================================================================
        Map<String, List<TradeRecord>> indexA = new HashMap<>();
        for (TradeRecord trade : tradesA) {
            String key = normalizeBusinessKey(trade.getExternalTradeId());
            indexA.computeIfAbsent(key, k -> new ArrayList<>()).add(trade);
        }

        Map<String, List<TradeRecord>> indexB = new HashMap<>();
        for (TradeRecord trade : tradesB) {
            String key = normalizeBusinessKey(trade.getExternalTradeId());
            indexB.computeIfAbsent(key, k -> new ArrayList<>()).add(trade);
        }

        // =========================================================================
        // PASS 2 — COMPARE AND CLASSIFY (O(n + m))
        // Deterministic iteration over sorted union of normalized keys
        // =========================================================================
        Set<String> allKeys = new TreeSet<>();
        allKeys.addAll(indexA.keySet());
        allKeys.addAll(indexB.keySet());

        List<ReconciliationResult> results = new ArrayList<>(allKeys.size());

        for (String key : allKeys) {
            List<TradeRecord> listA = indexA.getOrDefault(key, Collections.emptyList());
            List<TradeRecord> listB = indexB.getOrDefault(key, Collections.emptyList());

            // Case 1: Key exists only in Source A -> MISSING_IN_SOURCE_B
            if (listB.isEmpty()) {
                if (listA.size() == 1) {
                    TradeRecord tradeA = listA.get(0);
                    results.add(ReconciliationResult.builder()
                            .run(run)
                            .tradeA(tradeA)
                            .tradeB(null)
                            .matchStatus(MatchStatus.MISSING)
                            .discrepancyType(DiscrepancyType.MISSING_IN_SOURCE_B)
                            .severity(DiscrepancySeverity.HIGH)
                            .differenceDetails(String.format("Trade '%s' exists in %s but is missing in %s",
                                    tradeA.getExternalTradeId(), run.getSourceA(), run.getSourceB()))
                            .build());
                } else {
                    // Duplicate trades in Source A and missing in Source B
                    String ids = listA.stream().map(t -> t.getId().toString()).collect(Collectors.joining(", "));
                    results.add(ReconciliationResult.builder()
                            .run(run)
                            .tradeA(listA.get(0))
                            .tradeB(null)
                            .matchStatus(MatchStatus.MISMATCHED)
                            .discrepancyType(DiscrepancyType.DUPLICATE_TRADE)
                            .severity(DiscrepancySeverity.HIGH)
                            .differenceDetails(String.format("Duplicate candidate keys (%d trades) in %s with key '%s', missing in %s. Trade IDs: [%s]",
                                    listA.size(), run.getSourceA(), key, run.getSourceB(), ids))
                            .build());
                }
                continue;
            }

            // Case 2: Key exists only in Source B -> MISSING_IN_SOURCE_A
            if (listA.isEmpty()) {
                if (listB.size() == 1) {
                    TradeRecord tradeB = listB.get(0);
                    results.add(ReconciliationResult.builder()
                            .run(run)
                            .tradeA(null)
                            .tradeB(tradeB)
                            .matchStatus(MatchStatus.MISSING)
                            .discrepancyType(DiscrepancyType.MISSING_IN_SOURCE_A)
                            .severity(DiscrepancySeverity.HIGH)
                            .differenceDetails(String.format("Trade '%s' exists in %s but is missing in %s",
                                    tradeB.getExternalTradeId(), run.getSourceB(), run.getSourceA()))
                            .build());
                } else {
                    // Duplicate trades in Source B and missing in Source A
                    String ids = listB.stream().map(t -> t.getId().toString()).collect(Collectors.joining(", "));
                    results.add(ReconciliationResult.builder()
                            .run(run)
                            .tradeA(null)
                            .tradeB(listB.get(0))
                            .matchStatus(MatchStatus.MISMATCHED)
                            .discrepancyType(DiscrepancyType.DUPLICATE_TRADE)
                            .severity(DiscrepancySeverity.HIGH)
                            .differenceDetails(String.format("Duplicate candidate keys (%d trades) in %s with key '%s', missing in %s. Trade IDs: [%s]",
                                    listB.size(), run.getSourceB(), key, run.getSourceA(), ids))
                            .build());
                }
                continue;
            }

            // Case 3: Duplicate candidate keys in Source A and/or Source B
            // Never arbitrarily pick one candidate when matching is ambiguous
            if (listA.size() > 1 || listB.size() > 1) {
                String idsA = listA.stream().map(t -> t.getId().toString()).collect(Collectors.joining(", "));
                String idsB = listB.stream().map(t -> t.getId().toString()).collect(Collectors.joining(", "));
                results.add(ReconciliationResult.builder()
                        .run(run)
                        .tradeA(listA.get(0))
                        .tradeB(listB.get(0))
                        .matchStatus(MatchStatus.MISMATCHED)
                        .discrepancyType(DiscrepancyType.DUPLICATE_TRADE)
                        .severity(DiscrepancySeverity.HIGH)
                        .differenceDetails(String.format("Ambiguous match: %d candidate trade(s) in %s (IDs: [%s]) vs %d candidate trade(s) in %s (IDs: [%s]) for business key '%s'",
                                listA.size(), run.getSourceA(), idsA, listB.size(), run.getSourceB(), idsB, key))
                        .build());
                continue;
            }

            // Case 4: Exactly 1 candidate on each side (1:1 match)
            TradeRecord tradeA = listA.get(0);
            TradeRecord tradeB = listB.get(0);

            List<ComparisonBreak> breaks = new ArrayList<>();
            for (ComparisonRule rule : comparisonRules) {
                rule.compare(tradeA, tradeB, priceTolerance).ifPresent(breaks::add);
            }

            if (breaks.isEmpty()) {
                // Perfect match within tolerances
                results.add(ReconciliationResult.builder()
                        .run(run)
                        .tradeA(tradeA)
                        .tradeB(tradeB)
                        .matchStatus(MatchStatus.MATCHED)
                        .discrepancyType(DiscrepancyType.NONE)
                        .severity(null)
                        .differenceDetails(null)
                        .build());
            } else if (breaks.size() == 1) {
                ComparisonBreak singleBreak = breaks.get(0);
                results.add(ReconciliationResult.builder()
                        .run(run)
                        .tradeA(tradeA)
                        .tradeB(tradeB)
                        .matchStatus(MatchStatus.MISMATCHED)
                        .discrepancyType(singleBreak.getDiscrepancyType())
                        .severity(singleBreak.getSeverity())
                        .differenceDetails(singleBreak.getMessage())
                        .build());
            } else {
                // Multiple fields differ
                DiscrepancySeverity highestSeverity = breaks.stream()
                        .map(ComparisonBreak::getSeverity)
                        .reduce(DiscrepancySeverity.LOW, DiscrepancySeverity::max);

                String detailsJson;
                try {
                    detailsJson = objectMapper.writeValueAsString(breaks);
                } catch (JsonProcessingException e) {
                    detailsJson = breaks.stream()
                            .map(ComparisonBreak::getMessage)
                            .collect(Collectors.joining("; "));
                }

                results.add(ReconciliationResult.builder()
                        .run(run)
                        .tradeA(tradeA)
                        .tradeB(tradeB)
                        .matchStatus(MatchStatus.MISMATCHED)
                        .discrepancyType(DiscrepancyType.MULTI_FIELD_MISMATCH)
                        .severity(highestSeverity)
                        .differenceDetails(detailsJson)
                        .build());
            }
        }

        return results;
    }

    /**
     * Normalizes the external trade identifier:
     * - Trims whitespace
     * - Converts to uppercase
     * - Strips leading zeros from numeric components (e.g., TRD-00042 -> TRD-42)
     */
    public static String normalizeBusinessKey(String externalTradeId) {
        if (externalTradeId == null) {
            return "";
        }
        String trimmed = externalTradeId.trim().toUpperCase();
        return trimmed.replaceAll("(?<=\\D|^)0+(?=\\d)", "");
    }
}
