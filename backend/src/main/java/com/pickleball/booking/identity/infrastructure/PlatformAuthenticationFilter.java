package com.pickleball.booking.identity.infrastructure;

import tools.jackson.databind.ObjectMapper;
import com.pickleball.booking.identity.application.*;
import com.pickleball.booking.shared.api.ApiExceptionHandler;
import com.pickleball.booking.shared.api.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class PlatformAuthenticationFilter extends OncePerRequestFilter {
    private final PlatformTokenService tokens; private final IdentityService identities; private final ObjectMapper json;
    public PlatformAuthenticationFilter(PlatformTokenService tokens, IdentityService identities, ObjectMapper json) { this.tokens = tokens; this.identities = identities; this.json = json; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        var header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try { var userId = tokens.verifyAndGetUserId(header.substring(7)); identities.requireActiveUser(userId); SecurityContextHolder.getContext().setAuthentication(new PlatformAuthentication(new AuthenticatedPrincipal(userId))); }
            catch (RuntimeException exception) { unauthorized(request, response); return; }
        }
        chain.doFilter(request, response);
    }
    private void unauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException { response.setStatus(401); response.setContentType(MediaType.APPLICATION_JSON_VALUE); json.writeValue(response.getOutputStream(), new ApiExceptionHandler.ErrorResponse(new ApiExceptionHandler.ErrorBody("AUTH_INVALID_TOKEN", "Platform access token is invalid", List.of(), java.util.Map.of(), (String) request.getAttribute(RequestIdFilter.ATTRIBUTE)))); }
    private static class PlatformAuthentication extends AbstractAuthenticationToken {
        private final AuthenticatedPrincipal principal;
        PlatformAuthentication(AuthenticatedPrincipal principal) { super(List.of()); this.principal = principal; setAuthenticated(true); }
        @Override public Object getCredentials() { return ""; } @Override public AuthenticatedPrincipal getPrincipal() { return principal; }
    }
}
