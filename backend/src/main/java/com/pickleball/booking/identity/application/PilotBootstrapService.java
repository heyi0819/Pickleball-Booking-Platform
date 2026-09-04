package com.pickleball.booking.identity.application;

import com.pickleball.booking.identity.domain.RoleAssignmentStatus;
import com.pickleball.booking.identity.domain.RoleCode;
import com.pickleball.booking.identity.domain.UserStatus;
import com.pickleball.booking.identity.infrastructure.PlatformUserEntity;
import com.pickleball.booking.identity.infrastructure.PlatformUserRepository;
import com.pickleball.booking.identity.infrastructure.RoleAssignmentEntity;
import com.pickleball.booking.identity.infrastructure.RoleAssignmentRepository;
import com.pickleball.booking.shared.application.AuditOutboxService;
import com.pickleball.booking.shared.application.BusinessException;
import jakarta.transaction.Transactional;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Non-HTTP, one-time bootstrap operation. It is only invoked by the pilot-bootstrap profile command. */
@Service
public class PilotBootstrapService {
    private final PlatformUserRepository users;
    private final RoleAssignmentRepository roles;
    private final AuditOutboxService audit;
    public PilotBootstrapService(PlatformUserRepository users, RoleAssignmentRepository roles, AuditOutboxService audit) {
        this.users = users; this.roles = roles; this.audit = audit;
    }
    @Transactional
    public UUID grantInitialPlatformAdmin(UUID userId) {
        PlatformUserEntity user = users.findById(userId).orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Bootstrap user was not found"));
        if (user.getStatus() != UserStatus.ACTIVE) throw new BusinessException("AUTH_FORBIDDEN", "Bootstrap user is not active");
        if (roles.findByUserIdAndOrganizationIsNullAndRoleCode(userId, RoleCode.PLATFORM_ADMIN).isPresent()) {
            throw new BusinessException("PLATFORM_ADMIN_ALREADY_EXISTS", "Bootstrap user already has a platform administrator assignment");
        }
        boolean anyPlatformAdmin = roles.findAll().stream().anyMatch(role -> role.getRoleCode() == RoleCode.PLATFORM_ADMIN
                && role.getOrganization() == null && role.getStatus() == RoleAssignmentStatus.ACTIVE);
        if (anyPlatformAdmin) throw new BusinessException("PLATFORM_ADMIN_ALREADY_EXISTS", "An active platform administrator already exists");
        RoleAssignmentEntity assignment = roles.save(new RoleAssignmentEntity(user, null, RoleCode.PLATFORM_ADMIN));
        audit.recordAudit(null, userId, "PLATFORM_ADMIN_BOOTSTRAPPED", "RoleAssignment", assignment.getId(),
                "controlled pilot bootstrap", null, Map.of("role", RoleCode.PLATFORM_ADMIN.name()), null);
        return assignment.getId();
    }
}
