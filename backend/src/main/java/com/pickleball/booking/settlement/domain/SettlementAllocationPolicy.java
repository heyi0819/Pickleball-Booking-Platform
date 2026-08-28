package com.pickleball.booking.settlement.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Pure domain policy for deterministic coach allocation. */
public final class SettlementAllocationPolicy {
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private SettlementAllocationPolicy() {}

    public static List<Allocation> allocate(
            BigDecimal distributableAmount,
            List<Assignment> assignments,
            List<AllocationRequest> requestedAllocations) {
        BigDecimal distributable = money(distributableAmount, "distributableAmount");
        if (distributable.signum() < 0) {
            throw new IllegalArgumentException("distributableAmount must not be negative");
        }
        if (assignments == null || assignments.isEmpty()) {
            throw new IllegalArgumentException("At least one accepted coach assignment is required");
        }

        List<Assignment> orderedAssignments = assignments.stream()
                .sorted((left, right) -> left.assignmentId().compareTo(right.assignmentId()))
                .toList();
        ensureUniqueAssignments(orderedAssignments);

        if (requestedAllocations == null || requestedAllocations.isEmpty()) {
            return equalAllocations(distributable, orderedAssignments);
        }

        Map<UUID, AllocationRequest> requests = indexRequests(requestedAllocations);
        if (requests.size() != orderedAssignments.size()
                || orderedAssignments.stream().anyMatch(a -> !requests.containsKey(a.coachProfileId()))) {
            throw new IllegalArgumentException("coachAllocations must cover every accepted coach exactly once");
        }

        List<AllocationRequest> orderedRequests = orderedAssignments.stream()
                .map(a -> requests.get(a.coachProfileId()))
                .toList();

        boolean allPercentage = orderedRequests.stream().allMatch(r -> r.type() == AllocationType.PERCENTAGE);
        if (allPercentage) {
            return percentageAllocations(distributable, orderedAssignments, orderedRequests);
        }

        BigDecimal nonEqualTotal = BigDecimal.ZERO.setScale(2);
        int equalCount = 0;
        List<BigDecimal> provisional = new ArrayList<>(orderedRequests.size());
        for (AllocationRequest request : orderedRequests) {
            switch (request.type()) {
                case EQUAL -> {
                    requireNull(request.value(), "EQUAL allocationValue must be null");
                    provisional.add(null);
                    equalCount++;
                }
                case FIXED -> {
                    BigDecimal fixed = money(requireValue(request), "FIXED allocationValue");
                    if (fixed.signum() < 0) throw new IllegalArgumentException("FIXED allocationValue must not be negative");
                    provisional.add(fixed);
                    nonEqualTotal = nonEqualTotal.add(fixed);
                }
                case PERCENTAGE -> {
                    BigDecimal percentage = percentage(requireValue(request));
                    BigDecimal payable = distributable.multiply(percentage)
                            .divide(HUNDRED, 2, RoundingMode.HALF_UP);
                    provisional.add(payable);
                    nonEqualTotal = nonEqualTotal.add(payable);
                }
            }
        }

        if (nonEqualTotal.compareTo(distributable) > 0) {
            throw new IllegalArgumentException("Coach allocations exceed distributableAmount");
        }

        if (equalCount == 0 && nonEqualTotal.compareTo(distributable) != 0) {
            throw new IllegalArgumentException("Coach allocations must sum exactly to distributableAmount");
        }

        BigDecimal equalPool = distributable.subtract(nonEqualTotal);
        List<BigDecimal> equalAmounts = splitEvenly(equalPool, equalCount);
        int equalIndex = 0;
        List<Allocation> result = new ArrayList<>(orderedAssignments.size());
        for (int i = 0; i < orderedAssignments.size(); i++) {
            Assignment assignment = orderedAssignments.get(i);
            AllocationRequest request = orderedRequests.get(i);
            BigDecimal payable = provisional.get(i);
            if (request.type() == AllocationType.EQUAL) payable = equalAmounts.get(equalIndex++);
            result.add(new Allocation(
                    assignment.assignmentId(),
                    assignment.coachProfileId(),
                    request.type(),
                    request.type() == AllocationType.EQUAL ? null : request.value(),
                    payable));
        }
        assertTotal(distributable, result);
        return List.copyOf(result);
    }

    private static List<Allocation> equalAllocations(BigDecimal distributable, List<Assignment> assignments) {
        List<BigDecimal> amounts = splitEvenly(distributable, assignments.size());
        List<Allocation> result = new ArrayList<>(assignments.size());
        for (int i = 0; i < assignments.size(); i++) {
            Assignment assignment = assignments.get(i);
            result.add(new Allocation(
                    assignment.assignmentId(), assignment.coachProfileId(), AllocationType.EQUAL, null, amounts.get(i)));
        }
        return List.copyOf(result);
    }

    private static List<Allocation> percentageAllocations(
            BigDecimal distributable,
            List<Assignment> assignments,
            List<AllocationRequest> requests) {
        BigDecimal totalPercentage = requests.stream()
                .map(request -> percentage(requireValue(request)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalPercentage.compareTo(HUNDRED) != 0) {
            throw new IllegalArgumentException("PERCENTAGE allocations must sum to 100");
        }

        List<Allocation> result = new ArrayList<>(assignments.size());
        BigDecimal allocated = BigDecimal.ZERO.setScale(2);
        for (int i = 0; i < assignments.size(); i++) {
            Assignment assignment = assignments.get(i);
            AllocationRequest request = requests.get(i);
            BigDecimal payable;
            if (i == assignments.size() - 1) {
                payable = distributable.subtract(allocated);
            } else {
                payable = distributable.multiply(percentage(requireValue(request)))
                        .divide(HUNDRED, 2, RoundingMode.HALF_UP);
                allocated = allocated.add(payable);
            }
            result.add(new Allocation(
                    assignment.assignmentId(), assignment.coachProfileId(), AllocationType.PERCENTAGE,
                    request.value(), payable));
        }
        assertTotal(distributable, result);
        return List.copyOf(result);
    }

    private static List<BigDecimal> splitEvenly(BigDecimal total, int count) {
        if (count == 0) return List.of();
        BigDecimal base = total.divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
        BigDecimal remainder = total.subtract(base.multiply(BigDecimal.valueOf(count)));
        int extraCents = remainder.movePointRight(2).intValueExact();
        List<BigDecimal> amounts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            amounts.add(i >= count - extraCents ? base.add(new BigDecimal("0.01")) : base);
        }
        return amounts;
    }

    private static Map<UUID, AllocationRequest> indexRequests(List<AllocationRequest> requests) {
        Map<UUID, AllocationRequest> indexed = new HashMap<>();
        for (AllocationRequest request : requests) {
            if (request == null || request.coachProfileId() == null || request.type() == null) {
                throw new IllegalArgumentException("coachProfileId and allocationType are required");
            }
            if (indexed.put(request.coachProfileId(), request) != null) {
                throw new IllegalArgumentException("coachAllocations contains duplicate coachProfileId");
            }
        }
        return indexed;
    }

    private static void ensureUniqueAssignments(List<Assignment> assignments) {
        Set<UUID> ids = new HashSet<>();
        Set<UUID> coaches = new HashSet<>();
        for (Assignment assignment : assignments) {
            if (assignment == null || assignment.assignmentId() == null || assignment.coachProfileId() == null) {
                throw new IllegalArgumentException("Coach assignment identifiers are required");
            }
            if (!ids.add(assignment.assignmentId()) || !coaches.add(assignment.coachProfileId())) {
                throw new IllegalArgumentException("Accepted coach assignments must be unique");
            }
        }
    }

    private static BigDecimal requireValue(AllocationRequest request) {
        if (request.value() == null) throw new IllegalArgumentException(request.type() + " allocationValue is required");
        return request.value();
    }

    private static void requireNull(BigDecimal value, String message) {
        if (value != null) throw new IllegalArgumentException(message);
    }

    private static BigDecimal percentage(BigDecimal value) {
        if (value.scale() > 4) throw new IllegalArgumentException("PERCENTAGE allocationValue supports at most 4 decimals");
        if (value.signum() <= 0 || value.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException("PERCENTAGE allocationValue must be greater than 0 and at most 100");
        }
        return value;
    }

    private static BigDecimal money(BigDecimal value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(field + " supports at most 2 decimals", ex);
        }
    }

    private static void assertTotal(BigDecimal expected, List<Allocation> allocations) {
        BigDecimal actual = allocations.stream().map(Allocation::payableAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (actual.compareTo(expected) != 0) throw new IllegalStateException("Coach allocation total drifted from distributableAmount");
    }

    public enum AllocationType { EQUAL, PERCENTAGE, FIXED }

    public record Assignment(UUID assignmentId, UUID coachProfileId) {}

    public record AllocationRequest(UUID coachProfileId, AllocationType type, BigDecimal value) {}

    public record Allocation(
            UUID assignmentId,
            UUID coachProfileId,
            AllocationType type,
            BigDecimal value,
            BigDecimal payableAmount) {}
}
