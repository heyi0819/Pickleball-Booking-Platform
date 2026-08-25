package com.pickleball.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.math.BigDecimal;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class Slice6MigrationIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired DataSource dataSource;
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
    void emptyDatabaseMigratesThroughSliceSixPersistence() {
        assertThat(latestVersion(jdbc, "flyway_schema_history")).isEqualTo("10");
        assertThat(tableExists("receivables")).isTrue();
        assertThat(tableExists("receivable_items")).isTrue();
        assertThat(tableExists("receivable_adjustments")).isTrue();
        assertThat(tableExists("payments")).isTrue();
        assertThat(tableExists("payment_allocations")).isTrue();
        assertThat(tableExists("refunds")).isTrue();
    }

    @Test
    void v9ReceivablesForwardMigrateWithoutRewritingHistory() {
        String schema = "slice6_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource upgradeDataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        JdbcTemplate upgradeJdbc = new JdbcTemplate(upgradeDataSource);
        upgradeJdbc.execute("create schema " + schema);

        Flyway.configure()
                .dataSource(upgradeDataSource)
                .schemas(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("9"))
                .load()
                .migrate();

        UUID organizationId = UUID.randomUUID();
        UUID payerUserId = UUID.randomUUID();
        UUID committeeUserId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID receivableId = UUID.randomUUID();

        upgradeJdbc.update("insert into " + schema + ".organizations(id, code, name) values (?, ?, ?)",
                organizationId, "s6-upgrade-" + compact(organizationId), "Slice 6 upgrade");
        upgradeJdbc.update("insert into " + schema + ".users(id, display_name) values (?, 'existing payer')", payerUserId);
        upgradeJdbc.update("insert into " + schema + ".users(id, display_name) values (?, 'existing committee')", committeeUserId);
        upgradeJdbc.update("""
                insert into %s.courses(
                    id, organization_id, course_no, created_by_user_id, course_type,
                    schedule_type, billing_mode, expected_participant_count,
                    guest_participant_count, total_session_count, status)
                values (?, ?, ?, ?, 'GROUP', 'SINGLE', 'FULL_COURSE', 1, 0, 1, 'ACTIVE')
                """.formatted(schema), courseId, organizationId, "S6-" + compact(courseId), committeeUserId);
        upgradeJdbc.update("""
                insert into %s.receivables(
                    id, organization_id, receivable_no, course_id, payer_user_id,
                    billing_mode, total_amount, balance_amount, status)
                values (?, ?, ?, ?, ?, 'FULL_COURSE', 1200.00, 1200.00, 'OPEN')
                """.formatted(schema), receivableId, organizationId,
                "AR-" + compact(receivableId), courseId, payerUserId);

        Flyway.configure()
                .dataSource(upgradeDataSource)
                .schemas(schema)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertThat(latestVersion(upgradeJdbc, schema + ".flyway_schema_history")).isEqualTo("10");
        assertThat(upgradeJdbc.queryForObject(
                "select total_amount from " + schema + ".receivables where id = ?",
                BigDecimal.class, receivableId)).isEqualByComparingTo("1200.00");
        assertThat(upgradeJdbc.queryForObject(
                "select status from " + schema + ".receivables where id = ?",
                String.class, receivableId)).isEqualTo("OPEN");
        assertThat(schemaTableExists(upgradeJdbc, schema, "payments")).isTrue();
        assertThat(schemaTableExists(upgradeJdbc, schema, "refunds")).isTrue();
    }

    @Test
    void receivableAdjustmentsAreAppendOnlyPositiveAuditedEntries() {
        Fixture fixture = seedFixture();

        jdbc.update("""
                insert into receivable_adjustments(
                    id, organization_id, receivable_id, receivable_item_id,
                    adjustment_type, amount, reason, approved_by)
                values (?, ?, ?, ?, 'DECREASE', 100.00, 'Committee fee correction', ?)
                """, UUID.randomUUID(), fixture.organizationId(), fixture.receivableId(),
                fixture.receivableItemId(), fixture.committeeUserId());

        assertThat(catchThrowable(() -> jdbc.update("""
                insert into receivable_adjustments(
                    id, organization_id, receivable_id,
                    adjustment_type, amount, reason, approved_by)
                values (?, ?, ?, 'DECREASE', 0, 'Invalid zero adjustment', ?)
                """, UUID.randomUUID(), fixture.organizationId(), fixture.receivableId(),
                fixture.committeeUserId())))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(catchThrowable(() -> jdbc.update("""
                insert into receivable_adjustments(
                    id, organization_id, receivable_id,
                    adjustment_type, amount, reason, approved_by)
                values (?, ?, ?, 'REPLACE', 10.00, 'Invalid type', ?)
                """, UUID.randomUUID(), fixture.organizationId(), fixture.receivableId(),
                fixture.committeeUserId())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void paymentAndAllocationConstraintsProtectImmutableLedgerShape() {
        Fixture fixture = seedFixture();
        UUID paymentId = recordPayment(fixture, "pay-key-1", "PAY-1");

        jdbc.update("""
                insert into payment_allocations(
                    id, payment_id, receivable_item_id, amount, allocated_by)
                values (?, ?, ?, 600.00, ?)
                """, UUID.randomUUID(), paymentId, fixture.receivableItemId(), fixture.committeeUserId());

        assertThat(catchThrowable(() -> jdbc.update("""
                insert into payment_allocations(
                    id, payment_id, receivable_item_id, amount, allocated_by)
                values (?, ?, ?, 10.00, ?)
                """, UUID.randomUUID(), paymentId, fixture.receivableItemId(), fixture.committeeUserId())))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(catchThrowable(() -> jdbc.update("""
                insert into payments(
                    id, organization_id, payment_no, payer_user_id, amount,
                    payment_method, status, paid_at, recorded_by, idempotency_key)
                values (?, ?, ?, ?, 100.00, 'CASH', 'COMPLETED', now(), ?, 'pay-key-1')
                """, UUID.randomUUID(), fixture.organizationId(), "PAY-2",
                fixture.payerUserId(), fixture.committeeUserId())))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(catchThrowable(() -> jdbc.update("""
                insert into payments(
                    id, organization_id, payment_no, payer_user_id, amount,
                    payment_method, status, paid_at, recorded_by)
                values (?, ?, ?, ?, 0, 'CASH', 'COMPLETED', now(), ?)
                """, UUID.randomUUID(), fixture.organizationId(), "PAY-3",
                fixture.payerUserId(), fixture.committeeUserId())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refundLifecycleMetadataMatchesApprovalThenExecutionFlow() {
        Fixture fixture = seedFixture();
        UUID paymentId = recordPayment(fixture, "refund-payment-key", "PAY-R");
        UUID refundId = UUID.randomUUID();

        jdbc.update("""
                insert into refunds(
                    id, organization_id, refund_no, payment_id, receivable_item_id,
                    enrollment_id, amount, status, reason, requested_by)
                values (?, ?, ?, ?, ?, ?, 300.00, 'PENDING_APPROVAL', 'Student withdrawal', ?)
                """, refundId, fixture.organizationId(), "RF-" + compact(refundId), paymentId,
                fixture.receivableItemId(), fixture.enrollmentId(), fixture.payerUserId());

        assertThat(jdbc.queryForObject(
                "select refund_method is null from refunds where id = ?", Boolean.class, refundId)).isTrue();

        assertThat(catchThrowable(() -> jdbc.update("""
                update refunds set status = 'APPROVED', updated_at = now(), version = version + 1
                where id = ?
                """, refundId))).isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("""
                update refunds
                set status = 'APPROVED', approved_by = ?, approved_at = now(),
                    approval_note = 'Approved amount', updated_at = now(), version = version + 1
                where id = ?
                """, fixture.committeeUserId(), refundId);

        assertThat(catchThrowable(() -> jdbc.update("""
                update refunds
                set status = 'COMPLETED', updated_at = now(), version = version + 1
                where id = ?
                """, refundId))).isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("""
                update refunds
                set status = 'COMPLETED', refund_method = 'BANK_TRANSFER',
                    processed_by = ?, refunded_at = now(), reference_no = 'BANK-RF-1',
                    updated_at = now(), version = version + 1
                where id = ?
                """, fixture.committeeUserId(), refundId);

        assertThat(jdbc.queryForObject("select status from refunds where id = ?", String.class, refundId))
                .isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select approved_by from refunds where id = ?", UUID.class, refundId))
                .isEqualTo(fixture.committeeUserId());
        assertThat(jdbc.queryForObject("select processed_by from refunds where id = ?", UUID.class, refundId))
                .isEqualTo(fixture.committeeUserId());

        assertThat(catchThrowable(() -> jdbc.update("""
                insert into refunds(
                    id, organization_id, refund_no, payment_id,
                    amount, status, reason, requested_by, refund_method)
                values (?, ?, ?, ?, 10.00, 'PENDING_APPROVAL', 'Invalid method', ?, 'CARD')
                """, UUID.randomUUID(), fixture.organizationId(), "RF-BAD-" + compact(UUID.randomUUID()),
                paymentId, fixture.payerUserId())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID recordPayment(Fixture fixture, String idempotencyKey, String paymentNo) {
        UUID paymentId = UUID.randomUUID();
        jdbc.update("""
                insert into payments(
                    id, organization_id, payment_no, payer_user_id, amount,
                    payment_method, status, paid_at, recorded_by, idempotency_key)
                values (?, ?, ?, ?, 600.00, 'BANK_TRANSFER', 'COMPLETED', now(), ?, ?)
                """, paymentId, fixture.organizationId(), paymentNo, fixture.payerUserId(),
                fixture.committeeUserId(), idempotencyKey);
        return paymentId;
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
                organizationId, "slice6-" + compact(organizationId), "Slice 6 test");
        jdbc.update("insert into users(id, display_name) values (?, 'slice6 payer')", payerUserId);
        jdbc.update("insert into users(id, display_name) values (?, 'slice6 committee')", committeeUserId);
        jdbc.update("""
                insert into courses(
                    id, organization_id, course_no, created_by_user_id, course_type,
                    schedule_type, billing_mode, expected_participant_count,
                    guest_participant_count, total_session_count, status)
                values (?, ?, ?, ?, 'GROUP', 'SINGLE', 'FULL_COURSE', 1, 0, 1, 'ACTIVE')
                """, courseId, organizationId, "S6-" + compact(courseId), committeeUserId);
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

        return new Fixture(organizationId, payerUserId, committeeUserId, enrollmentId,
                receivableId, receivableItemId);
    }

    private boolean tableExists(String tableName) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(
                    select 1 from information_schema.tables
                    where table_schema = current_schema() and table_name = ?
                )
                """, Boolean.class, tableName));
    }

    private static boolean schemaTableExists(JdbcTemplate template, String schema, String tableName) {
        return Boolean.TRUE.equals(template.queryForObject("""
                select exists(
                    select 1 from information_schema.tables
                    where table_schema = ? and table_name = ?
                )
                """, Boolean.class, schema, tableName));
    }

    private static String latestVersion(JdbcTemplate template, String historyTable) {
        return template.queryForObject(
                "select version from " + historyTable + " where success order by installed_rank desc limit 1",
                String.class);
    }

    private static String compact(UUID id) {
        return id.toString().replace("-", "").substring(0, 12);
    }

    private record Fixture(
            UUID organizationId,
            UUID payerUserId,
            UUID committeeUserId,
            UUID enrollmentId,
            UUID receivableId,
            UUID receivableItemId) {}
}
