package com.dayclose.inventory.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ExtendReservationRequest(
        @NotBlank String extension // e.g., "30 minutes"
) {}
