package com.pickleball.booking.course.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class SessionChangeRequest {
    public enum Type {
        RESCHEDULE,
        CHANGE_COACH,
        CHANGE_VENUE,
        COACH_LEAVE
    }

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED,
        WITHDRAWN
    }

    private final UUID id;
    private final UUID organizationId;
    private final UUID courseSessionId;
    private final Type type;
    private final UUID requestedBy;
    private final String reason;
    private final Instant proposedStartAt;
    private final Instant proposedEndAt;
    private final UUID proposedCoachProfileId;
    private final UUID proposedVenueId;
    private final Instant createdAt;

    private Status status;
    private UUID decidedBy;
    private Instant decidedAt;
    private String decisionReason;

    private SessionChangeRequest(
            UUID id,
            UUID organizationId,
            UUID courseSessionId,
            Type type,
            UUID requestedBy,
            String reason,
            Instant proposedStartAt,
            Instant proposedEndAt,
            UUID proposedCoachProfileId,
            UUID proposedVenueId,
            Status status,
            UUID decidedBy,
            Instant decidedAt,
            String decisionReason,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.courseSessionId = Objects.requireNonNull(courseSessionId, "courseSessionId");
        this.type = Objects.requireNonNull(type, "type");
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy");
        this.reason = requiredText(reason, "reason");
        this.proposedStartAt = proposedStartAt;
        this.proposedEndAt = proposedEndAt;
        this.proposedCoachProfileId = proposedCoachProfileId;
        this.proposedVenueId = proposedVenueId;
        this.status = Objects.requireNonNull(status, "status");
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
        this.decisionReason = normalizeOptional(decisionReason);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        validateProposal();
        validatePersistedLifecycle();
    }

    public static SessionChangeRequest createPending(
            UUID id,
            UUID organizationId,
            UUID courseSessionId,
            Type type,
            UUID requestedBy,
            String reason,
            Instant proposedStartAt,
            Instant proposedEndAt,
            UUID proposedCoachProfileId,
            UUID proposedVenueId,
            Instant createdAt) {
        return new SessionChangeRequest(
                id, organizationId, courseSessionId, type, requestedBy, reason,
                proposedStartAt, proposedEndAt, proposedCoachProfileId, proposedVenueId,
                Status.PENDING, null, null, null, createdAt);
    }

    public static SessionChangeRequest createApprovedDirectReschedule(
            UUID id,
            UUID organizationId,
            UUID courseSessionId,
            UUID committeeUserId,
            String reason,
            Instant proposedStartAt,
            Instant proposedEndAt,
            Instant decidedAt) {
        String normalizedReason = requiredText(reason, "reason");
        return new SessionChangeRequest(
                id, organizationId, courseSessionId, Type.RESCHEDULE, committeeUserId, normalizedReason,
                proposedStartAt, proposedEndAt, null, null,
                Status.APPROVED, committeeUserId, decidedAt, normalizedReason, decidedAt);
    }

    public static SessionChangeRequest rehydrate(
            UUID id,
            UUID organizationId,
            UUID courseSessionId,
            Type type,
            UUID requestedBy,
            String reason,
            Instant proposedStartAt,
            Instant proposedEndAt,
            UUID proposedCoachProfileId,
            UUID proposedVenueId,
            Status status,
            UUID decidedBy,
            Instant decidedAt,
            String decisionReason,
            Instant createdAt) {
        return new SessionChangeRequest(
                id, organizationId, courseSessionId, type, requestedBy, reason,
                proposedStartAt, proposedEndAt, proposedCoachProfileId, proposedVenueId,
                status, decidedBy, decidedAt, decisionReason, createdAt);
    }

    public void approve(UUID decisionActor, Instant decidedAt, String decisionReason) {
        decide(Status.APPROVED, decisionActor, decidedAt, decisionReason);
    }

    public void reject(UUID decisionActor, Instant decidedAt, String decisionReason) {
        decide(Status.REJECTED, decisionActor, decidedAt, decisionReason);
    }

    public void withdraw(UUID actorUserId) {
        requireState(Status.PENDING);
        if (!requestedBy.equals(actorUserId)) {
            throw invalid(CourseOperationsDomainError.ACTOR_NOT_ALLOWED,
                    "only the requester can withdraw a pending session change request");
        }
        status = Status.WITHDRAWN;
    }

    private void decide(Status target, UUID actor, Instant at, String reason) {
        requireState(Status.PENDING);
        UUID validatedActor = Objects.requireNonNull(actor, "decisionActor");
        Instant validatedAt = Objects.requireNonNull(at, "decidedAt");
        if (validatedAt.isBefore(createdAt)) {
            throw invalid(CourseOperationsDomainError.INVALID_STATE,
                    "decidedAt cannot be before request creation");
        }
        String validatedReason = requiredText(reason, "decisionReason");
        decidedBy = validatedActor;
        decidedAt = validatedAt;
        decisionReason = validatedReason;
        status = target;
    }

    private void validateProposal() {
        switch (type) {
            case RESCHEDULE -> {
                if (proposedStartAt == null || proposedEndAt == null || !proposedStartAt.isBefore(proposedEndAt)) {
                    throw invalid(CourseOperationsDomainError.INVALID_CHANGE_PROPOSAL,
                            "RESCHEDULE requires a valid proposed time range");
                }
                if (!proposedStartAt.isAfter(createdAt)) {
                    throw invalid(CourseOperationsDomainError.INVALID_CHANGE_PROPOSAL,
                            "RESCHEDULE proposed start must be after request creation");
                }
            }
            case CHANGE_COACH -> {
                if (proposedCoachProfileId == null) {
                    throw invalid(CourseOperationsDomainError.INVALID_CHANGE_PROPOSAL,
                            "CHANGE_COACH requires proposedCoachProfileId");
                }
            }
            case CHANGE_VENUE -> {
                if (proposedVenueId == null) {
                    throw invalid(CourseOperationsDomainError.INVALID_CHANGE_PROPOSAL,
                            "CHANGE_VENUE requires proposedVenueId");
                }
            }
            case COACH_LEAVE -> {
                // The replacement coach is a committee/application decision, not required when leave is requested.
            }
        }
    }

    private void validatePersistedLifecycle() {
        if ((status == Status.APPROVED || status == Status.REJECTED)
                && (decidedBy == null || decidedAt == null || decisionReason == null)) {
            throw invalidState("decided session change requires actor, time and reason");
        }
        if (decidedAt != null && decidedAt.isBefore(createdAt)) {
            throw invalidState("decidedAt cannot be before request creation");
        }
    }

    private void requireState(Status expected) {
        if (status != expected) {
            throw invalidState("expected state " + expected + " but was " + status);
        }
    }

    private static String requiredText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw invalid(CourseOperationsDomainError.REQUIRED_FIELD, name + " is required");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static CourseOperationsDomainException invalidState(String message) {
        return invalid(CourseOperationsDomainError.INVALID_STATE, message);
    }

    private static CourseOperationsDomainException invalid(CourseOperationsDomainError error, String message) {
        return new CourseOperationsDomainException(error, message);
    }

    public UUID id() { return id; }
    public UUID organizationId() { return organizationId; }
    public UUID courseSessionId() { return courseSessionId; }
    public Type type() { return type; }
    public UUID requestedBy() { return requestedBy; }
    public String reason() { return reason; }
    public Instant proposedStartAt() { return proposedStartAt; }
    public Instant proposedEndAt() { return proposedEndAt; }
    public UUID proposedCoachProfileId() { return proposedCoachProfileId; }
    public UUID proposedVenueId() { return proposedVenueId; }
    public Status status() { return status; }
    public UUID decidedBy() { return decidedBy; }
    public Instant decidedAt() { return decidedAt; }
    public String decisionReason() { return decisionReason; }
    public Instant createdAt() { return createdAt; }
}
