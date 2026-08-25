package com.pickleball.booking.course.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CourseSessionTest {
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    @Test
    void coachCancellationMustPassPendingStateBeforeApproval() {
        CourseSession session = scheduled();

        session.markCoachCancellationPending(NOW);
        assertThat(session.status()).isEqualTo(CourseSession.Status.CANCEL_PENDING);

        session.approveCoachCancellation("coach unavailable", NOW.plusSeconds(60));
        assertThat(session.status()).isEqualTo(CourseSession.Status.CANCELLED);
        assertThat(session.cancellationSource()).isEqualTo(CourseSession.CancellationSource.COACH);
        assertThat(session.cancellationNote()).isEqualTo("coach unavailable");
    }

    @Test
    void rejectedCoachCancellationReturnsSessionToScheduled() {
        CourseSession session = scheduled();
        session.markCoachCancellationPending(NOW);

        session.rejectCoachCancellation();

        assertThat(session.status()).isEqualTo(CourseSession.Status.SCHEDULED);
        assertThat(session.cancellationSource()).isNull();
    }

    @Test
    void studentCannotCancelWholeSessionButCommitteeCanCancelFutureSession() {
        CourseSession session = scheduled();

        assertThatThrownBy(() -> session.cancelDirect(
                        CourseSession.CancellationSource.STUDENT, null, NOW))
                .isInstanceOfSatisfying(CourseOperationsDomainException.class,
                        ex -> assertThat(ex.error()).isEqualTo(CourseOperationsDomainError.ACTOR_NOT_ALLOWED));
        assertThat(session.status()).isEqualTo(CourseSession.Status.SCHEDULED);

        session.cancelDirect(CourseSession.CancellationSource.COMMITTEE, "court unavailable", NOW);
        assertThat(session.status()).isEqualTo(CourseSession.Status.CANCELLED);
        assertThat(session.cancellationSource()).isEqualTo(CourseSession.CancellationSource.COMMITTEE);
    }

    @Test
    void rescheduleOnlyChangesThisFutureSessionAndRejectsStartedSession() {
        CourseSession session = scheduled();
        Instant newStart = NOW.plusSeconds(10800);
        Instant newEnd = NOW.plusSeconds(14400);

        session.applyReschedule(newStart, newEnd, NOW);

        assertThat(session.scheduledStartAt()).isEqualTo(newStart);
        assertThat(session.scheduledEndAt()).isEqualTo(newEnd);
        assertThat(session.status()).isEqualTo(CourseSession.Status.SCHEDULED);

        CourseSession started = scheduled();
        assertThatThrownBy(() -> started.applyReschedule(
                        NOW.plusSeconds(18000), NOW.plusSeconds(21600), NOW.plusSeconds(7200)))
                .isInstanceOfSatisfying(CourseOperationsDomainException.class,
                        ex -> assertThat(ex.error()).isEqualTo(CourseOperationsDomainError.SESSION_ALREADY_STARTED));
        assertThat(started.scheduledStartAt()).isEqualTo(NOW.plusSeconds(7200));
    }

    @Test
    void completionRequiresScheduledEndAndNonNegativeActualCount() {
        CourseSession session = scheduled();

        assertThatThrownBy(() -> session.complete(2, NOW.plusSeconds(9000)))
                .isInstanceOfSatisfying(CourseOperationsDomainException.class,
                        ex -> assertThat(ex.error()).isEqualTo(CourseOperationsDomainError.INVALID_TIME_RANGE));
        assertThat(session.status()).isEqualTo(CourseSession.Status.SCHEDULED);

        session.complete(2, NOW.plusSeconds(10800));
        assertThat(session.status()).isEqualTo(CourseSession.Status.COMPLETED);
        assertThat(session.actualParticipantCount()).isEqualTo(2);
    }

    private CourseSession scheduled() {
        return CourseSession.rehydrate(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
                NOW.plusSeconds(7200), NOW.plusSeconds(10800),
                4, 0, null, CourseSession.Status.SCHEDULED,
                null, null, null, 0);
    }
}
