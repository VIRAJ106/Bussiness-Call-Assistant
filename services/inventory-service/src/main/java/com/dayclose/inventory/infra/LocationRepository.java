package com.dayclose.inventory.infra;

import com.dayclose.inventory.domain.Location;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class LocationRepository {

    private final JdbcClient jdbcClient;

    public LocationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Location create(UUID tenantId, String name) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("INSERT INTO locations (id, tenant_id, name) VALUES (:id, :tenant_id, :name)")
                .param("id", id)
                .param("tenant_id", tenantId)
                .param("name", name)
                .update();
        return new Location(id, tenantId, name);
    }

    public Optional<Location> findById(UUID tenantId, UUID id) {
        return jdbcClient.sql("SELECT id, tenant_id, name FROM locations WHERE id = :id AND tenant_id = :tenant_id")
                .param("id", id)
                .param("tenant_id", tenantId)
                .query((rs, rowNum) -> new Location(
                        (UUID) rs.getObject("id"),
                        (UUID) rs.getObject("tenant_id"),
                        rs.getString("name")
                ))
                .optional();
    }

    public List<Location> list(UUID tenantId) {
        return jdbcClient.sql("SELECT id, tenant_id, name FROM locations WHERE tenant_id = :tenant_id ORDER BY name ASC")
                .param("tenant_id", tenantId)
                .query((rs, rowNum) -> new Location(
                        (UUID) rs.getObject("id"),
                        (UUID) rs.getObject("tenant_id"),
                        rs.getString("name")
                ))
                .list();
    }
}
