package com.dayclose.inventory.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReceiveStockRequest(
        @NotNull UUID itemId,
        @NotNull UUID locationId,
        @NotNull @Min(1) Integer qty,
        String actor
) {}
