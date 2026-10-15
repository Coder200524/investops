package com.investops.reconciliation.rules;

import com.investops.reconciliation.entity.DiscrepancySeverity;
import com.investops.reconciliation.entity.DiscrepancyType;
import com.investops.trade.entity.TradeRecord;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
@Order(2)
public class PriceRule implements ComparisonRule {

    private static final BigDecimal DEFAULT_TOLERANCE = new BigDecimal("0.01000000");
    private static final BigDecimal HIGH_SEVERITY_THRESHOLD = new BigDecimal("0.05");

    @Override
    public Optional<ComparisonBreak> compare(TradeRecord tradeA, TradeRecord tradeB, BigDecimal priceTolerance) {
        BigDecimal priceA = tradeA.getPrice();
        BigDecimal priceB = tradeB.getPrice();

        if (priceA == null || priceB == null) {
            return Optional.of(ComparisonBreak.builder()
                    .field("price")
                    .valueA(String.valueOf(priceA))
                    .valueB(String.valueOf(priceB))
                    .discrepancyType(DiscrepancyType.PRICE_DIFFERENCE)
                    .severity(DiscrepancySeverity.CRITICAL)
                    .message("Price is null on one or both trades")
                    .build());
        }

        BigDecimal tolerance = (priceTolerance != null) ? priceTolerance : DEFAULT_TOLERANCE;
        BigDecimal diff = priceA.subtract(priceB).abs();

        if (diff.compareTo(tolerance) > 0) {
            DiscrepancySeverity severity = (diff.compareTo(HIGH_SEVERITY_THRESHOLD) > 0)
                    ? DiscrepancySeverity.HIGH
                    : DiscrepancySeverity.MEDIUM;

            return Optional.of(ComparisonBreak.builder()
                    .field("price")
                    .valueA(priceA.stripTrailingZeros().toPlainString())
                    .valueB(priceB.stripTrailingZeros().toPlainString())
                    .discrepancyType(DiscrepancyType.PRICE_DIFFERENCE)
                    .severity(severity)
                    .message(String.format("Price difference of %s exceeds tolerance %s: %s vs %s",
                            diff.stripTrailingZeros().toPlainString(),
                            tolerance.stripTrailingZeros().toPlainString(),
                            priceA.stripTrailingZeros().toPlainString(),
                            priceB.stripTrailingZeros().toPlainString()))
                    .build());
        }

        return Optional.empty();
    }
}
