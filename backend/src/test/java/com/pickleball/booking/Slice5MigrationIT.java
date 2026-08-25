package com.pickleball.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

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
class Slice5MigrationIT {

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
    void emptyDatabaseMigratesThroughSliceFivePersistence() {
        assertThat(latestVersion(jdbc, "flyway_schema_history")).isEqualTo("9");
        assertThat(tableExists("member_cancellation_records")).isTrue();
        assertThat(tableExists("course_cancellation_requests")).isTrue();
        assertThat(tableExists("session_change_requests")).isTrue();
    }

    @Test
    void v8FormalCourseRowsForwardMigrateWithoutRewritingHistory() {
        String schema = "slice5_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource upgradeDataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        JdbcTemplate upgradeJdbc = new JdbcTemplate(upgradeDataSource);
        upgradeJdbc.execute("create schema " + schema);

        Flyway.configure()
                .dataSource(upgradeDataSource)
                .schemas(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("8"))
                .load()
                .migrate();

        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID courseSessionId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();

        upgradeJdbc.update("insert into " + schema + ".organizations(id, code, name) values (?, ?, ?)",
                organizationId, "s5-upgrade-" + compact(organizationId), "Slice 5 upgrade");
        upgradeJdbc.update("insert into " + schema + ".users(id, display_name) values (?, 'existing student')", userId);
        upgradeJdbc.update("""
                insert into %s.courses(
                    id, organization_id, course_no, created_by_user_id, course_type,
                    schedule_type, billing_mode, expected_participant_count,
                    guest_participant_count, total_session_count, status)
                values (?, ?, ?, ?, 'GROUP', 'SINGLE', 'FULL_COURSE', 1, 0, 1, 'ACTIVE')
                """.formatted(schema), courseId, organizationId, "S5-" + compact(courseId), userId);
        upgradeJdbc.update("""
                insert into %s.course_sessions(
                    id, organization_id, course_id, sequence_no,
                    scheduled_start_at, scheduled_end_at,
                    expected_participant_count, guest_participant_count, status)
                values (?, ?, ?, 1, '2030-04-01 10:00+00', '2030-04-01 11:00+00', 1, 0, 'SCHEDULED')
                """.formatted(schema), courseSessionId, organizationId, courseId);
        upgradeJdbc.update("""
                insert into %s.course_memberships(id, organization_id, course_id, user_id, status)
                values (?, ?, ?, ?, 'ACTIVE')
                """.formatted(schema), membershipId, organizationId, courseId, userId);
        upgradeJdbc.update("""
                insert into %s.enrollments(
                    id, organization_id, course_membership_id, course_session_id, user_id, status)
                values (?, ?, ?, ?, ?, 'SCHEDULED')
                """.formatted(schema), enrollmentId, organizationId, membershipId, courseSessionId, userId);

        Flyway.configure()
                .dataSource(upgradeDataSource)
                .schemas(schema)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertThat(latestVersion(upgradeJdbc, schema + ".flyway_schema_history")).isEqualTo("9");
        assertThat(upgradeJdbc.queryForObject("select status from " + schema + ".enrollments where id = ?",
                String.class, enrollmentId)).isEqualTo("SCHEDULED");
        assertThat(upgradeJdbc.queryForObject("select status from " + schema + ".course_sessions where id = ?",
                String.class, courseSessionId)).isEqualTo("SCHEDULED");
        assertThat(schemaTableExists(upgradeJdbc, schema, "member_cancellation_records")).isTrue();
        assertThat(schemaTableExists(upgradeJdbc, schema, "course_cancellation_requests")).isTrue();
        assertThat(schemaTableExists(upgradeJdbc, schema, "session_change_requests")).isTrue();
    }

    @Test
    void memberCancellationHistoryIsAppendOnlyPerEnrollmentAndReasonIsOptional() {
        Fixture fixture = seedFixture();
        UUID recordId = UUID.randomUUID();

        jdbc.update("""
                insert into member_cancellation_records(
                    id, organization_id, member_id, enrollment_id, course_session_id,
                    reason, cancelled_at)
                values (?, ?, ?, ?, ?, null, now())
                """, recordId, fixture.organizationId(), fixture.studentUserId(),
                fixture.enrollmentId(), fixture.firstSessionId());

        assertThat(jdbc.queryForObject(
                "select reason is null from member_cancellation_records where id = ?",
                Boolean.class, recordId)).isTrue();

        assertThat(catchThrowable(() -> jdbc.update("""
                insert into member_cancellation_records(
                    id, organization_id, member_id, enrollment_id, course_session_id,
                    cancelled_at)
                values (?, ?, ?, ?, ?, now())
                """, UUID.randomUUID(), fixture.organizationId(), fixture.studentUserId(),
                fixture.enrollmentId(), fixture.firstSessionId())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void coachCancellationRequestConstraintsProtectReviewLifecycle() {
        Fixture fixture = seedFixture();

        jdbc.update("""
                insert into course_cancellation_requests(
                    id, organization_id, course_session_id, requested_by,
                    requester_role, reason, status)
                values (?, ?, ?, ?, 'COACH', 'Coach unavailable', 'PENDING_REVIEW')
                """, UUID.randomUUID(), fixture.organizationId(), fixture.firstSessionId(), fixture.coachUserId());

        assertThat(catchThrowable(() -> jdbc.update("""
                insert into course_cancellation_requests(
                    id, organization_id, course_session_id, requested_by,
                    requester_role, reason, status)
                values (?, ?, ?, ?, 'COACH', 'Second pending request', 'PENDING_REVIEW')
                """, UUID.randomUUID(), fixture.organizationId(), fixture.firstSessionId(), fixture.coachUserId())))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(catchThrowable(() -> jdbc.update("""
                insert into course_cancellation_requests(
                    id, organization_id, course_session_id, requested_by,
                    requester_role, reason, status)
                values (?, ?, ?, ?, 'STUDENT', 'Not a coach request', 'PENDING_REVIEW')
                """, UUID.randomUUID(), fixture.organizationId(), fixture.secondSessionId(), fixture.studentUserId())))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(catchThrowable(() -> jdbc.update("""
                insert into course_cancellation_requests(
                    id, organization_id, course_session_id, requested_by,
                    requester_role, reason, status)
                values (?, ?, ?, ?, 'COACH', 'Reviewed without metadata', 'APPROVED')
                """, UUID.randomUUID(), fixture.organizationId(), fixture.secondSessionId(), fixture.coachUserId())))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("""
                insert into course_cancellation_requests(
                    id, organization_id, course_session_id, requested_by,
                    requester_role, reason, status, reviewed_by, reviewed_at, review_note)
                values (?, ?, ?, ?, 'COACH', 'Valid reviewed request', 'REJECTED', ?, now(), 'Class proceeds')
                """, UUID.randomUUID(), fixture.organizationId(), fixture.secondSessionId(), fixture.coachUserId(),
                fixture.committeeUserId());
    }

    @Test
    void sessionChangeRequestConstraintsProtectRescheduleAndDecisionHistory() {
        Fixture fixture = seedFixture();

        assertThat(catchThrowable(() -> jdbc.update("""
                insert into session_change_requests(
                    id, organization_id, course_session_id, request_type,
                    requested_by, reason, status)
                values (?, ?, ?, 'RESCHEDULE', ?, 'Missing proposed range', 'PENDING')
                """, UUID.randomUUID(), fixture.organizationId(), fixture.firstSessionId(),
                fixture.committeeUserId())))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(catchThrowable(() -> jdbc.update("""
                insert into session_change_requests(
                    id, organization_id, course_session_id, request_type,
                    requested_by, reason, proposed_start_at, proposed_end_at, status)
                values (?, ?, ?, 'RESCHEDULE', ?, 'Invalid proposed range',
                    '2030-05-01 12:00+00', '2030-05-01 11:00+00', 'PENDING')
                """, UUID.randomUUID(), fixture.organizationId(), fixture.firstSessionId(),
                fixture.committeeUserId())))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("""
                insert into session_change_requests(
                    id, organization_id, course_session_id, request_type,
                    requested_by, reason, proposed_start_at, proposed_end_at, status)
                values (?, ?, ?, 'RESCHEDULE', ?, 'Move one recurring session',
                    '2030-05-01 11:00+00', '2030-05-01 12:00+00', 'PENDING')
                """, UUID.randomUUID(), fixture.organizationId(), fixture.firstSessionId(),
                fixture.committeeUserId());

        assertThat(catchThrowable(() -> jdbc.update("""
                insert into session_change_requests(
                    id, organization_id, course_session_id, request_type,
                    requested_by, reason, status)
                values (?, ?, ?, 'CHANGE_VENUE', ?, 'Approved without decision metadata', 'APPROVED')
                """, UUID.randomUUID(), fixture.organizationId(), fixture.secondSessionId(),
                fixture.committeeUserId())))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("""
                insert into session_change_requests(
                    id, organization_id, course_session_id, request_type,
                    requested_by, reason, status, decided_by, decided_at, decision_reason)
                values (?, ?, ?, 'CHANGE_VENUE', ?, 'Venue coordination', 'APPROVED', ?, now(), 'Approved')
                """, UUID.randomUUID(), fixture.organizationId(), fixture.secondSessionId(),
                fixture.committeeUserId(), fixture.committeeUserId());
    }

    private Fixture seedFixture() {
        UUID organizationId = UUID.randomUUID();
        UUID studentUserId = UUID.randomUUID();
        UUID coachUserId = UUID.randomUUID();
        UUID committeeUserId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID firstSessionId = UUID.randomUUID();
        UUID secondSessionId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();

        jdbc.update("insert into organizations(id, code, name) values (?, ?, ?)",
                organizationId, "slice5-" + compact(organizationId), "Slice 5 test");
        for (UUID userId : java.util.List.of(studentUserId, coachUserId, committeeUserId)) {
            jdbc.update("insert into users(id, display_name) values (?, 'slice5 user')", userId);
        }
        jdbc.update("""
                insert into courses(
                    id, organization_id, course_no, created_by_user_id, course_type,
                    schedule_type, billing_mode, expected_participant_count,
                    guest_participant_count, total_session_count, status)
                values (?, ?, ?, ?, 'GROUP', 'RECURRING', 'FULL_COURSE', 1, 0, 2, 'ACTIVE')
                """, courseId, organizationId, "S5-" + compact(courseId), committeeUserId);
        jdbc.update("""
                insert into course_sessions(
                    id, organization_id, course_id, sequence_no,
                    scheduled_start_at, scheduled_end_at,
                    expected_participant_count, guest_participant_count, status)
                values (?, ?, ?, 1, '2030-05-01 10:00+00', '2030-05-01 11:00+00', 1, 0, 'SCHEDULED')
                """, firstSessionId, organizationId, courseId);
        jdbc.update("""
                insert into course_sessions(
                    id, organization_id, course_id, sequence_no,
                    scheduled_start_at, scheduled_end_at,
                    expected_participant_count, guest_participant_count, status)
                values (?, ?, ?, 2, '2030-05-08 10:00+00', '2030-05-08 11:00+00', 1, 0, 'SCHEDULED')
                """, secondSessionId, organizationId, courseId);
        jdbc.update("""
                insert into course_memberships(id, organization_id, course_id, user_id, status)
                values (?, ?, ?, ?, 'ACTIVE')
                """, membershipId, organizationId, courseId, studentUserId);
        jdbc.update("""
                insert into enrollments(
                    id, organization_id, course_membership_id, course_session_id, user_id, status)
                values (?, ?, ?, ?, ?, 'SCHEDULED')
                """, enrollmentId, organizationId, membershipId, firstSessionId, studentUserId);

        return new Fixture(organizationId, studentUserId, coachUserId, committeeUserId,
                firstSessionId, secondSessionId, enrollmentId);
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
            UUID studentUserId,
            UUID coachUserId,
            UUID committeeUserId,
            UUID firstSessionId,
            UUID secondSessionId,
            UUID enrollmentId) {}
}
