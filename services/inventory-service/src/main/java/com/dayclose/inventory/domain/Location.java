package com.dayclose.inventory.domain;

import java.util.UUID;

public record Location(
        UUID id,
        UUID tenantId,
        String name
) {}
