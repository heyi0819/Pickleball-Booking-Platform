package com.pickleball.booking.lessonrequest.application;

import com.pickleball.booking.coach.infrastructure.*;
import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.identity.domain.RoleCode;
import com.pickleball.booking.identity.infrastructure.*;
import com.pickleball.booking.lessonrequest.domain.LessonRequestStatus;
import com.pickleball.booking.lessonrequest.infrastructure.*;
import com.pickleball.booking.organization.infrastructure.*;
import com.pickleball.booking.shared.application.BusinessException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class LessonRequestSubmissionConcurrencyIT {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) { r.add("spring.datasource.url", postgres::getJdbcUrl); r.add("spring.datasource.username", postgres::getUsername); r.add("spring.datasource.password", postgres::getPassword); r.add("security.jwt.signing-secret", () -> "test-only-signing-secret-with-at-least-thirty-two-characters"); }
    @Autowired LessonRequestService lessons; @Autowired LessonRequestRepository requests; @Autowired AvailabilityClaimRepository claims; @Autowired CoachProfileRepository profiles; @Autowired AvailabilityProposalRepository availability; @Autowired PlatformUserRepository users; @Autowired OrganizationRepository organizations; @Autowired RoleAssignmentRepository roles; @Autowired JdbcTemplate jdbc;

    @Test void concurrentProductionSubmissionsProduceOneClaimAndRollbackTheLoser() throws Exception {
        var org = organizations.saveAndFlush(new OrganizationEntity("CONC-" + UUID.randomUUID(), "Concurrency"));
        var coach = users.saveAndFlush(new PlatformUserEntity(UUID.randomUUID(), "Coach")); var committee = users.saveAndFlush(new PlatformUserEntity(UUID.randomUUID(), "Committee"));
        var studentA = users.saveAndFlush(new PlatformUserEntity(UUID.randomUUID(), "Student A")); var studentB = users.saveAndFlush(new PlatformUserEntity(UUID.randomUUID(), "Student B"));
        roles.saveAndFlush(new RoleAssignmentEntity(coach, org, RoleCode.COACH)); roles.saveAndFlush(new RoleAssignmentEntity(studentA, org, RoleCode.STUDENT)); roles.saveAndFlush(new RoleAssignmentEntity(studentB, org, RoleCode.STUDENT));
        var profile = profiles.saveAndFlush(new CoachProfileEntity(org.getId(), coach.getId(), null, null)); profile.approve(committee.getId()); profiles.saveAndFlush(profile);
        var proposal = availability.saveAndFlush(new AvailabilityProposalEntity(org.getId(), profile.getId(), Instant.now().plusSeconds(7200), Instant.now().plusSeconds(10800), null)); proposal.submit(); proposal.review(true, committee.getId(), "approved"); availability.saveAndFlush(proposal);
        var requestA = requests.saveAndFlush(request(org.getId(), studentA.getId(), profile.getId(), proposal.getId())); var requestB = requests.saveAndFlush(request(org.getId(), studentB.getId(), profile.getId(), proposal.getId()));
        var gate = new CountDownLatch(1); var ready = new CountDownLatch(2); var pool = Executors.newFixedThreadPool(2);
        try {
            Future<Outcome> first = pool.submit(() -> submitAfterGate(ready, gate, studentA.getId(), requestA.getId(), "key-a"));
            Future<Outcome> second = pool.submit(() -> submitAfterGate(ready, gate, studentB.getId(), requestB.getId(), "key-b"));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); gate.countDown();
            var outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertThat(outcomes).filteredOn(Outcome::success).hasSize(1);
            assertThat(outcomes).filteredOn(o -> "AVAILABILITY_ALREADY_CLAIMED".equals(o.code())).hasSize(1);
        } finally { pool.shutdownNow(); }
        var storedA = requests.findById(requestA.getId()).orElseThrow(); var storedB = requests.findById(requestB.getId()).orElseThrow();
        assertThat(List.of(storedA.getStatus(), storedB.getStatus())).containsExactlyInAnyOrder(LessonRequestStatus.SUBMITTED, LessonRequestStatus.DRAFT);
        assertThat(claims.findAll()).filteredOn(c -> c.getProposalId().equals(proposal.getId()) && c.getStatus().name().equals("ACTIVE")).hasSize(1);
        UUID loser = storedA.getStatus() == LessonRequestStatus.DRAFT ? storedA.getId() : storedB.getId();
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id = ?", Integer.class, loser)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id = ?", Integer.class, loser)).isZero();
    }

    private Outcome submitAfterGate(CountDownLatch ready, CountDownLatch gate, UUID student, UUID request, String key) throws InterruptedException { ready.countDown(); gate.await(); try { lessons.submit(new AuthenticatedPrincipal(student), request, key); return new Outcome(true, null); } catch (BusinessException exception) { return new Outcome(false, exception.code()); } }
    private LessonRequestEntity request(UUID org, UUID student, UUID profile, UUID proposal) { return new LessonRequestEntity(org, student, profile, proposal, "PRIVATE", "SINGLE", "PER_SESSION", null, (short) 1, (short) 0, null, null, (short) 1, null); }
    private record Outcome(boolean success, String code) { }
}
