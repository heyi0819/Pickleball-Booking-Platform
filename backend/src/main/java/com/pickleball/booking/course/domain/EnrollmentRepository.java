package com.pickleball.booking.course.domain;

import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository {
    Optional<Enrollment> findById(UUID enrollmentId);

    Enrollment save(Enrollment enrollment);
}
