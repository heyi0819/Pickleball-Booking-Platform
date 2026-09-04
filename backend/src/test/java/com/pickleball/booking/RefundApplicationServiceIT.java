package com.pickleball.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.receivable.application.ReceivableApplicationService;
import com.pickleball.booking.receivable.application.RefundApplicationService;
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
class RefundApplicationServiceIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired ReceivableApplicationService receivableService;
    @Autowired RefundApplicationService refundService;
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
    void refundRequiresApprovalAndPartialExecutionUpdatesLedgerWithoutOverwritingPaymentAmount() {
        Fixture fixture = seedPaidFixture();
        AuthenticatedPrincipal actor = new AuthenticatedPrincipal(fixture.committeeUserId());
        AuthenticatedPrincipal reviewer = new AuthenticatedPrincipal(fixture.reviewerUserId());

        var requested = refundService.requestRefund(
                actor,
                fixture.receivableId(),
                new RefundApplicationService.RequestRefundCommand(
                        fixture.paymentId(), new BigDecimal("600.00"), "student withdrawal"),
                "refund-request-" + compact(fixture.receivableId()),
                "refund-request-1");

        assertThat(requested.status()).isEqualTo("PENDING_APPROVAL");
        assertBusinessCode(() -> refundService.executeRefund(
                actor,
                requested.refundId(),
                new RefundApplicationService.ExecuteRefundCommand(
                        PaymentMethod.BANK_TRANSFER, Instant.now().minusSeconds(1), "RF-001"),
                "refund-exec-before-approve-" + compact(requested.refundId()),
                "refund-exec-before-approve"), "REFUND_NOT_APPROVED");

        var approved = refundService.reviewRefund(
                reviewer,
                requested.refundId(),
                new RefundApplicationService.ReviewRefundCommand(
                        RefundApplicationService.ReviewDecision.APPROVE, "approved by committee"),
                "refund-review-" + compact(requested.refundId()),
                "refund-review-1");
        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(approved.approvedBy()).isEqualTo(fixture.reviewerUserId());
        assertThat(approved.approvedAt()).isNotNull();

        var completed = refundService.executeRefund(
                actor,
                requested.refundId(),
                new RefundApplicationService.ExecuteRefundCommand(
                        PaymentMethod.BANK_TRANSFER, Instant.now().minusSeconds(1), "RF-001"),
                "refund-exec-" + compact(requested.refundId()),
                "refund-exec-1");

        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.processedBy()).isEqualTo(fixture.committeeUserId());
        assertThat(completed.refundedAt()).isNotNull();
        assertThat(jdbc.queryForObject(
                "select amount from payments where id = ?", BigDecimal.class, fixture.paymentId()))
                .isEqualByComparingTo("1200.00");
        assertThat(jdbc.queryForObject(
                "select status from payments where id = ?", String.class, fixture.paymentId()))
                .isEqualTo("PARTIALLY_REFUNDED");
        assertThat(jdbc.queryForObject(
                "select refunded_amount from receivables where id = ?", BigDecimal.class, fixture.receivableId()))
                .isEqualByComparingTo("600.00");
        assertThat(jdbc.queryForObject(
                "select balance_amount from receivables where id = ?", BigDecimal.class, fixture.receivableId()))
                .isEqualByComparingTo("600.00");
        assertThat(jdbc.queryForObject(
                "select refunded_amount from receivable_items where id = ?", BigDecimal.class, fixture.receivableItemId()))
                .isEqualByComparingTo("600.00");
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_logs where entity_id = ? and action = 'REFUND_REQUESTED'",
                Integer.class, requested.refundId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_logs where entity_id = ? and action = 'REFUND_APPROVED'",
                Integer.class, requested.refundId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_logs where entity_id = ? and action = 'REFUND_COMPLETED'",
                Integer.class, requested.refundId())).isEqualTo(1);
    }

    @Test
    void refundRequestAndExecutionAreIdempotent() {
        Fixture fixture = seedPaidFixture();
        AuthenticatedPrincipal actor = new AuthenticatedPrincipal(fixture.committeeUserId());
        AuthenticatedPrincipal reviewer = new AuthenticatedPrincipal(fixture.reviewerUserId());
        var requestCommand = new RefundApplicationService.RequestRefundCommand(
                fixture.paymentId(), new BigDecimal("500.00"), "idempotent refund");
        String requestKey = "refund-idem-request-" + compact(fixture.receivableId());

        var first = refundService.requestRefund(
                actor, fixture.receivableId(), requestCommand, requestKey, "idem-request-1");
        var replay = refundService.requestRefund(
                actor, fixture.receivableId(), requestCommand, requestKey, "idem-request-2");
        assertThat(replay.refundId()).isEqualTo(first.refundId());
        assertThat(jdbc.queryForObject(
                "select count(*) from refunds where payment_id = ?", Integer.class, fixture.paymentId()))
                .isEqualTo(1);

        refundService.reviewRefund(
                reviewer,
                first.refundId(),
                new RefundApplicationService.ReviewRefundCommand(
                        RefundApplicationService.ReviewDecision.APPROVE, "approved"),
                "refund-idem-review-" + compact(first.refundId()),
                "idem-review");

        Instant refundedAt = Instant.now().minusSeconds(1);
        var executeCommand = new RefundApplicationService.ExecuteRefundCommand(
                PaymentMethod.CASH, refundedAt, "CASH-001");
        String executeKey = "refund-idem-exec-" + compact(first.refundId());
        var executed = refundService.executeRefund(
                actor, first.refundId(), executeCommand, executeKey, "idem-exec-1");
        var executeReplay = refundService.executeRefund(
                actor, first.refundId(), executeCommand, executeKey, "idem-exec-2");

        assertThat(executeReplay.refundId()).isEqualTo(executed.refundId());
        assertThat(jdbc.queryForObject(
                "select refunded_amount from receivables where id = ?", BigDecimal.class, fixture.receivableId()))
                .isEqualByComparingTo("500.00");
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_logs where entity_id = ? and action = 'REFUND_COMPLETED'",
                Integer.class, first.refundId())).isEqualTo(1);
    }

    @Test
    void requesterCannotReviewTheirOwnRefund() {
        Fixture fixture = seedPaidFixture();
        AuthenticatedPrincipal requester = new AuthenticatedPrincipal(fixture.committeeUserId());
        var requested = refundService.requestRefund(
                requester,
                fixture.receivableId(),
                new RefundApplicationService.RequestRefundCommand(
                        fixture.paymentId(), new BigDecimal("100.00"), "separation of duties"),
                "refund-self-review-request-" + compact(fixture.receivableId()),
                "refund-self-review-request");

        assertBusinessCode(() -> refundService.reviewRefund(
                requester,
                requested.refundId(),
                new RefundApplicationService.ReviewRefundCommand(
                        RefundApplicationService.ReviewDecision.APPROVE, "not permitted"),
                "refund-self-review-" + compact(requested.refundId()),
                "refund-self-review"), "REVIEWER_SELF_APPROVAL_FORBIDDEN");
    }

    @Test
    void pendingAndApprovedRefundsReserveCapacityAndRejectedRefundReleasesIt() {
        Fixture fixture = seedPaidFixture();
        AuthenticatedPrincipal actor = new AuthenticatedPrincipal(fixture.committeeUserId());
        AuthenticatedPrincipal reviewer = new AuthenticatedPrincipal(fixture.reviewerUserId());

        var first = refundService.requestRefund(
                actor,
                fixture.receivableId(),
                new RefundApplicationService.RequestRefundCommand(
                        fixture.paymentId(), new BigDecimal("800.00"), "first request"),
                "refund-cap-first-" + compact(fixture.receivableId()),
                "refund-cap-first");

        assertBusinessCode(() -> refundService.requestRefund(
                actor,
                fixture.receivableId(),
                new RefundApplicationService.RequestRefundCommand(
                        fixture.paymentId(), new BigDecimal("500.00"), "would exceed"),
                "refund-cap-over-" + compact(fixture.receivableId()),
                "refund-cap-over"), "REFUND_EXCEEDS_REFUNDABLE");

        refundService.reviewRefund(
                reviewer,
                first.refundId(),
                new RefundApplicationService.ReviewRefundCommand(
                        RefundApplicationService.ReviewDecision.REJECT, "policy rejected"),
                "refund-cap-reject-" + compact(first.refundId()),
                "refund-cap-reject");

        var replacement = refundService.requestRefund(
                actor,
                fixture.receivableId(),
                new RefundApplicationService.RequestRefundCommand(
                        fixture.paymentId(), new BigDecimal("1200.00"), "capacity released"),
                "refund-cap-replacement-" + compact(fixture.receivableId()),
                "refund-cap-replacement");
        assertThat(replacement.status()).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void fullRefundMarksPaymentAndReceivableRefunded() {
        Fixture fixture = seedPaidFixture();
        AuthenticatedPrincipal actor = new AuthenticatedPrincipal(fixture.committeeUserId());
        AuthenticatedPrincipal reviewer = new AuthenticatedPrincipal(fixture.reviewerUserId());
        var requested = refundService.requestRefund(
                actor,
                fixture.receivableId(),
                new RefundApplicationService.RequestRefundCommand(
                        fixture.paymentId(), new BigDecimal("1200.00"), "full refund"),
                "refund-full-request-" + compact(fixture.receivableId()),
                "refund-full-request");
        refundService.reviewRefund(
                reviewer,
                requested.refundId(),
                new RefundApplicationService.ReviewRefundCommand(
                        RefundApplicationService.ReviewDecision.APPROVE, "approved"),
                "refund-full-review-" + compact(requested.refundId()),
                "refund-full-review");
        refundService.executeRefund(
                actor,
                requested.refundId(),
                new RefundApplicationService.ExecuteRefundCommand(
                        PaymentMethod.CASH, Instant.now().minusSeconds(1), null),
                "refund-full-exec-" + compact(requested.refundId()),
                "refund-full-exec");

        assertThat(jdbc.queryForObject(
                "select status from payments where id = ?", String.class, fixture.paymentId()))
                .isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject(
                "select status from receivables where id = ?", String.class, fixture.receivableId()))
                .isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject(
                "select refunded_amount from receivables where id = ?", BigDecimal.class, fixture.receivableId()))
                .isEqualByComparingTo("1200.00");
        assertThat(jdbc.queryForObject(
                "select balance_amount from receivables where id = ?", BigDecimal.class, fixture.receivableId()))
                .isEqualByComparingTo("1200.00");
    }

    private Fixture seedPaidFixture() {
        UUID organizationId = UUID.randomUUID();
        UUID payerUserId = UUID.randomUUID();
        UUID committeeUserId = UUID.randomUUID();
        UUID reviewerUserId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID courseSessionId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        UUID priceSnapshotId = UUID.randomUUID();
        UUID receivableId = UUID.randomUUID();
        UUID receivableItemId = UUID.randomUUID();

        jdbc.update("insert into organizations(id, code, name) values (?, ?, ?)",
                organizationId, "s6rf-" + compact(organizationId), "Slice 6 refund test");
        jdbc.update("insert into users(id, display_name) values (?, 'slice6 refund payer')", payerUserId);
        jdbc.update("insert into users(id, display_name) values (?, 'slice6 refund committee')", committeeUserId);
        jdbc.update("insert into users(id, display_name) values (?, 'slice6 refund reviewer')", reviewerUserId);
        jdbc.update("""
                insert into user_role_assignments(
                    id, organization_id, user_id, role_code, status, granted_by, granted_at)
                values (?, ?, ?, 'COMMITTEE', 'ACTIVE', ?, now())
                """, UUID.randomUUID(), organizationId, committeeUserId, committeeUserId);
        jdbc.update("""
                insert into user_role_assignments(
                    id, organization_id, user_id, role_code, status, granted_by, granted_at)
                values (?, ?, ?, 'COMMITTEE', 'ACTIVE', ?, now())
                """, UUID.randomUUID(), organizationId, reviewerUserId, committeeUserId);
        jdbc.update("""
                insert into courses(
                    id, organization_id, course_no, created_by_user_id, course_type,
                    schedule_type, billing_mode, expected_participant_count,
                    guest_participant_count, total_session_count, status)
                values (?, ?, ?, ?, 'GROUP', 'SINGLE', 'FULL_COURSE', 1, 0, 1, 'ACTIVE')
                """, courseId, organizationId, "S6RF-" + compact(courseId), committeeUserId);
        jdbc.update("""
                insert into course_sessions(
                    id, organization_id, course_id, sequence_no,
                    scheduled_start_at, scheduled_end_at,
                    expected_participant_count, guest_participant_count, status)
                values (?, ?, ?, 1, '2030-08-01 10:00+00', '2030-08-01 11:00+00', 1, 0, 'SCHEDULED')
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

        var payment = receivableService.recordPayment(
                new AuthenticatedPrincipal(committeeUserId),
                receivableId,
                new ReceivableApplicationService.RecordPaymentCommand(
                        new BigDecimal("1200.00"), PaymentMethod.BANK_TRANSFER,
                        Instant.now().minusSeconds(1), payerUserId, "paid before refund"),
                "payment-before-refund-" + compact(receivableId),
                "payment-before-refund");
        return new Fixture(
                organizationId, payerUserId, committeeUserId, reviewerUserId,
                receivableId, receivableItemId, payment.paymentId());
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
            UUID reviewerUserId,
            UUID receivableId,
            UUID receivableItemId,
            UUID paymentId) {}
}
