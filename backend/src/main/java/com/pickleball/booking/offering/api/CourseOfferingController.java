package com.pickleball.booking.offering.api;

import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.offering.application.CourseOfferingApiCommandService;
import com.pickleball.booking.offering.application.CourseOfferingApplicationService;
import com.pickleball.booking.offering.application.CourseOfferingQueryService;
import com.pickleball.booking.offering.application.CourseOfferingQueryService.OfferingDetail;
import com.pickleball.booking.offering.application.CourseOfferingQueryService.OfferingFilter;
import com.pickleball.booking.offering.application.CourseOfferingQueryService.PageResult;
import com.pickleball.booking.offering.application.CourseOfferingQueryService.RegistrationView;
import com.pickleball.booking.offering.domain.OfferingBillingMode;
import com.pickleball.booking.offering.domain.OfferingScheduleType;
import com.pickleball.booking.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
public class CourseOfferingController {
    private final CourseOfferingApplicationService core;
    private final CourseOfferingApiCommandService commands;
    private final CourseOfferingQueryService queries;

    public CourseOfferingController(
            CourseOfferingApplicationService core,
            CourseOfferingApiCommandService commands,
            CourseOfferingQueryService queries) {
        this.core = core;
        this.commands = commands;
        this.queries = queries;
    }

    @GetMapping("/course-offerings")
    public ApiResponse<PageResult<CourseOfferingQueryService.OfferingSummary>> list(
            Authentication authentication,
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) UUID coachProfileId,
            @RequestParam(required = false) String skillLevel,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            HttpServletRequest httpRequest) {
        return response(queries.list(principal(authentication), new OfferingFilter(
                organizationId, status, from, to, coachProfileId, skillLevel, page, size, sort)), httpRequest);
    }

    @GetMapping("/course-offerings/{offeringId}")
    public ApiResponse<OfferingDetail> detail(
            Authentication authentication,
            @PathVariable UUID offeringId,
            HttpServletRequest httpRequest) {
        return response(queries.detail(principal(authentication), offeringId), httpRequest);
    }

    @PostMapping("/course-offerings")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OfferingDetail> create(
            Authentication authentication,
            @Valid @RequestBody CreateDraftRequest request,
            HttpServletRequest httpRequest) {
        var actor = principal(authentication);
        var offering = core.createDraft(actor, request.organizationId(), draftCommand(request));
        return response(queries.detail(actor, offering.id()), httpRequest);
    }

    @PatchMapping("/course-offerings/{offeringId}")
    public ApiResponse<OfferingDetail> revise(
            Authentication authentication,
            @PathVariable UUID offeringId,
            @Valid @RequestBody UpdateDraftRequest request,
            HttpServletRequest httpRequest) {
        var actor = principal(authentication);
        var offering = core.reviseDraft(actor, offeringId, draftCommand(request));
        return response(queries.detail(actor, offering.id()), httpRequest);
    }

    @PostMapping("/course-offerings/{offeringId}/publication")
    public ApiResponse<OfferingDetail> publish(
            Authentication authentication,
            @PathVariable UUID offeringId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest) {
        var actor = principal(authentication);
        commands.publish(actor, offeringId, idempotencyKey);
        return response(queries.detail(actor, offeringId), httpRequest);
    }

    @PostMapping("/course-offerings/{offeringId}/closure")
    public ApiResponse<OfferingDetail> close(
            Authentication authentication,
            @PathVariable UUID offeringId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest) {
        var actor = principal(authentication);
        commands.close(actor, offeringId, idempotencyKey);
        return response(queries.detail(actor, offeringId), httpRequest);
    }

    @PostMapping("/course-offerings/{offeringId}/confirmation")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ConfirmationView> confirm(
            Authentication authentication,
            @PathVariable UUID offeringId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ConfirmationRequest request,
            HttpServletRequest httpRequest) {
        if (!request.confirm()) {
            throw new IllegalArgumentException("confirm must be true");
        }
        var result = core.confirm(principal(authentication), offeringId, idempotencyKey);
        return response(new ConfirmationView(
                result.offeringId(), result.offeringStatus(), result.courseId(), result.sessionIds(), result.receivableIds()),
                httpRequest);
    }

    @PostMapping("/course-offerings/{offeringId}/cancellation")
    public ApiResponse<OfferingDetail> cancelOffering(
            Authentication authentication,
            @PathVariable UUID offeringId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody(required = false) CancellationRequest request,
            HttpServletRequest httpRequest) {
        var actor = principal(authentication);
        commands.cancelOffering(actor, offeringId, idempotencyKey, request == null ? null : request.reason());
        return response(queries.detail(actor, offeringId), httpRequest);
    }

    @GetMapping("/course-offerings/{offeringId}/registrations")
    public ApiResponse<PageResult<RegistrationView>> registrations(
            Authentication authentication,
            @PathVariable UUID offeringId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        return response(queries.registrations(principal(authentication), offeringId, status, page, size), httpRequest);
    }

    @PostMapping("/course-offerings/{offeringId}/registrations")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RegistrationCommandView> register(
            Authentication authentication,
            @PathVariable UUID offeringId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest) {
        var registration = commands.register(principal(authentication), offeringId, idempotencyKey);
        return response(registrationView(registration), httpRequest);
    }

    @PostMapping("/course-offering-registrations/{registrationId}/cancellation")
    public ApiResponse<RegistrationCommandView> cancelRegistration(
            Authentication authentication,
            @PathVariable UUID registrationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody(required = false) CancellationRequest request,
            HttpServletRequest httpRequest) {
        var registration = commands.cancelRegistration(
                principal(authentication), registrationId, idempotencyKey, request == null ? null : request.reason());
        return response(registrationView(registration), httpRequest);
    }

    @GetMapping("/me/course-offering-registrations")
    public ApiResponse<PageResult<CourseOfferingQueryService.MyRegistrationView>> myRegistrations(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        return response(queries.mine(principal(authentication), page, size), httpRequest);
    }

    private CourseOfferingApplicationService.DraftCommand draftCommand(CreateDraftRequest request) {
        return draftCommand(
                request.coachProfileId(), request.title(), request.description(), request.scheduleType(),
                request.billingMode(), request.skillLevel(), request.minimumParticipants(), request.maximumParticipants(),
                request.registrationOpenAt(), request.registrationCloseAt(), request.sessionPlans());
    }

    private CourseOfferingApplicationService.DraftCommand draftCommand(UpdateDraftRequest request) {
        return draftCommand(
                request.coachProfileId(), request.title(), request.description(), request.scheduleType(),
                request.billingMode(), request.skillLevel(), request.minimumParticipants(), request.maximumParticipants(),
                request.registrationOpenAt(), request.registrationCloseAt(), request.sessionPlans());
    }

    private CourseOfferingApplicationService.DraftCommand draftCommand(
            UUID coachProfileId,
            String title,
            String description,
            String scheduleType,
            String billingMode,
            String skillLevel,
            int minimumParticipants,
            int maximumParticipants,
            Instant registrationOpenAt,
            Instant registrationCloseAt,
            List<SessionPlanRequest> sessionPlans) {
        return new CourseOfferingApplicationService.DraftCommand(
                coachProfileId, title, description,
                OfferingScheduleType.valueOf(scheduleType),
                OfferingBillingMode.valueOf(billingMode),
                skillLevel, minimumParticipants, maximumParticipants,
                registrationOpenAt, registrationCloseAt,
                sessionPlans.stream().map(session -> new CourseOfferingApplicationService.SessionCommand(
                        session.sequenceNo(), session.startAt(), session.endAt(), session.venueId(),
                        session.venueName(), session.venueAddress())).toList());
    }

    private RegistrationCommandView registrationView(com.pickleball.booking.offering.domain.OfferingRegistration registration) {
        return new RegistrationCommandView(
                registration.id(), registration.courseOfferingId(), registration.status().name(),
                registration.registeredAt(), registration.cancelledAt(), registration.cancelReason(),
                registration.convertedCourseMembershipId());
    }

    private AuthenticatedPrincipal principal(Authentication authentication) {
        return (AuthenticatedPrincipal) authentication.getPrincipal();
    }

    private <T> ApiResponse<T> response(T data, HttpServletRequest request) {
        return ApiResponse.of(data, (String) request.getAttribute("requestId"));
    }

    public record CreateDraftRequest(
            @NotNull UUID organizationId,
            @NotBlank @Pattern(regexp = "GROUP") String lessonType,
            @NotNull UUID coachProfileId,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 10000) String description,
            @NotBlank @Pattern(regexp = "SINGLE|RECURRING") String scheduleType,
            @NotBlank @Pattern(regexp = "FULL_COURSE|PER_SESSION") String billingMode,
            @Size(max = 30) String skillLevel,
            @Positive int minimumParticipants,
            @Positive int maximumParticipants,
            @NotNull Instant registrationOpenAt,
            @NotNull Instant registrationCloseAt,
            @NotEmpty List<@Valid SessionPlanRequest> sessionPlans) { }

    public record UpdateDraftRequest(
            @NotNull UUID coachProfileId,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 10000) String description,
            @NotBlank @Pattern(regexp = "SINGLE|RECURRING") String scheduleType,
            @NotBlank @Pattern(regexp = "FULL_COURSE|PER_SESSION") String billingMode,
            @Size(max = 30) String skillLevel,
            @Positive int minimumParticipants,
            @Positive int maximumParticipants,
            @NotNull Instant registrationOpenAt,
            @NotNull Instant registrationCloseAt,
            @NotEmpty List<@Valid SessionPlanRequest> sessionPlans) { }

    public record SessionPlanRequest(
            @Positive int sequenceNo,
            @NotNull Instant startAt,
            @NotNull Instant endAt,
            UUID venueId,
            @NotBlank @Size(max = 150) String venueName,
            @Size(max = 300) String venueAddress) { }

    public record CancellationRequest(@Size(max = 5000) String reason) { }
    public record ConfirmationRequest(boolean confirm) { }

    public record RegistrationCommandView(
            UUID id,
            UUID offeringId,
            String status,
            Instant registeredAt,
            Instant cancelledAt,
            String cancelReason,
            UUID convertedCourseMembershipId) { }

    public record ConfirmationView(
            UUID offeringId,
            String offeringStatus,
            UUID courseId,
            List<UUID> sessionIds,
            List<UUID> receivableIds) { }
}
