package com.pickleball.booking.offering.infrastructure;

import com.pickleball.booking.offering.domain.CourseOffering;
import com.pickleball.booking.offering.domain.CourseOfferingStatus;
import com.pickleball.booking.offering.domain.OfferingBillingMode;
import com.pickleball.booking.offering.domain.OfferingScheduleType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "course_offerings")
public class CourseOfferingEntity {
    @Id private UUID id;
    @Column(name="organization_id", nullable=false) private UUID organizationId;
    @Column(name="coach_profile_id", nullable=false) private UUID coachProfileId;
    @Column(nullable=false, length=200) private String title;
    @Column(columnDefinition="text") private String description;
    @Column(name="lesson_type", nullable=false, length=20) private String lessonType;
    @Enumerated(EnumType.STRING) @Column(name="schedule_type", nullable=false, length=20) private OfferingScheduleType scheduleType;
    @Enumerated(EnumType.STRING) @Column(name="billing_mode", nullable=false, length=20) private OfferingBillingMode billingMode;
    @Column(name="skill_level", length=30) private String skillLevel;
    @Column(name="minimum_participants", nullable=false) private short minimumParticipants;
    @Column(name="maximum_participants", nullable=false) private short maximumParticipants;
    @Column(name="registration_open_at", nullable=false) private Instant registrationOpenAt;
    @Column(name="registration_close_at", nullable=false) private Instant registrationCloseAt;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private CourseOfferingStatus status;
    @Column(name="published_by") private UUID publishedBy;
    @Column(name="published_at") private Instant publishedAt;
    @Column(name="closed_by") private UUID closedBy;
    @Column(name="closed_at") private Instant closedAt;
    @Column(name="confirmed_by") private UUID confirmedBy;
    @Column(name="confirmed_at") private Instant confirmedAt;
    @Column(name="cancelled_by") private UUID cancelledBy;
    @Column(name="cancelled_at") private Instant cancelledAt;
    @Column(name="cancel_reason", columnDefinition="text") private String cancelReason;
    @Column(name="created_by", nullable=false) private UUID createdBy;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    @Version private long version;

    protected CourseOfferingEntity() { }

    public CourseOfferingEntity(CourseOffering offering) {
        id = offering.id();
        organizationId = offering.organizationId();
        createdBy = offering.createdBy();
        apply(offering);
    }

    @PrePersist void prePersist(){ Instant now=Instant.now(); if(createdAt==null) createdAt=now; updatedAt=now; }
    @PreUpdate void preUpdate(){ updatedAt=Instant.now(); }

    public void apply(CourseOffering offering) {
        var spec = offering.spec();
        coachProfileId = spec.coachProfileId();
        title = spec.title();
        description = spec.description();
        lessonType = "GROUP";
        scheduleType = spec.scheduleType();
        billingMode = spec.billingMode();
        skillLevel = spec.skillLevel();
        minimumParticipants = (short) spec.minimumParticipants();
        maximumParticipants = (short) spec.maximumParticipants();
        registrationOpenAt = spec.registrationOpenAt();
        registrationCloseAt = spec.registrationCloseAt();
        status = offering.status();
        publishedBy = offering.publishedBy(); publishedAt = offering.publishedAt();
        closedBy = offering.closedBy(); closedAt = offering.closedAt();
        confirmedBy = offering.confirmedBy(); confirmedAt = offering.confirmedAt();
        cancelledBy = offering.cancelledBy(); cancelledAt = offering.cancelledAt(); cancelReason = offering.cancelReason();
    }

    public UUID getId(){return id;} public UUID getOrganizationId(){return organizationId;} public UUID getCoachProfileId(){return coachProfileId;}
    public String getTitle(){return title;} public String getDescription(){return description;} public OfferingScheduleType getScheduleType(){return scheduleType;}
    public OfferingBillingMode getBillingMode(){return billingMode;} public String getSkillLevel(){return skillLevel;} public short getMinimumParticipants(){return minimumParticipants;}
    public short getMaximumParticipants(){return maximumParticipants;} public Instant getRegistrationOpenAt(){return registrationOpenAt;} public Instant getRegistrationCloseAt(){return registrationCloseAt;}
    public CourseOfferingStatus getStatus(){return status;} public UUID getPublishedBy(){return publishedBy;} public Instant getPublishedAt(){return publishedAt;}
    public UUID getClosedBy(){return closedBy;} public Instant getClosedAt(){return closedAt;} public UUID getConfirmedBy(){return confirmedBy;} public Instant getConfirmedAt(){return confirmedAt;}
    public UUID getCancelledBy(){return cancelledBy;} public Instant getCancelledAt(){return cancelledAt;} public String getCancelReason(){return cancelReason;} public UUID getCreatedBy(){return createdBy;}
}
