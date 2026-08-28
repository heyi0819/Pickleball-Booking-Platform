package com.pickleball.booking.settlement.api;

import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.settlement.application.PayoutApplicationService;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payout-batches")
public class PayoutController {
    private final PayoutApplicationService payouts;

    public PayoutController(PayoutApplicationService payouts) {
        this.payouts = payouts;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateResponse> create(
            @Valid @RequestBody CreateRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        var result = payouts.create(
                principal(authentication),
                new PayoutApplicationService.CreateCommand(
                        body.method(),
                        body.payoutDate(),
                        body.items().stream().map(item -> new PayoutApplicationService.CreateItem(
                                item.coachSettlementId(), new BigDecimal(item.amount()))).toList()),
                requestId(request));
        return ApiResponse.of(new CreateResponse(
                result.payoutBatchId(),
                result.batchNo(),
                result.status(),
                result.method(),
                amount(result.totalAmount()),
                result.itemCount()), requestId(request));
    }

    @GetMapping("/{batchId}")
    public ApiResponse<PayoutBatchResponse> get(
            @PathVariable UUID batchId,
            Authentication authentication,
            HttpServletRequest request) {
        var result = payouts.get(principal(authentication), batchId);
        return ApiResponse.of(new PayoutBatchResponse(
                result.payoutBatchId(),
                result.batchNo(),
                result.status(),
                result.payoutDate(),
                result.method(),
                result.currency(),
                amount(result.totalAmount()),
                result.itemCount(),
                result.approvedAt(),
                result.completedAt(),
                result.items().stream().map(item -> new PayoutItemResponse(
                        item.payoutBatchItemId(),
                        item.coachSettlementId(),
                        item.coachProfileId(),
                        amount(item.amount()),
                        item.status(),
                        item.paidAt(),
                        item.referenceNo(),
                        item.failureReason())).toList()), requestId(request));
    }

    @PostMapping("/{batchId}/execution")
    public ApiResponse<ExecutionResponse> execute(
            @PathVariable UUID batchId,
            @Valid @RequestBody ExecutionRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication,
            HttpServletRequest request) {
        var result = payouts.execute(
                principal(authentication),
                batchId,
                new PayoutApplicationService.ExecutionCommand(body.paidAt(), body.referenceNo(), body.reason()),
                idempotencyKey,
                requestId(request));
        return ApiResponse.of(new ExecutionResponse(
                result.payoutBatchId(),
                result.status(),
                amount(result.totalAmount()),
                result.itemCount(),
                result.completedAt()), requestId(request));
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

    public record CreateRequest(
            @Pattern(regexp = "^(CASH|BANK_TRANSFER|OTHER)$") String method,
            @NotNull LocalDate payoutDate,
            @NotNull @Size(min = 1, max = 100) List<@Valid CreateItemRequest> items) {}

    public record CreateItemRequest(
            @NotNull UUID coachSettlementId,
            @NotBlank @Pattern(regexp = "^[0-9]{1,10}(?:\\.[0-9]{1,2})?$") String amount) {}

    public record CreateResponse(
            UUID payoutBatchId,
            String batchNo,
            String status,
            String method,
            String totalAmount,
            int itemCount) {}

    public record ExecutionRequest(
            @NotNull Instant paidAt,
            @Size(max = 100) String referenceNo,
            @NotBlank @Size(max = 5000) String reason) {}

    public record ExecutionResponse(
            UUID payoutBatchId,
            String status,
            String totalAmount,
            int itemCount,
            Instant completedAt) {}

    public record PayoutBatchResponse(
            UUID payoutBatchId,
            String batchNo,
            String status,
            LocalDate payoutDate,
            String method,
            String currency,
            String totalAmount,
            int itemCount,
            Instant approvedAt,
            Instant completedAt,
            List<PayoutItemResponse> items) {}

    public record PayoutItemResponse(
            UUID payoutBatchItemId,
            UUID coachSettlementId,
            UUID coachProfileId,
            String amount,
            String status,
            Instant paidAt,
            String referenceNo,
            String failureReason) {}
}
