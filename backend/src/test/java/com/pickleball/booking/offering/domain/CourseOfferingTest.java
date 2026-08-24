package com.pickleball.booking.offering.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CourseOfferingTest {
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    @Test
    void draftSpecRejectsInvalidCapacityAndRegistrationWindow() {
        assertThatThrownBy(() -> new CourseOfferingDraftSpec(
                        UUID.randomUUID(), "Group", null, OfferingScheduleType.SINGLE,
                        OfferingBillingMode.FULL_COURSE, null, 0, 6,
                        NOW, NOW.plusSeconds(3600)))
                .isInstanceOfSatisfying(OfferingDomainException.class,
                        ex -> assertThat(ex.error()).isEqualTo(OfferingDomainError.INVALID_CAPACITY));

        assertThatThrownBy(() -> new CourseOfferingDraftSpec(
                        UUID.randomUUID(), "Group", null, OfferingScheduleType.SINGLE,
                        OfferingBillingMode.FULL_COURSE, null, 3, 6,
                        NOW.plusSeconds(3600), NOW))
                .isInstanceOfSatisfying(OfferingDomainException.class,
                        ex -> assertThat(ex.error()).isEqualTo(OfferingDomainError.INVALID_REGISTRATION_WINDOW));
    }

    @Test
    void publicationRequiresReadyDependenciesAndFutureSessionPlan() {
        CourseOffering offering = draft(3, 6, OfferingScheduleType.SINGLE, List.of(session(1, 7200, 10800)));

        assertThatThrownBy(() -> offering.publish(
                        UUID.randomUUID(), NOW,
                        new PublicationReadiness(false, UUID.randomUUID(), true)))
                .isInstanceOfSatisfying(OfferingDomainException.class,
                        ex -> assertThat(ex.error()).isEqualTo(OfferingDomainError.OFFERING_NOT_READY));
        assertThat(offering.status()).isEqualTo(CourseOfferingStatus.DRAFT);

        UUID publisher = UUID.randomUUID();
        offering.publish(publisher, NOW, ready());

        assertThat(offering.status()).isEqualTo(CourseOfferingStatus.OPEN);
        assertThat(offering.publishedBy()).isEqualTo(publisher);
        assertThat(offering.publishedAt()).isEqualTo(NOW);
        assertThat(offering.isRegistrationOpenAt(NOW.plusSeconds(60))).isTrue();
    }

    @Test
    void singleOfferingMustHaveExactlyOneSessionAtPublication() {
        CourseOffering offering = draft(
                3, 6, OfferingScheduleType.SINGLE,
                List.of(session(1, 7200, 10800), session(2, 14400, 18000)));

        assertThatThrownBy(() -> offering.publish(UUID.randomUUID(), NOW, ready()))
                .isInstanceOfSatisfying(OfferingDomainException.class,
                        ex -> assertThat(ex.error()).isEqualTo(OfferingDomainError.INVALID_SESSION_PLAN));
    }

    @Test
    void openOfferingCannotReceiveNormalDraftRevision() {
        CourseOffering offering = draft(3, 6, OfferingScheduleType.SINGLE, List.of(session(1, 7200, 10800)));
        offering.publish(UUID.randomUUID(), NOW, ready());

        assertThatThrownBy(() -> offering.reviseDraft(spec(2, 8, OfferingScheduleType.SINGLE)))
                .isInstanceOfSatisfying(OfferingDomainException.class,
                        ex -> assertThat(ex.error()).isEqualTo(OfferingDomainError.INVALID_STATE));
        assertThatThrownBy(() -> offering.replaceSessionPlans(List.of(session(1, 9000, 12600))))
                .isInstanceOfSatisfying(OfferingDomainException.class,
                        ex -> assertThat(ex.error()).isEqualTo(OfferingDomainError.INVALID_STATE));
    }

    @Test
    void canonicalLifecycleClosesThenConfirmsWithinCapacity() {
        CourseOffering offering = draft(3, 6, OfferingScheduleType.RECURRING,
                List.of(session(1, 7200, 10800), session(2, 93600, 97200)));
        UUID committee = UUID.randomUUID();

        offering.publish(committee, NOW, ready());
        offering.close(committee, NOW.plusSeconds(1800));
        offering.confirm(committee, 4, NOW.plusSeconds(1900));

        assertThat(offering.status()).isEqualTo(CourseOfferingStatus.CONFIRMED);
        assertThat(offering.closedBy()).isEqualTo(committee);
        assertThat(offering.confirmedBy()).isEqualTo(committee);
    }

    @Test
    void confirmationRejectsCountsOutsideConfiguredCapacity() {
        CourseOffering below = openedAndClosed(3, 6);
        assertThatThrownBy(() -> below.confirm(UUID.randomUUID(), 2, NOW.plusSeconds(1900)))
                .isInstanceOfSatisfying(OfferingDomainException.class,
                        ex -> assertThat(ex.error()).isEqualTo(OfferingDomainError.PARTICIPANT_BELOW_MIN));

        CourseOffering above = openedAndClosed(3, 6);
        assertThatThrownBy(() -> above.confirm(UUID.randomUUID(), 7, NOW.plusSeconds(1900)))
                .isInstanceOfSatisfying(OfferingDomainException.class,
                        ex -> assertThat(ex.error()).isEqualTo(OfferingDomainError.PARTICIPANT_ABOVE_MAX));
    }

    @Test
    void cancelledOfferingCannotReturnToPublicationFlow() {
        CourseOffering offering = draft(3, 6, OfferingScheduleType.SINGLE, List.of(session(1, 7200, 10800)));
        offering.cancel(UUID.randomUUID(), NOW, null);

        assertThat(offering.status()).isEqualTo(CourseOfferingStatus.CANCELLED);
        assertThat(offering.cancelReason()).isNull();
        assertThatThrownBy(() -> offering.publish(UUID.randomUUID(), NOW, ready()))
                .isInstanceOfSatisfying(OfferingDomainException.class,
                        ex -> assertThat(ex.error()).isEqualTo(OfferingDomainError.INVALID_STATE));
    }

    private CourseOffering openedAndClosed(int min, int max) {
        CourseOffering offering = draft(min, max, OfferingScheduleType.SINGLE, List.of(session(1, 7200, 10800)));
        UUID actor = UUID.randomUUID();
        offering.publish(actor, NOW, ready());
        offering.close(actor, NOW.plusSeconds(1800));
        return offering;
    }

    private CourseOffering draft(
            int min,
            int max,
            OfferingScheduleType scheduleType,
            List<CourseOfferingSessionPlan> sessions) {
        return CourseOffering.createDraft(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), spec(min, max, scheduleType), sessions);
    }

    private CourseOfferingDraftSpec spec(int min, int max, OfferingScheduleType scheduleType) {
        return new CourseOfferingDraftSpec(
                UUID.randomUUID(), "Open Enrollment Group", "description", scheduleType,
                OfferingBillingMode.FULL_COURSE, "BEGINNER", min, max,
                NOW.minusSeconds(3600), NOW.plusSeconds(86400));
    }

    private CourseOfferingSessionPlan session(int sequence, long startOffset, long endOffset) {
        return new CourseOfferingSessionPlan(
                UUID.randomUUID(), sequence, NOW.plusSeconds(startOffset), NOW.plusSeconds(endOffset),
                null, "Test Venue", "Taipei");
    }

    private PublicationReadiness ready() {
        return new PublicationReadiness(true, UUID.randomUUID(), true);
    }
}
