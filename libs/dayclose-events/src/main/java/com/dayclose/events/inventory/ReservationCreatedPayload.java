package com.dayclose.events.inventory;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * Payload for {@code inventory.reservation.created} events.
 */
public record ReservationCreatedPayload(
        @JsonProperty("reservation_id") UUID reservationId,
        @JsonProperty("commitment_id") UUID commitmentId,
        @JsonProperty("item_id") UUID itemId,
        @JsonProperty("location_id") UUID locationId,
        @JsonProperty("qty") int qty,
        @JsonProperty("customer_ref") String customerRef,
        @JsonProperty("expires_at") Instant expiresAt
) {
}
