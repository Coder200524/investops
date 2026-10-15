package com.investops.reconciliation.entity;

public enum DiscrepancyType {
    NONE,
    MISSING_IN_SOURCE_A,
    MISSING_IN_SOURCE_B,
    DUPLICATE_TRADE,
    QUANTITY_MISMATCH,
    PRICE_DIFFERENCE,
    SETTLEMENT_DATE_MISMATCH,
    MULTI_FIELD_MISMATCH
}
