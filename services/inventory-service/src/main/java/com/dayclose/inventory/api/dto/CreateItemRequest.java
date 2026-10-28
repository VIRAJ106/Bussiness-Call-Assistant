package com.dayclose.inventory.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateItemRequest(
        @NotBlank String sku,
        @NotBlank String name,
        String unit,
        @NotNull @Min(0) Integer pricePaise,
        String currency,
        Integer reorderPoint,
        Integer reorderQty
) {}
