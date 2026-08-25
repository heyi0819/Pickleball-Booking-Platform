package com.pickleball.booking.course.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.pickleball.booking.coach.infrastructure.CoachProfileEntity;
import com.pickleball.booking.coach.infrastructure.CoachProfileRepository;
import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.identity.application.PlatformTokenService;
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
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class Slice5HttpEndToEndIT {
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

    @LocalServerPort int port;
    @Autowired ObjectMapper objectMapper;
    @Autowired PlatformTokenService tokens;
    @Autowired CourseOfferingApplicationService offerings;
    @Autowired OrganizationRepository organizations;
    @Autowired PlatformUserRepository users;
    @Autowired RoleAssignmentRepository roles;
    @Autowired CoachProfileRepository coachProfiles;
    @Autowired JdbcTemplate jdbc;

    @Test
    void roleScopedQueriesAndStudentCancellationWorkThroughHttp() throws Exception {
        Fixture fixture = formalFixture();
        String studentToken = token(fixture.studentId());
        String committeeToken = token(fixture.committeeId());
        String outsiderToken = token(fixture.outsiderId());

        JsonNode list = data(request("GET", "/api/v1/courses", studentToken, null, null, 200));
        assertThat(list.get("total").asLong()).isEqualTo(1);
        assertThat(list.get("items").get(0).get("id").asText()).isEqualTo(fixture.courseId().toString());

        JsonNode committeeFiltered = data(request(
                "GET", "/api/v1/courses?studentUserId=" + fixture.studentId(),
                committeeToken, null, null, 200));
        assertThat(committeeFiltered.get("total").asLong()).isEqualTo(1);

        JsonNode committeeOrganizationFiltered = data(request(
                "GET", "/api/v1/courses?organizationId=" + fixture.organizationId(),
                committeeToken, null, null, 200));
        assertThat(committeeOrganizationFiltered.get("total").asLong()).isEqualTo(1);

        JsonNode studentUserFilterForbidden = error(request(
                "GET", "/api/v1/courses?studentUserId=" + fixture.studentId(),
                studentToken, null, null, 403));
        assertThat(studentUserFilterForbidden.get("code").asText()).isEqualTo("AUTH_FORBIDDEN");

        JsonNode studentOrganizationFilterForbidden = error(request(
                "GET", "/api/v1/courses?organizationId=" + fixture.organizationId(),
                studentToken, null, null, 403));
        assertThat(studentOrganizationFilterForbidden.get("code").asText()).isEqualTo("AUTH_FORBIDDEN");

        JsonNode committeeOtherOrganizationDenied = error(request(
                "GET", "/api/v1/courses?organizationId=" + UUID.randomUUID(),
                committeeToken, null, null, 403));
        assertThat(committeeOtherOrganizationDenied.get("code").asText()).isEqualTo("ORG_SCOPE_DENIED");

        JsonNode course = data(request(
                "GET", "/api/v1/courses/" + fixture.courseId(), studentToken, null, null, 200));
        assertThat(course.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(course.get("sourceOfferingId").asText()).isEqualTo(fixture.offeringId().toString());

        JsonNode sessions = data(request(
                "GET", "/api/v1/courses/" + fixture.courseId() + "/sessions",
                studentToken, null, null, 200));
        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).get("ownEnrollmentId").asText()).isEqualTo(fixture.enrollmentId().toString());
        assertThat(sessions.get(0).get("ownEnrollmentStatus").asText()).isEqualTo("SCHEDULED");
        assertThat(sessions.get(0).get("coachProfileId").asText()).isEqualTo(fixture.coachProfileId().toString());
        assertThat(sessions.get(0).get("venueName").asText()).isEqualTo("Slice 5 HTTP Court");

        JsonNode singleSession = data(request(
                "GET", "/api/v1/course-sessions/" + fixture.sessionId(),
                studentToken, null, null, 200));
        assertThat(singleSession.get("courseId").asText()).isEqualTo(fixture.courseId().toString());

        JsonNode hidden = error(request(
                "GET", "/api/v1/courses/" + fixture.courseId(), outsiderToken, null, null, 404));
        assertThat(hidden.get("code").asText()).isEqualTo("RESOURCE_NOT_FOUND");

        JsonNode cancelled = data(request(
                "POST", "/api/v1/session-enrollments/" + fixture.enrollmentId() + "/cancellation",
                studentToken, null, "{\"reason\":null}", 200));
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.get("courseSessionStatus").asText()).isEqualTo("SCHEDULED");
        assertThat(jdbc.queryForObject(
                "select count(*) from member_cancellation_records where enrollment_id=?",
                Integer.class, fixture.enrollmentId())).isEqualTo(1);

        JsonNode repeat = error(request(
                "POST", "/api/v1/session-enrollments/" + fixture.enrollmentId() + "/cancellation",
                studentToken, null, "{\"reason\":null}", 409));
        assertThat(repeat.get("code").asText()).isEqualTo("STATE_TRANSITION_INVALID");
    }

    @Test
    void coachCancellationUsesPendingReviewAndCommitteeDecisionThroughHttp() throws Exception {
        Fixture fixture = formalFixture();
        String coachToken = token(fixture.coachUserId());
        String committeeToken = token(fixture.committeeId());
        String studentToken = token(fixture.studentId());

        JsonNode requested = data(request(
                "POST", "/api/v1/course-sessions/" + fixture.sessionId() + "/coach-cancellation-requests",
                coachToken, null, "{\"reason\":\"Coach unavailable\"}", 201));
        UUID requestId = UUID.fromString(requested.get("requestId").asText());
        assertThat(requested.get("status").asText()).isEqualTo("PENDING_REVIEW");
        assertThat(jdbc.queryForObject(
                "select status from course_sessions where id=?", String.class, fixture.sessionId()))
                .isEqualTo("CANCEL_PENDING");

        JsonNode queue = data(request(
                "GET", "/api/v1/coach-cancellation-requests?organizationId=" + fixture.organizationId(),
                committeeToken, null, null, 200));
        assertThat(queue).hasSize(1);
        assertThat(queue.get(0).get("requestId").asText()).isEqualTo(requestId.toString());
        assertThat(queue.get(0).get("courseNo").asText()).isNotBlank();
        assertThat(queue.get(0).get("requesterDisplayName").asText()).isEqualTo("Coach S5");

        JsonNode queueForbidden = error(request(
                "GET", "/api/v1/coach-cancellation-requests?organizationId=" + fixture.organizationId(),
                studentToken, null, null, 403));
        assertThat(queueForbidden.get("code").asText()).isEqualTo("AUTH_FORBIDDEN");

        JsonNode queueOtherOrg = error(request(
                "GET", "/api/v1/coach-cancellation-requests?organizationId=" + UUID.randomUUID(),
                committeeToken, null, null, 403));
        assertThat(queueOtherOrg.get("code").asText()).isEqualTo("ORG_SCOPE_DENIED");

        JsonNode forbidden = error(request(
                "POST", "/api/v1/coach-cancellation-requests/" + requestId + "/review",
                studentToken, null, "{\"decision\":\"APPROVE\",\"reason\":\"No\"}", 403));
        assertThat(forbidden.get("code").asText()).isEqualTo("AUTH_FORBIDDEN");

        JsonNode approved = data(request(
                "POST", "/api/v1/coach-cancellation-requests/" + requestId + "/review",
                committeeToken, null, "{\"decision\":\"APPROVE\",\"reason\":\"Approved\"}", 200));
        assertThat(approved.get("status").asText()).isEqualTo("APPROVED");
        assertThat(approved.get("sessionStatus").asText()).isEqualTo("CANCELLED");
        JsonNode queueAfterReview = data(request(
                "GET", "/api/v1/coach-cancellation-requests?organizationId=" + fixture.organizationId(),
                committeeToken, null, null, 200));
        assertThat(queueAfterReview).isEmpty();
        assertThat(jdbc.queryForObject("""
                select count(*) from schedule_reservations
                 where course_session_id=? and status in ('HELD','CONFIRMED')
                """, Integer.class, fixture.sessionId())).isZero();
    }

    @Test
    void rescheduleRequestReviewReplayAndDirectRescheduleWorkThroughHttp() throws Exception {
        Fixture fixture = formalFixture();
        String studentToken = token(fixture.studentId());
        String committeeToken = token(fixture.committeeId());
        Instant original = sessionStart(fixture.sessionId());
        Instant proposed = original.plusSeconds(7200);
        String requestKey = "http-reschedule-" + UUID.randomUUID();
        String body = rescheduleRequestBody(proposed, proposed.plusSeconds(3600), "Student request");

        JsonNode missingKey = error(request(
                "POST", "/api/v1/course-sessions/" + fixture.sessionId() + "/change-requests",
                studentToken, null, body, 400));
        assertThat(missingKey.get("code").asText()).isEqualTo("VALIDATION_FAILED");

        JsonNode created = data(request(
                "POST", "/api/v1/course-sessions/" + fixture.sessionId() + "/change-requests",
                studentToken, requestKey, body, 201));
        JsonNode replay = data(request(
                "POST", "/api/v1/course-sessions/" + fixture.sessionId() + "/change-requests",
                studentToken, requestKey, body, 201));
        UUID changeRequestId = UUID.fromString(created.get("changeRequestId").asText());
        assertThat(replay.get("changeRequestId").asText()).isEqualTo(changeRequestId.toString());
        assertThat(created.get("status").asText()).isEqualTo("PENDING");
        assertThat(sessionStart(fixture.sessionId())).isEqualTo(original);

        JsonNode changeQueue = data(request(
                "GET", "/api/v1/session-change-requests?organizationId=" + fixture.organizationId(),
                committeeToken, null, null, 200));
        assertThat(changeQueue).hasSize(1);
        assertThat(changeQueue.get(0).get("requestId").asText()).isEqualTo(changeRequestId.toString());
        assertThat(changeQueue.get(0).get("scheduledStartAt").asText()).isEqualTo(original.toString());
        assertThat(changeQueue.get(0).get("proposedStartAt").asText()).isEqualTo(proposed.toString());
        assertThat(changeQueue.get(0).get("requesterDisplayName").asText()).isEqualTo("Student S5");

        JsonNode conflictKey = error(request(
                "POST", "/api/v1/course-sessions/" + fixture.sessionId() + "/change-requests",
                studentToken, requestKey,
                rescheduleRequestBody(proposed.plusSeconds(600), proposed.plusSeconds(4200), "Changed"), 409));
        assertThat(conflictKey.get("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");

        JsonNode reviewed = data(request(
                "POST", "/api/v1/session-change-requests/" + changeRequestId + "/review",
                committeeToken, "review-" + UUID.randomUUID(),
                "{\"decision\":\"APPROVE\",\"reason\":\"Approved\"}", 200));
        assertThat(reviewed.get("status").asText()).isEqualTo("APPROVED");
        assertThat(reviewed.get("scheduledStartAt").asText()).isEqualTo(proposed.toString());
        assertThat(sessionStart(fixture.sessionId())).isEqualTo(proposed);
        JsonNode changeQueueAfterReview = data(request(
                "GET", "/api/v1/session-change-requests?organizationId=" + fixture.organizationId(),
                committeeToken, null, null, 200));
        assertThat(changeQueueAfterReview).isEmpty();

        Instant direct = proposed.plusSeconds(7200);
        JsonNode directResult = data(request(
                "POST", "/api/v1/course-sessions/" + fixture.sessionId() + "/reschedule",
                committeeToken, "direct-" + UUID.randomUUID(),
                directRescheduleBody(direct, direct.plusSeconds(3600), "Committee coordination"), 200));
        assertThat(directResult.get("status").asText()).isEqualTo("APPROVED");
        assertThat(directResult.get("scheduledStartAt").asText()).isEqualTo(direct.toString());
        assertThat(jdbc.queryForObject("""
                select count(*) from session_change_requests
                 where course_session_id=? and request_type='RESCHEDULE' and status='APPROVED'
                """, Integer.class, fixture.sessionId())).isEqualTo(2);
    }

    @Test
    void startedSessionRescheduleReturnsCanonical422() throws Exception {
        Fixture fixture = formalFixture();
        String studentToken = token(fixture.studentId());
        Instant pastStart = Instant.now().minusSeconds(7200);
        Instant pastEnd = Instant.now().minusSeconds(3600);
        jdbc.update("update course_sessions set scheduled_start_at=?, scheduled_end_at=? where id=?",
                Timestamp.from(pastStart), Timestamp.from(pastEnd), fixture.sessionId());
        jdbc.update("""
                update schedule_reservations
                   set reserved_period=tstzrange(?::timestamptz, ?::timestamptz, '[)')
                 where course_session_id=?
                """, Timestamp.from(pastStart), Timestamp.from(pastEnd), fixture.sessionId());

        Instant future = Instant.now().plusSeconds(7200);
        JsonNode response = error(request(
                "POST", "/api/v1/course-sessions/" + fixture.sessionId() + "/change-requests",
                studentToken, "started-" + UUID.randomUUID(),
                rescheduleRequestBody(future, future.plusSeconds(3600), "Too late"), 422));
        assertThat(response.get("code").asText()).isEqualTo("SESSION_ALREADY_STARTED");
        assertThat(jdbc.queryForObject("""
                select count(*) from session_change_requests where course_session_id=?
                """, Integer.class, fixture.sessionId())).isZero();
    }

    private Fixture formalFixture() {
        OrganizationEntity organization = organizations.saveAndFlush(
                new OrganizationEntity("HTTP-S5-" + UUID.randomUUID(), "Slice 5 HTTP Acceptance"));
        PlatformUserEntity committee = users.saveAndFlush(new PlatformUserEntity(UUID.randomUUID(), "Committee S5"));
        PlatformUserEntity coachUser = users.saveAndFlush(new PlatformUserEntity(UUID.randomUUID(), "Coach S5"));
        PlatformUserEntity student = users.saveAndFlush(new PlatformUserEntity(UUID.randomUUID(), "Student S5"));
        roles.saveAndFlush(new RoleAssignmentEntity(committee, organization, RoleCode.COMMITTEE));
        roles.saveAndFlush(new RoleAssignmentEntity(coachUser, organization, RoleCode.COACH));
        roles.saveAndFlush(new RoleAssignmentEntity(student, organization, RoleCode.STUDENT));

        OrganizationEntity outsiderOrganization = organizations.saveAndFlush(
                new OrganizationEntity("HTTP-S5-X-" + UUID.randomUUID(), "Slice 5 Outsider"));
        PlatformUserEntity outsider = users.saveAndFlush(new PlatformUserEntity(UUID.randomUUID(), "Outsider S5"));
        roles.saveAndFlush(new RoleAssignmentEntity(outsider, outsiderOrganization, RoleCode.STUDENT));

        CoachProfileEntity coachProfile = coachProfiles.saveAndFlush(
                new CoachProfileEntity(organization.getId(), coachUser.getId(), "INTERMEDIATE", null));
        coachProfile.approve(committee.getId());
        coachProfile = coachProfiles.saveAndFlush(coachProfile);

        UUID venueId = UUID.randomUUID();
        jdbc.update("""
                insert into venues(id, organization_id, name, address, default_cost_amount, status)
                values (?, ?, 'Slice 5 HTTP Court', 'Taipei', 0.00, 'ACTIVE')
                """, venueId, organization.getId());

        Instant now = Instant.now();
        Instant sessionStart = now.plusSeconds(10800);
        var offering = offerings.createDraft(
                principal(committee.getId()), organization.getId(),
                new DraftCommand(
                        coachProfile.getId(), "Slice 5 Formal Course", "HTTP course operations fixture",
                        OfferingScheduleType.SINGLE, OfferingBillingMode.FULL_COURSE, "INTERMEDIATE",
                        1, 4, now.minusSeconds(600), now.plusSeconds(3600),
                        List.of(new SessionCommand(
                                1, sessionStart, sessionStart.plusSeconds(3600), venueId,
                                "Slice 5 HTTP Court", "Taipei"))));
        var price = offerings.createPriceDraft(
                principal(committee.getId()), offering.id(),
                new PriceCommand("TWD", new BigDecimal("1200.00"), Map.of("source", "slice5-http-it")));
        offerings.confirmPrice(principal(committee.getId()), offering.id(), price.id());
        offerings.publish(principal(committee.getId()), offering.id());
        offerings.register(principal(student.getId()), offering.id(), "fixture-register-" + UUID.randomUUID());
        offerings.close(principal(committee.getId()), offering.id());
        var confirmation = offerings.confirm(
                principal(committee.getId()), offering.id(), "fixture-confirm-" + UUID.randomUUID());

        UUID sessionId = confirmation.sessionIds().getFirst();
        UUID enrollmentId = jdbc.queryForObject(
                "select id from enrollments where course_session_id=? and user_id=?",
                UUID.class, sessionId, student.getId());
        return new Fixture(
                organization.getId(), committee.getId(), coachUser.getId(), student.getId(), outsider.getId(),
                coachProfile.getId(), offering.id(), confirmation.courseId(), sessionId, enrollmentId);
    }

    private String rescheduleRequestBody(Instant startAt, Instant endAt, String reason) {
        return """
                {"requestType":"RESCHEDULE","proposedStartAt":"%s","proposedEndAt":"%s","reason":"%s"}
                """.formatted(startAt, endAt, reason);
    }

    private String directRescheduleBody(Instant startAt, Instant endAt, String reason) {
        return """
                {"startAt":"%s","endAt":"%s","reason":"%s"}
                """.formatted(startAt, endAt, reason);
    }

    private Instant sessionStart(UUID sessionId) {
        return jdbc.queryForObject(
                "select scheduled_start_at from course_sessions where id=?",
                Timestamp.class, sessionId).toInstant();
    }

    private String token(UUID userId) {
        return tokens.issue(userId).value();
    }

    private JsonNode data(HttpResponse<String> response) throws Exception {
        JsonNode envelope = objectMapper.readTree(response.body());
        assertThat(envelope.has("data"))
                .withFailMessage("Missing data envelope: %s", response.body())
                .isTrue();
        return envelope.get("data");
    }

    private JsonNode error(HttpResponse<String> response) throws Exception {
        JsonNode envelope = objectMapper.readTree(response.body());
        assertThat(envelope.has("error"))
                .withFailMessage("Missing error envelope: %s", response.body())
                .isTrue();
        return envelope.get("error");
    }

    private HttpResponse<String> request(
            String method,
            String path,
            String token,
            String idempotencyKey,
            String body,
            int expectedStatus) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token);
        if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);
        if (body != null) {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode())
                .withFailMessage("Expected HTTP %s but got %s: %s", expectedStatus, response.statusCode(), response.body())
                .isEqualTo(expectedStatus);
        return response;
    }

    private AuthenticatedPrincipal principal(UUID userId) {
        return new AuthenticatedPrincipal(userId);
    }

    private record Fixture(
            UUID organizationId,
            UUID committeeId,
            UUID coachUserId,
            UUID studentId,
            UUID outsiderId,
            UUID coachProfileId,
            UUID offeringId,
            UUID courseId,
            UUID sessionId,
            UUID enrollmentId) { }
}
