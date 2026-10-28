package com.dayclose.inventory.domain;

import java.util.UUID;

public record StockLevel(
        UUID itemId,
        UUID locationId,
        UUID tenantId,
        int onHand,
        int reserved
) {
    public int available() {
        return onHand - reserved;
    }
}
