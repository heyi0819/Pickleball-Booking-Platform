package com.pickleball.booking.receivable.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class FinanceReadViews {
    private FinanceReadViews() {}
    public record Page<T>(List<T> items, int page, int size, long totalElements) {}
    public record Receivable(UUID id, String receivableNo, UUID organizationId, String organizationName,
            UUID memberId, String memberName, UUID courseId, String courseNo, String currency,
            String totalAmount, String adjustedAmount, String paidAmount, String refundedAmount,
            String outstandingAmount, String status, Instant createdAt, Instant dueAt) {}
    public record ReceivableReference(UUID id, String receivableNo, UUID courseId, String courseNo) {}
    public record Payment(UUID id, String paymentNo, UUID organizationId, String organizationName,
            UUID memberId, String memberName, String amount, String currency, String status,
            String method, Instant paidAt, Instant recordedAt, String refundableAmount,
            List<ReceivableReference> receivables) {}
    public record Refund(UUID id, String refundNo, UUID organizationId, String organizationName,
            UUID paymentId, String paymentNo, UUID memberId, String memberName, String amount,
            String currency, String status, String reason, Instant requestedAt, Instant approvedAt,
            Instant refundedAt, String refundableAmount, List<ReceivableReference> receivables) {}
}
