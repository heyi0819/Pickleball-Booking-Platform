package com.pickleball.booking.coach.infrastructure;

import com.pickleball.booking.coach.domain.CoachProfileApprovalStatus;
import com.pickleball.booking.shared.application.BusinessException;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "coach_profiles")
public class CoachProfileEntity {
    @Id private UUID id;
    @Column(name="organization_id", nullable=false) private UUID organizationId;
    @Column(name="user_id", nullable=false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(name="approval_status", nullable=false) private CoachProfileApprovalStatus approvalStatus;
    @Column(name="skill_level") private String skillLevel; private String bio;
    @Column(name="approved_by") private UUID approvedBy; @Column(name="approved_at") private Instant approvedAt;
    @Column(name="created_at", nullable=false) private Instant createdAt; @Column(name="updated_at", nullable=false) private Instant updatedAt;
    @Version private long version; @Column(name="deleted_at") private Instant deletedAt;
    protected CoachProfileEntity() { }
    public CoachProfileEntity(UUID organizationId, UUID userId, String skillLevel, String bio) { this.id=UUID.randomUUID(); this.organizationId=organizationId; this.userId=userId; this.skillLevel=skillLevel; this.bio=bio; this.approvalStatus=CoachProfileApprovalStatus.PENDING; }
    @PrePersist void created() { var now=Instant.now(); createdAt=now; updatedAt=now; }
    @PreUpdate void updated() { updatedAt=Instant.now(); }
    public UUID getId(){return id;} public UUID getOrganizationId(){return organizationId;} public UUID getUserId(){return userId;} public CoachProfileApprovalStatus getApprovalStatus(){return approvalStatus;}
    public void approve(UUID actor) { approvalStatus=CoachProfileApprovalStatus.APPROVED; approvedBy=actor; approvedAt=Instant.now(); }
    public void reject() { approvalStatus=CoachProfileApprovalStatus.REJECTED; }
    public void resubmit() { if(approvalStatus!=CoachProfileApprovalStatus.REJECTED) throw new BusinessException("STATE_TRANSITION_INVALID","Coach profile cannot be resubmitted"); approvalStatus=CoachProfileApprovalStatus.PENDING; approvedBy=null; approvedAt=null; }
}
