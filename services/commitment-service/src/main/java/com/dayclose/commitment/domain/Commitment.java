package com.dayclose.commitment.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A commitment is a customer request (order, discount, reorder) awaiting owner approval.
 * State machine: created → pending_approval → approved/rejected → committed/failed (ADR-0005).
 */
public record Commitment(
        UUID id,
        UUID tenantId,
        String customerRef,
        CommitmentType type,
        int tier,
        CommitmentState state,
        int totalPaise,
        String currency,
        String metadata,
        Instant createdAt,
        Instant updatedAt
) {
    public boolean canTransitionTo(CommitmentState newState) {
        return switch (state) {
            case CREATED -> newState == CommitmentState.PENDING_APPROVAL;
            case PENDING_APPROVAL -> newState == CommitmentState.APPROVED || newState == CommitmentState.REJECTED;
            case APPROVED -> newState == CommitmentState.COMMITTED || newState == CommitmentState.FAILED;
            case REJECTED, COMMITTED, FAILED -> false; // Terminal states
        };
    }

    public boolean isPendingApproval() {
        return state == CommitmentState.PENDING_APPROVAL;
    }

    public boolean isTerminal() {
        return state == CommitmentState.REJECTED || 
               state == CommitmentState.COMMITTED || 
               state == CommitmentState.FAILED;
    }
}
