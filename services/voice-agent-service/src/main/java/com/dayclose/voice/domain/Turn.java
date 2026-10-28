package com.dayclose.voice.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * One turn in a conversation: customer speaks, agent responds.
 */
public record Turn(
        Long id,
        UUID sessionId,
        String speaker,  // "customer" | "agent"
        String transcript,
        String audioUrl,
        int durationMs,
        Instant occurredAt
) {}
