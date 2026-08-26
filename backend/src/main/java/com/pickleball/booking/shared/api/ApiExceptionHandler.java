package com.pickleball.booking.shared.api;

import com.pickleball.booking.identity.application.AccessForbiddenException;
import com.pickleball.booking.identity.application.LineCredentialInvalidException;
import com.pickleball.booking.shared.application.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Set<String> NOT_FOUND_CODES = Set.of(
            "RESOURCE_NOT_FOUND",
            "COURSE_OFFERING_NOT_FOUND",
            "COURSE_NOT_FOUND",
            "COURSE_SESSION_NOT_FOUND",
            "SESSION_CHANGE_REQUEST_NOT_FOUND",
            "COACH_CANCELLATION_REQUEST_NOT_FOUND",
            "ENROLLMENT_NOT_FOUND",
            "RECEIVABLE_NOT_FOUND",
            "PAYMENT_NOT_FOUND",
            "REFUND_NOT_FOUND");

    private static final Set<String> UNPROCESSABLE_CODES = Set.of(
            "LESSON_REQUEST_NOT_APPROVED",
            "COACH_NOT_APPROVED",
            "MATCH_NOT_READY",
            "OFFERING_NOT_READY",
            "PRICE_CHANGED_RECALC_REQUIRED",
            "PARTICIPANT_BELOW_MIN",
            "PARTICIPANT_ABOVE_MAX",
            "BOOKING_TIME_NOT_FUTURE",
            "SESSION_ALREADY_STARTED",
            "PAYMENT_AMOUNT_INVALID",
            "REFUND_EXCEEDS_REFUNDABLE",
            "PAYMENT_NOT_COMPLETED",
            "REFUND_NOT_APPROVED");

    @ExceptionHandler(LineCredentialInvalidException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    ErrorResponse line(LineCredentialInvalidException exception, HttpServletRequest request) {
        return error("AUTH_INVALID_TOKEN", exception.getMessage(), request);
    }

    @ExceptionHandler(AccessForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    ErrorResponse forbidden(AccessForbiddenException exception, HttpServletRequest request) {
        return error("AUTH_FORBIDDEN", exception.getMessage(), request);
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ErrorResponse> business(BusinessException exception, HttpServletRequest request) {
        HttpStatus status = switch (exception.code()) {
            case "AUTH_FORBIDDEN", "ORG_SCOPE_DENIED" -> HttpStatus.FORBIDDEN;
            case "VALIDATION_FAILED" -> HttpStatus.BAD_REQUEST;
            default -> NOT_FOUND_CODES.contains(exception.code())
                    ? HttpStatus.NOT_FOUND
                    : UNPROCESSABLE_CODES.contains(exception.code())
                            ? HttpStatus.UNPROCESSABLE_ENTITY
                            : HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(error(exception.code(), exception.getMessage(), request));
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            MethodArgumentNotValidException.class,
            MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse validation(Exception exception, HttpServletRequest request) {
        return error("VALIDATION_FAILED", "Request validation failed", request);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    ErrorResponse fallback(Exception exception, HttpServletRequest request) {
        return error("INTERNAL_ERROR", "An unexpected error occurred", request);
    }

    private ErrorResponse error(String code, String message, HttpServletRequest request) {
        return new ErrorResponse(new ErrorBody(
                code,
                message,
                List.of(),
                Map.of(),
                (String) request.getAttribute(RequestIdFilter.ATTRIBUTE)));
    }

    public record ErrorResponse(ErrorBody error) {}
    public record ErrorBody(String code, String message, List<?> fieldErrors,
            Map<String, ?> details, String traceId) {}
}
