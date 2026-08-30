package com.pickleball.booking.notification.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.identity.application.IdentityService;
import com.pickleball.booking.identity.domain.RoleCode;
import com.pickleball.booking.notification.infrastructure.AdminOperationsRepository;
import com.pickleball.booking.notification.infrastructure.AdminOperationsRepository.OutboxRow;
import com.pickleball.booking.shared.application.AuditOutboxService;
import com.pickleball.booking.shared.application.BusinessException;
import com.pickleball.booking.shared.application.IdempotencyService;
import com.pickleball.booking.shared.infrastructure.ApiIdempotencyKeyEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminOperationsServiceTest {
    @Mock AdminOperationsRepository repository;
    @Mock IdentityService identity;
    @Mock IdempotencyService idempotency;
    @Mock AuditOutboxService audit;
    private AdminOperationsService service;

    @BeforeEach
    void setUp() {
        service = new AdminOperationsService(repository, identity, idempotency, audit);
    }

    @Test
    void deniesOrganizationOutsideCommitteeScope() {
        UUID actor = UUID.randomUUID();
        UUID organization = UUID.randomUUID();
        when(identity.isAuthorizedForOrganization(new AuthenticatedPrincipal(actor), RoleCode.COMMITTEE, organization))
                .thenReturn(false);

        assertThatThrownBy(() -> service.listOutbox(
                new AuthenticatedPrincipal(actor), organization, "FAILED", false, 0, 50))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.code()).isEqualTo("ORG_SCOPE_DENIED"));
        verify(repository, never()).listOutbox(organization, "FAILED", false, 0, 50);
    }

    @Test
    void rejectsProcessedEventEvenWithAValidIdempotencyKey() {
        UUID actor = UUID.randomUUID();
        UUID organization = UUID.randomUUID();
        UUID event = UUID.randomUUID();
        OutboxRow processed = row(event, organization, "PROCESSED", 1);
        when(repository.findOutboxLocked(event)).thenReturn(Optional.of(processed));
        when(identity.isAuthorizedForOrganization(new AuthenticatedPrincipal(actor), RoleCode.COMMITTEE, organization))
                .thenReturn(true);
        when(idempotency.begin(organization, actor, "ADMIN_OUTBOX_RECOVERY", "key", event + "|reason"))
                .thenReturn(new ApiIdempotencyKeyEntity(organization, actor, "ADMIN_OUTBOX_RECOVERY", "key", "hash"));

        assertThatThrownBy(() -> service.recoverOutbox(
                new AuthenticatedPrincipal(actor), event, "reason", "key", "request"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.code()).isEqualTo("RECOVERY_NOT_ELIGIBLE"));
        verify(repository, never()).recoverOutbox(event, "PROCESSED", false);
    }

    @Test
    void deadEventIsRequeuedWithAttemptResetAndAuditOnly() {
        UUID actor = UUID.randomUUID();
        UUID organization = UUID.randomUUID();
        UUID event = UUID.randomUUID();
        OutboxRow before = row(event, organization, "DEAD", 5);
        OutboxRow after = row(event, organization, "PENDING", 0);
        when(repository.findOutboxLocked(event)).thenReturn(Optional.of(before), Optional.of(after));
        when(identity.isAuthorizedForOrganization(new AuthenticatedPrincipal(actor), RoleCode.COMMITTEE, organization))
                .thenReturn(true);
        var idem = new ApiIdempotencyKeyEntity(organization, actor, "ADMIN_OUTBOX_RECOVERY", "key", "hash");
        when(idempotency.begin(organization, actor, "ADMIN_OUTBOX_RECOVERY", "key", event + "|poison fixed"))
                .thenReturn(idem);
        when(repository.recoverOutbox(event, "DEAD", true)).thenReturn(true);

        service.recoverOutbox(new AuthenticatedPrincipal(actor), event, "poison fixed", "key", "request");

        verify(repository).recoverOutbox(event, "DEAD", true);
        verify(audit).recordAudit(org.mockito.ArgumentMatchers.eq(organization), org.mockito.ArgumentMatchers.eq(actor),
                org.mockito.ArgumentMatchers.eq("OUTBOX_EVENT_REQUEUED"), org.mockito.ArgumentMatchers.eq("OutboxEvent"),
                org.mockito.ArgumentMatchers.eq(event), org.mockito.ArgumentMatchers.eq("poison fixed"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("request"));
    }

    private static OutboxRow row(UUID id, UUID organizationId, String status, int attempts) {
        return new OutboxRow(id, organizationId, "Course", UUID.randomUUID(), "CourseChanged", status, attempts,
                Instant.parse("2026-08-29T00:00:00Z"), "PROCESSED".equals(status) ? Instant.now() : null,
                "FAILED".equals(status) || "DEAD".equals(status) ? "failure" : null, Instant.parse("2026-08-28T00:00:00Z"));
    }
}
