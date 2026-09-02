package com.pickleball.booking.receivable.application;

import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.identity.application.IdentityService;
import com.pickleball.booking.identity.domain.RoleCode;
import com.pickleball.booking.receivable.application.FinanceReadViews.*;
import com.pickleball.booking.receivable.infrastructure.AdminFinanceReadRepository;
import com.pickleball.booking.shared.application.BusinessException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AdminFinanceReadService {
    private static final Set<String> RECEIVABLE_STATUSES =
            Set.of("OPEN", "PARTIALLY_PAID", "PAID", "OVERDUE", "CANCELLED", "REFUNDED");
    private static final Set<String> PAYMENT_STATUSES =
            Set.of("COMPLETED", "PARTIALLY_REFUNDED", "REFUNDED", "VOIDED");
    private static final Set<String> REFUND_STATUSES =
            Set.of("PENDING_APPROVAL", "APPROVED", "REJECTED", "COMPLETED", "FAILED", "CANCELLED");
    private final AdminFinanceReadRepository reads;
    private final IdentityService identity;

    public AdminFinanceReadService(AdminFinanceReadRepository reads, IdentityService identity) {
        this.reads = reads;
        this.identity = identity;
    }

    public Page<Receivable> receivables(AuthenticatedPrincipal principal, UUID org, String status,
            UUID member, UUID course, int page, int size) {
        access(principal, org);
        validatePage(page, size);
        return reads.receivables(org, status(status, RECEIVABLE_STATUSES), member, course, page, size);
    }

    public Receivable receivable(AuthenticatedPrincipal principal, UUID org, UUID id) {
        access(principal, org);
        return reads.receivable(org, id).orElseThrow(AdminFinanceReadService::notFound);
    }

    public Page<Payment> payments(AuthenticatedPrincipal principal, UUID org, String status,
            UUID member, UUID receivable, int page, int size) {
        access(principal, org);
        validatePage(page, size);
        return reads.payments(org, status(status, PAYMENT_STATUSES), member, receivable, page, size);
    }

    public Payment payment(AuthenticatedPrincipal principal, UUID org, UUID id) {
        access(principal, org);
        return reads.payment(org, id).orElseThrow(AdminFinanceReadService::notFound);
    }

    public Page<Refund> refunds(AuthenticatedPrincipal principal, UUID org, String status,
            UUID member, UUID payment, int page, int size) {
        access(principal, org);
        validatePage(page, size);
        return reads.refunds(org, status(status, REFUND_STATUSES), member, payment, page, size);
    }

    public Refund refund(AuthenticatedPrincipal principal, UUID org, UUID id) {
        access(principal, org);
        return reads.refund(org, id).orElseThrow(AdminFinanceReadService::notFound);
    }

    private void access(AuthenticatedPrincipal principal, UUID org) {
        if (org == null) {
            throw new BusinessException("VALIDATION_FAILED", "An explicit organization context is required");
        }
        if (!identity.isAuthorizedForOrganization(principal, RoleCode.COMMITTEE, org)) {
            throw new BusinessException("ORG_SCOPE_DENIED", "Access denied for the selected organization");
        }
        if (!reads.organizationExists(org)) throw notFound();
    }

    private static String status(String value, Set<String> allowed) {
        if (value != null && !allowed.contains(value)) {
            throw new BusinessException("VALIDATION_FAILED", "Unsupported finance status filter");
        }
        return value;
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException("VALIDATION_FAILED", "Page must be nonnegative and size between 1 and 100");
        }
    }

    private static BusinessException notFound() {
        return new BusinessException("RESOURCE_NOT_FOUND", "Finance resource not found in the selected organization");
    }
}
