package com.pickleball.booking.coursematch.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pickleball.booking.coach.infrastructure.CoachProfileEntity;
import com.pickleball.booking.coach.infrastructure.CoachProfileRepository;
import com.pickleball.booking.identity.application.PlatformTokenService;
import com.pickleball.booking.identity.domain.RoleCode;
import com.pickleball.booking.identity.infrastructure.PlatformUserEntity;
import com.pickleball.booking.identity.infrastructure.PlatformUserRepository;
import com.pickleball.booking.identity.infrastructure.RoleAssignmentEntity;
import com.pickleball.booking.identity.infrastructure.RoleAssignmentRepository;
import com.pickleball.booking.lessonrequest.infrastructure.LessonRequestEntity;
import com.pickleball.booking.lessonrequest.infrastructure.LessonRequestRepository;
import com.pickleball.booking.organization.infrastructure.OrganizationEntity;
import com.pickleball.booking.organization.infrastructure.OrganizationRepository;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class Slice3HttpEndToEndIT {
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
    @Autowired OrganizationRepository organizations;
    @Autowired PlatformUserRepository users;
    @Autowired RoleAssignmentRepository roles;
    @Autowired CoachProfileRepository coachProfiles;
    @Autowired LessonRequestRepository lessonRequests;
    @Autowired JdbcTemplate jdbc;

    @Test
    void matchingPricingCoachAcceptanceAndFormationWorkThroughRealHttpBoundary() throws Exception {
        Fixture fixture = fixture();
        String committeeToken = tokens.issue(fixture.committeeId()).value();
        String coachToken = tokens.issue(fixture.coachId()).value();
        Instant startAt = Instant.now().plusSeconds(7200);
        Instant endAt = startAt.plusSeconds(3600);

        JsonNode created = expectData(request(
                "POST", "/api/v1/course-matches", committeeToken, null,
                """
                {
                  "lessonRequestId":"%s",
                  "coachAssignments":[{"coachProfileId":"%s","sessionIndexes":[1]}],
                  "sessionPlan":[{
                    "sequenceNo":1,
                    "startAt":"%s",
                    "endAt":"%s",
                    "venueId":"%s",
                    "venueName":null,
                    "venueAddress":null
                  }],
                  "participantCount":2
                }
                """.formatted(
                        fixture.lessonRequestId(), fixture.coachProfileId(), startAt, endAt, fixture.venueId()),
                201));
        UUID courseMatchId = UUID.fromString(created.get("id").asText());
        UUID invitationId = UUID.fromString(created.get("coachInvitations").get(0).get("invitationId").asText());
        assertThat(created.get("status").asText()).isEqualTo("DRAFT");
        assertThat(created.get("readiness").get("coachesAccepted").asBoolean()).isFalse();
        assertThat(created.get("readiness").get("readyToConfirm").asBoolean()).isFalse();

        JsonNode inbox = expectData(request(
                "GET", "/api/v1/course-match-invitations/mine", coachToken, null, null, 200));
        assertThat(inbox.isArray()).isTrue();
        assertThat(inbox).anySatisfy(item -> {
            assertThat(item.get("invitationId").asText()).isEqualTo(invitationId.toString());
            assertThat(item.get("courseMatchId").asText()).isEqualTo(courseMatchId.toString());
            assertThat(item.get("status").asText()).isEqualTo("INVITED");
        });

        JsonNode accepted = expectData(request(
                "POST", "/api/v1/course-match-invitations/" + invitationId + "/response",
                coachToken, null,
                "{\"status\":\"ACCEPTED\",\"responseNote\":\"Accepted in Slice 3 HTTP acceptance\"}",
                200));
        assertThat(accepted.get("status").asText()).isEqualTo("ACCEPTED");

        JsonNode beforePricing = expectData(request(
                "GET", "/api/v1/course-matches/" + courseMatchId, committeeToken, null, null, 200));
        assertThat(beforePricing.get("readiness").get("coachesAccepted").asBoolean()).isTrue();
        assertThat(beforePricing.get("readiness").get("scheduleConflictFree").asBoolean()).isTrue();
        assertThat(beforePricing.get("readiness").get("pricingConfirmed").asBoolean()).isFalse();
        assertThat(beforePricing.get("readiness").get("readyToConfirm").asBoolean()).isFalse();

        JsonNode preview = expectData(request(
                "POST", "/api/v1/course-matches/" + courseMatchId + "/pricing-preview",
                committeeToken, null, null, 200));
        assertThat(preview.get("currency").asText()).isEqualTo("TWD");
        assertThat(preview.get("totalAmount").asText()).isEqualTo("1000.00");
        assertThat(preview.get("breakdown")).hasSize(2);
        String pricingFingerprint = preview.get("pricingFingerprint").asText();
        assertThat(pricingFingerprint).hasSize(64);

        String priceKey = "slice3-price-" + UUID.randomUUID();
        JsonNode confirmedPrice = expectData(request(
                "POST", "/api/v1/course-matches/" + courseMatchId + "/pricing-confirmation",
                committeeToken, priceKey,
                """
                {
                  "acceptedTotalAmount":"1000.00",
                  "currency":"TWD",
                  "pricingFingerprint":"%s",
                  "confirmationNote":"Slice 3 HTTP acceptance"
                }
                """.formatted(pricingFingerprint),
                201));
        UUID matchPriceSnapshotId = UUID.fromString(confirmedPrice.get("priceSnapshotId").asText());
        assertThat(confirmedPrice.get("status").asText()).isEqualTo("CONFIRMED");

        JsonNode ready = expectData(request(
                "GET", "/api/v1/course-matches/" + courseMatchId, committeeToken, null, null, 200));
        assertThat(ready.get("readiness").get("pricingConfirmed").asBoolean()).isTrue();
        assertThat(ready.get("readiness").get("readyToConfirm").asBoolean()).isTrue();

        String formationKey = "slice3-formation-" + UUID.randomUUID();
        JsonNode formation = expectData(request(
                "POST", "/api/v1/course-matches/" + courseMatchId + "/confirmation",
                committeeToken, formationKey, "{\"confirm\":true}", 201));
        UUID courseId = UUID.fromString(formation.get("courseId").asText());
        UUID courseSessionId = UUID.fromString(formation.get("sessionIds").get(0).asText());
        UUID receivableId = UUID.fromString(formation.get("receivableIds").get(0).asText());
        assertThat(formation.get("courseMatchStatus").asText()).isEqualTo("CONFIRMED");
        assertThat(formation.get("courseStatus").asText()).isEqualTo("ACTIVE");

        assertThat(jdbc.queryForObject(
                "select count(*) from courses where id=? and source_match_id=? and status='ACTIVE'",
                Integer.class, courseId, courseMatchId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from course_sessions where id=? and course_id=? and status='SCHEDULED'",
                Integer.class, courseSessionId, courseId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from schedule_reservations where course_session_id=? and user_id=? and status='CONFIRMED'",
                Integer.class, courseSessionId, fixture.coachId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select source_match_price_snapshot_id from session_price_snapshots where course_session_id=?",
                UUID.class, courseSessionId)).isEqualTo(matchPriceSnapshotId);
        assertThat(jdbc.queryForObject(
                "select total_receivable from session_price_snapshots where course_session_id=?",
                BigDecimal.class, courseSessionId)).isEqualByComparingTo("1000.00");
        assertThat(jdbc.queryForObject(
                "select total_amount from receivables where id=?",
                BigDecimal.class, receivableId)).isEqualByComparingTo("1000.00");
        assertThat(jdbc.queryForObject(
                "select status from lesson_requests where id=?",
                String.class, fixture.lessonRequestId())).isEqualTo("MATCHED");
        assertThat(jdbc.queryForObject(
                "select status from course_matches where id=?",
                String.class, courseMatchId)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject(
                "select count(*) from course_approvals where course_id=?",
                Integer.class, courseId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from outbox_events where aggregate_type='CourseMatch' and aggregate_id=?",
                Integer.class, courseMatchId)).isGreaterThanOrEqualTo(1);
    }

    private Fixture fixture() {
        OrganizationEntity org = organizations.saveAndFlush(
                new OrganizationEntity("HTTP-S3-" + UUID.randomUUID(), "Slice 3 HTTP Acceptance"));
        PlatformUserEntity committee = users.saveAndFlush(
                new PlatformUserEntity(UUID.randomUUID(), "Committee HTTP"));
        PlatformUserEntity coach = users.saveAndFlush(
                new PlatformUserEntity(UUID.randomUUID(), "Coach HTTP"));
        PlatformUserEntity requester = users.saveAndFlush(
                new PlatformUserEntity(UUID.randomUUID(), "Requester HTTP"));
        roles.saveAndFlush(new RoleAssignmentEntity(committee, org, RoleCode.COMMITTEE));
        roles.saveAndFlush(new RoleAssignmentEntity(coach, org, RoleCode.COACH));
        roles.saveAndFlush(new RoleAssignmentEntity(requester, org, RoleCode.STUDENT));

        CoachProfileEntity coachProfile = coachProfiles.saveAndFlush(
                new CoachProfileEntity(org.getId(), coach.getId(), "INTERMEDIATE", null));
        coachProfile.approve(committee.getId());
        coachProfile = coachProfiles.saveAndFlush(coachProfile);

        UUID venueId = UUID.randomUUID();
        jdbc.update("""
                insert into venues(id, organization_id, name, address, default_cost_amount, status)
                values (?, ?, 'HTTP Acceptance Court', 'Taipei', 200.00, 'ACTIVE')
                """, venueId, org.getId());
        jdbc.update("""
                insert into pricing_rules(
                    id, organization_id, name, priority, coach_profile_id, course_type, skill_level,
                    min_participants, max_participants, base_amount, pricing_unit, active_from, status)
                values (?, ?, 'HTTP Acceptance Price', 10, ?, 'PRIVATE', 'INTERMEDIATE',
                    1, 4, 800.00, 'PER_SESSION', now()-interval '1 hour', 'ACTIVE')
                """, UUID.randomUUID(), org.getId(), coachProfile.getId());

        LessonRequestEntity request = new LessonRequestEntity(
                org.getId(), requester.getId(), coachProfile.getId(), null,
                "PRIVATE", "SINGLE", "FULL_COURSE", "INTERMEDIATE",
                (short) 2, (short) 0, null, (short) 4, (short) 1, null);
        request.submit();
        request.review(true, committee.getId(), "Approved for Slice 3 HTTP acceptance");
        request = lessonRequests.saveAndFlush(request);

        return new Fixture(
                org.getId(), committee.getId(), coach.getId(), requester.getId(),
                coachProfile.getId(), request.getId(), venueId);
    }

    private JsonNode expectData(HttpResponse<String> response, int expectedStatus) throws Exception {
        assertThat(response.statusCode())
                .withFailMessage("Expected HTTP %s but got %s: %s", expectedStatus, response.statusCode(), response.body())
                .isEqualTo(expectedStatus);
        JsonNode envelope = objectMapper.readTree(response.body());
        assertThat(envelope.has("data")).withFailMessage("Missing data envelope: %s", response.body()).isTrue();
        return envelope.get("data");
    }

    private HttpResponse<String> request(
            String method,
            String path,
            String token,
            String idempotencyKey,
            String body,
            int unusedExpectedStatus) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token);
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private record Fixture(
            UUID organizationId,
            UUID committeeId,
            UUID coachId,
            UUID requesterId,
            UUID coachProfileId,
            UUID lessonRequestId,
            UUID venueId) {}
}
