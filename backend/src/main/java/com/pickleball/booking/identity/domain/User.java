package com.pickleball.booking.identity.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Domain behaviour used by identity use cases; persistence is deliberately kept outside this type. */
public final class User {
    private final UUID id;
    private UserStatus status;
    private UserProfile profile;
    private LineIdentity lineIdentity;
    private final List<RoleAssignment> roles = new ArrayList<>();

    public User(UUID id, UserStatus status, UserProfile profile) { this.id = id; this.status = status; this.profile = profile; }
    public void bindLineIdentity(LineIdentity identity) { if (lineIdentity != null && !lineIdentity.subject().equals(identity.subject())) throw new IllegalStateException("LINE identity is already bound"); lineIdentity = identity; }
    public void updateProfile(UserProfile updated) { profile = updated; }
    public void assignRole(RoleAssignment assignment) { if (!assignment.userId().equals(id)) throw new IllegalArgumentException("role belongs to another user"); roles.add(assignment); }
    public void revokeRole(RoleCode code) { roles.removeIf(role -> role.roleCode() == code); }
    public void deactivate(UserStatus newStatus) { if (newStatus == UserStatus.ACTIVE) throw new IllegalArgumentException("deactivate requires non-active status"); status = newStatus; }
    public boolean canAccessPlatform() { return status == UserStatus.ACTIVE; }
}
