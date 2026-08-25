package com.pickleball.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.receivable.application.ReceivableApplicationService;
import com.pickleball.booking.receivable.domain.PaymentMethod;
import com.pickleball.booking.shared.application.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
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
class ReceivableApplicationServiceIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired ReceivableApplicationService service;
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
    void partialPaymentIsAllocatedAndIdempotentReplayDoesNotDuplicateLedgerEntry() {
        Fixture fixture = seedFixture();
        var command = command(fixture, "600.00", PaymentMethod.CASH);

        var first = service.recordPayment(
                new AuthenticatedPrincipal(fixture.committeeUserId()), fixture.receivableId(),
                command, "payment-key-1-" + compact(fixture.receivableId()), "request-1");
        var replay = service.recordPayment(
                new AuthenticatedPrincipal(fixture.committeeUserId()), fixture.receivableId(),
                command, "payment-key-1-" + compact(fixture.receivableId()), "request-2");

        assertThat(replay.paymentId()).isEqualTo(first.paymentId());
        assertThat(first.paymentStatus()).isEqualTo("PARTIALLY_PAID");
        assertThat(first.paidTotal()).isEqualByComparingTo("600.00");
        assertThat(first.outstandingAmount()).isEqualByComparingTo("600.00");
        assertThat(jdbc.queryForObject("select count(*) from payments where id = ?", Integer.class, first.paymentId()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select amount from payment_allocations where payment_id = ?",
                BigDecimal.class, first.paymentId())).isEqualByComparingTo("600.00");
        assertThat(jdbc.queryForObject(
                "select paid_amount from receivables where id = ?", BigDecimal.class, fixture.receivableId()))
                .isEqualByComparingTo("600.00");
        assertThat(jdbc.queryForObject(
                "select balance_amount from receivables where id = ?", BigDecimal.class, fixture.receivableId()))
                .isEqualByComparingTo("600.00");
        assertThat(jdbc.queryForObject(
                "select status from receivables where id = ?", String.class, fixture.receivableId()))
                .isEqualTo("PARTIALLY_PAID");
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_logs where action = 'PAYMENT_RECORDED' and entity_id = ?",
                Integer.class, first.paymentId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from outbox_events where event_type = 'PAYMENT_RECORDED' and aggregate_id = ?",
                Integer.class, first.paymentId())).isEqualTo(1);
    }

    @Test
    void secondInstallmentCanCompleteReceivable() {
        Fixture fixture = seedFixture();
        AuthenticatedPrincipal actor = new AuthenticatedPrincipal(fixture.committeeUserId());

        service.recordPayment(actor, fixture.receivableId(), command(fixture, "500.00", PaymentMethod.BANK_TRANSFER),
                "payment-first-" + compact(fixture.receivableId()), "request-first");
        var completed = service.recordPayment(actor, fixture.receivableId(), command(fixture, "700.00", PaymentMethod.CASH),
                "payment-final-" + compact(fixture.receivableId()), "request-final");

        assertThat(completed.paymentStatus()).isEqualTo("PAID");
        assertThat(completed.paidTotal()).isEqualByComparingTo("1200.00");
        assertThat(completed.outstandingAmount()).isEqualByComparingTo("0.00");
        assertThat(jdbc.queryForObject(
                "select status from receivables where id = ?", String.class, fixture.receivableId())).isEqualTo("PAID");
        assertThat(jdbc.queryForObject(
                "select closed_at is not null from receivables where id = ?", Boolean.class, fixture.receivableId()))
                .isTrue();
        assertThat(jdbc.queryForObject(
                "select paid_amount from receivable_items where id = ?", BigDecimal.class, fixture.receivableItemId()))
                .isEqualByComparingTo("1200.00");
    }

    @Test
    void overpaymentRollsBackWithoutCreatingPayment() {
        Fixture fixture = seedFixture();
        AuthenticatedPrincipal actor = new AuthenticatedPrincipal(fixture.committeeUserId());
        service.recordPayment(actor, fixture.receivableId(), command(fixture, "600.00", PaymentMethod.CASH),
                "payment-base-" + compact(fixture.receivableId()), "request-base");

        assertBusinessCode(() -> service.recordPayment(
                actor, fixture.receivableId(), command(fixture, "600.01", PaymentMethod.CASH),
                "payment-over-" + compact(fixture.receivableId()), "request-over"), "PAYMENT_AMOUNT_INVALID");

        assertThat(jdbc.queryForObject(
                "select count(*) from payments p join payment_allocations pa on pa.payment_id=p.id "
                        + "join receivable_items ri on ri.id=pa.receivable_item_id where ri.receivable_id=?",
                Integer.class, fixture.receivableId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select balance_amount from receivables where id = ?", BigDecimal.class, fixture.receivableId()))
                .isEqualByComparingTo("600.00");
    }

    @Test
    void nonCommitteeCannotRecordPaymentAndPayerCannotBeMassAssigned() {
        Fixture fixture = seedFixture();
        UUID studentUserId = UUID.randomUUID();
        jdbc.update("insert into users(id, display_name) values (?, 'slice6 student')", studentUserId);
        jdbc.update("""
                insert into user_role_assignments(
                    id, organization_id, user_id, role_code, status, granted_by, granted_at)
                values (?, ?, ?, 'STUDENT', 'ACTIVE', ?, now())
                """, UUID.randomUUID(), fixture.organizationId(), studentUserId, fixture.committeeUserId());

        assertBusinessCode(() -> service.recordPayment(
                new AuthenticatedPrincipal(studentUserId), fixture.receivableId(),
                command(fixture, "100.00", PaymentMethod.CASH),
                "student-payment-" + compact(fixture.receivableId()), "request-student"), "AUTH_FORBIDDEN");

        var wrongPayer = new ReceivableApplicationService.RecordPaymentCommand(
                new BigDecimal("100.00"), PaymentMethod.CASH, Instant.now(), studentUserId, "wrong payer");
        assertBusinessCode(() -> service.recordPayment(
                new AuthenticatedPrincipal(fixture.committeeUserId()), fixture.receivableId(), wrongPayer,
                "wrong-payer-" + compact(fixture.receivableId()), "request-wrong-payer"), "VALIDATION_FAILED");
    }

    private ReceivableApplicationService.RecordPaymentCommand command(
            Fixture fixture, String amount, PaymentMethod method) {
        return new ReceivableApplicationService.RecordPaymentCommand(
                new BigDecimal(amount), method, Instant.now(), fixture.payerUserId(), "manual payment");
    }

    private Fixture seedFixture() {
        UUID organizationId = UUID.randomUUID();
        UUID payerUserId = UUID.randomUUID();
        UUID committeeUserId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID courseSessionId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        UUID priceSnapshotId = UUID.randomUUID();
        UUID receivableId = UUID.randomUUID();
        UUID receivableItemId = UUID.randomUUID();

        jdbc.update("insert into organizations(id, code, name) values (?, ?, ?)",
                organizationId, "s6pay-" + compact(organizationId), "Slice 6 payment test");
        jdbc.update("insert into users(id, display_name) values (?, 'slice6 payer')", payerUserId);
        jdbc.update("insert into users(id, display_name) values (?, 'slice6 committee')", committeeUserId);
        jdbc.update("""
                insert into user_role_assignments(
                    id, organization_id, user_id, role_code, status, granted_by, granted_at)
                values (?, ?, ?, 'COMMITTEE', 'ACTIVE', ?, now())
                """, UUID.randomUUID(), organizationId, committeeUserId, committeeUserId);
        jdbc.update("""
                insert into courses(
                    id, organization_id, course_no, created_by_user_id, course_type,
                    schedule_type, billing_mode, expected_participant_count,
                    guest_participant_count, total_session_count, status)
                values (?, ?, ?, ?, 'GROUP', 'SINGLE', 'FULL_COURSE', 1, 0, 1, 'ACTIVE')
                """, courseId, organizationId, "S6P-" + compact(courseId), committeeUserId);
        jdbc.update("""
                insert into course_sessions(
                    id, organization_id, course_id, sequence_no,
                    scheduled_start_at, scheduled_end_at,
                    expected_participant_count, guest_participant_count, status)
                values (?, ?, ?, 1, '2030-06-01 10:00+00', '2030-06-01 11:00+00', 1, 0, 'SCHEDULED')
                """, courseSessionId, organizationId, courseId);
        jdbc.update("""
                insert into course_memberships(id, organization_id, course_id, user_id, status)
                values (?, ?, ?, ?, 'ACTIVE')
                """, membershipId, organizationId, courseId, payerUserId);
        jdbc.update("""
                insert into enrollments(
                    id, organization_id, course_membership_id, course_session_id, user_id, status)
                values (?, ?, ?, ?, ?, 'SCHEDULED')
                """, enrollmentId, organizationId, membershipId, courseSessionId, payerUserId);
        jdbc.update("""
                insert into session_price_snapshots(
                    id, organization_id, course_session_id, version_no, status,
                    tuition_amount, venue_fee, other_adjustment, total_receivable,
                    confirmed_by, confirmed_at, created_by)
                values (?, ?, ?, 1, 'CONFIRMED', 1200.00, 0, 0, 1200.00, ?, now(), ?)
                """, priceSnapshotId, organizationId, courseSessionId, committeeUserId, committeeUserId);
        jdbc.update("""
                insert into receivables(
                    id, organization_id, receivable_no, course_id, payer_user_id,
                    billing_mode, total_amount, balance_amount, status)
                values (?, ?, ?, ?, ?, 'FULL_COURSE', 1200.00, 1200.00, 'OPEN')
                """, receivableId, organizationId, "AR-" + compact(receivableId), courseId, payerUserId);
        jdbc.update("""
                insert into receivable_items(
                    id, receivable_id, course_session_id, enrollment_id, price_snapshot_id,
                    amount, paid_amount, refunded_amount, status)
                values (?, ?, ?, ?, ?, 1200.00, 0, 0, 'OPEN')
                """, receivableItemId, receivableId, courseSessionId, enrollmentId, priceSnapshotId);

        return new Fixture(organizationId, payerUserId, committeeUserId, receivableId, receivableItemId);
    }

    private static void assertBusinessCode(Runnable call, String code) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.code()).isEqualTo(code));
    }

    private static String compact(UUID id) {
        return id.toString().replace("-", "").substring(0, 12);
    }

    private record Fixture(
            UUID organizationId,
            UUID payerUserId,
            UUID committeeUserId,
            UUID receivableId,
            UUID receivableItemId) {}
}
