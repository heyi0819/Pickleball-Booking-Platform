package com.pickleball.booking.course.api;

import com.pickleball.booking.course.application.CourseOperationsApplicationService;
import com.pickleball.booking.course.application.CourseOperationsApplicationService.AttendanceDecision;
import com.pickleball.booking.course.application.CourseOperationsApplicationService.ReviewDecision;
import com.pickleball.booking.course.application.CourseOperationsQueryService;
import com.pickleball.booking.course.application.CourseOperationsQueryService.CourseDetail;
import com.pickleball.booking.course.application.CourseOperationsQueryService.CourseFilter;
import com.pickleball.booking.course.application.CourseOperationsQueryService.CourseSummary;
import com.pickleball.booking.course.application.CourseOperationsQueryService.PageResult;
import com.pickleball.booking.course.application.CourseOperationsQueryService.SessionSummary;
import com.pickleball.booking.course.domain.CourseCancellationRequest;
import com.pickleball.booking.course.domain.SessionChangeRequest;
import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CourseOperationsController {
    private final CourseOperationsApplicationService commands;
    private final CourseOperationsQueryService queries;

    public CourseOperationsController(
            CourseOperationsApplicationService commands,
            CourseOperationsQueryService queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @GetMapping("/courses")
    public ApiResponse<PageResult<CourseSummary>> courses(
            Authentication authentication,
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) UUID coachProfileId,
            @RequestParam(required = false) UUID studentUserId,
            @RequestParam(required = false) String courseType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            HttpServletRequest httpRequest) {
        return response(queries.list(
                principal(authentication),
                new CourseFilter(
                        organizationId, status, from, to, coachProfileId, studentUserId,
                        courseType, page, size, sort)), httpRequest);
    }

    @GetMapping("/courses/{courseId}")
    public ApiResponse<CourseDetail> course(
            Authentication authentication,
            @PathVariable UUID courseId,
            HttpServletRequest httpRequest) {
        return response(queries.detail(principal(authentication), courseId), httpRequest);
    }

    @GetMapping("/courses/{courseId}/sessions")
    public ApiResponse<List<SessionSummary>> sessions(
            Authentication authentication,
            @PathVariable UUID courseId,
            HttpServletRequest httpRequest) {
        return response(queries.sessions(principal(authentication), courseId), httpRequest);
    }

    @GetMapping("/course-sessions/{sessionId}")
    public ApiResponse<SessionSummary> session(
            Authentication authentication,
            @PathVariable UUID sessionId,
            HttpServletRequest httpRequest) {
        return response(queries.session(principal(authentication), sessionId), httpRequest);
    }

    @PostMapping("/session-enrollments/{enrollmentId}/cancellation")
    public ApiResponse<EnrollmentCancellationView> cancelEnrollment(
            Authentication authentication,
            @PathVariable UUID enrollmentId,
            @Valid @RequestBody(required = false) CancellationRequest request,
            HttpServletRequest httpRequest) {
        var result = commands.cancelEnrollment(
                principal(authentication), enrollmentId,
                request == null ? null : request.reason(), requestId(httpRequest));
        return response(new EnrollmentCancellationView(
                result.enrollment().id(), result.enrollment().status().name(),
                result.enrollment().cancelledAt(), result.courseSessionStatus().name()), httpRequest);
    }

    @PostMapping("/course-sessions/{sessionId}/coach-cancellation-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CoachCancellationRequestView> requestCoachCancellation(
            Authentication authentication,
            @PathVariable UUID sessionId,
            @Valid @RequestBody CoachCancellationRequestBody request,
            HttpServletRequest httpRequest) {
        CourseCancellationRequest created = commands.requestCoachCancellation(
                principal(authentication), sessionId, request.reason(), requestId(httpRequest));
        return response(coachCancellation(created), httpRequest);
    }

    @PostMapping("/coach-cancellation-requests/{requestId}/review")
    public ApiResponse<CoachCancellationReviewView> reviewCoachCancellation(
            Authentication authentication,
            @PathVariable UUID requestId,
            @Valid @RequestBody ReviewRequest request,
            HttpServletRequest httpRequest) {
        var result = commands.reviewCoachCancellation(
                principal(authentication), requestId, reviewDecision(request.decision()),
                request.reason(), requestId(httpRequest));
        return response(new CoachCancellationReviewView(
                result.request().id(), result.request().status().name(),
                result.request().courseSessionId(), result.session().status().name(),
                result.request().reviewedAt()), httpRequest);
    }

    @PostMapping("/course-sessions/{sessionId}/change-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SessionChangeRequestView> requestChange(
            Authentication authentication,
            @PathVariable UUID sessionId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RescheduleRequest request,
            HttpServletRequest httpRequest) {
        SessionChangeRequest created = commands.requestReschedule(
                principal(authentication), sessionId,
                request.proposedStartAt(), request.proposedEndAt(), request.reason(),
                idempotencyKey, requestId(httpRequest));
        return response(changeRequest(created), httpRequest);
    }

    @PostMapping("/session-change-requests/{requestId}/review")
    public ApiResponse<RescheduleView> reviewChange(
            Authentication authentication,
            @PathVariable UUID requestId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReviewRequest request,
            HttpServletRequest httpRequest) {
        var result = commands.reviewReschedule(
                principal(authentication), requestId, reviewDecision(request.decision()), request.reason(),
                idempotencyKey, requestId(httpRequest));
        return response(reschedule(result), httpRequest);
    }

    @PostMapping("/course-sessions/{sessionId}/reschedule")
    public ApiResponse<RescheduleView> directReschedule(
            Authentication authentication,
            @PathVariable UUID sessionId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody DirectRescheduleRequest request,
            HttpServletRequest httpRequest) {
        var result = commands.directReschedule(
                principal(authentication), sessionId,
                request.startAt(), request.endAt(), request.reason(),
                idempotencyKey, requestId(httpRequest));
        return response(reschedule(result), httpRequest);
    }

    private CoachCancellationRequestView coachCancellation(CourseCancellationRequest request) {
        return new CoachCancellationRequestView(
                request.id(), request.courseSessionId(), request.status().name(),
                request.reason(), request.createdAt(), request.reviewedAt());
    }

    private SessionChangeRequestView changeRequest(SessionChangeRequest request) {
        return new SessionChangeRequestView(
                request.id(), request.courseSessionId(), request.status().name(), request.type().name(),
                request.proposedStartAt(), request.proposedEndAt(), request.reason(),
                request.decidedBy(), request.decidedAt(), request.decisionReason());
    }

    private RescheduleView reschedule(CourseOperationsApplicationService.RescheduleResult result) {
        return new RescheduleView(
                result.request().id(), result.request().status().name(), result.session().id(),
                result.session().status().name(), result.session().scheduledStartAt(), result.session().scheduledEndAt());
    }

    private ReviewDecision reviewDecision(String value) {
        return ReviewDecision.valueOf(value);
    }

    private AuthenticatedPrincipal principal(Authentication authentication) {
        return (AuthenticatedPrincipal) authentication.getPrincipal();
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute("requestId");
    }

    private <T> ApiResponse<T> response(T data, HttpServletRequest request) {
        return ApiResponse.of(data, requestId(request));
    }

    public record CancellationRequest(@Size(max = 5000) String reason) { }

    public record CoachCancellationRequestBody(
            @NotBlank @Size(max = 5000) String reason) { }

    public record ReviewRequest(
            @NotBlank @Pattern(regexp = "APPROVE|REJECT") String decision,
            @NotBlank @Size(max = 5000) String reason) { }

    public record RescheduleRequest(
            @NotBlank @Pattern(regexp = "RESCHEDULE") String requestType,
            @NotNull Instant proposedStartAt,
            @NotNull Instant proposedEndAt,
            @NotBlank @Size(max = 5000) String reason) { }

    public record DirectRescheduleRequest(
            @NotNull Instant startAt,
            @NotNull Instant endAt,
            @NotBlank @Size(max = 5000) String reason) { }

    public record EnrollmentCancellationView(
            UUID enrollmentId,
            String status,
            Instant cancelledAt,
            String courseSessionStatus) { }

    public record CoachCancellationRequestView(
            UUID requestId,
            UUID sessionId,
            String status,
            String reason,
            Instant createdAt,
            Instant reviewedAt) { }

    public record CoachCancellationReviewView(
            UUID requestId,
            String status,
            UUID sessionId,
            String sessionStatus,
            Instant reviewedAt) { }

    public record SessionChangeRequestView(
            UUID changeRequestId,
            UUID sessionId,
            String status,
            String requestType,
            Instant proposedStartAt,
            Instant proposedEndAt,
            String reason,
            UUID decidedBy,
            Instant decidedAt,
            String decisionReason) { }

    public record RescheduleView(
            UUID changeRequestId,
            String status,
            UUID sessionId,
            String sessionStatus,
            Instant scheduledStartAt,
            Instant scheduledEndAt) { }
}
