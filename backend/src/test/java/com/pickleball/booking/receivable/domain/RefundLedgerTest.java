package com.pickleball.booking.receivable.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefundLedgerTest {

    @Test
    void pendingRefundCanBeApprovedThenCompleted() {
        UUID refundId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        var refund = pending(refundId, "600.00");
        Instant approvedAt = Instant.parse("2026-08-26T00:00:00Z");
        Instant refundedAt = Instant.parse("2026-08-26T00:30:00Z");

        refund.approve(actor, approvedAt, "approved");
        refund.complete(actor, refundedAt, PaymentMethod.BANK_TRANSFER, "12345");

        assertThat(refund.status()).isEqualTo("COMPLETED");
        assertThat(refund.approvedBy()).isEqualTo(actor);
        assertThat(refund.approvedAt()).isEqualTo(approvedAt);
        assertThat(refund.processedBy()).isEqualTo(actor);
        assertThat(refund.refundedAt()).isEqualTo(refundedAt);
        assertThat(refund.refundMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
    }

    @Test
    void refundCannotExecuteBeforeApproval() {
        var refund = pending(UUID.randomUUID(), "600.00");

        assertCode(() -> refund.complete(
                UUID.randomUUID(), Instant.now(), PaymentMethod.CASH, null), "REFUND_NOT_APPROVED");
    }

    @Test
    void completedRefundCannotBeProcessedAgain() {
        UUID actor = UUID.randomUUID();
        var refund = pending(UUID.randomUUID(), "600.00");
        refund.approve(actor, Instant.now(), null);
        refund.complete(actor, Instant.now(), PaymentMethod.CASH, null);

        assertCode(() -> refund.complete(
                actor, Instant.now(), PaymentMethod.CASH, null), "REFUND_ALREADY_PROCESSED");
    }

    @Test
    void pendingRefundCanBeRejectedButNotApprovedAfterward() {
        var refund = pending(UUID.randomUUID(), "600.00");
        refund.reject("not refundable");

        assertThat(refund.status()).isEqualTo("REJECTED");
        assertCode(() -> refund.approve(UUID.randomUUID(), Instant.now(), null), "STATE_TRANSITION_INVALID");
    }

    @Test
    void amountMustBePositiveWithAtMostTwoDecimals() {
        assertCode(() -> RefundLedger.requirePositiveMoney(BigDecimal.ZERO), "VALIDATION_FAILED");
        assertCode(() -> RefundLedger.requirePositiveMoney(new BigDecimal("1.001")), "VALIDATION_FAILED");
    }

    private static RefundLedger pending(UUID refundId, String amount) {
        return new RefundLedger(
                refundId, UUID.randomUUID(), UUID.randomUUID(), new BigDecimal(amount), "student withdrawal", UUID.randomUUID(),
                "PENDING_APPROVAL", null, null, null, null, null, null, null, null);
    }

    private static void assertCode(Runnable call, String code) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(RefundRuleViolation.class, ex -> assertThat(ex.code()).isEqualTo(code));
    }
}
