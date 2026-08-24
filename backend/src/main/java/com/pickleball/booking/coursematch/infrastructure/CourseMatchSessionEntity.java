package com.pickleball.booking.coursematch.infrastructure;

import com.pickleball.booking.coursematch.domain.VenueSnapshotType;
import com.pickleball.booking.shared.application.BusinessException;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "course_match_sessions")
public class CourseMatchSessionEntity {
    @Id
    private UUID id;

    @Column(name = "course_match_id", nullable = false)
    private UUID courseMatchId;

    @Column(name = "session_index", nullable = false)
    private short sessionIndex;

    @Column(name = "scheduled_start_at", nullable = false)
    private Instant scheduledStartAt;

    @Column(name = "scheduled_end_at", nullable = false)
    private Instant scheduledEndAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "venue_snapshot_type", nullable = false)
    private VenueSnapshotType venueSnapshotType;

    @Column(name = "venue_snapshot_id")
    private UUID venueSnapshotId;

    @Column(name = "venue_snapshot_name", nullable = false)
    private String venueSnapshotName;

    @Column(name = "venue_snapshot_address")
    private String venueSnapshotAddress;

    @Column(name = "venue_fingerprint", nullable = false)
    private String venueFingerprint;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CourseMatchSessionEntity() {
    }

    public CourseMatchSessionEntity(
            UUID courseMatchId,
            short sessionIndex,
            Instant scheduledStartAt,
            Instant scheduledEndAt,
            VenueSnapshotType venueSnapshotType,
            UUID venueSnapshotId,
            String venueSnapshotName,
            String venueSnapshotAddress,
            String venueFingerprint) {
        this.id = UUID.randomUUID();
        this.courseMatchId = courseMatchId;
        this.sessionIndex = sessionIndex;
        updatePlan(scheduledStartAt, scheduledEndAt, venueSnapshotType, venueSnapshotId,
                venueSnapshotName, venueSnapshotAddress, venueFingerprint);
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

    public void updatePlan(
            Instant scheduledStartAt,
            Instant scheduledEndAt,
            VenueSnapshotType venueSnapshotType,
            UUID venueSnapshotId,
            String venueSnapshotName,
            String venueSnapshotAddress,
            String venueFingerprint) {
        if (scheduledStartAt == null || scheduledEndAt == null || !scheduledStartAt.isBefore(scheduledEndAt)) {
            throw new BusinessException("VALIDATION_FAILED", "Match session start must be before end");
        }
        if (venueSnapshotType == null || venueSnapshotName == null || venueSnapshotName.isBlank()
                || venueFingerprint == null || venueFingerprint.isBlank()) {
            throw new BusinessException("VALIDATION_FAILED", "Venue snapshot is incomplete");
        }
        if (venueSnapshotType == VenueSnapshotType.VENUE && venueSnapshotId == null) {
            throw new BusinessException("VALIDATION_FAILED", "Managed venue snapshot requires a venue id");
        }
        if (venueSnapshotType == VenueSnapshotType.OTHER && venueSnapshotId != null) {
            throw new BusinessException("VALIDATION_FAILED", "External venue snapshot cannot contain a venue id");
        }
        this.scheduledStartAt = scheduledStartAt;
        this.scheduledEndAt = scheduledEndAt;
        this.venueSnapshotType = venueSnapshotType;
        this.venueSnapshotId = venueSnapshotId;
        this.venueSnapshotName = venueSnapshotName.trim();
        this.venueSnapshotAddress = blankToNull(venueSnapshotAddress);
        this.venueFingerprint = venueFingerprint;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getId() { return id; }
    public UUID getCourseMatchId() { return courseMatchId; }
    public short getSessionIndex() { return sessionIndex; }
    public Instant getScheduledStartAt() { return scheduledStartAt; }
    public Instant getScheduledEndAt() { return scheduledEndAt; }
    public VenueSnapshotType getVenueSnapshotType() { return venueSnapshotType; }
    public UUID getVenueSnapshotId() { return venueSnapshotId; }
    public String getVenueSnapshotName() { return venueSnapshotName; }
    public String getVenueSnapshotAddress() { return venueSnapshotAddress; }
    public String getVenueFingerprint() { return venueFingerprint; }
}
