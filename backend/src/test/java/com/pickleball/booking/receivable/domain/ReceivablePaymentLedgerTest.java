package com.pickleball.booking.receivable.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReceivablePaymentLedgerTest {

    @Test
    void partialPaymentAllocatesInProvidedOrderAndUpdatesBalance() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        var ledger = ledger(
                "1200.00", "0.00", "0.00", "0.00", "1200.00", "OPEN",
                List.of(
                        item(firstId, "500.00", "0.00", "0.00", "OPEN"),
                        item(secondId, "700.00", "0.00", "0.00", "OPEN")));

        var result = ledger.recordPayment(new BigDecimal("600.00"));

        assertThat(result.paymentStatus()).isEqualTo("PARTIALLY_PAID");
        assertThat(result.paidTotal()).isEqualByComparingTo("600.00");
        assertThat(result.outstandingAmount()).isEqualByComparingTo("600.00");
        assertThat(result.allocations()).containsExactly(
                new ReceivablePaymentLedger.Allocation(firstId, new BigDecimal("500.00")),
                new ReceivablePaymentLedger.Allocation(secondId, new BigDecimal("100.00")));
        assertThat(ledger.items().get(0).status()).isEqualTo("PAID");
        assertThat(ledger.items().get(1).status()).isEqualTo("PARTIALLY_PAID");
    }

    @Test
    void finalPaymentClosesOutstandingBalance() {
        var ledger = ledger(
                "1200.00", "0.00", "600.00", "0.00", "600.00", "PARTIALLY_PAID",
                List.of(item(UUID.randomUUID(), "1200.00", "600.00", "0.00", "PARTIALLY_PAID")));

        var result = ledger.recordPayment(new BigDecimal("600.00"));

        assertThat(result.paymentStatus()).isEqualTo("PAID");
        assertThat(ledger.persistenceStatus()).isEqualTo("PAID");
        assertThat(ledger.balanceAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void overpaymentAndCancelledReceivableAreRejected() {
        var open = ledger(
                "1200.00", "0.00", "600.00", "0.00", "600.00", "PARTIALLY_PAID",
                List.of(item(UUID.randomUUID(), "1200.00", "600.00", "0.00", "PARTIALLY_PAID")));
        assertRuleCode(() -> open.recordPayment(new BigDecimal("600.01")), "PAYMENT_AMOUNT_INVALID");

        var cancelled = ledger(
                "1200.00", "0.00", "0.00", "0.00", "1200.00", "CANCELLED",
                List.of(item(UUID.randomUUID(), "1200.00", "0.00", "0.00", "OPEN")));
        assertRuleCode(() -> cancelled.recordPayment(new BigDecimal("100.00")), "STATE_TRANSITION_INVALID");
    }

    @Test
    void paymentMustBeFullyAllocatableToReceivableItems() {
        var ledger = ledger(
                "1200.00", "200.00", "0.00", "0.00", "1400.00", "OPEN",
                List.of(item(UUID.randomUUID(), "1200.00", "0.00", "0.00", "OPEN")));

        assertRuleCode(() -> ledger.recordPayment(new BigDecimal("1300.00")), "PAYMENT_AMOUNT_INVALID");
    }

    private static ReceivablePaymentLedger ledger(
            String total, String adjusted, String paid, String refunded, String balance, String status,
            List<ReceivablePaymentLedger.Item> items) {
        return new ReceivablePaymentLedger(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal(total), new BigDecimal(adjusted), new BigDecimal(paid),
                new BigDecimal(refunded), new BigDecimal(balance), status, items);
    }

    private static ReceivablePaymentLedger.Item item(
            UUID id, String amount, String paid, String refunded, String status) {
        return new ReceivablePaymentLedger.Item(
                id, new BigDecimal(amount), new BigDecimal(paid), new BigDecimal(refunded), status);
    }

    private static void assertRuleCode(Runnable call, String code) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(
                        ReceivablePaymentRuleViolation.class,
                        ex -> assertThat(ex.code()).isEqualTo(code));
    }
}
