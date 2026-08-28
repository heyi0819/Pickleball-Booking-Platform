package com.pickleball.booking.settlement.infrastructure;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "coach_settlements")
public class CoachSettlementEntity {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "session_settlement_id", nullable = false) private UUID sessionSettlementId;
    @Column(name = "coach_assignment_id", nullable = false) private UUID coachAssignmentId;
    @Column(name = "coach_profile_id", nullable = false) private UUID coachProfileId;
    @Column(name = "allocation_type", nullable = false, length = 20) private String allocationType;
    @Column(name = "allocation_value", precision = 12, scale = 4) private BigDecimal allocationValue;
    @Column(name = "payable_amount", nullable = false, precision = 12, scale = 2) private BigDecimal payableAmount;
    @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2) private BigDecimal paidAmount;
    @Column(name = "payout_status", nullable = false, length = 30) private String payoutStatus;
    @Column(name = "ready_at") private Instant readyAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected CoachSettlementEntity() {}

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
    public UUID getSessionSettlementId() { return sessionSettlementId; }
    public UUID getCoachAssignmentId() { return coachAssignmentId; }
    public UUID getCoachProfileId() { return coachProfileId; }
    public String getAllocationType() { return allocationType; }
    public BigDecimal getAllocationValue() { return allocationValue; }
    public BigDecimal getPayableAmount() { return payableAmount; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public String getPayoutStatus() { return payoutStatus; }
    public Instant getReadyAt() { return readyAt; }
    public long getVersion() { return version; }
}
