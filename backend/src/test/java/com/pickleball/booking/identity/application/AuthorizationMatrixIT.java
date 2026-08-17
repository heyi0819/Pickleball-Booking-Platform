package com.pickleball.booking.identity.application;

import com.pickleball.booking.identity.domain.*;
import com.pickleball.booking.identity.infrastructure.*;
import com.pickleball.booking.organization.domain.OrganizationStatus;
import com.pickleball.booking.organization.infrastructure.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class AuthorizationMatrixIT {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) { r.add("spring.datasource.url", postgres::getJdbcUrl); r.add("spring.datasource.username", postgres::getUsername); r.add("spring.datasource.password", postgres::getPassword); r.add("security.jwt.signing-secret", () -> "test-only-signing-secret-with-at-least-thirty-two-characters"); }
    @Autowired IdentityService identity; @Autowired PlatformUserRepository users; @Autowired OrganizationRepository organizations; @Autowired RoleAssignmentRepository assignments;

    @Test void persistedRoleAndOrganizationMatrixIsEnforcedAtApplicationAuthorizationBoundary() {
        var orgA = organizations.saveAndFlush(new OrganizationEntity("ORG-A-" + UUID.randomUUID(), "A")); final var orgAId = orgA.getId();
        var orgB = organizations.saveAndFlush(new OrganizationEntity("ORG-B-" + UUID.randomUUID(), "B"));
        var staff = users.saveAndFlush(new PlatformUserEntity(UUID.randomUUID(), "Staff"));
        var admin = users.saveAndFlush(new PlatformUserEntity(UUID.randomUUID(), "Admin"));
        var inactiveRole = users.saveAndFlush(new PlatformUserEntity(UUID.randomUUID(), "Inactive role"));
        var suspended = users.saveAndFlush(new PlatformUserEntity(UUID.randomUUID(), "Suspended")); suspended.changeStatus(UserStatus.SUSPENDED); users.saveAndFlush(suspended);
        assignments.saveAndFlush(new RoleAssignmentEntity(staff, orgA, RoleCode.COACH));
        assignments.saveAndFlush(new RoleAssignmentEntity(admin, null, RoleCode.PLATFORM_ADMIN));
        var revoked = assignments.saveAndFlush(new RoleAssignmentEntity(inactiveRole, orgA, RoleCode.COACH)); revoked.changeStatus(RoleAssignmentStatus.REVOKED); assignments.saveAndFlush(revoked);

        assertThat(identity.isAuthorizedForOrganization(new AuthenticatedPrincipal(staff.getId()), RoleCode.COACH, orgA.getId())).isTrue();
        assertThat(identity.isAuthorizedForOrganization(new AuthenticatedPrincipal(staff.getId()), RoleCode.COACH, orgB.getId())).isFalse();
        assertThat(identity.isAuthorizedForOrganization(new AuthenticatedPrincipal(admin.getId()), RoleCode.COACH, orgB.getId())).isTrue();
        assertThat(identity.isAuthorizedForOrganization(new AuthenticatedPrincipal(inactiveRole.getId()), RoleCode.COACH, orgA.getId())).isFalse();
        orgA = organizations.findById(orgA.getId()).orElseThrow(); orgA.changeStatus(OrganizationStatus.SUSPENDED); organizations.saveAndFlush(orgA);
        assertThat(identity.isAuthorizedForOrganization(new AuthenticatedPrincipal(staff.getId()), RoleCode.COACH, orgA.getId())).isFalse();
        orgA = organizations.findById(orgA.getId()).orElseThrow(); orgA.changeStatus(OrganizationStatus.ACTIVE); organizations.saveAndFlush(orgA);
        assertThat(identity.roles(new AuthenticatedPrincipal(staff.getId()))).hasSize(1);
        orgA = organizations.findById(orgA.getId()).orElseThrow(); orgA.changeStatus(OrganizationStatus.SUSPENDED); organizations.saveAndFlush(orgA);
        assertThat(identity.roles(new AuthenticatedPrincipal(staff.getId()))).isEmpty();
        orgA = organizations.findById(orgA.getId()).orElseThrow(); orgA.changeStatus(OrganizationStatus.ACTIVE); organizations.saveAndFlush(orgA);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> identity.isAuthorizedForOrganization(new AuthenticatedPrincipal(suspended.getId()), RoleCode.COACH, orgAId))).isInstanceOf(AccessForbiddenException.class);
    }
}
