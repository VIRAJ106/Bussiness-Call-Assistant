package com.dayclose.voice.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A voice call session with a customer or supplier.
 */
public record CallSession(
        UUID id,
        UUID tenantId,
        String callerId,
        String direction,  // "inbound" | "outbound"
        CallState state,
        String language,
        Instant startedAt,
        Instant endedAt,
        int durationSeconds,
        String transcriptSummary
) {
    public boolean isActive() {
        return state == CallState.ACTIVE || state == CallState.TOOL_EXECUTING;
    }

    public boolean isTerminated() {
        return state == CallState.COMPLETED || 
               state == CallState.FAILED || 
               state == CallState.ABANDONED;
    }
}
