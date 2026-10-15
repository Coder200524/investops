package com.investops.trade.entity;

public enum SourceSystem {
    SYSTEM_A,
    SYSTEM_B;

    public static SourceSystem fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Source system cannot be null");
        }
        try {
            return SourceSystem.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported source system: " + value + ". Supported: SYSTEM_A, SYSTEM_B");
        }
    }
}
