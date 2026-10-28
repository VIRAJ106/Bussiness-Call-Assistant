package com.dayclose.inventory.service;

import com.dayclose.inventory.domain.Reservation;
import com.dayclose.inventory.infra.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Business logic for reservations with event publishing.
 * Wraps repository calls with transactional event publishing to outbox.
 */
@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final ReservationRepository reservationRepository;
    private final EventPublisher eventPublisher;

    public ReservationService(ReservationRepository reservationRepository, EventPublisher eventPublisher) {
        this.reservationRepository = reservationRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Reserve stock and publish event.
     */
    @Transactional
    public UUID reserveStock(
            UUID tenantId,
            UUID commitmentId,
            UUID itemId,
            UUID locationId,
            int qty,
            String ttlInterval,
            String customerRef) {
        
        // Call database function to reserve stock
        UUID reservationId = reservationRepository.reserveStock(
                tenantId, commitmentId, itemId, locationId, qty, ttlInterval, customerRef
        );

        // Load reservation to get expiry time for event
        Reservation reservation = reservationRepository.findById(tenantId, reservationId)
                .orElseThrow(() -> new IllegalStateException("Reservation was not created"));

        // Publish event to outbox (same transaction)
        eventPublisher.publishReservationCreated(
                tenantId, reservationId, commitmentId, itemId, locationId, qty, customerRef,
                reservation.expiresAt()
        );

        log.info("Reserved stock: reservation {} for commitment {} (item {}, location {}, qty {})",
                reservationId, commitmentId, itemId, locationId, qty);

        return reservationId;
    }

    /**
     * Commit a reservation and publish event.
     */
    @Transactional
    public void commitReservation(UUID tenantId, UUID reservationId, String actor) {
        // Load reservation to get details for event
        Reservation reservation = reservationRepository.findById(tenantId, reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + reservationId));

        // Commit via database function
        reservationRepository.commitReservation(tenantId, reservationId, actor);

        // Publish event to outbox (same transaction)
        eventPublisher.publishReservationCommitted(
                tenantId,
                reservationId,
                reservation.commitmentId(),
                reservation.itemId(),
                reservation.locationId(),
                reservation.qty()
        );

        log.info("Committed reservation {} by actor {}", reservationId, actor);
    }

    /**
     * Release a reservation and publish event.
     */
    @Transactional
    public void releaseReservation(UUID tenantId, UUID reservationId, String actor) {
        // Load reservation to get details for event
        Reservation reservation = reservationRepository.findById(tenantId, reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + reservationId));

        // Release via database function
        reservationRepository.releaseReservation(tenantId, reservationId, actor);

        // Publish event to outbox (same transaction)
        eventPublisher.publishReservationReleased(
                tenantId,
                reservationId,
                reservation.commitmentId(),
                reservation.itemId(),
                reservation.locationId(),
                reservation.qty(),
                actor != null ? actor : "system"
        );

        log.info("Released reservation {} by actor {}", reservationId, actor);
    }
}
