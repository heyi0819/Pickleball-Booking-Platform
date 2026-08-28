package com.pickleball.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.settlement.application.SettlementApplicationService;
import com.pickleball.booking.shared.application.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
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
class SettlementApplicationServiceIT {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired SettlementApplicationService settlementService;
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
    void unpaidSessionCanBeConfirmedButCoachRemainsWaitingReceiptAndConfirmationIsIdempotent() {
        Fixture fixture = seedFixture(false);
        AuthenticatedPrincipal actor = new AuthenticatedPrincipal(fixture.committeeUserId());

        var calculated = settlementService.calculate(
                actor,
                fixture.courseSessionId(),
                new SettlementApplicationService.CalculationCommand(new BigDecimal("0.00"), List.of()),
                "settlement-calc-unpaid");

        assertThat(calculated.grossReceivable()).isEqualByComparingTo("1000.00");
        assertThat(calculated.venueCost()).isEqualByComparingTo("100.00");
        assertThat(calculated.distributableAmount()).isEqualByComparingTo("900.00");
        assertThat(calculated.coachPayableTotal()).isEqualByComparingTo("900.00");

        String key = "settlement-confirm-" + compact(calculated.sessionSettlementId());
        var confirmed = settlementService.confirm(
                actor,
                calculated.sessionSettlementId(),
                new SettlementApplicationService.ConfirmationCommand("committee confirmed settlement"),
                key,
                "settlement-confirm-unpaid-1");
        var replay = settlementService.confirm(
                actor,
                calculated.sessionSettlementId(),
                new SettlementApplicationService.ConfirmationCommand("committee confirmed settlement"),
                key,
                "settlement-confirm-unpaid-2");

        assertThat(confirmed.status()).isEqualTo("CONFIRMED");
        assertThat(replay.sessionSettlementId()).isEqualTo(confirmed.sessionSettlementId());
        assertThat(jdbc.queryForObject(
                "select payout_status from coach_settlements where session_settlement_id = ?",
                String.class, calculated.sessionSettlementId())).isEqualTo("WAITING_RECEIPT");
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_logs where entity_id = ? and action = 'SETTLEMENT_CONFIRMED'",
                Integer.class, calculated.sessionSettlementId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from outbox_events where aggregate_id = ? and event_type = 'SETTLEMENT_CONFIRMED'",
                Integer.class, calculated.sessionSettlementId())).isEqualTo(1);

        assertBusinessCode(() -> settlementService.calculate(
                actor,
                fixture.courseSessionId(),
                new SettlementApplicationService.CalculationCommand(new BigDecimal("0.00"), List.of()),
                "settlement-recalc-confirmed"), "STATE_TRANSITION_INVALID");
    }

    @Test
    void fullyCollectedSessionMakesCoachReadyOnlyWhenSettlementIsConfirmed() {
        Fixture fixture = seedFixture(true);
        AuthenticatedPrincipal actor = new AuthenticatedPrincipal(fixture.committeeUserId());

        var calculated = settlementService.calculate(
                actor,
                fixture.courseSessionId(),
                new SettlementApplicationService.CalculationCommand(new BigDecimal("50.00"), List.of()),
                "settlement-calc-paid");
        assertThat(jdbc.queryForObject(
                "select payout_status from coach_settlements where session_settlement_id = ?",
                String.class, calculated.sessionSettlementId())).isEqualTo("WAITING_RECEIPT");

        var confirmed = settlementService.confirm(
                actor,
                calculated.sessionSettlementId(),
                new SettlementApplicationService.ConfirmationCommand("finance collected"),
                "settlement-confirm-paid-" + compact(calculated.sessionSettlementId()),
                "settlement-confirm-paid");

        assertThat(confirmed.status()).isEqualTo("CONFIRMED");
        assertThat(calculated.distributableAmount()).isEqualByComparingTo("950.00");
        assertThat(jdbc.queryForObject(
                "select payout_status from coach_settlements where session_settlement_id = ?",
                String.class, calculated.sessionSettlementId())).isEqualTo("READY");
        assertThat(jdbc.queryForObject(
                "select ready_at is not null from coach_settlements where session_settlement_id = ?",
                Boolean.class, calculated.sessionSettlementId())).isTrue();
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
                organizationId, "s7-" + compact(organizationId), "Slice 7 settlement test");
        jdbc.update("insert into users(id, display_name) values (?, 'slice7 committee')", committeeUserId);
        jdbc.update("insert into users(id, display_name) values (?, 'slice7 coach')", coachUserId);
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
                """, courseId, organizationId, "S7-" + compact(courseId), committeeUserId);
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
                values (?, ?, ?, 'COMMITTEE', 'Slice 7 test venue', 100.00, 'COMMITTEE',
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
                """, receivableId, organizationId, "S7AR-" + compact(receivableId), courseId, coachUserId,
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

        return new Fixture(organizationId, committeeUserId, coachProfileId, courseSessionId);
    }

    private static void assertBusinessCode(Runnable call, String code) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.code()).isEqualTo(code));
    }

    private static String compact(UUID id) {
        return id.toString().replace("-", "").substring(0, 12);
    }

    private record Fixture(UUID organizationId, UUID committeeUserId, UUID coachProfileId, UUID courseSessionId) {}
}
