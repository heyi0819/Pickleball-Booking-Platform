package com.pickleball.booking.course.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CourseCancellationRequestTest {
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    @Test
    void coachCancellationRequestRequiresReasonAndStartsPendingReview() {
        assertThatThrownBy(() -> CourseCancellationRequest.createPending(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), " ", NOW))
                .isInstanceOfSatisfying(CourseOperationsDomainException.class,
                        ex -> assertThat(ex.error()).isEqualTo(CourseOperationsDomainError.REQUIRED_FIELD));

        CourseCancellationRequest request = pending();
        assertThat(request.status()).isEqualTo(CourseCancellationRequest.Status.PENDING_REVIEW);
        assertThat(request.requesterRole()).isEqualTo(CourseCancellationRequest.RequesterRole.COACH);
    }

    @Test
    void committeeReviewProducesTerminalDecisionMetadata() {
        CourseCancellationRequest request = pending();
        UUID reviewer = UUID.randomUUID();

        request.approve(reviewer, NOW.plusSeconds(60), "approved");

        assertThat(request.status()).isEqualTo(CourseCancellationRequest.Status.APPROVED);
        assertThat(request.reviewedBy()).isEqualTo(reviewer);
        assertThat(request.reviewNote()).isEqualTo("approved");
    }

    @Test
    void invalidReviewMustNotPartiallyMutatePendingRequest() {
        CourseCancellationRequest request = pending();

        assertThatThrownBy(() -> request.reject(
                        UUID.randomUUID(), NOW.minusSeconds(1), "too early"))
                .isInstanceOfSatisfying(CourseOperationsDomainException.class,
                        ex -> assertThat(ex.error()).isEqualTo(CourseOperationsDomainError.INVALID_STATE));

        assertThat(request.status()).isEqualTo(CourseCancellationRequest.Status.PENDING_REVIEW);
        assertThat(request.reviewedBy()).isNull();
        assertThat(request.reviewedAt()).isNull();
        assertThat(request.reviewNote()).isNull();
    }

    @Test
    void onlyRequestingCoachCanWithdrawPendingRequest() {
        CourseCancellationRequest request = pending();

        assertThatThrownBy(() -> request.withdraw(UUID.randomUUID()))
                .isInstanceOfSatisfying(CourseOperationsDomainException.class,
                        ex -> assertThat(ex.error()).isEqualTo(CourseOperationsDomainError.ACTOR_NOT_ALLOWED));
        assertThat(request.status()).isEqualTo(CourseCancellationRequest.Status.PENDING_REVIEW);

        request.withdraw(request.requestedBy());
        assertThat(request.status()).isEqualTo(CourseCancellationRequest.Status.WITHDRAWN);
    }

    private CourseCancellationRequest pending() {
        return CourseCancellationRequest.createPending(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "coach unavailable", NOW);
    }
}
