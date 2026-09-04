package com.pickleball.booking.identity.application;

import com.pickleball.booking.identity.domain.RoleAssignmentStatus;
import com.pickleball.booking.identity.domain.RoleCode;
import com.pickleball.booking.identity.domain.UserStatus;
import com.pickleball.booking.identity.infrastructure.PlatformUserEntity;
import com.pickleball.booking.identity.infrastructure.PlatformUserRepository;
import com.pickleball.booking.identity.infrastructure.RoleAssignmentEntity;
import com.pickleball.booking.identity.infrastructure.RoleAssignmentRepository;
import com.pickleball.booking.organization.domain.OrganizationStatus;
import com.pickleball.booking.organization.infrastructure.OrganizationEntity;
import com.pickleball.booking.organization.infrastructure.OrganizationRepository;
import com.pickleball.booking.shared.application.AuditOutboxService;
import com.pickleball.booking.shared.application.BusinessException;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Global-admin-only delegation of organization-scoped committee roles. */
@Service
public class RoleDelegationService {
    private final IdentityService identity;
    private final PlatformUserRepository users;
    private final OrganizationRepository organizations;
    private final RoleAssignmentRepository roles;
    private final AuditOutboxService audit;

    public RoleDelegationService(IdentityService identity, PlatformUserRepository users,
            OrganizationRepository organizations, RoleAssignmentRepository roles, AuditOutboxService audit) {
        this.identity = identity; this.users = users; this.organizations = organizations; this.roles = roles; this.audit = audit;
    }

    @Transactional
    public List<UserSummary> findActiveUsers(AuthenticatedPrincipal actor, String query) {
        identity.requirePlatformAdministrator(actor);
        String normalized = query == null ? "" : query.trim();
        if (normalized.length() < 2) throw new BusinessException("VALIDATION_FAILED", "query must contain at least two characters");
        return users.findTop20ByDisplayNameContainingIgnoreCaseAndStatusOrderByDisplayNameAsc(normalized, UserStatus.ACTIVE)
                .stream().map(this::user).toList();
    }
    @Transactional
    public List<OrganizationSummary> activeOrganizations(AuthenticatedPrincipal actor) {
        identity.requirePlatformAdministrator(actor);
        return organizations.findByStatusOrderByNameAsc(OrganizationStatus.ACTIVE).stream()
                .map(org -> new OrganizationSummary(org.getId(), org.getCode(), org.getName())).toList();
    }

    @Transactional
    public CommitteeMembership grantCommittee(AuthenticatedPrincipal actor, UUID organizationId, UUID userId) {
        identity.requirePlatformAdministrator(actor);
        OrganizationEntity organization = activeOrganization(organizationId);
        PlatformUserEntity user = activeUser(userId);
        var existing = roles.findByUserIdAndOrganizationIdAndRoleCode(userId, organizationId, RoleCode.COMMITTEE);
        if (existing.isPresent() && existing.get().getStatus() == RoleAssignmentStatus.ACTIVE) {
            throw new BusinessException("ROLE_ASSIGNMENT_EXISTS", "Committee role is already active for this organization");
        }
        RoleAssignmentEntity assignment = existing.orElseGet(() -> roles.save(new RoleAssignmentEntity(user, organization, RoleCode.COMMITTEE)));
        if (existing.isPresent()) assignment.changeStatus(RoleAssignmentStatus.ACTIVE);
        audit.recordAudit(organizationId, actor.userId(), "COMMITTEE_ROLE_GRANTED", "RoleAssignment", assignment.getId(),
                "committee role granted", null, Map.of("userId", userId, "role", RoleCode.COMMITTEE.name()), null);
        return membership(assignment);
    }

    @Transactional
    public void revokeCommittee(AuthenticatedPrincipal actor, UUID organizationId, UUID userId) {
        identity.requirePlatformAdministrator(actor);
        activeOrganization(organizationId);
        RoleAssignmentEntity assignment = roles.findByUserIdAndOrganizationIdAndRoleCode(userId, organizationId, RoleCode.COMMITTEE)
                .orElseThrow(() -> new BusinessException("ROLE_ASSIGNMENT_NOT_FOUND", "Committee role was not found"));
        if (assignment.getStatus() != RoleAssignmentStatus.ACTIVE) {
            throw new BusinessException("ROLE_ASSIGNMENT_NOT_ACTIVE", "Committee role is not active");
        }
        assignment.changeStatus(RoleAssignmentStatus.REVOKED);
        audit.recordAudit(organizationId, actor.userId(), "COMMITTEE_ROLE_REVOKED", "RoleAssignment", assignment.getId(),
                "committee role revoked", Map.of("userId", userId, "role", RoleCode.COMMITTEE.name()), null, null);
    }

    private OrganizationEntity activeOrganization(UUID id) {
        OrganizationEntity organization = organizations.findById(id)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Organization was not found"));
        if (organization.getStatus() != OrganizationStatus.ACTIVE) throw new BusinessException("ORG_SCOPE_DENIED", "Organization is not active");
        return organization;
    }
    private PlatformUserEntity activeUser(UUID id) {
        PlatformUserEntity user = users.findById(id).orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "User was not found"));
        if (user.getStatus() != UserStatus.ACTIVE) throw new BusinessException("AUTH_FORBIDDEN", "User is not active");
        return user;
    }
    private UserSummary user(PlatformUserEntity entity) { return new UserSummary(entity.getId(), entity.getDisplayName()); }
    private CommitteeMembership membership(RoleAssignmentEntity entity) { return new CommitteeMembership(entity.getId(), entity.getUser().getId(), entity.getOrganization().getId(), entity.getStatus().name()); }
    public record UserSummary(UUID id, String displayName) {}
    public record OrganizationSummary(UUID id, String code, String name) {}
    public record CommitteeMembership(UUID id, UUID userId, UUID organizationId, String status) {}
}
