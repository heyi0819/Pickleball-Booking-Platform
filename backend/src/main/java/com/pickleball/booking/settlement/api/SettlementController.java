package com.pickleball.booking.settlement.api;

import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.settlement.application.SettlementApplicationService;
import com.pickleball.booking.settlement.domain.SettlementAllocationPolicy.AllocationRequest;
import com.pickleball.booking.settlement.domain.SettlementAllocationPolicy.AllocationType;
import com.pickleball.booking.shared.api.ApiResponse;
import com.pickleball.booking.shared.api.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SettlementController {
    private final SettlementApplicationService settlements;

    public SettlementController(SettlementApplicationService settlements) {
        this.settlements = settlements;
    }

    @GetMapping("/course-sessions/{sessionId}/settlement")
    public ApiResponse<SettlementResponse> getSettlement(
            @PathVariable UUID sessionId,
            Authentication authentication,
            HttpServletRequest request) {
        var result = settlements.getByCourseSession(principal(authentication), sessionId);
        return ApiResponse.of(toResponse(result), requestId(request));
    }

    @PostMapping("/course-sessions/{sessionId}/settlement-calculation")
    public ApiResponse<CalculationResponse> calculate(
            @PathVariable UUID sessionId,
            @Valid @RequestBody CalculationRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        List<AllocationRequest> allocations = body.coachAllocations() == null ? List.of() : body.coachAllocations().stream()
                .map(item -> new AllocationRequest(
                        item.coachProfileId(), item.allocationType(), decimalOrNull(item.allocationValue())))
                .toList();
        var result = settlements.calculate(
                principal(authentication),
                sessionId,
                new SettlementApplicationService.CalculationCommand(decimalOrZero(body.otherAdjustment()), allocations),
                requestId(request));
        return ApiResponse.of(new CalculationResponse(
                result.sessionSettlementId(),
                amount(result.grossReceivable()),
                amount(result.venueCost()),
                amount(result.otherAdjustment()),
                amount(result.distributableAmount()),
                amount(result.coachPayableTotal()),
                "CALCULATED"), requestId(request));
    }

    @PostMapping("/session-settlements/{settlementId}/confirmation")
    public ApiResponse<ConfirmationResponse> confirm(
            @PathVariable UUID settlementId,
            @Valid @RequestBody ConfirmationRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication,
            HttpServletRequest request) {
        var result = settlements.confirm(
                principal(authentication),
                settlementId,
                new SettlementApplicationService.ConfirmationCommand(body.reason()),
                idempotencyKey,
                requestId(request));
        return ApiResponse.of(new ConfirmationResponse(
                result.sessionSettlementId(), result.status(), result.confirmedAt()), requestId(request));
    }

    private static SettlementResponse toResponse(SettlementApplicationService.SettlementView result) {
        return new SettlementResponse(
                result.sessionSettlementId(),
                result.courseSessionId(),
                result.status(),
                amount(result.grossReceivable()),
                amount(result.venueCost()),
                amount(result.otherAdjustment()),
                amount(result.distributableAmount()),
                result.coachSettlements().stream().map(row -> new CoachSettlementResponse(
                        row.coachSettlementId(),
                        row.coachProfileId(),
                        amount(row.payableAmount()),
                        amount(row.paidAmount()),
                        row.payoutStatus(),
                        row.version())).toList(),
                result.version());
    }

    private static AuthenticatedPrincipal principal(Authentication authentication) {
        if (authentication.getPrincipal() instanceof AuthenticatedPrincipal principal) return principal;
        return new AuthenticatedPrincipal(UUID.fromString(authentication.getName()));
    }

    private static String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE);
    }

    private static BigDecimal decimalOrZero(String value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value);
    }

    private static BigDecimal decimalOrNull(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static String amount(BigDecimal value) {
        return value.toPlainString();
    }

    public record CalculationRequest(
            @Pattern(regexp = "^-?[0-9]{1,10}(?:\\.[0-9]{1,2})?$") String otherAdjustment,
            @Size(max = 20) List<@Valid CoachAllocationRequest> coachAllocations) {}

    public record CoachAllocationRequest(
            @NotNull UUID coachProfileId,
            @NotNull AllocationType allocationType,
            @Pattern(regexp = "^[0-9]{1,10}(?:\\.[0-9]{1,4})?$") String allocationValue) {}

    public record CalculationResponse(
            UUID sessionSettlementId,
            String grossReceivable,
            String venueCost,
            String otherAdjustment,
            String distributableAmount,
            String coachPayableTotal,
            String status) {}

    public record ConfirmationRequest(@NotBlank @Size(max = 5000) String reason) {}

    public record ConfirmationResponse(UUID sessionSettlementId, String status, java.time.Instant confirmedAt) {}

    public record SettlementResponse(
            UUID sessionSettlementId,
            UUID courseSessionId,
            String status,
            String grossReceivable,
            String venueCost,
            String otherAdjustment,
            String distributableAmount,
            List<CoachSettlementResponse> coachSettlements,
            long version) {}

    public record CoachSettlementResponse(
            UUID coachSettlementId,
            UUID coachProfileId,
            String payableAmount,
            String paidAmount,
            String payoutStatus,
            long version) {}
}
