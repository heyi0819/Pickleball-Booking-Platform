package com.pickleball.booking.course.domain;

public enum CourseOperationsDomainError {
    REQUIRED_FIELD,
    INVALID_STATE,
    INVALID_TIME_RANGE,
    SESSION_ALREADY_STARTED,
    INVALID_PARTICIPANT_COUNT,
    INVALID_CHANGE_PROPOSAL,
    ACTOR_NOT_ALLOWED
}
