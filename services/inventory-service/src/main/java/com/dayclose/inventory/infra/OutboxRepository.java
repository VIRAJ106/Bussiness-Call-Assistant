package com.dayclose.inventory.infra;

import com.dayclose.events.DayCloseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class OutboxRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public OutboxRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    public record OutboxRecord(
            long id,
            UUID eventId,
            String eventType,
            int version,
            UUID tenantId,
            UUID correlationId,
            UUID causationId,
            String payloadJson,
            Instant occurredAt
    ) {}

    public void save(DayCloseEvent event) {
        try {
            String payloadJson = objectMapper.writeValueAsString(event.payload());
            jdbcClient.sql("""
                    INSERT INTO outbox (event_id, event_type, version, tenant_id, correlation_id, causation_id, payload, occurred_at)
                    VALUES (:event_id, :event_type, :version, :tenant_id, :correlation_id, :causation_id, CAST(:payload AS jsonb), :occurred_at)
                    """)
                    .param("event_id", event.eventId())
                    .param("event_type", event.type())
                    .param("version", event.version())
                    .param("tenant_id", event.tenantId())
                    .param("correlation_id", event.correlationId())
                    .param("causation_id", event.causationId())
                    .param("payload", payloadJson)
                    .param("occurred_at", Timestamp.from(event.occurredAt()))
                    .update();
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize event payload to JSON", e);
        }
    }

    public List<OutboxRecord> fetchUnpublished(int limit) {
        return jdbcClient.sql("""
                SELECT id, event_id, event_type, version, tenant_id, correlation_id, causation_id, payload::text AS payload_json, occurred_at
                FROM outbox
                WHERE NOT published
                ORDER BY id ASC
                LIMIT :limit
                """)
                .param("limit", limit)
                .query((rs, rowNum) -> new OutboxRecord(
                        rs.getLong("id"),
                        (UUID) rs.getObject("event_id"),
                        rs.getString("event_type"),
                        rs.getInt("version"),
                        (UUID) rs.getObject("tenant_id"),
                        (UUID) rs.getObject("correlation_id"),
                        (UUID) rs.getObject("causation_id"),
                        rs.getString("payload_json"),
                        rs.getTimestamp("occurred_at").toInstant()
                ))
                .list();
    }

    public void markPublished(long id) {
        jdbcClient.sql("UPDATE outbox SET published = true, published_at = now() WHERE id = :id")
                .param("id", id)
                .update();
    }

    public void incrementRetry(long id) {
        jdbcClient.sql("UPDATE outbox SET retry_count = retry_count + 1 WHERE id = :id")
                .param("id", id)
                .update();
    }
}
