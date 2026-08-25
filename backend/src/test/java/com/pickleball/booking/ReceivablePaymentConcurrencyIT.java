package com.pickleball.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.receivable.application.ReceivableApplicationService;
import com.pickleball.booking.receivable.domain.PaymentMethod;
import com.pickleball.booking.shared.application.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
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
class ReceivablePaymentConcurrencyIT {

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
    void concurrentPaymentsAreSerializedAndCannotOverpayReceivable() throws Exception {
        Fixture fixture = seedFixture();
        var actor = new AuthenticatedPrincipal(fixture.committeeUserId());
        Instant paidAt = Instant.now();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<String> first = paymentAttempt(
                    ready, start, actor, fixture, paidAt, "concurrent-a-" + compact(fixture.receivableId()));
            Callable<String> second = paymentAttempt(
                    ready, start, actor, fixture, paidAt, "concurrent-b-" + compact(fixture.receivableId()));

            Future<String> firstResult = executor.submit(first);
            Future<String> secondResult = executor.submit(second);
            ready.await();
            start.countDown();

            assertThat(List.of(firstResult.get(), secondResult.get()))
                    .containsExactlyInAnyOrder("SUCCESS", "PAYMENT_AMOUNT_INVALID");
        }

        assertThat(jdbc.queryForObject("""
                select count(*)
                from payments p
                join payment_allocations pa on pa.payment_id = p.id
                join receivable_items ri on ri.id = pa.receivable_item_id
                where ri.receivable_id = ?
                """, Integer.class, fixture.receivableId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select paid_amount from receivables where id = ?",
                BigDecimal.class, fixture.receivableId())).isEqualByComparingTo("800.00");
        assertThat(jdbc.queryForObject(
                "select balance_amount from receivables where id = ?",
                BigDecimal.class, fixture.receivableId())).isEqualByComparingTo("400.00");
        assertThat(jdbc.queryForObject(
                "select status from receivables where id = ?",
                String.class, fixture.receivableId())).isEqualTo("PARTIALLY_PAID");
    }

    private Callable<String> paymentAttempt(
            CountDownLatch ready,
            CountDownLatch start,
            AuthenticatedPrincipal actor,
            Fixture fixture,
            Instant paidAt,
            String idempotencyKey) {
        return () -> {
            ready.countDown();
            start.await();
            try {
                service.recordPayment(
                        actor,
                        fixture.receivableId(),
                        new ReceivableApplicationService.RecordPaymentCommand(
                                new BigDecimal("800.00"),
                                PaymentMethod.CASH,
                                paidAt,
                                fixture.payerUserId(),
                                "concurrent payment"),
                        idempotencyKey,
                        "request-" + idempotencyKey);
                return "SUCCESS";
            } catch (BusinessException ex) {
                return ex.code();
            }
        };
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
                organizationId, "s6con-" + compact(organizationId), "Slice 6 concurrency test");
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
                """, courseId, organizationId, "S6C-" + compact(courseId), committeeUserId);
        jdbc.update("""
                insert into course_sessions(
                    id, organization_id, course_id, sequence_no,
                    scheduled_start_at, scheduled_end_at,
                    expected_participant_count, guest_participant_count, status)
                values (?, ?, ?, 1, '2030-06-15 10:00+00', '2030-06-15 11:00+00', 1, 0, 'SCHEDULED')
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

        return new Fixture(payerUserId, committeeUserId, receivableId);
    }

    private static String compact(UUID id) {
        return id.toString().replace("-", "").substring(0, 12);
    }

    private record Fixture(UUID payerUserId, UUID committeeUserId, UUID receivableId) {}
}
