package com.dayclose.commitment.saga.dto;

import java.util.UUID;

/**
 * Request to commit a reservation in inventory-service.
 */
public record CommitReservationRequest(
        UUID reservationId,
        String committedBy,
        String reason
) {}
