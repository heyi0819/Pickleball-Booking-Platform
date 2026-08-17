package com.pickleball.booking.identity.infrastructure;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity @Table(name = "user_external_identities", uniqueConstraints = @UniqueConstraint(name = "uq_external_identity_provider_subject", columnNames = {"provider", "provider_subject"}))
public class ExternalIdentityEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private PlatformUserEntity user;
    @Column(nullable = false) private String provider;
    @Column(name = "provider_subject", nullable = false) private String providerSubject;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "profile_data", columnDefinition = "jsonb", nullable = false) private Map<String, String> profileData;
    @Column(name = "linked_at", nullable = false) private Instant linkedAt;
    @Column(name = "last_verified_at") private Instant lastVerifiedAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    protected ExternalIdentityEntity() {}
    public ExternalIdentityEntity(PlatformUserEntity user, String subject, Map<String, String> profileData) { this.id = UUID.randomUUID(); this.user = user; this.provider = "LINE"; this.providerSubject = subject; this.profileData = profileData; this.linkedAt = Instant.now(); this.lastVerifiedAt = linkedAt; }
    public PlatformUserEntity getUser() { return user; } public void verifiedNow() { lastVerifiedAt = Instant.now(); }
}
