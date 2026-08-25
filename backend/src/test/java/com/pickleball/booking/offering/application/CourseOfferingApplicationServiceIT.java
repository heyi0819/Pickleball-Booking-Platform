package com.pickleball.booking.offering.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.pickleball.booking.coach.infrastructure.CoachProfileEntity;
import com.pickleball.booking.coach.infrastructure.CoachProfileRepository;
import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.identity.domain.RoleCode;
import com.pickleball.booking.identity.infrastructure.PlatformUserEntity;
import com.pickleball.booking.identity.infrastructure.PlatformUserRepository;
import com.pickleball.booking.identity.infrastructure.RoleAssignmentEntity;
import com.pickleball.booking.identity.infrastructure.RoleAssignmentRepository;
import com.pickleball.booking.offering.application.CourseOfferingApplicationService.DraftCommand;
import com.pickleball.booking.offering.application.CourseOfferingApplicationService.PriceCommand;
import com.pickleball.booking.offering.application.CourseOfferingApplicationService.SessionCommand;
import com.pickleball.booking.offering.domain.CourseOfferingStatus;
import com.pickleball.booking.offering.domain.OfferingBillingMode;
import com.pickleball.booking.offering.domain.OfferingScheduleType;
import com.pickleball.booking.organization.infrastructure.OrganizationEntity;
import com.pickleball.booking.organization.infrastructure.OrganizationRepository;
import com.pickleball.booking.shared.application.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
class CourseOfferingApplicationServiceIT {
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

    @Autowired CourseOfferingApplicationService service;
    @Autowired OrganizationRepository organizations;
    @Autowired PlatformUserRepository users;
    @Autowired RoleAssignmentRepository roles;
    @Autowired CoachProfileRepository coachProfiles;
    @Autowired JdbcTemplate jdbc;

    @Test
    void pendingCoachCanBeDraftedButPublicationRequiresApproval() {
        Fixture fixture = draftFixture(false, 2, "USD", 1);

        Throwable failure = catchThrowable(() -> service.publish(
                principal(fixture.committeeId()), fixture.offeringId()));
        assertBusinessCode(failure, "COACH_NOT_APPROVED");
        assertThat(jdbc.queryForObject(
                "select status from course_offerings where id=?", String.class, fixture.offeringId()))
                .isEqualTo("DRAFT");

        CoachProfileEntity coach = coachProfiles.findById(fixture.coachProfileId()).orElseThrow();
        coach.approve(fixture.committeeId());
        coachProfiles.saveAndFlush(coach);

        var published = service.publish(principal(fixture.committeeId()), fixture.offeringId());
        assertThat(published.status()).isEqualTo(CourseOfferingStatus.OPEN);
        assertThat(jdbc.queryForObject("""
                select count(*) from schedule_reservations
                where organization_id=? and user_id=? and reservation_role='COACH' and status='HELD'
                """, Integer.class, fixture.organizationId(), fixture.coachUserId())).isEqualTo(1);
    }

    @Test
    void registrationAndConfirmationCreateCompleteFormalArtifactsAndPreserveCurrency() {
        Fixture fixture = draftFixture(true, 2, "USD", 1);
        service.publish(principal(fixture.committeeId()), fixture.offeringId());

        UUID studentId = fixture.studentIds().getFirst();
        String registrationKey = "offering-register-" + UUID.randomUUID();
        var registration = service.register(principal(studentId), fixture.offeringId(), registrationKey);
        var registrationReplay = service.register(principal(studentId), fixture.offeringId(), registrationKey);
        assertThat(registrationReplay.id()).isEqualTo(registration.id());

        service.close(principal(fixture.committeeId()), fixture.offeringId());
        String confirmationKey = "offering-confirm-" + UUID.randomUUID();
        var result = service.confirm(principal(fixture.committeeId()), fixture.offeringId(), confirmationKey);
        var replay = service.confirm(principal(fixture.committeeId()), fixture.offeringId(), confirmationKey);

        assertThat(result.offeringStatus()).isEqualTo("CONFIRMED");
        assertThat(result.sessionIds()).hasSize(1);
        assertThat(result.receivableIds()).hasSize(1);
        assertThat(replay.courseId()).isEqualTo(result.courseId());
        assertThat(replay.sessionIds()).containsExactlyElementsOf(result.sessionIds());
        assertThat(replay.receivableIds()).containsExactlyElementsOf(result.receivableIds());

        UUID sessionId = result.sessionIds().getFirst();
        UUID receivableId = result.receivableIds().getFirst();
        assertThat(count("courses", "source_offering_id", fixture.offeringId())).isEqualTo(1);
        assertThat(count("course_sessions", "course_id", result.courseId())).isEqualTo(1);
        assertThat(count("course_memberships", "course_id", result.courseId())).isEqualTo(1);
        assertThat(count("enrollments", "course_session_id", sessionId)).isEqualTo(1);
        assertThat(count("session_coach_assignments", "course_session_id", sessionId)).isEqualTo(1);
        assertThat(count("session_venue_arrangements", "course_session_id", sessionId)).isEqualTo(1);
        assertThat(count("course_approvals", "course_id", result.courseId())).isEqualTo(1);
        assertThat(count("session_price_snapshots", "course_session_id", sessionId)).isEqualTo(1);
        assertThat(count("receivable_items", "receivable_id", receivableId)).isEqualTo(1);

        assertThat(jdbc.queryForObject(
                "select status from course_offerings where id=?", String.class, fixture.offeringId()))
                .isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject(
                "select status from course_offering_registrations where id=?", String.class, registration.id()))
                .isEqualTo("CONVERTED");
        assertThat(jdbc.queryForObject("select currency from receivables where id=?", String.class, receivableId).trim())
                .isEqualTo("USD");
        assertThat(jdbc.queryForObject(
                "select currency from session_price_snapshots where course_session_id=?", String.class, sessionId).trim())
                .isEqualTo("USD");
        assertThat(jdbc.queryForObject(
                "select total_amount from receivables where id=?", BigDecimal.class, receivableId))
                .isEqualByComparingTo("1200.00");
        assertThat(jdbc.queryForObject("""
                select source_offering_price_snapshot_id
                from session_price_snapshots where course_session_id=?
                """, UUID.class, sessionId)).isEqualTo(fixture.priceSnapshotId());
        assertThat(jdbc.queryForObject("""
                select count(*) from schedule_reservations
                where course_session_id=? and course_offering_session_id is null and status='CONFIRMED'
                """, Integer.class, sessionId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                select count(*) from schedule_reservations
                where organization_id=? and course_offering_session_id is not null
                  and status in ('HELD','CONFIRMED')
                """, Integer.class, fixture.organizationId())).isZero();
    }

    @Test
    void concurrentRegistrationCannotExceedMaximumCapacity() throws Exception {
        Fixture fixture = draftFixture(true, 1, "TWD", 2);
        service.publish(principal(fixture.committeeId()), fixture.offeringId());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch gate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Outcome> first = pool.submit(() -> registerAfterGate(
                    ready, gate, fixture.studentIds().get(0), fixture.offeringId()));
            Future<Outcome> second = pool.submit(() -> registerAfterGate(
                    ready, gate, fixture.studentIds().get(1), fixture.offeringId()));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            gate.countDown();

            List<Outcome> outcomes = List.of(
                    first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertThat(outcomes).filteredOn(Outcome::success).hasSize(1);
            assertThat(outcomes).filteredOn(outcome -> "OFFERING_FULL".equals(outcome.code())).hasSize(1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbc.queryForObject("""
                select count(*) from course_offering_registrations
                where course_offering_id=? and status='ACTIVE'
                """, Integer.class, fixture.offeringId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from schedule_reservations r
                join course_offering_sessions s on s.id=r.course_offering_session_id
                where s.course_offering_id=? and r.reservation_role='PARTICIPANT' and r.status='HELD'
                """, Integer.class, fixture.offeringId())).isEqualTo(1);
    }

    @Test
    void lateParticipantReservationFailureRollsBackConfirmationArtifacts() {
        Fixture fixture = draftFixture(true, 1, "TWD", 1);
        service.publish(principal(fixture.committeeId()), fixture.offeringId());
        UUID studentId = fixture.studentIds().getFirst();
        var registration = service.register(
                principal(studentId), fixture.offeringId(), "rollback-register-" + UUID.randomUUID());
        service.close(principal(fixture.committeeId()), fixture.offeringId());

        jdbc.update("""
                update schedule_reservations
                set status='RELEASED', released_at=now(), release_reason='TEST_LATE_FAILURE', updated_at=now()
                where organization_id=? and user_id=? and reservation_role='PARTICIPANT' and status='HELD'
                """, fixture.organizationId(), studentId);

        Throwable failure = catchThrowable(() -> service.confirm(
                principal(fixture.committeeId()), fixture.offeringId(), "rollback-confirm-" + UUID.randomUUID()));
        assertBusinessCode(failure, "OFFERING_NOT_READY");

        assertThat(count("courses", "source_offering_id", fixture.offeringId())).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from receivables where organization_id=?", Integer.class, fixture.organizationId()))
                .isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from session_price_snapshots where organization_id=?", Integer.class, fixture.organizationId()))
                .isZero();
        assertThat(jdbc.queryForObject(
                "select status from course_offerings where id=?", String.class, fixture.offeringId()))
                .isEqualTo("CLOSED");
        assertThat(jdbc.queryForObject(
                "select status from course_offering_registrations where id=?", String.class, registration.id()))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("""
                select count(*) from schedule_reservations
                where organization_id=? and user_id=? and reservation_role='COACH' and status='HELD'
                """, Integer.class, fixture.organizationId(), fixture.coachUserId())).isEqualTo(1);
    }

    private Outcome registerAfterGate(
            CountDownLatch ready, CountDownLatch gate, UUID studentId, UUID offeringId) throws InterruptedException {
        ready.countDown();
        gate.await();
        try {
            var registration = service.register(
                    principal(studentId), offeringId, "capacity-" + studentId + "-" + UUID.randomUUID());
            return new Outcome(true, null, registration.id());
        } catch (BusinessException exception) {
            return new Outcome(false, exception.code(), null);
        }
    }

    private Fixture draftFixture(boolean approveCoach, int maximumParticipants, String currency, int studentCount) {
        OrganizationEntity organization = organizations.saveAndFlush(
                new OrganizationEntity("OFFER-" + UUID.randomUUID(), "Open Enrollment Test"));
        jdbc.update("update organizations set currency=? where id=?", currency, organization.getId());

        PlatformUserEntity committee = users.saveAndFlush(
                new PlatformUserEntity(UUID.randomUUID(), "Committee"));
        PlatformUserEntity coachUser = users.saveAndFlush(
                new PlatformUserEntity(UUID.randomUUID(), "Coach"));
        roles.saveAndFlush(new RoleAssignmentEntity(committee, organization, RoleCode.COMMITTEE));
        roles.saveAndFlush(new RoleAssignmentEntity(coachUser, organization, RoleCode.COACH));

        CoachProfileEntity coachProfile = coachProfiles.saveAndFlush(
                new CoachProfileEntity(organization.getId(), coachUser.getId(), "INTERMEDIATE", null));
        if (approveCoach) {
            coachProfile.approve(committee.getId());
            coachProfiles.saveAndFlush(coachProfile);
        }

        List<UUID> studentIds = new ArrayList<>();
        for (int i = 0; i < studentCount; i++) {
            PlatformUserEntity student = users.saveAndFlush(
                    new PlatformUserEntity(UUID.randomUUID(), "Student " + i));
            roles.saveAndFlush(new RoleAssignmentEntity(student, organization, RoleCode.STUDENT));
            studentIds.add(student.getId());
        }

        UUID venueId = UUID.randomUUID();
        jdbc.update("""
                insert into venues(id, organization_id, name, address, default_cost_amount, status)
                values (?, ?, 'Open Enrollment Court', 'Taipei', 0.00, 'ACTIVE')
                """, venueId, organization.getId());

        Instant now = Instant.now();
        Instant start = now.plusSeconds(7200);
        var offering = service.createDraft(
                principal(committee.getId()), organization.getId(),
                new DraftCommand(
                        coachProfile.getId(), "Group Open Enrollment", "Integration test offering",
                        OfferingScheduleType.SINGLE, OfferingBillingMode.FULL_COURSE, "INTERMEDIATE",
                        1, maximumParticipants,
                        now.minusSeconds(600), now.plusSeconds(3600),
                        List.of(new SessionCommand(
                                1, start, start.plusSeconds(3600), venueId,
                                "Open Enrollment Court", "Taipei"))));
        var price = service.createPriceDraft(
                principal(committee.getId()), offering.id(),
                new PriceCommand(currency, new BigDecimal("1200.00"), Map.of("source", "integration-test")));
        service.confirmPrice(principal(committee.getId()), offering.id(), price.id());

        return new Fixture(
                organization.getId(), committee.getId(), coachUser.getId(), coachProfile.getId(),
                offering.id(), price.id(), List.copyOf(studentIds));
    }

    private int count(String table, String column, UUID value) {
        return jdbc.queryForObject(
                "select count(*) from " + table + " where " + column + "=?", Integer.class, value);
    }

    private void assertBusinessCode(Throwable failure, String expectedCode) {
        assertThat(failure).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) failure).code()).isEqualTo(expectedCode);
    }

    private AuthenticatedPrincipal principal(UUID userId) {
        return new AuthenticatedPrincipal(userId);
    }

    private record Fixture(
            UUID organizationId,
            UUID committeeId,
            UUID coachUserId,
            UUID coachProfileId,
            UUID offeringId,
            UUID priceSnapshotId,
            List<UUID> studentIds) { }

    private record Outcome(boolean success, String code, UUID registrationId) { }
}
