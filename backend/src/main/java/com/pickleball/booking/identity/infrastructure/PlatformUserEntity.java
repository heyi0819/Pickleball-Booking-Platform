package com.pickleball.booking.identity.infrastructure;

import com.pickleball.booking.identity.domain.UserStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "users")
public class PlatformUserEntity {
    @Id private UUID id;
    @Column(name = "display_name", nullable = false) private String displayName;
    private String email;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private UserStatus status = UserStatus.ACTIVE;
    @Column(nullable = false) private String locale = "zh-TW";
    @Column(name = "last_login_at") private Instant lastLoginAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;
    @Column(name = "deleted_at") private Instant deletedAt;
    protected PlatformUserEntity() {}
    public PlatformUserEntity(UUID id, String displayName) { this.id = id; this.displayName = displayName; }
    @PrePersist void create() { var now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void update() { updatedAt = Instant.now(); }
    public UUID getId() { return id; } public String getDisplayName() { return displayName; } public String getEmail() { return email; } public String getLocale() { return locale; } public UserStatus getStatus() { return status; }
    public void updateProfile(String displayName, String email, String locale) { this.displayName = displayName; this.email = email; this.locale = locale; }
    public void recordLogin() { lastLoginAt = Instant.now(); }
    public void changeStatus(UserStatus status) { this.status = status; }
}
