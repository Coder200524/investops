package com.investops.exceptionmgmt.entity;

import java.util.EnumSet;
import java.util.Set;

public enum ExceptionStatus {
    OPEN,
    ACKNOWLEDGED,
    IN_REVIEW,
    ESCALATED,
    RESOLVED,
    CLOSED;

    public boolean canTransitionTo(ExceptionStatus target) {
        if (target == null || this == target) {
            return false;
        }

        return switch (this) {
            case OPEN -> EnumSet.of(ACKNOWLEDGED, IN_REVIEW, ESCALATED, RESOLVED).contains(target);
            case ACKNOWLEDGED -> EnumSet.of(IN_REVIEW, ESCALATED, RESOLVED).contains(target);
            case IN_REVIEW -> EnumSet.of(ACKNOWLEDGED, ESCALATED, RESOLVED).contains(target);
            case ESCALATED -> EnumSet.of(IN_REVIEW, RESOLVED).contains(target);
            case RESOLVED -> target == CLOSED;
            case CLOSED -> false;
        };
    }

    public boolean isTerminal() {
        return this == RESOLVED || this == CLOSED;
    }
}
