package com.investops.reconciliation.rules;

import com.investops.reconciliation.entity.DiscrepancySeverity;
import com.investops.reconciliation.entity.DiscrepancyType;
import com.investops.trade.entity.TradeRecord;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
@Order(5)
public class SideRule implements ComparisonRule {

    @Override
    public Optional<ComparisonBreak> compare(TradeRecord tradeA, TradeRecord tradeB, BigDecimal priceTolerance) {
        if (tradeA.getSide() == null || tradeB.getSide() == null || tradeA.getSide() != tradeB.getSide()) {
            return Optional.of(ComparisonBreak.builder()
                    .field("side")
                    .valueA(String.valueOf(tradeA.getSide()))
                    .valueB(String.valueOf(tradeB.getSide()))
                    .discrepancyType(DiscrepancyType.MULTI_FIELD_MISMATCH)
                    .severity(DiscrepancySeverity.CRITICAL)
                    .message(String.format("Trade side mismatch: %s vs %s", tradeA.getSide(), tradeB.getSide()))
                    .build());
        }

        return Optional.empty();
    }
}
