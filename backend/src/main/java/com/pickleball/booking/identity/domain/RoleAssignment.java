package com.pickleball.booking.identity.domain;

import java.util.UUID;

public record RoleAssignment(UUID userId, RoleCode roleCode, RoleAssignmentStatus status, OrganizationScope scope) {
    public RoleAssignment {
        if (roleCode == RoleCode.PLATFORM_ADMIN && !scope.isGlobal()) throw new IllegalArgumentException("PLATFORM_ADMIN must be global");
        if (roleCode != RoleCode.PLATFORM_ADMIN && scope.isGlobal()) throw new IllegalArgumentException("only PLATFORM_ADMIN can be global");
    }
    public boolean isActive() { return status == RoleAssignmentStatus.ACTIVE; }
}
