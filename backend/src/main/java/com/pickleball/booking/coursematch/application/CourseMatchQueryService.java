package com.pickleball.booking.coursematch.application;

import com.pickleball.booking.coursematch.infrastructure.CourseMatchRepository;
import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.identity.application.IdentityService;
import com.pickleball.booking.identity.domain.RoleCode;
import com.pickleball.booking.shared.application.BusinessException;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CourseMatchQueryService {
    private final IdentityService identity;
    private final CourseMatchRepository matches;
    private final CourseMatchService courseMatches;

    public CourseMatchQueryService(
            IdentityService identity,
            CourseMatchRepository matches,
            CourseMatchService courseMatches) {
        this.identity = identity;
        this.matches = matches;
        this.courseMatches = courseMatches;
    }

    @Transactional
    public List<CourseMatchService.Detail> listForOrganization(
            AuthenticatedPrincipal actor,
            UUID organizationId) {
        identity.requireActiveUser(actor.userId());
        if (!identity.isAuthorizedForOrganization(actor, RoleCode.COMMITTEE, organizationId)) {
            throw new BusinessException("AUTH_FORBIDDEN", "Committee or platform administrator permission is required");
        }
        return matches.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
                .map(match -> courseMatches.detail(actor, match.getId()))
                .toList();
    }
}
