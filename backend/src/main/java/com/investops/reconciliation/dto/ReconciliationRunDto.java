package com.investops.reconciliation.dto;

import com.investops.reconciliation.entity.ReconciliationRun;
import com.investops.reconciliation.entity.RunStatus;
import com.investops.trade.entity.SourceSystem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationRunDto {

    private UUID id;
    private Instant runDate;
    private LocalDate tradeDateFrom;
    private LocalDate tradeDateTo;
    private SourceSystem sourceA;
    private SourceSystem sourceB;
    private RunStatus status;
    private int totalCompared;
    private int matchedCount;
    private int breakCount;
    private double matchRate;
    private BigDecimal priceTolerance;
    private UUID initiatedBy;
    private Instant startedAt;
    private Instant completedAt;
    private Instant createdAt;

    public static ReconciliationRunDto from(ReconciliationRun run) {
        double matchRate = 0.0;
        if (run.getTotalCompared() > 0) {
            matchRate = BigDecimal.valueOf((double) run.getMatchedCount() * 100.0 / run.getTotalCompared())
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
        } else if (run.getStatus() == RunStatus.COMPLETED) {
            matchRate = 100.0;
        }

        return ReconciliationRunDto.builder()
                .id(run.getId())
                .runDate(run.getRunDate())
                .tradeDateFrom(run.getTradeDateFrom())
                .tradeDateTo(run.getTradeDateTo())
                .sourceA(run.getSourceA())
                .sourceB(run.getSourceB())
                .status(run.getStatus())
                .totalCompared(run.getTotalCompared())
                .matchedCount(run.getMatchedCount())
                .breakCount(run.getBreakCount())
                .matchRate(matchRate)
                .priceTolerance(run.getPriceTolerance())
                .initiatedBy(run.getInitiatedBy())
                .startedAt(run.getStartedAt())
                .completedAt(run.getCompletedAt())
                .createdAt(run.getCreatedAt())
                .build();
    }
}
