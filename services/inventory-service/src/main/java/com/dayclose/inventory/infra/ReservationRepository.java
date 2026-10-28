package com.dayclose.inventory.infra;

import com.dayclose.inventory.domain.Reservation;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ReservationRepository {

    private final JdbcClient jdbcClient;

    public ReservationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public UUID reserveStock(UUID tenantId, UUID commitmentId, UUID itemId, UUID locationId, int qty, String ttlInterval, String customerRef) {
        String ttl = (ttlInterval != null && !ttlInterval.isBlank()) ? ttlInterval : "30 minutes";
        return (UUID) jdbcClient.sql("""
                SELECT reserve_stock(
                    :tenant_id,
                    :commitment_id,
                    :item_id,
                    :location_id,
                    :qty,
                    CAST(:ttl AS interval),
                    :customer_ref
                )
                """)
                .param("tenant_id", tenantId)
                .param("commitment_id", commitmentId)
                .param("item_id", itemId)
                .param("location_id", locationId)
                .param("qty", qty)
                .param("ttl", ttl)
                .param("customer_ref", customerRef)
                .query()
                .singleValue();
    }

    public String reserveStockLines(UUID tenantId, UUID commitmentId, String customerRef, String linesJson, String ttlInterval) {
        String ttl = (ttlInterval != null && !ttlInterval.isBlank()) ? ttlInterval : "30 minutes";
        return (String) jdbcClient.sql("""
                SELECT reserve_stock_lines(
                    :tenant_id,
                    :commitment_id,
                    :customer_ref,
                    CAST(:lines AS jsonb),
                    CAST(:ttl AS interval)
                )::text
                """)
                .param("tenant_id", tenantId)
                .param("commitment_id", commitmentId)
                .param("customer_ref", customerRef)
                .param("lines", linesJson)
                .param("ttl", ttl)
                .query()
                .singleValue();
    }

    public void commitReservation(UUID tenantId, UUID reservationId, String actor) {
        jdbcClient.sql("SELECT commit_reservation(:tenant_id, :reservation_id, :actor)")
                .param("tenant_id", tenantId)
                .param("reservation_id", reservationId)
                .param("actor", actor != null ? actor : "system")
                .query()
                .singleValue();
    }

    public void releaseReservation(UUID tenantId, UUID reservationId, String actor) {
        jdbcClient.sql("SELECT release_reservation(:tenant_id, :reservation_id, :actor)")
                .param("tenant_id", tenantId)
                .param("reservation_id", reservationId)
                .param("actor", actor != null ? actor : "system")
                .query()
                .singleValue();
    }

    public Instant extendReservation(UUID tenantId, UUID reservationId, String extensionInterval) {
        String ext = (extensionInterval != null && !extensionInterval.isBlank()) ? extensionInterval : "30 minutes";
        Timestamp ts = (Timestamp) jdbcClient.sql("SELECT extend_reservation(:tenant_id, :reservation_id, CAST(:ext AS interval))")
                .param("tenant_id", tenantId)
                .param("reservation_id", reservationId)
                .param("ext", ext)
                .query()
                .singleValue();
        return ts.toInstant();
    }

    public int releaseExpiredAll(int batchSize) {
        Integer count = (Integer) jdbcClient.sql("SELECT release_expired_all(:batch_size)")
                .param("batch_size", batchSize)
                .query()
                .singleValue();
        return count != null ? count : 0;
    }

    public Optional<Reservation> findById(UUID tenantId, UUID id) {
        return jdbcClient.sql("""
                SELECT id, tenant_id, commitment_id, item_id, location_id, qty, customer_ref, state, expires_at
                FROM reservations WHERE id = :id AND tenant_id = :tenant_id
                """)
                .param("id", id)
                .param("tenant_id", tenantId)
                .query((rs, rowNum) -> new Reservation(
                        (UUID) rs.getObject("id"),
                        (UUID) rs.getObject("tenant_id"),
                        (UUID) rs.getObject("commitment_id"),
                        (UUID) rs.getObject("item_id"),
                        (UUID) rs.getObject("location_id"),
                        rs.getInt("qty"),
                        rs.getString("customer_ref"),
                        rs.getString("state"),
                        rs.getTimestamp("expires_at").toInstant()
                ))
                .optional();
    }

    public List<Reservation> list(UUID tenantId, UUID commitmentId, String state) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, tenant_id, commitment_id, item_id, location_id, qty, customer_ref, state, expires_at
                FROM reservations WHERE tenant_id = :tenant_id
                """);

        if (commitmentId != null) sql.append(" AND commitment_id = :commitment_id");
        if (state != null) sql.append(" AND state = :state");
        sql.append(" ORDER BY expires_at ASC");

        var query = jdbcClient.sql(sql.toString()).param("tenant_id", tenantId);
        if (commitmentId != null) query = query.param("commitment_id", commitmentId);
        if (state != null) query = query.param("state", state);

        return query.query((rs, rowNum) -> new Reservation(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("tenant_id"),
                (UUID) rs.getObject("commitment_id"),
                (UUID) rs.getObject("item_id"),
                (UUID) rs.getObject("location_id"),
                rs.getInt("qty"),
                rs.getString("customer_ref"),
                rs.getString("state"),
                rs.getTimestamp("expires_at").toInstant()
        )).list();
    }
}
