package com.pickleball.booking.offering.infrastructure;

import com.pickleball.booking.offering.domain.*;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class CourseOfferingPersistenceAdapter implements CourseOfferingRepository {
    private final CourseOfferingJpaRepository offerings;
    private final CourseOfferingSessionJpaRepository sessions;

    public CourseOfferingPersistenceAdapter(CourseOfferingJpaRepository offerings, CourseOfferingSessionJpaRepository sessions){
        this.offerings=offerings; this.sessions=sessions;
    }

    @Override public Optional<CourseOffering> findById(UUID offeringId){return offerings.findById(offeringId).map(this::toDomain);}
    public Optional<CourseOffering> findLockedById(UUID offeringId){return offerings.findLockedById(offeringId).map(this::toDomain);}

    @Override public CourseOffering save(CourseOffering offering){
        CourseOfferingEntity entity=offerings.findById(offering.id()).orElseGet(() -> new CourseOfferingEntity(offering));
        entity.apply(offering);
        offerings.saveAndFlush(entity);
        if(offering.status()==CourseOfferingStatus.DRAFT){
            sessions.deleteByCourseOfferingId(offering.id());
            sessions.flush();
            sessions.saveAll(offering.sessionPlans().stream().map(p -> new CourseOfferingSessionEntity(offering.organizationId(),offering.id(),p)).toList());
            sessions.flush();
        }
        return offering;
    }

    private CourseOffering toDomain(CourseOfferingEntity entity){
        var spec=new CourseOfferingDraftSpec(entity.getCoachProfileId(),entity.getTitle(),entity.getDescription(),entity.getScheduleType(),entity.getBillingMode(),entity.getSkillLevel(),entity.getMinimumParticipants(),entity.getMaximumParticipants(),entity.getRegistrationOpenAt(),entity.getRegistrationCloseAt());
        var plans=sessions.findByCourseOfferingIdOrderBySequenceNoAsc(entity.getId()).stream().map(CourseOfferingSessionEntity::toDomain).toList();
        return CourseOffering.rehydrate(entity.getId(),entity.getOrganizationId(),entity.getCreatedBy(),spec,plans,entity.getStatus(),entity.getPublishedBy(),entity.getPublishedAt(),entity.getClosedBy(),entity.getClosedAt(),entity.getConfirmedBy(),entity.getConfirmedAt(),entity.getCancelledBy(),entity.getCancelledAt(),entity.getCancelReason());
    }
}
