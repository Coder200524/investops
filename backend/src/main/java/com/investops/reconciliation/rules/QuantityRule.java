package com.investops.reconciliation.rules;

import com.investops.reconciliation.entity.DiscrepancySeverity;
import com.investops.reconciliation.entity.DiscrepancyType;
import com.investops.trade.entity.TradeRecord;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Component
@Order(1)
public class QuantityRule implements ComparisonRule {

    private static final BigDecimal CRITICAL_PERCENT_THRESHOLD = new BigDecimal("0.10");

    @Override
    public Optional<ComparisonBreak> compare(TradeRecord tradeA, TradeRecord tradeB, BigDecimal priceTolerance) {
        BigDecimal qtyA = tradeA.getQuantity();
        BigDecimal qtyB = tradeB.getQuantity();

        if (qtyA == null || qtyB == null) {
            return Optional.of(ComparisonBreak.builder()
                    .field("quantity")
                    .valueA(String.valueOf(qtyA))
                    .valueB(String.valueOf(qtyB))
                    .discrepancyType(DiscrepancyType.QUANTITY_MISMATCH)
                    .severity(DiscrepancySeverity.CRITICAL)
                    .message("Quantity is null on one or both trades")
                    .build());
        }

        if (qtyA.compareTo(qtyB) != 0) {
            BigDecimal diff = qtyA.subtract(qtyB).abs();
            BigDecimal maxQty = qtyA.max(qtyB);
            DiscrepancySeverity severity = DiscrepancySeverity.HIGH;

            if (maxQty.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal pctDiff = diff.divide(maxQty, 8, RoundingMode.HALF_UP);
                if (pctDiff.compareTo(CRITICAL_PERCENT_THRESHOLD) > 0) {
                    severity = DiscrepancySeverity.CRITICAL;
                }
            }

            return Optional.of(ComparisonBreak.builder()
                    .field("quantity")
                    .valueA(qtyA.stripTrailingZeros().toPlainString())
                    .valueB(qtyB.stripTrailingZeros().toPlainString())
                    .discrepancyType(DiscrepancyType.QUANTITY_MISMATCH)
                    .severity(severity)
                    .message(String.format("Quantity mismatch: %s vs %s (diff: %s)",
                            qtyA.stripTrailingZeros().toPlainString(),
                            qtyB.stripTrailingZeros().toPlainString(),
                            diff.stripTrailingZeros().toPlainString()))
                    .build());
        }

        return Optional.empty();
    }
}
