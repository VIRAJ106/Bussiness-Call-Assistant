package com.dayclose.inventory.infra;

import com.dayclose.inventory.domain.StockLevel;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class StockRepository {

    private final JdbcClient jdbcClient;

    public StockRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void receiveStock(UUID tenantId, UUID itemId, UUID locationId, int qty, String actor) {
        jdbcClient.sql("SELECT receive_stock(:tenant_id, :item_id, :location_id, :qty, :actor)")
                .param("tenant_id", tenantId)
                .param("item_id", itemId)
                .param("location_id", locationId)
                .param("qty", qty)
                .param("actor", actor != null ? actor : "system")
                .query()
                .singleValue();
    }

    public void adjustStock(UUID tenantId, UUID itemId, UUID locationId, int delta, String actor, String reasonNote) {
        jdbcClient.sql("SELECT adjust_stock(:tenant_id, :item_id, :location_id, :delta, :actor, :reason_note)")
                .param("tenant_id", tenantId)
                .param("item_id", itemId)
                .param("location_id", locationId)
                .param("delta", delta)
                .param("actor", actor != null ? actor : "system")
                .param("reason_note", reasonNote)
                .query()
                .singleValue();
    }

    public Optional<StockLevel> getStockLevel(UUID tenantId, UUID itemId, UUID locationId) {
        return jdbcClient.sql("""
                SELECT item_id, location_id, tenant_id, on_hand, reserved
                FROM stock_levels
                WHERE item_id = :item_id AND location_id = :location_id AND tenant_id = :tenant_id
                """)
                .param("item_id", itemId)
                .param("location_id", locationId)
                .param("tenant_id", tenantId)
                .query((rs, rowNum) -> new StockLevel(
                        (UUID) rs.getObject("item_id"),
                        (UUID) rs.getObject("location_id"),
                        (UUID) rs.getObject("tenant_id"),
                        rs.getInt("on_hand"),
                        rs.getInt("reserved")
                ))
                .optional();
    }

    public List<StockLevel> listStock(UUID tenantId, UUID itemId, UUID locationId) {
        StringBuilder sql = new StringBuilder("SELECT item_id, location_id, tenant_id, on_hand, reserved FROM stock_levels WHERE tenant_id = :tenant_id");
        var spec = jdbcClient.sql("");

        if (itemId != null) {
            sql.append(" AND item_id = :item_id");
        }
        if (locationId != null) {
            sql.append(" AND location_id = :location_id");
        }

        var query = jdbcClient.sql(sql.toString()).param("tenant_id", tenantId);
        if (itemId != null) query = query.param("item_id", itemId);
        if (locationId != null) query = query.param("location_id", locationId);

        return query.query((rs, rowNum) -> new StockLevel(
                (UUID) rs.getObject("item_id"),
                (UUID) rs.getObject("location_id"),
                (UUID) rs.getObject("tenant_id"),
                rs.getInt("on_hand"),
                rs.getInt("reserved")
        )).list();
    }
}
