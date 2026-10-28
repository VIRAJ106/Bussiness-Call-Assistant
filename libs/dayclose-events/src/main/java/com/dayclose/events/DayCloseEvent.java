package com.dayclose.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * Standard event envelope for all cross-service events (ADR-0006, §4.5).
 *
 * <p>Every event published to RabbitMQ uses this envelope. Payloads are additive-only
 * within a version; a breaking change publishes a new {@code version} alongside the old
 * for one release.
 *
 * @param eventId       unique id for deduplication (inbox table)
 * @param type          dot-separated event type, e.g. "inventory.reservation.created"
 * @param version       schema version of the payload (starts at 1)
 * @param occurredAt    when the domain event happened (UTC)
 * @param tenantId      owning tenant (from the verified JWT, never user-supplied)
 * @param correlationId links all events in a single user-initiated workflow
 * @param causationId   the event that directly caused this one
 * @param producer      service name that published the event
 * @param payload       event-specific data (type depends on {@code type} and {@code version})
 */
public record DayCloseEvent(
        @JsonProperty("event_id") UUID eventId,
        @JsonProperty("type") String type,
        @JsonProperty("version") int version,
        @JsonProperty("occurred_at") Instant occurredAt,
        @JsonProperty("tenant_id") UUID tenantId,
        @JsonProperty("correlation_id") UUID correlationId,
        @JsonProperty("causation_id") UUID causationId,
        @JsonProperty("producer") String producer,
        @JsonProperty("payload") Object payload
) {

    /**
     * Creates a new event with a generated id and current timestamp.
     */
    public static DayCloseEvent create(
            String type, int version, UUID tenantId,
            UUID correlationId, UUID causationId,
            String producer, Object payload) {
        return new DayCloseEvent(
                UUID.randomUUID(), type, version, Instant.now(),
                tenantId, correlationId, causationId, producer, payload);
    }
}
