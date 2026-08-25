package com.pickleball.booking.course.domain;

import java.util.Optional;
import java.util.UUID;

public interface CourseSessionRepository {
    Optional<CourseSession> findById(UUID courseSessionId);

    CourseSession save(CourseSession courseSession);
}
