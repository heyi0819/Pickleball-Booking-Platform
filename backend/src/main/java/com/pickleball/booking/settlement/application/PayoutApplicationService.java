package com.pickleball.booking.settlement.application;

import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.identity.application.IdentityService;
import com.pickleball.booking.identity.domain.RoleCode;
import com.pickleball.booking.settlement.infrastructure.PayoutStore;
import com.pickleball.booking.settlement.infrastructure.PayoutStore.CoachSettlementPayoutRow;
import com.pickleball.booking.settlement.infrastructure.PayoutStore.PayoutBatchItemRow;
import com.pickleball.booking.settlement.infrastructure.PayoutStore.PayoutBatchRow;
import com.pickleball.booking.shared.application.AuditOutboxService;
import com.pickleball.booking.shared.application.BusinessException;
import com.pickleball.booking.shared.application.IdempotencyService;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PayoutApplicationService {
    private static final String EXECUTE_OPERATION = "PAYOUT_BATCH_EXECUTION";
    private static final Set<String> METHODS = Set.of("CASH", "BANK_TRANSFER", "OTHER");

    private final PayoutStore store;
    private final IdentityService identity;
    private final IdempotencyService idempotency;
    private final AuditOutboxService audit;

    public PayoutApplicationService(
            PayoutStore store,
            IdentityService identity,
            IdempotencyService idempotency,
            AuditOutboxService audit) {
        this.store = store;
        this.identity = identity;
        this.idempotency = idempotency;
        this.audit = audit;
    }

    @Transactional
    public CreateView create(
            AuthenticatedPrincipal principal,
            CreateCommand command,
            String requestId) {
        identity.requireActiveUser(principal.userId());
        if (command == null) throw new BusinessException("VALIDATION_FAILED", "request body is required");
        if (command.payoutDate() == null) throw new BusinessException("VALIDATION_FAILED", "payoutDate is required");
        if (command.items() == null || command.items().isEmpty()) {
            throw new BusinessException("VALIDATION_FAILED", "At least one payout item is required");
        }

        String method = payoutMethod(command.method());
        List<NormalizedItem> items = normalizeItems(command.items());
        List<LockedItem> lockedItems = new ArrayList<>();
        UUID organizationId = null;

        for (NormalizedItem item : items) {
            CoachSettlementPayoutRow coach = store.findCoachSettlementLocked(item.coachSettlementId())
                    .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Coach settlement was not found"));
            if (organizationId == null) {
                organizationId = coach.organizationId();
                requireCommittee(principal, organizationId);
            } else if (!organizationId.equals(coach.organizationId())) {
                throw new BusinessException("ORG_SCOPE_DENIED", "All payout items must belong to the same organization");
            }

            if (store.hasActiveBatchItem(coach.id())) {
                throw new BusinessException("CONCURRENT_MODIFICATION", "Coach settlement already belongs to an active payout batch");
            }
            if (!"READY".equals(coach.payoutStatus())) {
                throw new BusinessException("SETTLEMENT_NOT_READY", "Only a READY coach settlement can be added to a payout batch");
            }
            if (item.amount().compareTo(coach.outstandingAmount()) > 0) {
                throw new BusinessException("SETTLEMENT_NOT_READY", "Payout amount exceeds coach settlement outstanding amount");
            }
            BigDecimal allocated = store.nonCancelledAllocatedAmount(coach.id()).add(item.amount()).setScale(2);
            if (allocated.compareTo(coach.payableAmount()) > 0) {
                throw new BusinessException("SETTLEMENT_NOT_READY", "Payout items would exceed coach settlement payable amount");
            }
            lockedItems.add(new LockedItem(coach, item.amount()));
        }

        if (organizationId == null) throw new IllegalStateException("Validated payout items had no organization");
        store.lockOrganization(organizationId);
        String batchNo = store.nextBatchNo(organizationId, command.payoutDate());
        BigDecimal totalAmount = lockedItems.stream()
                .map(LockedItem::amount)
                .reduce(new BigDecimal("0.00"), BigDecimal::add)
                .setScale(2);
        UUID batchId = store.createBatch(
                organizationId, batchNo, command.payoutDate(), method, totalAmount, lockedItems.size(), principal.userId());

        for (LockedItem item : lockedItems) {
            store.createItem(batchId, item.coachSettlement(), item.amount());
            if (!store.markCoachInBatch(item.coachSettlement())) {
                throw new BusinessException("CONCURRENT_MODIFICATION", "Coach settlement changed while creating payout batch");
            }
        }

        var after = Map.of(
                "status", "DRAFT",
                "batchNo", batchNo,
                "method", method,
                "payoutDate", command.payoutDate(),
                "totalAmount", totalAmount,
                "itemCount", lockedItems.size());
        audit.record(organizationId, principal.userId(), "PAYOUT_BATCH_CREATED",
                "PayoutBatch", batchId, null, null, after, requestId);
        return new CreateView(batchId, batchNo, "DRAFT", method, totalAmount, lockedItems.size());
    }

    @Transactional
    public ExecutionView execute(
            AuthenticatedPrincipal principal,
            UUID batchId,
            ExecutionCommand command,
            String idempotencyKey,
            String requestId) {
        identity.requireActiveUser(principal.userId());
        PayoutBatchRow batch = store.findBatchLocked(batchId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Payout batch was not found"));
        requireCommittee(principal, batch.organizationId());

        if (command == null) throw new BusinessException("VALIDATION_FAILED", "request body is required");
        if (command.paidAt() == null) throw new BusinessException("VALIDATION_FAILED", "paidAt is required");
        String reason = requireReason(command.reason());
        String referenceNo = referenceNo(command.referenceNo());
        var idem = idempotency.begin(
                batch.organizationId(), principal.userId(), EXECUTE_OPERATION, idempotencyKey,
                batchId + "|" + command.paidAt() + "|" + (referenceNo == null ? "" : referenceNo) + "|" + reason);
        if (idem.getResultResourceId() != null) {
            PayoutBatchRow replay = store.findBatch(idem.getResultResourceId())
                    .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Executed payout batch was not found"));
            return toExecutionView(replay);
        }

        if ("COMPLETED".equals(batch.status())) {
            throw new BusinessException("PAYOUT_ALREADY_PROCESSED", "Payout batch was already completed");
        }
        if (!"DRAFT".equals(batch.status())) {
            throw new BusinessException("STATE_TRANSITION_INVALID", "Only a DRAFT payout batch can be executed");
        }

        List<PayoutBatchItemRow> batchItems = store.findBatchItems(batchId);
        validateBatchTotals(batch, batchItems);
        List<PayoutBatchItemRow> orderedItems = batchItems.stream()
                .sorted(Comparator.comparing(item -> item.coachSettlementId().toString()))
                .toList();
        List<LockedExecutionItem> lockedItems = new ArrayList<>();

        for (PayoutBatchItemRow item : orderedItems) {
            if (!"PLANNED".equals(item.status())) {
                throw new BusinessException("STATE_TRANSITION_INVALID", "All payout batch items must be PLANNED before execution");
            }
            CoachSettlementPayoutRow coach = store.findCoachSettlementLocked(item.coachSettlementId())
                    .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Coach settlement was not found"));
            if (!batch.organizationId().equals(coach.organizationId())) {
                throw new BusinessException("ORG_SCOPE_DENIED", "Payout item is outside the batch organization");
            }
            if (!"IN_BATCH".equals(coach.payoutStatus())) {
                throw new BusinessException("SETTLEMENT_NOT_READY", "Coach settlement is not reserved by an executable payout batch");
            }
            if (item.amount().compareTo(coach.outstandingAmount()) > 0) {
                throw new BusinessException("SETTLEMENT_NOT_READY", "Payout item exceeds coach settlement outstanding amount");
            }
            if (store.nonCancelledAllocatedAmount(coach.id()).compareTo(coach.payableAmount()) > 0) {
                throw new BusinessException("SETTLEMENT_NOT_READY", "Payout allocations exceed coach settlement payable amount");
            }
            lockedItems.add(new LockedExecutionItem(item, coach));
        }

        Instant approvedAt = Instant.now();
        if (!store.approveBatch(batchId, batch.version(), principal.userId(), approvedAt)) {
            throw new BusinessException("CONCURRENT_MODIFICATION", "Payout batch changed before approval");
        }
        if (!store.markBatchProcessing(batchId)) {
            throw new BusinessException("CONCURRENT_MODIFICATION", "Payout batch changed before processing");
        }
        for (LockedExecutionItem item : lockedItems) {
            if (!store.markItemPaid(item.item().id(), principal.userId(), command.paidAt(), referenceNo)) {
                throw new BusinessException("CONCURRENT_MODIFICATION", "Payout item changed during execution");
            }
            if (!store.addCoachPaidAmount(item.coachSettlement().id(), item.item().amount())) {
                throw new BusinessException("CONCURRENT_MODIFICATION", "Coach settlement changed during payout execution");
            }
        }
        if (!store.completeBatch(batchId, command.paidAt())) {
            throw new BusinessException("CONCURRENT_MODIFICATION", "Payout batch changed before completion");
        }

        PayoutBatchRow completed = store.findBatch(batchId)
                .orElseThrow(() -> new IllegalStateException("Completed payout batch disappeared"));
        var before = Map.of("status", batch.status());
        var after = Map.of(
                "status", completed.status(),
                "approvedBy", principal.userId(),
                "approvedAt", approvedAt,
                "completedAt", command.paidAt(),
                "totalAmount", completed.totalAmount(),
                "itemCount", completed.itemCount());
        audit.record(batch.organizationId(), principal.userId(), "PAYOUT_BATCH_EXECUTED",
                "PayoutBatch", batchId, reason, before, after, requestId);
        idem.complete("PayoutBatch", batchId, 200);
        return toExecutionView(completed);
    }

    @Transactional
    public PayoutBatchView get(AuthenticatedPrincipal principal, UUID batchId) {
        identity.requireActiveUser(principal.userId());
        PayoutBatchRow batch = store.findBatch(batchId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Payout batch was not found"));
        requireCommittee(principal, batch.organizationId());
        List<PayoutBatchItemRow> items = store.findBatchItems(batchId);
        return new PayoutBatchView(
                batch.id(), batch.batchNo(), batch.status(), batch.payoutDate(), batch.method(), batch.currency(),
                batch.totalAmount(), batch.itemCount(), batch.approvedAt(), batch.completedAt(),
                items.stream().map(item -> new PayoutItemView(
                        item.id(), item.coachSettlementId(), item.coachProfileId(), item.amount(), item.status(),
                        item.paidAt(), item.referenceNo(), item.failureReason())).toList());
    }

    private static List<NormalizedItem> normalizeItems(List<CreateItem> rawItems) {
        Set<UUID> ids = new HashSet<>();
        List<NormalizedItem> normalized = new ArrayList<>();
        for (CreateItem item : rawItems) {
            if (item == null || item.coachSettlementId() == null) {
                throw new BusinessException("VALIDATION_FAILED", "coachSettlementId is required for every payout item");
            }
            if (!ids.add(item.coachSettlementId())) {
                throw new BusinessException("VALIDATION_FAILED", "Duplicate coachSettlementId is not allowed in a payout batch");
            }
            BigDecimal amount = money(item.amount(), "amount");
            if (amount.signum() <= 0) throw new BusinessException("VALIDATION_FAILED", "Payout amount must be greater than zero");
            normalized.add(new NormalizedItem(item.coachSettlementId(), amount));
        }
        normalized.sort(Comparator.comparing(item -> item.coachSettlementId().toString()));
        return normalized;
    }

    private static void validateBatchTotals(PayoutBatchRow batch, List<PayoutBatchItemRow> items) {
        if (items.size() != batch.itemCount()) {
            throw new BusinessException("CONCURRENT_MODIFICATION", "Payout batch item count does not match its persisted total");
        }
        BigDecimal sum = items.stream()
                .map(PayoutBatchItemRow::amount)
                .reduce(new BigDecimal("0.00"), BigDecimal::add)
                .setScale(2);
        if (sum.compareTo(batch.totalAmount()) != 0) {
            throw new BusinessException("CONCURRENT_MODIFICATION", "Payout batch amount does not match its persisted items");
        }
    }

    private ExecutionView toExecutionView(PayoutBatchRow batch) {
        return new ExecutionView(batch.id(), batch.status(), batch.totalAmount(), batch.itemCount(), batch.completedAt());
    }

    private void requireCommittee(AuthenticatedPrincipal principal, UUID organizationId) {
        if (!identity.isAuthorizedForOrganization(principal, RoleCode.COMMITTEE, organizationId)) {
            throw new BusinessException("ORG_SCOPE_DENIED", "Committee or platform administrator access is required for this organization");
        }
    }

    private static String payoutMethod(String method) {
        if (method == null || method.isBlank()) return "CASH";
        String value = method.trim().toUpperCase();
        if (!METHODS.contains(value)) throw new BusinessException("VALIDATION_FAILED", "Unsupported payout method");
        return value;
    }

    private static BigDecimal money(BigDecimal value, String field) {
        if (value == null) throw new BusinessException("VALIDATION_FAILED", field + " is required");
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

    private static String referenceNo(String referenceNo) {
        if (referenceNo == null || referenceNo.isBlank()) return null;
        String value = referenceNo.trim();
        if (value.length() > 100) throw new BusinessException("VALIDATION_FAILED", "referenceNo supports at most 100 characters");
        return value;
    }

    public record CreateCommand(String method, LocalDate payoutDate, List<CreateItem> items) {}
    public record CreateItem(UUID coachSettlementId, BigDecimal amount) {}
    public record CreateView(UUID payoutBatchId, String batchNo, String status, String method, BigDecimal totalAmount, int itemCount) {}

    public record ExecutionCommand(Instant paidAt, String referenceNo, String reason) {}
    public record ExecutionView(UUID payoutBatchId, String status, BigDecimal totalAmount, int itemCount, Instant completedAt) {}

    public record PayoutBatchView(
            UUID payoutBatchId,
            String batchNo,
            String status,
            LocalDate payoutDate,
            String method,
            String currency,
            BigDecimal totalAmount,
            int itemCount,
            Instant approvedAt,
            Instant completedAt,
            List<PayoutItemView> items) {}

    public record PayoutItemView(
            UUID payoutBatchItemId,
            UUID coachSettlementId,
            UUID coachProfileId,
            BigDecimal amount,
            String status,
            Instant paidAt,
            String referenceNo,
            String failureReason) {}

    private record NormalizedItem(UUID coachSettlementId, BigDecimal amount) {}
    private record LockedItem(CoachSettlementPayoutRow coachSettlement, BigDecimal amount) {}
    private record LockedExecutionItem(PayoutBatchItemRow item, CoachSettlementPayoutRow coachSettlement) {}
}
