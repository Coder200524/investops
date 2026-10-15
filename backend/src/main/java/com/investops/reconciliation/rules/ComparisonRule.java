package com.investops.reconciliation.rules;

import com.investops.trade.entity.TradeRecord;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Strategy interface for pluggable field-level comparison rules between two matched trades.
 */
public interface ComparisonRule {

    /**
     * Compare a specific field between tradeA and tradeB.
     *
     * @param tradeA the trade from Source System A
     * @param tradeB the trade from Source System B
     * @param priceTolerance the price tolerance to apply (used by price comparison rules)
     * @return Optional containing ComparisonBreak if a discrepancy is detected, or Optional.empty() if matched
     */
    Optional<ComparisonBreak> compare(TradeRecord tradeA, TradeRecord tradeB, BigDecimal priceTolerance);
}
