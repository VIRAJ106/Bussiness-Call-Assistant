package com.dayclose.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record ServiceCredential(
        String clientId,
        UUID tenantId,
        String secretHash,
        String scopes,
        Instant createdAt
) {}
