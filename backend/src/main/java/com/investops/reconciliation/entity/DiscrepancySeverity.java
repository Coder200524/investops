package com.investops.reconciliation.entity;

public enum DiscrepancySeverity {
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    CRITICAL(4);

    private final int level;

    DiscrepancySeverity(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public static DiscrepancySeverity max(DiscrepancySeverity a, DiscrepancySeverity b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.level >= b.level ? a : b;
    }
}
