package com.dayclose.notification.service;

import com.dayclose.notification.domain.NotificationChannel;
import com.dayclose.notification.infra.NotificationRepository;
import com.dayclose.notification.infra.TemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Service for creating and scheduling notifications.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final TemplateRepository templateRepository;
    private final TemplateRenderer templateRenderer;
    private final ObjectMapper objectMapper;

    public NotificationService(NotificationRepository notificationRepository,
                              TemplateRepository templateRepository,
                              TemplateRenderer templateRenderer,
                              ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.templateRepository = templateRepository;
        this.templateRenderer = templateRenderer;
        this.objectMapper = objectMapper;
    }

    /**
     * Send a notification using a template.
     */
    public Long sendNotification(UUID tenantId, String recipient, NotificationChannel channel,
                                String templateId, Map<String, Object> data) {
        return sendNotification(tenantId, recipient, channel, templateId, data, 5, Instant.now());
    }

    /**
     * Send a notification with priority and scheduling.
     */
    public Long sendNotification(UUID tenantId, String recipient, NotificationChannel channel,
                                String templateId, Map<String, Object> data,
                                int priority, Instant scheduledAt) {
        // Get template
        String templateBody = templateRepository.getTemplate(tenantId, templateId, channel);
        if (templateBody == null) {
            throw new IllegalArgumentException("Template not found: " + templateId);
        }

        // Render template with data
        String messageBody = templateRenderer.render(templateBody, data);

        // Convert data to JSON
        String templateDataJson;
        try {
            templateDataJson = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize template data", e);
        }

        // Create notification
        return notificationRepository.create(
                tenantId,
                recipient,
                channel,
                templateId,
                templateDataJson,
                messageBody,
                priority,
                scheduledAt
        );
    }
}
