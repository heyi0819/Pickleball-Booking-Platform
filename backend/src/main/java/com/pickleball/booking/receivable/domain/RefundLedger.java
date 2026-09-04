package com.pickleball.booking.receivable.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** Refund aggregate. Cross-aggregate refundable balance is checked by the application transaction. */
public final class RefundLedger {
    private final UUID id;
    private final UUID organizationId;
    private final UUID paymentId;
    private final BigDecimal amount;
    private final String reason;
    private final UUID requestedBy;
    private String status;
    private UUID approvedBy;
    private Instant approvedAt;
    private String approvalNote;
    private UUID processedBy;
    private Instant refundedAt;
    private PaymentMethod refundMethod;
    private String referenceNo;
    private String failureReason;

    public RefundLedger(
            UUID id,
            UUID organizationId,
            UUID paymentId,
            BigDecimal amount,
            String reason,
            UUID requestedBy,
            String status,
            UUID approvedBy,
            Instant approvedAt,
            String approvalNote,
            UUID processedBy,
            Instant refundedAt,
            PaymentMethod refundMethod,
            String referenceNo,
            String failureReason) {
        this.id = id;
        this.organizationId = organizationId;
        this.paymentId = paymentId;
        this.amount = requirePositiveMoney(amount);
        this.reason = requireReason(reason);
        this.requestedBy = requestedBy;
        this.status = status;
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
        this.approvalNote = normalize(approvalNote);
        this.processedBy = processedBy;
        this.refundedAt = refundedAt;
        this.refundMethod = refundMethod;
        this.referenceNo = normalize(referenceNo);
        this.failureReason = normalize(failureReason);
    }

    public void approve(UUID actorUserId, Instant approvedAt, String note) {
        requirePendingReview();
        if (actorUserId == null || approvedAt == null) {
            throw new RefundRuleViolation("VALIDATION_FAILED", "Approval actor and time are required");
        }
        status = "APPROVED";
        approvedBy = actorUserId;
        this.approvedAt = approvedAt;
        approvalNote = normalize(note);
    }

    public void reject(String reason) {
        requirePendingReview();
        status = "REJECTED";
        approvalNote = requireReason(reason);
    }

    public void complete(UUID actorUserId, Instant refundedAt, PaymentMethod method, String referenceNo) {
        if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
            throw new RefundRuleViolation("REFUND_ALREADY_PROCESSED", "Refund was already processed");
        }
        if (!"APPROVED".equals(status)) {
            throw new RefundRuleViolation("REFUND_NOT_APPROVED", "Refund must be approved before execution");
        }
        if (actorUserId == null || refundedAt == null || method == null) {
            throw new RefundRuleViolation("VALIDATION_FAILED", "Refund execution metadata is incomplete");
        }
        status = "COMPLETED";
        processedBy = actorUserId;
        this.refundedAt = refundedAt;
        refundMethod = method;
        this.referenceNo = normalize(referenceNo);
        failureReason = null;
    }

    public void markFailed(String reason) {
        if (!"APPROVED".equals(status)) {
            throw new RefundRuleViolation("REFUND_NOT_APPROVED", "Only approved refunds can fail execution");
        }
        status = "FAILED";
        failureReason = requireReason(reason);
    }

    private void requirePendingReview() {
        if (!"PENDING_APPROVAL".equals(status)) {
            throw new RefundRuleViolation("STATE_TRANSITION_INVALID", "Refund is no longer pending review");
        }
    }

    public static BigDecimal requirePositiveMoney(BigDecimal value) {
        if (value == null) {
            throw new RefundRuleViolation("VALIDATION_FAILED", "Refund amount is required");
        }
        try {
            BigDecimal normalized = value.setScale(2, RoundingMode.UNNECESSARY);
            if (normalized.signum() <= 0) {
                throw new RefundRuleViolation("VALIDATION_FAILED", "Refund amount must be positive");
            }
            return normalized;
        } catch (ArithmeticException ex) {
            throw new RefundRuleViolation("VALIDATION_FAILED", "Money values support at most two decimal places");
        }
    }

    public static String requireReason(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new RefundRuleViolation("VALIDATION_FAILED", "Refund reason is required");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID id() { return id; }
    public UUID organizationId() { return organizationId; }
    public UUID paymentId() { return paymentId; }
    public BigDecimal amount() { return amount; }
    public String reason() { return reason; }
    public UUID requestedBy() { return requestedBy; }
    public String status() { return status; }
    public UUID approvedBy() { return approvedBy; }
    public Instant approvedAt() { return approvedAt; }
    public String approvalNote() { return approvalNote; }
    public UUID processedBy() { return processedBy; }
    public Instant refundedAt() { return refundedAt; }
    public PaymentMethod refundMethod() { return refundMethod; }
    public String referenceNo() { return referenceNo; }
    public String failureReason() { return failureReason; }
}
