package com.pickleball.booking.offering.infrastructure;

import com.pickleball.booking.offering.domain.CourseOfferingSessionPlan;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="course_offering_sessions")
public class CourseOfferingSessionEntity {
    @Id private UUID id;
    @Column(name="organization_id",nullable=false) private UUID organizationId;
    @Column(name="course_offering_id",nullable=false) private UUID courseOfferingId;
    @Column(name="sequence_no",nullable=false) private short sequenceNo;
    @Column(name="start_at",nullable=false) private Instant startAt;
    @Column(name="end_at",nullable=false) private Instant endAt;
    @Column(name="venue_id") private UUID venueId;
    @Column(name="venue_name_snapshot",nullable=false,length=150) private String venueNameSnapshot;
    @Column(name="venue_address_snapshot",length=300) private String venueAddressSnapshot;
    @Column(name="created_at",nullable=false) private Instant createdAt;

    protected CourseOfferingSessionEntity() { }
    public CourseOfferingSessionEntity(UUID organizationId, UUID courseOfferingId, CourseOfferingSessionPlan plan){
        this.id=plan.id(); this.organizationId=organizationId; this.courseOfferingId=courseOfferingId;
        this.sequenceNo=(short)plan.sequenceNo(); this.startAt=plan.startAt(); this.endAt=plan.endAt();
        this.venueId=plan.venueId(); this.venueNameSnapshot=plan.venueNameSnapshot(); this.venueAddressSnapshot=plan.venueAddressSnapshot();
    }
    @PrePersist void prePersist(){if(createdAt==null) createdAt=Instant.now();}
    public CourseOfferingSessionPlan toDomain(){return new CourseOfferingSessionPlan(id,sequenceNo,startAt,endAt,venueId,venueNameSnapshot,venueAddressSnapshot);}
    public UUID getId(){return id;} public UUID getOrganizationId(){return organizationId;} public UUID getCourseOfferingId(){return courseOfferingId;}
    public short getSequenceNo(){return sequenceNo;} public Instant getStartAt(){return startAt;} public Instant getEndAt(){return endAt;} public UUID getVenueId(){return venueId;}
    public String getVenueNameSnapshot(){return venueNameSnapshot;} public String getVenueAddressSnapshot(){return venueAddressSnapshot;}
}
