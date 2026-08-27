package com.pickleball.booking.settlement.infrastructure;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "settlement_adjustments")
public class SettlementAdjustmentEntity {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "session_settlement_id", nullable = false) private UUID sessionSettlementId;
    @Column(name = "adjustment_type", nullable = false, length = 30) private String adjustmentType;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 10) private String direction;
    @Column(name = "before_amount", nullable = false, precision = 12, scale = 2) private BigDecimal beforeAmount;
    @Column(name = "after_amount", nullable = false, precision = 12, scale = 2) private BigDecimal afterAmount;
    @Column(name = "handling_method", length = 30) private String handlingMethod;
    @Column(nullable = false) private String reason;
    @Column(name = "approved_by", nullable = false) private UUID approvedBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected SettlementAdjustmentEntity() {}

    @PrePersist
    void created() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getSessionSettlementId() { return sessionSettlementId; }
    public String getAdjustmentType() { return adjustmentType; }
    public BigDecimal getAmount() { return amount; }
    public String getDirection() { return direction; }
    public BigDecimal getBeforeAmount() { return beforeAmount; }
    public BigDecimal getAfterAmount() { return afterAmount; }
    public String getHandlingMethod() { return handlingMethod; }
    public String getReason() { return reason; }
    public UUID getApprovedBy() { return approvedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
