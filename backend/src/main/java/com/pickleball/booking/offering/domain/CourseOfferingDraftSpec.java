package com.pickleball.booking.offering.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CourseOfferingDraftSpec(
        UUID coachProfileId,
        String title,
        String description,
        OfferingScheduleType scheduleType,
        OfferingBillingMode billingMode,
        String skillLevel,
        int minimumParticipants,
        int maximumParticipants,
        Instant registrationOpenAt,
        Instant registrationCloseAt) {

    public CourseOfferingDraftSpec {
        Objects.requireNonNull(coachProfileId, "coachProfileId");
        Objects.requireNonNull(scheduleType, "scheduleType");
        Objects.requireNonNull(billingMode, "billingMode");
        Objects.requireNonNull(registrationOpenAt, "registrationOpenAt");
        Objects.requireNonNull(registrationCloseAt, "registrationCloseAt");
        title = normalizeRequired(title, "title");
        description = normalizeOptional(description);
        skillLevel = normalizeOptional(skillLevel);
        if (minimumParticipants <= 0 || maximumParticipants < minimumParticipants) {
            throw new OfferingDomainException(
                    OfferingDomainError.INVALID_CAPACITY,
                    "maximumParticipants must be greater than or equal to minimumParticipants > 0");
        }
        if (!registrationCloseAt.isAfter(registrationOpenAt)) {
            throw new OfferingDomainException(
                    OfferingDomainError.INVALID_REGISTRATION_WINDOW,
                    "registrationCloseAt must be after registrationOpenAt");
        }
    }

    private static String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new OfferingDomainException(
                    OfferingDomainError.REQUIRED_FIELD,
                    fieldName + " is required");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
