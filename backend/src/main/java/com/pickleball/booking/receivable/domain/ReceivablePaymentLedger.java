package com.pickleball.booking.receivable.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Domain aggregate used while recording a payment. Persistence locking is handled by the repository,
 * while this type owns amount and status invariants.
 */
public final class ReceivablePaymentLedger {
    private final UUID id;
    private final UUID organizationId;
    private final UUID payerUserId;
    private final BigDecimal totalAmount;
    private final BigDecimal adjustedAmount;
    private BigDecimal paidAmount;
    private final BigDecimal refundedAmount;
    private BigDecimal balanceAmount;
    private String persistenceStatus;
    private final List<Item> items;

    public ReceivablePaymentLedger(
            UUID id,
            UUID organizationId,
            UUID payerUserId,
            BigDecimal totalAmount,
            BigDecimal adjustedAmount,
            BigDecimal paidAmount,
            BigDecimal refundedAmount,
            BigDecimal balanceAmount,
            String persistenceStatus,
            List<Item> items) {
        this.id = id;
        this.organizationId = organizationId;
        this.payerUserId = payerUserId;
        this.totalAmount = money(totalAmount);
        this.adjustedAmount = money(adjustedAmount);
        this.paidAmount = money(paidAmount);
        this.refundedAmount = money(refundedAmount);
        this.balanceAmount = money(balanceAmount);
        this.persistenceStatus = persistenceStatus;
        this.items = new ArrayList<>(items);
        verifyStoredBalance();
    }

    public PaymentApplication recordPayment(BigDecimal rawAmount) {
        BigDecimal amount = requirePositiveMoney(rawAmount);
        if ("CANCELLED".equals(persistenceStatus)) {
            throw new ReceivablePaymentRuleViolation(
                    "STATE_TRANSITION_INVALID", "Cancelled receivable cannot accept payments");
        }
        if (balanceAmount.signum() <= 0 || amount.compareTo(balanceAmount) > 0) {
            throw new ReceivablePaymentRuleViolation(
                    "PAYMENT_AMOUNT_INVALID", "Payment exceeds receivable outstanding amount");
        }

        BigDecimal allocatable = items.stream()
                .filter(Item::isAllocatable)
                .map(Item::outstandingAmount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        if (amount.compareTo(allocatable) > 0) {
            throw new ReceivablePaymentRuleViolation(
                    "PAYMENT_AMOUNT_INVALID", "Payment cannot be fully allocated to receivable items");
        }

        BigDecimal remaining = amount;
        List<Allocation> allocations = new ArrayList<>();
        for (Item item : items) {
            if (remaining.signum() == 0) break;
            if (!item.isAllocatable()) continue;
            BigDecimal itemOutstanding = item.outstandingAmount();
            if (itemOutstanding.signum() <= 0) continue;
            BigDecimal allocated = remaining.min(itemOutstanding).setScale(2, RoundingMode.UNNECESSARY);
            item.applyPayment(allocated);
            allocations.add(new Allocation(item.id(), allocated));
            remaining = remaining.subtract(allocated).setScale(2, RoundingMode.UNNECESSARY);
        }
        if (remaining.signum() != 0) {
            throw new IllegalStateException("Payment allocation did not consume the full payment amount");
        }

        paidAmount = paidAmount.add(amount).setScale(2, RoundingMode.UNNECESSARY);
        balanceAmount = totalAmount.add(adjustedAmount).subtract(paidAmount).add(refundedAmount)
                .setScale(2, RoundingMode.UNNECESSARY);
        if (balanceAmount.signum() == 0) {
            persistenceStatus = "PAID";
        } else if (!"OVERDUE".equals(persistenceStatus)) {
            persistenceStatus = "PARTIALLY_PAID";
        }
        return new PaymentApplication(amount, List.copyOf(allocations), paymentStatus(), paidAmount, balanceAmount);
    }

    public String paymentStatus() {
        BigDecimal netPaid = paidAmount.subtract(refundedAmount);
        if (balanceAmount.signum() == 0) return "PAID";
        if (netPaid.signum() > 0) return "PARTIALLY_PAID";
        return "UNPAID";
    }

    private void verifyStoredBalance() {
        BigDecimal expected = totalAmount.add(adjustedAmount).subtract(paidAmount).add(refundedAmount)
                .setScale(2, RoundingMode.UNNECESSARY);
        if (expected.compareTo(balanceAmount) != 0) {
            throw new IllegalStateException("Stored receivable balance is inconsistent");
        }
    }

    public static BigDecimal requirePositiveMoney(BigDecimal value) {
        BigDecimal normalized = money(value);
        if (normalized.signum() <= 0) {
            throw new ReceivablePaymentRuleViolation("PAYMENT_AMOUNT_INVALID", "Payment amount must be positive");
        }
        return normalized;
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null) {
            throw new ReceivablePaymentRuleViolation("PAYMENT_AMOUNT_INVALID", "Payment amount is required");
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new ReceivablePaymentRuleViolation(
                    "PAYMENT_AMOUNT_INVALID", "Money values support at most two decimal places");
        }
    }

    public UUID id() { return id; }
    public UUID organizationId() { return organizationId; }
    public UUID payerUserId() { return payerUserId; }
    public BigDecimal paidAmount() { return paidAmount; }
    public BigDecimal balanceAmount() { return balanceAmount; }
    public String persistenceStatus() { return persistenceStatus; }
    public List<Item> items() { return List.copyOf(items); }

    public record Allocation(UUID receivableItemId, BigDecimal amount) {}
    public record PaymentApplication(
            BigDecimal amount,
            List<Allocation> allocations,
            String paymentStatus,
            BigDecimal paidTotal,
            BigDecimal outstandingAmount) {}

    public static final class Item {
        private final UUID id;
        private final BigDecimal amount;
        private BigDecimal paidAmount;
        private final BigDecimal refundedAmount;
        private String status;

        public Item(UUID id, BigDecimal amount, BigDecimal paidAmount, BigDecimal refundedAmount, String status) {
            this.id = id;
            this.amount = money(amount);
            this.paidAmount = money(paidAmount);
            this.refundedAmount = money(refundedAmount);
            this.status = status;
        }

        public BigDecimal outstandingAmount() {
            BigDecimal outstanding = amount.subtract(paidAmount).add(refundedAmount)
                    .setScale(2, RoundingMode.UNNECESSARY);
            return outstanding.signum() < 0 ? BigDecimal.ZERO.setScale(2) : outstanding;
        }

        boolean isAllocatable() {
            return !"CANCELLED".equals(status) && outstandingAmount().signum() > 0;
        }

        void applyPayment(BigDecimal allocation) {
            paidAmount = paidAmount.add(allocation).setScale(2, RoundingMode.UNNECESSARY);
            status = outstandingAmount().signum() == 0 ? "PAID" : "PARTIALLY_PAID";
        }

        public UUID id() { return id; }
        public BigDecimal paidAmount() { return paidAmount; }
        public String status() { return status; }
    }
}
