package com.pickleball.booking.receivable.api;

import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.receivable.application.AdminFinanceReadService;
import com.pickleball.booking.receivable.application.FinanceReadViews.*;
import com.pickleball.booking.shared.api.ApiResponse;
import com.pickleball.booking.shared.api.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminFinanceReadController {
    private final AdminFinanceReadService reads;

    public AdminFinanceReadController(AdminFinanceReadService reads) {
        this.reads = reads;
    }

    @GetMapping("/receivables")
    public ApiResponse<Page<Receivable>> receivables(
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID memberId,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication, HttpServletRequest request) {
        return ApiResponse.of(reads.receivables(principal(authentication), organizationId, status,
                memberId, courseId, page, size), requestId(request));
    }

    @GetMapping("/receivables/{id}")
    public ApiResponse<Receivable> receivable(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID organizationId,
            Authentication authentication, HttpServletRequest request) {
        return ApiResponse.of(reads.receivable(principal(authentication), organizationId, id), requestId(request));
    }

    @GetMapping("/payments")
    public ApiResponse<Page<Payment>> payments(
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID memberId,
            @RequestParam(required = false) UUID receivableId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication, HttpServletRequest request) {
        return ApiResponse.of(reads.payments(principal(authentication), organizationId, status,
                memberId, receivableId, page, size), requestId(request));
    }

    @GetMapping("/payments/{id}")
    public ApiResponse<Payment> payment(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID organizationId,
            Authentication authentication, HttpServletRequest request) {
        return ApiResponse.of(reads.payment(principal(authentication), organizationId, id), requestId(request));
    }

    @GetMapping("/refunds")
    public ApiResponse<Page<Refund>> refunds(
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID memberId,
            @RequestParam(required = false) UUID paymentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication, HttpServletRequest request) {
        return ApiResponse.of(reads.refunds(principal(authentication), organizationId, status,
                memberId, paymentId, page, size), requestId(request));
    }

    @GetMapping("/refunds/{id}")
    public ApiResponse<Refund> refund(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID organizationId,
            Authentication authentication, HttpServletRequest request) {
        return ApiResponse.of(reads.refund(principal(authentication), organizationId, id), requestId(request));
    }

    private static AuthenticatedPrincipal principal(Authentication authentication) {
        if (authentication.getPrincipal() instanceof AuthenticatedPrincipal principal) return principal;
        return new AuthenticatedPrincipal(UUID.fromString(authentication.getName()));
    }

    private static String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE);
    }
}
