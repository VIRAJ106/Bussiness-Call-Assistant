package com.dayclose.inventory.infra;

import com.dayclose.inventory.domain.Item;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ItemRepository {

    private final JdbcClient jdbcClient;

    public ItemRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Item create(UUID tenantId, String sku, String name, String unit, int pricePaise, String currency, int reorderPoint, int reorderQty) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                INSERT INTO items (id, tenant_id, sku, name, unit, price_paise, currency, reorder_point, reorder_qty, active)
                VALUES (:id, :tenant_id, :sku, :name, :unit, :price_paise, :currency, :reorder_point, :reorder_qty, true)
                """)
                .param("id", id)
                .param("tenant_id", tenantId)
                .param("sku", sku)
                .param("name", name)
                .param("unit", unit != null ? unit : "pcs")
                .param("price_paise", pricePaise)
                .param("currency", currency != null ? currency : "INR")
                .param("reorder_point", reorderPoint)
                .param("reorder_qty", reorderQty)
                .update();

        return new Item(id, tenantId, sku, name, unit != null ? unit : "pcs", pricePaise, currency != null ? currency : "INR", reorderPoint, reorderQty, true);
    }

    public Optional<Item> findById(UUID tenantId, UUID id) {
        return jdbcClient.sql("""
                SELECT id, tenant_id, sku, name, unit, price_paise, currency, reorder_point, reorder_qty, active
                FROM items WHERE id = :id AND tenant_id = :tenant_id
                """)
                .param("id", id)
                .param("tenant_id", tenantId)
                .query((rs, rowNum) -> new Item(
                        (UUID) rs.getObject("id"),
                        (UUID) rs.getObject("tenant_id"),
                        rs.getString("sku"),
                        rs.getString("name"),
                        rs.getString("unit"),
                        rs.getInt("price_paise"),
                        rs.getString("currency"),
                        rs.getInt("reorder_point"),
                        rs.getInt("reorder_qty"),
                        rs.getBoolean("active")
                ))
                .optional();
    }

    public Optional<Item> findBySku(UUID tenantId, String sku) {
        return jdbcClient.sql("""
                SELECT id, tenant_id, sku, name, unit, price_paise, currency, reorder_point, reorder_qty, active
                FROM items WHERE sku = :sku AND tenant_id = :tenant_id
                """)
                .param("sku", sku)
                .param("tenant_id", tenantId)
                .query((rs, rowNum) -> new Item(
                        (UUID) rs.getObject("id"),
                        (UUID) rs.getObject("tenant_id"),
                        rs.getString("sku"),
                        rs.getString("name"),
                        rs.getString("unit"),
                        rs.getInt("price_paise"),
                        rs.getString("currency"),
                        rs.getInt("reorder_point"),
                        rs.getInt("reorder_qty"),
                        rs.getBoolean("active")
                ))
                .optional();
    }

    public List<Item> list(UUID tenantId, int limit) {
        return jdbcClient.sql("""
                SELECT id, tenant_id, sku, name, unit, price_paise, currency, reorder_point, reorder_qty, active
                FROM items WHERE tenant_id = :tenant_id
                ORDER BY name ASC LIMIT :limit
                """)
                .param("tenant_id", tenantId)
                .param("limit", limit)
                .query((rs, rowNum) -> new Item(
                        (UUID) rs.getObject("id"),
                        (UUID) rs.getObject("tenant_id"),
                        rs.getString("sku"),
                        rs.getString("name"),
                        rs.getString("unit"),
                        rs.getInt("price_paise"),
                        rs.getString("currency"),
                        rs.getInt("reorder_point"),
                        rs.getInt("reorder_qty"),
                        rs.getBoolean("active")
                ))
                .list();
    }

    public void update(Item item) {
        jdbcClient.sql("""
                UPDATE items
                   SET name = :name, unit = :unit, price_paise = :price_paise,
                       currency = :currency, reorder_point = :reorder_point,
                       reorder_qty = :reorder_qty, active = :active
                 WHERE id = :id AND tenant_id = :tenant_id
                """)
                .param("id", item.id())
                .param("tenant_id", item.tenantId())
                .param("name", item.name())
                .param("unit", item.unit())
                .param("price_paise", item.pricePaise())
                .param("currency", item.currency())
                .param("reorder_point", item.reorderPoint())
                .param("reorder_qty", item.reorderQty())
                .param("active", item.active())
                .update();
    }
}
