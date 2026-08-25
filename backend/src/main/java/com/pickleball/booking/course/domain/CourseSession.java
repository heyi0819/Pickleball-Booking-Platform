package com.pickleball.booking.course.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class CourseSession {
    public enum Status {
        SCHEDULED,
        CANCEL_PENDING,
        CANCELLED,
        COMPLETED,
        POSTPONED
    }

    public enum CancellationSource {
        STUDENT,
        COACH,
        COMMITTEE,
        SYSTEM
    }

    private final UUID id;
    private final UUID organizationId;
    private final UUID courseId;
    private final int sequenceNo;
    private final int expectedParticipantCount;
    private final int guestParticipantCount;
    private final long version;

    private Instant scheduledStartAt;
    private Instant scheduledEndAt;
    private Integer actualParticipantCount;
    private Status status;
    private CancellationSource cancellationSource;
    private String cancellationNote;
    private Instant completedAt;

    private CourseSession(
            UUID id,
            UUID organizationId,
            UUID courseId,
            int sequenceNo,
            Instant scheduledStartAt,
            Instant scheduledEndAt,
            int expectedParticipantCount,
            int guestParticipantCount,
            Integer actualParticipantCount,
            Status status,
            CancellationSource cancellationSource,
            String cancellationNote,
            Instant completedAt,
            long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.courseId = Objects.requireNonNull(courseId, "courseId");
        if (sequenceNo <= 0) {
            throw invalid(CourseOperationsDomainError.REQUIRED_FIELD, "sequenceNo must be positive");
        }
        validateTimeRange(scheduledStartAt, scheduledEndAt);
        if (expectedParticipantCount <= 0 || guestParticipantCount < 0
                || (actualParticipantCount != null && actualParticipantCount < 0)) {
            throw invalid(CourseOperationsDomainError.INVALID_PARTICIPANT_COUNT,
                    "participant counts must be non-negative and expectedParticipantCount must be positive");
        }
        if (version < 0) {
            throw invalid(CourseOperationsDomainError.REQUIRED_FIELD, "version must not be negative");
        }
        this.sequenceNo = sequenceNo;
        this.scheduledStartAt = scheduledStartAt;
        this.scheduledEndAt = scheduledEndAt;
        this.expectedParticipantCount = expectedParticipantCount;
        this.guestParticipantCount = guestParticipantCount;
        this.actualParticipantCount = actualParticipantCount;
        this.status = Objects.requireNonNull(status, "status");
        this.cancellationSource = cancellationSource;
        this.cancellationNote = normalizeOptional(cancellationNote);
        this.completedAt = completedAt;
        this.version = version;
        validatePersistedLifecycle();
    }

    public static CourseSession rehydrate(
            UUID id,
            UUID organizationId,
            UUID courseId,
            int sequenceNo,
            Instant scheduledStartAt,
            Instant scheduledEndAt,
            int expectedParticipantCount,
            int guestParticipantCount,
            Integer actualParticipantCount,
            Status status,
            CancellationSource cancellationSource,
            String cancellationNote,
            Instant completedAt,
            long version) {
        return new CourseSession(
                id, organizationId, courseId, sequenceNo, scheduledStartAt, scheduledEndAt,
                expectedParticipantCount, guestParticipantCount, actualParticipantCount,
                status, cancellationSource, cancellationNote, completedAt, version);
    }

    public void markCoachCancellationPending(Instant now) {
        requireState(Status.SCHEDULED);
        requireNotStarted(now);
        status = Status.CANCEL_PENDING;
    }

    public void rejectCoachCancellation() {
        requireState(Status.CANCEL_PENDING);
        status = Status.SCHEDULED;
        cancellationSource = null;
        cancellationNote = null;
    }

    public void approveCoachCancellation(String reason, Instant now) {
        requireState(Status.CANCEL_PENDING);
        requireNotStarted(now);
        status = Status.CANCELLED;
        cancellationSource = CancellationSource.COACH;
        cancellationNote = normalizeOptional(reason);
    }

    public void cancelDirect(CancellationSource source, String reason, Instant now) {
        Objects.requireNonNull(source, "source");
        if (source != CancellationSource.COMMITTEE && source != CancellationSource.SYSTEM) {
            throw invalid(CourseOperationsDomainError.ACTOR_NOT_ALLOWED,
                    "student cancels enrollment and coach cancellation requires committee review");
        }
        if (status != Status.SCHEDULED && status != Status.POSTPONED) {
            throw invalidState("only scheduled or postponed session can be cancelled directly");
        }
        requireNotStarted(now);
        status = Status.CANCELLED;
        cancellationSource = source;
        cancellationNote = normalizeOptional(reason);
    }

    public void applyReschedule(Instant newStartAt, Instant newEndAt, Instant now) {
        if (status != Status.SCHEDULED && status != Status.POSTPONED) {
            throw invalidState("only scheduled or postponed session can be rescheduled");
        }
        requireNotStarted(now);
        validateTimeRange(newStartAt, newEndAt);
        Objects.requireNonNull(now, "now");
        if (!newStartAt.isAfter(now)) {
            throw invalid(CourseOperationsDomainError.INVALID_TIME_RANGE,
                    "rescheduled start must be in the future");
        }
        scheduledStartAt = newStartAt;
        scheduledEndAt = newEndAt;
        status = Status.SCHEDULED;
    }

    public void complete(int actualParticipantCount, Instant completedAt) {
        requireState(Status.SCHEDULED);
        Objects.requireNonNull(completedAt, "completedAt");
        if (actualParticipantCount < 0) {
            throw invalid(CourseOperationsDomainError.INVALID_PARTICIPANT_COUNT,
                    "actualParticipantCount must not be negative");
        }
        if (completedAt.isBefore(scheduledEndAt)) {
            throw invalid(CourseOperationsDomainError.INVALID_TIME_RANGE,
                    "session cannot be completed before scheduled end");
        }
        this.actualParticipantCount = actualParticipantCount;
        this.completedAt = completedAt;
        this.status = Status.COMPLETED;
    }

    private void validatePersistedLifecycle() {
        if (status == Status.CANCELLED && cancellationSource == null) {
            throw invalidState("cancelled session requires cancellationSource");
        }
        if (status == Status.COMPLETED && completedAt == null) {
            throw invalidState("completed session requires completedAt");
        }
    }

    private void requireNotStarted(Instant now) {
        Objects.requireNonNull(now, "now");
        if (!now.isBefore(scheduledStartAt)) {
            throw invalid(CourseOperationsDomainError.SESSION_ALREADY_STARTED,
                    "operation is allowed only before the session starts");
        }
    }

    private void requireState(Status expected) {
        if (status != expected) {
            throw invalidState("expected state " + expected + " but was " + status);
        }
    }

    private static void validateTimeRange(Instant startAt, Instant endAt) {
        Objects.requireNonNull(startAt, "startAt");
        Objects.requireNonNull(endAt, "endAt");
        if (!startAt.isBefore(endAt)) {
            throw invalid(CourseOperationsDomainError.INVALID_TIME_RANGE, "startAt must be before endAt");
        }
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
    public UUID courseId() { return courseId; }
    public int sequenceNo() { return sequenceNo; }
    public Instant scheduledStartAt() { return scheduledStartAt; }
    public Instant scheduledEndAt() { return scheduledEndAt; }
    public int expectedParticipantCount() { return expectedParticipantCount; }
    public int guestParticipantCount() { return guestParticipantCount; }
    public Integer actualParticipantCount() { return actualParticipantCount; }
    public Status status() { return status; }
    public CancellationSource cancellationSource() { return cancellationSource; }
    public String cancellationNote() { return cancellationNote; }
    public Instant completedAt() { return completedAt; }
    public long version() { return version; }
}
