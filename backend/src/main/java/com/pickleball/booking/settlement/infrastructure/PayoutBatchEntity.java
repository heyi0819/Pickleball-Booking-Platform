package com.pickleball.booking.settlement.infrastructure;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "payout_batches")
public class PayoutBatchEntity {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "batch_no", nullable = false, length = 30) private String batchNo;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "payout_date") private LocalDate payoutDate;
    @Column(length = 20) private String method;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2) private BigDecimal totalAmount;
    @Column(name = "item_count", nullable = false) private int itemCount;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "approved_by") private UUID approvedBy;
    @Column(name = "approved_at") private Instant approvedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected PayoutBatchEntity() {}

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
    public String getBatchNo() { return batchNo; }
    public String getStatus() { return status; }
    public LocalDate getPayoutDate() { return payoutDate; }
    public String getMethod() { return method; }
    public String getCurrency() { return currency; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public int getItemCount() { return itemCount; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getApprovedBy() { return approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public long getVersion() { return version; }
}
