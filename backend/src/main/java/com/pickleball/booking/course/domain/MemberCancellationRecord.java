package com.pickleball.booking.course.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class MemberCancellationRecord {
    private final UUID id;
    private final UUID organizationId;
    private final UUID memberId;
    private final UUID enrollmentId;
    private final UUID courseSessionId;
    private final String reason;
    private final Instant cancelledAt;

    private MemberCancellationRecord(
            UUID id,
            UUID organizationId,
            UUID memberId,
            UUID enrollmentId,
            UUID courseSessionId,
            String reason,
            Instant cancelledAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.memberId = Objects.requireNonNull(memberId, "memberId");
        this.enrollmentId = Objects.requireNonNull(enrollmentId, "enrollmentId");
        this.courseSessionId = Objects.requireNonNull(courseSessionId, "courseSessionId");
        this.reason = normalizeOptional(reason);
        this.cancelledAt = Objects.requireNonNull(cancelledAt, "cancelledAt");
    }

    public static MemberCancellationRecord record(
            UUID id,
            UUID organizationId,
            UUID memberId,
            UUID enrollmentId,
            UUID courseSessionId,
            String reason,
            Instant cancelledAt) {
        return new MemberCancellationRecord(
                id, organizationId, memberId, enrollmentId, courseSessionId, reason, cancelledAt);
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID id() { return id; }
    public UUID organizationId() { return organizationId; }
    public UUID memberId() { return memberId; }
    public UUID enrollmentId() { return enrollmentId; }
    public UUID courseSessionId() { return courseSessionId; }
    public String reason() { return reason; }
    public Instant cancelledAt() { return cancelledAt; }
}
