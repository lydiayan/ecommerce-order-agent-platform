package com.example.mallorder.refund;

import java.util.EnumSet;
import java.util.Set;

public enum AfterSalesStatus {
    PENDING_REVIEW,
    APPROVED,
    REJECTED,
    WAITING_RETURN,
    RETURNING,
    RECEIVED,
    REFUNDING,
    REFUNDED,
    CLOSED;

    public boolean canTransitionTo(AfterSalesStatus target) {
        return allowedTargets().contains(target);
    }

    private Set<AfterSalesStatus> allowedTargets() {
        return switch (this) {
            case PENDING_REVIEW -> EnumSet.of(APPROVED, REJECTED, CLOSED);
            case APPROVED -> EnumSet.of(WAITING_RETURN, REFUNDING, CLOSED);
            case WAITING_RETURN -> EnumSet.of(RETURNING, CLOSED);
            case RETURNING -> EnumSet.of(RECEIVED, CLOSED);
            case RECEIVED -> EnumSet.of(REFUNDING, CLOSED);
            case REFUNDING -> EnumSet.of(REFUNDED, CLOSED);
            case REFUNDED, REJECTED -> EnumSet.of(CLOSED);
            case CLOSED -> EnumSet.noneOf(AfterSalesStatus.class);
        };
    }
}
