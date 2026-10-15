package com.investops.reconciliation.entity;

public enum MatchStatus {
    MATCHED,
    MISMATCHED,
    MISSING,
    UNMATCHED;

    public boolean isBreak() {
        return this != MATCHED;
    }
}
