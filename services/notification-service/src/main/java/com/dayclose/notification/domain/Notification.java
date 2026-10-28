package com.dayclose.notification.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A notification to be sent to a customer or owner.
 */
public record Notification(
        Long id,
        UUID tenantId,
        String recipient,
        NotificationChannel channel,
        String templateId,
        String templateData,
        String messageBody,
        NotificationStatus status,
        int priority,
        Instant scheduledAt,
        Instant sentAt,
        String failedReason,
        int retryCount,
        int maxRetries
) {}
