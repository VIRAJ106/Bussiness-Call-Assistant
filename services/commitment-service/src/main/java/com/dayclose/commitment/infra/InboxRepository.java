package com.dayclose.commitment.infra;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Inbox for idempotent event handling (ADR-0006).
 */
@Repository
public class InboxRepository {

    private final JdbcClient jdbcClient;

    public InboxRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Mark an event as processed. Returns false if already processed (duplicate).
     */
    public boolean markProcessed(UUID eventId, String eventType, UUID tenantId) {
        try {
            jdbcClient.sql("""
                    INSERT INTO inbox (event_id, event_type, tenant_id)
                    VALUES (:event_id, :event_type, :tenant_id)
                    """)
                    .param("event_id", eventId)
                    .param("event_type", eventType)
                    .param("tenant_id", tenantId)
                    .update();
            return true;
        } catch (DuplicateKeyException e) {
            return false; // Already processed
        }
    }

    /**
     * Check if an event has been processed.
     */
    public boolean isProcessed(UUID eventId) {
        Integer count = jdbcClient.sql("SELECT COUNT(*) FROM inbox WHERE event_id = :event_id")
                .param("event_id", eventId)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }
}
