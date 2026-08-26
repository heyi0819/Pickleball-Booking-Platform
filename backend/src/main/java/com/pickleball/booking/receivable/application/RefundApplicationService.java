package com.pickleball.booking.receivable.application;

import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.identity.application.IdentityService;
import com.pickleball.booking.identity.domain.RoleCode;
import com.pickleball.booking.receivable.domain.PaymentMethod;
import com.pickleball.booking.receivable.domain.RefundLedger;
import com.pickleball.booking.receivable.domain.RefundRuleViolation;
import com.pickleball.booking.receivable.infrastructure.RefundStore;
import com.pickleball.booking.shared.application.AuditOutboxService;
import com.pickleball.booking.shared.application.BusinessException;
import com.pickleball.booking.shared.application.IdempotencyService;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RefundApplicationService {
    private static final String REQUEST_OPERATION = "REFUND_REQUEST";
    private static final String REVIEW_OPERATION = "REFUND_REVIEW";
    private static final String EXECUTE_OPERATION = "REFUND_EXECUTION";

    private final RefundStore store;
    private final IdentityService identity;
    private final IdempotencyService idempotency;
    private final AuditOutboxService audit;

    public RefundApplicationService(
            RefundStore store,
            IdentityService identity,
            IdempotencyService idempotency,
            AuditOutboxService audit) {
        this.store = store;
        this.identity = identity;
        this.idempotency = idempotency;
        this.audit = audit;
    }

    @Transactional
    public RefundResult requestRefund(
            AuthenticatedPrincipal actor,
            UUID receivableId,
            RequestRefundCommand command,
            String idempotencyKey,
            String requestId) {
        requireActorAndCommand(actor, command);
        if (receivableId == null || command.paymentId() == null) {
            throw new BusinessException("VALIDATION_FAILED", "Receivable and payment are required");
        }
        identity.requireActiveUser(actor.userId());
        BigDecimal amount = positiveMoney(command.amount());
        String reason = reason(command.reason());

        RefundStore.RefundRequestContext context = store.findRequestContextLocked(receivableId, command.paymentId())
                .orElseThrow(() -> new BusinessException(
                        "PAYMENT_NOT_FOUND", "Payment does not belong to the target receivable"));
        requireFinancePermission(actor, context.receivable().organizationId());

        String requestIdentity = String.join("|",
                receivableId.toString(), command.paymentId().toString(), amount.toPlainString(), reason);
        var idem = idempotency.begin(
                context.receivable().organizationId(), actor.userId(), REQUEST_OPERATION,
                idempotencyKey, requestIdentity);
        if (idem.getResultResourceId() != null) {
            return result(store.findRefund(idem.getResultResourceId())
                    .orElseThrow(() -> new IllegalStateException("Idempotent refund result is missing")));
        }

        if (!isRefundablePaymentStatus(context.payment().status())) {
            throw new BusinessException("PAYMENT_NOT_COMPLETED", "Only completed payments can be refunded");
        }
        BigDecimal available = context.payment().amount().subtract(context.reservedRefundAmount());
        if (amount.compareTo(available) > 0) {
            throw new BusinessException("REFUND_EXCEEDS_REFUNDABLE", "Refund exceeds payment refundable amount");
        }

        UUID refundId = store.insertRefund(context, amount, reason, actor.userId());
        RefundLedger refund = store.findRefund(refundId)
                .orElseThrow(() -> new IllegalStateException("Refund was not persisted"));
        audit.record(
                refund.organizationId(), actor.userId(), "REFUND_REQUESTED", "Refund", refundId,
                reason, null, refundState(refund), requestId);
        idem.complete("Refund", refundId, 201);
        return result(refund);
    }

    @Transactional
    public RefundResult reviewRefund(
            AuthenticatedPrincipal actor,
            UUID refundId,
            ReviewRefundCommand command,
            String idempotencyKey,
            String requestId) {
        requireActorAndCommand(actor, command);
        if (refundId == null || command.decision() == null) {
            throw new BusinessException("VALIDATION_FAILED", "Refund and review decision are required");
        }
        identity.requireActiveUser(actor.userId());
        RefundLedger refund = store.findRefundLocked(refundId)
                .orElseThrow(() -> new BusinessException("REFUND_NOT_FOUND", "Refund was not found"));
        requireFinancePermission(actor, refund.organizationId());

        String reviewReason = normalize(command.reason());
        String requestIdentity = String.join("|",
                refundId.toString(), command.decision().name(), Objects.toString(reviewReason, ""));
        var idem = idempotency.begin(
                refund.organizationId(), actor.userId(), REVIEW_OPERATION, idempotencyKey, requestIdentity);
        if (idem.getResultResourceId() != null) {
            return result(store.findRefund(idem.getResultResourceId())
                    .orElseThrow(() -> new IllegalStateException("Idempotent refund review result is missing")));
        }

        Map<String, Object> before = refundState(refund);
        try {
            if (command.decision() == ReviewDecision.APPROVE) {
                RefundStore.PaymentRow payment = store.lockPayment(refund.paymentId());
                if (!isRefundablePaymentStatus(payment.status())) {
                    throw new BusinessException("PAYMENT_NOT_COMPLETED", "Payment is no longer refundable");
                }
                BigDecimal committed = store.activeReservedRefundAmount(refund.paymentId(), refund.id())
                        .add(refund.amount());
                if (committed.compareTo(payment.amount()) > 0) {
                    throw new BusinessException(
                            "REFUND_EXCEEDS_REFUNDABLE", "Approved refunds would exceed payment amount");
                }
                refund.approve(actor.userId(), Instant.now(), reviewReason);
            } else {
                refund.reject(reason(reviewReason));
            }
        } catch (RefundRuleViolation ex) {
            throw business(ex);
        }
        store.saveReview(refund);
        String action = command.decision() == ReviewDecision.APPROVE ? "REFUND_APPROVED" : "REFUND_REJECTED";
        audit.record(
                refund.organizationId(), actor.userId(), action, "Refund", refund.id(),
                reviewReason, before, refundState(refund), requestId);
        idem.complete("Refund", refund.id(), 200);
        return result(refund);
    }

    @Transactional
    public RefundResult executeRefund(
            AuthenticatedPrincipal actor,
            UUID refundId,
            ExecuteRefundCommand command,
            String idempotencyKey,
            String requestId) {
        requireActorAndCommand(actor, command);
        if (refundId == null || command.method() == null || command.refundedAt() == null) {
            throw new BusinessException("VALIDATION_FAILED", "Refund execution metadata is required");
        }
        if (command.refundedAt().isAfter(Instant.now())) {
            throw new BusinessException("VALIDATION_FAILED", "Refund time cannot be in the future");
        }
        identity.requireActiveUser(actor.userId());
        RefundLedger refund = store.findRefundLocked(refundId)
                .orElseThrow(() -> new BusinessException("REFUND_NOT_FOUND", "Refund was not found"));
        requireFinancePermission(actor, refund.organizationId());

        String reference = normalize(command.reference());
        String requestIdentity = String.join("|",
                refundId.toString(), command.method().name(), command.refundedAt().toString(),
                Objects.toString(reference, ""));
        var idem = idempotency.begin(
                refund.organizationId(), actor.userId(), EXECUTE_OPERATION, idempotencyKey, requestIdentity);
        if (idem.getResultResourceId() != null) {
            return result(store.findRefund(idem.getResultResourceId())
                    .orElseThrow(() -> new IllegalStateException("Idempotent refund execution result is missing")));
        }

        Map<String, Object> before = refundState(refund);
        RefundStore.ExecutionContext financial = store.findExecutionContextLocked(refund);
        if (!financial.receivable().organizationId().equals(refund.organizationId())) {
            throw new IllegalStateException("Refund organization does not match receivable organization");
        }
        BigDecimal completedAfter = financial.completedRefundAmount().add(refund.amount());
        if (completedAfter.compareTo(financial.payment().amount()) > 0) {
            throw new BusinessException("REFUND_EXCEEDS_REFUNDABLE", "Completed refunds exceed payment amount");
        }
        try {
            refund.complete(actor.userId(), command.refundedAt(), command.method(), reference);
        } catch (RefundRuleViolation ex) {
            throw business(ex);
        }
        RefundStore.FinancialResult applied = store.completeRefund(refund, financial);

        Map<String, Object> after = refundState(refund);
        after.put("receivableId", applied.receivableId());
        after.put("paymentStatus", applied.paymentStatus());
        after.put("receivableStatus", applied.receivableStatus());
        after.put("refundedTotal", applied.refundedTotal());
        after.put("balanceAmount", applied.balanceAmount());
        audit.record(
                refund.organizationId(), actor.userId(), "REFUND_COMPLETED", "Refund", refund.id(),
                reference, before, after, requestId);
        idem.complete("Refund", refund.id(), 200);
        return result(refund);
    }

    private void requireFinancePermission(AuthenticatedPrincipal actor, UUID organizationId) {
        if (!identity.isAuthorizedForOrganization(actor, RoleCode.COMMITTEE, organizationId)) {
            throw new BusinessException(
                    "AUTH_FORBIDDEN", "Committee or platform administrator permission is required");
        }
    }

    private static boolean isRefundablePaymentStatus(String status) {
        return "COMPLETED".equals(status) || "PARTIALLY_REFUNDED".equals(status);
    }

    private static void requireActorAndCommand(AuthenticatedPrincipal actor, Object command) {
        if (actor == null || actor.userId() == null || command == null) {
            throw new BusinessException("VALIDATION_FAILED", "Refund command is incomplete");
        }
    }

    private static BigDecimal positiveMoney(BigDecimal value) {
        try {
            return RefundLedger.requirePositiveMoney(value);
        } catch (RefundRuleViolation ex) {
            throw business(ex);
        }
    }

    private static String reason(String value) {
        try {
            return RefundLedger.requireReason(value);
        } catch (RefundRuleViolation ex) {
            throw business(ex);
        }
    }

    private static BusinessException business(RefundRuleViolation ex) {
        return new BusinessException(ex.code(), ex.getMessage());
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Map<String, Object> refundState(RefundLedger refund) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("paymentId", refund.paymentId());
        state.put("amount", refund.amount());
        state.put("status", refund.status());
        state.put("approvedBy", refund.approvedBy());
        state.put("approvedAt", refund.approvedAt());
        state.put("processedBy", refund.processedBy());
        state.put("refundedAt", refund.refundedAt());
        state.put("method", refund.refundMethod());
        return state;
    }

    private static RefundResult result(RefundLedger refund) {
        return new RefundResult(
                refund.id(), refund.paymentId(), refund.amount(), refund.status(),
                refund.approvedBy(), refund.approvedAt(), refund.processedBy(), refund.refundedAt(),
                refund.refundMethod());
    }

    public enum ReviewDecision { APPROVE, REJECT }

    public record RequestRefundCommand(UUID paymentId, BigDecimal amount, String reason) {}
    public record ReviewRefundCommand(ReviewDecision decision, String reason) {}
    public record ExecuteRefundCommand(PaymentMethod method, Instant refundedAt, String reference) {}
    public record RefundResult(
            UUID refundId,
            UUID paymentId,
            BigDecimal amount,
            String status,
            UUID approvedBy,
            Instant approvedAt,
            UUID processedBy,
            Instant refundedAt,
            PaymentMethod method) {}
}
