package com.dayclose.voice.domain;

import java.time.Instant;

/**
 * Record of a tool call made by the agent during a session.
 */
public record ToolCall(
        Long id,
        java.util.UUID sessionId,
        String toolName,
        String arguments,
        String result,
        String status,  // "success" | "failed"
        int durationMs,
        Instant occurredAt
) {}
