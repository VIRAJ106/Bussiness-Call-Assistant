package com.dayclose.inventory.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AdjustStockRequest(
        @NotNull UUID itemId,
        @NotNull UUID locationId,
        @NotNull Integer delta,
        String actor,
        String reasonNote
) {}
