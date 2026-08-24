package com.pickleball.booking.coursematch.api;

import com.pickleball.booking.coursematch.application.CourseMatchService;
import com.pickleball.booking.coursematch.application.CourseMatchService.*;
import com.pickleball.booking.coursematch.infrastructure.CourseMatchSessionEntity;
import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/course-matches")
public class CourseMatchController {
    private final CourseMatchService service;

    public CourseMatchController(CourseMatchService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CourseMatchView> create(
            Authentication authentication,
            @Valid @RequestBody CreateRequest request,
            HttpServletRequest httpRequest) {
        return response(view(service.create(principal(authentication), createCommand(request))), httpRequest);
    }

    @GetMapping("/{courseMatchId}")
    public ApiResponse<CourseMatchView> detail(
            Authentication authentication,
            @PathVariable UUID courseMatchId,
            HttpServletRequest httpRequest) {
        return response(view(service.detail(principal(authentication), courseMatchId)), httpRequest);
    }

    @PatchMapping("/{courseMatchId}")
    public ApiResponse<CourseMatchView> patch(
            Authentication authentication,
            @PathVariable UUID courseMatchId,
            @Valid @RequestBody PatchRequest request,
            HttpServletRequest httpRequest) {
        return response(view(service.patch(principal(authentication), courseMatchId, patchCommand(request))), httpRequest);
    }

    private CreateCommand createCommand(CreateRequest request) {
        return new CreateCommand(
                request.lessonRequestId(),
                coachCommands(request.coachAssignments()),
                sessionCommands(request.sessionPlan()),
                request.participantCount());
    }

    private PatchCommand patchCommand(PatchRequest request) {
        return new PatchCommand(
                request.participantCount(),
                request.coachAssignments() == null ? null : coachCommands(request.coachAssignments()),
                request.sessionPlan() == null ? null : sessionCommands(request.sessionPlan()));
    }

    private List<CoachAssignmentCommand> coachCommands(List<CoachAssignmentRequest> requests) {
        return requests.stream()
                .map(r -> new CoachAssignmentCommand(r.coachProfileId(), r.sessionIndexes()))
                .toList();
    }

    private List<SessionPlanCommand> sessionCommands(List<SessionPlanRequest> requests) {
        return requests.stream()
                .map(r -> new SessionPlanCommand(r.sequenceNo(), r.startAt(), r.endAt(),
                        r.venueId(), r.venueName(), r.venueAddress()))
                .toList();
    }

    private CourseMatchView view(Detail detail) {
        var match = detail.match();
        Map<UUID, CourseMatchSessionEntity> sessionById = detail.sessions().stream()
                .collect(Collectors.toMap(CourseMatchSessionEntity::getId, Function.identity()));
        List<SessionView> sessionViews = detail.sessions().stream()
                .map(s -> new SessionView(s.getId(), s.getSessionIndex(), s.getScheduledStartAt(),
                        s.getScheduledEndAt(), s.getVenueSnapshotType().name(), s.getVenueSnapshotId(),
                        s.getVenueSnapshotName(), s.getVenueSnapshotAddress()))
                .toList();
        List<InvitationView> invitationViews = detail.coachAssignments().stream()
                .map(a -> new InvitationView(
                        a.getId(),
                        a.getCourseMatchSessionId(),
                        sessionById.get(a.getCourseMatchSessionId()).getSessionIndex(),
                        a.getCoachProfileId(),
                        a.getAssignmentOrder(),
                        a.getStatus().name(),
                        a.getInvitationSentAt(),
                        a.getRespondedAt(),
                        a.getResponseNote()))
                .toList();
        return new CourseMatchView(
                match.getId(),
                match.getLessonRequestId(),
                match.getStatus().name(),
                match.getParticipantCount(),
                match.getMinimumParticipantsSnapshot(),
                match.getMaximumParticipantsSnapshot(),
                match.getVersion(),
                sessionViews,
                invitationViews,
                detail.readiness(),
                detail.pricing());
    }

    private AuthenticatedPrincipal principal(Authentication authentication) {
        return (AuthenticatedPrincipal) authentication.getPrincipal();
    }

    private <T> ApiResponse<T> response(T data, HttpServletRequest request) {
        return ApiResponse.of(data, (String) request.getAttribute("requestId"));
    }

    public record CreateRequest(
            @NotNull UUID lessonRequestId,
            @NotEmpty List<@Valid CoachAssignmentRequest> coachAssignments,
            @NotEmpty List<@Valid SessionPlanRequest> sessionPlan,
            @Positive short participantCount) {}

    public record PatchRequest(
            @Positive Short participantCount,
            List<@Valid CoachAssignmentRequest> coachAssignments,
            List<@Valid SessionPlanRequest> sessionPlan) {}

    public record CoachAssignmentRequest(
            @NotNull UUID coachProfileId,
            @NotEmpty List<@Positive Short> sessionIndexes) {}

    public record SessionPlanRequest(
            @Positive short sequenceNo,
            @NotNull Instant startAt,
            @NotNull Instant endAt,
            UUID venueId,
            @Size(max = 150) String venueName,
            @Size(max = 300) String venueAddress) {}

    public record CourseMatchView(
            UUID id,
            UUID lessonRequestId,
            String status,
            short participantCount,
            Short minimumParticipants,
            Short maximumParticipants,
            long version,
            List<SessionView> sessions,
            List<InvitationView> coachInvitations,
            Readiness readiness,
            PriceState pricing) {}

    public record SessionView(
            UUID id,
            short sequenceNo,
            Instant startAt,
            Instant endAt,
            String venueType,
            UUID venueId,
            String venueName,
            String venueAddress) {}

    public record InvitationView(
            UUID invitationId,
            UUID courseMatchSessionId,
            short sessionIndex,
            UUID coachProfileId,
            short assignmentOrder,
            String status,
            Instant invitationSentAt,
            Instant respondedAt,
            String responseNote) {}
}
