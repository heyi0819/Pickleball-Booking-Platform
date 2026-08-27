package com.pickleball.booking.settlement.infrastructure;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "session_settlements")
public class SessionSettlementEntity {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "course_session_id", nullable = false) private UUID courseSessionId;
    @Column(name = "price_snapshot_id", nullable = false) private UUID priceSnapshotId;
    @Column(name = "gross_receivable", nullable = false, precision = 12, scale = 2) private BigDecimal grossReceivable;
    @Column(name = "venue_cost", nullable = false, precision = 12, scale = 2) private BigDecimal venueCost;
    @Column(name = "other_adjustment", nullable = false, precision = 12, scale = 2) private BigDecimal otherAdjustment;
    @Column(name = "distributable_amount", nullable = false, precision = 12, scale = 2) private BigDecimal distributableAmount;
    @Column(nullable = false, length = 30) private String status;
    @Column(name = "confirmed_by") private UUID confirmedBy;
    @Column(name = "confirmed_at") private Instant confirmedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected SessionSettlementEntity() {}

    @PrePersist
    void created() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate void updated() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getCourseSessionId() { return courseSessionId; }
    public UUID getPriceSnapshotId() { return priceSnapshotId; }
    public BigDecimal getGrossReceivable() { return grossReceivable; }
    public BigDecimal getVenueCost() { return venueCost; }
    public BigDecimal getOtherAdjustment() { return otherAdjustment; }
    public BigDecimal getDistributableAmount() { return distributableAmount; }
    public String getStatus() { return status; }
    public UUID getConfirmedBy() { return confirmedBy; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public long getVersion() { return version; }
}
