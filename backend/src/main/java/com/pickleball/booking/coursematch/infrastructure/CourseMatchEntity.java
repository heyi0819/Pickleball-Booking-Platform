package com.pickleball.booking.coursematch.infrastructure;

import com.pickleball.booking.coursematch.domain.CourseMatchStatus;
import com.pickleball.booking.shared.application.BusinessException;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "course_matches")
public class CourseMatchEntity {
    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "lesson_request_id")
    private UUID lessonRequestId;

    @Column(name = "participant_count", nullable = false)
    private short participantCount;

    @Column(name = "minimum_participants_snapshot")
    private Short minimumParticipantsSnapshot;

    @Column(name = "maximum_participants_snapshot")
    private Short maximumParticipantsSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CourseMatchStatus status;

    @Column(name = "decision_note")
    private String decisionNote;

    @Column(name = "confirmed_by")
    private UUID confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected CourseMatchEntity() {
    }

    public CourseMatchEntity(
            UUID organizationId,
            UUID lessonRequestId,
            short participantCount,
            Short minimumParticipantsSnapshot,
            Short maximumParticipantsSnapshot,
            UUID createdBy) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.lessonRequestId = lessonRequestId;
        this.minimumParticipantsSnapshot = minimumParticipantsSnapshot;
        this.maximumParticipantsSnapshot = maximumParticipantsSnapshot;
        this.createdBy = createdBy;
        this.status = CourseMatchStatus.DRAFT;
        setParticipantCount(participantCount);
    }

    @PrePersist
    void created() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void updated() {
        updatedAt = Instant.now();
    }

    public void updateParticipantCount(short participantCount) {
        requireDraft();
        setParticipantCount(participantCount);
    }

    public void requireDraft() {
        if (status != CourseMatchStatus.DRAFT) {
            throw new BusinessException("STATE_TRANSITION_INVALID", "Only a draft course match can be edited");
        }
    }

    public boolean participantCountValid() {
        if (minimumParticipantsSnapshot != null && participantCount < minimumParticipantsSnapshot) {
            return false;
        }
        return maximumParticipantsSnapshot == null || participantCount <= maximumParticipantsSnapshot;
    }

    private void setParticipantCount(short participantCount) {
        if (participantCount <= 0) {
            throw new BusinessException("VALIDATION_FAILED", "Participant count must be positive");
        }
        if (maximumParticipantsSnapshot != null && participantCount > maximumParticipantsSnapshot) {
            throw new BusinessException("PARTICIPANT_ABOVE_MAX", "Participant count is above the approved maximum");
        }
        this.participantCount = participantCount;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getLessonRequestId() { return lessonRequestId; }
    public short getParticipantCount() { return participantCount; }
    public Short getMinimumParticipantsSnapshot() { return minimumParticipantsSnapshot; }
    public Short getMaximumParticipantsSnapshot() { return maximumParticipantsSnapshot; }
    public CourseMatchStatus getStatus() { return status; }
    public String getDecisionNote() { return decisionNote; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
