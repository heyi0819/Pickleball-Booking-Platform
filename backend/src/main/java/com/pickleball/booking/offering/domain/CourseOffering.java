package com.pickleball.booking.offering.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class CourseOffering {
    private final UUID id;
    private final UUID organizationId;
    private final UUID createdBy;

    private CourseOfferingDraftSpec spec;
    private List<CourseOfferingSessionPlan> sessionPlans;
    private CourseOfferingStatus status;

    private UUID publishedBy;
    private Instant publishedAt;
    private UUID closedBy;
    private Instant closedAt;
    private UUID confirmedBy;
    private Instant confirmedAt;
    private UUID cancelledBy;
    private Instant cancelledAt;
    private String cancelReason;

    private CourseOffering(
            UUID id,
            UUID organizationId,
            UUID createdBy,
            CourseOfferingDraftSpec spec,
            List<CourseOfferingSessionPlan> sessionPlans) {
        this.id = Objects.requireNonNull(id, "id");
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.spec = Objects.requireNonNull(spec, "spec");
        this.sessionPlans = validatedSessionPlans(sessionPlans);
        this.status = CourseOfferingStatus.DRAFT;
    }

    public static CourseOffering createDraft(
            UUID id,
            UUID organizationId,
            UUID createdBy,
            CourseOfferingDraftSpec spec,
            List<CourseOfferingSessionPlan> sessionPlans) {
        return new CourseOffering(id, organizationId, createdBy, spec, sessionPlans);
    }

    public void reviseDraft(CourseOfferingDraftSpec revisedSpec) {
        requireState(CourseOfferingStatus.DRAFT);
        this.spec = Objects.requireNonNull(revisedSpec, "revisedSpec");
    }

    public void replaceSessionPlans(List<CourseOfferingSessionPlan> revisedSessionPlans) {
        requireState(CourseOfferingStatus.DRAFT);
        this.sessionPlans = validatedSessionPlans(revisedSessionPlans);
    }

    public void publish(UUID actorUserId, Instant now, PublicationReadiness readiness) {
        requireState(CourseOfferingStatus.DRAFT);
        requireActor(actorUserId);
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(readiness, "readiness");

        validatePublicationPlan(now);
        if (!spec.registrationCloseAt().isAfter(now)) {
            throw new OfferingDomainException(
                    OfferingDomainError.OFFERING_NOT_READY,
                    "registration window has already closed");
        }
        if (!readiness.isReady()) {
            throw new OfferingDomainException(
                    OfferingDomainError.OFFERING_NOT_READY,
                    "offering requires approved coach, confirmed price and held coach reservations before publication");
        }

        status = CourseOfferingStatus.OPEN;
        publishedBy = actorUserId;
        publishedAt = now;
    }

    public void close(UUID actorUserId, Instant now) {
        requireState(CourseOfferingStatus.OPEN);
        requireActor(actorUserId);
        Objects.requireNonNull(now, "now");
        status = CourseOfferingStatus.CLOSED;
        closedBy = actorUserId;
        closedAt = now;
    }

    public void confirm(UUID actorUserId, int activeRegistrationCount, Instant now) {
        requireState(CourseOfferingStatus.CLOSED);
        requireActor(actorUserId);
        Objects.requireNonNull(now, "now");
        if (activeRegistrationCount < spec.minimumParticipants()) {
            throw new OfferingDomainException(
                    OfferingDomainError.PARTICIPANT_BELOW_MIN,
                    "active registration count is below minimumParticipants");
        }
        if (activeRegistrationCount > spec.maximumParticipants()) {
            throw new OfferingDomainException(
                    OfferingDomainError.PARTICIPANT_ABOVE_MAX,
                    "active registration count exceeds maximumParticipants");
        }
        status = CourseOfferingStatus.CONFIRMED;
        confirmedBy = actorUserId;
        confirmedAt = now;
    }

    public void cancel(UUID actorUserId, Instant now, String reason) {
        requireActor(actorUserId);
        Objects.requireNonNull(now, "now");
        if (status == CourseOfferingStatus.CONFIRMED || status == CourseOfferingStatus.CANCELLED) {
            throw invalidState("confirmed or cancelled offering cannot be cancelled through the pre-course flow");
        }
        status = CourseOfferingStatus.CANCELLED;
        cancelledBy = actorUserId;
        cancelledAt = now;
        cancelReason = normalizeOptional(reason);
    }

    public boolean isRegistrationOpenAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return status == CourseOfferingStatus.OPEN
                && !now.isBefore(spec.registrationOpenAt())
                && now.isBefore(spec.registrationCloseAt());
    }

    private void validatePublicationPlan(Instant now) {
        if (sessionPlans.isEmpty()) {
            throw new OfferingDomainException(
                    OfferingDomainError.OFFERING_NOT_READY,
                    "at least one session plan is required before publication");
        }
        if (spec.scheduleType() == OfferingScheduleType.SINGLE && sessionPlans.size() != 1) {
            throw new OfferingDomainException(
                    OfferingDomainError.INVALID_SESSION_PLAN,
                    "SINGLE offering must contain exactly one session plan");
        }
        if (sessionPlans.stream().anyMatch(session -> !session.isFutureAt(now))) {
            throw new OfferingDomainException(
                    OfferingDomainError.INVALID_SESSION_PLAN,
                    "all offering sessions must still be in the future at publication time");
        }
    }

    private static List<CourseOfferingSessionPlan> validatedSessionPlans(List<CourseOfferingSessionPlan> plans) {
        List<CourseOfferingSessionPlan> copy = plans == null ? List.of() : List.copyOf(plans);
        Set<Integer> sequences = new HashSet<>();
        for (CourseOfferingSessionPlan plan : copy) {
            Objects.requireNonNull(plan, "session plan");
            if (!sequences.add(plan.sequenceNo())) {
                throw new OfferingDomainException(
                        OfferingDomainError.INVALID_SESSION_PLAN,
                        "session sequence numbers must be unique");
            }
        }
        return copy;
    }

    private void requireState(CourseOfferingStatus expected) {
        if (status != expected) {
            throw invalidState("expected state " + expected + " but was " + status);
        }
    }

    private static void requireActor(UUID actorUserId) {
        if (actorUserId == null) {
            throw new OfferingDomainException(OfferingDomainError.REQUIRED_FIELD, "actorUserId is required");
        }
    }

    private static OfferingDomainException invalidState(String message) {
        return new OfferingDomainException(OfferingDomainError.INVALID_STATE, message);
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID id() {
        return id;
    }

    public UUID organizationId() {
        return organizationId;
    }

    public UUID createdBy() {
        return createdBy;
    }

    public CourseOfferingDraftSpec spec() {
        return spec;
    }

    public List<CourseOfferingSessionPlan> sessionPlans() {
        return sessionPlans;
    }

    public CourseOfferingStatus status() {
        return status;
    }

    public UUID publishedBy() {
        return publishedBy;
    }

    public Instant publishedAt() {
        return publishedAt;
    }

    public UUID closedBy() {
        return closedBy;
    }

    public Instant closedAt() {
        return closedAt;
    }

    public UUID confirmedBy() {
        return confirmedBy;
    }

    public Instant confirmedAt() {
        return confirmedAt;
    }

    public UUID cancelledBy() {
        return cancelledBy;
    }

    public Instant cancelledAt() {
        return cancelledAt;
    }

    public String cancelReason() {
        return cancelReason;
    }
}
