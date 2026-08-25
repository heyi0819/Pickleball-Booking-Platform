package com.pickleball.booking.course.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnrollmentTest {
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");
    private static final Instant SESSION_START = NOW.plusSeconds(7200);

    @Test
    void studentCancellationCancelsEnrollmentAndCreatesImmutableHistoryRecord() {
        Enrollment enrollment = scheduledEnrollment();
        UUID recordId = UUID.randomUUID();

        MemberCancellationRecord record = enrollment.cancel(recordId, "   ", NOW, SESSION_START);

        assertThat(enrollment.status()).isEqualTo(Enrollment.Status.CANCELLED);
        assertThat(enrollment.cancelledAt()).isEqualTo(NOW);
        assertThat(record.id()).isEqualTo(recordId);
        assertThat(record.enrollmentId()).isEqualTo(enrollment.id());
        assertThat(record.memberId()).isEqualTo(enrollment.userId());
        assertThat(record.courseSessionId()).isEqualTo(enrollment.courseSessionId());
        assertThat(record.reason()).isNull();
        assertThat(record.cancelledAt()).isEqualTo(NOW);
    }

    @Test
    void studentCancellationAfterSessionStartIsRejectedWithoutStateChange() {
        Enrollment enrollment = scheduledEnrollment();

        assertThatThrownBy(() -> enrollment.cancel(
                        UUID.randomUUID(), null, SESSION_START, SESSION_START))
                .isInstanceOfSatisfying(CourseOperationsDomainException.class,
                        ex -> assertThat(ex.error()).isEqualTo(CourseOperationsDomainError.SESSION_ALREADY_STARTED));

        assertThat(enrollment.status()).isEqualTo(Enrollment.Status.SCHEDULED);
        assertThat(enrollment.cancelledAt()).isNull();
    }

    @Test
    void cancellationRecordFailureMustNotMutateEnrollment() {
        Enrollment enrollment = scheduledEnrollment();

        assertThatThrownBy(() -> enrollment.cancel(null, null, NOW, SESSION_START))
                .isInstanceOf(NullPointerException.class);

        assertThat(enrollment.status()).isEqualTo(Enrollment.Status.SCHEDULED);
        assertThat(enrollment.cancelledAt()).isNull();
    }

    @Test
    void attendanceCanOnlyBeMarkedAfterSessionStarts() {
        Enrollment enrollment = scheduledEnrollment();

        assertThatThrownBy(() -> enrollment.markAttended(NOW, SESSION_START))
                .isInstanceOfSatisfying(CourseOperationsDomainException.class,
                        ex -> assertThat(ex.error()).isEqualTo(CourseOperationsDomainError.INVALID_STATE));
        assertThat(enrollment.status()).isEqualTo(Enrollment.Status.SCHEDULED);

        enrollment.markAttended(SESSION_START, SESSION_START);
        assertThat(enrollment.status()).isEqualTo(Enrollment.Status.ATTENDED);
        assertThat(enrollment.attendanceMarkedAt()).isEqualTo(SESSION_START);
    }

    @Test
    void terminalEnrollmentStateCannotBeCancelledAgain() {
        Enrollment enrollment = scheduledEnrollment();
        enrollment.cancel(UUID.randomUUID(), null, NOW, SESSION_START);

        assertThatThrownBy(() -> enrollment.cancel(
                        UUID.randomUUID(), null, NOW.plusSeconds(60), SESSION_START))
                .isInstanceOfSatisfying(CourseOperationsDomainException.class,
                        ex -> assertThat(ex.error()).isEqualTo(CourseOperationsDomainError.INVALID_STATE));
    }

    private Enrollment scheduledEnrollment() {
        return Enrollment.rehydrate(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Enrollment.Status.SCHEDULED, NOW.minusSeconds(3600), null, null, 0);
    }
}
