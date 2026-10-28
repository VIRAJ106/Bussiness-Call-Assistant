package com.dayclose.notification.infra;

import com.dayclose.notification.domain.NotificationChannel;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class TemplateRepository {

    private final JdbcClient jdbcClient;

    public TemplateRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Get template body. Tries tenant-specific first, falls back to system template.
     */
    public String getTemplate(UUID tenantId, String templateId, NotificationChannel channel) {
        // Try tenant-specific template first
        String template = jdbcClient.sql("""
                SELECT body FROM templates 
                WHERE id = :template_id 
                  AND channel = :channel 
                  AND (tenant_id = :tenant_id OR tenant_id IS NULL)
                ORDER BY tenant_id DESC NULLS LAST
                LIMIT 1
                """)
                .param("template_id", templateId)
                .param("channel", channel.toDbValue())
                .param("tenant_id", tenantId)
                .query(String.class)
                .optional()
                .orElse(null);

        return template;
    }
}
