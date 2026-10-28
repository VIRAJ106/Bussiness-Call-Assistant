package com.dayclose.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record User(
        UUID id,
        UUID tenantId,
        String email,
        String passwordHash,
        String role,
        Instant createdAt
) {}
