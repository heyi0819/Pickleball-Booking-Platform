package com.pickleball.booking.offering.infrastructure;

import com.pickleball.booking.offering.domain.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class OfferingRegistrationPersistenceAdapter implements OfferingRegistrationRepository {
    private final OfferingRegistrationJpaRepository registrations;
    public OfferingRegistrationPersistenceAdapter(OfferingRegistrationJpaRepository registrations){this.registrations=registrations;}
    @Override public Optional<OfferingRegistration> findById(UUID registrationId){return registrations.findById(registrationId).map(OfferingRegistrationEntity::toDomain);}
    public Optional<OfferingRegistration> findLockedById(UUID registrationId){return registrations.findLockedById(registrationId).map(OfferingRegistrationEntity::toDomain);}
    public boolean hasActive(UUID offeringId,UUID userId){return registrations.existsByCourseOfferingIdAndUserIdAndStatus(offeringId,userId,OfferingRegistrationStatus.ACTIVE);}
    public long activeCount(UUID offeringId){return registrations.countByCourseOfferingIdAndStatus(offeringId,OfferingRegistrationStatus.ACTIVE);}
    public List<OfferingRegistration> findLockedActiveByOfferingId(UUID offeringId){return registrations.findLockedByOfferingIdAndStatus(offeringId,OfferingRegistrationStatus.ACTIVE).stream().map(OfferingRegistrationEntity::toDomain).toList();}
    @Override public OfferingRegistration save(OfferingRegistration registration){var entity=registrations.findById(registration.id()).orElseGet(() -> new OfferingRegistrationEntity(registration)); entity.apply(registration); registrations.save(entity); return registration;}
}
