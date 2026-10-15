package com.investops.reconciliation.dto;

import com.investops.reconciliation.entity.RunStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationRunResponse {
    private UUID runId;
    private RunStatus status;
    private String message;
}
