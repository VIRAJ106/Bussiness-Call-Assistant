package com.dayclose.commitment.domain;

/**
 * Commitment state machine (ADR-0005).
 */
public enum CommitmentState {
    CREATED,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    COMMITTED,
    FAILED;

    public static CommitmentState fromString(String value) {
        return valueOf(value.toUpperCase());
    }

    public String toDbValue() {
        return name().toLowerCase();
    }
}
