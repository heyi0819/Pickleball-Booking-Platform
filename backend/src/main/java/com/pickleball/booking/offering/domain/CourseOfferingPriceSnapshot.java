package com.pickleball.booking.offering.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class CourseOfferingPriceSnapshot {
    private final UUID id;
    private final UUID organizationId;
    private final UUID courseOfferingId;
    private final int versionNo;
    private final String currency;
    private final BigDecimal pricePerParticipant;
    private final Map<String, Object> ruleTrace;
    private final UUID createdBy;

    private OfferingPriceSnapshotStatus status;
    private UUID confirmedBy;
    private Instant confirmedAt;

    private CourseOfferingPriceSnapshot(
            UUID id,
            UUID organizationId,
            UUID courseOfferingId,
            int versionNo,
            String currency,
            BigDecimal pricePerParticipant,
            Map<String, Object> ruleTrace,
            UUID createdBy) {
        this.id = Objects.requireNonNull(id, "id");
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.courseOfferingId = Objects.requireNonNull(courseOfferingId, "courseOfferingId");
        if (versionNo < 1) {
            throw new OfferingDomainException(OfferingDomainError.INVALID_PRICE, "versionNo must be at least 1");
        }
        this.versionNo = versionNo;
        this.currency = normalizeCurrency(currency);
        this.pricePerParticipant = Objects.requireNonNull(pricePerParticipant, "pricePerParticipant");
        if (pricePerParticipant.signum() < 0) {
            throw new OfferingDomainException(
                    OfferingDomainError.INVALID_PRICE,
                    "pricePerParticipant must not be negative");
        }
        this.ruleTrace = ruleTrace == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(ruleTrace));
        this.createdBy = createdBy;
        this.status = OfferingPriceSnapshotStatus.DRAFT;
    }

    public static CourseOfferingPriceSnapshot createDraft(
            UUID id,
            UUID organizationId,
            UUID courseOfferingId,
            int versionNo,
            String currency,
            BigDecimal pricePerParticipant,
            Map<String, Object> ruleTrace,
            UUID createdBy) {
        return new CourseOfferingPriceSnapshot(
                id,
                organizationId,
                courseOfferingId,
                versionNo,
                currency,
                pricePerParticipant,
                ruleTrace,
                createdBy);
    }

    public void confirm(UUID actorUserId, Instant now) {
        requireStatus(OfferingPriceSnapshotStatus.DRAFT);
        confirmedBy = Objects.requireNonNull(actorUserId, "actorUserId");
        confirmedAt = Objects.requireNonNull(now, "now");
        status = OfferingPriceSnapshotStatus.CONFIRMED;
    }

    public void supersede() {
        requireStatus(OfferingPriceSnapshotStatus.CONFIRMED);
        status = OfferingPriceSnapshotStatus.SUPERSEDED;
    }

    private void requireStatus(OfferingPriceSnapshotStatus expected) {
        if (status != expected) {
            throw new OfferingDomainException(
                    OfferingDomainError.INVALID_STATE,
                    "expected price snapshot state " + expected + " but was " + status);
        }
    }

    private static String normalizeCurrency(String value) {
        if (value == null || value.isBlank() || value.trim().length() != 3) {
            throw new OfferingDomainException(
                    OfferingDomainError.INVALID_PRICE,
                    "currency must be a three-letter code");
        }
        return value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public UUID id() {
        return id;
    }

    public UUID organizationId() {
        return organizationId;
    }

    public UUID courseOfferingId() {
        return courseOfferingId;
    }

    public int versionNo() {
        return versionNo;
    }

    public OfferingPriceSnapshotStatus status() {
        return status;
    }

    public String currency() {
        return currency;
    }

    public BigDecimal pricePerParticipant() {
        return pricePerParticipant;
    }

    public Map<String, Object> ruleTrace() {
        return ruleTrace;
    }

    public UUID createdBy() {
        return createdBy;
    }

    public UUID confirmedBy() {
        return confirmedBy;
    }

    public Instant confirmedAt() {
        return confirmedAt;
    }
}
