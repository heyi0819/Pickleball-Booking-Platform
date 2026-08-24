package com.pickleball.booking.coach.infrastructure;

import com.pickleball.booking.coach.domain.AvailabilityProposalStatus;
import com.pickleball.booking.shared.application.BusinessException;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="coach_availability_proposals")
public class AvailabilityProposalEntity {
    @Id private UUID id; @Column(name="organization_id",nullable=false) private UUID organizationId; @Column(name="coach_profile_id",nullable=false) private UUID coachProfileId;
    @Column(name="start_at",nullable=false) private Instant startAt; @Column(name="end_at",nullable=false) private Instant endAt; @Column(name="preferred_venue_id") private UUID preferredVenueId;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private AvailabilityProposalStatus status=AvailabilityProposalStatus.DRAFT;
    @Column(name="submitted_at") private Instant submittedAt; @Column(name="reviewed_by") private UUID reviewedBy; @Column(name="reviewed_at") private Instant reviewedAt; @Column(name="review_note") private String reviewNote;
    @Column(name="matched_at") private Instant matchedAt; @Column(name="created_at",nullable=false) private Instant createdAt; @Column(name="updated_at",nullable=false) private Instant updatedAt; @Version private long version;
    protected AvailabilityProposalEntity() { }
    public AvailabilityProposalEntity(UUID org, UUID profile, Instant start, Instant end, UUID venue) { id=UUID.randomUUID(); organizationId=org; coachProfileId=profile; changeTime(start,end,venue); }
    @PrePersist void created(){var now=Instant.now(); createdAt=now;updatedAt=now;} @PreUpdate void updated(){updatedAt=Instant.now();}
    public UUID getId(){return id;} public UUID getOrganizationId(){return organizationId;} public UUID getCoachProfileId(){return coachProfileId;} public Instant getStartAt(){return startAt;} public Instant getEndAt(){return endAt;} public UUID getPreferredVenueId(){return preferredVenueId;} public AvailabilityProposalStatus getStatus(){return status;} public Instant getSubmittedAt(){return submittedAt;} public UUID getReviewedBy(){return reviewedBy;} public Instant getReviewedAt(){return reviewedAt;} public String getReviewNote(){return reviewNote;}
    public void update(Instant start, Instant end, UUID venue) { if(status != AvailabilityProposalStatus.DRAFT) throw new BusinessException("STATE_TRANSITION_INVALID","Only a draft proposal can be updated"); changeTime(start,end,venue); }
    public void submit() { if(status != AvailabilityProposalStatus.DRAFT) throw new BusinessException("STATE_TRANSITION_INVALID","Only a draft proposal can be submitted"); if(!startAt.isAfter(Instant.now())) throw new BusinessException("BOOKING_TIME_NOT_FUTURE","Availability must be in the future"); status=AvailabilityProposalStatus.SUBMITTED; submittedAt=Instant.now(); }
    public void review(boolean approve, UUID actor, String note) { if(status != AvailabilityProposalStatus.SUBMITTED) throw new BusinessException("STATE_TRANSITION_INVALID","Only a submitted proposal can be reviewed"); if(note==null||note.isBlank()) throw new BusinessException("VALIDATION_FAILED","Review note is required"); status=approve?AvailabilityProposalStatus.APPROVED:AvailabilityProposalStatus.REJECTED; reviewedBy=actor;reviewedAt=Instant.now();reviewNote=note.trim(); }
    public void close() { if(status!=AvailabilityProposalStatus.APPROVED && status!=AvailabilityProposalStatus.REJECTED) throw new BusinessException("STATE_TRANSITION_INVALID","Only reviewed proposals can be closed"); status=AvailabilityProposalStatus.CLOSED; }
    private void changeTime(Instant start, Instant end, UUID venue) { if(start==null||end==null||!start.isBefore(end)) throw new BusinessException("VALIDATION_FAILED","startAt must be before endAt"); startAt=start;endAt=end;preferredVenueId=venue; }
}
