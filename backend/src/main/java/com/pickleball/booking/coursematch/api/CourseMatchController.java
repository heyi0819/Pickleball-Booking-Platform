package com.pickleball.booking.coursematch.api;

import com.pickleball.booking.coursematch.application.CourseMatchService;
import com.pickleball.booking.coursematch.application.CourseMatchService.*;
import com.pickleball.booking.coursematch.application.MatchPricingService;
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
    private final MatchPricingService pricing;

    public CourseMatchController(CourseMatchService service, MatchPricingService pricing) {
        this.service = service;
        this.pricing = pricing;
    }

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

    @PostMapping("/{courseMatchId}/pricing-preview")
    public ApiResponse<PricingPreviewView> pricingPreview(
            Authentication authentication,
            @PathVariable UUID courseMatchId,
            HttpServletRequest httpRequest) {
        return response(pricingPreviewView(pricing.preview(principal(authentication), courseMatchId)), httpRequest);
    }

    @PostMapping("/{courseMatchId}/pricing-confirmation")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PriceSnapshotView> pricingConfirmation(
            Authentication authentication,
            @PathVariable UUID courseMatchId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PricingConfirmationRequest request,
            HttpServletRequest httpRequest) {
        var command = new MatchPricingService.ConfirmPricingCommand(
                request.acceptedTotalAmount(), request.currency(), request.pricingFingerprint(), request.confirmationNote());
        return response(priceSnapshotView(pricing.confirm(
                principal(authentication), courseMatchId, idempotencyKey, command)), httpRequest);
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

    private PricingPreviewView pricingPreviewView(MatchPricingService.PricingPreview preview) {
        return new PricingPreviewView(
                preview.courseMatchId(), preview.currency(), preview.billingMode(),
                preview.totalAmount().toPlainString(),
                preview.breakdown().stream().map(item -> new PricingItemView(
                        item.courseMatchSessionId(), item.itemType(), item.description(),
                        item.quantity().stripTrailingZeros().toPlainString(), item.unitAmount().toPlainString(),
                        item.lineAmount().toPlainString(), item.sourceReferenceType(), item.sourceReferenceId())).toList(),
                preview.pricingFingerprint());
    }

    private PriceSnapshotView priceSnapshotView(MatchPricingService.PriceSnapshot snapshot) {
        return new PriceSnapshotView(
                snapshot.priceSnapshotId(), snapshot.courseMatchId(), snapshot.status(), snapshot.billingMode(),
                snapshot.totalAmount().toPlainString(), snapshot.currency(), snapshot.pricingFingerprint(),
                snapshot.confirmedBy(), snapshot.confirmedAt());
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

    public record PricingConfirmationRequest(
            @NotNull @DecimalMin("0.00") java.math.BigDecimal acceptedTotalAmount,
            @NotBlank @Size(min = 3, max = 3) String currency,
            @NotBlank @Size(min = 64, max = 64) String pricingFingerprint,
            @Size(max = 5000) String confirmationNote) {}

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

    public record PricingPreviewView(
            UUID courseMatchId,
            String currency,
            String billingMode,
            String totalAmount,
            List<PricingItemView> breakdown,
            String pricingFingerprint) {}

    public record PricingItemView(
            UUID courseMatchSessionId,
            String itemType,
            String description,
            String quantity,
            String unitAmount,
            String lineAmount,
            String sourceReferenceType,
            UUID sourceReferenceId) {}

    public record PriceSnapshotView(
            UUID priceSnapshotId,
            UUID courseMatchId,
            String status,
            String billingMode,
            String totalAmount,
            String currency,
            String pricingFingerprint,
            UUID confirmedBy,
            Instant confirmedAt) {}
}
