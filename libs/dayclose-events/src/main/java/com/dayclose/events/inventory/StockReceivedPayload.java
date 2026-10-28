package com.dayclose.events.inventory;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * Payload for {@code inventory.stock.received} events.
 */
public record StockReceivedPayload(
        @JsonProperty("item_id") UUID itemId,
        @JsonProperty("location_id") UUID locationId,
        @JsonProperty("qty") int qty,
        @JsonProperty("new_on_hand") int newOnHand,
        @JsonProperty("actor") String actor
) {
}
