package com.dayclose.events.inventory;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * Payload for {@code inventory.reservation.committed} events.
 */
public record ReservationCommittedPayload(
        @JsonProperty("reservation_id") UUID reservationId,
        @JsonProperty("commitment_id") UUID commitmentId,
        @JsonProperty("item_id") UUID itemId,
        @JsonProperty("location_id") UUID locationId,
        @JsonProperty("qty") int qty
) {
}
