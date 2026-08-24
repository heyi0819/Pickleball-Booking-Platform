package com.pickleball.booking.offering.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CourseOfferingPriceSnapshotTest {
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    @Test
    void draftPriceSnapshotNormalizesCurrencyAndKeepsImmutableInputs() {
        CourseOfferingPriceSnapshot snapshot = snapshot(new BigDecimal("1200.00"));

        assertThat(snapshot.status()).isEqualTo(OfferingPriceSnapshotStatus.DRAFT);
        assertThat(snapshot.currency()).isEqualTo("TWD");
        assertThat(snapshot.pricePerParticipant()).isEqualByComparingTo("1200.00");
        assertThat(snapshot.ruleTrace()).containsEntry("rule", "group-default");
        assertThatThrownBy(() -> snapshot.ruleTrace().put("changed", true))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void confirmedSnapshotCanOnlyBeConfirmedOnceThenSuperseded() {
        CourseOfferingPriceSnapshot snapshot = snapshot(new BigDecimal("1200.00"));
        UUID committee = UUID.randomUUID();

        snapshot.confirm(committee, NOW);

        assertThat(snapshot.status()).isEqualTo(OfferingPriceSnapshotStatus.CONFIRMED);
        assertThat(snapshot.confirmedBy()).isEqualTo(committee);
        assertThat(snapshot.confirmedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> snapshot.confirm(committee, NOW.plusSeconds(1)))
                .isInstanceOfSatisfying(OfferingDomainException.class,
                        ex -> assertThat(ex.error()).isEqualTo(OfferingDomainError.INVALID_STATE));

        snapshot.supersede();
        assertThat(snapshot.status()).isEqualTo(OfferingPriceSnapshotStatus.SUPERSEDED);
    }

    @Test
    void negativePriceIsRejected() {
        assertThatThrownBy(() -> snapshot(new BigDecimal("-0.01")))
                .isInstanceOfSatisfying(OfferingDomainException.class,
                        ex -> assertThat(ex.error()).isEqualTo(OfferingDomainError.INVALID_PRICE));
    }

    private CourseOfferingPriceSnapshot snapshot(BigDecimal amount) {
        return CourseOfferingPriceSnapshot.createDraft(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, "twd", amount,
                Map.of("rule", "group-default"), UUID.randomUUID());
    }
}
