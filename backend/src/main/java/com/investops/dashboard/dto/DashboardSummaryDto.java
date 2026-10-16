package com.investops.dashboard.dto;

import com.investops.exceptionmgmt.dto.ExceptionDto;
import com.investops.reconciliation.dto.ReconciliationRunDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryDto {

    private long totalTrades;
    private long totalReconciliationRuns;
    private long totalReconciled;
    private double matchRate;
    private long openExceptions;

    private Map<String, Long> exceptionsBySeverity;
    private Map<String, Long> exceptionsByStatus;
    private Map<String, Long> exceptionsByType;

    private List<ReconciliationRunDto> recentRuns;
    private List<ExceptionDto> recentHighSeverityExceptions;

    private Instant lastRunAt;
    private Double lastRunMatchRate;
}
