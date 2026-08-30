package com.pickleball.booking.notification.application;

import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.identity.application.IdentityService;
import com.pickleball.booking.identity.domain.RoleCode;
import com.pickleball.booking.notification.infrastructure.AdminOperationsRepository;
import com.pickleball.booking.notification.infrastructure.AdminOperationsRepository.NotificationRow;
import com.pickleball.booking.notification.infrastructure.AdminOperationsRepository.OutboxRow;
import com.pickleball.booking.shared.application.AuditOutboxService;
import com.pickleball.booking.shared.application.BusinessException;
import com.pickleball.booking.shared.application.IdempotencyService;
import jakarta.transaction.Transactional;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AdminOperationsService {
    private static final Set<String> OUTBOX_STATUSES = Set.of("PENDING", "PROCESSING", "PROCESSED", "FAILED", "DEAD");
    private static final Set<String> NOTIFICATION_STATUSES = Set.of("PENDING", "SENDING", "SENT", "FAILED", "DEAD");
    private static final Set<String> RECOVERABLE_STATUSES = Set.of("FAILED", "DEAD");
    private static final String OUTBOX_OPERATION = "ADMIN_OUTBOX_RECOVERY";
    private static final String NOTIFICATION_OPERATION = "ADMIN_NOTIFICATION_RECOVERY";

    private final AdminOperationsRepository repository;
    private final IdentityService identity;
    private final IdempotencyService idempotency;
    private final AuditOutboxService audit;

    public AdminOperationsService(
            AdminOperationsRepository repository,
            IdentityService identity,
            IdempotencyService idempotency,
            AuditOutboxService audit) {
        this.repository = repository;
        this.identity = identity;
        this.idempotency = idempotency;
        this.audit = audit;
    }

    @Transactional
    public AdminOperationsRepository.Page<OutboxRow> listOutbox(
            AuthenticatedPrincipal principal, UUID organizationId, String status, boolean retryDue, int page, int size) {
        requireOperationsAccess(principal, organizationId);
        return repository.listOutbox(organizationId, normalizeStatus(status, OUTBOX_STATUSES), retryDue, page, size);
    }

    @Transactional
    public AdminOperationsRepository.Page<NotificationRow> listNotifications(
            AuthenticatedPrincipal principal, UUID organizationId, String status, boolean retryDue, int page, int size) {
        requireOperationsAccess(principal, organizationId);
        return repository.listNotifications(
                organizationId, normalizeStatus(status, NOTIFICATION_STATUSES), retryDue, page, size);
    }

    @Transactional
    public OutboxRow recoverOutbox(
            AuthenticatedPrincipal principal, UUID eventId, String reason, String idempotencyKey, String requestId) {
        OutboxRow before = repository.findOutboxLocked(eventId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Outbox event was not found"));
        requireOperationsAccess(principal, before.organizationId());
        String normalizedReason = requireReason(reason);
        var idem = idempotency.begin(before.organizationId(), principal.userId(), OUTBOX_OPERATION, idempotencyKey,
                eventId + "|" + normalizedReason);
        if (idem.getResultResourceId() != null) return before;
        requireRecoverable(before.status(), "Outbox event");
        boolean requeue = "DEAD".equals(before.status());
        if (!repository.recoverOutbox(eventId, before.status(), requeue)) {
            throw new BusinessException("CONCURRENT_MODIFICATION", "Outbox event changed during recovery");
        }
        OutboxRow after = repository.findOutboxLocked(eventId).orElseThrow();
        audit.recordAudit(before.organizationId(), principal.userId(),
                requeue ? "OUTBOX_EVENT_REQUEUED" : "OUTBOX_EVENT_RETRY_REQUESTED",
                "OutboxEvent", eventId, normalizedReason, auditView(before), auditView(after), requestId);
        idem.complete("OutboxEvent", eventId, 200);
        return after;
    }

    @Transactional
    public NotificationRow recoverNotification(
            AuthenticatedPrincipal principal, UUID notificationId, String reason, String idempotencyKey, String requestId) {
        NotificationRow before = repository.findNotificationLocked(notificationId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Notification was not found"));
        requireOperationsAccess(principal, before.organizationId());
        String normalizedReason = requireReason(reason);
        var idem = idempotency.begin(before.organizationId(), principal.userId(), NOTIFICATION_OPERATION, idempotencyKey,
                notificationId + "|" + normalizedReason);
        if (idem.getResultResourceId() != null) return before;
        requireRecoverable(before.status(), "Notification");
        boolean requeue = "DEAD".equals(before.status());
        if (!repository.recoverNotification(notificationId, before.status(), requeue)) {
            throw new BusinessException("CONCURRENT_MODIFICATION", "Notification changed during recovery");
        }
        NotificationRow after = repository.findNotificationLocked(notificationId).orElseThrow();
        audit.recordAudit(before.organizationId(), principal.userId(),
                requeue ? "NOTIFICATION_REQUEUED" : "NOTIFICATION_REDELIVERY_REQUESTED",
                "Notification", notificationId, normalizedReason, auditView(before), auditView(after), requestId);
        idem.complete("Notification", notificationId, 200);
        return after;
    }

    private void requireOperationsAccess(AuthenticatedPrincipal principal, UUID organizationId) {
        if (organizationId == null) throw new BusinessException("VALIDATION_FAILED", "organizationId is required");
        identity.requireActiveUser(principal.userId());
        if (!identity.isAuthorizedForOrganization(principal, RoleCode.COMMITTEE, organizationId)) {
            throw new BusinessException("ORG_SCOPE_DENIED",
                    "Committee or platform administrator access is required for this organization");
        }
    }

    private static String normalizeStatus(String status, Set<String> allowed) {
        if (status == null || status.isBlank()) return null;
        String normalized = status.trim().toUpperCase();
        if (!allowed.contains(normalized)) throw new BusinessException("VALIDATION_FAILED", "Unsupported status filter");
        return normalized;
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) throw new BusinessException("VALIDATION_FAILED", "reason is required");
        String normalized = reason.trim();
        if (normalized.length() > 1000) throw new BusinessException("VALIDATION_FAILED", "reason supports at most 1000 characters");
        return normalized;
    }

    private static void requireRecoverable(String status, String label) {
        if (!RECOVERABLE_STATUSES.contains(status)) {
            throw new BusinessException("RECOVERY_NOT_ELIGIBLE", label + " is not FAILED or DEAD");
        }
    }

    private static Map<String, Object> auditView(OutboxRow row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("status", row.status());
        view.put("attemptCount", row.attemptCount());
        view.put("availableAt", row.availableAt());
        view.put("lastError", row.lastError());
        return view;
    }

    private static Map<String, Object> auditView(NotificationRow row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("status", row.status());
        view.put("attemptCount", row.attemptCount());
        view.put("nextAttemptAt", row.nextAttemptAt());
        view.put("lastErrorCode", row.lastErrorCode());
        view.put("lastErrorMessage", row.lastErrorMessage());
        return view;
    }
}
