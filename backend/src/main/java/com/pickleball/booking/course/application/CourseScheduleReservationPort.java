package com.pickleball.booking.course.application;

import java.time.Instant;
import java.util.UUID;

public interface CourseScheduleReservationPort {
    int shiftActiveReservations(UUID courseSessionId, Instant newStartAt, Instant newEndAt);

    int releaseParticipantReservation(UUID courseSessionId, UUID userId, String reason);

    int releaseAllActiveReservations(UUID courseSessionId, String reason);
}
