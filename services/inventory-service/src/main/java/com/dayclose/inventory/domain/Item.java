package com.dayclose.inventory.domain;

import java.util.UUID;

public record Item(
        UUID id,
        UUID tenantId,
        String sku,
        String name,
        String unit,
        int pricePaise,
        String currency,
        int reorderPoint,
        int reorderQty,
        boolean active
) {}
