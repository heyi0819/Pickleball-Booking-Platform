package com.pickleball.booking.notification.api;

import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.notification.application.AdminOperationsService;
import com.pickleball.booking.notification.infrastructure.AdminOperationsRepository.NotificationRow;
import com.pickleball.booking.notification.infrastructure.AdminOperationsRepository.OutboxRow;
import com.pickleball.booking.shared.api.ApiResponse;
import com.pickleball.booking.shared.api.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminOperationsController {
    private final AdminOperationsService operations;

    public AdminOperationsController(AdminOperationsService operations) {
        this.operations = operations;
    }

    @GetMapping("/outbox-events")
    public ApiResponse<PageResponse<OutboxEventResponse>> listOutbox(
            @RequestParam UUID organizationId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") boolean retryDue,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
            Authentication authentication,
            HttpServletRequest request) {
        var result = operations.listOutbox(principal(authentication), organizationId, status, retryDue, page, size);
        return ApiResponse.of(new PageResponse<>(result.items().stream().map(AdminOperationsController::outbox).toList(),
                result.page(), result.size(), result.totalElements()), requestId(request));
    }

    @PostMapping("/outbox-events/{eventId}/retry")
    public ApiResponse<OutboxEventResponse> retryOutbox(
            @PathVariable UUID eventId,
            @Valid @RequestBody RecoveryRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication,
            HttpServletRequest request) {
        var result = operations.recoverOutbox(
                principal(authentication), eventId, body.reason(), idempotencyKey, requestId(request));
        return ApiResponse.of(outbox(result), requestId(request));
    }

    @GetMapping("/notifications")
    public ApiResponse<PageResponse<NotificationResponse>> listNotifications(
            @RequestParam UUID organizationId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") boolean retryDue,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
            Authentication authentication,
            HttpServletRequest request) {
        var result = operations.listNotifications(
                principal(authentication), organizationId, status, retryDue, page, size);
        return ApiResponse.of(new PageResponse<>(result.items().stream().map(AdminOperationsController::notification).toList(),
                result.page(), result.size(), result.totalElements()), requestId(request));
    }

    @PostMapping("/notifications/{notificationId}/retry")
    public ApiResponse<NotificationResponse> retryNotification(
            @PathVariable UUID notificationId,
            @Valid @RequestBody RecoveryRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication,
            HttpServletRequest request) {
        var result = operations.recoverNotification(
                principal(authentication), notificationId, body.reason(), idempotencyKey, requestId(request));
        return ApiResponse.of(notification(result), requestId(request));
    }

    private static OutboxEventResponse outbox(OutboxRow row) {
        return new OutboxEventResponse(row.id(), row.organizationId(), row.aggregateType(), row.aggregateId(),
                row.eventType(), row.status(), row.attemptCount(), row.availableAt(), row.processedAt(),
                row.lastError(), row.createdAt());
    }

    private static NotificationResponse notification(NotificationRow row) {
        return new NotificationResponse(row.id(), row.organizationId(), row.notificationTargetId(),
                row.recipientUserId(), row.channel(), row.templateCode(), row.businessType(), row.businessId(),
                row.status(), row.attemptCount(), row.nextAttemptAt(), row.sentAt(), row.lastErrorCode(),
                row.lastErrorMessage(), row.createdAt(), row.updatedAt());
    }

    private static AuthenticatedPrincipal principal(Authentication authentication) {
        if (authentication.getPrincipal() instanceof AuthenticatedPrincipal principal) return principal;
        return new AuthenticatedPrincipal(UUID.fromString(authentication.getName()));
    }

    private static String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE);
    }

    public record RecoveryRequest(@NotBlank @Size(max = 1000) String reason) {}
    public record PageResponse<T>(List<T> items, int page, int size, long totalElements) {}
    public record OutboxEventResponse(UUID id, UUID organizationId, String aggregateType, UUID aggregateId,
            String eventType, String status, int attemptCount, Instant availableAt, Instant processedAt,
            String lastError, Instant createdAt) {}
    public record NotificationResponse(UUID id, UUID organizationId, UUID notificationTargetId,
            UUID recipientUserId, String channel, String templateCode, String businessType, UUID businessId,
            String status, int attemptCount, Instant nextAttemptAt, Instant sentAt, String lastErrorCode,
            String lastErrorMessage, Instant createdAt, Instant updatedAt) {}
}
