package com.dayclose.commitment.saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Coordinates saga execution for commitments.
 * This is the entry point called by the API controller.
 */
@Service
public class SagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SagaOrchestrator.class);

    private final ApprovalSaga approvalSaga;
    private final RejectionSaga rejectionSaga;

    public SagaOrchestrator(ApprovalSaga approvalSaga, RejectionSaga rejectionSaga) {
        this.approvalSaga = approvalSaga;
        this.rejectionSaga = rejectionSaga;
    }

    /**
     * Executes the approval saga asynchronously.
     * In production, this should be triggered by an event or scheduled task.
     */
    public void executeApprovalSaga(UUID commitmentId, String actor) {
        log.info("Orchestrating approval saga for commitment {} by actor {}", commitmentId, actor);
        
        // TODO: In production, publish event to queue and let worker pick it up
        // For now, execute synchronously (but non-blocking for caller)
        try {
            approvalSaga.execute(commitmentId, actor);
        } catch (Exception e) {
            log.error("Approval saga failed for commitment {}: {}", commitmentId, e.getMessage());
            // Don't rethrow - saga has already marked commitment as FAILED
        }
    }

    /**
     * Executes the rejection saga asynchronously.
     */
    public void executeRejectionSaga(UUID commitmentId, String actor, String reason) {
        log.info("Orchestrating rejection saga for commitment {} by actor {}", commitmentId, actor);
        
        // TODO: In production, publish event to queue and let worker pick it up
        // For now, execute synchronously (but non-blocking for caller)
        try {
            rejectionSaga.execute(commitmentId, actor, reason);
        } catch (Exception e) {
            log.error("Rejection saga failed for commitment {}: {}", commitmentId, e.getMessage());
            // Don't rethrow - saga has already marked commitment as FAILED
        }
    }
}
