package com.pickleball.booking.identity.domain;

import com.pickleball.booking.identity.application.OrganizationAccessPolicy;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class IdentityDomainTest {
    @Test void profileNeedsAContactMethodToBeComplete() {
        assertThat(new UserProfile("Member", null, null, "zh-TW").isComplete()).isFalse();
        assertThat(new UserProfile("Member", "0912345678", null, "zh-TW").isComplete()).isTrue();
    }
    @Test void onlyPlatformAdminMayUseGlobalScope() {
        var userId = UUID.randomUUID();
        assertThatThrownBy(() -> new RoleAssignment(userId, RoleCode.STUDENT, RoleAssignmentStatus.ACTIVE, OrganizationScope.global())).isInstanceOf(IllegalArgumentException.class);
        assertThat(new RoleAssignment(userId, RoleCode.PLATFORM_ADMIN, RoleAssignmentStatus.ACTIVE, OrganizationScope.global()).isActive()).isTrue();
    }
    @Test void accessPolicyUsesServerResolvedScopeAndGlobalAdminException() {
        var organizationId = UUID.randomUUID();
        var assignments = List.of(new RoleAssignment(UUID.randomUUID(), RoleCode.COMMITTEE, RoleAssignmentStatus.ACTIVE, new OrganizationScope(organizationId)), new RoleAssignment(UUID.randomUUID(), RoleCode.PLATFORM_ADMIN, RoleAssignmentStatus.ACTIVE, OrganizationScope.global()));
        var policy = new OrganizationAccessPolicy();
        assertThat(policy.permits(assignments, RoleCode.COMMITTEE, organizationId)).isTrue();
        assertThat(policy.permits(assignments, RoleCode.COMMITTEE, UUID.randomUUID())).isTrue();
    }
}
