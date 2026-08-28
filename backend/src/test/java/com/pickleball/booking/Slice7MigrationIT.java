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
class Slice7MigrationIT {

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
    void emptyDatabaseMigratesThroughSliceSevenPersistence() {
        assertThat(migrationApplied(jdbc, "flyway_schema_history", "11")).isTrue();
        assertThat(tableExists("session_settlements")).isTrue();
        assertThat(tableExists("settlement_adjustments")).isTrue();
        assertThat(tableExists("coach_settlements")).isTrue();
        assertThat(tableExists("payout_batches")).isTrue();
        assertThat(tableExists("payout_batch_items")).isTrue();
    }

    @Test
    void v10FinanceRowsForwardMigrateWithoutRewritingHistory() {
        String schema = "slice7_upgrade_" + compact(UUID.randomUUID());
        DriverManagerDataSource upgradeDataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        JdbcTemplate upgradeJdbc = new JdbcTemplate(upgradeDataSource);
        upgradeJdbc.execute("create schema " + schema);

        Flyway.configure()
                .dataSource(upgradeDataSource)
                .schemas(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("10"))
                .load()
                .migrate();

        UUID organizationId = UUID.randomUUID();
        UUID committeeUserId = UUID.randomUUID();
        UUID payerUserId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID receivableId = UUID.randomUUID();
        upgradeJdbc.update("insert into " + schema + ".organizations(id, code, name) values (?, ?, ?)",
                organizationId, "s7-" + compact(organizationId), "Slice 7 upgrade");
        upgradeJdbc.update("insert into " + schema + ".users(id, display_name) values (?, 'committee')", committeeUserId);
        upgradeJdbc.update("insert into " + schema + ".users(id, display_name) values (?, 'payer')", payerUserId);
        upgradeJdbc.update("""
                insert into %s.courses(
                    id, organization_id, course_no, created_by_user_id, course_type,
                    schedule_type, billing_mode, expected_participant_count,
                    guest_participant_count, total_session_count, status)
                values (?, ?, ?, ?, 'GROUP', 'SINGLE', 'FULL_COURSE', 1, 0, 1, 'ACTIVE')
                """.formatted(schema), courseId, organizationId, "S7-" + compact(courseId), committeeUserId);
        upgradeJdbc.update("""
                insert into %s.receivables(
                    id, organization_id, receivable_no, course_id, payer_user_id,
                    billing_mode, total_amount, balance_amount, status)
                values (?, ?, ?, ?, ?, 'FULL_COURSE', 1800.00, 1800.00, 'OPEN')
                """.formatted(schema), receivableId, organizationId,
                "AR-" + compact(receivableId), courseId, payerUserId);

        Flyway.configure()
                .dataSource(upgradeDataSource)
                .schemas(schema)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertThat(migrationApplied(upgradeJdbc, schema + ".flyway_schema_history", "11")).isTrue();
        assertThat(upgradeJdbc.queryForObject(
                "select total_amount from " + schema + ".receivables where id = ?",
                BigDecimal.class, receivableId)).isEqualByComparingTo("1800.00");
        assertThat(upgradeJdbc.queryForObject(
                "select status from " + schema + ".receivables where id = ?",
                String.class, receivableId)).isEqualTo("OPEN");
        assertThat(schemaTableExists(upgradeJdbc, schema, "session_settlements")).isTrue();
        assertThat(schemaTableExists(upgradeJdbc, schema, "payout_batches")).isTrue();
    }

    @Test
    void settlementRelationalGuardsProtectFinancialShape() {
        Fixture f = seedFixture();
        UUID settlementId = insertSettlement(f, "CALCULATED");

        assertThat(catchThrowable(() -> jdbc.update("""
                insert into session_settlements(
                    id, organization_id, course_session_id, price_snapshot_id,
                    gross_receivable, venue_cost, other_adjustment, distributable_amount, status)
                values (?, ?, ?, ?, 1800.00, 300.00, 0, 1500.00, 'CALCULATED')
                """, UUID.randomUUID(), f.organizationId(), f.courseSessionId(), f.priceSnapshotId())))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(catchThrowable(() -> jdbc.update("""
                insert into session_settlements(
                    id, organization_id, course_session_id, price_snapshot_id,
                    gross_receivable, venue_cost, other_adjustment, distributable_amount, status)
                values (?, ?, ?, ?, 1800.00, 300.00, 0, 1400.00, 'CALCULATED')
                """, UUID.randomUUID(), f.organizationId(), f.secondCourseSessionId(), f.secondPriceSnapshotId())))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("""
                insert into settlement_adjustments(
                    id, organization_id, session_settlement_id, adjustment_type,
                    amount, direction, before_amount, after_amount, handling_method,
                    reason, approved_by)
                values (?, ?, ?, 'OTHER', 100.00, 'INCREASE', 0, 100.00, 'MANUAL', ?, ?)
                """, UUID.randomUUID(), f.organizationId(), settlementId,
                "Committee correction", f.committeeUserId());

        assertThat(catchThrowable(() -> jdbc.update("""
                insert into settlement_adjustments(
                    id, organization_id, session_settlement_id, adjustment_type,
                    amount, direction, before_amount, after_amount, reason, approved_by)
                values (?, ?, ?, 'OTHER', 100.00, 'DECREASE', 500.00, 450.00, ?, ?)
                """, UUID.randomUUID(), f.organizationId(), settlementId,
                "Invalid arithmetic", f.committeeUserId())))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID coachSettlementId = UUID.randomUUID();
        jdbc.update("""
                insert into coach_settlements(
                    id, organization_id, session_settlement_id, coach_assignment_id,
                    coach_profile_id, allocation_type, payable_amount, paid_amount, payout_status)
                values (?, ?, ?, ?, ?, 'EQUAL', 1500.00, 0, 'WAITING_RECEIPT')
                """, coachSettlementId, f.organizationId(), settlementId,
                f.coachAssignmentId(), f.coachProfileId());

        assertThat(catchThrowable(() -> jdbc.update("""
                update coach_settlements
                set paid_amount = 1600.00, updated_at = now(), version = version + 1
                where id = ?
                """, coachSettlementId))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void payoutRelationalGuardsRejectDuplicateAndIncompletePaidRecords() {
        Fixture f = seedFixture();
        UUID settlementId = insertSettlement(f, "CONFIRMED");
        UUID coachSettlementId = UUID.randomUUID();
        jdbc.update("""
                insert into coach_settlements(
                    id, organization_id, session_settlement_id, coach_assignment_id,
                    coach_profile_id, allocation_type, payable_amount, paid_amount,
                    payout_status, ready_at)
                values (?, ?, ?, ?, ?, 'EQUAL', 1500.00, 0, 'READY', now())
                """, coachSettlementId, f.organizationId(), settlementId,
                f.coachAssignmentId(), f.coachProfileId());

        UUID batchId = insertBatch(f, "PB-" + compact(UUID.randomUUID()));
        UUID itemId = UUID.randomUUID();
        jdbc.update("""
                insert into payout_batch_items(
                    id, payout_batch_id, coach_settlement_id, coach_profile_id, amount, status)
                values (?, ?, ?, ?, 1500.00, 'PLANNED')
                """, itemId, batchId, coachSettlementId, f.coachProfileId());

        assertThat(catchThrowable(() -> jdbc.update("""
                insert into payout_batch_items(
                    id, payout_batch_id, coach_settlement_id, coach_profile_id, amount, status)
                values (?, ?, ?, ?, 10.00, 'PLANNED')
                """, UUID.randomUUID(), batchId, coachSettlementId, f.coachProfileId())))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(catchThrowable(() -> jdbc.update(
                "update payout_batch_items set status='PAID', updated_at=now() where id=?", itemId)))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("""
                update payout_batch_items
                set status='PAID', paid_at=now(), processed_by=?, reference_no='CASH-1', updated_at=now()
                where id=?
                """, f.committeeUserId(), itemId);

        assertThat(catchThrowable(() -> jdbc.update(
                "update payout_batches set status='COMPLETED', updated_at=now(), version=version+1 where id=?", batchId)))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("""
                update payout_batches
                set status='COMPLETED', approved_by=?, approved_at=now(), completed_at=now(),
                    updated_at=now(), version=version+1
                where id=?
                """, f.committeeUserId(), batchId);
        assertThat(jdbc.queryForObject("select status from payout_batches where id=?", String.class, batchId))
                .isEqualTo("COMPLETED");
    }

    private Fixture seedFixture() {
        UUID organizationId = UUID.randomUUID();
        UUID committeeUserId = UUID.randomUUID();
        UUID coachUserId = UUID.randomUUID();
        UUID coachProfileId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID courseSessionId = UUID.randomUUID();
        UUID secondCourseSessionId = UUID.randomUUID();
        UUID priceSnapshotId = UUID.randomUUID();
        UUID secondPriceSnapshotId = UUID.randomUUID();
        UUID coachAssignmentId = UUID.randomUUID();

        jdbc.update("insert into organizations(id, code, name) values (?, ?, ?)",
                organizationId, "slice7-" + compact(organizationId), "Slice 7 test");
        jdbc.update("insert into users(id, display_name) values (?, 'slice7 committee')", committeeUserId);
        jdbc.update("insert into users(id, display_name) values (?, 'slice7 coach')", coachUserId);
        jdbc.update("insert into coach_profiles(id, organization_id, user_id, approval_status) values (?, ?, ?, 'APPROVED')",
                coachProfileId, organizationId, coachUserId);
        jdbc.update("""
                insert into courses(
                    id, organization_id, course_no, created_by_user_id, course_type,
                    schedule_type, billing_mode, expected_participant_count,
                    guest_participant_count, total_session_count, status)
                values (?, ?, ?, ?, 'GROUP', 'RECURRING', 'PER_SESSION', 1, 0, 2, 'ACTIVE')
                """, courseId, organizationId, "S7-" + compact(courseId), committeeUserId);
        insertSession(courseSessionId, organizationId, courseId, 1, "2030-07-01 10:00+00", "2030-07-01 11:00+00");
        insertSession(secondCourseSessionId, organizationId, courseId, 2, "2030-07-08 10:00+00", "2030-07-08 11:00+00");
        insertSnapshot(priceSnapshotId, organizationId, courseSessionId, committeeUserId);
        insertSnapshot(secondPriceSnapshotId, organizationId, secondCourseSessionId, committeeUserId);
        jdbc.update("""
                insert into session_coach_assignments(
                    id, organization_id, course_session_id, coach_profile_id,
                    source_type, status, is_primary, responded_at, assigned_by)
                values (?, ?, ?, ?, 'DIRECT', 'ACCEPTED', true, now(), ?)
                """, coachAssignmentId, organizationId, courseSessionId, coachProfileId, committeeUserId);

        return new Fixture(organizationId, committeeUserId, coachProfileId, courseSessionId,
                secondCourseSessionId, priceSnapshotId, secondPriceSnapshotId, coachAssignmentId);
    }

    private void insertSession(UUID id, UUID organizationId, UUID courseId, int sequence, String start, String end) {
        jdbc.update("""
                insert into course_sessions(
                    id, organization_id, course_id, sequence_no, scheduled_start_at, scheduled_end_at,
                    expected_participant_count, guest_participant_count, actual_participant_count,
                    status, completed_at)
                values (?, ?, ?, ?, ?::timestamptz, ?::timestamptz, 1, 0, 1, 'COMPLETED', ?::timestamptz)
                """, id, organizationId, courseId, sequence, start, end, end);
    }

    private void insertSnapshot(UUID id, UUID organizationId, UUID sessionId, UUID committeeUserId) {
        jdbc.update("""
                insert into session_price_snapshots(
                    id, organization_id, course_session_id, version_no, status,
                    tuition_amount, venue_fee, other_adjustment, total_receivable,
                    confirmed_by, confirmed_at, created_by)
                values (?, ?, ?, 1, 'CONFIRMED', 1800.00, 300.00, 0, 2100.00, ?, now(), ?)
                """, id, organizationId, sessionId, committeeUserId, committeeUserId);
    }

    private UUID insertSettlement(Fixture f, String status) {
        UUID id = UUID.randomUUID();
        boolean confirmed = "CONFIRMED".equals(status);
        jdbc.update("""
                insert into session_settlements(
                    id, organization_id, course_session_id, price_snapshot_id,
                    gross_receivable, venue_cost, other_adjustment, distributable_amount,
                    status, confirmed_by, confirmed_at)
                values (?, ?, ?, ?, 1800.00, 300.00, 0, 1500.00, ?, ?, ?)
                """, id, f.organizationId(), f.courseSessionId(), f.priceSnapshotId(), status,
                confirmed ? f.committeeUserId() : null,
                confirmed ? java.sql.Timestamp.from(java.time.Instant.now()) : null);
        return id;
    }

    private UUID insertBatch(Fixture f, String batchNo) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into payout_batches(
                    id, organization_id, batch_no, status, payout_date, method,
                    total_amount, item_count, created_by)
                values (?, ?, ?, 'DRAFT', current_date, 'CASH', 1500.00, 1, ?)
                """, id, f.organizationId(), batchNo, f.committeeUserId());
        return id;
    }

    private boolean tableExists(String tableName) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from information_schema.tables
                    where table_schema=current_schema() and table_name=?)
                """, Boolean.class, tableName));
    }

    private static boolean schemaTableExists(JdbcTemplate template, String schema, String tableName) {
        return Boolean.TRUE.equals(template.queryForObject("""
                select exists(select 1 from information_schema.tables
                    where table_schema=? and table_name=?)
                """, Boolean.class, schema, tableName));
    }

    private static boolean migrationApplied(JdbcTemplate template, String historyTable, String version) {
        Integer count = template.queryForObject(
                "select count(*) from " + historyTable + " where success=true and version=?",
                Integer.class, version);
        return count != null && count == 1;
    }

    private static String compact(UUID value) {
        return value.toString().replace("-", "").substring(0, 12);
    }

    private record Fixture(
            UUID organizationId,
            UUID committeeUserId,
            UUID coachProfileId,
            UUID courseSessionId,
            UUID secondCourseSessionId,
            UUID priceSnapshotId,
            UUID secondPriceSnapshotId,
            UUID coachAssignmentId) {}
}
