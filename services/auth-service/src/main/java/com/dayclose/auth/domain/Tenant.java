package com.dayclose.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record Tenant(UUID id, String name, Instant createdAt) {}
