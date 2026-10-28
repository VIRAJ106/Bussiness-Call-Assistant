package com.dayclose.commitment.domain;

/**
 * Types of commitments the AI agent can create.
 */
public enum CommitmentType {
    ORDER,      // Customer order
    DISCOUNT,   // Price discount offered
    REORDER;    // Stock reorder request

    public static CommitmentType fromString(String value) {
        return valueOf(value.toUpperCase());
    }

    public String toDbValue() {
        return name().toLowerCase();
    }
}
