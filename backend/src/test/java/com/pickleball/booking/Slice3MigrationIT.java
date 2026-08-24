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
class Slice3MigrationIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("security.jwt.signing-secret", () -> "test-only-signing-secret-with-at-least-thirty-two-characters");
    }

    @Test
    void emptyDatabaseMigratesThroughSliceThree() {
        assertThat(jdbc.queryForObject(
                "select version from flyway_schema_history where success = true order by installed_rank desc limit 1",
                String.class)).isEqualTo("4");

        assertThat(tableExists("course_matches")).isTrue();
        assertThat(tableExists("course_match_sessions")).isTrue();
        assertThat(tableExists("course_match_session_coaches")).isTrue();
        assertThat(tableExists("course_match_price_snapshots")).isTrue();
        assertThat(tableExists("course_match_price_snapshot_items")).isTrue();
        assertThat(tableExists("courses")).isTrue();
        assertThat(tableExists("course_sessions")).isTrue();
        assertThat(tableExists("session_price_snapshots")).isTrue();
    }

    @Test
    void sliceTwoSchemaAndDataForwardMigrateToSliceThree() {
        String schema = "slice3_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource upgradeDataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        JdbcTemplate upgradeJdbc = new JdbcTemplate(upgradeDataSource);
        upgradeJdbc.execute("create schema " + schema);

        Flyway sliceTwo = Flyway.configure()
                .dataSource(upgradeDataSource)
                .schemas(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("3"))
                .load();
        sliceTwo.migrate();

        UUID organizationId = UUID.randomUUID();
        upgradeJdbc.update(
                "insert into " + schema + ".organizations(id, code, name) values (?, ?, ?)",
                organizationId, "upgrade-" + organizationId, "Slice 2 preserved data");

        Flyway sliceThree = Flyway.configure()
                .dataSource(upgradeDataSource)
                .schemas(schema)
                .locations("classpath:db/migration")
                .load();
        sliceThree.migrate();

        assertThat(upgradeJdbc.queryForObject(
                "select count(*) from " + schema + ".organizations where id = ?",
                Integer.class, organizationId)).isEqualTo(1);
        assertThat(upgradeJdbc.queryForObject(
                "select max(version) from " + schema + ".flyway_schema_history where success = true",
                String.class)).isEqualTo("4");
        assertThat(upgradeJdbc.queryForObject(
                "select to_regclass(?) is not null",
                Boolean.class, schema + ".course_matches")).isTrue();
    }

    @Test
    void matchPricingConstraintsProtectConfirmationAndFormalLineage() {
        Fixture fixture = seedFixture();

        UUID courseMatchId = UUID.randomUUID();
        UUID matchSessionId = UUID.randomUUID();
        UUID matchPriceSnapshotId = UUID.randomUUID();

        jdbc.update("""
                insert into course_matches(
                    id, organization_id, lesson_request_id, status,
                    participant_count_snapshot, minimum_participants_snapshot, maximum_participants_snapshot)
                values (?, ?, ?, 'DRAFT', 2, 1, 4)
                """, courseMatchId, fixture.organizationId(), fixture.lessonRequestId());
        jdbc.update("""
                insert into course_match_sessions(
                    id, course_match_id, sequence_no, start_at, end_at,
                    venue_name_snapshot, venue_address_snapshot)
                values (?, ?, 1, now() + interval '2 hours', now() + interval '3 hours', 'Test Venue', 'Taipei')
                """, matchSessionId, courseMatchId);
        jdbc.update("""
                insert into course_match_session_coaches(
                    id, course_match_session_id, coach_profile_id, role_type, status, invited_by)
                values (?, ?, ?, 'PRIMARY', 'INVITED', ?)
                """, UUID.randomUUID(), matchSessionId, fixture.coachProfileId(), fixture.committeeUserId());

        jdbc.update("""
                insert into course_match_price_snapshots(
                    id, organization_id, course_match_id, version_no, status,
                    billing_mode, total_amount, pricing_fingerprint,
                    confirmed_by, confirmed_at, created_by)
                values (?, ?, ?, 1, 'CONFIRMED', 'FULL_COURSE', 1800.00, ?, ?, now(), ?)
                """, matchPriceSnapshotId, fixture.organizationId(), courseMatchId,
                fingerprint("a"), fixture.committeeUserId(), fixture.committeeUserId());
        jdbc.update("""
                insert into course_match_price_snapshot_items(
                    id, course_match_price_snapshot_id, course_match_session_id,
                    item_type, description, quantity, unit_amount, line_amount)
                values (?, ?, ?, 'TUITION', 'Tuition', 1, 1800.00, 1800.00)
                """, UUID.randomUUID(), matchPriceSnapshotId, matchSessionId);

        assertThat(catchThrowable(() -> jdbc.update("""
                insert into course_match_price_snapshots(
                    id, organization_id, course_match_id, version_no, status,
                    billing_mode, total_amount, pricing_fingerprint,
                    confirmed_by, confirmed_at, created_by)
                values (?, ?, ?, 2, 'CONFIRMED', 'FULL_COURSE', 1800.00, ?, ?, now(), ?)
                """, UUID.randomUUID(), fixture.organizationId(), courseMatchId,
                fingerprint("b"), fixture.committeeUserId(), fixture.committeeUserId())))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID secondMatchId = UUID.randomUUID();
        jdbc.update("""
                insert into course_matches(id, organization_id, lesson_request_id, status, participant_count_snapshot)
                values (?, ?, ?, 'DRAFT', 1)
                """, secondMatchId, fixture.organizationId(), fixture.lessonRequestId());
        assertThat(catchThrowable(() -> jdbc.update("""
                insert into course_match_price_snapshots(
                    id, organization_id, course_match_id, version_no, status,
                    billing_mode, total_amount, pricing_fingerprint)
                values (?, ?, ?, 1, 'CONFIRMED', 'PER_SESSION', 900.00, ?)
                """, UUID.randomUUID(), fixture.organizationId(), secondMatchId, fingerprint("c"))))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID courseId = UUID.randomUUID();
        jdbc.update("""
                insert into courses(
                    id, organization_id, course_no, source_match_id, created_by_user_id,
                    course_type, schedule_type, billing_mode, expected_participant_count,
                    minimum_participants, maximum_participants, total_session_count, status, activated_at)
                values (?, ?, ?, ?, ?, 'PRIVATE', 'RECURRING', 'FULL_COURSE', 2, 1, 4, 2, 'ACTIVE', now())
                """, courseId, fixture.organizationId(), "C-" + courseId, courseMatchId, fixture.committeeUserId());

        assertThat(catchThrowable(() -> jdbc.update("""
                insert into courses(
                    id, organization_id, course_no, source_match_id, created_by_user_id,
                    course_type, schedule_type, billing_mode, expected_participant_count,
                    minimum_participants, maximum_participants, total_session_count, status)
                values (?, ?, ?, ?, ?, 'PRIVATE', 'SINGLE', 'FULL_COURSE', 2, 1, 4, 1, 'DRAFT')
                """, UUID.randomUUID(), fixture.organizationId(), "C-DUP-" + courseId,
                courseMatchId, fixture.committeeUserId())))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID courseSessionOne = createCourseSession(fixture.organizationId(), courseId, 1, "4 hours", "5 hours");
        UUID courseSessionTwo = createCourseSession(fixture.organizationId(), courseId, 2, "6 hours", "7 hours");

        jdbc.update("""
                insert into session_price_snapshots(
                    id, organization_id, course_session_id, version_no, status,
                    tuition_amount, venue_fee, other_adjustment, total_receivable,
                    source_match_price_snapshot_id, confirmed_by, confirmed_at, created_by)
                values (?, ?, ?, 1, 'CONFIRMED', 800.00, 100.00, 0.00, 900.00, ?, ?, now(), ?)
                """, UUID.randomUUID(), fixture.organizationId(), courseSessionOne,
                matchPriceSnapshotId, fixture.committeeUserId(), fixture.committeeUserId());

        assertThat(catchThrowable(() -> jdbc.update("""
                insert into session_price_snapshots(
                    id, organization_id, course_session_id, version_no, status,
                    tuition_amount, venue_fee, other_adjustment, total_receivable,
                    source_match_price_snapshot_id, source_offering_price_snapshot_id,
                    confirmed_by, confirmed_at, created_by)
                values (?, ?, ?, 1, 'CONFIRMED', 800.00, 100.00, 0.00, 900.00, ?, ?, ?, now(), ?)
                """, UUID.randomUUID(), fixture.organizationId(), courseSessionTwo,
                matchPriceSnapshotId, UUID.randomUUID(), fixture.committeeUserId(), fixture.committeeUserId())))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(catchThrowable(() -> jdbc.update("""
                insert into session_price_snapshots(
                    id, organization_id, course_session_id, version_no, status,
                    tuition_amount, venue_fee, other_adjustment, total_receivable,
                    source_match_price_snapshot_id, confirmed_by, confirmed_at, created_by)
                values (?, ?, ?, 1, 'CONFIRMED', 800.00, 100.00, 0.00, 900.00, ?, ?, now(), ?)
                """, UUID.randomUUID(), fixture.organizationId(), courseSessionTwo,
                UUID.randomUUID(), fixture.committeeUserId(), fixture.committeeUserId())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void availabilityClaimConvertedMatchForeignKeyIsEnforced() {
        Fixture fixture = seedFixture();
        UUID courseMatchId = UUID.randomUUID();
        jdbc.update("""
                insert into course_matches(id, organization_id, lesson_request_id, status, participant_count_snapshot)
                values (?, ?, ?, 'DRAFT', 1)
                """, courseMatchId, fixture.organizationId(), fixture.lessonRequestId());

        UUID claimId = UUID.randomUUID();
        jdbc.update("""
                insert into coach_availability_claims(
                    id, organization_id, coach_availability_proposal_id, lesson_request_id,
                    status, converted_course_match_id)
                values (?, ?, ?, ?, 'CONVERTED', ?)
                """, claimId, fixture.organizationId(), fixture.availabilityProposalId(),
                fixture.lessonRequestId(), courseMatchId);

        assertThat(jdbc.queryForObject(
                "select converted_course_match_id from coach_availability_claims where id = ?",
                UUID.class, claimId)).isEqualTo(courseMatchId);

        assertThat(catchThrowable(() -> jdbc.update(
                "update coach_availability_claims set converted_course_match_id = ? where id = ?",
                UUID.randomUUID(), claimId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Fixture seedFixture() {
        UUID organizationId = UUID.randomUUID();
        UUID coachUserId = UUID.randomUUID();
        UUID studentUserId = UUID.randomUUID();
        UUID committeeUserId = UUID.randomUUID();
        UUID coachProfileId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();
        UUID lessonRequestId = UUID.randomUUID();

        jdbc.update("insert into organizations(id, code, name) values (?, ?, ?)",
                organizationId, "slice3-" + organizationId, "Slice 3 test");
        for (UUID userId : java.util.List.of(coachUserId, studentUserId, committeeUserId)) {
            jdbc.update("insert into users(id, display_name) values (?, ?)", userId, "test user");
        }
        jdbc.update("""
                insert into coach_profiles(id, organization_id, user_id, approval_status)
                values (?, ?, ?, 'APPROVED')
                """, coachProfileId, organizationId, coachUserId);
        jdbc.update("""
                insert into coach_availability_proposals(
                    id, organization_id, coach_profile_id, start_at, end_at, status,
                    submitted_at, reviewed_by, reviewed_at, review_note)
                values (?, ?, ?, now() + interval '2 hours', now() + interval '3 hours', 'APPROVED',
                    now(), ?, now(), 'approved for Slice 3 test')
                """, proposalId, organizationId, coachProfileId, committeeUserId);
        jdbc.update("""
                insert into lesson_requests(
                    id, organization_id, requester_user_id, preferred_coach_profile_id,
                    lesson_type, schedule_type, billing_mode, participant_count,
                    guest_participant_count, requested_session_count, status,
                    submitted_at, reviewed_by, reviewed_at, review_note)
                values (?, ?, ?, ?, 'PRIVATE', 'SINGLE', 'FULL_COURSE', 2, 0, 1, 'APPROVED',
                    now(), ?, now(), 'approved for Slice 3 test')
                """, lessonRequestId, organizationId, studentUserId, coachProfileId, committeeUserId);

        return new Fixture(organizationId, coachProfileId, proposalId, lessonRequestId, committeeUserId);
    }

    private UUID createCourseSession(UUID organizationId, UUID courseId, int sequenceNo, String startOffset, String endOffset) {
        UUID sessionId = UUID.randomUUID();
        jdbc.update("""
                insert into course_sessions(
                    id, organization_id, course_id, sequence_no,
                    scheduled_start_at, scheduled_end_at,
                    expected_participant_count, guest_participant_count, status)
                values (?, ?, ?, ?, now() + (?::interval), now() + (?::interval), 2, 0, 'SCHEDULED')
                """, sessionId, organizationId, courseId, sequenceNo, startOffset, endOffset);
        return sessionId;
    }

    private boolean tableExists(String tableName) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select to_regclass(?) is not null", Boolean.class, tableName));
    }

    private String fingerprint(String value) {
        return value.repeat(64).substring(0, 64);
    }

    private record Fixture(
            UUID organizationId,
            UUID coachProfileId,
            UUID availabilityProposalId,
            UUID lessonRequestId,
            UUID committeeUserId) {
    }
}
