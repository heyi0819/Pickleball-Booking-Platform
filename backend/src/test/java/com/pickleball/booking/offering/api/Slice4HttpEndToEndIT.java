package com.pickleball.booking.offering.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.pickleball.booking.coach.infrastructure.CoachProfileEntity;
import com.pickleball.booking.coach.infrastructure.CoachProfileRepository;
import com.pickleball.booking.identity.application.PlatformTokenService;
import com.pickleball.booking.identity.domain.RoleCode;
import com.pickleball.booking.identity.infrastructure.PlatformUserEntity;
import com.pickleball.booking.identity.infrastructure.PlatformUserRepository;
import com.pickleball.booking.identity.infrastructure.RoleAssignmentEntity;
import com.pickleball.booking.identity.infrastructure.RoleAssignmentRepository;
import com.pickleball.booking.organization.infrastructure.OrganizationEntity;
import com.pickleball.booking.organization.infrastructure.OrganizationRepository;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class Slice4HttpEndToEndIT {
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
    @Autowired JdbcTemplate jdbc;

    @Test
    void committeeAndStudentOpenEnrollmentJourneyWorksThroughHttpBoundary() throws Exception {
        Fixture fixture = fixture(2, true);
        String committeeToken = token(fixture.committeeId());
        String studentToken = token(fixture.studentOneId());
        Instant registrationOpenAt = Instant.now().minusSeconds(300);
        Instant registrationCloseAt = Instant.now().plusSeconds(3600);
        Instant sessionStart = Instant.now().plusSeconds(7200);

        JsonNode created = expectData(request(
                "POST", "/api/v1/course-offerings", committeeToken, null,
                draftBody(fixture, registrationOpenAt, registrationCloseAt, sessionStart), 201));
        UUID offeringId = UUID.fromString(created.get("summary").get("id").asText());
        assertThat(created.get("summary").get("status").asText()).isEqualTo("DRAFT");
        assertThat(created.get("sessionPlans")).hasSize(1);

        JsonNode confirmedPrice = confirmPrice(committeeToken, offeringId, "1200.00");
        assertThat(confirmedPrice.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(confirmedPrice.get("pricePerParticipant").asText()).isEqualTo("1200.00");

        String publishKey = "publish-" + UUID.randomUUID();
        JsonNode published = expectData(request(
                "POST", "/api/v1/course-offerings/" + offeringId + "/publication",
                committeeToken, publishKey, null, 200));
        JsonNode publishReplay = expectData(request(
                "POST", "/api/v1/course-offerings/" + offeringId + "/publication",
                committeeToken, publishKey, null, 200));
        assertThat(published.get("summary").get("status").asText()).isEqualTo("OPEN");
        assertThat(publishReplay.get("summary").get("id").asText()).isEqualTo(offeringId.toString());

        JsonNode studentList = expectData(request(
                "GET", "/api/v1/course-offerings?organizationId=" + fixture.organizationId(),
                studentToken, null, null, 200));
        assertThat(studentList.get("total").asLong()).isEqualTo(1);
        assertThat(studentList.get("items").get(0).get("registrationState").asText()).isEqualTo("OPEN");
        assertThat(studentList.get("items").get(0).get("registeredCount").asInt()).isZero();

        String registrationKey = "registration-" + UUID.randomUUID();
        JsonNode registration = expectData(request(
                "POST", "/api/v1/course-offerings/" + offeringId + "/registrations",
                studentToken, registrationKey, null, 201));
        JsonNode registrationReplay = expectData(request(
                "POST", "/api/v1/course-offerings/" + offeringId + "/registrations",
                studentToken, registrationKey, null, 201));
        UUID registrationId = UUID.fromString(registration.get("id").asText());
        assertThat(registrationReplay.get("id").asText()).isEqualTo(registrationId.toString());
        assertThat(registration.get("status").asText()).isEqualTo("ACTIVE");

        JsonNode detail = expectData(request(
                "GET", "/api/v1/course-offerings/" + offeringId, studentToken, null, null, 200));
        assertThat(detail.get("summary").get("registrationState").asText()).isEqualTo("REGISTERED");
        assertThat(detail.get("summary").get("registeredCount").asInt()).isEqualTo(1);
        assertThat(detail.get("summary").get("remainingCapacity").asInt()).isEqualTo(1);
        assertThat(detail.get("summary").get("ownRegistrationId").asText()).isEqualTo(registrationId.toString());

        JsonNode registrations = expectData(request(
                "GET", "/api/v1/course-offerings/" + offeringId + "/registrations",
                committeeToken, null, null, 200));
        assertThat(registrations.get("total").asLong()).isEqualTo(1);
        assertThat(registrations.get("items").get(0).get("userId").asText())
                .isEqualTo(fixture.studentOneId().toString());
        assertThat(registrations.get("items").get(0).get("scheduleConflictIndicator").asBoolean()).isFalse();

        JsonNode mine = expectData(request(
                "GET", "/api/v1/me/course-offering-registrations", studentToken, null, null, 200));
        assertThat(mine.get("total").asLong()).isEqualTo(1);
        assertThat(mine.get("items").get(0).get("id").asText()).isEqualTo(registrationId.toString());
    }

    @Test
    void pricingFingerprintReplayAndDraftRevisionProtectAgainstStalePrice() throws Exception {
        Fixture fixture = fixture(4, true);
        String committeeToken = token(fixture.committeeId());
        String studentToken = token(fixture.studentOneId());
        Instant now = Instant.now();
        Instant registrationOpenAt = now.minusSeconds(60);
        Instant registrationCloseAt = now.plusSeconds(3600);
        Instant sessionStart = now.plusSeconds(7200);

        JsonNode created = expectData(request(
                "POST", "/api/v1/course-offerings", committeeToken, null,
                draftBody(fixture, registrationOpenAt, registrationCloseAt, sessionStart), 201));
        UUID offeringId = UUID.fromString(created.get("summary").get("id").asText());

        JsonNode preview = pricingPreview(committeeToken, offeringId, "3200.00");
        String firstFingerprint = preview.get("pricingFingerprint").asText();
        assertThat(firstFingerprint).hasSize(64);
        assertThat(preview.get("billingMode").asText()).isEqualTo("FULL_COURSE");
        assertThat(preview.get("sessionCount").asInt()).isEqualTo(1);

        String priceKey = "price-confirm-" + UUID.randomUUID();
        String confirmationBody = pricingConfirmationBody("3200.00", firstFingerprint, "Initial committee price");
        JsonNode first = expectData(request(
                "POST", "/api/v1/course-offerings/" + offeringId + "/pricing-confirmation",
                committeeToken, priceKey, confirmationBody, 201));
        JsonNode replay = expectData(request(
                "POST", "/api/v1/course-offerings/" + offeringId + "/pricing-confirmation",
                committeeToken, priceKey, confirmationBody, 201));
        UUID firstSnapshotId = UUID.fromString(first.get("priceSnapshotId").asText());
        assertThat(replay.get("priceSnapshotId").asText()).isEqualTo(firstSnapshotId.toString());
        assertThat(first.get("pricingFingerprint").asText()).isEqualTo(firstFingerprint);
        assertThat(jdbc.queryForObject(
                "select rule_trace ->> 'pricingFingerprint' from course_offering_price_snapshots where id=?",
                String.class, firstSnapshotId)).isEqualTo(firstFingerprint);

        JsonNode studentPricingForbidden = expectError(request(
                "POST", "/api/v1/course-offerings/" + offeringId + "/pricing-preview",
                studentToken, null, pricingPreviewBody("3200.00"), 403));
        assertThat(studentPricingForbidden.get("code").asText()).isEqualTo("AUTH_FORBIDDEN");

        expectData(request(
                "PATCH", "/api/v1/course-offerings/" + offeringId,
                committeeToken, null,
                updateDraftBody(fixture, registrationOpenAt, registrationCloseAt, sessionStart.plusSeconds(600)), 200));
        assertThat(jdbc.queryForObject(
                "select status from course_offering_price_snapshots where id=?", String.class, firstSnapshotId))
                .isEqualTo("SUPERSEDED");

        JsonNode publishWithoutFreshPrice = expectError(request(
                "POST", "/api/v1/course-offerings/" + offeringId + "/publication",
                committeeToken, "publish-stale-" + UUID.randomUUID(), null, 422));
        assertThat(publishWithoutFreshPrice.get("code").asText()).isEqualTo("OFFERING_NOT_READY");

        JsonNode stale = expectError(request(
                "POST", "/api/v1/course-offerings/" + offeringId + "/pricing-confirmation",
                committeeToken, "stale-price-" + UUID.randomUUID(), confirmationBody, 422));
        assertThat(stale.get("code").asText()).isEqualTo("PRICE_CHANGED_RECALC_REQUIRED");

        JsonNode refreshedPreview = pricingPreview(committeeToken, offeringId, "3200.00");
        String refreshedFingerprint = refreshedPreview.get("pricingFingerprint").asText();
        assertThat(refreshedFingerprint).isNotEqualTo(firstFingerprint);
        JsonNode refreshed = expectData(request(
                "POST", "/api/v1/course-offerings/" + offeringId + "/pricing-confirmation",
                committeeToken, "fresh-price-" + UUID.randomUUID(),
                pricingConfirmationBody("3200.00", refreshedFingerprint, "Reconfirmed after schedule change"), 201));
        assertThat(refreshed.get("priceSnapshotId").asText()).isNotEqualTo(firstSnapshotId.toString());
        assertThat(jdbc.queryForObject("""
                select count(*) from course_offering_price_snapshots
                where course_offering_id=? and status='CONFIRMED'
                """, Integer.class, offeringId)).isEqualTo(1);
    }

    @Test
    void offeringCancellationCancelsActiveRegistrationAndReleasesReservationsIdempotently() throws Exception {
        Fixture fixture = fixture(2, true);
        UUID offeringId = createReadyPublishedOffering(fixture);
        String studentToken = token(fixture.studentOneId());
        String committeeToken = token(fixture.committeeId());

        JsonNode registration = expectData(request(
                "POST", "/api/v1/course-offerings/" + offeringId + "/registrations",
                studentToken, "register-before-cancel-" + UUID.randomUUID(), null, 201));
        UUID registrationId = UUID.fromString(registration.get("id").asText());

        String cancellationKey = "offering-cancel-" + UUID.randomUUID();
        JsonNode cancelled = expectData(request(
                "POST", "/api/v1/course-offerings/" + offeringId + "/cancellation",
                committeeToken, cancellationKey, "{\"reason\":\"Court unavailable\"}", 200));
        JsonNode replay = expectData(request(
                "POST", "/api/v1/course-offerings/" + offeringId + "/cancellation",
                committeeToken, cancellationKey, "{\"reason\":\"Court unavailable\"}", 200));
        assertThat(cancelled.get("summary").get("status").asText()).isEqualTo("CANCELLED");
        assertThat(replay.get("summary").get("status").asText()).isEqualTo("CANCELLED");

        assertThat(jdbc.queryForObject(
                "select status from course_offering_registrations where id=?", String.class, registrationId))
                .isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject(
                "select cancel_reason from course_offering_registrations where id=?", String.class, registrationId))
                .isEqualTo("Court unavailable");
        assertThat(jdbc.queryForObject("""
                select count(*) from schedule_reservations r
                join course_offering_sessions s on s.id=r.course_offering_session_id
                where s.course_offering_id=? and r.status='HELD'
                """, Integer.class, offeringId)).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from audit_logs
                where entity_type='OfferingRegistration' and entity_id=?
                  and action='COURSE_OFFERING_REGISTRATION_CANCELLED_BY_OFFERING'
                """, Integer.class, registrationId)).isEqualTo(1);
    }

    @Test
    void securityCapacityDuplicateAndReadinessErrorsUseStableContractCodes() throws Exception {
        Fixture fixture = fixture(1, true);
        String committeeToken = token(fixture.committeeId());
        String studentOneToken = token(fixture.studentOneId());
        String studentTwoToken = token(fixture.studentTwoId());
        String outsiderToken = token(fixture.outsiderId());
        Instant now = Instant.now();

        JsonNode created = expectData(request(
                "POST", "/api/v1/course-offerings", committeeToken, null,
                draftBody(fixture, now.minusSeconds(60), now.plusSeconds(3600), now.plusSeconds(7200)), 201));
        UUID offeringId = UUID.fromString(created.get("summary").get("id").asText());

        JsonNode readiness = expectError(request(
                "POST", "/api/v1/course-offerings/" + offeringId + "/publication",
                committeeToken, "not-ready-" + UUID.randomUUID(), null, 422));
        assertThat(readiness.get("code").asText()).isEqualTo("OFFERING_NOT_READY");

        confirmPrice(committeeToken, offeringId, "1200.00");
        request("POST", "/api/v1/course-offerings/" + offeringId + "/publication",
                committeeToken, "publish-errors-" + UUID.randomUUID(), null, 200);

        JsonNode forbidden = expectError(request(
                "GET", "/api/v1/course-offerings/" + offeringId,
                outsiderToken, null, null, 403));
        assertThat(forbidden.get("code").asText()).isEqualTo("AUTH_FORBIDDEN");

        String firstKey = "capacity-first-" + UUID.randomUUID();
        request("POST", "/api/v1/course-offerings/" + offeringId + "/registrations",
                studentOneToken, firstKey, null, 201);
        JsonNode duplicate = expectError(request(
                "POST", "/api/v1/course-offerings/" + offeringId + "/registrations",
                studentOneToken, "duplicate-" + UUID.randomUUID(), null, 409));
        assertThat(duplicate.get("code").asText()).isEqualTo("OFFERING_ALREADY_REGISTERED");

        JsonNode full = expectError(request(
                "POST", "/api/v1/course-offerings/" + offeringId + "/registrations",
                studentTwoToken, "capacity-second-" + UUID.randomUUID(), null, 409));
        assertThat(full.get("code").asText()).isEqualTo("OFFERING_CAPACITY_FULL");

        JsonNode studentCreateForbidden = expectError(request(
                "POST", "/api/v1/course-offerings", studentOneToken, null,
                draftBody(fixture, now.minusSeconds(60), now.plusSeconds(3600), now.plusSeconds(10800)), 403));
        assertThat(studentCreateForbidden.get("code").asText()).isEqualTo("AUTH_FORBIDDEN");
    }

    private UUID createReadyPublishedOffering(Fixture fixture) throws Exception {
        Instant now = Instant.now();
        String committeeToken = token(fixture.committeeId());
        JsonNode created = expectData(request(
                "POST", "/api/v1/course-offerings", committeeToken, null,
                draftBody(fixture, now.minusSeconds(300), now.plusSeconds(3600), now.plusSeconds(7200)), 201));
        UUID offeringId = UUID.fromString(created.get("summary").get("id").asText());
        confirmPrice(committeeToken, offeringId, "1200.00");
        request("POST", "/api/v1/course-offerings/" + offeringId + "/publication",
                committeeToken, "publish-ready-" + UUID.randomUUID(), null, 200);
        return offeringId;
    }

    private JsonNode confirmPrice(String committeeToken, UUID offeringId, String amount) throws Exception {
        JsonNode preview = pricingPreview(committeeToken, offeringId, amount);
        String fingerprint = preview.get("pricingFingerprint").asText();
        String key = "pricing-confirm-" + UUID.randomUUID();
        String body = pricingConfirmationBody(amount, fingerprint, "Slice 4 HTTP acceptance");
        JsonNode confirmed = expectData(request(
                "POST", "/api/v1/course-offerings/" + offeringId + "/pricing-confirmation",
                committeeToken, key, body, 201));
        JsonNode replay = expectData(request(
                "POST", "/api/v1/course-offerings/" + offeringId + "/pricing-confirmation",
                committeeToken, key, body, 201));
        assertThat(replay.get("priceSnapshotId").asText()).isEqualTo(confirmed.get("priceSnapshotId").asText());
        return confirmed;
    }

    private JsonNode pricingPreview(String committeeToken, UUID offeringId, String amount) throws Exception {
        return expectData(request(
                "POST", "/api/v1/course-offerings/" + offeringId + "/pricing-preview",
                committeeToken, null, pricingPreviewBody(amount), 200));
    }

    private String pricingPreviewBody(String amount) {
        return "{\"currency\":\"TWD\",\"pricePerParticipant\":" + amount + "}";
    }

    private String pricingConfirmationBody(String amount, String fingerprint, String note) {
        return """
                {
                  "acceptedPricePerParticipant":%s,
                  "currency":"TWD",
                  "pricingFingerprint":"%s",
                  "confirmationNote":"%s"
                }
                """.formatted(amount, fingerprint, note);
    }

    private Fixture fixture(int maximumParticipants, boolean approvedCoach) {
        OrganizationEntity organization = organizations.saveAndFlush(
                new OrganizationEntity("HTTP-S4-" + UUID.randomUUID(), "Slice 4 HTTP Acceptance"));
        PlatformUserEntity committee = users.saveAndFlush(new PlatformUserEntity(UUID.randomUUID(), "Committee S4"));
        PlatformUserEntity coach = users.saveAndFlush(new PlatformUserEntity(UUID.randomUUID(), "Coach S4"));
        PlatformUserEntity studentOne = users.saveAndFlush(new PlatformUserEntity(UUID.randomUUID(), "Student One S4"));
        PlatformUserEntity studentTwo = users.saveAndFlush(new PlatformUserEntity(UUID.randomUUID(), "Student Two S4"));
        PlatformUserEntity outsider = users.saveAndFlush(new PlatformUserEntity(UUID.randomUUID(), "Outsider S4"));
        roles.saveAndFlush(new RoleAssignmentEntity(committee, organization, RoleCode.COMMITTEE));
        roles.saveAndFlush(new RoleAssignmentEntity(coach, organization, RoleCode.COACH));
        roles.saveAndFlush(new RoleAssignmentEntity(studentOne, organization, RoleCode.STUDENT));
        roles.saveAndFlush(new RoleAssignmentEntity(studentTwo, organization, RoleCode.STUDENT));

        CoachProfileEntity coachProfile = coachProfiles.saveAndFlush(
                new CoachProfileEntity(organization.getId(), coach.getId(), "INTERMEDIATE", null));
        if (approvedCoach) {
            coachProfile.approve(committee.getId());
            coachProfile = coachProfiles.saveAndFlush(coachProfile);
        }

        UUID venueId = UUID.randomUUID();
        jdbc.update("""
                insert into venues(id, organization_id, name, address, default_cost_amount, status)
                values (?, ?, 'Slice 4 HTTP Court', 'Taipei', 0.00, 'ACTIVE')
                """, venueId, organization.getId());
        return new Fixture(
                organization.getId(), committee.getId(), coach.getId(), studentOne.getId(), studentTwo.getId(),
                outsider.getId(), coachProfile.getId(), venueId, maximumParticipants);
    }

    private String draftBody(
            Fixture fixture, Instant registrationOpenAt, Instant registrationCloseAt, Instant sessionStart) {
        return """
                {
                  "organizationId":"%s",
                  "lessonType":"GROUP",
                  "coachProfileId":"%s",
                  "title":"Open Enrollment HTTP Course",
                  "description":"Slice 4 HTTP acceptance",
                  "scheduleType":"SINGLE",
                  "billingMode":"FULL_COURSE",
                  "skillLevel":"INTERMEDIATE",
                  "minimumParticipants":1,
                  "maximumParticipants":%d,
                  "registrationOpenAt":"%s",
                  "registrationCloseAt":"%s",
                  "sessionPlans":[{
                    "sequenceNo":1,
                    "startAt":"%s",
                    "endAt":"%s",
                    "venueId":"%s",
                    "venueName":"Slice 4 HTTP Court",
                    "venueAddress":"Taipei"
                  }]
                }
                """.formatted(
                        fixture.organizationId(), fixture.coachProfileId(), fixture.maximumParticipants(),
                        registrationOpenAt, registrationCloseAt,
                        sessionStart, sessionStart.plusSeconds(3600), fixture.venueId());
    }

    private String updateDraftBody(
            Fixture fixture, Instant registrationOpenAt, Instant registrationCloseAt, Instant sessionStart) {
        return """
                {
                  "coachProfileId":"%s",
                  "title":"Open Enrollment HTTP Course - revised",
                  "description":"Slice 4 HTTP acceptance revised",
                  "scheduleType":"SINGLE",
                  "billingMode":"FULL_COURSE",
                  "skillLevel":"INTERMEDIATE",
                  "minimumParticipants":1,
                  "maximumParticipants":%d,
                  "registrationOpenAt":"%s",
                  "registrationCloseAt":"%s",
                  "sessionPlans":[{
                    "sequenceNo":1,
                    "startAt":"%s",
                    "endAt":"%s",
                    "venueId":"%s",
                    "venueName":"Slice 4 HTTP Court",
                    "venueAddress":"Taipei"
                  }]
                }
                """.formatted(
                        fixture.coachProfileId(), fixture.maximumParticipants(),
                        registrationOpenAt, registrationCloseAt,
                        sessionStart, sessionStart.plusSeconds(3600), fixture.venueId());
    }

    private String token(UUID userId) {
        return tokens.issue(userId).value();
    }

    private JsonNode expectData(HttpResponse<String> response) throws Exception {
        JsonNode envelope = objectMapper.readTree(response.body());
        assertThat(envelope.has("data"))
                .withFailMessage("Missing data envelope: %s", response.body())
                .isTrue();
        return envelope.get("data");
    }

    private JsonNode expectError(HttpResponse<String> response) throws Exception {
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

    private record Fixture(
            UUID organizationId,
            UUID committeeId,
            UUID coachId,
            UUID studentOneId,
            UUID studentTwoId,
            UUID outsiderId,
            UUID coachProfileId,
            UUID venueId,
            int maximumParticipants) { }
}
