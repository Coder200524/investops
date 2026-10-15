package com.investops.reconciliation.rules;

import com.investops.reconciliation.entity.DiscrepancySeverity;
import com.investops.reconciliation.entity.DiscrepancyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComparisonBreak {
    private String field;
    private String valueA;
    private String valueB;
    private DiscrepancyType discrepancyType;
    private DiscrepancySeverity severity;
    private String message;
}
