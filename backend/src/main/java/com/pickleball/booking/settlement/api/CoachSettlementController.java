package com.pickleball.booking.settlement.api;

import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.settlement.application.CoachSettlementQueryService;
import com.pickleball.booking.shared.api.ApiResponse;
import com.pickleball.booking.shared.api.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/coach-settlements")
public class CoachSettlementController {
    private final CoachSettlementQueryService coachSettlements;

    public CoachSettlementController(CoachSettlementQueryService coachSettlements) {
        this.coachSettlements = coachSettlements;
    }

    @GetMapping
    public ApiResponse<CoachSettlementListResponse> listMine(
            Authentication authentication,
            HttpServletRequest request) {
        var items = coachSettlements.listMine(principal(authentication)).stream()
                .map(item -> new CoachSettlementItemResponse(
                        item.coachSettlementId(),
                        item.courseSessionId(),
                        amount(item.payableAmount()),
                        amount(item.paidAmount()),
                        amount(item.outstandingAmount()),
                        item.payoutStatus()))
                .toList();
        return ApiResponse.of(new CoachSettlementListResponse(items), requestId(request));
    }

    private static AuthenticatedPrincipal principal(Authentication authentication) {
        if (authentication.getPrincipal() instanceof AuthenticatedPrincipal principal) return principal;
        return new AuthenticatedPrincipal(UUID.fromString(authentication.getName()));
    }

    private static String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE);
    }

    private static String amount(BigDecimal value) {
        return value.toPlainString();
    }

    public record CoachSettlementListResponse(List<CoachSettlementItemResponse> items) {}

    public record CoachSettlementItemResponse(
            UUID coachSettlementId,
            UUID courseSessionId,
            String payableAmount,
            String paidAmount,
            String outstandingAmount,
            String payoutStatus) {}
}
