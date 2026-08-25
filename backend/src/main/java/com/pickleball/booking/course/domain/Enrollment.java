package com.pickleball.booking.course.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Enrollment {
    public enum Status {
        SCHEDULED,
        CANCELLED,
        ATTENDED,
        ABSENT
    }

    private final UUID id;
    private final UUID organizationId;
    private final UUID courseMembershipId;
    private final UUID courseSessionId;
    private final UUID userId;
    private final Instant enrolledAt;
    private final long version;

    private Status status;
    private Instant cancelledAt;
    private Instant attendanceMarkedAt;

    private Enrollment(
            UUID id,
            UUID organizationId,
            UUID courseMembershipId,
            UUID courseSessionId,
            UUID userId,
            Status status,
            Instant enrolledAt,
            Instant cancelledAt,
            Instant attendanceMarkedAt,
            long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.courseMembershipId = Objects.requireNonNull(courseMembershipId, "courseMembershipId");
        this.courseSessionId = Objects.requireNonNull(courseSessionId, "courseSessionId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.status = Objects.requireNonNull(status, "status");
        this.enrolledAt = Objects.requireNonNull(enrolledAt, "enrolledAt");
        this.cancelledAt = cancelledAt;
        this.attendanceMarkedAt = attendanceMarkedAt;
        if (version < 0) {
            throw invalid(CourseOperationsDomainError.REQUIRED_FIELD, "version must not be negative");
        }
        this.version = version;
        validatePersistedLifecycle();
    }

    public static Enrollment rehydrate(
            UUID id,
            UUID organizationId,
            UUID courseMembershipId,
            UUID courseSessionId,
            UUID userId,
            Status status,
            Instant enrolledAt,
            Instant cancelledAt,
            Instant attendanceMarkedAt,
            long version) {
        return new Enrollment(
                id, organizationId, courseMembershipId, courseSessionId, userId,
                status, enrolledAt, cancelledAt, attendanceMarkedAt, version);
    }

    public MemberCancellationRecord cancel(
            UUID cancellationRecordId,
            String reason,
            Instant cancelledAt,
            Instant sessionStartAt) {
        requireState(Status.SCHEDULED);
        Objects.requireNonNull(cancellationRecordId, "cancellationRecordId");
        Objects.requireNonNull(cancelledAt, "cancelledAt");
        Objects.requireNonNull(sessionStartAt, "sessionStartAt");
        if (!cancelledAt.isBefore(sessionStartAt)) {
            throw invalid(CourseOperationsDomainError.SESSION_ALREADY_STARTED,
                    "student cancellation is allowed only before the session starts");
        }
        MemberCancellationRecord record = MemberCancellationRecord.record(
                cancellationRecordId,
                organizationId,
                userId,
                id,
                courseSessionId,
                reason,
                cancelledAt);
        this.status = Status.CANCELLED;
        this.cancelledAt = cancelledAt;
        return record;
    }

    public void markAttended(Instant markedAt, Instant sessionStartAt) {
        markAttendance(Status.ATTENDED, markedAt, sessionStartAt);
    }

    public void markAbsent(Instant markedAt, Instant sessionStartAt) {
        markAttendance(Status.ABSENT, markedAt, sessionStartAt);
    }

    private void markAttendance(Status target, Instant markedAt, Instant sessionStartAt) {
        requireState(Status.SCHEDULED);
        Objects.requireNonNull(markedAt, "markedAt");
        Objects.requireNonNull(sessionStartAt, "sessionStartAt");
        if (markedAt.isBefore(sessionStartAt)) {
            throw invalid(CourseOperationsDomainError.INVALID_STATE,
                    "attendance cannot be marked before the session starts");
        }
        status = target;
        attendanceMarkedAt = markedAt;
    }

    private void validatePersistedLifecycle() {
        if (status == Status.CANCELLED && cancelledAt == null) {
            throw invalidState("cancelled enrollment requires cancelledAt");
        }
        if ((status == Status.ATTENDED || status == Status.ABSENT) && attendanceMarkedAt == null) {
            throw invalidState("attendance state requires attendanceMarkedAt");
        }
    }

    private void requireState(Status expected) {
        if (status != expected) {
            throw invalidState("expected state " + expected + " but was " + status);
        }
    }

    private static CourseOperationsDomainException invalidState(String message) {
        return invalid(CourseOperationsDomainError.INVALID_STATE, message);
    }

    private static CourseOperationsDomainException invalid(CourseOperationsDomainError error, String message) {
        return new CourseOperationsDomainException(error, message);
    }

    public UUID id() { return id; }
    public UUID organizationId() { return organizationId; }
    public UUID courseMembershipId() { return courseMembershipId; }
    public UUID courseSessionId() { return courseSessionId; }
    public UUID userId() { return userId; }
    public Status status() { return status; }
    public Instant enrolledAt() { return enrolledAt; }
    public Instant cancelledAt() { return cancelledAt; }
    public Instant attendanceMarkedAt() { return attendanceMarkedAt; }
    public long version() { return version; }
}
