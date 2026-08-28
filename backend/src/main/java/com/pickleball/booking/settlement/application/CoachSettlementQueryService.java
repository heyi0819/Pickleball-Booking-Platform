package com.pickleball.booking.settlement.application;

import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.identity.application.IdentityService;
import com.pickleball.booking.identity.domain.RoleCode;
import com.pickleball.booking.settlement.infrastructure.CoachSettlementRepository;
import com.pickleball.booking.shared.application.BusinessException;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class CoachSettlementQueryService {
    private final IdentityService identity;
    private final CoachSettlementRepository coachSettlements;

    public CoachSettlementQueryService(
            IdentityService identity,
            CoachSettlementRepository coachSettlements) {
        this.identity = identity;
        this.coachSettlements = coachSettlements;
    }

    @Transactional
    public java.util.List<CoachSettlementItem> listMine(AuthenticatedPrincipal principal) {
        var roles = identity.roles(principal);
        boolean globalCoach = roles.stream()
                .anyMatch(role -> role.roleCode() == RoleCode.COACH && role.organizationId() == null);
        Set<UUID> coachOrganizations = roles.stream()
                .filter(role -> role.roleCode() == RoleCode.COACH && role.organizationId() != null)
                .map(IdentityService.RoleView::organizationId)
                .collect(Collectors.toUnmodifiableSet());

        if (!globalCoach && coachOrganizations.isEmpty()) {
            throw new BusinessException("ROLE_FORBIDDEN", "An active COACH role is required");
        }

        return coachSettlements.findOwnedByUserId(principal.userId()).stream()
                .filter(row -> globalCoach || coachOrganizations.contains(row.getOrganizationId()))
                .map(row -> new CoachSettlementItem(
                        row.getCoachSettlementId(),
                        row.getCourseSessionId(),
                        row.getPayableAmount(),
                        row.getPaidAmount(),
                        row.getPayableAmount().subtract(row.getPaidAmount()).setScale(2),
                        row.getPayoutStatus()))
                .toList();
    }

    public record CoachSettlementItem(
            UUID coachSettlementId,
            UUID courseSessionId,
            BigDecimal payableAmount,
            BigDecimal paidAmount,
            BigDecimal outstandingAmount,
            String payoutStatus) {}
}
