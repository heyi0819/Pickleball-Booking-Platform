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
class Slice4MigrationIT {

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
    void emptyDatabaseMigratesThroughSliceFourPersistence() {
        assertThat(Integer.parseInt(latestVersion(jdbc, "flyway_schema_history"))).isGreaterThanOrEqualTo(8);
        for (String table : java.util.List.of(
                "course_offerings",
                "course_offering_sessions",
                "course_offering_price_snapshots",
                "course_offering_registrations")) {
            assertThat(tableExists(table)).as(table).isTrue();
        }
        assertThat(columnExists("courses", "source_offering_id")).isTrue();
        assertThat(columnExists("schedule_reservations", "course_offering_session_id")).isTrue();
        assertThat(columnExists("session_price_snapshots", "source_offering_price_snapshot_id")).isTrue();
    }

    @Test
    void v7FormalReservationRowsForwardMigrateWithoutRewritingHistory() {
        String schema = "slice4_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource upgradeDataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        JdbcTemplate upgradeJdbc = new JdbcTemplate(upgradeDataSource);
        upgradeJdbc.execute("create schema " + schema);

        Flyway.configure()
                .dataSource(upgradeDataSource)
                .schemas(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("7"))
                .load()
                .migrate();

        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID courseSessionId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();

        upgradeJdbc.update("insert into " + schema + ".organizations(id, code, name) values (?, ?, ?)",
                organizationId, "s4-upgrade-" + organizationId, "Slice 4 upgrade");
        upgradeJdbc.update("insert into " + schema + ".users(id, display_name) values (?, 'existing user')", userId);
        upgradeJdbc.update("""
                insert into %s.courses(
                    id, organization_id, course_no, created_by_user_id, course_type,
                    schedule_type, billing_mode, expected_participant_count,
                    guest_participant_count, total_session_count, status)
                values (?, ?, ?, ?, 'GROUP', 'SINGLE', 'FULL_COURSE', 1, 0, 1, 'ACTIVE')
                """.formatted(schema), courseId, organizationId, "S4-" + compact(courseId), userId);
        upgradeJdbc.update("""
                insert into %s.course_sessions(
                    id, organization_id, course_id, sequence_no,
                    scheduled_start_at, scheduled_end_at,
                    expected_participant_count, guest_participant_count, status)
                values (?, ?, ?, 1, '2030-01-01 10:00+00', '2030-01-01 11:00+00', 1, 0, 'SCHEDULED')
                """.formatted(schema), courseSessionId, organizationId, courseId);
        upgradeJdbc.update("""
                insert into %s.schedule_reservations(
                    id, organization_id, user_id, course_session_id,
                    reservation_role, reserved_period, status)
                values (?, ?, ?, ?, 'PARTICIPANT',
                    tstzrange('2030-01-01 10:00+00', '2030-01-01 11:00+00', '[)'), 'CONFIRMED')
                """.formatted(schema), reservationId, organizationId, userId, courseSessionId);

        Flyway.configure()
                .dataSource(upgradeDataSource)
                .schemas(schema)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertThat(Integer.parseInt(latestVersion(upgradeJdbc, schema + ".flyway_schema_history")))
                .isGreaterThanOrEqualTo(8);
        assertThat(upgradeJdbc.queryForObject("""
                select course_session_id from %s.schedule_reservations where id = ?
                """.formatted(schema), UUID.class, reservationId)).isEqualTo(courseSessionId);
        assertThat(upgradeJdbc.queryForObject("""
                select course_offering_session_id is null
                from %s.schedule_reservations where id = ?
                """.formatted(schema), Boolean.class, reservationId)).isTrue();
    }

    @Test
    void registrationAndSourceConstraintsProtectOpenEnrollmentIntegrity() {
        Fixture fixture = seedFixture();
        UUID offeringId = createOpenOffering(fixture, (short) 1, (short) 2);

        jdbc.update("""
                insert into course_offering_registrations(
                    id, organization_id, course_offering_id, user_id, status)
                values (?, ?, ?, ?, 'ACTIVE')
                """, UUID.randomUUID(), fixture.organizationId(), offeringId, fixture.studentUserId());

        assertThat(catchThrowable(() -> jdbc.update("""
                insert into course_offering_registrations(
                    id, organization_id, course_offering_id, user_id, status)
                values (?, ?, ?, ?, 'ACTIVE')
                """, UUID.randomUUID(), fixture.organizationId(), offeringId, fixture.studentUserId())))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID matchId = UUID.randomUUID();
        jdbc.update("""
                insert into course_matches(id, organization_id, status, participant_count, created_by)
                values (?, ?, 'DRAFT', 1, ?)
                """, matchId, fixture.organizationId(), fixture.committeeUserId());

        assertThat(catchThrowable(() -> insertCourse(
                UUID.randomUUID(), fixture, matchId, offeringId, "BOTH-" + compact(UUID.randomUUID()))))
                .isInstanceOf(DataIntegrityViolationException.class);

        insertCourse(UUID.randomUUID(), fixture, null, offeringId, "OFFER-" + compact(offeringId));
        assertThat(catchThrowable(() -> insertCourse(
                UUID.randomUUID(), fixture, null, offeringId, "DUP-" + compact(UUID.randomUUID()))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void reservationExclusionProtectsAcrossOfferingAndFormalCourseSources() {
        Fixture fixture = seedFixture();
        UUID offeringId = createOpenOffering(fixture, (short) 1, (short) 4);
        UUID offeringSessionId = UUID.randomUUID();
        jdbc.update("""
                insert into course_offering_sessions(
                    id, organization_id, course_offering_id, sequence_no,
                    start_at, end_at, venue_name_snapshot)
                values (?, ?, ?, 1, '2030-02-01 10:00+00', '2030-02-01 11:00+00', 'Open Enrollment Court')
                """, offeringSessionId, fixture.organizationId(), offeringId);

        jdbc.update("""
                insert into schedule_reservations(
                    id, organization_id, user_id, course_offering_session_id,
                    reservation_role, reserved_period, status)
                values (?, ?, ?, ?, 'PARTICIPANT',
                    tstzrange('2030-02-01 10:00+00', '2030-02-01 11:00+00', '[)'), 'HELD')
                """, UUID.randomUUID(), fixture.organizationId(), fixture.studentUserId(), offeringSessionId);

        UUID courseId = UUID.randomUUID();
        insertCourse(courseId, fixture, null, null, "DIRECT-" + compact(courseId));
        UUID formalSessionId = UUID.randomUUID();
        jdbc.update("""
                insert into course_sessions(
                    id, organization_id, course_id, sequence_no,
                    scheduled_start_at, scheduled_end_at,
                    expected_participant_count, guest_participant_count, status)
                values (?, ?, ?, 1, '2030-02-01 10:30+00', '2030-02-01 11:30+00', 1, 0, 'SCHEDULED')
                """, formalSessionId, fixture.organizationId(), courseId);

        assertThat(catchThrowable(() -> jdbc.update("""
                insert into schedule_reservations(
                    id, organization_id, user_id, course_session_id,
                    reservation_role, reserved_period, status)
                values (?, ?, ?, ?, 'PARTICIPANT',
                    tstzrange('2030-02-01 10:30+00', '2030-02-01 11:30+00', '[)'), 'CONFIRMED')
                """, UUID.randomUUID(), fixture.organizationId(), fixture.studentUserId(), formalSessionId)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(catchThrowable(() -> jdbc.update("""
                insert into schedule_reservations(
                    id, organization_id, user_id, reservation_role, reserved_period, status)
                values (?, ?, ?, 'PARTICIPANT',
                    tstzrange('2030-02-01 12:00+00', '2030-02-01 13:00+00', '[)'), 'HELD')
                """, UUID.randomUUID(), fixture.organizationId(), fixture.studentUserId())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void offeringPriceSnapshotCompletesDeferredSessionPriceLineageForeignKey() {
        Fixture fixture = seedFixture();
        UUID offeringId = createOpenOffering(fixture, (short) 1, (short) 4);
        UUID offeringPriceSnapshotId = UUID.randomUUID();
        jdbc.update("""
                insert into course_offering_price_snapshots(
                    id, organization_id, course_offering_id, version_no, status,
                    price_per_participant, confirmed_by, confirmed_at, created_by)
                values (?, ?, ?, 1, 'CONFIRMED', 900.00, ?, now(), ?)
                """, offeringPriceSnapshotId, fixture.organizationId(), offeringId,
                fixture.committeeUserId(), fixture.committeeUserId());

        UUID courseId = UUID.randomUUID();
        insertCourse(courseId, fixture, null, offeringId, "PRICE-" + compact(courseId));
        UUID formalSessionId = UUID.randomUUID();
        jdbc.update("""
                insert into course_sessions(
                    id, organization_id, course_id, sequence_no,
                    scheduled_start_at, scheduled_end_at,
                    expected_participant_count, guest_participant_count, status)
                values (?, ?, ?, 1, '2030-03-01 10:00+00', '2030-03-01 11:00+00', 1, 0, 'SCHEDULED')
                """, formalSessionId, fixture.organizationId(), courseId);

        jdbc.update("""
                insert into session_price_snapshots(
                    id, organization_id, course_session_id, version_no, status,
                    tuition_amount, venue_fee, other_adjustment, total_receivable,
                    source_offering_price_snapshot_id, confirmed_by, confirmed_at, created_by)
                values (?, ?, ?, 1, 'CONFIRMED', 900.00, 0, 0, 900.00, ?, ?, now(), ?)
                """, UUID.randomUUID(), fixture.organizationId(), formalSessionId,
                offeringPriceSnapshotId, fixture.committeeUserId(), fixture.committeeUserId());

        assertThat(catchThrowable(() -> jdbc.update("""
                update session_price_snapshots
                set source_offering_price_snapshot_id = ?
                where course_session_id = ?
                """, UUID.randomUUID(), formalSessionId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Fixture seedFixture() {
        UUID organizationId = UUID.randomUUID();
        UUID coachUserId = UUID.randomUUID();
        UUID studentUserId = UUID.randomUUID();
        UUID committeeUserId = UUID.randomUUID();
        UUID coachProfileId = UUID.randomUUID();

        jdbc.update("insert into organizations(id, code, name) values (?, ?, ?)",
                organizationId, "slice4-" + compact(organizationId), "Slice 4 test");
        for (UUID userId : java.util.List.of(coachUserId, studentUserId, committeeUserId)) {
            jdbc.update("insert into users(id, display_name) values (?, ?)", userId, "slice4 user");
        }
        jdbc.update("""
                insert into coach_profiles(id, organization_id, user_id, approval_status)
                values (?, ?, ?, 'APPROVED')
                """, coachProfileId, organizationId, coachUserId);

        return new Fixture(organizationId, coachProfileId, studentUserId, committeeUserId);
    }

    private UUID createOpenOffering(Fixture fixture, short minimum, short maximum) {
        UUID offeringId = UUID.randomUUID();
        jdbc.update("""
                insert into course_offerings(
                    id, organization_id, coach_profile_id, title, lesson_type,
                    schedule_type, billing_mode, minimum_participants, maximum_participants,
                    registration_open_at, registration_close_at, status,
                    published_by, published_at, created_by)
                values (?, ?, ?, 'Open Enrollment Test', 'GROUP',
                    'SINGLE', 'FULL_COURSE', ?, ?, now() - interval '1 hour',
                    now() + interval '1 day', 'OPEN', ?, now(), ?)
                """, offeringId, fixture.organizationId(), fixture.coachProfileId(), minimum, maximum,
                fixture.committeeUserId(), fixture.committeeUserId());
        return offeringId;
    }

    private void insertCourse(UUID courseId, Fixture fixture, UUID sourceMatchId,
            UUID sourceOfferingId, String courseNo) {
        jdbc.update("""
                insert into courses(
                    id, organization_id, course_no, source_match_id, source_offering_id,
                    created_by_user_id, course_type, schedule_type, billing_mode,
                    expected_participant_count, guest_participant_count,
                    total_session_count, status)
                values (?, ?, ?, ?, ?, ?, 'GROUP', 'SINGLE', 'FULL_COURSE', 1, 0, 1, 'ACTIVE')
                """, courseId, fixture.organizationId(), courseNo, sourceMatchId, sourceOfferingId,
                fixture.committeeUserId());
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

    private static String compact(UUID id) {
        return id.toString().replace("-", "").substring(0, 20);
    }

    private record Fixture(UUID organizationId, UUID coachProfileId, UUID studentUserId, UUID committeeUserId) {}
}
