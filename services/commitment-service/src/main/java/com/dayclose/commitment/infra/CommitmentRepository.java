package com.dayclose.commitment.infra;

import com.dayclose.commitment.domain.Commitment;
import com.dayclose.commitment.domain.CommitmentState;
import com.dayclose.commitment.domain.CommitmentType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CommitmentRepository {

    private final JdbcClient jdbcClient;

    public CommitmentRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Commitment create(UUID tenantId, String customerRef, CommitmentType type, int tier, 
                            int totalPaise, String currency, String metadata) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                INSERT INTO commitments (id, tenant_id, customer_ref, type, tier, state, total_paise, currency, metadata)
                VALUES (:id, :tenant_id, :customer_ref, :type, :tier, 'created', :total_paise, :currency, CAST(:metadata AS jsonb))
                """)
                .param("id", id)
                .param("tenant_id", tenantId)
                .param("customer_ref", customerRef)
                .param("type", type.toDbValue())
                .param("tier", tier)
                .param("total_paise", totalPaise)
                .param("currency", currency != null ? currency : "INR")
                .param("metadata", metadata)
                .update();

        return findById(tenantId, id).orElseThrow();
    }

    public Optional<Commitment> findById(UUID tenantId, UUID id) {
        return jdbcClient.sql("""
                SELECT id, tenant_id, customer_ref, type, tier, state, total_paise, currency, 
                       metadata::text, created_at, updated_at
                FROM commitments WHERE id = :id AND tenant_id = :tenant_id
                """)
                .param("id", id)
                .param("tenant_id", tenantId)
                .query(this::mapCommitment)
                .optional();
    }

    /**
     * Find by ID without tenant restriction (for saga orchestration).
     */
    public Optional<Commitment> findById(UUID id) {
        return jdbcClient.sql("""
                SELECT id, tenant_id, customer_ref, type, tier, state, total_paise, currency, 
                       metadata::text, created_at, updated_at
                FROM commitments WHERE id = :id
                """)
                .param("id", id)
                .query(this::mapCommitment)
                .optional();
    }

    public List<Commitment> listByState(UUID tenantId, CommitmentState state, int limit) {
        return jdbcClient.sql("""
                SELECT id, tenant_id, customer_ref, type, tier, state, total_paise, currency,
                       metadata::text, created_at, updated_at
                FROM commitments 
                WHERE tenant_id = :tenant_id AND state = :state
                ORDER BY created_at DESC
                LIMIT :limit
                """)
                .param("tenant_id", tenantId)
                .param("state", state.toDbValue())
                .param("limit", limit)
                .query(this::mapCommitment)
                .list();
    }

    public List<Commitment> listByCustomer(UUID tenantId, String customerRef, int limit) {
        return jdbcClient.sql("""
                SELECT id, tenant_id, customer_ref, type, tier, state, total_paise, currency,
                       metadata::text, created_at, updated_at
                FROM commitments 
                WHERE tenant_id = :tenant_id AND customer_ref = :customer_ref
                ORDER BY created_at DESC
                LIMIT :limit
                """)
                .param("tenant_id", tenantId)
                .param("customer_ref", customerRef)
                .param("limit", limit)
                .query(this::mapCommitment)
                .list();
    }

    public void transition(UUID commitmentId, CommitmentState toState, String actor, String reason) {
        jdbcClient.sql("SELECT transition_commitment(:commitment_id, :to_state, :actor, :reason)")
                .param("commitment_id", commitmentId)
                .param("to_state", toState.toDbValue())
                .param("actor", actor)
                .param("reason", reason)
                .query()
                .singleValue();
    }

    private Commitment mapCommitment(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Commitment(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("tenant_id"),
                rs.getString("customer_ref"),
                CommitmentType.fromString(rs.getString("type")),
                rs.getInt("tier"),
                CommitmentState.fromString(rs.getString("state")),
                rs.getInt("total_paise"),
                rs.getString("currency"),
                rs.getString("metadata"),
                ((Timestamp) rs.getObject("created_at")).toInstant(),
                ((Timestamp) rs.getObject("updated_at")).toInstant()
        );
    }
}
