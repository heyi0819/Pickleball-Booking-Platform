package com.pickleball.booking.settlement.infrastructure;

import com.pickleball.booking.settlement.domain.SettlementAllocationPolicy.Allocation;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SettlementStore {
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final JdbcTemplate jdbc;

    public SettlementStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<CalculationSource> findCalculationSourceLocked(UUID courseSessionId) {
        List<SessionRow> sessions = jdbc.query("""
                select id, organization_id, status
                from course_sessions
                where id = ?
                for update
                """, (rs, rowNum) -> new SessionRow(
                rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getString("status")), courseSessionId);
        if (sessions.isEmpty()) return Optional.empty();

        SessionRow session = sessions.getFirst();
        Optional<PriceSnapshotRow> snapshot = jdbc.query("""
                select id, total_receivable
                from session_price_snapshots
                where course_session_id = ? and status = 'CONFIRMED'
                """, (rs, rowNum) -> new PriceSnapshotRow(
                rs.getObject("id", UUID.class), rs.getBigDecimal("total_receivable")), courseSessionId)
                .stream().findFirst();

        Optional<BigDecimal> venueCost = jdbc.query("""
                select cost_amount
                from session_venue_arrangements
                where course_session_id = ? and status = 'CONFIRMED'
                """, (rs, rowNum) -> rs.getBigDecimal("cost_amount"), courseSessionId)
                .stream().findFirst();

        List<FinanceItem> financeItems = jdbc.query("""
                select id, price_snapshot_id, amount, paid_amount, refunded_amount, status
                from receivable_items
                where course_session_id = ?
                order by id
                for update
                """, (rs, rowNum) -> new FinanceItem(
                rs.getObject("id", UUID.class),
                rs.getObject("price_snapshot_id", UUID.class),
                rs.getBigDecimal("amount"),
                rs.getBigDecimal("paid_amount"),
                rs.getBigDecimal("refunded_amount"),
                rs.getString("status")), courseSessionId);

        List<CoachAssignmentRow> coaches = jdbc.query("""
                select id, coach_profile_id
                from session_coach_assignments
                where course_session_id = ? and status = 'ACCEPTED'
                order by id
                for update
                """, (rs, rowNum) -> new CoachAssignmentRow(
                rs.getObject("id", UUID.class), rs.getObject("coach_profile_id", UUID.class)), courseSessionId);

        return Optional.of(new CalculationSource(
                session.id(), session.organizationId(), session.status(), snapshot.orElse(null),
                venueCost.orElse(ZERO), financeItems, coaches));
    }

    public Optional<SettlementRow> findSettlementLockedBySession(UUID courseSessionId) {
        return querySettlement("where course_session_id = ? for update", courseSessionId);
    }

    public Optional<SettlementRow> findSettlementLockedById(UUID settlementId) {
        return querySettlement("where id = ? for update", settlementId);
    }

    public Optional<SettlementRow> findSettlementBySession(UUID courseSessionId) {
        return querySettlement("where course_session_id = ?", courseSessionId);
    }

    private Optional<SettlementRow> querySettlement(String clause, UUID id) {
        return jdbc.query("""
                select id, organization_id, course_session_id, price_snapshot_id,
                       gross_receivable, venue_cost, other_adjustment, distributable_amount,
                       status, confirmed_by, confirmed_at, version
                from session_settlements
                """ + clause, (rs, rowNum) -> new SettlementRow(
                rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getObject("course_session_id", UUID.class),
                rs.getObject("price_snapshot_id", UUID.class),
                rs.getBigDecimal("gross_receivable"),
                rs.getBigDecimal("venue_cost"),
                rs.getBigDecimal("other_adjustment"),
                rs.getBigDecimal("distributable_amount"),
                rs.getString("status"),
                rs.getObject("confirmed_by", UUID.class),
                rs.getTimestamp("confirmed_at") == null ? null : rs.getTimestamp("confirmed_at").toInstant(),
                rs.getLong("version")), id).stream().findFirst();
    }

    public UUID saveCalculated(
            CalculationSource source,
            BigDecimal grossReceivable,
            BigDecimal otherAdjustment,
            BigDecimal distributableAmount,
            List<Allocation> allocations) {
        Optional<SettlementRow> existing = findSettlementLockedBySession(source.courseSessionId());
        UUID settlementId;
        if (existing.isPresent()) {
            SettlementRow row = existing.get();
            settlementId = row.id();
            jdbc.update("""
                    update session_settlements
                    set price_snapshot_id = ?, gross_receivable = ?, venue_cost = ?, other_adjustment = ?,
                        distributable_amount = ?, status = 'CALCULATED', confirmed_by = null, confirmed_at = null,
                        updated_at = now(), version = version + 1
                    where id = ?
                    """, source.priceSnapshot().id(), grossReceivable, source.venueCost(), otherAdjustment,
                    distributableAmount, settlementId);
            jdbc.update("delete from coach_settlements where session_settlement_id = ?", settlementId);
        } else {
            settlementId = UUID.randomUUID();
            jdbc.update("""
                    insert into session_settlements(
                        id, organization_id, course_session_id, price_snapshot_id,
                        gross_receivable, venue_cost, other_adjustment, distributable_amount, status)
                    values (?, ?, ?, ?, ?, ?, ?, ?, 'CALCULATED')
                    """, settlementId, source.organizationId(), source.courseSessionId(), source.priceSnapshot().id(),
                    grossReceivable, source.venueCost(), otherAdjustment, distributableAmount);
        }

        for (Allocation allocation : allocations) {
            jdbc.update("""
                    insert into coach_settlements(
                        id, organization_id, session_settlement_id, coach_assignment_id, coach_profile_id,
                        allocation_type, allocation_value, payable_amount, paid_amount, payout_status)
                    values (?, ?, ?, ?, ?, ?, ?, ?, 0, 'WAITING_RECEIPT')
                    """, UUID.randomUUID(), source.organizationId(), settlementId,
                    allocation.assignmentId(), allocation.coachProfileId(), allocation.type().name(),
                    allocation.value(), allocation.payableAmount());
        }
        return settlementId;
    }

    public List<CoachSettlementRow> findCoachSettlements(UUID settlementId) {
        return jdbc.query("""
                select id, coach_profile_id, payable_amount, paid_amount, payout_status, version
                from coach_settlements
                where session_settlement_id = ?
                order by coach_profile_id, id
                """, (rs, rowNum) -> new CoachSettlementRow(
                rs.getObject("id", UUID.class),
                rs.getObject("coach_profile_id", UUID.class),
                rs.getBigDecimal("payable_amount"),
                rs.getBigDecimal("paid_amount"),
                rs.getString("payout_status"),
                rs.getLong("version")), settlementId);
    }

    public boolean actorOwnsCoachSettlement(UUID settlementId, UUID userId) {
        Integer count = jdbc.queryForObject("""
                select count(*)
                from coach_settlements cs
                join coach_profiles cp on cp.id = cs.coach_profile_id
                where cs.session_settlement_id = ? and cp.user_id = ? and cp.deleted_at is null
                """, Integer.class, settlementId, userId);
        return count != null && count > 0;
    }

    public List<CoachSettlementRow> findCoachSettlementsForActor(UUID settlementId, UUID userId) {
        return jdbc.query("""
                select cs.id, cs.coach_profile_id, cs.payable_amount, cs.paid_amount, cs.payout_status, cs.version
                from coach_settlements cs
                join coach_profiles cp on cp.id = cs.coach_profile_id
                where cs.session_settlement_id = ? and cp.user_id = ? and cp.deleted_at is null
                order by cs.coach_profile_id, cs.id
                """, (rs, rowNum) -> new CoachSettlementRow(
                rs.getObject("id", UUID.class),
                rs.getObject("coach_profile_id", UUID.class),
                rs.getBigDecimal("payable_amount"),
                rs.getBigDecimal("paid_amount"),
                rs.getString("payout_status"),
                rs.getLong("version")), settlementId, userId);
    }

    public FinanceReadiness lockFinanceReadiness(UUID courseSessionId, UUID priceSnapshotId) {
        List<FinanceItem> items = jdbc.query("""
                select id, price_snapshot_id, amount, paid_amount, refunded_amount, status
                from receivable_items
                where course_session_id = ?
                order by id
                for update
                """, (rs, rowNum) -> new FinanceItem(
                rs.getObject("id", UUID.class),
                rs.getObject("price_snapshot_id", UUID.class),
                rs.getBigDecimal("amount"),
                rs.getBigDecimal("paid_amount"),
                rs.getBigDecimal("refunded_amount"),
                rs.getString("status")), courseSessionId);
        boolean sameSnapshot = items.stream()
                .filter(FinanceItem::active)
                .allMatch(item -> priceSnapshotId.equals(item.priceSnapshotId()));
        boolean fullyCollected = items.stream().filter(FinanceItem::active).allMatch(FinanceItem::fullyCollected);
        return new FinanceReadiness(sameSnapshot, fullyCollected);
    }

    public void confirm(UUID settlementId, UUID actorUserId, Instant confirmedAt, boolean financeReady) {
        jdbc.update("""
                update session_settlements
                set status = 'PENDING_APPROVAL', updated_at = now(), version = version + 1
                where id = ? and status = 'CALCULATED'
                """, settlementId);
        jdbc.update("""
                update session_settlements
                set status = 'CONFIRMED', confirmed_by = ?, confirmed_at = ?,
                    updated_at = now(), version = version + 1
                where id = ? and status = 'PENDING_APPROVAL'
                """, actorUserId, Timestamp.from(confirmedAt), settlementId);
        if (financeReady) {
            jdbc.update("""
                    update coach_settlements
                    set payout_status = 'READY', ready_at = ?, updated_at = now(), version = version + 1
                    where session_settlement_id = ? and payout_status = 'WAITING_RECEIPT'
                    """, Timestamp.from(confirmedAt), settlementId);
        }
    }

    public record CalculationSource(
            UUID courseSessionId,
            UUID organizationId,
            String sessionStatus,
            PriceSnapshotRow priceSnapshot,
            BigDecimal venueCost,
            List<FinanceItem> financeItems,
            List<CoachAssignmentRow> coachAssignments) {

        public BigDecimal grossReceivable() {
            return financeItems.stream()
                    .filter(FinanceItem::active)
                    .map(FinanceItem::netReceivable)
                    .reduce(ZERO, BigDecimal::add)
                    .setScale(2);
        }

        public boolean hasCanonicalPriceLineage() {
            if (priceSnapshot == null) return false;
            return financeItems.stream()
                    .filter(FinanceItem::active)
                    .allMatch(item -> priceSnapshot.id().equals(item.priceSnapshotId()));
        }

        public boolean receivablesExistWhenRequired() {
            return priceSnapshot != null
                    && (priceSnapshot.totalReceivable().signum() == 0
                    || financeItems.stream().anyMatch(FinanceItem::active));
        }
    }

    public record PriceSnapshotRow(UUID id, BigDecimal totalReceivable) {}

    public record CoachAssignmentRow(UUID assignmentId, UUID coachProfileId) {}

    public record FinanceItem(
            UUID id,
            UUID priceSnapshotId,
            BigDecimal amount,
            BigDecimal paidAmount,
            BigDecimal refundedAmount,
            String status) {
        boolean active() { return !"CANCELLED".equals(status); }
        BigDecimal netReceivable() { return amount.subtract(refundedAmount).max(BigDecimal.ZERO); }
        BigDecimal netCollected() { return paidAmount.subtract(refundedAmount).max(BigDecimal.ZERO); }
        boolean fullyCollected() { return netCollected().compareTo(netReceivable()) >= 0; }
    }

    public record SettlementRow(
            UUID id,
            UUID organizationId,
            UUID courseSessionId,
            UUID priceSnapshotId,
            BigDecimal grossReceivable,
            BigDecimal venueCost,
            BigDecimal otherAdjustment,
            BigDecimal distributableAmount,
            String status,
            UUID confirmedBy,
            Instant confirmedAt,
            long version) {}

    public record CoachSettlementRow(
            UUID id,
            UUID coachProfileId,
            BigDecimal payableAmount,
            BigDecimal paidAmount,
            String payoutStatus,
            long version) {}

    public record FinanceReadiness(boolean sameSnapshot, boolean fullyCollected) {}

    private record SessionRow(UUID id, UUID organizationId, String status) {}
}
