package com.dayclose.commitment.saga.client;

import com.dayclose.commitment.saga.dto.SendNotificationRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * HTTP client for notification-service.
 * Uses circuit breaker and retry for resilience.
 */
@Component
public class NotificationClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);

    private final RestTemplate restTemplate;
    private final String notificationServiceUrl;

    public NotificationClient(
            RestTemplate restTemplate,
            @Value("${services.notification.url}") String notificationServiceUrl) {
        this.restTemplate = restTemplate;
        this.notificationServiceUrl = notificationServiceUrl;
    }

    /**
     * Sends a notification (queues for async delivery).
     */
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendFallback")
    @Retry(name = "notification-service")
    public void sendNotification(UUID tenantId, String recipient, String channel, String templateCode, Map<String, Object> data) {
        String url = notificationServiceUrl + "/v1/notifications";

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-ID", tenantId.toString());
        headers.set("Content-Type", "application/json");

        SendNotificationRequest request = new SendNotificationRequest(tenantId, recipient, channel, templateCode, data);
        HttpEntity<SendNotificationRequest> entity = new HttpEntity<>(request, headers);

        log.info("Sending notification to {} via {} using template {}", recipient, channel, templateCode);
        restTemplate.exchange(url, HttpMethod.POST, entity, Void.class);
        log.info("Successfully queued notification to {}", recipient);
    }

    // Circuit breaker fallback - notification failures should not block saga
    private void sendFallback(UUID tenantId, String recipient, String channel, String templateCode, Map<String, Object> data, Throwable t) {
        log.warn("Failed to send notification to {} - will retry later: {}", recipient, t.getMessage());
        // Don't throw - notification is best-effort, saga should continue
    }
}
