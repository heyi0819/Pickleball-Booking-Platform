package com.pickleball.booking.offering.domain;

import java.util.Optional;
import java.util.UUID;

public interface OfferingRegistrationRepository {
    Optional<OfferingRegistration> findById(UUID registrationId);

    OfferingRegistration save(OfferingRegistration registration);
}
