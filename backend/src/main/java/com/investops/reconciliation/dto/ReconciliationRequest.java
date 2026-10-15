package com.investops.reconciliation.dto;

import com.investops.trade.entity.SourceSystem;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationRequest {

    @NotNull(message = "tradeDateFrom is required")
    private LocalDate tradeDateFrom;

    @NotNull(message = "tradeDateTo is required")
    private LocalDate tradeDateTo;

    @Builder.Default
    private SourceSystem sourceA = SourceSystem.SYSTEM_A;

    @Builder.Default
    private SourceSystem sourceB = SourceSystem.SYSTEM_B;

    @Builder.Default
    private BigDecimal priceTolerance = new BigDecimal("0.01000000");
}
