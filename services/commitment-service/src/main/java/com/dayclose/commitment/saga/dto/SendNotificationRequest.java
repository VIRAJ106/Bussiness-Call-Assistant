package com.dayclose.commitment.saga.dto;

import java.util.Map;
import java.util.UUID;

/**
 * Request to send a notification via notification-service.
 */
public record SendNotificationRequest(
        UUID tenantId,
        String recipient,
        String channel,  // "sms" or "email"
        String templateCode,
        Map<String, Object> data
) {}
