package com.pickleball.booking.shared.application;

import com.pickleball.booking.identity.domain.RoleAssignmentStatus;
import com.pickleball.booking.identity.domain.RoleCode;
import com.pickleball.booking.identity.infrastructure.RoleAssignmentRepository;
import com.pickleball.booking.organization.domain.OrganizationStatus;
import java.util.*;
import org.springframework.stereotype.Component;

/** Resolves the server-owned organization context. Slice 1 does not yet expose a client-selectable org header. */
@Component
public class OrganizationScopeResolver {
    private final RoleAssignmentRepository roles;
    public OrganizationScopeResolver(RoleAssignmentRepository roles) { this.roles=roles; }
    public UUID activeOrganizationFor(UUID userId, RoleCode preferredRole) {
        var candidates=roles.findByUserId(userId).stream().filter(r -> r.getStatus()==RoleAssignmentStatus.ACTIVE && r.getOrganization()!=null && r.getOrganization().getStatus()==OrganizationStatus.ACTIVE).filter(r -> r.getRoleCode()==preferredRole).map(r -> r.getOrganization().getId()).distinct().toList();
        if(candidates.size()==1) return candidates.getFirst();
        if(candidates.isEmpty()) throw new BusinessException("ORG_SCOPE_DENIED","No active organization scope is available");
        throw new BusinessException("ORG_SCOPE_DENIED","Organization context is ambiguous");
    }
}
