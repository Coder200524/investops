package com.investops.reconciliation.rules;

import com.investops.reconciliation.entity.DiscrepancySeverity;
import com.investops.reconciliation.entity.DiscrepancyType;
import com.investops.trade.entity.TradeRecord;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Component
@Order(3)
public class SettlementDateRule implements ComparisonRule {

    @Override
    public Optional<ComparisonBreak> compare(TradeRecord tradeA, TradeRecord tradeB, BigDecimal priceTolerance) {
        LocalDate dateA = tradeA.getSettlementDate();
        LocalDate dateB = tradeB.getSettlementDate();

        if (dateA == null || dateB == null) {
            return Optional.of(ComparisonBreak.builder()
                    .field("settlement_date")
                    .valueA(String.valueOf(dateA))
                    .valueB(String.valueOf(dateB))
                    .discrepancyType(DiscrepancyType.SETTLEMENT_DATE_MISMATCH)
                    .severity(DiscrepancySeverity.CRITICAL)
                    .message("Settlement date is null on one or both trades")
                    .build());
        }

        if (!dateA.equals(dateB)) {
            long daysDiff = Math.abs(ChronoUnit.DAYS.between(dateA, dateB));
            DiscrepancySeverity severity = (daysDiff > 1) ? DiscrepancySeverity.HIGH : DiscrepancySeverity.MEDIUM;

            return Optional.of(ComparisonBreak.builder()
                    .field("settlement_date")
                    .valueA(dateA.toString())
                    .valueB(dateB.toString())
                    .discrepancyType(DiscrepancyType.SETTLEMENT_DATE_MISMATCH)
                    .severity(severity)
                    .message(String.format("Settlement date mismatch (%d days difference): %s vs %s",
                            daysDiff, dateA, dateB))
                    .build());
        }

        return Optional.empty();
    }
}
