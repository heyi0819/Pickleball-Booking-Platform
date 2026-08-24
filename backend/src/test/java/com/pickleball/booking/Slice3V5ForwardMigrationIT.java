package com.pickleball.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class Slice3V5ForwardMigrationIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Test
    void v4RowsWithMultipleAssistantsRemainAlignedAfterCurrentMigrations() {
        String schema = "slice3_v5_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create schema " + schema);

        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("4"))
                .load()
                .migrate();

        UUID organizationId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        UUID lessonRequestId = UUID.randomUUID();
        UUID courseMatchId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();

        jdbc.update("insert into " + schema + ".organizations(id, code, name) values (?, ?, ?)",
                organizationId, "v5-forward", "V5 forward test");
        jdbc.update("insert into " + schema + ".users(id, display_name) values (?, ?), (?, ?)",
                requesterId, "requester", reviewerId, "reviewer");
        jdbc.update("""
                insert into %s.venues(id, organization_id, name, address, status)
                values (?, ?, 'Canonical Venue', 'Taipei', 'ACTIVE')
                """.formatted(schema), venueId, organizationId);
        jdbc.update("""
                insert into %s.lesson_requests(
                    id, organization_id, requester_user_id, lesson_type, schedule_type, billing_mode,
                    participant_count, guest_participant_count, requested_session_count, status,
                    submitted_at, reviewed_by, reviewed_at, review_note)
                values (?, ?, ?, 'PRIVATE', 'SINGLE', 'FULL_COURSE', 3, 0, 1, 'APPROVED',
                    now(), ?, now(), 'approved')
                """.formatted(schema), lessonRequestId, organizationId, requesterId, reviewerId);
        jdbc.update("""
                insert into %s.course_matches(
                    id, organization_id, lesson_request_id, status,
                    participant_count_snapshot, minimum_participants_snapshot, maximum_participants_snapshot)
                values (?, ?, ?, 'DRAFT', 3, 2, 8)
                """.formatted(schema), courseMatchId, organizationId, lessonRequestId);
        jdbc.update("""
                insert into %s.course_match_sessions(
                    id, course_match_id, sequence_no, start_at, end_at, venue_id,
                    venue_name_snapshot, venue_address_snapshot)
                values (?, ?, 1, now() + interval '2 hours', now() + interval '3 hours', ?,
                    'Canonical Venue', 'Taipei')
                """.formatted(schema), sessionId, courseMatchId, venueId);

        List<UUID> coachProfiles = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        for (int i = 0; i < coachProfiles.size(); i++) {
            UUID coachUserId = UUID.randomUUID();
            jdbc.update("insert into " + schema + ".users(id, display_name) values (?, ?)", coachUserId, "coach-" + i);
            jdbc.update("""
                    insert into %s.coach_profiles(id, organization_id, user_id, approval_status)
                    values (?, ?, ?, 'APPROVED')
                    """.formatted(schema), coachProfiles.get(i), organizationId, coachUserId);
        }

        insertV4Coach(jdbc, schema, sessionId, coachProfiles.get(0), "PRIMARY", "INVITED", reviewerId);
        insertV4Coach(jdbc, schema, sessionId, coachProfiles.get(1), "ASSISTANT", "INVITED", reviewerId);
        insertV4Coach(jdbc, schema, sessionId, coachProfiles.get(2), "ASSISTANT", "ACCEPTED", reviewerId);
        insertV4Coach(jdbc, schema, sessionId, coachProfiles.get(3), "ASSISTANT", "REPLACED", reviewerId);

        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertThat(jdbc.queryForObject(
                "select max(version) from " + schema + ".flyway_schema_history where success = true",
                String.class)).isEqualTo("7");
        assertThat(jdbc.queryForObject(
                "select created_by from " + schema + ".course_matches where id = ?",
                UUID.class, courseMatchId)).isEqualTo(reviewerId);
        assertThat(jdbc.queryForObject(
                "select participant_count from " + schema + ".course_matches where id = ?",
                Short.class, courseMatchId)).isEqualTo((short) 3);
        assertThat(jdbc.queryForObject(
                "select venue_snapshot_type from " + schema + ".course_match_sessions where id = ?",
                String.class, sessionId)).isEqualTo("VENUE");
        assertThat(jdbc.queryForObject(
                "select length(venue_fingerprint) from " + schema + ".course_match_sessions where id = ?",
                Integer.class, sessionId)).isEqualTo(64);
        assertThat(jdbc.queryForObject(
                "select to_regclass(?) is not null", Boolean.class, schema + ".pricing_rules")).isTrue();
        assertThat(jdbc.queryForObject(
                "select to_regclass(?) is not null", Boolean.class, schema + ".schedule_reservations")).isTrue();

        List<Integer> activeOrders = jdbc.queryForList("""
                select assignment_order
                from %s.course_match_session_coaches
                where course_match_session_id = ? and status in ('INVITED','ACCEPTED')
                order by assignment_order
                """.formatted(schema), Integer.class, sessionId);
        assertThat(activeOrders).containsExactly(1, 2, 3);
        assertThat(jdbc.queryForObject("""
                select status from %s.course_match_session_coaches
                where course_match_session_id = ? and coach_profile_id = ?
                """.formatted(schema), String.class, sessionId, coachProfiles.get(3))).isEqualTo("CANCELLED");
    }

    private void insertV4Coach(JdbcTemplate jdbc, String schema, UUID sessionId, UUID coachProfileId,
            String role, String status, UUID invitedBy) {
        jdbc.update("""
                insert into %s.course_match_session_coaches(
                    id, course_match_session_id, coach_profile_id, role_type, status,
                    invited_at, responded_at, invited_by)
                values (?, ?, ?, ?, ?, now(),
                    case when ? <> 'INVITED' then now() else null end, ?)
                """.formatted(schema), UUID.randomUUID(), sessionId, coachProfileId, role, status, status, invitedBy);
    }
}
