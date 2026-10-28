package com.dayclose.inventory.api;

import com.dayclose.inventory.api.dto.*;
import com.dayclose.inventory.domain.Reservation;
import com.dayclose.inventory.infra.ReservationRepository;
import com.dayclose.inventory.service.ReservationService;
import com.dayclose.web.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API for stock reservations (ADR-0005).
 * Reservations hold stock temporarily pending owner approval (Tier 1 → Tier 2).
 */
@RestController
@RequestMapping("/v1/reservations")
public class ReservationController {

    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;
    private final ObjectMapper objectMapper;

    public ReservationController(
            ReservationRepository reservationRepository,
            ReservationService reservationService,
            ObjectMapper objectMapper) {
        this.reservationRepository = reservationRepository;
        this.reservationService = reservationService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a single-line reservation.
     * Idempotent: same commitmentId + itemId + locationId returns the existing reservation.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createReservation(@Valid @RequestBody CreateReservationRequest request) {
        UUID tenantId = TenantContext.require();

        UUID reservationId = reservationService.reserveStock(
                tenantId,
                request.commitmentId(),
                request.itemId(),
                request.locationId(),
                request.qty(),
                request.ttl(),
                request.customerRef()
        );

        Map<String, Object> response = new HashMap<>();
        response.put("reservation_id", reservationId);
        response.put("commitment_id", request.commitmentId());
        response.put("item_id", request.itemId());
        response.put("location_id", request.locationId());
        response.put("qty", request.qty());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Create a multi-line reservation with deadlock prevention (ADR implementation plan §3, Finding 8).
     * Lines are processed in sorted order by (item_id, location_id).
     */
    @PostMapping("/multi")
    public ResponseEntity<JsonNode> createMultiLineReservation(@Valid @RequestBody CreateMultiLineReservationRequest request) {
        UUID tenantId = TenantContext.require();

        // Convert lines to JSON array
        List<Map<String, Object>> linesData = request.lines().stream()
                .map(line -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("item_id", line.itemId().toString());
                    m.put("location_id", line.locationId().toString());
                    m.put("qty", line.qty());
                    return m;
                })
                .toList();

        String linesJson;
        try {
            linesJson = objectMapper.writeValueAsString(linesData);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize lines: " + e.getMessage());
        }

        String resultJson = reservationRepository.reserveStockLines(
                tenantId,
                request.commitmentId(),
                request.customerRef(),
                linesJson,
                request.ttl()
        );

        try {
            JsonNode result = objectMapper.readTree(resultJson);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse reservation result: " + e.getMessage());
        }
    }

    /**
     * Get a reservation by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Reservation> getReservation(@PathVariable UUID id) {
        UUID tenantId = TenantContext.require();

        return reservationRepository.findById(tenantId, id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * List reservations with optional filters.
     */
    @GetMapping
    public ResponseEntity<List<Reservation>> listReservations(
            @RequestParam(required = false) UUID commitmentId,
            @RequestParam(required = false) String state) {
        UUID tenantId = TenantContext.require();

        List<Reservation> reservations = reservationRepository.list(tenantId, commitmentId, state);
        return ResponseEntity.ok(reservations);
    }

    /**
     * Commit a reservation (owner approved, stock leaves).
     * ADR-0005: commits are re-validated (expiry check happens in DB function).
     */
    @PostMapping("/{id}/commit")
    public ResponseEntity<Void> commitReservation(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ReservationActionRequest request) {
        UUID tenantId = TenantContext.require();

        reservationService.commitReservation(
                tenantId,
                id,
                request != null ? request.actor() : null
        );

        return ResponseEntity.noContent().build();
    }

    /**
     * Release a reservation (owner rejected or compensation).
     */
    @PostMapping("/{id}/release")
    public ResponseEntity<Void> releaseReservation(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ReservationActionRequest request) {
        UUID tenantId = TenantContext.require();

        reservationService.releaseReservation(
                tenantId,
                id,
                request != null ? request.actor() : null
        );

        return ResponseEntity.noContent().build();
    }

    /**
     * Extend a reservation's expiry (owner needs more time to decide).
     */
    @PostMapping("/{id}/extend")
    public ResponseEntity<Map<String, Instant>> extendReservation(
            @PathVariable UUID id,
            @Valid @RequestBody ExtendReservationRequest request) {
        UUID tenantId = TenantContext.require();

        Instant newExpiry = reservationRepository.extendReservation(
                tenantId,
                id,
                request.extension()
        );

        Map<String, Instant> response = new HashMap<>();
        response.put("expires_at", newExpiry);

        return ResponseEntity.ok(response);
    }
}
