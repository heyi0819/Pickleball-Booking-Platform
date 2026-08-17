package com.pickleball.booking.identity.domain;

import java.util.UUID;

public record OrganizationScope(UUID organizationId) {
    public static OrganizationScope global() { return new OrganizationScope(null); }
    public boolean isGlobal() { return organizationId == null; }
}
