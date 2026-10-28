package com.dayclose.commitment.saga;

import com.dayclose.commitment.domain.Commitment;
import com.dayclose.commitment.domain.CommitmentLine;
import com.dayclose.commitment.domain.CommitmentState;
import com.dayclose.commitment.infra.CommitmentLineRepository;
import com.dayclose.commitment.infra.CommitmentRepository;
import com.dayclose.commitment.saga.client.InventoryClient;
import com.dayclose.commitment.saga.client.NotificationClient;
import com.dayclose.commitment.saga.client.SagaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates the approval saga (ADR-0003):
 * 1. Approve commitment (state = APPROVED)
 * 2. Commit all reservations in inventory-service
 * 3. Send notification to customer
 * 4. Mark commitment as COMPLETED
 *
 * If any step fails, compensate by releasing reservations.
 */
@Component
public class ApprovalSaga {

    private static final Logger log = LoggerFactory.getLogger(ApprovalSaga.class);

    private final CommitmentRepository commitmentRepository;
    private final CommitmentLineRepository lineRepository;
    private final InventoryClient inventoryClient;
    private final NotificationClient notificationClient;

    public ApprovalSaga(
            CommitmentRepository commitmentRepository,
            CommitmentLineRepository lineRepository,
            InventoryClient inventoryClient,
            NotificationClient notificationClient) {
        this.commitmentRepository = commitmentRepository;
        this.lineRepository = lineRepository;
        this.inventoryClient = inventoryClient;
        this.notificationClient = notificationClient;
    }

    /**
     * Executes the approval saga.
     * This is called AFTER the commitment has been transitioned to APPROVED state.
     */
    @Transactional
    public void execute(UUID commitmentId, String actor) {
        log.info("Starting approval saga for commitment {}", commitmentId);

        // Step 1: Load commitment and lines
        Commitment commitment = commitmentRepository.findById(commitmentId)
                .orElseThrow(() -> new IllegalArgumentException("Commitment not found: " + commitmentId));

        if (commitment.state() != CommitmentState.APPROVED) {
            throw new IllegalStateException("Commitment must be in APPROVED state to execute saga");
        }

        List<CommitmentLine> lines = lineRepository.findByCommitment(commitmentId);

        try {
            // Step 2: Commit all reservations
            log.info("Committing {} reservations for commitment {}", lines.size(), commitmentId);
            for (CommitmentLine line : lines) {
                if (line.reservationId() != null) {
                    inventoryClient.commitReservation(
                            commitment.tenantId(),
                            line.reservationId(),
                            actor,
                            "Commitment " + commitmentId + " approved"
                    );
                }
            }

            // Step 3: Send approval notification to customer
            log.info("Sending approval notification for commitment {}", commitmentId);
            sendApprovalNotification(commitment);

            // Step 4: Transition to COMMITTED
            commitmentRepository.transition(
                    commitmentId,
                    CommitmentState.COMMITTED,
                    "saga",
                    "All reservations committed successfully"
            );

            log.info("Approval saga completed successfully for commitment {}", commitmentId);

        } catch (Exception e) {
            log.error("Approval saga failed for commitment {}: {}", commitmentId, e.getMessage(), e);

            // Compensation: release any reservations that might have been committed
            compensate(commitment, lines, actor);

            // Transition to FAILED
            commitmentRepository.transition(
                    commitmentId,
                    CommitmentState.FAILED,
                    "saga",
                    "Saga failed: " + e.getMessage()
            );

            throw new SagaException("Approval saga failed", e);
        }
    }

    /**
     * Compensates by releasing all reservations.
     */
    private void compensate(Commitment commitment, List<CommitmentLine> lines, String actor) {
        log.warn("Compensating approval saga for commitment {}", commitment.id());

        for (CommitmentLine line : lines) {
            if (line.reservationId() != null) {
                try {
                    inventoryClient.releaseReservation(
                            commitment.tenantId(),
                            line.reservationId(),
                            actor,
                            "Compensation: approval saga failed for commitment " + commitment.id()
                    );
                } catch (Exception e) {
                    log.error("Failed to release reservation {} during compensation: {}", 
                            line.reservationId(), e.getMessage());
                    // Continue with other reservations even if one fails
                }
            }
        }
    }

    /**
     * Sends approval notification to customer.
     */
    private void sendApprovalNotification(Commitment commitment) {
        Map<String, Object> data = new HashMap<>();
        data.put("commitmentId", commitment.id().toString());
        data.put("customerRef", commitment.customerRef());
        data.put("totalAmount", formatCurrency(commitment.totalPaise(), commitment.currency()));
        data.put("type", commitment.type().name().toLowerCase());

        notificationClient.sendNotification(
                commitment.tenantId(),
                commitment.customerRef(),  // Assuming customerRef is phone number
                "sms",
                "commitment_approved",
                data
        );
    }

    private String formatCurrency(int paise, String currency) {
        return String.format("%.2f %s", paise / 100.0, currency);
    }
}
