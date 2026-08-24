package com.pickleball.booking.offering.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OfferingRegistrationTest {
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    @Test
    void registrationStartsActive() {
        UUID student = UUID.randomUUID();
        OfferingRegistration registration = registration(student);

        assertThat(registration.status()).isEqualTo(OfferingRegistrationStatus.ACTIVE);
        assertThat(registration.userId()).isEqualTo(student);
        assertThat(registration.registeredAt()).isEqualTo(NOW);
    }

    @Test
    void studentCanCancelOwnActiveRegistrationWithoutReason() {
        UUID student = UUID.randomUUID();
        OfferingRegistration registration = registration(student);
        Instant cancelledAt = NOW.plusSeconds(300);

        registration.cancelByStudent(student, cancelledAt, null);

        assertThat(registration.status()).isEqualTo(OfferingRegistrationStatus.CANCELLED);
        assertThat(registration.cancelledAt()).isEqualTo(cancelledAt);
        assertThat(registration.cancelReason()).isNull();
    }

    @Test
    void studentCannotCancelAnotherStudentsRegistration() {
        OfferingRegistration registration = registration(UUID.randomUUID());

        assertThatThrownBy(() -> registration.cancelByStudent(UUID.randomUUID(), NOW.plusSeconds(60), "no"))
                .isInstanceOfSatisfying(OfferingDomainException.class,
                        ex -> assertThat(ex.error()).isEqualTo(OfferingDomainError.REGISTRATION_ACTOR_FORBIDDEN));
        assertThat(registration.status()).isEqualTo(OfferingRegistrationStatus.ACTIVE);
    }

    @Test
    void activeRegistrationCanConvertExactlyOnce() {
        OfferingRegistration registration = registration(UUID.randomUUID());
        UUID membershipId = UUID.randomUUID();

        registration.markConverted(membershipId);

        assertThat(registration.status()).isEqualTo(OfferingRegistrationStatus.CONVERTED);
        assertThat(registration.convertedCourseMembershipId()).isEqualTo(membershipId);
        assertThatThrownBy(() -> registration.markConverted(UUID.randomUUID()))
                .isInstanceOfSatisfying(OfferingDomainException.class,
                        ex -> assertThat(ex.error()).isEqualTo(OfferingDomainError.INVALID_STATE));
    }

    @Test
    void cancelledRegistrationCannotBeConverted() {
        UUID student = UUID.randomUUID();
        OfferingRegistration registration = registration(student);
        registration.cancelByStudent(student, NOW.plusSeconds(60), "changed plans");

        assertThatThrownBy(() -> registration.markConverted(UUID.randomUUID()))
                .isInstanceOfSatisfying(OfferingDomainException.class,
                        ex -> assertThat(ex.error()).isEqualTo(OfferingDomainError.INVALID_STATE));
    }

    private OfferingRegistration registration(UUID student) {
        return OfferingRegistration.register(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), student, NOW);
    }
}
