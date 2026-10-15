package com.investops.reconciliation.rules;

import com.investops.reconciliation.entity.DiscrepancySeverity;
import com.investops.reconciliation.entity.DiscrepancyType;
import com.investops.trade.entity.TradeRecord;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
@Order(4)
public class SymbolRule implements ComparisonRule {

    @Override
    public Optional<ComparisonBreak> compare(TradeRecord tradeA, TradeRecord tradeB, BigDecimal priceTolerance) {
        String symbolA = tradeA.getSymbol();
        String symbolB = tradeB.getSymbol();

        if (symbolA == null || symbolB == null || !symbolA.trim().equalsIgnoreCase(symbolB.trim())) {
            return Optional.of(ComparisonBreak.builder()
                    .field("symbol")
                    .valueA(symbolA)
                    .valueB(symbolB)
                    .discrepancyType(DiscrepancyType.MULTI_FIELD_MISMATCH)
                    .severity(DiscrepancySeverity.CRITICAL)
                    .message(String.format("Instrument symbol mismatch: %s vs %s", symbolA, symbolB))
                    .build());
        }

        return Optional.empty();
    }
}
