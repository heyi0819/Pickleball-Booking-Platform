package com.pickleball.booking.receivable.infrastructure;

import com.pickleball.booking.receivable.domain.PaymentMethod;
import com.pickleball.booking.receivable.domain.ReceivablePaymentLedger;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReceivablePaymentStore {
    private final JdbcTemplate jdbc;

    public ReceivablePaymentStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ReceivablePaymentLedger> findLockedById(UUID receivableId) {
        List<ReceivableRow> rows = jdbc.query("""
                select id, organization_id, payer_user_id, total_amount, adjusted_amount,
                       paid_amount, refunded_amount, balance_amount, status
                from receivables
                where id = ?
                for update
                """, (rs, rowNum) -> new ReceivableRow(
                rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getObject("payer_user_id", UUID.class),
                rs.getBigDecimal("total_amount"),
                rs.getBigDecimal("adjusted_amount"),
                rs.getBigDecimal("paid_amount"),
                rs.getBigDecimal("refunded_amount"),
                rs.getBigDecimal("balance_amount"),
                rs.getString("status")), receivableId);
        if (rows.isEmpty()) return Optional.empty();

        ReceivableRow row = rows.getFirst();
        List<ReceivablePaymentLedger.Item> items = jdbc.query("""
                select id, amount, paid_amount, refunded_amount, status
                from receivable_items
                where receivable_id = ?
                order by sort_order, course_session_id, id
                for update
                """, (rs, rowNum) -> new ReceivablePaymentLedger.Item(
                rs.getObject("id", UUID.class),
                rs.getBigDecimal("amount"),
                rs.getBigDecimal("paid_amount"),
                rs.getBigDecimal("refunded_amount"),
                rs.getString("status")), receivableId);

        return Optional.of(new ReceivablePaymentLedger(
                row.id(), row.organizationId(), row.payerUserId(), row.totalAmount(), row.adjustedAmount(),
                row.paidAmount(), row.refundedAmount(), row.balanceAmount(), row.status(), items));
    }

    public UUID insertPayment(
            ReceivablePaymentLedger ledger,
            ReceivablePaymentLedger.PaymentApplication application,
            PaymentMethod method,
            Instant paidAt,
            UUID actorUserId,
            String idempotencyKey,
            String note) {
        UUID paymentId = UUID.randomUUID();
        String paymentNo = "PAY-" + paymentId.toString().replace("-", "").substring(0, 20);
        jdbc.update("""
                insert into payments(
                    id, organization_id, payment_no, payer_user_id, amount, currency,
                    payment_method, status, paid_at, recorded_by, idempotency_key, note)
                values (?, ?, ?, ?, ?, 'TWD', ?, 'COMPLETED', ?, ?, ?, ?)
                """, paymentId, ledger.organizationId(), paymentNo, ledger.payerUserId(),
                application.amount(), method.name(), Timestamp.from(paidAt), actorUserId,
                idempotencyKey, blankToNull(note));

        for (ReceivablePaymentLedger.Allocation allocation : application.allocations()) {
            jdbc.update("""
                    insert into payment_allocations(
                        id, payment_id, receivable_item_id, amount, allocated_by, allocated_at)
                    values (?, ?, ?, ?, ?, now())
                    """, UUID.randomUUID(), paymentId, allocation.receivableItemId(), allocation.amount(), actorUserId);
        }

        for (ReceivablePaymentLedger.Item item : ledger.items()) {
            if (application.allocations().stream().noneMatch(a -> a.receivableItemId().equals(item.id()))) continue;
            jdbc.update("""
                    update receivable_items
                    set paid_amount = ?, status = ?, updated_at = now(), version = version + 1
                    where id = ?
                    """, item.paidAmount(), item.status(), item.id());
        }

        jdbc.update("""
                update receivables
                set paid_amount = ?, balance_amount = ?, status = ?,
                    closed_at = case when ? = 'PAID' then now() else null end,
                    updated_at = now(), version = version + 1
                where id = ?
                """, ledger.paidAmount(), ledger.balanceAmount(), ledger.persistenceStatus(),
                ledger.persistenceStatus(), ledger.id());
        return paymentId;
    }

    public Optional<PaymentRow> findPaymentForReceivable(UUID paymentId, UUID receivableId) {
        List<PaymentRow> rows = jdbc.query("""
                select distinct p.id, p.amount, p.payment_method, p.paid_at
                from payments p
                join payment_allocations pa on pa.payment_id = p.id
                join receivable_items ri on ri.id = pa.receivable_item_id
                where p.id = ? and ri.receivable_id = ?
                """, (rs, rowNum) -> new PaymentRow(
                rs.getObject("id", UUID.class),
                rs.getBigDecimal("amount"),
                PaymentMethod.valueOf(rs.getString("payment_method")),
                rs.getTimestamp("paid_at").toInstant()), paymentId, receivableId);
        return rows.stream().findFirst();
    }

    public record PaymentRow(UUID id, BigDecimal amount, PaymentMethod method, Instant paidAt) {}

    private record ReceivableRow(
            UUID id,
            UUID organizationId,
            UUID payerUserId,
            BigDecimal totalAmount,
            BigDecimal adjustedAmount,
            BigDecimal paidAmount,
            BigDecimal refundedAmount,
            BigDecimal balanceAmount,
            String status) {}

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
