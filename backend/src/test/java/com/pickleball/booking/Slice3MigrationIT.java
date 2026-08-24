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
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("security.jwt.signing-secret", () -> "test-only-signing-secret-with-at-least-thirty-two-characters");
    }

    @Test
    void emptyDatabaseMigratesThroughCurrentSliceThree() {
        assertThat(latestVersion(jdbc, "flyway_schema_history")).isEqualTo("6");
        assertThat(tableExists("course_matches")).isTrue();
        assertThat(tableExists("course_match_sessions")).isTrue();
        assertThat(tableExists("course_match_session_coaches")).isTrue();
        assertThat(tableExists("course_match_price_snapshots")).isTrue();
        assertThat(tableExists("course_match_price_snapshot_items")).isTrue();
        assertThat(tableExists("pricing_rules")).isTrue();
        assertThat(columnExists("course_matches", "participant_count")).isTrue();
        assertThat(columnExists("course_match_sessions", "venue_fingerprint")).isTrue();
        assertThat(columnExists("course_match_session_coaches", "assignment_order")).isTrue();
        assertThat(columnExists("course_match_session_coaches", "role_type")).isFalse();
    }

    @Test
    void sliceTwoSchemaAndDataForwardMigrateThroughCurrentSliceThree() {
        String schema = "slice3_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource upgradeDataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        JdbcTemplate upgradeJdbc = new JdbcTemplate(upgradeDataSource);
        upgradeJdbc.execute("create schema " + schema);

        Flyway.configure().dataSource(upgradeDataSource).schemas(schema).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("3")).load().migrate();
        UUID organizationId = UUID.randomUUID();
        upgradeJdbc.update("insert into " + schema + ".organizations(id, code, name) values (?, ?, ?)",
                organizationId, "upgrade-" + organizationId, "Slice 2 preserved data");

        Flyway.configure().dataSource(upgradeDataSource).schemas(schema).locations("classpath:db/migration")
                .load().migrate();

        assertThat(upgradeJdbc.queryForObject(
                "select count(*) from " + schema + ".organizations where id = ?", Integer.class, organizationId))
                .isEqualTo(1);
        assertThat(latestVersion(upgradeJdbc, schema + ".flyway_schema_history")).isEqualTo("6");
        assertThat(upgradeJdbc.queryForObject("select to_regclass(?) is not null", Boolean.class,
                schema + ".pricing_rules")).isTrue();
    }

    @Test
    void pricingConstraintsProtectRuleAndSnapshotIntegrity() {
        Fixture fixture = seedFixture();
        UUID matchId = createMatch(fixture);
        UUID sessionId = createMatchSession(matchId);

        UUID ruleId = UUID.randomUUID();
        jdbc.update("""
                insert into pricing_rules(
                    id, organization_id, name, priority, coach_profile_id, course_type,
                    min_participants, max_participants, base_amount, pricing_unit,
                    active_from, status)
                values (?, ?, 'Primary private price', 10, ?, 'PRIVATE', 1, 4, 800.00, 'PER_SESSION', now(), 'ACTIVE')
                """, ruleId, fixture.organizationId(), fixture.coachProfileId());
        assertThat(jdbc.queryForObject("select count(*) from pricing_rules where id=?", Integer.class, ruleId)).isOne();

        assertThat(catchThrowable(() -> jdbc.update("""
                insert into pricing_rules(
                    id, organization_id, name, priority, base_amount, pricing_unit, active_from, status)
                values (?, ?, 'invalid', 1, -1.00, 'PER_SESSION', now(), 'ACTIVE')
                """, UUID.randomUUID(), fixture.organizationId())))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID snapshotId = UUID.randomUUID();
        jdbc.update("""
                insert into course_match_price_snapshots(
                    id, organization_id, course_match_id, version_no, status, billing_mode,
                    total_amount, pricing_fingerprint, confirmed_by, confirmed_at, created_by)
                values (?, ?, ?, 1, 'CONFIRMED', 'FULL_COURSE', 800.00, ?, ?, now(), ?)
                """, snapshotId, fixture.organizationId(), matchId, fingerprint("a"),
                fixture.committeeUserId(), fixture.committeeUserId());
        jdbc.update("""
                insert into course_match_price_snapshot_items(
                    id, course_match_price_snapshot_id, course_match_session_id,
                    item_type, description, quantity, unit_amount, line_amount)
                values (?, ?, ?, 'TUITION', 'Tuition', 1, 800.00, 800.00)
                """, UUID.randomUUID(), snapshotId, sessionId);

        assertThat(catchThrowable(() -> jdbc.update("""
                insert into course_match_price_snapshots(
                    id, organization_id, course_match_id, version_no, status, billing_mode,
                    total_amount, pricing_fingerprint, confirmed_by, confirmed_at, created_by)
                values (?, ?, ?, 2, 'CONFIRMED', 'FULL_COURSE', 800.00, ?, ?, now(), ?)
                """, UUID.randomUUID(), fixture.organizationId(), matchId, fingerprint("b"),
                fixture.committeeUserId(), fixture.committeeUserId())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void availabilityClaimConvertedMatchForeignKeyIsEnforced() {
        Fixture fixture = seedFixture();
        UUID matchId = createMatch(fixture);
        UUID claimId = UUID.randomUUID();
        jdbc.update("""
                insert into coach_availability_claims(
                    id, organization_id, coach_availability_proposal_id, lesson_request_id,
                    status, converted_course_match_id)
                values (?, ?, ?, ?, 'CONVERTED', ?)
                """, claimId, fixture.organizationId(), fixture.availabilityProposalId(),
                fixture.lessonRequestId(), matchId);
        assertThat(jdbc.queryForObject(
                "select converted_course_match_id from coach_availability_claims where id=?", UUID.class, claimId))
                .isEqualTo(matchId);
        assertThat(catchThrowable(() -> jdbc.update(
                "update coach_availability_claims set converted_course_match_id=? where id=?",
                UUID.randomUUID(), claimId))).isInstanceOf(DataIntegrityViolationException.class);
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
        jdbc.update("insert into coach_profiles(id, organization_id, user_id, approval_status) values (?, ?, ?, 'APPROVED')",
                coachProfileId, organizationId, coachUserId);
        jdbc.update("""
                insert into coach_availability_proposals(
                    id, organization_id, coach_profile_id, start_at, end_at, status,
                    submitted_at, reviewed_by, reviewed_at, review_note)
                values (?, ?, ?, now()+interval '2 hours', now()+interval '3 hours', 'APPROVED',
                    now(), ?, now(), 'approved')
                """, proposalId, organizationId, coachProfileId, committeeUserId);
        jdbc.update("""
                insert into lesson_requests(
                    id, organization_id, requester_user_id, preferred_coach_profile_id,
                    lesson_type, schedule_type, billing_mode, participant_count,
                    guest_participant_count, requested_session_count, status,
                    submitted_at, reviewed_by, reviewed_at, review_note)
                values (?, ?, ?, ?, 'PRIVATE', 'SINGLE', 'FULL_COURSE', 2, 0, 1, 'APPROVED',
                    now(), ?, now(), 'approved')
                """, lessonRequestId, organizationId, studentUserId, coachProfileId, committeeUserId);
        return new Fixture(organizationId, coachProfileId, proposalId, lessonRequestId, committeeUserId);
    }

    private UUID createMatch(Fixture fixture) {
        UUID matchId = UUID.randomUUID();
        jdbc.update("""
                insert into course_matches(id, organization_id, lesson_request_id, status, participant_count, created_by)
                values (?, ?, ?, 'DRAFT', 2, ?)
                """, matchId, fixture.organizationId(), fixture.lessonRequestId(), fixture.committeeUserId());
        return matchId;
    }

    private UUID createMatchSession(UUID matchId) {
        UUID sessionId = UUID.randomUUID();
        jdbc.update("""
                insert into course_match_sessions(
                    id, course_match_id, session_index, scheduled_start_at, scheduled_end_at,
                    venue_snapshot_type, venue_snapshot_name, venue_fingerprint)
                values (?, ?, 1, now()+interval '2 hours', now()+interval '3 hours', 'OTHER', 'Test Venue', ?)
                """, sessionId, matchId, fingerprint("v"));
        return sessionId;
    }

    private String latestVersion(JdbcTemplate template, String historyTable) {
        return template.queryForObject(
                "select version from " + historyTable + " where success=true order by installed_rank desc limit 1",
                String.class);
    }

    private boolean tableExists(String tableName) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select to_regclass(?) is not null", Boolean.class, tableName));
    }

    private boolean columnExists(String tableName, String columnName) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from information_schema.columns
                    where table_schema=current_schema() and table_name=? and column_name=?)
                """, Boolean.class, tableName, columnName));
    }

    private String fingerprint(String value) { return value.repeat(64).substring(0, 64); }

    private record Fixture(UUID organizationId, UUID coachProfileId, UUID availabilityProposalId,
            UUID lessonRequestId, UUID committeeUserId) {}
}
