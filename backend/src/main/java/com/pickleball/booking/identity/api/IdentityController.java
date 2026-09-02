package com.pickleball.booking.identity.api;

import com.pickleball.booking.identity.application.*;
import com.pickleball.booking.identity.domain.UserProfile;
import com.pickleball.booking.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class IdentityController {
    private final IdentityService service;
    public IdentityController(IdentityService service) { this.service = service; }
    @PostMapping("/auth/line/login") public ApiResponse<LoginResponse> login(@Valid @RequestBody LineLoginRequest request, HttpServletRequest http) { var result = service.login(request.idToken()); return ok(new LoginResponse(result.accessToken(), "Bearer", result.expiresIn(), new LoginUser(result.userId(), result.displayName(), result.roles())), http); }
    @PostMapping("/auth/line/admin/exchange") public ApiResponse<LoginResponse> adminExchange(@Valid @RequestBody AdminLineExchangeRequest request, HttpServletRequest http) { var result = service.adminLogin(request.authorizationCode(), request.codeVerifier(), request.nonce()); return ok(new LoginResponse(result.accessToken(), "Bearer", result.expiresIn(), new LoginUser(result.userId(), result.displayName(), result.roles())), http); }
    @GetMapping("/me") public ApiResponse<IdentityService.MeView> me(Authentication authentication, HttpServletRequest http) { return ok(service.me(principal(authentication)), http); }
    @PatchMapping("/me/profile") public ApiResponse<IdentityService.MeView> profile(Authentication authentication, @Valid @RequestBody ProfileRequest request, HttpServletRequest http) { return ok(service.updateProfile(principal(authentication), new UserProfile(request.displayName(), request.email(), request.locale())), http); }
    @GetMapping("/me/roles") public ApiResponse<List<IdentityService.RoleView>> roles(Authentication authentication, HttpServletRequest http) { return ok(service.roles(principal(authentication)), http); }
    private AuthenticatedPrincipal principal(Authentication auth) { return (AuthenticatedPrincipal) auth.getPrincipal(); }
    private <T> ApiResponse<T> ok(T data, HttpServletRequest request) { return ApiResponse.of(data, (String) request.getAttribute("requestId")); }
    public record LineLoginRequest(@NotBlank @Size(max = 10000) String idToken) {}
    public record AdminLineExchangeRequest(@NotBlank @Size(max = 2048) String authorizationCode, @NotBlank @Size(min = 43, max = 128) String codeVerifier, @NotBlank @Size(max = 256) String nonce) {}
    public record ProfileRequest(@NotBlank @Size(max = 100) String displayName, @Email @Size(max = 254) String email, @NotBlank @Size(max = 10) String locale) {}
    public record LoginResponse(String accessToken, String tokenType, long expiresIn, LoginUser user) {}
    public record LoginUser(UUID id, String displayName, List<?> roles) {}
}
