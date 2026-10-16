package com.investops.dashboard.service;

import com.investops.dashboard.dto.DashboardSummaryDto;
import com.investops.exceptionmgmt.dto.ExceptionDto;
import com.investops.exceptionmgmt.entity.ExceptionStatus;
import com.investops.exceptionmgmt.entity.TradeException;
import com.investops.exceptionmgmt.repository.ExceptionRepository;
import com.investops.reconciliation.dto.ReconciliationRunDto;
import com.investops.reconciliation.entity.DiscrepancySeverity;
import com.investops.reconciliation.entity.DiscrepancyType;
import com.investops.reconciliation.entity.ReconciliationRun;
import com.investops.reconciliation.repository.ReconciliationRunRepository;
import com.investops.trade.repository.TradeRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final TradeRecordRepository tradeRecordRepository;
    private final ReconciliationRunRepository reconciliationRunRepository;
    private final ExceptionRepository exceptionRepository;

    @Transactional(readOnly = true)
    public DashboardSummaryDto getDashboardSummary() {
        long totalTrades = tradeRecordRepository.count();
        long totalRuns = reconciliationRunRepository.count();

        List<ReconciliationRun> allRuns = reconciliationRunRepository.findAll();
        long totalCompared = 0;
        long totalMatched = 0;

        for (ReconciliationRun run : allRuns) {
            totalCompared += run.getTotalCompared();
            totalMatched += run.getMatchedCount();
        }

        double overallMatchRate = 0.0;
        if (totalCompared > 0) {
            overallMatchRate = BigDecimal.valueOf((double) totalMatched * 100.0 / totalCompared)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
        } else if (totalRuns > 0) {
            overallMatchRate = 100.0;
        }

        // Exceptions by severity
        Map<String, Long> severityCounts = new LinkedHashMap<>();
        for (DiscrepancySeverity severity : DiscrepancySeverity.values()) {
            severityCounts.put(severity.name(), exceptionRepository.countBySeverity(severity));
        }

        // Exceptions by status
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (ExceptionStatus status : ExceptionStatus.values()) {
            statusCounts.put(status.name(), exceptionRepository.countByStatus(status));
        }

        // Total open exceptions (OPEN, ACKNOWLEDGED, IN_REVIEW)
        long openExceptions = statusCounts.getOrDefault(ExceptionStatus.OPEN.name(), 0L)
                + statusCounts.getOrDefault(ExceptionStatus.ACKNOWLEDGED.name(), 0L)
                + statusCounts.getOrDefault(ExceptionStatus.IN_REVIEW.name(), 0L);

        // Exceptions by type
        Map<String, Long> typeCounts = new LinkedHashMap<>();
        for (DiscrepancyType type : DiscrepancyType.values()) {
            typeCounts.put(type.name(), exceptionRepository.countByExceptionType(type));
        }

        // Recent runs (top 5)
        List<ReconciliationRunDto> recentRuns = reconciliationRunRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(0, 5))
                .getContent()
                .stream()
                .map(ReconciliationRunDto::from)
                .collect(Collectors.toList());

        // Recent high/critical exceptions (top 5)
        List<TradeException> highSevExceptions = exceptionRepository.findTop10BySeverityInOrderByCreatedAtDesc(
                List.of(DiscrepancySeverity.CRITICAL, DiscrepancySeverity.HIGH));
        List<ExceptionDto> recentExceptions = highSevExceptions.stream()
                .limit(5)
                .map(ExceptionDto::from)
                .collect(Collectors.toList());

        Instant lastRunAt = null;
        Double lastRunMatchRate = null;
        if (!recentRuns.isEmpty()) {
            ReconciliationRunDto latest = recentRuns.get(0);
            lastRunAt = latest.getCreatedAt();
            lastRunMatchRate = latest.getMatchRate();
        }

        return DashboardSummaryDto.builder()
                .totalTrades(totalTrades)
                .totalReconciliationRuns(totalRuns)
                .totalReconciled(totalCompared)
                .matchRate(overallMatchRate)
                .openExceptions(openExceptions)
                .exceptionsBySeverity(severityCounts)
                .exceptionsByStatus(statusCounts)
                .exceptionsByType(typeCounts)
                .recentRuns(recentRuns)
                .recentHighSeverityExceptions(recentExceptions)
                .lastRunAt(lastRunAt)
                .lastRunMatchRate(lastRunMatchRate)
                .build();
    }
}
