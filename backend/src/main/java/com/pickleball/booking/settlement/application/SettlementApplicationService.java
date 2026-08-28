package com.pickleball.booking.settlement.application;

import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.identity.application.IdentityService;
import com.pickleball.booking.identity.domain.RoleCode;
import com.pickleball.booking.settlement.domain.SettlementAllocationPolicy;
import com.pickleball.booking.settlement.domain.SettlementAllocationPolicy.AllocationRequest;
import com.pickleball.booking.settlement.infrastructure.SettlementStore;
import com.pickleball.booking.settlement.infrastructure.SettlementStore.CoachSettlementRow;
import com.pickleball.booking.settlement.infrastructure.SettlementStore.SettlementRow;
import com.pickleball.booking.shared.application.AuditOutboxService;
import com.pickleball.booking.shared.application.BusinessException;
import com.pickleball.booking.shared.application.IdempotencyService;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SettlementApplicationService {
    private static final String CONFIRM_OPERATION = "SESSION_SETTLEMENT_CONFIRMATION";

    private final SettlementStore store;
    private final IdentityService identity;
    private final IdempotencyService idempotency;
    private final AuditOutboxService audit;

    public SettlementApplicationService(
            SettlementStore store,
            IdentityService identity,
            IdempotencyService idempotency,
            AuditOutboxService audit) {
        this.store = store;
        this.identity = identity;
        this.idempotency = idempotency;
        this.audit = audit;
    }

    @Transactional
    public CalculationView calculate(
            AuthenticatedPrincipal principal,
            UUID courseSessionId,
            CalculationCommand command,
            String requestId) {
        identity.requireActiveUser(principal.userId());
        var source = store.findCalculationSourceLocked(courseSessionId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Course session was not found"));
        requireCommittee(principal, source.organizationId());

        if (!"COMPLETED".equals(source.sessionStatus())) {
            throw new BusinessException("SETTLEMENT_NOT_READY", "Course session must be COMPLETED before settlement calculation");
        }
        if (source.priceSnapshot() == null) {
            throw new BusinessException("SETTLEMENT_NOT_READY", "A CONFIRMED session price snapshot is required");
        }
        if (!source.receivablesExistWhenRequired()) {
            throw new BusinessException("SETTLEMENT_NOT_READY", "Finance receivable facts have not been generated for this session");
        }
        if (!source.hasCanonicalPriceLineage()) {
            throw new BusinessException("SETTLEMENT_NOT_READY", "Receivable items do not match the CONFIRMED session price snapshot");
        }
        if (source.coachAssignments().isEmpty()) {
            throw new BusinessException("SETTLEMENT_NOT_READY", "At least one ACCEPTED coach assignment is required");
        }

        store.findSettlementLockedBySession(courseSessionId).ifPresent(existing -> {
            if ("CONFIRMED".equals(existing.status())) {
                throw new BusinessException("STATE_TRANSITION_INVALID", "A CONFIRMED settlement is immutable; use an adjustment");
            }
            if ("VOIDED".equals(existing.status())) {
                throw new BusinessException("STATE_TRANSITION_INVALID", "A VOIDED settlement cannot be recalculated");
            }
        });

        BigDecimal grossReceivable = source.grossReceivable();
        BigDecimal otherAdjustment = money(command.otherAdjustment() == null ? BigDecimal.ZERO : command.otherAdjustment(), "otherAdjustment");
        BigDecimal distributable = grossReceivable.subtract(source.venueCost()).add(otherAdjustment).setScale(2);
        if (distributable.signum() < 0) {
            throw new BusinessException("SETTLEMENT_NOT_READY", "Settlement distributableAmount cannot be negative");
        }

        List<SettlementAllocationPolicy.Assignment> assignments = source.coachAssignments().stream()
                .map(row -> new SettlementAllocationPolicy.Assignment(row.assignmentId(), row.coachProfileId()))
                .toList();
        List<SettlementAllocationPolicy.Allocation> allocations;
        try {
            allocations = SettlementAllocationPolicy.allocate(distributable, assignments, command.coachAllocations());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("VALIDATION_FAILED", ex.getMessage());
        }

        UUID settlementId = store.saveCalculated(source, grossReceivable, otherAdjustment, distributable, allocations);
        BigDecimal coachPayableTotal = allocations.stream()
                .map(SettlementAllocationPolicy.Allocation::payableAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2);

        var after = Map.of(
                "status", "CALCULATED",
                "grossReceivable", grossReceivable,
                "venueCost", source.venueCost(),
                "otherAdjustment", otherAdjustment,
                "distributableAmount", distributable,
                "coachPayableTotal", coachPayableTotal);
        audit.record(source.organizationId(), principal.userId(), "SETTLEMENT_CALCULATED",
                "SessionSettlement", settlementId, null, null, after, requestId);

        return new CalculationView(
                settlementId, grossReceivable, source.venueCost(), otherAdjustment, distributable, coachPayableTotal);
    }

    @Transactional
    public ConfirmationView confirm(
            AuthenticatedPrincipal principal,
            UUID settlementId,
            ConfirmationCommand command,
            String idempotencyKey,
            String requestId) {
        identity.requireActiveUser(principal.userId());
        SettlementRow settlement = store.findSettlementLockedById(settlementId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Session settlement was not found"));
        requireCommittee(principal, settlement.organizationId());

        String reason = requireReason(command.reason());
        var idem = idempotency.begin(
                settlement.organizationId(), principal.userId(), CONFIRM_OPERATION, idempotencyKey,
                settlementId + "|" + reason);
        if (idem.getResultResourceId() != null) {
            SettlementRow replay = store.findSettlementLockedById(idem.getResultResourceId())
                    .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Confirmed settlement was not found"));
            return new ConfirmationView(replay.id(), replay.status(), replay.confirmedAt());
        }

        if (!"CALCULATED".equals(settlement.status())) {
            throw new BusinessException("STATE_TRANSITION_INVALID", "Only a CALCULATED settlement can be confirmed");
        }

        var readiness = store.lockFinanceReadiness(settlement.courseSessionId(), settlement.priceSnapshotId());
        if (!readiness.sameSnapshot()) {
            throw new BusinessException("SETTLEMENT_NOT_READY", "Finance facts changed from the calculated price lineage");
        }

        Instant confirmedAt = Instant.now();
        store.confirm(settlementId, principal.userId(), confirmedAt, readiness.fullyCollected());
        SettlementRow confirmed = store.findSettlementLockedById(settlementId)
                .orElseThrow(() -> new IllegalStateException("Confirmed settlement disappeared"));

        var before = Map.of("status", settlement.status());
        var after = Map.of(
                "status", confirmed.status(),
                "confirmedBy", principal.userId(),
                "confirmedAt", confirmedAt,
                "financeReady", readiness.fullyCollected());
        audit.record(settlement.organizationId(), principal.userId(), "SETTLEMENT_CONFIRMED",
                "SessionSettlement", settlementId, reason, before, after, requestId);
        idem.complete("SessionSettlement", settlementId, 200);
        return new ConfirmationView(settlementId, confirmed.status(), confirmedAt);
    }

    @Transactional
    public SettlementView getByCourseSession(AuthenticatedPrincipal principal, UUID courseSessionId) {
        identity.requireActiveUser(principal.userId());
        SettlementRow settlement = store.findSettlementBySession(courseSessionId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Session settlement was not found"));

        boolean committee = identity.isAuthorizedForOrganization(principal, RoleCode.COMMITTEE, settlement.organizationId());
        List<CoachSettlementRow> coachRows;
        if (committee) {
            coachRows = store.findCoachSettlements(settlement.id());
        } else {
            boolean coachRole = identity.isAuthorizedForOrganization(principal, RoleCode.COACH, settlement.organizationId());
            if (!coachRole || !store.actorOwnsCoachSettlement(settlement.id(), principal.userId())) {
                throw new BusinessException("ORG_SCOPE_DENIED", "Settlement is outside the actor organization or teaching scope");
            }
            coachRows = store.findCoachSettlementsForActor(settlement.id(), principal.userId());
        }

        return toView(settlement, coachRows);
    }

    private SettlementView toView(SettlementRow settlement, List<CoachSettlementRow> coachRows) {
        return new SettlementView(
                settlement.id(),
                settlement.courseSessionId(),
                settlement.status(),
                settlement.grossReceivable(),
                settlement.venueCost(),
                settlement.otherAdjustment(),
                settlement.distributableAmount(),
                coachRows.stream().map(row -> new CoachSettlementView(
                        row.id(), row.coachProfileId(), row.payableAmount(), row.paidAmount(), row.payoutStatus(), row.version())).toList(),
                settlement.version());
    }

    private void requireCommittee(AuthenticatedPrincipal principal, UUID organizationId) {
        if (!identity.isAuthorizedForOrganization(principal, RoleCode.COMMITTEE, organizationId)) {
            throw new BusinessException("ORG_SCOPE_DENIED", "Committee or platform administrator access is required for this organization");
        }
    }

    private static BigDecimal money(BigDecimal value, String field) {
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new BusinessException("VALIDATION_FAILED", field + " supports at most 2 decimals");
        }
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) throw new BusinessException("VALIDATION_FAILED", "reason is required");
        return reason.trim();
    }

    public record CalculationCommand(BigDecimal otherAdjustment, List<AllocationRequest> coachAllocations) {}

    public record CalculationView(
            UUID sessionSettlementId,
            BigDecimal grossReceivable,
            BigDecimal venueCost,
            BigDecimal otherAdjustment,
            BigDecimal distributableAmount,
            BigDecimal coachPayableTotal) {}

    public record ConfirmationCommand(String reason) {}

    public record ConfirmationView(UUID sessionSettlementId, String status, Instant confirmedAt) {}

    public record SettlementView(
            UUID sessionSettlementId,
            UUID courseSessionId,
            String status,
            BigDecimal grossReceivable,
            BigDecimal venueCost,
            BigDecimal otherAdjustment,
            BigDecimal distributableAmount,
            List<CoachSettlementView> coachSettlements,
            long version) {}

    public record CoachSettlementView(
            UUID coachSettlementId,
            UUID coachProfileId,
            BigDecimal payableAmount,
            BigDecimal paidAmount,
            String payoutStatus,
            long version) {}
}
