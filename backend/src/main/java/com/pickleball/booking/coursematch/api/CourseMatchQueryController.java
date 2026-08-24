package com.pickleball.booking.coursematch.api;

import com.pickleball.booking.coursematch.application.CourseMatchQueryService;
import com.pickleball.booking.coursematch.application.CourseMatchService.PriceState;
import com.pickleball.booking.coursematch.application.CourseMatchService.Readiness;
import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/course-matches")
public class CourseMatchQueryController {
    private final CourseMatchQueryService query;

    public CourseMatchQueryController(CourseMatchQueryService query) {
        this.query = query;
    }

    @GetMapping
    public ApiResponse<List<CourseMatchSummaryView>> list(
            Authentication authentication,
            @RequestParam UUID organizationId,
            HttpServletRequest request) {
        var result = query.listForOrganization(principal(authentication), organizationId).stream()
                .map(detail -> new CourseMatchSummaryView(
                        detail.match().getId(),
                        detail.match().getLessonRequestId(),
                        detail.match().getStatus().name(),
                        detail.match().getParticipantCount(),
                        detail.match().getVersion(),
                        detail.match().getCreatedAt(),
                        detail.readiness(),
                        detail.pricing()))
                .toList();
        return ApiResponse.of(result, (String) request.getAttribute("requestId"));
    }

    private AuthenticatedPrincipal principal(Authentication authentication) {
        return (AuthenticatedPrincipal) authentication.getPrincipal();
    }

    public record CourseMatchSummaryView(
            UUID id,
            UUID lessonRequestId,
            String status,
            short participantCount,
            long version,
            Instant createdAt,
            Readiness readiness,
            PriceState pricing) {}
}
