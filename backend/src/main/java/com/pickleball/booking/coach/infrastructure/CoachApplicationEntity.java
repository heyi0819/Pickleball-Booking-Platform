package com.pickleball.booking.coach.infrastructure;

import com.pickleball.booking.coach.domain.CoachApplicationStatus;
import com.pickleball.booking.shared.application.BusinessException;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="coach_applications")
public class CoachApplicationEntity {
    @Id private UUID id; @Column(name="organization_id",nullable=false) private UUID organizationId; @Column(name="coach_profile_id",nullable=false) private UUID coachProfileId;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private CoachApplicationStatus status;
    @Column(name="application_note") private String applicationNote; @Column(name="submitted_at",nullable=false) private Instant submittedAt;
    @Column(name="reviewed_by") private UUID reviewedBy; @Column(name="reviewed_at") private Instant reviewedAt; @Column(name="review_note") private String reviewNote; @Column(name="created_at",nullable=false) private Instant createdAt;
    protected CoachApplicationEntity() { }
    public CoachApplicationEntity(UUID organizationId, UUID profileId, String note) { id=UUID.randomUUID(); this.organizationId=organizationId; coachProfileId=profileId; applicationNote=note; status=CoachApplicationStatus.SUBMITTED; submittedAt=Instant.now(); }
    @PrePersist void created(){createdAt=Instant.now();}
    public UUID getId(){return id;} public UUID getOrganizationId(){return organizationId;} public UUID getCoachProfileId(){return coachProfileId;} public CoachApplicationStatus getStatus(){return status;} public String getApplicationNote(){return applicationNote;} public Instant getSubmittedAt(){return submittedAt;} public UUID getReviewedBy(){return reviewedBy;} public Instant getReviewedAt(){return reviewedAt;} public String getReviewNote(){return reviewNote;}
    public void approve(UUID actor, String note) { decide(CoachApplicationStatus.APPROVED,actor,note); }
    public void reject(UUID actor, String note) { decide(CoachApplicationStatus.REJECTED,actor,note); }
    private void decide(CoachApplicationStatus outcome, UUID actor, String note) { if(status!=CoachApplicationStatus.SUBMITTED) throw new BusinessException("STATE_TRANSITION_INVALID","Coach application is already reviewed"); if(note==null || note.isBlank()) throw new BusinessException("VALIDATION_FAILED","Review note is required"); status=outcome; reviewedBy=actor; reviewedAt=Instant.now(); reviewNote=note.trim(); }
}
