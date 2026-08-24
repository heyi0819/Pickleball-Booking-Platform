package com.pickleball.booking.offering.domain;

import java.util.Optional;
import java.util.UUID;

public interface CourseOfferingPriceSnapshotRepository {
    Optional<CourseOfferingPriceSnapshot> findById(UUID snapshotId);

    Optional<CourseOfferingPriceSnapshot> findConfirmedByOfferingId(UUID offeringId);

    CourseOfferingPriceSnapshot save(CourseOfferingPriceSnapshot snapshot);
}
