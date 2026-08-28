package com.pickleball.booking.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
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
class NotificationProjectionRepositoryIT {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("security.jwt.signing-secret",
                () -> "test-only-signing-secret-with-at-least-thirty-two-characters");
    }

    @Autowired NotificationProjectionRepository repository;
    @Autowired JdbcTemplate jdbc;

    @Test
    void projectsCourseSessionRecipientsTargetsAndDedupeThroughRealPostgres() {
        UUID organizationId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Instant sessionStart = Instant.parse("2030-08-01T02:00:00Z");

        jdbc.update("insert into organizations(id, code, name) values (?, ?, ?)",
                organizationId, "s8-projection-" + compact(organizationId), "S8 Projection Org");
        jdbc.update("insert into users(id, display_name) values (?, 'S8 recipient')", recipientId);
        jdbc.update("""
                insert into courses(
                    id, organization_id, course_no, created_by_user_id, course_type,
                    schedule_type, billing_mode, expected_participant_count,
                    guest_participant_count, total_session_count, status)
                values (?, ?, 'S8-NOTIFY', ?, 'GROUP', 'SINGLE', 'FULL_COURSE', 1, 0, 1, 'ACTIVE')
                """, courseId, organizationId, recipientId);
        jdbc.update("""
                insert into course_sessions(
                    id, organization_id, course_id, sequence_no, scheduled_start_at, scheduled_end_at,
                    expected_participant_count, guest_participant_count, status)
                values (?, ?, ?, 1, ?, ?, 1, 0, 'SCHEDULED')
                """, sessionId, organizationId, courseId,
                Timestamp.from(sessionStart), Timestamp.from(sessionStart.plusSeconds(3600)));
        jdbc.update("""
                insert into session_venue_arrangements(
                    id, organization_id, course_session_id, source_type, venue_name_snapshot,
                    cost_amount, cost_payer_type, status, confirmed_by, confirmed_at)
                values (?, ?, ?, 'COMMITTEE', 'Taipei Test Court', 0, 'NONE', 'CONFIRMED', ?, now())
                """, UUID.randomUUID(), organizationId, sessionId, recipientId);
        jdbc.update("""
                insert into course_contact_assignments(
                    id, organization_id, course_id, user_id, effective_from, assigned_by)
                values (?, ?, ?, ?, now(), ?)
                """, UUID.randomUUID(), organizationId, courseId, recipientId, recipientId);
        jdbc.update("""
                insert into course_memberships(id, organization_id, course_id, user_id, status)
                values (?, ?, ?, ?, 'ACTIVE')
                """, membershipId, organizationId, courseId, recipientId);
        jdbc.update("""
                insert into enrollments(
                    id, organization_id, course_membership_id, course_session_id, user_id, status)
                values (?, ?, ?, ?, ?, 'SCHEDULED')
                """, UUID.randomUUID(), organizationId, membershipId, sessionId, recipientId);
        jdbc.update("""
                insert into notification_targets(
                    id, organization_id, channel, target_type, target_code, external_target_id)
                values (?, ?, 'LINE', 'GROUP', 'COACH_REMINDER_GROUP', 'line-group-test')
                """, targetId, organizationId);

        assertThat(repository.coursePayload(organizationId, courseId))
                .containsEntry("courseNo", "S8-NOTIFY")
                .containsEntry("firstSessionStart", "2030-08-01 10:00")
                .containsEntry("venueName", "Taipei Test Court");
        assertThat(repository.sessionPayload(organizationId, sessionId))
                .containsEntry("courseNo", "S8-NOTIFY")
                .containsEntry("sessionStart", "2030-08-01 10:00")
                .containsEntry("venueName", "Taipei Test Court");
        assertThat(repository.courseRecipients(organizationId, courseId)).containsExactly(recipientId);
        assertThat(repository.sessionRecipients(organizationId, sessionId)).containsExactly(recipientId);
        assertThat(repository.activeTargets("COACH_REMINDER_GROUP"))
                .containsExactly(new NotificationProjectionRepository.NotificationTarget(targetId, organizationId));
        assertThat(repository.scheduledSessions(
                organizationId, sessionStart.minusSeconds(60), sessionStart.plusSeconds(60)))
                .singleElement()
                .satisfies(session -> assertThat(session)
                        .containsEntry("courseNo", "S8-NOTIFY")
                        .containsEntry("start", "2030-08-01 10:00")
                        .containsEntry("venueName", "Taipei Test Court"));

        String userDedupe = "OUTBOX:" + UUID.randomUUID() + ":" + recipientId;
        assertThat(repository.enqueueUser(
                organizationId, recipientId, "COURSE_CONFIRMED", "Course", courseId,
                Map.of("courseNo", "S8-NOTIFY"), userDedupe)).isTrue();
        assertThat(repository.enqueueUser(
                organizationId, recipientId, "COURSE_CONFIRMED", "Course", courseId,
                Map.of("courseNo", "S8-NOTIFY"), userDedupe)).isFalse();

        String targetDedupe = "COACH_DAILY_REMINDER:" + targetId + ":2030-08-01";
        assertThat(repository.enqueueTarget(
                organizationId, targetId, "COACH_DAILY_REMINDER", "NotificationTarget", targetId,
                Map.of("reminderDate", "2030-08-01"), targetDedupe)).isTrue();

        assertThat(jdbc.queryForObject(
                "select count(*) from notifications where organization_id=?", Integer.class, organizationId))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "select recipient_user_id from notifications where dedupe_key=?", UUID.class, userDedupe))
                .isEqualTo(recipientId);
        assertThat(jdbc.queryForObject(
                "select notification_target_id from notifications where dedupe_key=?", UUID.class, targetDedupe))
                .isEqualTo(targetId);
    }

    private static String compact(UUID id) {
        return id.toString().replace("-", "").substring(0, 12);
    }
}
