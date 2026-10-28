package com.dayclose.auth.infra;

import com.dayclose.auth.domain.ServiceCredential;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ServiceCredentialRepository {

    private final JdbcClient jdbcClient;

    public ServiceCredentialRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public ServiceCredential create(String clientId, UUID tenantId, String secretHash, String scopes) {
        Instant now = Instant.now();
        jdbcClient.sql("""
                INSERT INTO service_credentials (client_id, tenant_id, secret_hash, scopes, created_at)
                VALUES (:client_id, :tenant_id, :secret_hash, :scopes, :created_at)
                """)
                .param("client_id", clientId)
                .param("tenant_id", tenantId)
                .param("secret_hash", secretHash)
                .param("scopes", scopes)
                .param("created_at", now)
                .update();
        return new ServiceCredential(clientId, tenantId, secretHash, scopes, now);
    }

    public Optional<ServiceCredential> findByClientId(String clientId) {
        return jdbcClient.sql("""
                SELECT client_id, tenant_id, secret_hash, scopes, created_at
                FROM service_credentials WHERE client_id = :client_id
                """)
                .param("client_id", clientId)
                .query((rs, rowNum) -> new ServiceCredential(
                        rs.getString("client_id"),
                        (UUID) rs.getObject("tenant_id"),
                        rs.getString("secret_hash"),
                        rs.getString("scopes"),
                        rs.getTimestamp("created_at").toInstant()
                ))
                .optional();
    }
}
