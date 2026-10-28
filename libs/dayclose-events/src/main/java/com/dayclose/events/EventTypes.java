package com.dayclose.events;

/**
 * Constants for all event type strings used across services (ADR-0006).
 *
 * <p>Event types follow the pattern: {@code service.entity.action}.
 * Using constants prevents typos in routing keys and consumer bindings.
 */
public final class EventTypes {

    private EventTypes() {
    }

    // ── inventory-service events ──────────────────────────────────────────
    public static final String RESERVATION_CREATED   = "inventory.reservation.created";
    public static final String RESERVATION_COMMITTED  = "inventory.reservation.committed";
    public static final String RESERVATION_RELEASED   = "inventory.reservation.released";
    public static final String RESERVATION_EXPIRED    = "inventory.reservation.expired";
    public static final String STOCK_RECEIVED         = "inventory.stock.received";
    public static final String STOCK_ADJUSTED         = "inventory.stock.adjusted";
    public static final String STOCK_LOW              = "inventory.stock.low";

    // ── commitment-service events ─────────────────────────────────────────
    public static final String COMMITMENT_CREATED          = "commitment.created";
    public static final String COMMITMENT_PENDING_APPROVAL = "commitment.pending_approval";
    public static final String COMMITMENT_APPROVED         = "commitment.approved";
    public static final String COMMITMENT_REJECTED         = "commitment.rejected";
    public static final String COMMITMENT_COMMITTED        = "commitment.committed";
    public static final String COMMITMENT_FAILED           = "commitment.failed";

    // ── knowledge-service events ──────────────────────────────────────────
    public static final String CATALOG_SYNCED = "knowledge.catalog.synced";

    // ── notification-service events ───────────────────────────────────────
    public static final String NOTIFICATION_SENT   = "notification.sent";
    public static final String NOTIFICATION_FAILED = "notification.failed";

    // ── Exchange name ─────────────────────────────────────────────────────
    public static final String EXCHANGE = "dayclose.events";
}
