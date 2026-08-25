package com.pickleball.booking.course.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.pickleball.booking.coach.infrastructure.CoachProfileEntity;
import com.pickleball.booking.coach.infrastructure.CoachProfileRepository;
import com.pickleball.booking.course.application.CourseOperationsApplicationService.AttendanceDecision;
import com.pickleball.booking.course.application.CourseOperationsApplicationService.ReviewDecision;
import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.identity.domain.RoleCode;
import com.pickleball.booking.identity.infrastructure.PlatformUserEntity;
import com.pickleball.booking.identity.infrastructure.PlatformUserRepository;
import com.pickleball.booking.identity.infrastructure.RoleAssignmentEntity;
import com.pickleball.booking.identity.infrastructure.RoleAssignmentRepository;
import com.pickleball.booking.offering.application.CourseOfferingApplicationService;
import com.pickleball.booking.offering.application.CourseOfferingApplicationService.DraftCommand;
import com.pickleball.booking.offering.application.CourseOfferingApplicationService.PriceCommand;
import com.pickleball.booking.offering.application.CourseOfferingApplicationService.SessionCommand;
import com.pickleball.booking.offering.domain.OfferingBillingMode;
import com.pickleball.booking.offering.domain.OfferingScheduleType;
import com.pickleball.booking.organization.infrastructure.OrganizationEntity;
import com.pickleball.booking.organization.infrastructure.OrganizationRepository;
import com.pickleball.booking.shared.application.BusinessException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
@Testcontainers
class CourseOperationsApplicationServiceIT {
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

    @Autowired CourseOperationsApplicationService service;
    @Autowired CourseOfferingApplicationService offeringService;
    @Autowired OrganizationRepository organizations;
    @Autowired PlatformUserRepository users;
    @Autowired RoleAssignmentRepository roles;
    @Autowired CoachProfileRepository coachProfiles;
    @Autowired JdbcTemplate jdbc;

    @Test
    void studentCancellationCreatesImmutableHistoryAndReleasesOnlyParticipantReservation() {
        FormalFixture fixture = formalFixture(1);
        UUID sessionId = fixture.sessionIds().getFirst();
        UUID enrollmentId = enrollmentId(sessionId, fixture.studentId());

        var result = service.cancelEnrollment(
                principal(fixture.studentId()), enrollmentId, null, "cancel-" + UUID.randomUUID());

        assertThat(result.enrollment().status().name()).isEqualTo("CANCELLED");
        assertThat(result.courseSessionStatus().name()).isEqualTo("SCHEDULED");
        assertThat(jdbc.queryForObject(
                "select count(*) from member_cancellation_records where enrollment_id=?",
                Integer.class, enrollmentId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select status from schedule_reservations
                 where course_session_id=? and user_id=? and reservation_role='PARTICIPANT'
                """, String.class, sessionId, fixture.studentId())).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("""
                select status from schedule_reservations
                 where course_session_id=? and user_id=? and reservation_role='COACH'
                """, String.class, sessionId, fixture.coachUserId())).isEqualTo("CONFIRMED");
    }

    @Test
    void coachCancellationNeedsCommitteeApprovalAndApprovalReleasesSessionReservations() {
        FormalFixture fixture = formalFixture(1);
        UUID sessionId = fixture.sessionIds().getFirst();

        var request = service.requestCoachCancellation(
                principal(fixture.coachUserId()), sessionId, "Coach unavailable", "coach-cancel-" + UUID.randomUUID());
        assertThat(request.status().name()).isEqualTo("PENDING_REVIEW");
        assertThat(sessionStatus(sessionId)).isEqualTo("CANCEL_PENDING");
        assertThat(activeReservationCount(sessionId)).isEqualTo(2);

        var result = service.reviewCoachCancellation(
                principal(fixture.committeeId()), request.id(), ReviewDecision.APPROVE,
                "Approved by committee", "coach-review-" + UUID.randomUUID());

        assertThat(result.request().status().name()).isEqualTo("APPROVED");
        assertThat(result.session().status().name()).isEqualTo("CANCELLED");
        assertThat(result.session().cancellationSource().name()).isEqualTo("COACH");
        assertThat(activeReservationCount(sessionId)).isZero();
    }

    @Test
    void pendingRescheduleDoesNotChangeSessionOrReservations() {
        FormalFixture fixture = formalFixture(1);
        UUID sessionId = fixture.sessionIds().getFirst();
        Instant originalStart = sessionStart(sessionId);
        Instant originalReservationStart = reservationStart(sessionId, fixture.studentId());
        Instant proposedStart = originalStart.plusSeconds(7200);

        var request = service.requestReschedule(
                principal(fixture.studentId()), sessionId,
                proposedStart, proposedStart.plusSeconds(3600), "Need another time",
                "reschedule-request-" + UUID.randomUUID(), "trace-" + UUID.randomUUID());

        assertThat(request.status().name()).isEqualTo("PENDING");
        assertThat(sessionStart(sessionId)).isEqualTo(originalStart);
        assertThat(reservationStart(sessionId, fixture.studentId())).isEqualTo(originalReservationStart);
    }

    @Test
    void approvalConflictRollsBackSessionReservationAndRequestDecision() {
        FormalFixture fixture = formalFixture(2);
        UUID firstSession = fixture.sessionIds().get(0);
        UUID secondSession = fixture.sessionIds().get(1);
        Instant originalStart = sessionStart(firstSession);
        Instant conflictingStart = sessionStart(secondSession);

        var request = service.requestReschedule(
                principal(fixture.coachUserId()), firstSession,
                conflictingStart, conflictingStart.plusSeconds(3600), "Conflict test",
                "conflict-request-" + UUID.randomUUID(), "trace-" + UUID.randomUUID());

        Throwable failure = catchThrowable(() -> service.reviewReschedule(
                principal(fixture.committeeId()), request.id(), ReviewDecision.APPROVE, "Approve",
                "conflict-review-" + UUID.randomUUID(), "trace-" + UUID.randomUUID()));
        assertBusinessCode(failure, "SCHEDULE_CONFLICT");

        assertThat(sessionStart(firstSession)).isEqualTo(originalStart);
        assertThat(jdbc.queryForObject(
                "select status from session_change_requests where id=?", String.class, request.id()))
                .isEqualTo("PENDING");
        assertThat(reservationStart(firstSession, fixture.studentId())).isEqualTo(originalStart);
    }

    @Test
    void directRescheduleChangesOnlyTargetRecurringSessionAndStoresApprovedHistoryAndAuditSnapshots() {
        FormalFixture fixture = formalFixture(2);
        UUID firstSession = fixture.sessionIds().get(0);
        UUID secondSession = fixture.sessionIds().get(1);
        Instant firstOriginal = sessionStart(firstSession);
        Instant secondOriginal = sessionStart(secondSession);
        Instant newStart = firstOriginal.plusSeconds(3600);
        String key = "direct-" + UUID.randomUUID();
        String trace = "trace-" + UUID.randomUUID();

        var result = service.directReschedule(
                principal(fixture.committeeId()), firstSession,
                newStart, newStart.plusSeconds(3600), "Venue timing coordinated", key, trace);
        var replay = service.directReschedule(
                principal(fixture.committeeId()), firstSession,
                newStart, newStart.plusSeconds(3600), "Venue timing coordinated", key, trace);

        assertThat(result.request().status().name()).isEqualTo("APPROVED");
        assertThat(replay.request().id()).isEqualTo(result.request().id());
        assertThat(sessionStart(firstSession)).isEqualTo(newStart);
        assertThat(sessionStart(secondSession)).isEqualTo(secondOriginal);
        assertThat(reservationStart(firstSession, fixture.studentId())).isEqualTo(newStart);
        assertThat(jdbc.queryForObject("""
                select count(*) from session_change_requests
                 where id=? and request_type='RESCHEDULE' and status='APPROVED'
                """, Integer.class, result.request().id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from audit_logs
                 where entity_type='CourseSession' and entity_id=?
                   and action='SESSION_DIRECT_RESCHEDULED'
                   and before_data is not null and after_data is not null and request_id=?
                """, Integer.class, firstSession, trace)).isEqualTo(1);
        assertThat(firstOriginal).isNotEqualTo(newStart);
    }

    @Test
    void assignedCoachCanMarkAttendanceOnlyAfterSessionStarts() {
        FormalFixture fixture = formalFixture(1);
        UUID sessionId = fixture.sessionIds().getFirst();
        UUID enrollmentId = enrollmentId(sessionId, fixture.studentId());
        Instant pastStart = Instant.now().minusSeconds(7200);
        Instant pastEnd = Instant.now().minusSeconds(3600);
        jdbc.update("""
                update course_sessions set scheduled_start_at=?, scheduled_end_at=? where id=?
                """, Timestamp.from(pastStart), Timestamp.from(pastEnd), sessionId);
        jdbc.update("""
                update schedule_reservations
                   set reserved_period=tstzrange(?::timestamptz, ?::timestamptz, '[)')
                 where course_session_id=?
                """, Timestamp.from(pastStart), Timestamp.from(pastEnd), sessionId);

        var enrollment = service.markAttendance(
                principal(fixture.coachUserId()), enrollmentId, AttendanceDecision.ATTENDED,
                "attendance-" + UUID.randomUUID());

        assertThat(enrollment.status().name()).isEqualTo("ATTENDED");
        assertThat(jdbc.queryForObject("select status from enrollments where id=?", String.class, enrollmentId))
                .isEqualTo("ATTENDED");
    }

    private FormalFixture formalFixture(int sessionCount) {
        OrganizationEntity organization = organizations.saveAndFlush(
                new OrganizationEntity("COURSE-OPS-" + UUID.randomUUID(), "Course Operations Test"));
        PlatformUserEntity committee = users.saveAndFlush(new PlatformUserEntity(UUID.randomUUID(), "Committee"));
        PlatformUserEntity coachUser = users.saveAndFlush(new PlatformUserEntity(UUID.randomUUID(), "Coach"));
        PlatformUserEntity student = users.saveAndFlush(new PlatformUserEntity(UUID.randomUUID(), "Student"));
        roles.saveAndFlush(new RoleAssignmentEntity(committee, organization, RoleCode.COMMITTEE));
        roles.saveAndFlush(new RoleAssignmentEntity(coachUser, organization, RoleCode.COACH));
        roles.saveAndFlush(new RoleAssignmentEntity(student, organization, RoleCode.STUDENT));

        CoachProfileEntity coachProfile = coachProfiles.saveAndFlush(
                new CoachProfileEntity(organization.getId(), coachUser.getId(), "INTERMEDIATE", null));
        coachProfile.approve(committee.getId());
        coachProfiles.saveAndFlush(coachProfile);

        UUID venueId = UUID.randomUUID();
        jdbc.update("""
                insert into venues(id, organization_id, name, address, default_cost_amount, status)
                values (?, ?, 'Course Ops Court', 'Taipei', 0.00, 'ACTIVE')
                """, venueId, organization.getId());

        Instant now = Instant.now();
        List<SessionCommand> sessionCommands = new ArrayList<>();
        for (int index = 0; index < sessionCount; index++) {
            Instant start = now.plusSeconds(10800L + index * 10800L);
            sessionCommands.add(new SessionCommand(
                    index + 1, start, start.plusSeconds(3600), venueId, "Course Ops Court", "Taipei"));
        }

        var offering = offeringService.createDraft(
                principal(committee.getId()), organization.getId(),
                new DraftCommand(
                        coachProfile.getId(), "Course Operations Fixture", "Formal course fixture",
                        sessionCount == 1 ? OfferingScheduleType.SINGLE : OfferingScheduleType.RECURRING,
                        OfferingBillingMode.FULL_COURSE, "INTERMEDIATE",
                        1, 4, now.minusSeconds(600), now.plusSeconds(3600), sessionCommands));
        var price = offeringService.createPriceDraft(
                principal(committee.getId()), offering.id(),
                new PriceCommand("TWD", new BigDecimal("1200.00"), Map.of("source", "course-operations-it")));
        offeringService.confirmPrice(principal(committee.getId()), offering.id(), price.id());
        offeringService.publish(principal(committee.getId()), offering.id());
        offeringService.register(
                principal(student.getId()), offering.id(), "fixture-register-" + UUID.randomUUID());
        offeringService.close(principal(committee.getId()), offering.id());
        var confirmation = offeringService.confirm(
                principal(committee.getId()), offering.id(), "fixture-confirm-" + UUID.randomUUID());

        return new FormalFixture(
                organization.getId(), committee.getId(), coachUser.getId(), student.getId(),
                confirmation.courseId(), confirmation.sessionIds());
    }

    private UUID enrollmentId(UUID sessionId, UUID studentId) {
        return jdbc.queryForObject(
                "select id from enrollments where course_session_id=? and user_id=?",
                UUID.class, sessionId, studentId);
    }

    private String sessionStatus(UUID sessionId) {
        return jdbc.queryForObject("select status from course_sessions where id=?", String.class, sessionId);
    }

    private int activeReservationCount(UUID sessionId) {
        return jdbc.queryForObject("""
                select count(*) from schedule_reservations
                 where course_session_id=? and status in ('HELD','CONFIRMED')
                """, Integer.class, sessionId);
    }

    private Instant sessionStart(UUID sessionId) {
        return jdbc.queryForObject(
                "select scheduled_start_at from course_sessions where id=?",
                Timestamp.class, sessionId).toInstant();
    }

    private Instant reservationStart(UUID sessionId, UUID userId) {
        return jdbc.queryForObject("""
                select lower(reserved_period) from schedule_reservations
                 where course_session_id=? and user_id=?
                """, Timestamp.class, sessionId, userId).toInstant();
    }

    private void assertBusinessCode(Throwable failure, String expectedCode) {
        assertThat(failure).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) failure).code()).isEqualTo(expectedCode);
    }

    private AuthenticatedPrincipal principal(UUID userId) {
        return new AuthenticatedPrincipal(userId);
    }

    private record FormalFixture(
            UUID organizationId,
            UUID committeeId,
            UUID coachUserId,
            UUID studentId,
            UUID courseId,
            List<UUID> sessionIds) { }
}
