package com.dayclose.notification.infra;

import com.dayclose.notification.domain.Notification;
import com.dayclose.notification.domain.NotificationChannel;
import com.dayclose.notification.domain.NotificationStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class NotificationRepository {

    private final JdbcClient jdbcClient;

    public NotificationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Long create(UUID tenantId, String recipient, NotificationChannel channel,
                      String templateId, String templateData, String messageBody,
                      int priority, Instant scheduledAt) {
        return jdbcClient.sql("""
                INSERT INTO notifications (tenant_id, recipient, channel, template_id, 
                                          template_data, message_body, priority, scheduled_at)
                VALUES (:tenant_id, :recipient, :channel, :template_id, 
                        CAST(:template_data AS jsonb), :message_body, :priority, :scheduled_at)
                RETURNING id
                """)
                .param("tenant_id", tenantId)
                .param("recipient", recipient)
                .param("channel", channel.toDbValue())
                .param("template_id", templateId)
                .param("template_data", templateData)
                .param("message_body", messageBody)
                .param("priority", priority)
                .param("scheduled_at", scheduledAt)
                .query(Long.class)
                .single();
    }

    public List<Notification> fetchPending(int limit) {
        return jdbcClient.sql("""
                SELECT id, tenant_id, recipient, channel, template_id, template_data, 
                       message_body, status, priority, scheduled_at, sent_at, 
                       failed_reason, retry_count, max_retries
                FROM notifications
                WHERE status = 'pending' AND scheduled_at <= now()
                ORDER BY priority DESC, scheduled_at ASC
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
                """)
                .param("limit", limit)
                .query((rs, rowNum) -> new Notification(
                        rs.getLong("id"),
                        (UUID) rs.getObject("tenant_id"),
                        rs.getString("recipient"),
                        NotificationChannel.fromString(rs.getString("channel")),
                        rs.getString("template_id"),
                        rs.getString("template_data"),
                        rs.getString("message_body"),
                        NotificationStatus.fromString(rs.getString("status")),
                        rs.getInt("priority"),
                        rs.getTimestamp("scheduled_at").toInstant(),
                        rs.getTimestamp("sent_at") != null ? rs.getTimestamp("sent_at").toInstant() : null,
                        rs.getString("failed_reason"),
                        rs.getInt("retry_count"),
                        rs.getInt("max_retries")
                ))
                .list();
    }

    public void updateStatus(Long id, NotificationStatus status, String failedReason) {
        jdbcClient.sql("""
                UPDATE notifications 
                SET status = :status,
                    sent_at = CASE WHEN :status = 'sent' THEN now() ELSE sent_at END,
                    failed_reason = :failed_reason
                WHERE id = :id
                """)
                .param("id", id)
                .param("status", status.toDbValue())
                .param("failed_reason", failedReason)
                .update();
    }

    public void incrementRetry(Long id) {
        jdbcClient.sql("""
                UPDATE notifications 
                SET retry_count = retry_count + 1,
                    status = CASE 
                        WHEN retry_count + 1 >= max_retries THEN 'failed'
                        ELSE 'pending'
                    END
                WHERE id = :id
                """)
                .param("id", id)
                .update();
    }
}
