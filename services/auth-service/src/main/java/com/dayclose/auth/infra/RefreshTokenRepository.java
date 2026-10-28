package com.dayclose.auth.infra;

import com.dayclose.auth.domain.RefreshToken;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RefreshTokenRepository {

    private final JdbcClient jdbcClient;

    public RefreshTokenRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public RefreshToken create(UUID userId, String tokenHash, UUID familyId, Instant expiresAt) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcClient.sql("""
                INSERT INTO refresh_tokens (id, user_id, token_hash, family_id, revoked, expires_at, created_at)
                VALUES (:id, :user_id, :token_hash, :family_id, false, :expires_at, :created_at)
                """)
                .param("id", id)
                .param("user_id", userId)
                .param("token_hash", tokenHash)
                .param("family_id", familyId)
                .param("expires_at", expiresAt)
                .param("created_at", now)
                .update();
        return new RefreshToken(id, userId, tokenHash, familyId, false, expiresAt, now);
    }

    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return jdbcClient.sql("""
                SELECT id, user_id, token_hash, family_id, revoked, expires_at, created_at
                FROM refresh_tokens WHERE token_hash = :token_hash
                """)
                .param("token_hash", tokenHash)
                .query((rs, rowNum) -> new RefreshToken(
                        (UUID) rs.getObject("id"),
                        (UUID) rs.getObject("user_id"),
                        rs.getString("token_hash"),
                        (UUID) rs.getObject("family_id"),
                        rs.getBoolean("revoked"),
                        rs.getTimestamp("expires_at").toInstant(),
                        rs.getTimestamp("created_at").toInstant()
                ))
                .optional();
    }

    public void revoke(UUID id) {
        jdbcClient.sql("UPDATE refresh_tokens SET revoked = true WHERE id = :id")
                .param("id", id)
                .update();
    }

    public void revokeFamily(UUID familyId) {
        jdbcClient.sql("UPDATE refresh_tokens SET revoked = true WHERE family_id = :family_id")
                .param("family_id", familyId)
                .update();
    }
}
