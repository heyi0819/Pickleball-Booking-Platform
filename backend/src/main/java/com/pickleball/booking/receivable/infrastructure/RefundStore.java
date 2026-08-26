package com.pickleball.booking.receivable.infrastructure;

import com.pickleball.booking.receivable.domain.PaymentMethod;
import com.pickleball.booking.receivable.domain.RefundLedger;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RefundStore {
    private final JdbcTemplate jdbc;

    public RefundStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<RefundRequestContext> findRequestContextLocked(UUID receivableId, UUID paymentId) {
        List<ReceivableRow> receivables = jdbc.query("""
                select id, organization_id, total_amount, adjusted_amount, paid_amount,
                       refunded_amount, balance_amount, status
                from receivables where id = ? for update
                """, (rs, rowNum) -> new ReceivableRow(
                rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                rs.getBigDecimal("total_amount"), rs.getBigDecimal("adjusted_amount"),
                rs.getBigDecimal("paid_amount"), rs.getBigDecimal("refunded_amount"),
                rs.getBigDecimal("balance_amount"), rs.getString("status")), receivableId);
        if (receivables.isEmpty()) return Optional.empty();

        List<PaymentRow> payments = jdbc.query("""
                select p.id, p.organization_id, p.amount, p.status
                from payments p
                where p.id = ?
                  and exists (
                    select 1 from payment_allocations pa
                    join receivable_items ri on ri.id = pa.receivable_item_id
                    where pa.payment_id = p.id and ri.receivable_id = ?
                  )
                for update
                """, (rs, rowNum) -> new PaymentRow(
                rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                rs.getBigDecimal("amount"), rs.getString("status")), paymentId, receivableId);
        if (payments.isEmpty()) return Optional.empty();

        ReceivableRow receivable = receivables.getFirst();
        PaymentRow payment = payments.getFirst();
        if (!receivable.organizationId().equals(payment.organizationId())) {
            throw new IllegalStateException("Payment and receivable organization mismatch");
        }
        BigDecimal reserved = jdbc.queryForObject("""
                select coalesce(sum(amount), 0)
                from refunds
                where payment_id = ? and status in ('PENDING_APPROVAL','APPROVED','COMPLETED')
                """, BigDecimal.class, paymentId);
        return Optional.of(new RefundRequestContext(receivable, payment, money(reserved)));
    }

    public UUID insertRefund(
            RefundRequestContext context,
            BigDecimal amount,
            String reason,
            UUID actorUserId) {
        UUID refundId = UUID.randomUUID();
        String refundNo = "RF-" + refundId.toString().replace("-", "").substring(0, 20);
        jdbc.update("""
                insert into refunds(
                    id, organization_id, refund_no, payment_id, amount, status,
                    reason, requested_by, requested_at)
                values (?, ?, ?, ?, ?, 'PENDING_APPROVAL', ?, ?, now())
                """, refundId, context.receivable().organizationId(), refundNo,
                context.payment().id(), amount, reason, actorUserId);
        return refundId;
    }

    public Optional<RefundLedger> findRefundLocked(UUID refundId) {
        List<RefundLedger> rows = jdbc.query("""
                select id, organization_id, payment_id, amount, reason, status,
                       approved_by, approved_at, approval_note,
                       processed_by, refunded_at, refund_method, reference_no, failure_reason
                from refunds where id = ? for update
                """, (rs, rowNum) -> new RefundLedger(
                rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getObject("payment_id", UUID.class),
                rs.getBigDecimal("amount"),
                rs.getString("reason"),
                rs.getString("status"),
                rs.getObject("approved_by", UUID.class),
                instant(rs.getTimestamp("approved_at")),
                rs.getString("approval_note"),
                rs.getObject("processed_by", UUID.class),
                instant(rs.getTimestamp("refunded_at")),
                paymentMethod(rs.getString("refund_method")),
                rs.getString("reference_no"),
                rs.getString("failure_reason")), refundId);
        return rows.stream().findFirst();
    }

    public Optional<RefundLedger> findRefund(UUID refundId) {
        List<RefundLedger> rows = jdbc.query("""
                select id, organization_id, payment_id, amount, reason, status,
                       approved_by, approved_at, approval_note,
                       processed_by, refunded_at, refund_method, reference_no, failure_reason
                from refunds where id = ?
                """, (rs, rowNum) -> new RefundLedger(
                rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getObject("payment_id", UUID.class),
                rs.getBigDecimal("amount"), rs.getString("reason"), rs.getString("status"),
                rs.getObject("approved_by", UUID.class), instant(rs.getTimestamp("approved_at")),
                rs.getString("approval_note"), rs.getObject("processed_by", UUID.class),
                instant(rs.getTimestamp("refunded_at")), paymentMethod(rs.getString("refund_method")),
                rs.getString("reference_no"), rs.getString("failure_reason")), refundId);
        return rows.stream().findFirst();
    }

    public void saveReview(RefundLedger refund) {
        jdbc.update("""
                update refunds
                set status = ?, approved_by = ?, approved_at = ?, approval_note = ?,
                    updated_at = now(), version = version + 1
                where id = ?
                """, refund.status(), refund.approvedBy(), timestamp(refund.approvedAt()),
                refund.approvalNote(), refund.id());
    }

    public ExecutionContext findExecutionContextLocked(RefundLedger refund) {
        List<UUID> receivableIds = jdbc.query("""
                select distinct ri.receivable_id
                from payment_allocations pa
                join receivable_items ri on ri.id = pa.receivable_item_id
                where pa.payment_id = ?
                """, (rs, rowNum) -> rs.getObject(1, UUID.class), refund.paymentId());
        if (receivableIds.size() != 1) {
            throw new IllegalStateException("Refund payment must resolve to exactly one receivable");
        }
        UUID receivableId = receivableIds.getFirst();

        ReceivableRow receivable = jdbc.query("""
                select id, organization_id, total_amount, adjusted_amount, paid_amount,
                       refunded_amount, balance_amount, status
                from receivables where id = ? for update
                """, (rs, rowNum) -> new ReceivableRow(
                rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                rs.getBigDecimal("total_amount"), rs.getBigDecimal("adjusted_amount"),
                rs.getBigDecimal("paid_amount"), rs.getBigDecimal("refunded_amount"),
                rs.getBigDecimal("balance_amount"), rs.getString("status")), receivableId).getFirst();

        PaymentRow payment = jdbc.query("""
                select id, organization_id, amount, status
                from payments where id = ? for update
                """, (rs, rowNum) -> new PaymentRow(
                rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                rs.getBigDecimal("amount"), rs.getString("status")), refund.paymentId()).getFirst();

        List<RefundableItem> items = jdbc.query("""
                select ri.id, ri.amount, ri.paid_amount, ri.refunded_amount, ri.status,
                       pa.amount as payment_allocation_amount
                from payment_allocations pa
                join receivable_items ri on ri.id = pa.receivable_item_id
                where pa.payment_id = ? and ri.receivable_id = ?
                order by ri.sort_order, ri.course_session_id, ri.id
                for update of ri
                """, (rs, rowNum) -> new RefundableItem(
                rs.getObject("id", UUID.class), rs.getBigDecimal("amount"),
                rs.getBigDecimal("paid_amount"), rs.getBigDecimal("refunded_amount"),
                rs.getString("status"), rs.getBigDecimal("payment_allocation_amount")),
                refund.paymentId(), receivableId);

        BigDecimal completed = jdbc.queryForObject("""
                select coalesce(sum(amount), 0)
                from refunds
                where payment_id = ? and status = 'COMPLETED' and id <> ?
                """, BigDecimal.class, refund.paymentId(), refund.id());
        return new ExecutionContext(receivable, payment, items, money(completed));
    }

    public FinancialResult completeRefund(RefundLedger refund, ExecutionContext context) {
        BigDecimal completedAfter = context.completedRefundAmount().add(refund.amount());
        if (completedAfter.compareTo(context.payment().amount()) > 0) {
            throw new IllegalStateException("Completed refunds exceed payment amount after locked validation");
        }

        BigDecimal remaining = refund.amount();
        List<ItemRefund> itemRefunds = new ArrayList<>();
        for (RefundableItem item : context.items()) {
            if (remaining.signum() == 0) break;
            BigDecimal itemNetPaid = item.paidAmount().subtract(item.refundedAmount()).max(BigDecimal.ZERO);
            BigDecimal capacity = item.paymentAllocationAmount().min(itemNetPaid);
            if (capacity.signum() <= 0) continue;
            BigDecimal applied = remaining.min(capacity);
            BigDecimal newRefunded = item.refundedAmount().add(applied);
            BigDecimal netPaid = item.paidAmount().subtract(newRefunded);
            String newStatus;
            if (netPaid.signum() <= 0 && newRefunded.signum() > 0) newStatus = "REFUNDED";
            else if (item.amount().subtract(netPaid).signum() <= 0) newStatus = "PAID";
            else if (netPaid.signum() > 0) newStatus = "PARTIALLY_PAID";
            else newStatus = "OPEN";
            jdbc.update("""
                    update receivable_items
                    set refunded_amount = ?, status = ?, updated_at = now(), version = version + 1
                    where id = ?
                    """, newRefunded, newStatus, item.id());
            itemRefunds.add(new ItemRefund(item.id(), applied));
            remaining = remaining.subtract(applied);
        }
        if (remaining.signum() != 0) {
            throw new IllegalStateException("Refund cannot be fully applied to paid receivable items");
        }

        jdbc.update("""
                update refunds
                set status = ?, processed_by = ?, refunded_at = ?, refund_method = ?,
                    reference_no = ?, failure_reason = null, updated_at = now(), version = version + 1
                where id = ?
                """, refund.status(), refund.processedBy(), timestamp(refund.refundedAt()),
                refund.refundMethod().name(), refund.referenceNo(), refund.id());

        String paymentStatus = completedAfter.compareTo(context.payment().amount()) == 0
                ? "REFUNDED" : "PARTIALLY_REFUNDED";
        jdbc.update("update payments set status = ? where id = ?", paymentStatus, context.payment().id());

        ReceivableRow receivable = context.receivable();
        BigDecimal newRefundedTotal = receivable.refundedAmount().add(refund.amount());
        BigDecimal newBalance = receivable.totalAmount().add(receivable.adjustedAmount())
                .subtract(receivable.paidAmount()).add(newRefundedTotal);
        BigDecimal netPaid = receivable.paidAmount().subtract(newRefundedTotal);
        String receivableStatus;
        if (newRefundedTotal.signum() > 0 && netPaid.signum() <= 0) receivableStatus = "REFUNDED";
        else if (newBalance.signum() == 0) receivableStatus = "PAID";
        else if ("OVERDUE".equals(receivable.status())) receivableStatus = "OVERDUE";
        else if (netPaid.signum() > 0) receivableStatus = "PARTIALLY_PAID";
        else receivableStatus = "OPEN";
        jdbc.update("""
                update receivables
                set refunded_amount = ?, balance_amount = ?, status = ?,
                    closed_at = case when ? in ('PAID','REFUNDED') then now() else null end,
                    updated_at = now(), version = version + 1
                where id = ?
                """, newRefundedTotal, newBalance, receivableStatus, receivableStatus, receivable.id());

        return new FinancialResult(
                receivable.id(), paymentStatus, receivableStatus,
                newRefundedTotal, newBalance, List.copyOf(itemRefunds));
    }

    public BigDecimal activeReservedRefundAmount(UUID paymentId, UUID excludingRefundId) {
        return money(jdbc.queryForObject("""
                select coalesce(sum(amount), 0)
                from refunds
                where payment_id = ?
                  and status in ('PENDING_APPROVAL','APPROVED','COMPLETED')
                  and id <> ?
                """, BigDecimal.class, paymentId, excludingRefundId));
    }

    public PaymentRow lockPayment(UUID paymentId) {
        return jdbc.query("""
                select id, organization_id, amount, status from payments where id = ? for update
                """, (rs, rowNum) -> new PaymentRow(
                rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                rs.getBigDecimal("amount"), rs.getString("status")), paymentId).getFirst();
    }

    public record RefundRequestContext(ReceivableRow receivable, PaymentRow payment, BigDecimal reservedRefundAmount) {}
    public record ExecutionContext(
            ReceivableRow receivable,
            PaymentRow payment,
            List<RefundableItem> items,
            BigDecimal completedRefundAmount) {}
    public record FinancialResult(
            UUID receivableId,
            String paymentStatus,
            String receivableStatus,
            BigDecimal refundedTotal,
            BigDecimal balanceAmount,
            List<ItemRefund> itemRefunds) {}
    public record ItemRefund(UUID receivableItemId, BigDecimal amount) {}
    public record ReceivableRow(
            UUID id,
            UUID organizationId,
            BigDecimal totalAmount,
            BigDecimal adjustedAmount,
            BigDecimal paidAmount,
            BigDecimal refundedAmount,
            BigDecimal balanceAmount,
            String status) {}
    public record PaymentRow(UUID id, UUID organizationId, BigDecimal amount, String status) {}
    public record RefundableItem(
            UUID id,
            BigDecimal amount,
            BigDecimal paidAmount,
            BigDecimal refundedAmount,
            String status,
            BigDecimal paymentAllocationAmount) {}

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2);
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static PaymentMethod paymentMethod(String value) {
        return value == null ? null : PaymentMethod.valueOf(value);
    }
}
