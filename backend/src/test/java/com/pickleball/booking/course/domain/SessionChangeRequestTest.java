package com.pickleball.booking.course.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionChangeRequestTest {
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    @Test
    void rescheduleRequiresValidFutureProposedRange() {
        SessionChangeRequest request = SessionChangeRequest.createPending(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                SessionChangeRequest.Type.RESCHEDULE, UUID.randomUUID(), "student request",
                NOW.plusSeconds(7200), NOW.plusSeconds(10800), null, null, NOW);

        assertThat(request.status()).isEqualTo(SessionChangeRequest.Status.PENDING);
        assertThat(request.proposedStartAt()).isEqualTo(NOW.plusSeconds(7200));

        assertThatThrownBy(() -> SessionChangeRequest.createPending(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        SessionChangeRequest.Type.RESCHEDULE, UUID.randomUUID(), "bad time",
                        NOW.minusSeconds(1), NOW.plusSeconds(3600), null, null, NOW))
                .isInstanceOfSatisfying(CourseOperationsDomainException.class,
                        ex -> assertThat(ex.error()).isEqualTo(CourseOperationsDomainError.INVALID_CHANGE_PROPOSAL));
    }

    @Test
    void coachAndVenueChangesRequireTheirTargetProposal() {
        assertThatThrownBy(() -> SessionChangeRequest.createPending(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        SessionChangeRequest.Type.CHANGE_COACH, UUID.randomUUID(), "replace coach",
                        null, null, null, null, NOW))
                .isInstanceOfSatisfying(CourseOperationsDomainException.class,
                        ex -> assertThat(ex.error()).isEqualTo(CourseOperationsDomainError.INVALID_CHANGE_PROPOSAL));

        assertThatThrownBy(() -> SessionChangeRequest.createPending(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        SessionChangeRequest.Type.CHANGE_VENUE, UUID.randomUUID(), "replace venue",
                        null, null, null, null, NOW))
                .isInstanceOfSatisfying(CourseOperationsDomainException.class,
                        ex -> assertThat(ex.error()).isEqualTo(CourseOperationsDomainError.INVALID_CHANGE_PROPOSAL));
    }

    @Test
    void committeeDecisionProducesRequiredMetadata() {
        SessionChangeRequest request = pendingCoachLeave();
        UUID committee = UUID.randomUUID();

        request.approve(committee, NOW.plusSeconds(60), "replacement arranged");

        assertThat(request.status()).isEqualTo(SessionChangeRequest.Status.APPROVED);
        assertThat(request.decidedBy()).isEqualTo(committee);
        assertThat(request.decisionReason()).isEqualTo("replacement arranged");
    }

    @Test
    void invalidDecisionMustNotPartiallyMutatePendingRequest() {
        SessionChangeRequest request = pendingCoachLeave();

        assertThatThrownBy(() -> request.reject(
                        UUID.randomUUID(), NOW.minusSeconds(1), "too early"))
                .isInstanceOfSatisfying(CourseOperationsDomainException.class,
                        ex -> assertThat(ex.error()).isEqualTo(CourseOperationsDomainError.INVALID_STATE));

        assertThat(request.status()).isEqualTo(SessionChangeRequest.Status.PENDING);
        assertThat(request.decidedBy()).isNull();
        assertThat(request.decidedAt()).isNull();
        assertThat(request.decisionReason()).isNull();
    }

    @Test
    void directCommitteeRescheduleIsPersistableAsApprovedHistoryWithoutOriginalColumns() {
        UUID committee = UUID.randomUUID();
        SessionChangeRequest request = SessionChangeRequest.createApprovedDirectReschedule(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), committee,
                "committee reschedule", NOW.plusSeconds(7200), NOW.plusSeconds(10800), NOW);

        assertThat(request.type()).isEqualTo(SessionChangeRequest.Type.RESCHEDULE);
        assertThat(request.status()).isEqualTo(SessionChangeRequest.Status.APPROVED);
        assertThat(request.requestedBy()).isEqualTo(committee);
        assertThat(request.decidedBy()).isEqualTo(committee);
        assertThat(request.decisionReason()).isEqualTo("committee reschedule");
    }

    @Test
    void onlyRequesterCanWithdrawPendingChange() {
        SessionChangeRequest request = pendingCoachLeave();

        assertThatThrownBy(() -> request.withdraw(UUID.randomUUID()))
                .isInstanceOfSatisfying(CourseOperationsDomainException.class,
                        ex -> assertThat(ex.error()).isEqualTo(CourseOperationsDomainError.ACTOR_NOT_ALLOWED));
        assertThat(request.status()).isEqualTo(SessionChangeRequest.Status.PENDING);

        request.withdraw(request.requestedBy());
        assertThat(request.status()).isEqualTo(SessionChangeRequest.Status.WITHDRAWN);
    }

    private SessionChangeRequest pendingCoachLeave() {
        return SessionChangeRequest.createPending(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                SessionChangeRequest.Type.COACH_LEAVE, UUID.randomUUID(), "coach leave",
                null, null, null, null, NOW);
    }
}
