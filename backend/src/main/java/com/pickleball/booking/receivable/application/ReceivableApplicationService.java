package com.pickleball.booking.receivable.application;

import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.identity.application.IdentityService;
import com.pickleball.booking.identity.domain.RoleCode;
import com.pickleball.booking.receivable.domain.PaymentMethod;
import com.pickleball.booking.receivable.domain.ReceivablePaymentLedger;
import com.pickleball.booking.receivable.domain.ReceivablePaymentRuleViolation;
import com.pickleball.booking.receivable.infrastructure.ReceivablePaymentStore;
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
public class ReceivableApplicationService {
    private static final String RECORD_PAYMENT_OPERATION = "RECEIVABLE_RECORD_PAYMENT";

    private final ReceivablePaymentStore store;
    private final IdentityService identity;
    private final IdempotencyService idempotency;
    private final AuditOutboxService audit;

    public ReceivableApplicationService(
            ReceivablePaymentStore store,
            IdentityService identity,
            IdempotencyService idempotency,
            AuditOutboxService audit) {
        this.store = store;
        this.identity = identity;
        this.idempotency = idempotency;
        this.audit = audit;
    }

    @Transactional
    public PaymentResult recordPayment(
            AuthenticatedPrincipal actor,
            UUID receivableId,
            RecordPaymentCommand command,
            String idempotencyKey,
            String requestId) {
        if (actor == null || actor.userId() == null || receivableId == null || command == null) {
            throw new BusinessException("VALIDATION_FAILED", "Payment command is incomplete");
        }
        identity.requireActiveUser(actor.userId());
        BigDecimal amount;
        try {
            amount = ReceivablePaymentLedger.requirePositiveMoney(command.amount());
        } catch (ReceivablePaymentRuleViolation ex) {
            throw business(ex);
        }
        if (command.method() == null || command.paidAt() == null || command.payerUserId() == null) {
            throw new BusinessException("VALIDATION_FAILED", "Payment method, paidAt and payerUserId are required");
        }
        if (command.paidAt().isAfter(Instant.now())) {
            throw new BusinessException("VALIDATION_FAILED", "Payment time cannot be in the future");
        }

        ReceivablePaymentLedger ledger = store.findLockedById(receivableId)
                .orElseThrow(() -> new BusinessException("RECEIVABLE_NOT_FOUND", "Receivable was not found"));
        if (!identity.isAuthorizedForOrganization(actor, RoleCode.COMMITTEE, ledger.organizationId())) {
            throw new BusinessException("AUTH_FORBIDDEN", "Committee or platform administrator permission is required");
        }
        if (!ledger.payerUserId().equals(command.payerUserId())) {
            throw new BusinessException("VALIDATION_FAILED", "payerUserId must match the receivable payer");
        }

        String requestIdentity = String.join("|",
                receivableId.toString(),
                amount.toPlainString(),
                command.method().name(),
                command.paidAt().toString(),
                command.payerUserId().toString(),
                Objects.toString(normalize(command.note()), ""));
        var idem = idempotency.begin(
                ledger.organizationId(), actor.userId(), RECORD_PAYMENT_OPERATION, idempotencyKey, requestIdentity);
        if (idem.getResultResourceId() != null) {
            var payment = store.findPaymentForReceivable(idem.getResultResourceId(), receivableId)
                    .orElseThrow(() -> new IllegalStateException("Idempotent payment result is missing"));
            return new PaymentResult(
                    payment.id(), receivableId, payment.amount(), payment.method(), ledger.paymentStatus(),
                    ledger.paidAmount(), ledger.balanceAmount());
        }

        Map<String, Object> before = receivableState(ledger);
        ReceivablePaymentLedger.PaymentApplication applied;
        try {
            applied = ledger.recordPayment(amount);
        } catch (ReceivablePaymentRuleViolation ex) {
            throw business(ex);
        }
        UUID paymentId = store.insertPayment(
                ledger, applied, command.method(), command.paidAt(), actor.userId(), idempotencyKey, command.note());

        Map<String, Object> paymentAfter = new LinkedHashMap<>();
        paymentAfter.put("receivableId", receivableId);
        paymentAfter.put("amount", applied.amount());
        paymentAfter.put("method", command.method());
        paymentAfter.put("paidAt", command.paidAt());
        paymentAfter.put("paymentStatus", applied.paymentStatus());
        audit.record(
                ledger.organizationId(), actor.userId(), "PAYMENT_RECORDED", "Payment", paymentId,
                normalize(command.note()), null, paymentAfter, requestId);
        audit.record(
                ledger.organizationId(), actor.userId(),
                "PAID".equals(applied.paymentStatus()) ? "RECEIVABLE_PAID" : "RECEIVABLE_PARTIALLY_PAID",
                "Receivable", receivableId, null, before, receivableState(ledger), requestId);

        idem.complete("Payment", paymentId, 201);
        return new PaymentResult(
                paymentId, receivableId, applied.amount(), command.method(), applied.paymentStatus(),
                applied.paidTotal(), applied.outstandingAmount());
    }

    private static BusinessException business(ReceivablePaymentRuleViolation ex) {
        return new BusinessException(ex.code(), ex.getMessage());
    }

    private static Map<String, Object> receivableState(ReceivablePaymentLedger ledger) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("paidAmount", ledger.paidAmount());
        state.put("balanceAmount", ledger.balanceAmount());
        state.put("status", ledger.persistenceStatus());
        return state;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record RecordPaymentCommand(
            BigDecimal amount,
            PaymentMethod method,
            Instant paidAt,
            UUID payerUserId,
            String note) {}

    public record PaymentResult(
            UUID paymentId,
            UUID receivableId,
            BigDecimal amount,
            PaymentMethod method,
            String paymentStatus,
            BigDecimal paidTotal,
            BigDecimal outstandingAmount) {}
}
