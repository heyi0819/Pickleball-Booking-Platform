package com.pickleball.booking.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pickleball.booking.settlement.domain.SettlementAllocationPolicy.AllocationRequest;
import com.pickleball.booking.settlement.domain.SettlementAllocationPolicy.AllocationType;
import com.pickleball.booking.settlement.domain.SettlementAllocationPolicy.Assignment;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SettlementAllocationPolicyTest {
    @Test
    void defaultEqualAllocationIsDeterministicAndPreservesCents() {
        Assignment first = new Assignment(new UUID(0, 1), UUID.randomUUID());
        Assignment second = new Assignment(new UUID(0, 2), UUID.randomUUID());
        Assignment third = new Assignment(new UUID(0, 3), UUID.randomUUID());

        var result = SettlementAllocationPolicy.allocate(
                new BigDecimal("100.00"), List.of(third, first, second), List.of());

        assertThat(result).extracting(a -> a.payableAmount().toPlainString())
                .containsExactly("33.33", "33.33", "33.34");
        assertThat(result).allMatch(a -> a.type() == AllocationType.EQUAL && a.value() == null);
    }

    @Test
    void percentageAllocationRequiresOneHundredPercentAndAssignsRoundingResidualDeterministically() {
        UUID firstCoach = UUID.randomUUID();
        UUID secondCoach = UUID.randomUUID();
        UUID thirdCoach = UUID.randomUUID();
        List<Assignment> assignments = List.of(
                new Assignment(new UUID(0, 1), firstCoach),
                new Assignment(new UUID(0, 2), secondCoach),
                new Assignment(new UUID(0, 3), thirdCoach));
        List<AllocationRequest> requests = List.of(
                new AllocationRequest(firstCoach, AllocationType.PERCENTAGE, new BigDecimal("33.33")),
                new AllocationRequest(secondCoach, AllocationType.PERCENTAGE, new BigDecimal("33.33")),
                new AllocationRequest(thirdCoach, AllocationType.PERCENTAGE, new BigDecimal("33.34")));

        var result = SettlementAllocationPolicy.allocate(new BigDecimal("100.00"), assignments, requests);

        assertThat(result).extracting(a -> a.payableAmount().toPlainString())
                .containsExactly("33.33", "33.33", "33.34");
    }

    @Test
    void fixedPlusEqualAllocationsUseEqualShareForRemainingAmount() {
        UUID fixedCoach = UUID.randomUUID();
        UUID equalCoachA = UUID.randomUUID();
        UUID equalCoachB = UUID.randomUUID();
        List<Assignment> assignments = List.of(
                new Assignment(new UUID(0, 1), fixedCoach),
                new Assignment(new UUID(0, 2), equalCoachA),
                new Assignment(new UUID(0, 3), equalCoachB));
        List<AllocationRequest> requests = List.of(
                new AllocationRequest(fixedCoach, AllocationType.FIXED, new BigDecimal("40.00")),
                new AllocationRequest(equalCoachA, AllocationType.EQUAL, null),
                new AllocationRequest(equalCoachB, AllocationType.EQUAL, null));

        var result = SettlementAllocationPolicy.allocate(new BigDecimal("100.00"), assignments, requests);

        assertThat(result).extracting(a -> a.payableAmount().toPlainString())
                .containsExactly("40.00", "30.00", "30.00");
    }

    @Test
    void explicitAllocationMustCoverAcceptedCoachesAndPreserveTotal() {
        UUID firstCoach = UUID.randomUUID();
        UUID secondCoach = UUID.randomUUID();
        List<Assignment> assignments = List.of(
                new Assignment(new UUID(0, 1), firstCoach),
                new Assignment(new UUID(0, 2), secondCoach));

        assertThatThrownBy(() -> SettlementAllocationPolicy.allocate(
                new BigDecimal("100.00"), assignments,
                List.of(new AllocationRequest(firstCoach, AllocationType.FIXED, new BigDecimal("40.00")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cover every accepted coach");

        assertThatThrownBy(() -> SettlementAllocationPolicy.allocate(
                new BigDecimal("100.00"), assignments,
                List.of(
                        new AllocationRequest(firstCoach, AllocationType.FIXED, new BigDecimal("40.00")),
                        new AllocationRequest(secondCoach, AllocationType.FIXED, new BigDecimal("50.00")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sum exactly");
    }
}
