package com.investops.exceptionmgmt.dto;

import com.investops.exceptionmgmt.entity.ExceptionStatus;
import com.investops.exceptionmgmt.entity.TradeException;
import com.investops.reconciliation.entity.DiscrepancySeverity;
import com.investops.reconciliation.entity.DiscrepancyType;
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
public class ExceptionDto {

    private UUID id;
    private UUID resultId;
    private UUID runId;
    private String businessKey;
    private DiscrepancyType exceptionType;
    private DiscrepancySeverity severity;
    private ExceptionStatus status;
    private String fieldName;
    private String expectedValue;
    private String actualValue;
    private String differenceDetails;
    private String resolutionNotes;
    private UUID assignedToId;
    private String assignedToUsername;
    private int version;
    private Instant createdAt;
    private Instant updatedAt;

    public static ExceptionDto from(TradeException ex) {
        String key = null;
        String details = null;
        if (ex.getResult() != null) {
            details = ex.getResult().getDifferenceDetails();
            if (ex.getResult().getTradeA() != null) {
                key = ex.getResult().getTradeA().getExternalTradeId();
            } else if (ex.getResult().getTradeB() != null) {
                key = ex.getResult().getTradeB().getExternalTradeId();
            }
        }

        return ExceptionDto.builder()
                .id(ex.getId())
                .resultId(ex.getResult() != null ? ex.getResult().getId() : null)
                .runId(ex.getRun() != null ? ex.getRun().getId() : null)
                .businessKey(key)
                .exceptionType(ex.getExceptionType())
                .severity(ex.getSeverity())
                .status(ex.getStatus())
                .fieldName(ex.getFieldName())
                .expectedValue(ex.getExpectedValue())
                .actualValue(ex.getActualValue())
                .differenceDetails(details)
                .resolutionNotes(ex.getResolutionNotes())
                .assignedToId(ex.getAssignedTo() != null ? ex.getAssignedTo().getId() : null)
                .assignedToUsername(ex.getAssignedTo() != null ? ex.getAssignedTo().getUsername() : null)
                .version(ex.getVersion())
                .createdAt(ex.getCreatedAt())
                .updatedAt(ex.getUpdatedAt())
                .build();
    }
}
