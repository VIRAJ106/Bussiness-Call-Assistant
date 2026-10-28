package com.dayclose.commitment.infra;

import com.dayclose.commitment.domain.CommitmentLine;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class CommitmentLineRepository {

    private final JdbcClient jdbcClient;

    public CommitmentLineRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void create(UUID commitmentId, UUID itemId, UUID locationId, UUID reservationId, int qty, int pricePaise) {
        jdbcClient.sql("""
                INSERT INTO commitment_lines (commitment_id, item_id, location_id, reservation_id, qty, price_paise)
                VALUES (:commitment_id, :item_id, :location_id, :reservation_id, :qty, :price_paise)
                """)
                .param("commitment_id", commitmentId)
                .param("item_id", itemId)
                .param("location_id", locationId)
                .param("reservation_id", reservationId)
                .param("qty", qty)
                .param("price_paise", pricePaise)
                .update();
    }

    public List<CommitmentLine> findByCommitment(UUID commitmentId) {
        return jdbcClient.sql("""
                SELECT id, commitment_id, item_id, location_id, reservation_id, qty, price_paise
                FROM commitment_lines WHERE commitment_id = :commitment_id
                """)
                .param("commitment_id", commitmentId)
                .query((rs, rowNum) -> new CommitmentLine(
                        rs.getLong("id"),
                        (UUID) rs.getObject("commitment_id"),
                        (UUID) rs.getObject("item_id"),
                        (UUID) rs.getObject("location_id"),
                        (UUID) rs.getObject("reservation_id"),
                        rs.getInt("qty"),
                        rs.getInt("price_paise")
                ))
                .list();
    }
}
