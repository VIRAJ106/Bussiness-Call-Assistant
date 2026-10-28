package com.dayclose.commitment.api.dto;

public record ApproveCommitmentRequest(
        String actor,  // e.g., "owner:<uuid>"
        String reason
) {}
