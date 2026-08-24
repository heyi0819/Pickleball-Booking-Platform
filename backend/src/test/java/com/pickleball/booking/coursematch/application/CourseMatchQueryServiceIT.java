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
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class CourseMatchQueryServiceIT {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("security.jwt.signing-secret", () -> "test-only-signing-secret-with-at-least-thirty-two-characters");
    }

    @Autowired CourseMatchQueryService query;
    @Autowired CourseMatchInvitationQueryService invitationQuery;
    @Autowired CourseMatchService commands;
    @Autowired OrganizationRepository organizations;
    @Autowired PlatformUserRepository users;
    @Autowired RoleAssignmentRepository roles;
    @Autowired CoachProfileRepository coachProfiles;
    @Autowired LessonRequestRepository lessonRequests;

    @Test
    void committeeCanListOrganizationMatchesButUnrelatedStudentCannot() {
        Fixture fixture = fixture();
        var created = createMatch(fixture);

        var results = query.listForOrganization(new AuthenticatedPrincipal(fixture.committee().getId()), fixture.org().getId());
        assertThat(results).extracting(detail -> detail.match().getId()).contains(created.match().getId());

        Throwable denied = catchThrowable(() -> query.listForOrganization(
                new AuthenticatedPrincipal(fixture.unrelated().getId()), fixture.org().getId()));
        assertThat(denied).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) denied).code()).isEqualTo("AUTH_FORBIDDEN");
    }

    @Test
    void coachCanReadOnlyOwnedInvitationInboxAndStudentCannotUseCoachInbox() {
        Fixture fixture = fixture();
        var created = createMatch(fixture);

        var invitations = invitationQuery.mine(new AuthenticatedPrincipal(fixture.coach().getId()));
        assertThat(invitations).hasSize(1);
        assertThat(invitations.getFirst().courseMatchId()).isEqualTo(created.match().getId());
        assertThat(invitations.getFirst().coachProfileId()).isEqualTo(fixture.coachProfile().getId());
        assertThat(invitations.getFirst().status()).isEqualTo("INVITED");

        Throwable denied = catchThrowable(() -> invitationQuery.mine(
                new AuthenticatedPrincipal(fixture.unrelated().getId())));
        assertThat(denied).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) denied).code()).isEqualTo("AUTH_FORBIDDEN");
    }

    private CourseMatchService.Detail createMatch(Fixture fixture) {
        return commands.create(new AuthenticatedPrincipal(fixture.committee().getId()), new CreateCommand(
                fixture.request().getId(),
                List.of(new CoachAssignmentCommand(fixture.coachProfile().getId(), List.of((short) 1))),
                List.of(new SessionPlanCommand((short) 1, Instant.now().plusSeconds(7200),
                        Instant.now().plusSeconds(10800), null, "Court", "Taipei")),
                (short) 1));
    }

    private Fixture fixture() {
        OrganizationEntity org = organizations.saveAndFlush(new OrganizationEntity("Q-" + UUID.randomUUID(), "Query Test"));
        PlatformUserEntity committee = users.saveAndFlush(new PlatformUserEntity(UUID.randomUUID(), "Committee"));
        PlatformUserEntity requester = users.saveAndFlush(new PlatformUserEntity(UUID.randomUUID(), "Requester"));
        PlatformUserEntity coach = users.saveAndFlush(new PlatformUserEntity(UUID.randomUUID(), "Coach"));
        PlatformUserEntity unrelated = users.saveAndFlush(new PlatformUserEntity(UUID.randomUUID(), "Unrelated"));
        roles.saveAndFlush(new RoleAssignmentEntity(committee, org, RoleCode.COMMITTEE));
        roles.saveAndFlush(new RoleAssignmentEntity(requester, org, RoleCode.STUDENT));
        roles.saveAndFlush(new RoleAssignmentEntity(coach, org, RoleCode.COACH));
        roles.saveAndFlush(new RoleAssignmentEntity(unrelated, org, RoleCode.STUDENT));

        CoachProfileEntity coachProfile = coachProfiles.saveAndFlush(new CoachProfileEntity(org.getId(), coach.getId(), null, null));
        coachProfile.approve(committee.getId());
        coachProfile = coachProfiles.saveAndFlush(coachProfile);

        LessonRequestEntity request = new LessonRequestEntity(org.getId(), requester.getId(), coachProfile.getId(), null,
                "PRIVATE", "SINGLE", "FULL_COURSE", null, (short) 1, (short) 0, null, (short) 4, (short) 1, null);
        request.submit();
        request.review(true, committee.getId(), "approved");
        request = lessonRequests.saveAndFlush(request);
        return new Fixture(org, committee, coach, unrelated, coachProfile, request);
    }

    private record Fixture(
            OrganizationEntity org,
            PlatformUserEntity committee,
            PlatformUserEntity coach,
            PlatformUserEntity unrelated,
            CoachProfileEntity coachProfile,
            LessonRequestEntity request) {}
}
