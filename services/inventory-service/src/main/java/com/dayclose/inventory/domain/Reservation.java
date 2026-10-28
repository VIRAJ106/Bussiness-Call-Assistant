package com.dayclose.inventory.domain;

import java.time.Instant;
import java.util.UUID;

public record Reservation(
        UUID id,
        UUID tenantId,
        UUID commitmentId,
        UUID itemId,
        UUID locationId,
        int qty,
        String customerRef,
        String state,
        Instant expiresAt
) {
    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }
}
