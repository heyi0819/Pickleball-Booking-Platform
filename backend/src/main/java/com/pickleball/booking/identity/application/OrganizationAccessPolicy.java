package com.pickleball.booking.identity.application;

import com.pickleball.booking.identity.domain.*;
import java.util.Collection;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Application authorization boundary; callers must supply server-resolved assignments only. */
@Component
public class OrganizationAccessPolicy {
    public boolean permits(Collection<RoleAssignment> assignments, RoleCode requiredRole, UUID organizationId) {
        return assignments.stream().anyMatch(assignment -> assignment.isActive() &&
                ((assignment.roleCode() == RoleCode.PLATFORM_ADMIN && assignment.scope().isGlobal()) ||
                 (assignment.roleCode() == requiredRole && organizationId != null && organizationId.equals(assignment.scope().organizationId()))));
    }
}
