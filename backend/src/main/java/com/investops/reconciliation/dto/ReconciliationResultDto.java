package com.investops.reconciliation.dto;

import com.investops.reconciliation.entity.DiscrepancySeverity;
import com.investops.reconciliation.entity.DiscrepancyType;
import com.investops.reconciliation.entity.MatchStatus;
import com.investops.reconciliation.entity.ReconciliationResult;
import com.investops.trade.dto.TradeRecordDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationResultDto {

    private UUID id;
    private UUID runId;
    private String businessKey;
    private MatchStatus matchStatus;
    private DiscrepancyType discrepancyType;
    private DiscrepancySeverity severity;
    private String differenceDetails;
    private TradeRecordDto tradeA;
    private TradeRecordDto tradeB;
    private Instant createdAt;

    public static ReconciliationResultDto from(ReconciliationResult result) {
        String key = null;
        if (result.getTradeA() != null) {
            key = result.getTradeA().getExternalTradeId();
        } else if (result.getTradeB() != null) {
            key = result.getTradeB().getExternalTradeId();
        }

        return ReconciliationResultDto.builder()
                .id(result.getId())
                .runId(result.getRun().getId())
                .businessKey(key)
                .matchStatus(result.getMatchStatus())
                .discrepancyType(result.getDiscrepancyType())
                .severity(result.getSeverity())
                .differenceDetails(result.getDifferenceDetails())
                .tradeA(result.getTradeA() != null ? TradeRecordDto.from(result.getTradeA()) : null)
                .tradeB(result.getTradeB() != null ? TradeRecordDto.from(result.getTradeB()) : null)
                .createdAt(result.getCreatedAt())
                .build();
    }
}
