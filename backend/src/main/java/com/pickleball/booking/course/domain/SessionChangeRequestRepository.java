package com.pickleball.booking.course.domain;

import java.util.Optional;
import java.util.UUID;

public interface SessionChangeRequestRepository {
    Optional<SessionChangeRequest> findById(UUID requestId);

    SessionChangeRequest save(SessionChangeRequest request);
}
