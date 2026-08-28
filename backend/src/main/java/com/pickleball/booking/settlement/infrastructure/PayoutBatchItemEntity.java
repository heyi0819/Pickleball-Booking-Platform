package com.pickleball.booking.settlement.infrastructure;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payout_batch_items")
public class PayoutBatchItemEntity {
    @Id private UUID id;
    @Column(name = "payout_batch_id", nullable = false) private UUID payoutBatchId;
    @Column(name = "coach_settlement_id", nullable = false) private UUID coachSettlementId;
    @Column(name = "coach_profile_id", nullable = false) private UUID coachProfileId;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "paid_at") private Instant paidAt;
    @Column(name = "processed_by") private UUID processedBy;
    @Column(name = "reference_no", length = 100) private String referenceNo;
    @Column(name = "failure_reason") private String failureReason;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected PayoutBatchItemEntity() {}

    @PrePersist
    void created() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate void updated() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getPayoutBatchId() { return payoutBatchId; }
    public UUID getCoachSettlementId() { return coachSettlementId; }
    public UUID getCoachProfileId() { return coachProfileId; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
    public Instant getPaidAt() { return paidAt; }
    public UUID getProcessedBy() { return processedBy; }
    public String getReferenceNo() { return referenceNo; }
    public String getFailureReason() { return failureReason; }
}
