package com.pickleball.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.settlement.application.PayoutApplicationService;
import com.pickleball.booking.settlement.application.SettlementApplicationService;
import com.pickleball.booking.shared.application.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PayoutApplicationServiceIT {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired SettlementApplicationService settlementService;
    @Autowired PayoutApplicationService payoutService;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("security.jwt.signing-secret",
                () -> "test-only-signing-secret-with-at-least-thirty-two-characters");
    }

    @Test
    void completedPayoutIsIdempotentAndCannotDoubleIncrementCoachPaidAmount() throws Exception {
        ReadyFixture fixture = prepareSettlement(true);
        AuthenticatedPrincipal actor = new AuthenticatedPrincipal(fixture.committeeUserId());
        var created = payoutService.create(
                actor,
                new PayoutApplicationService.CreateCommand(
                        "CASH",
                        LocalDate.of(2026, 8, 28),
                        List.of(new PayoutApplicationService.CreateItem(
                                fixture.coachSettlementId(), fixture.payableAmount()))),
                "payout-create-idempotent");

        assertThat(created.status()).isEqualTo("DRAFT");
        assertThat(created.batchNo()).startsWith("PB-20260828-");
        assertThat(jdbc.queryForObject(
                "select payout_status from coach_settlements where id = ?",
                String.class, fixture.coachSettlementId())).isEqualTo("IN_BATCH");

        String key = "payout-execute-" + compact(created.payoutBatchId());
        Instant paidAt = Instant.parse("2026-08-28T06:30:00Z");
        var command = new PayoutApplicationService.ExecutionCommand(
                paidAt, "cash-register-20260828-01", "committee paid coach manually");

        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Future<String> first = executor.submit(() -> executeAfter(start, actor, created.payoutBatchId(), command, key, "execute-1"));
            Future<String> second = executor.submit(() -> executeAfter(start, actor, created.payoutBatchId(), command, key, "execute-2"));
            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder("COMPLETED", "COMPLETED");
        }

        assertThat(jdbc.queryForObject(
                "select status from payout_batches where id = ?",
                String.class, created.payoutBatchId())).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject(
                "select status from payout_batch_items where payout_batch_id = ?",
                String.class, created.payoutBatchId())).isEqualTo("PAID");
        assertThat(jdbc.queryForObject(
                "select paid_amount from coach_settlements where id = ?",
                BigDecimal.class, fixture.coachSettlementId())).isEqualByComparingTo(fixture.payableAmount());
        assertThat(jdbc.queryForObject(
                "select payout_status from coach_settlements where id = ?",
                String.class, fixture.coachSettlementId())).isEqualTo("PAID");
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_logs where entity_id = ? and action = 'PAYOUT_BATCH_EXECUTED'",
                Integer.class, created.payoutBatchId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from outbox_events where aggregate_id = ? and event_type = 'PAYOUT_BATCH_EXECUTED'",
                Integer.class, created.payoutBatchId())).isEqualTo(1);
    }

    @Test
    void unpaidTuitionKeepsCoachWaitingReceiptAndBatchCreationIsRejected() {
        ReadyFixture fixture = prepareSettlement(false);
        AuthenticatedPrincipal actor = new AuthenticatedPrincipal(fixture.committeeUserId());

        assertThat(jdbc.queryForObject(
                "select payout_status from coach_settlements where id = ?",
                String.class, fixture.coachSettlementId())).isEqualTo("WAITING_RECEIPT");
        assertBusinessCode(() -> payoutService.create(
                actor,
                new PayoutApplicationService.CreateCommand(
                        null,
                        LocalDate.of(2026, 8, 28),
                        List.of(new PayoutApplicationService.CreateItem(
                                fixture.coachSettlementId(), fixture.payableAmount()))),
                "payout-create-unpaid"), "SETTLEMENT_NOT_READY");
        assertThat(jdbc.queryForObject(
                "select count(*) from payout_batches where organization_id = ?",
                Integer.class, fixture.organizationId())).isZero();
    }

    @Test
    void concurrentBatchCreationForSameCoachSettlementAllowsOnlyOneActiveBatch() throws Exception {
        ReadyFixture fixture = prepareSettlement(true);
        AuthenticatedPrincipal actor = new AuthenticatedPrincipal(fixture.committeeUserId());
        var command = new PayoutApplicationService.CreateCommand(
                "CASH",
                LocalDate.of(2026, 8, 29),
                List.of(new PayoutApplicationService.CreateItem(
                        fixture.coachSettlementId(), fixture.payableAmount())));

        List<String> outcomes = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Future<String> first = executor.submit(() -> createAfter(start, actor, command, "concurrent-create-1"));
            Future<String> second = executor.submit(() -> createAfter(start, actor, command, "concurrent-create-2"));
            start.countDown();
            outcomes.add(first.get());
            outcomes.add(second.get());
        }

        assertThat(outcomes).containsExactlyInAnyOrder("SUCCESS", "CONCURRENT_MODIFICATION");
        assertThat(jdbc.queryForObject(
                "select count(*) from payout_batches where organization_id = ?",
                Integer.class, fixture.organizationId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*)
                from payout_batch_items
                where coach_settlement_id = ? and status <> 'CANCELLED'
                """, Integer.class, fixture.coachSettlementId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select payout_status from coach_settlements where id = ?",
                String.class, fixture.coachSettlementId())).isEqualTo("IN_BATCH");
    }

    private String executeAfter(
            CountDownLatch start,
            AuthenticatedPrincipal actor,
            UUID batchId,
            PayoutApplicationService.ExecutionCommand command,
            String key,
            String requestId) throws InterruptedException {
        start.await();
        return payoutService.execute(actor, batchId, command, key, requestId).status();
    }

    private String createAfter(
            CountDownLatch start,
            AuthenticatedPrincipal actor,
            PayoutApplicationService.CreateCommand command,
            String requestId) throws InterruptedException {
        start.await();
        try {
            payoutService.create(actor, command, requestId);
            return "SUCCESS";
        } catch (BusinessException ex) {
            return ex.code();
        }
    }

    private ReadyFixture prepareSettlement(boolean paid) {
        Fixture fixture = seedFixture(paid);
        AuthenticatedPrincipal actor = new AuthenticatedPrincipal(fixture.committeeUserId());
        var calculated = settlementService.calculate(
                actor,
                fixture.courseSessionId(),
                new SettlementApplicationService.CalculationCommand(new BigDecimal("0.00"), List.of()),
                "payout-settlement-calc-" + compact(fixture.courseSessionId()));
        settlementService.confirm(
                actor,
                calculated.sessionSettlementId(),
                new SettlementApplicationService.ConfirmationCommand("prepare payout integration fixture"),
                "payout-settlement-confirm-" + compact(calculated.sessionSettlementId()),
                "payout-settlement-confirm");
        UUID coachSettlementId = jdbc.queryForObject(
                "select id from coach_settlements where session_settlement_id = ?",
                UUID.class, calculated.sessionSettlementId());
        BigDecimal payableAmount = jdbc.queryForObject(
                "select payable_amount from coach_settlements where id = ?",
                BigDecimal.class, coachSettlementId);
        return new ReadyFixture(
                fixture.organizationId(), fixture.committeeUserId(), coachSettlementId, payableAmount);
    }

    private Fixture seedFixture(boolean paid) {
        UUID organizationId = UUID.randomUUID();
        UUID committeeUserId = UUID.randomUUID();
        UUID coachUserId = UUID.randomUUID();
        UUID coachProfileId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID courseSessionId = UUID.randomUUID();
        UUID priceSnapshotId = UUID.randomUUID();
        UUID receivableId = UUID.randomUUID();

        jdbc.update("insert into organizations(id, code, name) values (?, ?, ?)",
                organizationId, "s7p-" + compact(organizationId), "Slice 7 payout test");
        jdbc.update("insert into users(id, display_name) values (?, 'slice7 payout committee')", committeeUserId);
        jdbc.update("insert into users(id, display_name) values (?, 'slice7 payout coach')", coachUserId);
        jdbc.update("""
                insert into user_role_assignments(
                    id, organization_id, user_id, role_code, status, granted_by, granted_at)
                values (?, ?, ?, 'COMMITTEE', 'ACTIVE', ?, now())
                """, UUID.randomUUID(), organizationId, committeeUserId, committeeUserId);
        jdbc.update("""
                insert into coach_profiles(id, organization_id, user_id, approval_status)
                values (?, ?, ?, 'APPROVED')
                """, coachProfileId, organizationId, coachUserId);
        jdbc.update("""
                insert into courses(
                    id, organization_id, course_no, created_by_user_id, course_type,
                    schedule_type, billing_mode, expected_participant_count,
                    guest_participant_count, total_session_count, status)
                values (?, ?, ?, ?, 'GROUP', 'SINGLE', 'PER_SESSION', 1, 0, 1, 'COMPLETED')
                """, courseId, organizationId, "S7P-" + compact(courseId), committeeUserId);
        jdbc.update("""
                insert into course_sessions(
                    id, organization_id, course_id, sequence_no,
                    scheduled_start_at, scheduled_end_at,
                    expected_participant_count, guest_participant_count, actual_participant_count,
                    status, completed_at)
                values (?, ?, ?, 1, '2026-08-01 10:00+00', '2026-08-01 11:00+00', 1, 0, 1,
                        'COMPLETED', '2026-08-01 11:05+00')
                """, courseSessionId, organizationId, courseId);
        jdbc.update("""
                insert into session_price_snapshots(
                    id, organization_id, course_session_id, version_no, status,
                    tuition_amount, venue_fee, other_adjustment, total_receivable,
                    confirmed_by, confirmed_at, created_by)
                values (?, ?, ?, 1, 'CONFIRMED', 900.00, 100.00, 0, 1000.00, ?, now(), ?)
                """, priceSnapshotId, organizationId, courseSessionId, committeeUserId, committeeUserId);
        jdbc.update("""
                insert into session_venue_arrangements(
                    id, organization_id, course_session_id, source_type,
                    venue_name_snapshot, cost_amount, cost_payer_type, status,
                    confirmed_by, confirmed_at)
                values (?, ?, ?, 'COMMITTEE', 'Slice 7 payout venue', 100.00, 'COMMITTEE',
                        'CONFIRMED', ?, now())
                """, UUID.randomUUID(), organizationId, courseSessionId, committeeUserId);
        jdbc.update("""
                insert into session_coach_assignments(
                    id, organization_id, course_session_id, coach_profile_id,
                    source_type, status, is_primary, responded_at, assigned_by)
                values (?, ?, ?, ?, 'DIRECT', 'ACCEPTED', true, now(), ?)
                """, UUID.randomUUID(), organizationId, courseSessionId, coachProfileId, committeeUserId);
        jdbc.update("""
                insert into receivables(
                    id, organization_id, receivable_no, course_id, payer_user_id,
                    billing_mode, total_amount, paid_amount, balance_amount, status)
                values (?, ?, ?, ?, ?, 'PER_SESSION', 1000.00, ?, ?, ?)
                """, receivableId, organizationId, "S7PAR-" + compact(receivableId), courseId, coachUserId,
                paid ? new BigDecimal("1000.00") : BigDecimal.ZERO,
                paid ? BigDecimal.ZERO : new BigDecimal("1000.00"),
                paid ? "PAID" : "OPEN");
        jdbc.update("""
                insert into receivable_items(
                    id, receivable_id, course_session_id, price_snapshot_id,
                    amount, paid_amount, refunded_amount, status)
                values (?, ?, ?, ?, 1000.00, ?, 0, ?)
                """, UUID.randomUUID(), receivableId, courseSessionId, priceSnapshotId,
                paid ? new BigDecimal("1000.00") : BigDecimal.ZERO,
                paid ? "PAID" : "OPEN");

        return new Fixture(organizationId, committeeUserId, courseSessionId);
    }

    private static void assertBusinessCode(Runnable call, String code) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.code()).isEqualTo(code));
    }

    private static String compact(UUID id) {
        return id.toString().replace("-", "").substring(0, 12);
    }

    private record Fixture(UUID organizationId, UUID committeeUserId, UUID courseSessionId) {}
    private record ReadyFixture(
            UUID organizationId,
            UUID committeeUserId,
            UUID coachSettlementId,
            BigDecimal payableAmount) {}
}
