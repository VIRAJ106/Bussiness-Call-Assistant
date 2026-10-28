package com.dayclose.inventory.service;

import com.dayclose.events.DayCloseEvent;
import com.dayclose.events.EventSerializer;
import com.dayclose.events.EventTypes;
import com.dayclose.events.inventory.ReservationCommittedPayload;
import com.dayclose.events.inventory.ReservationCreatedPayload;
import com.dayclose.events.inventory.ReservationReleasedPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes events to the outbox for eventual publishing to RabbitMQ.
 * This ensures transactional event publishing (ADR-0003).
 */
@Service
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);
    private static final String PRODUCER = "inventory-service";

    private final JdbcClient jdbcClient;

    public EventPublisher(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Publishes a reservation created event.
     */
    public void publishReservationCreated(
            UUID tenantId,
            UUID reservationId,
            UUID commitmentId,
            UUID itemId,
            UUID locationId,
            int qty,
            String customerRef,
            Instant expiresAt) {
        
        ReservationCreatedPayload payload = new ReservationCreatedPayload(
                reservationId, commitmentId, itemId, locationId, qty, customerRef, expiresAt
        );

        DayCloseEvent event = DayCloseEvent.create(
                EventTypes.RESERVATION_CREATED,
                1,  // version
                tenantId,
                commitmentId,  // correlationId
                reservationId,  // causationId
                PRODUCER,
                payload
        );

        publishToOutbox(event);
    }

    /**
     * Publishes a reservation committed event.
     */
    public void publishReservationCommitted(
            UUID tenantId,
            UUID reservationId,
            UUID commitmentId,
            UUID itemId,
            UUID locationId,
            int qty) {
        
        ReservationCommittedPayload payload = new ReservationCommittedPayload(
                reservationId, commitmentId, itemId, locationId, qty
        );

        DayCloseEvent event = DayCloseEvent.create(
                EventTypes.RESERVATION_COMMITTED,
                1,  // version
                tenantId,
                commitmentId,  // correlationId
                reservationId,  // causationId
                PRODUCER,
                payload
        );

        publishToOutbox(event);
    }

    /**
     * Publishes a reservation released event.
     */
    public void publishReservationReleased(
            UUID tenantId,
            UUID reservationId,
            UUID commitmentId,
            UUID itemId,
            UUID locationId,
            int qty,
            String reason) {
        
        ReservationReleasedPayload payload = new ReservationReleasedPayload(
                reservationId, commitmentId, itemId, locationId, qty, reason
        );

        DayCloseEvent event = DayCloseEvent.create(
                EventTypes.RESERVATION_RELEASED,
                1,  // version
                tenantId,
                commitmentId,  // correlationId
                reservationId,  // causationId
                PRODUCER,
                payload
        );

        publishToOutbox(event);
    }

    /**
     * Inserts event into outbox table.
     * Outbox worker will pick it up and publish to RabbitMQ.
     */
    private void publishToOutbox(DayCloseEvent event) {
        try {
            String payload = EventSerializer.serialize(event);

            jdbcClient.sql("""
                    INSERT INTO outbox (id, event_type, tenant_id, aggregate_id, payload, occurred_at)
                    VALUES (:id, :event_type, :tenant_id, :aggregate_id, CAST(:payload AS jsonb), :occurred_at)
                    """)
                    .param("id", event.eventId())
                    .param("event_type", event.type())
                    .param("tenant_id", event.tenantId())
                    .param("aggregate_id", event.causationId())  // using causationId as aggregate_id
                    .param("payload", payload)
                    .param("occurred_at", event.occurredAt())
                    .update();

            log.info("Published event {} to outbox: {}", event.type(), event.eventId());

        } catch (Exception e) {
            log.error("Failed to publish event to outbox: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to publish event", e);
        }
    }
}
