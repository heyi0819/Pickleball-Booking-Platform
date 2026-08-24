package com.pickleball.booking.coursematch.application;

import com.pickleball.booking.coach.domain.CoachProfileApprovalStatus;
import com.pickleball.booking.coach.infrastructure.CoachProfileEntity;
import com.pickleball.booking.coach.infrastructure.CoachProfileRepository;
import com.pickleball.booking.coursematch.infrastructure.CourseMatchEntity;
import com.pickleball.booking.coursematch.infrastructure.CourseMatchRepository;
import com.pickleball.booking.coursematch.infrastructure.CourseMatchSessionCoachEntity;
import com.pickleball.booking.coursematch.infrastructure.CourseMatchSessionCoachRepository;
import com.pickleball.booking.coursematch.infrastructure.CourseMatchSessionEntity;
import com.pickleball.booking.coursematch.infrastructure.CourseMatchSessionRepository;
import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.identity.application.IdentityService;
import com.pickleball.booking.shared.application.AuditOutboxService;
import com.pickleball.booking.shared.application.BusinessException;
import jakarta.transaction.Transactional;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CourseMatchInvitationService {
    private final IdentityService identity;
    private final CourseMatchSessionCoachRepository assignments;
    private final CourseMatchSessionRepository sessions;
    private final CourseMatchRepository matches;
    private final CoachProfileRepository coachProfiles;
    private final AuditOutboxService audit;
    private final JdbcTemplate jdbc;

    public CourseMatchInvitationService(
            IdentityService identity,
            CourseMatchSessionCoachRepository assignments,
            CourseMatchSessionRepository sessions,
            CourseMatchRepository matches,
            CoachProfileRepository coachProfiles,
            AuditOutboxService audit,
            JdbcTemplate jdbc) {
        this.identity = identity;
        this.assignments = assignments;
        this.sessions = sessions;
        this.matches = matches;
        this.coachProfiles = coachProfiles;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @Transactional
    public InvitationResult respond(AuthenticatedPrincipal actor, UUID invitationId, ResponseCommand command) {
        identity.requireActiveUser(actor.userId());
        if (command == null || command.status() == null || command.status().isBlank()) {
            throw new BusinessException("VALIDATION_FAILED", "Invitation response status is required");
        }

        // Resolve immutable ownership first, then acquire locks in the same Match-first order
        // used by draft editing and final confirmation to avoid lock-order deadlocks.
        CourseMatchSessionCoachEntity reference = assignments.findById(invitationId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Course match invitation was not found"));
        CourseMatchSessionEntity session = sessions.findById(reference.getCourseMatchSessionId())
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Course match session was not found"));
        CourseMatchEntity match = matches.findLockedById(session.getCourseMatchId())
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Course match was not found"));
        match.requireDraft();
        CourseMatchSessionCoachEntity invitation = assignments.findLockedById(invitationId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Course match invitation was not found"));
        if (!invitation.getCourseMatchSessionId().equals(session.getId())) {
            throw new BusinessException("CONCURRENT_MODIFICATION", "Course match invitation changed concurrently");
        }

        CoachProfileEntity coach = coachProfiles.findById(invitation.getCoachProfileId())
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Coach profile was not found"));
        if (!coach.getUserId().equals(actor.userId())) {
            throw new BusinessException("AUTH_FORBIDDEN", "Only the invited coach may respond to this invitation");
        }
        if (!coach.getOrganizationId().equals(match.getOrganizationId())
                || coach.getApprovalStatus() != CoachProfileApprovalStatus.APPROVED) {
            throw new BusinessException("COACH_NOT_APPROVED", "Coach is not approved for this organization");
        }

        String response = command.status().trim().toUpperCase(Locale.ROOT);
        if ("ACCEPTED".equals(response)) {
            invitation.accept(command.responseNote());
        } else if ("REJECTED".equals(response)) {
            invitation.reject(command.responseNote());
            supersedeConfirmedPricing(match.getId());
        } else {
            throw new BusinessException("VALIDATION_FAILED", "Invitation response must be ACCEPTED or REJECTED");
        }

        audit.record(match.getOrganizationId(), actor.userId(), "COURSE_MATCH_INVITATION_RESPONDED",
                "CourseMatchSessionCoach", invitation.getId(), response);
        return new InvitationResult(match.getId(), session.getId(), invitation);
    }

    private void supersedeConfirmedPricing(UUID courseMatchId) {
        jdbc.update("""
                update course_match_price_snapshots
                set status = 'SUPERSEDED'
                where course_match_id = ? and status = 'CONFIRMED'
                """, courseMatchId);
    }

    public record ResponseCommand(String status, String responseNote) {}
    public record InvitationResult(UUID courseMatchId, UUID courseMatchSessionId,
            CourseMatchSessionCoachEntity invitation) {}
}
