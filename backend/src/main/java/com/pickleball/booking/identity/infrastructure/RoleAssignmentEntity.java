package com.pickleball.booking.identity.infrastructure;

import com.pickleball.booking.identity.domain.RoleAssignmentStatus;
import com.pickleball.booking.identity.domain.RoleCode;
import com.pickleball.booking.organization.infrastructure.OrganizationEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "user_role_assignments")
public class RoleAssignmentEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = true) @JoinColumn(name = "organization_id") private OrganizationEntity organization;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private PlatformUserEntity user;
    @Enumerated(EnumType.STRING) @Column(name = "role_code", nullable = false) private RoleCode roleCode;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RoleAssignmentStatus status;
    @Column(name = "granted_at", nullable = false) private Instant grantedAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    protected RoleAssignmentEntity() {}
    public RoleAssignmentEntity(PlatformUserEntity user, OrganizationEntity organization, RoleCode roleCode) { this.id = UUID.randomUUID(); this.user = user; this.organization = organization; this.roleCode = roleCode; this.status = RoleAssignmentStatus.ACTIVE; this.grantedAt = Instant.now(); }
    public RoleCode getRoleCode() { return roleCode; } public RoleAssignmentStatus getStatus() { return status; } public OrganizationEntity getOrganization() { return organization; } public PlatformUserEntity getUser() { return user; }
    public void changeStatus(RoleAssignmentStatus status) { this.status = status; this.revokedAt = status == RoleAssignmentStatus.REVOKED ? Instant.now() : null; }
    public UUID getId() { return id; }
}
