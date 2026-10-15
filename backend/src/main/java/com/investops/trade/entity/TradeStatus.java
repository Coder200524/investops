package com.investops.trade.entity;

public enum TradeStatus {
    PENDING,
    VALIDATED,
    REJECTED;

    public static TradeStatus fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return VALIDATED;
        }
        try {
            return TradeStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return VALIDATED;
        }
    }
}
