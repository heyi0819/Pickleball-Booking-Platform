package com.pickleball.booking.organization.domain;

import java.util.UUID;

public record Organization(UUID id, String code, String name, OrganizationStatus status) {
    public boolean isActive() { return status == OrganizationStatus.ACTIVE; }
}
