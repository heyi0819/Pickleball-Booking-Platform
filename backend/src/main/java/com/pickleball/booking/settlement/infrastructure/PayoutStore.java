package com.pickleball.booking.settlement.infrastructure;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PayoutStore {
    private static final DateTimeFormatter BATCH_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final JdbcTemplate jdbc;

    public PayoutStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<CoachSettlementPayoutRow> findCoachSettlementLocked(UUID coachSettlementId) {
        return jdbc.query("""
                select id, organization_id, coach_profile_id, payable_amount, paid_amount, payout_status, version
                from coach_settlements
                where id = ?
                for update
                """, (rs, rowNum) -> new CoachSettlementPayoutRow(
                rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getObject("coach_profile_id", UUID.class),
                rs.getBigDecimal("payable_amount"),
                rs.getBigDecimal("paid_amount"),
                rs.getString("payout_status"),
                rs.getLong("version")), coachSettlementId).stream().findFirst();
    }

    public boolean hasActiveBatchItem(UUID coachSettlementId) {
        Integer count = jdbc.queryForObject("""
                select count(*)
                from payout_batch_items pbi
                join payout_batches pb on pb.id = pbi.payout_batch_id
                where pbi.coach_settlement_id = ?
                  and pbi.status <> 'CANCELLED'
                  and pb.status in ('DRAFT','APPROVED','PROCESSING')
                """, Integer.class, coachSettlementId);
        return count != null && count > 0;
    }

    public BigDecimal nonCancelledAllocatedAmount(UUID coachSettlementId) {
        BigDecimal amount = jdbc.queryForObject("""
                select coalesce(sum(amount), 0)
                from payout_batch_items
                where coach_settlement_id = ? and status <> 'CANCELLED'
                """, BigDecimal.class, coachSettlementId);
        return amount == null ? new BigDecimal("0.00") : amount.setScale(2);
    }

    public void lockOrganization(UUID organizationId) {
        Integer count = jdbc.queryForObject("""
                select count(*)
                from organizations
                where id = ?
                for update
                """, Integer.class, organizationId);
        if (count == null || count != 1) {
            throw new IllegalStateException("Organization disappeared while creating payout batch");
        }
    }

    public String nextBatchNo(UUID organizationId, LocalDate payoutDate) {
        String prefix = "PB-" + BATCH_DATE.format(payoutDate) + "-";
        Integer count = jdbc.queryForObject("""
                select count(*)
                from payout_batches
                where organization_id = ? and batch_no like ?
                """, Integer.class, organizationId, prefix + "%");
        return prefix + String.format("%03d", (count == null ? 0 : count) + 1);
    }

    public UUID createBatch(
            UUID organizationId,
            String batchNo,
            LocalDate payoutDate,
            String method,
            BigDecimal totalAmount,
            int itemCount,
            UUID createdBy) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into payout_batches(
                    id, organization_id, batch_no, status, payout_date, method, currency,
                    total_amount, item_count, created_by)
                values (?, ?, ?, 'DRAFT', ?, ?, 'TWD', ?, ?, ?)
                """, id, organizationId, batchNo, Date.valueOf(payoutDate), method,
                totalAmount, itemCount, createdBy);
        return id;
    }

    public void createItem(UUID batchId, CoachSettlementPayoutRow coachSettlement, BigDecimal amount) {
        jdbc.update("""
                insert into payout_batch_items(
                    id, payout_batch_id, coach_settlement_id, coach_profile_id, amount, status)
                values (?, ?, ?, ?, ?, 'PLANNED')
                """, UUID.randomUUID(), batchId, coachSettlement.id(), coachSettlement.coachProfileId(), amount);
    }

    public boolean markCoachInBatch(CoachSettlementPayoutRow coachSettlement) {
        return jdbc.update("""
                update coach_settlements
                set payout_status = 'IN_BATCH', updated_at = now(), version = version + 1
                where id = ? and payout_status = 'READY' and version = ?
                """, coachSettlement.id(), coachSettlement.version()) == 1;
    }

    public Optional<PayoutBatchRow> findBatchLocked(UUID batchId) {
        return queryBatch("where id = ? for update", batchId);
    }

    public Optional<PayoutBatchRow> findBatch(UUID batchId) {
        return queryBatch("where id = ?", batchId);
    }

    private Optional<PayoutBatchRow> queryBatch(String clause, UUID batchId) {
        return jdbc.query("""
                select id, organization_id, batch_no, status, payout_date, method, currency,
                       total_amount, item_count, created_by, approved_by, approved_at, completed_at, version
                from payout_batches
                """ + clause, (rs, rowNum) -> new PayoutBatchRow(
                rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getString("batch_no"),
                rs.getString("status"),
                rs.getDate("payout_date") == null ? null : rs.getDate("payout_date").toLocalDate(),
                rs.getString("method"),
                rs.getString("currency"),
                rs.getBigDecimal("total_amount"),
                rs.getInt("item_count"),
                rs.getObject("created_by", UUID.class),
                rs.getObject("approved_by", UUID.class),
                instant(rs.getTimestamp("approved_at")),
                instant(rs.getTimestamp("completed_at")),
                rs.getLong("version")), batchId).stream().findFirst();
    }

    public List<PayoutBatchItemRow> findBatchItems(UUID batchId) {
        return jdbc.query("""
                select id, payout_batch_id, coach_settlement_id, coach_profile_id, amount,
                       status, paid_at, processed_by, reference_no, failure_reason
                from payout_batch_items
                where payout_batch_id = ?
                order by coach_settlement_id, id
                """, (rs, rowNum) -> new PayoutBatchItemRow(
                rs.getObject("id", UUID.class),
                rs.getObject("payout_batch_id", UUID.class),
                rs.getObject("coach_settlement_id", UUID.class),
                rs.getObject("coach_profile_id", UUID.class),
                rs.getBigDecimal("amount"),
                rs.getString("status"),
                instant(rs.getTimestamp("paid_at")),
                rs.getObject("processed_by", UUID.class),
                rs.getString("reference_no"),
                rs.getString("failure_reason")), batchId);
    }

    public boolean approveBatch(UUID batchId, long expectedVersion, UUID actor, Instant approvedAt) {
        return jdbc.update("""
                update payout_batches
                set status = 'APPROVED', approved_by = ?, approved_at = ?,
                    updated_at = now(), version = version + 1
                where id = ? and status = 'DRAFT' and version = ?
                """, actor, Timestamp.from(approvedAt), batchId, expectedVersion) == 1;
    }

    public boolean markBatchProcessing(UUID batchId) {
        return jdbc.update("""
                update payout_batches
                set status = 'PROCESSING', updated_at = now(), version = version + 1
                where id = ? and status = 'APPROVED'
                """, batchId) == 1;
    }

    public boolean markItemPaid(UUID itemId, UUID actor, Instant paidAt, String referenceNo) {
        return jdbc.update("""
                update payout_batch_items
                set status = 'PAID', paid_at = ?, processed_by = ?, reference_no = ?,
                    failure_reason = null, updated_at = now()
                where id = ? and status = 'PLANNED'
                """, Timestamp.from(paidAt), actor, referenceNo, itemId) == 1;
    }

    public boolean addCoachPaidAmount(UUID coachSettlementId, BigDecimal amount) {
        return jdbc.update("""
                update coach_settlements
                set paid_amount = paid_amount + ?,
                    payout_status = case
                        when paid_amount + ? = payable_amount then 'PAID'
                        else 'PARTIALLY_PAID'
                    end,
                    updated_at = now(), version = version + 1
                where id = ?
                  and payout_status = 'IN_BATCH'
                  and paid_amount + ? <= payable_amount
                """, amount, amount, coachSettlementId, amount) == 1;
    }

    public boolean completeBatch(UUID batchId, Instant completedAt) {
        return jdbc.update("""
                update payout_batches
                set status = 'COMPLETED', completed_at = ?, updated_at = now(), version = version + 1
                where id = ? and status = 'PROCESSING'
                """, Timestamp.from(completedAt), batchId) == 1;
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    public record CoachSettlementPayoutRow(
            UUID id,
            UUID organizationId,
            UUID coachProfileId,
            BigDecimal payableAmount,
            BigDecimal paidAmount,
            String payoutStatus,
            long version) {
        public BigDecimal outstandingAmount() {
            return payableAmount.subtract(paidAmount).setScale(2);
        }
    }

    public record PayoutBatchRow(
            UUID id,
            UUID organizationId,
            String batchNo,
            String status,
            LocalDate payoutDate,
            String method,
            String currency,
            BigDecimal totalAmount,
            int itemCount,
            UUID createdBy,
            UUID approvedBy,
            Instant approvedAt,
            Instant completedAt,
            long version) {}

    public record PayoutBatchItemRow(
            UUID id,
            UUID payoutBatchId,
            UUID coachSettlementId,
            UUID coachProfileId,
            BigDecimal amount,
            String status,
            Instant paidAt,
            UUID processedBy,
            String referenceNo,
            String failureReason) {}
}
