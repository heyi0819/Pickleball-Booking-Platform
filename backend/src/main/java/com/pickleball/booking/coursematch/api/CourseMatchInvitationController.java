package com.pickleball.booking.coursematch.api;

import com.pickleball.booking.coursematch.application.CourseMatchInvitationService;
import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/course-match-invitations")
public class CourseMatchInvitationController {
    private final CourseMatchInvitationService service;

    public CourseMatchInvitationController(CourseMatchInvitationService service) {
        this.service = service;
    }

    @PostMapping("/{invitationId}/response")
    public ApiResponse<InvitationResponseView> respond(
            Authentication authentication,
            @PathVariable UUID invitationId,
            @Valid @RequestBody InvitationResponseRequest request,
            HttpServletRequest httpRequest) {
        var result = service.respond(
                principal(authentication), invitationId,
                new CourseMatchInvitationService.ResponseCommand(request.status(), request.responseNote()));
        var invitation = result.invitation();
        return ApiResponse.of(new InvitationResponseView(
                invitation.getId(), result.courseMatchId(), result.courseMatchSessionId(),
                invitation.getCoachProfileId(), invitation.getStatus().name(), invitation.getRespondedAt(),
                invitation.getResponseNote()), (String) httpRequest.getAttribute("requestId"));
    }

    private AuthenticatedPrincipal principal(Authentication authentication) {
        return (AuthenticatedPrincipal) authentication.getPrincipal();
    }

    public record InvitationResponseRequest(
            @NotBlank @Pattern(regexp = "ACCEPTED|REJECTED") String status,
            @Size(max = 5000) String responseNote) {}

    public record InvitationResponseView(
            UUID invitationId,
            UUID courseMatchId,
            UUID courseMatchSessionId,
            UUID coachProfileId,
            String status,
            Instant respondedAt,
            String responseNote) {}
}
