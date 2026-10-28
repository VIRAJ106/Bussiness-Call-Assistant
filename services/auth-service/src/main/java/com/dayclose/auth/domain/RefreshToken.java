package com.dayclose.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record RefreshToken(
        UUID id,
        UUID userId,
        String tokenHash,
        UUID familyId,
        boolean revoked,
        Instant expiresAt,
        Instant createdAt
) {}
