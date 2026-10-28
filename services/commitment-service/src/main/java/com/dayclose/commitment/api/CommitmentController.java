package com.dayclose.commitment.api;

import com.dayclose.commitment.api.dto.ApproveCommitmentRequest;
import com.dayclose.commitment.api.dto.CreateCommitmentRequest;
import com.dayclose.commitment.api.dto.RejectCommitmentRequest;
import com.dayclose.commitment.domain.Commitment;
import com.dayclose.commitment.domain.CommitmentState;
import com.dayclose.commitment.domain.CommitmentType;
import com.dayclose.commitment.infra.CommitmentLineRepository;
import com.dayclose.commitment.infra.CommitmentRepository;
import com.dayclose.commitment.saga.SagaOrchestrator;
import com.dayclose.web.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST API for commitments (orders/discounts/reorders) and owner approval workflow (ADR-0005).
 */
@RestController
@RequestMapping("/v1/commitments")
public class CommitmentController {

    private final CommitmentRepository commitmentRepository;
    private final CommitmentLineRepository lineRepository;
    private final SagaOrchestrator sagaOrchestrator;

    public CommitmentController(CommitmentRepository commitmentRepository,
                               CommitmentLineRepository lineRepository,
                               SagaOrchestrator sagaOrchestrator) {
        this.commitmentRepository = commitmentRepository;
        this.lineRepository = lineRepository;
        this.sagaOrchestrator = sagaOrchestrator;
    }

    @PostMapping
    public ResponseEntity<Commitment> createCommitment(@Valid @RequestBody CreateCommitmentRequest request) {
        UUID tenantId = TenantContext.require();

        CommitmentType type = CommitmentType.fromString(request.type());

        // Calculate total
        int totalPaise = request.lines().stream()
                .mapToInt(line -> line.qty() * line.pricePaise())
                .sum();

        // Create commitment (starts in "created" state)
        Commitment commitment = commitmentRepository.create(
                tenantId,
                request.customerRef(),
                type,
                1,  // Default to Tier 1 (policy engine will upgrade if needed)
                totalPaise,
                "INR",
                request.metadata()
        );

        // Create lines
        for (var line : request.lines()) {
            lineRepository.create(
                    commitment.id(),
                    line.itemId(),
                    line.locationId(),
                    line.reservationId(),
                    line.qty(),
                    line.pricePaise()
            );
        }

        // Transition to pending_approval (policy engine would check if auto-approve applies)
        commitmentRepository.transition(
                commitment.id(),
                CommitmentState.PENDING_APPROVAL,
                "agent",
                "Commitment created"
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(commitment);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Commitment> getCommitment(@PathVariable UUID id) {
        UUID tenantId = TenantContext.require();

        return commitmentRepository.findById(tenantId, id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<Commitment>> listCommitments(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String customerRef,
            @RequestParam(defaultValue = "50") int limit) {
        UUID tenantId = TenantContext.require();

        if (limit > 100) limit = 100;

        List<Commitment> commitments;
        if (state != null) {
            commitments = commitmentRepository.listByState(tenantId, CommitmentState.fromString(state), limit);
        } else if (customerRef != null) {
            commitments = commitmentRepository.listByCustomer(tenantId, customerRef, limit);
        } else {
            commitments = commitmentRepository.listByState(tenantId, CommitmentState.PENDING_APPROVAL, limit);
        }

        return ResponseEntity.ok(commitments);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approveCommitment(
            @PathVariable UUID id,
            @Valid @RequestBody ApproveCommitmentRequest request) {
        UUID tenantId = TenantContext.require();

        // Verify commitment exists and is pending
        Commitment commitment = commitmentRepository.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Commitment not found"));

        if (!commitment.isPendingApproval()) {
            throw new IllegalStateException("Commitment is not pending approval");
        }

        // Transition to approved
        commitmentRepository.transition(
                id,
                CommitmentState.APPROVED,
                request.actor() != null ? request.actor() : "owner",
                request.reason()
        );

        // Trigger saga to commit stock reservations and notify customer
        String actor = request.actor() != null ? request.actor() : "owner";
        sagaOrchestrator.executeApprovalSaga(id, actor);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> rejectCommitment(
            @PathVariable UUID id,
            @Valid @RequestBody RejectCommitmentRequest request) {
        UUID tenantId = TenantContext.require();

        // Verify commitment exists and is pending
        Commitment commitment = commitmentRepository.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Commitment not found"));

        if (!commitment.isPendingApproval()) {
            throw new IllegalStateException("Commitment is not pending approval");
        }

        // Transition to rejected
        commitmentRepository.transition(
                id,
                CommitmentState.REJECTED,
                request.actor() != null ? request.actor() : "owner",
                request.reason()
        );

        // Trigger compensation saga to release reservations and notify customer
        String actor = request.actor() != null ? request.actor() : "owner";
        sagaOrchestrator.executeRejectionSaga(id, actor, request.reason());

        return ResponseEntity.noContent().build();
    }
}
