package com.investops.trade.entity;

public enum TradeSide {
    BUY,
    SELL;

    public static TradeSide fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Trade side cannot be null");
        }
        try {
            return TradeSide.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid trade side: " + value + ". Allowed values: BUY, SELL");
        }
    }
}
