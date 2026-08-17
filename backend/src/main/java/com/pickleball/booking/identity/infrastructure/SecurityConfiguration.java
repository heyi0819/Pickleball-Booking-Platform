package com.pickleball.booking.identity.infrastructure;

import tools.jackson.databind.ObjectMapper;
import com.pickleball.booking.shared.api.ApiExceptionHandler;
import com.pickleball.booking.shared.api.RequestIdFilter;
import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration @EnableWebSecurity
public class SecurityConfiguration {
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, PlatformAuthenticationFilter platformFilter, ObjectMapper json) throws Exception {
        return http.csrf(csrf -> csrf.disable()).sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/line/login").permitAll().requestMatchers("/actuator/health/**", "/v3/api-docs.yaml").permitAll().anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> { response.setStatus(401); response.setContentType(MediaType.APPLICATION_JSON_VALUE); json.writeValue(response.getOutputStream(), new ApiExceptionHandler.ErrorResponse(new ApiExceptionHandler.ErrorBody("AUTH_INVALID_TOKEN", "Authentication is required", List.of(), java.util.Map.of(), (String) request.getAttribute(RequestIdFilter.ATTRIBUTE)))); }))
                .addFilterBefore(platformFilter, UsernamePasswordAuthenticationFilter.class).build();
    }
}
