package com.investops.exceptionmgmt.dto;

import com.investops.exceptionmgmt.entity.ExceptionStatus;
import com.investops.reconciliation.entity.DiscrepancySeverity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionUpdateRequest {

    private ExceptionStatus status;
    private DiscrepancySeverity severity;
    private UUID assignedTo;
    private String resolutionNotes;
    private Integer version;
}
