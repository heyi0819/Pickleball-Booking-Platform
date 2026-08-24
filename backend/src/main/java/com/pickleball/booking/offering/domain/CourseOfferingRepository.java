package com.pickleball.booking.offering.domain;

import java.util.Optional;
import java.util.UUID;

public interface CourseOfferingRepository {
    Optional<CourseOffering> findById(UUID offeringId);

    CourseOffering save(CourseOffering offering);
}
