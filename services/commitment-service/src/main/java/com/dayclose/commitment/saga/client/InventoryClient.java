package com.dayclose.commitment.saga.client;

import com.dayclose.commitment.saga.dto.CommitReservationRequest;
import com.dayclose.commitment.saga.dto.ReleaseReservationRequest;
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

import java.util.UUID;

/**
 * HTTP client for inventory-service.
 * Uses circuit breaker and retry for resilience (ADR-0003).
 */
@Component
public class InventoryClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryClient.class);

    private final RestTemplate restTemplate;
    private final String inventoryServiceUrl;

    public InventoryClient(
            RestTemplate restTemplate,
            @Value("${services.inventory.url}") String inventoryServiceUrl) {
        this.restTemplate = restTemplate;
        this.inventoryServiceUrl = inventoryServiceUrl;
    }

    /**
     * Commits a reservation (deducts stock permanently).
     */
    @CircuitBreaker(name = "inventory-service", fallbackMethod = "commitFallback")
    @Retry(name = "inventory-service")
    public void commitReservation(UUID tenantId, UUID reservationId, String actor, String reason) {
        String url = inventoryServiceUrl + "/v1/reservations/" + reservationId + "/commit";

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-ID", tenantId.toString());
        headers.set("Content-Type", "application/json");

        CommitReservationRequest request = new CommitReservationRequest(reservationId, actor, reason);
        HttpEntity<CommitReservationRequest> entity = new HttpEntity<>(request, headers);

        log.info("Committing reservation {} for tenant {}", reservationId, tenantId);
        restTemplate.exchange(url, HttpMethod.POST, entity, Void.class);
        log.info("Successfully committed reservation {}", reservationId);
    }

    /**
     * Releases a reservation (returns stock to available).
     */
    @CircuitBreaker(name = "inventory-service", fallbackMethod = "releaseFallback")
    @Retry(name = "inventory-service")
    public void releaseReservation(UUID tenantId, UUID reservationId, String actor, String reason) {
        String url = inventoryServiceUrl + "/v1/reservations/" + reservationId + "/release";

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-ID", tenantId.toString());
        headers.set("Content-Type", "application/json");

        ReleaseReservationRequest request = new ReleaseReservationRequest(reservationId, actor, reason);
        HttpEntity<ReleaseReservationRequest> entity = new HttpEntity<>(request, headers);

        log.info("Releasing reservation {} for tenant {}", reservationId, tenantId);
        restTemplate.exchange(url, HttpMethod.POST, entity, Void.class);
        log.info("Successfully released reservation {}", reservationId);
    }

    // Circuit breaker fallbacks
    private void commitFallback(UUID tenantId, UUID reservationId, String actor, String reason, Throwable t) {
        log.error("Circuit breaker fallback triggered for commit reservation {}: {}", reservationId, t.getMessage());
        throw new SagaException("Inventory service unavailable - cannot commit reservation", t);
    }

    private void releaseFallback(UUID tenantId, UUID reservationId, String actor, String reason, Throwable t) {
        log.error("Circuit breaker fallback triggered for release reservation {}: {}", reservationId, t.getMessage());
        throw new SagaException("Inventory service unavailable - cannot release reservation", t);
    }
}
