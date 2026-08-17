package com.pickleball.booking.organization.infrastructure;

import com.pickleball.booking.organization.domain.OrganizationStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity @Table(name = "organizations")
public class OrganizationEntity {
    @Id private UUID id;
    @Column(nullable = false, unique = true) private String code;
    @Column(nullable = false) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private OrganizationStatus status = OrganizationStatus.ACTIVE;
    @Column(nullable = false) private String timezone = "Asia/Taipei";
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable = false, columnDefinition = "char(3)") private String currency = "TWD";
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb", nullable = false) private Map<String, Object> settings = Map.of();
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;
    protected OrganizationEntity() {}
    public OrganizationEntity(String code, String name) { this.id = UUID.randomUUID(); this.code = code; this.name = name; }
    @PrePersist void create() { var now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void update() { updatedAt = Instant.now(); }
    public UUID getId() { return id; } public String getCode() { return code; } public String getName() { return name; } public OrganizationStatus getStatus() { return status; }
    public void changeStatus(OrganizationStatus status) { this.status = status; }
}
