package com.dayclose.voice.infra;

import com.dayclose.voice.domain.CallSession;
import com.dayclose.voice.domain.CallState;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CallSessionRepository {
    
    private final JdbcClient jdbcClient;
    
    public CallSessionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }
    
    public CallSession create(UUID tenantId, String callerId, String direction, String language) {
        UUID id = UUID.randomUUID();
        
        jdbcClient.sql("""
                INSERT INTO call_sessions (id, tenant_id, caller_id, direction, state, language)
                VALUES (:id, :tenant_id, :caller_id, :direction, 'initiated', :language)
                """)
                .param("id", id)
                .param("tenant_id", tenantId)
                .param("caller_id", callerId)
                .param("direction", direction)
                .param("language", language)
                .update();
        
        return new CallSession(id, tenantId, callerId, direction, 
                CallState.INITIATED, language, Instant.now(), null, 0, null);
    }
    
    public Optional<CallSession> findById(UUID tenantId, UUID id) {
        return jdbcClient.sql("""
                SELECT id, tenant_id, caller_id, direction, state, language, 
                       started_at, ended_at, duration_seconds, transcript_summary
                FROM call_sessions 
                WHERE id = :id AND tenant_id = :tenant_id
                """)
                .param("id", id)
                .param("tenant_id", tenantId)
                .query((rs, rowNum) -> new CallSession(
                        (UUID) rs.getObject("id"),
                        (UUID) rs.getObject("tenant_id"),
                        rs.getString("caller_id"),
                        rs.getString("direction"),
                        CallState.fromString(rs.getString("state")),
                        rs.getString("language"),
                        ((Timestamp) rs.getObject("started_at")).toInstant(),
                        rs.getObject("ended_at") != null 
                                ? ((Timestamp) rs.getObject("ended_at")).toInstant() 
                                : null,
                        rs.getInt("duration_seconds"),
                        rs.getString("transcript_summary")
                ))
                .optional();
    }
    
    public void updateState(UUID sessionId, CallState state) {
        jdbcClient.sql("UPDATE call_sessions SET state = :state WHERE id = :id")
                .param("state", state.toDbValue())
                .param("id", sessionId)
                .update();
    }
    
    public void endSession(UUID sessionId, CallState finalState) {
        jdbcClient.sql("""
                UPDATE call_sessions 
                   SET state = :state, 
                       ended_at = now(),
                       duration_seconds = EXTRACT(EPOCH FROM (now() - started_at))::int
                 WHERE id = :id
                """)
                .param("state", finalState.toDbValue())
                .param("id", sessionId)
                .update();
    }
}
