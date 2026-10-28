package com.dayclose.commitment.saga.dto;

import java.util.UUID;

/**
 * Request to release a reservation in inventory-service.
 */
public record ReleaseReservationRequest(
        UUID reservationId,
        String releasedBy,
        String reason
) {}
