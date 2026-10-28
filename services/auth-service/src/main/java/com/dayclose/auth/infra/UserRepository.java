package com.dayclose.auth.infra;

import com.dayclose.auth.domain.User;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepository {

    private final JdbcClient jdbcClient;

    public UserRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public User create(UUID tenantId, String email, String passwordHash, String role) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcClient.sql("""
                INSERT INTO users (id, tenant_id, email, password_hash, role, created_at)
                VALUES (:id, :tenant_id, :email, :password_hash, :role, :created_at)
                """)
                .param("id", id)
                .param("tenant_id", tenantId)
                .param("email", email.toLowerCase())
                .param("password_hash", passwordHash)
                .param("role", role)
                .param("created_at", now)
                .update();
        return new User(id, tenantId, email.toLowerCase(), passwordHash, role, now);
    }

    public Optional<User> findByEmail(String email) {
        return jdbcClient.sql("""
                SELECT id, tenant_id, email, password_hash, role, created_at
                FROM users WHERE email = :email
                """)
                .param("email", email.toLowerCase())
                .query((rs, rowNum) -> new User(
                        (UUID) rs.getObject("id"),
                        (UUID) rs.getObject("tenant_id"),
                        rs.getString("email"),
                        rs.getString("password_hash"),
                        rs.getString("role"),
                        rs.getTimestamp("created_at").toInstant()
                ))
                .optional();
    }

    public Optional<User> findById(UUID id) {
        return jdbcClient.sql("""
                SELECT id, tenant_id, email, password_hash, role, created_at
                FROM users WHERE id = :id
                """)
                .param("id", id)
                .query((rs, rowNum) -> new User(
                        (UUID) rs.getObject("id"),
                        (UUID) rs.getObject("tenant_id"),
                        rs.getString("email"),
                        rs.getString("password_hash"),
                        rs.getString("role"),
                        rs.getTimestamp("created_at").toInstant()
                ))
                .optional();
    }
}
