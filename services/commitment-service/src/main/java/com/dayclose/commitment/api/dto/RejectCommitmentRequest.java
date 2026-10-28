package com.dayclose.commitment.api.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectCommitmentRequest(
        String actor,
        @NotBlank String reason
) {}
