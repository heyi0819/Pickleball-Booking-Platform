package com.pickleball.booking.offering.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class OfferingRegistration {
    private final UUID id;
    private final UUID organizationId;
    private final UUID courseOfferingId;
    private final UUID userId;
    private final Instant registeredAt;

    private OfferingRegistrationStatus status;
    private Instant cancelledAt;
    private String cancelReason;
    private UUID convertedCourseMembershipId;

    private OfferingRegistration(
            UUID id,
            UUID organizationId,
            UUID courseOfferingId,
            UUID userId,
            Instant registeredAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.courseOfferingId = Objects.requireNonNull(courseOfferingId, "courseOfferingId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.registeredAt = Objects.requireNonNull(registeredAt, "registeredAt");
        this.status = OfferingRegistrationStatus.ACTIVE;
    }

    public static OfferingRegistration register(
            UUID id,
            UUID organizationId,
            UUID courseOfferingId,
            UUID studentUserId,
            Instant registeredAt) {
        return new OfferingRegistration(id, organizationId, courseOfferingId, studentUserId, registeredAt);
    }

    public static OfferingRegistration rehydrate(
            UUID id,
            UUID organizationId,
            UUID courseOfferingId,
            UUID userId,
            Instant registeredAt,
            OfferingRegistrationStatus status,
            Instant cancelledAt,
            String cancelReason,
            UUID convertedCourseMembershipId) {
        OfferingRegistration registration = new OfferingRegistration(
                id, organizationId, courseOfferingId, userId, registeredAt);
        registration.status = Objects.requireNonNull(status, "status");
        registration.cancelledAt = cancelledAt;
        registration.cancelReason = normalizeOptional(cancelReason);
        registration.convertedCourseMembershipId = convertedCourseMembershipId;
        if (status == OfferingRegistrationStatus.CANCELLED && cancelledAt == null) {
            throw new OfferingDomainException(
                    OfferingDomainError.INVALID_STATE, "cancelled registration lifecycle metadata is missing");
        }
        if (status == OfferingRegistrationStatus.CONVERTED && convertedCourseMembershipId == null) {
            throw new OfferingDomainException(
                    OfferingDomainError.INVALID_STATE, "converted registration membership lineage is missing");
        }
        return registration;
    }

    public void cancelByStudent(UUID actorUserId, Instant now, String reason) {
        requireActive();
        Objects.requireNonNull(now, "now");
        if (!userId.equals(actorUserId)) {
            throw new OfferingDomainException(
                    OfferingDomainError.REGISTRATION_ACTOR_FORBIDDEN,
                    "student can cancel only their own offering registration");
        }
        status = OfferingRegistrationStatus.CANCELLED;
        cancelledAt = now;
        cancelReason = normalizeOptional(reason);
    }

    public void markConverted(UUID courseMembershipId) {
        requireActive();
        convertedCourseMembershipId = Objects.requireNonNull(courseMembershipId, "courseMembershipId");
        status = OfferingRegistrationStatus.CONVERTED;
    }

    private void requireActive() {
        if (status != OfferingRegistrationStatus.ACTIVE) {
            throw new OfferingDomainException(
                    OfferingDomainError.INVALID_STATE,
                    "registration must be ACTIVE but was " + status);
        }
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID id() { return id; }
    public UUID organizationId() { return organizationId; }
    public UUID courseOfferingId() { return courseOfferingId; }
    public UUID userId() { return userId; }
    public Instant registeredAt() { return registeredAt; }
    public OfferingRegistrationStatus status() { return status; }
    public Instant cancelledAt() { return cancelledAt; }
    public String cancelReason() { return cancelReason; }
    public UUID convertedCourseMembershipId() { return convertedCourseMembershipId; }
}
