package com.dayclose.commitment.domain;

import java.util.UUID;

/**
 * A line item in a commitment (one product/quantity/price).
 */
public record CommitmentLine(
        Long id,
        UUID commitmentId,
        UUID itemId,
        UUID locationId,
        UUID reservationId,
        int qty,
        int pricePaise
) {}
