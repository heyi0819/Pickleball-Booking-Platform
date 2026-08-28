package com.pickleball.booking.settlement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.identity.application.IdentityService;
import com.pickleball.booking.identity.domain.RoleCode;
import com.pickleball.booking.settlement.infrastructure.CoachSettlementRepository;
import com.pickleball.booking.shared.application.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CoachSettlementQueryServiceTest {
    @Mock private IdentityService identity;
    @Mock private CoachSettlementRepository coachSettlements;
    @InjectMocks private CoachSettlementQueryService service;

    @Test
    void returnsOnlyOwnedSettlementsInsideActiveCoachOrganizationScope() {
        UUID userId = UUID.randomUUID();
        UUID allowedOrganization = UUID.randomUUID();
        UUID deniedOrganization = UUID.randomUUID();
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(userId);

        when(identity.roles(principal)).thenReturn(List.of(
                new IdentityService.RoleView(RoleCode.COACH, allowedOrganization, "ORG-A", "Org A")));
        when(coachSettlements.findOwnedByUserId(userId)).thenReturn(List.of(
                row(allowedOrganization, "1500.00", "500.00", "PARTIALLY_PAID"),
                row(deniedOrganization, "900.00", "0.00", "READY")));

        var result = service.listMine(principal);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().payableAmount()).isEqualByComparingTo("1500.00");
        assertThat(result.getFirst().paidAmount()).isEqualByComparingTo("500.00");
        assertThat(result.getFirst().outstandingAmount()).isEqualByComparingTo("1000.00");
        assertThat(result.getFirst().payoutStatus()).isEqualTo("PARTIALLY_PAID");
    }

    @Test
    void rejectsUserWithoutActiveCoachRole() {
        UUID userId = UUID.randomUUID();
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(userId);
        when(identity.roles(principal)).thenReturn(List.of(
                new IdentityService.RoleView(RoleCode.STUDENT, UUID.randomUUID(), "ORG-A", "Org A")));

        assertThatThrownBy(() -> service.listMine(principal))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo("ROLE_FORBIDDEN"));
    }

    private static CoachSettlementRepository.MyCoachSettlementRow row(
            UUID organizationId,
            String payable,
            String paid,
            String status) {
        UUID coachSettlementId = UUID.randomUUID();
        UUID courseSessionId = UUID.randomUUID();
        return new CoachSettlementRepository.MyCoachSettlementRow() {
            @Override public UUID getCoachSettlementId() { return coachSettlementId; }
            @Override public UUID getOrganizationId() { return organizationId; }
            @Override public UUID getCourseSessionId() { return courseSessionId; }
            @Override public BigDecimal getPayableAmount() { return new BigDecimal(payable); }
            @Override public BigDecimal getPaidAmount() { return new BigDecimal(paid); }
            @Override public String getPayoutStatus() { return status; }
        };
    }
}
