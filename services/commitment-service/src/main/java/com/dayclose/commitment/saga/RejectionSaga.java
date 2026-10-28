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
 * Orchestrates the rejection saga (ADR-0003):
 * 1. Reject commitment (state = REJECTED)
 * 2. Release all reservations in inventory-service
 * 3. Send notification to customer
 *
 * This is simpler than approval saga since we're just releasing stock.
 */
@Component
public class RejectionSaga {

    private static final Logger log = LoggerFactory.getLogger(RejectionSaga.class);

    private final CommitmentRepository commitmentRepository;
    private final CommitmentLineRepository lineRepository;
    private final InventoryClient inventoryClient;
    private final NotificationClient notificationClient;

    public RejectionSaga(
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
     * Executes the rejection saga.
     * This is called AFTER the commitment has been transitioned to REJECTED state.
     */
    @Transactional
    public void execute(UUID commitmentId, String actor, String reason) {
        log.info("Starting rejection saga for commitment {}", commitmentId);

        // Step 1: Load commitment and lines
        Commitment commitment = commitmentRepository.findById(commitmentId)
                .orElseThrow(() -> new IllegalArgumentException("Commitment not found: " + commitmentId));

        if (commitment.state() != CommitmentState.REJECTED) {
            throw new IllegalStateException("Commitment must be in REJECTED state to execute saga");
        }

        List<CommitmentLine> lines = lineRepository.findByCommitment(commitmentId);

        try {
            // Step 2: Release all reservations
            log.info("Releasing {} reservations for commitment {}", lines.size(), commitmentId);
            for (CommitmentLine line : lines) {
                if (line.reservationId() != null) {
                    inventoryClient.releaseReservation(
                            commitment.tenantId(),
                            line.reservationId(),
                            actor,
                            reason != null ? reason : "Commitment " + commitmentId + " rejected"
                    );
                }
            }

            // Step 3: Send rejection notification to customer
            log.info("Sending rejection notification for commitment {}", commitmentId);
            sendRejectionNotification(commitment, reason);

            log.info("Rejection saga completed successfully for commitment {}", commitmentId);

        } catch (Exception e) {
            log.error("Rejection saga failed for commitment {}: {}", commitmentId, e.getMessage(), e);

            // For rejection, we don't need compensation - if release fails, the TTL will handle it
            // But we should still mark it as failed
            commitmentRepository.transition(
                    commitmentId,
                    CommitmentState.FAILED,
                    "saga",
                    "Rejection saga failed: " + e.getMessage()
            );

            throw new SagaException("Rejection saga failed", e);
        }
    }

    /**
     * Sends rejection notification to customer.
     */
    private void sendRejectionNotification(Commitment commitment, String reason) {
        Map<String, Object> data = new HashMap<>();
        data.put("commitmentId", commitment.id().toString());
        data.put("customerRef", commitment.customerRef());
        data.put("totalAmount", formatCurrency(commitment.totalPaise(), commitment.currency()));
        data.put("type", commitment.type().name().toLowerCase());
        data.put("reason", reason != null ? reason : "Not approved");

        notificationClient.sendNotification(
                commitment.tenantId(),
                commitment.customerRef(),  // Assuming customerRef is phone number
                "sms",
                "commitment_rejected",
                data
        );
    }

    private String formatCurrency(int paise, String currency) {
        return String.format("%.2f %s", paise / 100.0, currency);
    }
}
