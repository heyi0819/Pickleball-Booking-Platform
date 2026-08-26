package com.pickleball.booking.receivable.api;

import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.receivable.application.ReceivableApplicationService;
import com.pickleball.booking.receivable.application.RefundApplicationService;
import com.pickleball.booking.receivable.domain.PaymentMethod;
import com.pickleball.booking.shared.api.ApiResponse;
import com.pickleball.booking.shared.api.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/v1")
public class FinanceController {
    private static final String TWD = "TWD";

    private final ReceivableApplicationService receivables;
    private final RefundApplicationService refunds;

    public FinanceController(ReceivableApplicationService receivables, RefundApplicationService refunds) {
        this.receivables = receivables;
        this.refunds = refunds;
    }

    @PostMapping("/receivables/{receivableId}/payments")
    public ResponseEntity<ApiResponse<PaymentResponse>> recordPayment(
            @PathVariable UUID receivableId,
            @Valid @RequestBody PaymentRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication,
            HttpServletRequest request) {
        var result = receivables.recordPayment(
                principal(authentication),
                receivableId,
                new ReceivableApplicationService.RecordPaymentCommand(
                        money(body.amount()), body.method(), body.paidAt(), body.payerUserId(), body.note()),
                idempotencyKey,
                requestId(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(
                new PaymentResponse(
                        result.paymentId(),
                        result.receivableId(),
                        amount(result.amount()),
                        result.method(),
                        result.paymentStatus(),
                        amount(result.paidTotal()),
                        amount(result.outstandingAmount())),
                requestId(request)));
    }

    @PostMapping("/receivables/{receivableId}/refunds")
    public ResponseEntity<ApiResponse<RefundRequestResponse>> requestRefund(
            @PathVariable UUID receivableId,
            @Valid @RequestBody RefundRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication,
            HttpServletRequest request) {
        var result = refunds.requestRefund(
                principal(authentication),
                receivableId,
                new RefundApplicationService.RequestRefundCommand(
                        body.paymentId(), money(body.amount()), body.reason()),
                idempotencyKey,
                requestId(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(
                new RefundRequestResponse(
                        result.refundId(), result.paymentId(), result.status(), amount(result.amount()), TWD),
                requestId(request)));
    }

    @PostMapping("/refunds/{refundId}/review")
    public ApiResponse<RefundReviewResponse> reviewRefund(
            @PathVariable UUID refundId,
            @Valid @RequestBody RefundReviewRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication,
            HttpServletRequest request) {
        var result = refunds.reviewRefund(
                principal(authentication),
                refundId,
                new RefundApplicationService.ReviewRefundCommand(body.decision(), body.reason()),
                idempotencyKey,
                requestId(request));
        return ApiResponse.of(
                new RefundReviewResponse(
                        result.refundId(), result.status(), result.approvedBy(), result.approvedAt()),
                requestId(request));
    }

    @PostMapping("/refunds/{refundId}/execution")
    public ApiResponse<RefundExecutionResponse> executeRefund(
            @PathVariable UUID refundId,
            @Valid @RequestBody RefundExecutionRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication,
            HttpServletRequest request) {
        var result = refunds.executeRefund(
                principal(authentication),
                refundId,
                new RefundApplicationService.ExecuteRefundCommand(
                        body.method(), body.refundedAt(), body.reference()),
                idempotencyKey,
                requestId(request));
        return ApiResponse.of(
                new RefundExecutionResponse(
                        result.refundId(), result.status(), result.processedBy(), result.refundedAt()),
                requestId(request));
    }

    private static AuthenticatedPrincipal principal(Authentication authentication) {
        return new AuthenticatedPrincipal(UUID.fromString(authentication.getName()));
    }

    private static String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private static String amount(BigDecimal value) {
        return value.toPlainString();
    }

    public record PaymentRequest(
            @NotBlank @Pattern(regexp = "^[0-9]{1,10}(?:\\.[0-9]{1,2})?$") String amount,
            @NotNull PaymentMethod method,
            @NotNull Instant paidAt,
            @NotNull UUID payerUserId,
            @Size(max = 5000) String note) {}

    public record PaymentResponse(
            UUID paymentId,
            UUID receivableId,
            String amount,
            PaymentMethod method,
            String paymentStatus,
            String paidTotal,
            String outstandingAmount) {}

    public record RefundRequest(
            @NotNull UUID paymentId,
            @NotBlank @Pattern(regexp = "^[0-9]{1,10}(?:\\.[0-9]{1,2})?$") String amount,
            @NotBlank @Size(max = 5000) String reason) {}

    public record RefundRequestResponse(
            UUID refundId,
            UUID paymentId,
            String status,
            String amount,
            String currency) {}

    public record RefundReviewRequest(
            @NotNull RefundApplicationService.ReviewDecision decision,
            @NotBlank @Size(max = 5000) String reason) {}

    public record RefundReviewResponse(
            UUID refundId,
            String status,
            UUID approvedBy,
            Instant approvedAt) {}

    public record RefundExecutionRequest(
            @NotNull PaymentMethod method,
            @NotNull Instant refundedAt,
            @Size(max = 100) String reference) {}

    public record RefundExecutionResponse(
            UUID refundId,
            String status,
            UUID processedBy,
            Instant refundedAt) {}
}
