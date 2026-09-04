package com.pickleball.booking.identity.api;

import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.identity.application.RoleDelegationService;
import com.pickleball.booking.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminRoleDelegationController {
    private final RoleDelegationService service;
    public AdminRoleDelegationController(RoleDelegationService service) { this.service = service; }
    @GetMapping("/users")
    public ApiResponse<List<RoleDelegationService.UserSummary>> users(Authentication authentication, @RequestParam String query, HttpServletRequest request) {
        return ok(service.findActiveUsers(principal(authentication), query), request);
    }
    @GetMapping("/organizations")
    public ApiResponse<List<RoleDelegationService.OrganizationSummary>> organizations(Authentication authentication, HttpServletRequest request) {
        return ok(service.activeOrganizations(principal(authentication)), request);
    }
    @PostMapping("/organizations/{organizationId}/committee-members/{userId}") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RoleDelegationService.CommitteeMembership> grant(Authentication authentication, @PathVariable UUID organizationId, @PathVariable UUID userId, HttpServletRequest request) {
        return ok(service.grantCommittee(principal(authentication), organizationId, userId), request);
    }
    @DeleteMapping("/organizations/{organizationId}/committee-members/{userId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(Authentication authentication, @PathVariable UUID organizationId, @PathVariable UUID userId) {
        service.revokeCommittee(principal(authentication), organizationId, userId);
    }
    private static AuthenticatedPrincipal principal(Authentication authentication) { return (AuthenticatedPrincipal) authentication.getPrincipal(); }
    private static <T> ApiResponse<T> ok(T data, HttpServletRequest request) { return ApiResponse.of(data, (String) request.getAttribute("requestId")); }
}
