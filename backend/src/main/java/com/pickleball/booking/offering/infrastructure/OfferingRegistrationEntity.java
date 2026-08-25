package com.pickleball.booking.offering.infrastructure;

import com.pickleball.booking.offering.domain.OfferingRegistration;
import com.pickleball.booking.offering.domain.OfferingRegistrationStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="course_offering_registrations")
public class OfferingRegistrationEntity {
    @Id private UUID id;
    @Column(name="organization_id",nullable=false) private UUID organizationId;
    @Column(name="course_offering_id",nullable=false) private UUID courseOfferingId;
    @Column(name="user_id",nullable=false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private OfferingRegistrationStatus status;
    @Column(name="registered_at",nullable=false) private Instant registeredAt;
    @Column(name="cancelled_at") private Instant cancelledAt;
    @Column(name="cancel_reason",columnDefinition="text") private String cancelReason;
    @Column(name="converted_course_membership_id") private UUID convertedCourseMembershipId;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Version private long version;

    protected OfferingRegistrationEntity() { }
    public OfferingRegistrationEntity(OfferingRegistration registration){id=registration.id(); organizationId=registration.organizationId(); courseOfferingId=registration.courseOfferingId(); userId=registration.userId(); registeredAt=registration.registeredAt(); apply(registration);}
    @PrePersist void prePersist(){Instant now=Instant.now(); if(createdAt==null) createdAt=now; updatedAt=now;}
    @PreUpdate void preUpdate(){updatedAt=Instant.now();}
    public void apply(OfferingRegistration registration){status=registration.status(); cancelledAt=registration.cancelledAt(); cancelReason=registration.cancelReason(); convertedCourseMembershipId=registration.convertedCourseMembershipId();}
    public OfferingRegistration toDomain(){return OfferingRegistration.rehydrate(id,organizationId,courseOfferingId,userId,registeredAt,status,cancelledAt,cancelReason,convertedCourseMembershipId);}
    public UUID getId(){return id;} public UUID getOrganizationId(){return organizationId;} public UUID getCourseOfferingId(){return courseOfferingId;} public UUID getUserId(){return userId;}
    public OfferingRegistrationStatus getStatus(){return status;} public Instant getRegisteredAt(){return registeredAt;} public UUID getConvertedCourseMembershipId(){return convertedCourseMembershipId;}
}
