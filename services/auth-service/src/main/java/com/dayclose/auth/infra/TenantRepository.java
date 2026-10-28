package com.dayclose.auth.infra;

import com.dayclose.auth.domain.Tenant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TenantRepository {

    private final JdbcClient jdbcClient;

    public TenantRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Tenant create(String name) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcClient.sql("INSERT INTO tenants (id, name, created_at) VALUES (:id, :name, :created_at)")
                .param("id", id)
                .param("name", name)
                .param("created_at", now)
                .update();
        return new Tenant(id, name, now);
    }

    public Optional<Tenant> findById(UUID id) {
        return jdbcClient.sql("SELECT id, name, created_at FROM tenants WHERE id = :id")
                .param("id", id)
                .query((rs, rowNum) -> new Tenant(
                        (UUID) rs.getObject("id"),
                        rs.getString("name"),
                        rs.getTimestamp("created_at").toInstant()
                ))
                .optional();
    }
}
