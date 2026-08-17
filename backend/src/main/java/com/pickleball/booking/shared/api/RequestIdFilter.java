package com.pickleball.booking.shared.api;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component @Order(-100)
public class RequestIdFilter implements Filter {
    public static final String ATTRIBUTE = "requestId";
    @Override public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        var httpRequest = (HttpServletRequest) request; var id = OptionalId.safe(httpRequest.getHeader("X-Request-Id"));
        httpRequest.setAttribute(ATTRIBUTE, id); ((HttpServletResponse) response).setHeader("X-Request-Id", id); MDC.put(ATTRIBUTE, id);
        try { chain.doFilter(request, response); } finally { MDC.remove(ATTRIBUTE); }
    }
    private static final class OptionalId { static String safe(String candidate) { return candidate != null && candidate.length() <= 128 ? candidate : UUID.randomUUID().toString(); } }
}
