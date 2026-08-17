package com.pickleball.booking.shared.api;

import com.pickleball.booking.identity.application.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(LineCredentialInvalidException.class) @ResponseStatus(HttpStatus.UNAUTHORIZED) ErrorResponse line(LineCredentialInvalidException e, HttpServletRequest r) { return error("AUTH_INVALID_TOKEN", e.getMessage(), r); }
    @ExceptionHandler(AccessForbiddenException.class) @ResponseStatus(HttpStatus.FORBIDDEN) ErrorResponse forbidden(AccessForbiddenException e, HttpServletRequest r) { return error("AUTH_FORBIDDEN", e.getMessage(), r); }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class}) @ResponseStatus(HttpStatus.BAD_REQUEST) ErrorResponse validation(Exception e, HttpServletRequest r) { return error("VALIDATION_FAILED", "Request validation failed", r); }
    @ExceptionHandler(Exception.class) @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR) ErrorResponse fallback(Exception e, HttpServletRequest r) { return error("INTERNAL_ERROR", "An unexpected error occurred", r); }
    private ErrorResponse error(String code, String message, HttpServletRequest request) { return new ErrorResponse(new ErrorBody(code, message, List.of(), Map.of(), (String) request.getAttribute(RequestIdFilter.ATTRIBUTE))); }
    public record ErrorResponse(ErrorBody error) {} public record ErrorBody(String code, String message, List<?> fieldErrors, Map<String, ?> details, String traceId) {}
}
