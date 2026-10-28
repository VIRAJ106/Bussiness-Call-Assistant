package com.dayclose.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Shared Jackson ObjectMapper configuration for event serialization.
 *
 * <p>All services must use this mapper for event payloads to ensure consistent
 * date formatting (ISO-8601, §4.4) and forward-compatible deserialization
 * (unknown properties are ignored, per the additive-only rule).
 */
public final class EventSerializer {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private EventSerializer() {
    }

    public static String serialize(DayCloseEvent event) {
        try {
            return MAPPER.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new EventSerializationException("Failed to serialize event: " + event.eventId(), e);
        }
    }

    public static DayCloseEvent deserialize(String json) {
        try {
            return MAPPER.readValue(json, DayCloseEvent.class);
        } catch (JsonProcessingException e) {
            throw new EventSerializationException("Failed to deserialize event", e);
        }
    }

    public static <T> T deserializePayload(DayCloseEvent event, Class<T> payloadType) {
        try {
            return MAPPER.convertValue(event.payload(), payloadType);
        } catch (IllegalArgumentException e) {
            throw new EventSerializationException(
                    "Failed to deserialize payload for event: " + event.eventId(), e);
        }
    }

    public static ObjectMapper objectMapper() {
        return MAPPER.copy();
    }

    public static class EventSerializationException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public EventSerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
