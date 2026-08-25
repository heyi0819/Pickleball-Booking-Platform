package com.pickleball.booking.course.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class CourseCancellationRequest {
    public enum RequesterRole {
        COACH
    }

    public enum Status {
        PENDING_REVIEW,
        APPROVED,
        REJECTED,
        WITHDRAWN
    }

    private final UUID id;
    private final UUID organizationId;
    private final UUID courseSessionId;
    private final UUID requestedBy;
    private final RequesterRole requesterRole;
    private final String reason;
    private final Instant createdAt;

    private Status status;
    private UUID reviewedBy;
    private Instant reviewedAt;
    private String reviewNote;

    private CourseCancellationRequest(
            UUID id,
            UUID organizationId,
            UUID courseSessionId,
            UUID requestedBy,
            RequesterRole requesterRole,
            String reason,
            Status status,
            UUID reviewedBy,
            Instant reviewedAt,
            String reviewNote,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.courseSessionId = Objects.requireNonNull(courseSessionId, "courseSessionId");
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy");
        this.requesterRole = Objects.requireNonNull(requesterRole, "requesterRole");
        this.reason = requiredText(reason, "reason");
        this.status = Objects.requireNonNull(status, "status");
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt;
        this.reviewNote = normalizeOptional(reviewNote);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        validatePersistedLifecycle();
    }

    public static CourseCancellationRequest createPending(
            UUID id,
            UUID organizationId,
            UUID courseSessionId,
            UUID requestedBy,
            String reason,
            Instant createdAt) {
        return new CourseCancellationRequest(
                id, organizationId, courseSessionId, requestedBy, RequesterRole.COACH,
                reason, Status.PENDING_REVIEW, null, null, null, createdAt);
    }

    public static CourseCancellationRequest rehydrate(
            UUID id,
            UUID organizationId,
            UUID courseSessionId,
            UUID requestedBy,
            RequesterRole requesterRole,
            String reason,
            Status status,
            UUID reviewedBy,
            Instant reviewedAt,
            String reviewNote,
            Instant createdAt) {
        return new CourseCancellationRequest(
                id, organizationId, courseSessionId, requestedBy, requesterRole,
                reason, status, reviewedBy, reviewedAt, reviewNote, createdAt);
    }

    public void approve(UUID reviewer, Instant reviewedAt, String reviewNote) {
        review(Status.APPROVED, reviewer, reviewedAt, reviewNote);
    }

    public void reject(UUID reviewer, Instant reviewedAt, String reviewNote) {
        review(Status.REJECTED, reviewer, reviewedAt, reviewNote);
    }

    public void withdraw(UUID actorUserId) {
        requireState(Status.PENDING_REVIEW);
        if (!requestedBy.equals(actorUserId)) {
            throw invalid(CourseOperationsDomainError.ACTOR_NOT_ALLOWED,
                    "only the requesting coach can withdraw a pending cancellation request");
        }
        status = Status.WITHDRAWN;
    }

    private void review(Status target, UUID reviewer, Instant reviewedAt, String note) {
        requireState(Status.PENDING_REVIEW);
        UUID validatedReviewer = Objects.requireNonNull(reviewer, "reviewer");
        Instant validatedReviewedAt = Objects.requireNonNull(reviewedAt, "reviewedAt");
        if (validatedReviewedAt.isBefore(createdAt)) {
            throw invalid(CourseOperationsDomainError.INVALID_STATE,
                    "reviewedAt cannot be before request creation");
        }
        String validatedNote = requiredText(note, "reviewNote");
        this.reviewedBy = validatedReviewer;
        this.reviewedAt = validatedReviewedAt;
        this.reviewNote = validatedNote;
        this.status = target;
    }

    private void validatePersistedLifecycle() {
        if ((status == Status.APPROVED || status == Status.REJECTED)
                && (reviewedBy == null || reviewedAt == null || reviewNote == null)) {
            throw invalidState("reviewed cancellation request requires reviewer, time and note");
        }
        if (reviewedAt != null && reviewedAt.isBefore(createdAt)) {
            throw invalidState("reviewedAt cannot be before request creation");
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
    public UUID requestedBy() { return requestedBy; }
    public RequesterRole requesterRole() { return requesterRole; }
    public String reason() { return reason; }
    public Status status() { return status; }
    public UUID reviewedBy() { return reviewedBy; }
    public Instant reviewedAt() { return reviewedAt; }
    public String reviewNote() { return reviewNote; }
    public Instant createdAt() { return createdAt; }
}
