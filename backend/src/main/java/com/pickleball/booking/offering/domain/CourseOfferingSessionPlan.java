package com.pickleball.booking.offering.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CourseOfferingSessionPlan(
        UUID id,
        int sequenceNo,
        Instant startAt,
        Instant endAt,
        UUID venueId,
        String venueNameSnapshot,
        String venueAddressSnapshot) {

    public CourseOfferingSessionPlan {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(startAt, "startAt");
        Objects.requireNonNull(endAt, "endAt");
        if (sequenceNo <= 0) {
            throw new OfferingDomainException(
                    OfferingDomainError.INVALID_SESSION_PLAN,
                    "sequenceNo must be greater than zero");
        }
        if (!endAt.isAfter(startAt)) {
            throw new OfferingDomainException(
                    OfferingDomainError.INVALID_SESSION_PLAN,
                    "endAt must be after startAt");
        }
        if (venueNameSnapshot == null || venueNameSnapshot.isBlank()) {
            throw new OfferingDomainException(
                    OfferingDomainError.INVALID_SESSION_PLAN,
                    "venueNameSnapshot is required");
        }
        venueNameSnapshot = venueNameSnapshot.trim();
        venueAddressSnapshot = venueAddressSnapshot == null || venueAddressSnapshot.isBlank()
                ? null
                : venueAddressSnapshot.trim();
    }

    public boolean isFutureAt(Instant instant) {
        return startAt.isAfter(Objects.requireNonNull(instant, "instant"));
    }
}
