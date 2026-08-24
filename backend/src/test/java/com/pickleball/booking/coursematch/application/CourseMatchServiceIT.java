package com.pickleball.booking.coursematch.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.pickleball.booking.coach.infrastructure.CoachProfileEntity;
import com.pickleball.booking.coach.infrastructure.CoachProfileRepository;
import com.pickleball.booking.coursematch.application.CourseMatchService.*;
import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.identity.domain.RoleCode;
import com.pickleball.booking.identity.infrastructure.*;
import com.pickleball.booking.lessonrequest.infrastructure.LessonRequestEntity;
import com.pickleball.booking.lessonrequest.infrastructure.LessonRequestRepository;
import com.pickleball.booking.organization.infrastructure.OrganizationEntity;
import com.pickleball.booking.organization.infrastructure.OrganizationRepository;
import com.pickleball.booking.shared.application.BusinessException;
import java.time.Instant;
import java.util.List;
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
class CourseMatchServiceIT {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("security.jwt.signing-secret", () -> "test-only-signing-secret-with-at-least-thirty-two-characters");
    }

    @Autowired CourseMatchService service;
    @Autowired CourseMatchInvitationService invitations;
    @Autowired OrganizationRepository organizations;
    @Autowired PlatformUserRepository users;
    @Autowired RoleAssignmentRepository roles;
    @Autowired CoachProfileRepository coachProfiles;
    @Autowired LessonRequestRepository lessonRequests;
    @Autowired JdbcTemplate jdbc;

    @Test
    void committeeCreatesDraftWithServerOwnedSnapshotsAndScopedReadVisibility() {
        Fixture fixture = fixture();
        var detail = service.create(new AuthenticatedPrincipal(fixture.committeeId()), new CreateCommand(
                fixture.lessonRequestId(),
                List.of(new CoachAssignmentCommand(fixture.coachProfileId(), List.of((short) 1))),
                List.of(new SessionPlanCommand((short) 1,
                        Instant.now().plusSeconds(7200), Instant.now().plusSeconds(10800),
                        null, "External Pickleball Court", "Taipei")),
                (short) 2));

        assertThat(detail.match().getStatus().name()).isEqualTo("DRAFT");
        assertThat(detail.match().getParticipantCount()).isEqualTo((short) 2);
        assertThat(detail.sessions()).hasSize(1);
        assertThat(detail.sessions().getFirst().getVenueSnapshotType().name()).isEqualTo("OTHER");
        assertThat(detail.sessions().getFirst().getVenueFingerprint()).hasSize(64);
        assertThat(detail.coachAssignments()).hasSize(1);
        assertThat(detail.coachAssignments().getFirst().getStatus().name()).isEqualTo("INVITED");
        assertThat(detail.readiness().lessonRequestApproved()).isTrue();
        assertThat(detail.readiness().venueReady()).isTrue();
        assertThat(detail.readiness().sessionsFuture()).isTrue();
        assertThat(detail.readiness().coachesAccepted()).isFalse();
        assertThat(detail.readiness().scheduleConflictFree()).isFalse();
        assertThat(detail.readiness().readyToConfirm()).isFalse();

        assertThat(service.detail(new AuthenticatedPrincipal(fixture.requesterId()), detail.match().getId()).match().getId())
                .isEqualTo(detail.match().getId());
        assertThat(service.detail(new AuthenticatedPrincipal(fixture.coachId()), detail.match().getId()).match().getId())
                .isEqualTo(detail.match().getId());

        Throwable denied = catchThrowable(() -> service.detail(
                new AuthenticatedPrincipal(fixture.unrelatedId()), detail.match().getId()));
        assertThat(denied).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) denied).code()).isEqualTo("AUTH_FORBIDDEN");

        assertThat(jdbc.queryForObject(
                "select count(*) from audit_logs where entity_type='CourseMatch' and entity_id=?",
                Integer.class, detail.match().getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from outbox_events where aggregate_type='CourseMatch' and aggregate_id=?",
                Integer.class, detail.match().getId())).isEqualTo(1);
    }

    @Test
    void readinessUsesAcceptedCoachReservationsAndConfirmedPricing() {
        Fixture fixture = fixture();
        Instant startAt = Instant.now().plusSeconds(7200);
        Instant endAt = startAt.plusSeconds(3600);
        var created = service.create(new AuthenticatedPrincipal(fixture.committeeId()), new CreateCommand(
                fixture.lessonRequestId(),
                List.of(new CoachAssignmentCommand(fixture.coachProfileId(), List.of((short) 1))),
                List.of(new SessionPlanCommand((short) 1, startAt, endAt,
                        null, "Readiness Court", "Taipei")),
                (short) 2));

        invitations.respond(new AuthenticatedPrincipal(fixture.coachId()),
                created.coachAssignments().getFirst().getId(),
                new CourseMatchInvitationService.ResponseCommand("ACCEPTED", "available"));

        UUID priceSnapshotId = UUID.randomUUID();
        jdbc.update("""
                insert into course_match_price_snapshots(
                    id, organization_id, course_match_id, version_no, status, billing_mode,
                    total_amount, pricing_fingerprint, confirmed_by, confirmed_at, created_by)
                values (?, ?, ?, 1, 'CONFIRMED', 'FULL_COURSE', 1800.00, ?, ?, now(), ?)
                """, priceSnapshotId, fixture.organizationId(), created.match().getId(), "r".repeat(64),
                fixture.committeeId(), fixture.committeeId());

        var ready = service.detail(new AuthenticatedPrincipal(fixture.committeeId()), created.match().getId());
        assertThat(ready.readiness().coachesAccepted()).isTrue();
        assertThat(ready.readiness().pricingConfirmed()).isTrue();
        assertThat(ready.readiness().scheduleConflictFree()).isTrue();
        assertThat(ready.readiness().readyToConfirm()).isTrue();

        UUID conflictingCourseId = UUID.randomUUID();
        UUID conflictingSessionId = UUID.randomUUID();
        jdbc.update("""
                insert into courses(
                    id, organization_id, course_no, created_by_user_id, course_type, schedule_type,
                    billing_mode, expected_participant_count, guest_participant_count,
                    maximum_participants, total_session_count, status, activated_at)
                values (?, ?, ?, ?, 'PRIVATE', 'SINGLE', 'FULL_COURSE', 1, 0, 4, 1, 'ACTIVE', now())
                """, conflictingCourseId, fixture.organizationId(),
                "R-" + UUID.randomUUID().toString().substring(0, 20), fixture.committeeId());
        jdbc.update("""
                insert into course_sessions(
                    id, organization_id, course_id, sequence_no, scheduled_start_at, scheduled_end_at,
                    expected_participant_count, guest_participant_count, status)
                values (?, ?, ?, 1, ?, ?, 1, 0, 'SCHEDULED')
                """, conflictingSessionId, fixture.organizationId(), conflictingCourseId, startAt, endAt);
        jdbc.update("""
                insert into schedule_reservations(
                    id, organization_id, user_id, course_session_id, reservation_role,
                    reserved_period, status)
                values (?, ?, ?, ?, 'COACH', tstzrange(?, ?, '[)'), 'CONFIRMED')
                """, UUID.randomUUID(), fixture.organizationId(), fixture.coachId(), conflictingSessionId,
                startAt, endAt);

        var conflicted = service.detail(new AuthenticatedPrincipal(fixture.committeeId()), created.match().getId());
        assertThat(conflicted.readiness().coachesAccepted()).isTrue();
        assertThat(conflicted.readiness().scheduleConflictFree()).isFalse();
        assertThat(conflicted.readiness().readyToConfirm()).isFalse();
    }

    @Test
    void pricingSensitiveDraftPatchSupersedesConfirmedMatchPriceSnapshot() {
        Fixture fixture = fixture();
        var created = service.create(new AuthenticatedPrincipal(fixture.committeeId()), new CreateCommand(
                fixture.lessonRequestId(),
                List.of(new CoachAssignmentCommand(fixture.coachProfileId(), List.of((short) 1))),
                List.of(new SessionPlanCommand((short) 1,
                        Instant.now().plusSeconds(7200), Instant.now().plusSeconds(10800),
                        null, "Initial Court", "Taipei")),
                (short) 2));

        UUID priceSnapshotId = UUID.randomUUID();
        jdbc.update("""
                insert into course_match_price_snapshots(
                    id, organization_id, course_match_id, version_no, status, billing_mode,
                    total_amount, pricing_fingerprint, confirmed_by, confirmed_at, created_by)
                values (?, ?, ?, 1, 'CONFIRMED', 'FULL_COURSE', 1800.00, ?, ?, now(), ?)
                """, priceSnapshotId, fixture.organizationId(), created.match().getId(), "p".repeat(64),
                fixture.committeeId(), fixture.committeeId());

        var patched = service.patch(new AuthenticatedPrincipal(fixture.committeeId()), created.match().getId(),
                new PatchCommand((short) 3, null, null));

        assertThat(patched.match().getParticipantCount()).isEqualTo((short) 3);
        assertThat(patched.pricing().status()).isEqualTo("NOT_CONFIRMED");
        assertThat(jdbc.queryForObject(
                "select status from course_match_price_snapshots where id=?", String.class, priceSnapshotId))
                .isEqualTo("SUPERSEDED");
    }

    private Fixture fixture() {
        OrganizationEntity org = organizations.saveAndFlush(
                new OrganizationEntity("MATCH-" + UUID.randomUUID(), "Course Match Test"));
        PlatformUserEntity committee = users.saveAndFlush(new PlatformUserEntity(UUID.randomUUID(), "Committee"));
        PlatformUserEntity requester = users.saveAndFlush(new PlatformUserEntity(UUID.randomUUID(), "Requester"));
        PlatformUserEntity coach = users.saveAndFlush(new PlatformUserEntity(UUID.randomUUID(), "Coach"));
        PlatformUserEntity unrelated = users.saveAndFlush(new PlatformUserEntity(UUID.randomUUID(), "Unrelated"));
        roles.saveAndFlush(new RoleAssignmentEntity(committee, org, RoleCode.COMMITTEE));
        roles.saveAndFlush(new RoleAssignmentEntity(requester, org, RoleCode.STUDENT));
        roles.saveAndFlush(new RoleAssignmentEntity(coach, org, RoleCode.COACH));
        roles.saveAndFlush(new RoleAssignmentEntity(unrelated, org, RoleCode.STUDENT));

        CoachProfileEntity coachProfile = coachProfiles.saveAndFlush(
                new CoachProfileEntity(org.getId(), coach.getId(), null, null));
        coachProfile.approve(committee.getId());
        coachProfiles.saveAndFlush(coachProfile);

        LessonRequestEntity request = new LessonRequestEntity(
                org.getId(), requester.getId(), coachProfile.getId(), null,
                "PRIVATE", "SINGLE", "FULL_COURSE", null,
                (short) 2, (short) 0, null, (short) 4, (short) 1, null);
        request.submit();
        request.review(true, committee.getId(), "approved for matching");
        request = lessonRequests.saveAndFlush(request);

        return new Fixture(org.getId(), committee.getId(), requester.getId(), coach.getId(), unrelated.getId(),
                coachProfile.getId(), request.getId());
    }

    private record Fixture(UUID organizationId, UUID committeeId, UUID requesterId, UUID coachId,
            UUID unrelatedId, UUID coachProfileId, UUID lessonRequestId) {}
}
