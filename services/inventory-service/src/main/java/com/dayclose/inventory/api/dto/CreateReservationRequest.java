package com.dayclose.inventory.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateReservationRequest(
        @NotNull UUID commitmentId,
        @NotNull UUID itemId,
        @NotNull UUID locationId,
        @NotNull @Min(1) Integer qty,
        String customerRef,
        String ttl // e.g., "30 minutes", "2 hours"
) {}
