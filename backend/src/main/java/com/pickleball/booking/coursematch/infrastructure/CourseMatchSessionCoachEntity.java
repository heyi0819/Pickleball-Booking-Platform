package com.pickleball.booking.coursematch.infrastructure;

import com.pickleball.booking.coursematch.domain.CourseMatchCoachStatus;
import com.pickleball.booking.shared.application.BusinessException;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "course_match_session_coaches")
public class CourseMatchSessionCoachEntity {
    @Id private UUID id;
    @Column(name = "course_match_session_id", nullable = false) private UUID courseMatchSessionId;
    @Column(name = "coach_profile_id", nullable = false) private UUID coachProfileId;
    @Column(name = "availability_proposal_id") private UUID availabilityProposalId;
    @Column(name = "assignment_order", nullable = false) private short assignmentOrder;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private CourseMatchCoachStatus status;
    @Column(name = "invitation_sent_at", nullable = false) private Instant invitationSentAt;
    @Column(name = "responded_at") private Instant respondedAt;
    @Column(name = "response_note") private String responseNote;
    @Column(name = "invited_by", nullable = false) private UUID invitedBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected CourseMatchSessionCoachEntity() {}

    public CourseMatchSessionCoachEntity(UUID sessionId, UUID coachProfileId, UUID availabilityProposalId,
            short assignmentOrder, UUID invitedBy) {
        if (sessionId == null || coachProfileId == null || invitedBy == null || assignmentOrder <= 0) {
            throw new BusinessException("VALIDATION_FAILED", "Coach assignment is incomplete");
        }
        this.id = UUID.randomUUID();
        this.courseMatchSessionId = sessionId;
        this.coachProfileId = coachProfileId;
        this.availabilityProposalId = availabilityProposalId;
        this.assignmentOrder = assignmentOrder;
        this.invitedBy = invitedBy;
        this.status = CourseMatchCoachStatus.INVITED;
    }

    @PrePersist
    void created() {
        Instant now = Instant.now();
        createdAt = now;
        if (invitationSentAt == null) invitationSentAt = now;
    }

    public void cancel() {
        if (status == CourseMatchCoachStatus.CANCELLED) return;
        if (status == CourseMatchCoachStatus.REJECTED) return;
        status = CourseMatchCoachStatus.CANCELLED;
    }

    public void accept(String note) {
        if (status != CourseMatchCoachStatus.INVITED) {
            throw new BusinessException("STATE_TRANSITION_INVALID", "Only an invited coach can accept");
        }
        status = CourseMatchCoachStatus.ACCEPTED;
        respondedAt = Instant.now();
        responseNote = blankToNull(note);
    }

    public void reject(String note) {
        if (status != CourseMatchCoachStatus.INVITED) {
            throw new BusinessException("STATE_TRANSITION_INVALID", "Only an invited coach can reject");
        }
        status = CourseMatchCoachStatus.REJECTED;
        respondedAt = Instant.now();
        responseNote = blankToNull(note);
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public UUID getId() { return id; }
    public UUID getCourseMatchSessionId() { return courseMatchSessionId; }
    public UUID getCoachProfileId() { return coachProfileId; }
    public UUID getAvailabilityProposalId() { return availabilityProposalId; }
    public short getAssignmentOrder() { return assignmentOrder; }
    public CourseMatchCoachStatus getStatus() { return status; }
    public Instant getInvitationSentAt() { return invitationSentAt; }
    public Instant getRespondedAt() { return respondedAt; }
    public String getResponseNote() { return responseNote; }
    public UUID getInvitedBy() { return invitedBy; }
}
