package com.pickleball.booking.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pickleball.booking.identity.domain.RoleAssignmentStatus;
import com.pickleball.booking.identity.domain.RoleCode;
import com.pickleball.booking.identity.infrastructure.PlatformUserEntity;
import com.pickleball.booking.identity.infrastructure.PlatformUserRepository;
import com.pickleball.booking.identity.infrastructure.RoleAssignmentEntity;
import com.pickleball.booking.identity.infrastructure.RoleAssignmentRepository;
import com.pickleball.booking.organization.infrastructure.OrganizationEntity;
import com.pickleball.booking.organization.infrastructure.OrganizationRepository;
import com.pickleball.booking.shared.application.AuditOutboxService;
import com.pickleball.booking.shared.application.BusinessException;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoleDelegationServiceTest {
    @Mock IdentityService identity;
    @Mock PlatformUserRepository users;
    @Mock OrganizationRepository organizations;
    @Mock RoleAssignmentRepository roles;
    @Mock AuditOutboxService audit;
    private RoleDelegationService service;

    @BeforeEach
    void setUp() {
        service = new RoleDelegationService(identity, users, organizations, roles, audit);
    }

    @Test
    void platformAdminCanGrantCommitteeRoleAndAuditTheScopedAssignment() {
        UUID actorId = UUID.randomUUID();
        PlatformUserEntity user = new PlatformUserEntity(UUID.randomUUID(), "Committee candidate");
        OrganizationEntity organization = new OrganizationEntity("pilot", "Pilot organization");
        when(organizations.findById(organization.getId())).thenReturn(Optional.of(organization));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(roles.findByUserIdAndOrganizationIdAndRoleCode(user.getId(), organization.getId(), RoleCode.COMMITTEE))
                .thenReturn(Optional.empty());
        when(roles.save(any(RoleAssignmentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.grantCommittee(new AuthenticatedPrincipal(actorId), organization.getId(), user.getId());

        assertThat(result.organizationId()).isEqualTo(organization.getId());
        assertThat(result.userId()).isEqualTo(user.getId());
        assertThat(result.status()).isEqualTo(RoleAssignmentStatus.ACTIVE.name());
        verify(identity).requirePlatformAdministrator(new AuthenticatedPrincipal(actorId));
        verify(audit).recordAudit(any(), any(), org.mockito.ArgumentMatchers.eq("COMMITTEE_ROLE_GRANTED"),
                org.mockito.ArgumentMatchers.eq("RoleAssignment"), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsDuplicateActiveCommitteeAssignment() {
        PlatformUserEntity user = new PlatformUserEntity(UUID.randomUUID(), "Committee candidate");
        OrganizationEntity organization = new OrganizationEntity("pilot", "Pilot organization");
        RoleAssignmentEntity assignment = new RoleAssignmentEntity(user, organization, RoleCode.COMMITTEE);
        when(organizations.findById(organization.getId())).thenReturn(Optional.of(organization));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(roles.findByUserIdAndOrganizationIdAndRoleCode(user.getId(), organization.getId(), RoleCode.COMMITTEE))
                .thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.grantCommittee(new AuthenticatedPrincipal(UUID.randomUUID()), organization.getId(), user.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("ROLE_ASSIGNMENT_EXISTS"));
    }

    @Test
    void controlledBootstrapCreatesTheFirstAndOnlyGlobalPlatformAdmin() {
        PlatformUserEntity user = new PlatformUserEntity(UUID.randomUUID(), "Initial administrator");
        PilotBootstrapService bootstrap = new PilotBootstrapService(users, roles, audit);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(roles.findByUserIdAndOrganizationIsNullAndRoleCode(user.getId(), RoleCode.PLATFORM_ADMIN))
                .thenReturn(Optional.empty());
        when(roles.findAll()).thenReturn(List.of());
        when(roles.save(any(RoleAssignmentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UUID assignmentId = bootstrap.grantInitialPlatformAdmin(user.getId());

        assertThat(assignmentId).isNotNull();
        verify(audit).recordAudit(any(), org.mockito.ArgumentMatchers.eq(user.getId()),
                org.mockito.ArgumentMatchers.eq("PLATFORM_ADMIN_BOOTSTRAPPED"),
                org.mockito.ArgumentMatchers.eq("RoleAssignment"), org.mockito.ArgumentMatchers.eq(assignmentId),
                any(), any(), any(), any());
    }
}
