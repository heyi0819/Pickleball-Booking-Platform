package com.pickleball.booking.course.domain;

import java.util.Optional;
import java.util.UUID;

public interface CourseCancellationRequestRepository {
    Optional<CourseCancellationRequest> findById(UUID requestId);

    CourseCancellationRequest save(CourseCancellationRequest request);
}
