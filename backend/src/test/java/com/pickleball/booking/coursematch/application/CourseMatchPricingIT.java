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
class CourseMatchPricingIT {
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

    @Autowired CourseMatchService matches;
    @Autowired CourseMatchInvitationService invitations;
    @Autowired MatchPricingService pricing;
    @Autowired OrganizationRepository organizations;
    @Autowired PlatformUserRepository users;
    @Autowired RoleAssignmentRepository roles;
    @Autowired CoachProfileRepository coachProfiles;
    @Autowired LessonRequestRepository lessonRequests;
    @Autowired JdbcTemplate jdbc;

    @Test
    void onlyInvitedCoachCanRespondAndResponseIsOneWay() {
        Fixture fixture = fixture();

        Throwable denied = catchThrowable(() -> invitations.respond(
                principal(fixture.requesterId()), fixture.invitationId(),
                new CourseMatchInvitationService.ResponseCommand("ACCEPTED", null)));
        assertBusinessCode(denied, "AUTH_FORBIDDEN");

        var accepted = invitations.respond(
                principal(fixture.coachId()), fixture.invitationId(),
                new CourseMatchInvitationService.ResponseCommand("ACCEPTED", "available"));
        assertThat(accepted.invitation().getStatus().name()).isEqualTo("ACCEPTED");
        assertThat(accepted.invitation().getRespondedAt()).isNotNull();

        Throwable duplicate = catchThrowable(() -> invitations.respond(
                principal(fixture.coachId()), fixture.invitationId(),
                new CourseMatchInvitationService.ResponseCommand("REJECTED", "changed mind")));
        assertBusinessCode(duplicate, "STATE_TRANSITION_INVALID");

        assertThat(jdbc.queryForObject("""
                select count(*) from audit_logs
                where entity_type='CourseMatchSessionCoach' and entity_id=?
                """, Integer.class, fixture.invitationId())).isEqualTo(1);
    }

    @Test
    void previewDoesNotPersistAndConfirmationIsImmutableAndIdempotent() {
        Fixture fixture = fixture();
        accept(fixture);
        seedPricingRule(fixture, new BigDecimal("800.00"), "PER_SESSION");

        var preview = pricing.preview(principal(fixture.committeeId()), fixture.courseMatchId());
        assertThat(preview.currency()).isEqualTo("TWD");
        assertThat(preview.totalAmount()).isEqualByComparingTo("1000.00");
        assertThat(preview.breakdown()).hasSize(2);
        assertThat(preview.pricingFingerprint()).hasSize(64);
        assertThat(snapshotCount(fixture.courseMatchId())).isZero();

        String key = "pricing-" + UUID.randomUUID();
        var command = new MatchPricingService.ConfirmPricingCommand(
                new BigDecimal("1000.00"), "TWD", preview.pricingFingerprint(), "approved price");
        var first = pricing.confirm(principal(fixture.committeeId()), fixture.courseMatchId(), key, command);
        var replay = pricing.confirm(principal(fixture.committeeId()), fixture.courseMatchId(), key, command);

        assertThat(replay.priceSnapshotId()).isEqualTo(first.priceSnapshotId());
        assertThat(first.status()).isEqualTo("CONFIRMED");
        assertThat(first.totalAmount()).isEqualByComparingTo("1000.00");
        assertThat(snapshotCount(fixture.courseMatchId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select coalesce(sum(line_amount),0) from course_match_price_snapshot_items
                where course_match_price_snapshot_id=?
                """, BigDecimal.class, first.priceSnapshotId())).isEqualByComparingTo("1000.00");

        Throwable conflict = catchThrowable(() -> pricing.confirm(
                principal(fixture.committeeId()), fixture.courseMatchId(), key,
                new MatchPricingService.ConfirmPricingCommand(
                        new BigDecimal("999.00"), "TWD", preview.pricingFingerprint(), "approved price")));
        assertBusinessCode(conflict, "IDEMPOTENCY_CONFLICT");
    }

    @Test
    void changedVenueCostMakesOldPreviewStaleWithoutPersistingSnapshot() {
        Fixture fixture = fixture();
        accept(fixture);
        seedPricingRule(fixture, new BigDecimal("800.00"), "PER_SESSION");

        var preview = pricing.preview(principal(fixture.committeeId()), fixture.courseMatchId());
        jdbc.update("update venues set default_cost_amount=250.00 where id=?", fixture.venueId());

        Throwable stale = catchThrowable(() -> pricing.confirm(
                principal(fixture.committeeId()), fixture.courseMatchId(), "stale-" + UUID.randomUUID(),
                new MatchPricingService.ConfirmPricingCommand(
                        preview.totalAmount(), "TWD", preview.pricingFingerprint(), null)));
        assertBusinessCode(stale, "PRICE_CHANGED_RECALC_REQUIRED");
        assertThat(snapshotCount(fixture.courseMatchId())).isZero();

        var recalculated = pricing.preview(principal(fixture.committeeId()), fixture.courseMatchId());
        assertThat(recalculated.totalAmount()).isEqualByComparingTo("1050.00");
        assertThat(recalculated.pricingFingerprint()).isNotEqualTo(preview.pricingFingerprint());
    }

    @Test
    void studentCannotPreviewOrConfirmMatchPricing() {
        Fixture fixture = fixture();
        seedPricingRule(fixture, new BigDecimal("800.00"), "PER_SESSION");

        Throwable previewDenied = catchThrowable(() -> pricing.preview(
                principal(fixture.requesterId()), fixture.courseMatchId()));
        assertBusinessCode(previewDenied, "AUTH_FORBIDDEN");
    }

    @Test
    void concurrentPricingConfirmationsCreateAtMostOneActiveSnapshot() throws Exception {
        Fixture fixture = fixture();
        accept(fixture);
        seedPricingRule(fixture, new BigDecimal("800.00"), "PER_SESSION");
        var preview = pricing.preview(principal(fixture.committeeId()), fixture.courseMatchId());
        var command = new MatchPricingService.ConfirmPricingCommand(
                preview.totalAmount(), "TWD", preview.pricingFingerprint(), null);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch gate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Outcome> first = pool.submit(() -> confirmAfterGate(
                    ready, gate, fixture, "concurrent-a-" + UUID.randomUUID(), command));
            Future<Outcome> second = pool.submit(() -> confirmAfterGate(
                    ready, gate, fixture, "concurrent-b-" + UUID.randomUUID(), command));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            gate.countDown();
            List<Outcome> outcomes = List.of(
                    first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertThat(outcomes).filteredOn(Outcome::success).hasSize(1);
            assertThat(outcomes).filteredOn(o -> "STATE_TRANSITION_INVALID".equals(o.code())).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(snapshotCount(fixture.courseMatchId())).isEqualTo(1);
    }

    private Outcome confirmAfterGate(
            CountDownLatch ready,
            CountDownLatch gate,
            Fixture fixture,
            String key,
            MatchPricingService.ConfirmPricingCommand command) throws InterruptedException {
        ready.countDown();
        gate.await();
        try {
            pricing.confirm(principal(fixture.committeeId()), fixture.courseMatchId(), key, command);
            return new Outcome(true, null);
        } catch (BusinessException exception) {
            return new Outcome(false, exception.code());
        }
    }

    private Fixture fixture() {
        OrganizationEntity org = organizations.saveAndFlush(
                new OrganizationEntity("PRICE-" + UUID.randomUUID(), "Pricing Test"));
        PlatformUserEntity committee = users.saveAndFlush(new PlatformUserEntity(UUID.randomUUID(), "Committee"));
        PlatformUserEntity requester = users.saveAndFlush(new PlatformUserEntity(UUID.randomUUID(), "Requester"));
        PlatformUserEntity coach = users.saveAndFlush(new PlatformUserEntity(UUID.randomUUID(), "Coach"));
        roles.saveAndFlush(new RoleAssignmentEntity(committee, org, RoleCode.COMMITTEE));
        roles.saveAndFlush(new RoleAssignmentEntity(requester, org, RoleCode.STUDENT));
        roles.saveAndFlush(new RoleAssignmentEntity(coach, org, RoleCode.COACH));

        CoachProfileEntity coachProfile = coachProfiles.saveAndFlush(
                new CoachProfileEntity(org.getId(), coach.getId(), "INTERMEDIATE", null));
        coachProfile.approve(committee.getId());
        coachProfiles.saveAndFlush(coachProfile);

        LessonRequestEntity request = new LessonRequestEntity(
                org.getId(), requester.getId(), coachProfile.getId(), null,
                "PRIVATE", "SINGLE", "FULL_COURSE", "INTERMEDIATE",
                (short) 2, (short) 0, null, (short) 4, (short) 1, null);
        request.submit();
        request.review(true, committee.getId(), "approved for pricing");
        request = lessonRequests.saveAndFlush(request);

        UUID venueId = UUID.randomUUID();
        jdbc.update("""
                insert into venues(id, organization_id, name, address, default_cost_amount, status)
                values (?, ?, 'Pricing Court', 'Taipei', 200.00, 'ACTIVE')
                """, venueId, org.getId());

        var detail = matches.create(principal(committee.getId()), new CreateCommand(
                request.getId(),
                List.of(new CoachAssignmentCommand(coachProfile.getId(), List.of((short) 1))),
                List.of(new SessionPlanCommand((short) 1,
                        Instant.now().plusSeconds(7200), Instant.now().plusSeconds(10800),
                        venueId, null, null)),
                (short) 2));

        return new Fixture(org.getId(), committee.getId(), requester.getId(), coach.getId(),
                coachProfile.getId(), request.getId(), venueId, detail.match().getId(),
                detail.coachAssignments().getFirst().getId());
    }

    private void accept(Fixture fixture) {
        invitations.respond(principal(fixture.coachId()), fixture.invitationId(),
                new CourseMatchInvitationService.ResponseCommand("ACCEPTED", null));
    }

    private void seedPricingRule(Fixture fixture, BigDecimal baseAmount, String pricingUnit) {
        jdbc.update("""
                insert into pricing_rules(
                    id, organization_id, name, priority, coach_profile_id, course_type, skill_level,
                    min_participants, max_participants, base_amount, pricing_unit, active_from, status)
                values (?, ?, 'Coach pricing', 10, ?, 'PRIVATE', 'INTERMEDIATE',
                    1, 4, ?, ?, now()-interval '1 hour', 'ACTIVE')
                """, UUID.randomUUID(), fixture.organizationId(), fixture.coachProfileId(), baseAmount, pricingUnit);
    }

    private int snapshotCount(UUID courseMatchId) {
        return jdbc.queryForObject("""
                select count(*) from course_match_price_snapshots
                where course_match_id=? and status='CONFIRMED'
                """, Integer.class, courseMatchId);
    }

    private AuthenticatedPrincipal principal(UUID userId) {
        return new AuthenticatedPrincipal(userId);
    }

    private void assertBusinessCode(Throwable throwable, String code) {
        assertThat(throwable).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) throwable).code()).isEqualTo(code);
    }

    private record Fixture(
            UUID organizationId,
            UUID committeeId,
            UUID requesterId,
            UUID coachId,
            UUID coachProfileId,
            UUID lessonRequestId,
            UUID venueId,
            UUID courseMatchId,
            UUID invitationId) {}

    private record Outcome(boolean success, String code) {}
}
