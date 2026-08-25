package com.pickleball.booking.course.domain;

import java.util.Objects;

public final class CourseOperationsDomainException extends RuntimeException {
    private final CourseOperationsDomainError error;

    public CourseOperationsDomainException(CourseOperationsDomainError error, String message) {
        super(message);
        this.error = Objects.requireNonNull(error, "error");
    }

    public CourseOperationsDomainError error() {
        return error;
    }
}
