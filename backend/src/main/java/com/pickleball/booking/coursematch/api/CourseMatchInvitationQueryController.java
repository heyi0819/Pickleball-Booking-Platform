package com.pickleball.booking.coursematch.api;

import com.pickleball.booking.coursematch.application.CourseMatchInvitationQueryService;
import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/course-match-invitations")
public class CourseMatchInvitationQueryController {
    private final CourseMatchInvitationQueryService query;

    public CourseMatchInvitationQueryController(CourseMatchInvitationQueryService query) {
        this.query = query;
    }

    @GetMapping("/mine")
    public ApiResponse<List<CourseMatchInvitationQueryService.InvitationSummary>> mine(
            Authentication authentication,
            HttpServletRequest request) {
        return ApiResponse.of(
                query.mine(principal(authentication)),
                (String) request.getAttribute("requestId"));
    }

    private AuthenticatedPrincipal principal(Authentication authentication) {
        return (AuthenticatedPrincipal) authentication.getPrincipal();
    }
}
