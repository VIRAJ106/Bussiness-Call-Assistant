package com.dayclose.voice.domain;

/**
 * Call session states.
 */
public enum CallState {
    INITIATED,
    ACTIVE,
    TOOL_EXECUTING,
    COMPLETED,
    FAILED,
    ABANDONED;

    public String toDbValue() {
        return name().toLowerCase();
    }

    public static CallState fromString(String value) {
        return valueOf(value.toUpperCase());
    }
}
