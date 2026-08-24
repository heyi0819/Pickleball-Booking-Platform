package com.pickleball.booking.coursematch.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.pickleball.booking.coach.infrastructure.CoachProfileEntity;
import com.pickleball.booking.coach.infrastructure.CoachProfileRepository;
import com.pickleball.booking.coursematch.application.CourseMatchService.CoachAssignmentCommand;
import com.pickleball.booking.coursematch.application.CourseMatchService.CreateCommand;
import com.pickleball.booking.coursematch.application.CourseMatchService.SessionPlanCommand;
import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.identity.domain.RoleCode;
import com.pickleball.booking.identity.infrastructure.PlatformUserEntity;
import com.pickleball.booking.identity.infrastructure.PlatformUserRepository;
import com.pickleball.booking.identity.infrastructure.RoleAssignmentEntity;
import com.pickleball.booking.identity.infrastructure.RoleAssignmentRepository;
import com.pickleball.booking.lessonrequest.infrastructure.LessonRequestEntity;
import com.pickleball.booking.lessonrequest.infrastructure.LessonRequestRepository;
import com.pickleball.booking.organization.infrastructure.OrganizationEntity;
import com.pickleball.booking.organization.infrastructure.OrganizationRepository;
import com.pickleball.booking.shared.application.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
class ConfirmCourseMatchServiceIT {
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

    @Autowired CourseMatchService matchService;
    @Autowired CourseMatchInvitationService invitationService;
    @Autowired MatchPricingService pricingService;
    @Autowired ConfirmCourseMatchService confirmationService;
    @Autowired OrganizationRepository organizations;
    @Autowired PlatformUserRepository users;
    @Autowired RoleAssignmentRepository roles;
    @Autowired CoachProfileRepository coachProfiles;
    @Autowired LessonRequestRepository lessonRequests;
    @Autowired JdbcTemplate jdbc;

    @Test
    void confirmedMatchFormsCompleteFormalCourseAndReplaysIdempotently() {
        Fixture fixture = readyFixture(Instant.now().plusSeconds(7200), false);
        UUID matchPriceSnapshotId = confirmPricing(fixture);
        String key = "formation-" + UUID.randomUUID();

        var first = confirmationService.confirm(
                principal(fixture.committeeId()), fixture.courseMatchId(), key,
                new ConfirmCourseMatchService.ConfirmCommand(true));
        var replay = confirmationService.confirm(
                principal(fixture.committeeId()), fixture.courseMatchId(), key,
                new ConfirmCourseMatchService.ConfirmCommand(true));

        assertThat(first.courseMatchStatus()).isEqualTo("CONFIRMED");
        assertThat(first.courseStatus()).isEqualTo("ACTIVE");
        assertThat(first.sessionIds()).hasSize(1);
        assertThat(first.receivableIds()).hasSize(1);
        assertThat(replay.courseId()).isEqualTo(first.courseId());
        assertThat(replay.sessionIds()).containsExactlyElementsOf(first.sessionIds());
        assertThat(replay.receivableIds()).containsExactlyElementsOf(first.receivableIds());

        assertThat(count("courses", "source_match_id", fixture.courseMatchId())).isEqualTo(1);
        assertThat(count("course_sessions", "course_id", first.courseId())).isEqualTo(1);
        assertThat(count("course_contact_assignments", "course_id", first.courseId())).isEqualTo(1);
        assertThat(count("course_memberships", "course_id", first.courseId())).isZero();
        assertThat(count("enrollments", "organization_id", fixture.organizationId())).isZero();
        assertThat(count("session_coach_assignments", "course_session_id", first.sessionIds().getFirst())).isEqualTo(1);
        assertThat(count("schedule_reservations", "course_session_id", first.sessionIds().getFirst())).isEqualTo(1);
        assertThat(count("session_venue_arrangements", "course_session_id", first.sessionIds().getFirst())).isEqualTo(1);
        assertThat(count("course_approvals", "course_id", first.courseId())).isEqualTo(1);
        assertThat(count("session_price_snapshots", "course_session_id", first.sessionIds().getFirst())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select source_match_price_snapshot_id from session_price_snapshots where course_session_id=?
                """, UUID.class, first.sessionIds().getFirst())).isEqualTo(matchPriceSnapshotId);
        assertThat(jdbc.queryForObject("""
                select total_receivable from session_price_snapshots where course_session_id=?
                """, BigDecimal.class, first.sessionIds().getFirst())).isEqualByComparingTo("1000.00");
        assertThat(jdbc.queryForObject("""
                select total_amount from receivables where id=?
                """, BigDecimal.class, first.receivableIds().getFirst())).isEqualByComparingTo("1000.00");
        assertThat(jdbc.queryForObject("select status from lesson_requests where id=?", String.class,
                fixture.lessonRequestId())).isEqualTo("MATCHED");
        assertThat(jdbc.queryForObject("select status from course_matches where id=?", String.class,
                fixture.courseMatchId())).isEqualTo("CONFIRMED");
    }

    @Test
    void stalePricingRejectsFormationWithoutPartialArtifacts() {
        Fixture fixture = readyFixture(Instant.now().plusSeconds(7200), false);
        confirmPricing(fixture);
        jdbc.update("update venues set default_cost_amount=250.00 where id=?", fixture.venueId());

        Throwable failure = catchThrowable(() -> confirmationService.confirm(
                principal(fixture.committeeId()), fixture.courseMatchId(), "stale-form-" + UUID.randomUUID(),
                new ConfirmCourseMatchService.ConfirmCommand(true)));
        assertBusinessCode(failure, "PRICE_CHANGED_RECALC_REQUIRED");

        assertNoFormalArtifacts(fixture);
        assertThat(jdbc.queryForObject("select status from lesson_requests where id=?", String.class,
                fixture.lessonRequestId())).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("select status from course_matches where id=?", String.class,
                fixture.courseMatchId())).isEqualTo("DRAFT");
    }

    @Test
    void lateAvailabilityConversionFailureRollsBackAllFormalArtifacts() {
        Fixture fixture = readyFixture(Instant.now().plusSeconds(7200), true);
        confirmPricing(fixture);
        jdbc.update("update coach_availability_proposals set status='CLOSED' where id=?",
                fixture.availabilityProposalId());

        Throwable failure = catchThrowable(() -> confirmationService.confirm(
                principal(fixture.committeeId()), fixture.courseMatchId(), "late-fail-" + UUID.randomUUID(),
                new ConfirmCourseMatchService.ConfirmCommand(true)));
        assertBusinessCode(failure, "MATCH_NOT_READY");

        assertNoFormalArtifacts(fixture);
        assertThat(jdbc.queryForObject("select status from coach_availability_claims where lesson_request_id=?",
                String.class, fixture.lessonRequestId())).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select status from lesson_requests where id=?", String.class,
                fixture.lessonRequestId())).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("select status from course_matches where id=?", String.class,
                fixture.courseMatchId())).isEqualTo("DRAFT");
    }

    @Test
    void concurrentConfirmationOfSameMatchCreatesExactlyOneCourse() throws Exception {
        Fixture fixture = readyFixture(Instant.now().plusSeconds(7200), false);
        confirmPricing(fixture);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch gate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Outcome> first = pool.submit(() -> confirmAfterGate(
                    ready, gate, fixture, "same-a-" + UUID.randomUUID()));
            Future<Outcome> second = pool.submit(() -> confirmAfterGate(
                    ready, gate, fixture, "same-b-" + UUID.randomUUID()));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            gate.countDown();
            List<Outcome> outcomes = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertThat(outcomes).filteredOn(Outcome::success).hasSize(1);
            assertThat(outcomes).filteredOn(o -> "STATE_TRANSITION_INVALID".equals(o.code())).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(count("courses", "source_match_id", fixture.courseMatchId())).isEqualTo(1);
    }

    @Test
    void twoMatchesForSameCoachAndTimeCannotBothForm() throws Exception {
        Shared shared = sharedFixture();
        Instant start = Instant.now().plusSeconds(7200);
        Fixture firstFixture = matchFixture(shared, start, false);
        Fixture secondFixture = matchFixture(shared, start, false);
        confirmPricing(firstFixture);
        confirmPricing(secondFixture);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch gate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Outcome> first = pool.submit(() -> confirmAfterGate(
                    ready, gate, firstFixture, "overlap-a-" + UUID.randomUUID()));
            Future<Outcome> second = pool.submit(() -> confirmAfterGate(
                    ready, gate, secondFixture, "overlap-b-" + UUID.randomUUID()));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            gate.countDown();
            List<Outcome> outcomes = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertThat(outcomes).filteredOn(Outcome::success).hasSize(1);
            assertThat(outcomes).filteredOn(o -> "SCHEDULE_CONFLICT".equals(o.code())).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("select count(*) from courses where organization_id=?", Integer.class,
                shared.organizationId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from schedule_reservations where organization_id=?",
                Integer.class, shared.organizationId())).isEqualTo(1);
    }

    private Outcome confirmAfterGate(
            CountDownLatch ready, CountDownLatch gate, Fixture fixture, String key) throws InterruptedException {
        ready.countDown();
        gate.await();
        try {
            confirmationService.confirm(principal(fixture.committeeId()), fixture.courseMatchId(), key,
                    new ConfirmCourseMatchService.ConfirmCommand(true));
            return new Outcome(true, null);
        } catch (BusinessException exception) {
            return new Outcome(false, exception.code());
        }
    }

    private Fixture readyFixture(Instant start, boolean withAvailabilityClaim) {
        return matchFixture(sharedFixture(), start, withAvailabilityClaim);
    }

    private Shared sharedFixture() {
        OrganizationEntity org = organizations.saveAndFlush(
                new OrganizationEntity("FORM-" + UUID.randomUUID(), "Formation Test"));
        PlatformUserEntity committee = users.saveAndFlush(new PlatformUserEntity(UUID.randomUUID(), "Committee"));
        PlatformUserEntity coach = users.saveAndFlush(new PlatformUserEntity(UUID.randomUUID(), "Coach"));
        roles.saveAndFlush(new RoleAssignmentEntity(committee, org, RoleCode.COMMITTEE));
        roles.saveAndFlush(new RoleAssignmentEntity(coach, org, RoleCode.COACH));

        CoachProfileEntity coachProfile = coachProfiles.saveAndFlush(
                new CoachProfileEntity(org.getId(), coach.getId(), "INTERMEDIATE", null));
        coachProfile.approve(committee.getId());
        coachProfiles.saveAndFlush(coachProfile);

        UUID venueId = UUID.randomUUID();
        jdbc.update("""
                insert into venues(id, organization_id, name, address, default_cost_amount, status)
                values (?, ?, 'Formation Court', 'Taipei', 200.00, 'ACTIVE')
                """, venueId, org.getId());
        jdbc.update("""
                insert into pricing_rules(
                    id, organization_id, name, priority, coach_profile_id, course_type, skill_level,
                    min_participants, max_participants, base_amount, pricing_unit, active_from, status)
                values (?, ?, 'Formation price', 10, ?, 'PRIVATE', 'INTERMEDIATE',
                    1, 4, 800.00, 'PER_SESSION', now()-interval '1 hour', 'ACTIVE')
                """, UUID.randomUUID(), org.getId(), coachProfile.getId());
        return new Shared(org.getId(), committee.getId(), coach.getId(), coachProfile.getId(), venueId);
    }

    private Fixture matchFixture(Shared shared, Instant start, boolean withAvailabilityClaim) {
        PlatformUserEntity requester = users.saveAndFlush(new PlatformUserEntity(UUID.randomUUID(), "Requester"));
        OrganizationEntity org = organizations.findById(shared.organizationId()).orElseThrow();
        roles.saveAndFlush(new RoleAssignmentEntity(requester, org, RoleCode.STUDENT));

        UUID proposalId = null;
        if (withAvailabilityClaim) {
            proposalId = UUID.randomUUID();
            jdbc.update("""
                    insert into coach_availability_proposals(
                        id, organization_id, coach_profile_id, start_at, end_at, status,
                        submitted_at, reviewed_by, reviewed_at, review_note)
                    values (?, ?, ?, ?, ?, 'APPROVED', now(), ?, now(), 'approved')
                    """, proposalId, shared.organizationId(), shared.coachProfileId(),
                    java.sql.Timestamp.from(start), java.sql.Timestamp.from(start.plusSeconds(3600)),
                    shared.committeeId());
        }

        LessonRequestEntity request = new LessonRequestEntity(
                shared.organizationId(), requester.getId(), shared.coachProfileId(), proposalId,
                "PRIVATE", "SINGLE", "FULL_COURSE", "INTERMEDIATE",
                (short) 2, (short) 0, null, (short) 4, (short) 1, null);
        request.submit();
        request.review(true, shared.committeeId(), "approved for formation");
        request = lessonRequests.saveAndFlush(request);

        if (proposalId != null) {
            jdbc.update("""
                    insert into coach_availability_claims(
                        id, organization_id, coach_availability_proposal_id, lesson_request_id, status)
                    values (?, ?, ?, ?, 'ACTIVE')
                    """, UUID.randomUUID(), shared.organizationId(), proposalId, request.getId());
        }

        var detail = matchService.create(principal(shared.committeeId()), new CreateCommand(
                request.getId(),
                List.of(new CoachAssignmentCommand(shared.coachProfileId(), List.of((short) 1))),
                List.of(new SessionPlanCommand((short) 1, start, start.plusSeconds(3600),
                        shared.venueId(), null, null)),
                (short) 2));
        UUID invitationId = detail.coachAssignments().getFirst().getId();
        invitationService.respond(principal(shared.coachId()), invitationId,
                new CourseMatchInvitationService.ResponseCommand("ACCEPTED", "accepted"));

        return new Fixture(shared.organizationId(), shared.committeeId(), requester.getId(), shared.coachId(),
                shared.coachProfileId(), request.getId(), shared.venueId(), detail.match().getId(), invitationId,
                proposalId);
    }

    private UUID confirmPricing(Fixture fixture) {
        var preview = pricingService.preview(principal(fixture.committeeId()), fixture.courseMatchId());
        var snapshot = pricingService.confirm(
                principal(fixture.committeeId()), fixture.courseMatchId(), "price-" + UUID.randomUUID(),
                new MatchPricingService.ConfirmPricingCommand(
                        preview.totalAmount(), "TWD", preview.pricingFingerprint(), "confirmed"));
        return snapshot.priceSnapshotId();
    }

    private void assertNoFormalArtifacts(Fixture fixture) {
        assertThat(count("courses", "source_match_id", fixture.courseMatchId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from receivables where organization_id=?", Integer.class,
                fixture.organizationId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from schedule_reservations where organization_id=?", Integer.class,
                fixture.organizationId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from session_price_snapshots where organization_id=?", Integer.class,
                fixture.organizationId())).isZero();
    }

    private int count(String table, String column, UUID value) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + column + "=?", Integer.class, value);
    }

    private AuthenticatedPrincipal principal(UUID userId) {
        return new AuthenticatedPrincipal(userId);
    }

    private void assertBusinessCode(Throwable throwable, String code) {
        assertThat(throwable).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) throwable).code()).isEqualTo(code);
    }

    private record Shared(UUID organizationId, UUID committeeId, UUID coachId, UUID coachProfileId, UUID venueId) {}
    private record Fixture(
            UUID organizationId, UUID committeeId, UUID requesterId, UUID coachId, UUID coachProfileId,
            UUID lessonRequestId, UUID venueId, UUID courseMatchId, UUID invitationId, UUID availabilityProposalId) {}
    private record Outcome(boolean success, String code) {}
}
