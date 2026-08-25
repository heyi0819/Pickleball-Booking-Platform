package com.pickleball.booking.course.application;

import java.util.UUID;

public interface CourseOperationsAccessPort {
    boolean isAssignedCoach(UUID organizationId, UUID courseSessionId, UUID userId);

    boolean isScheduledParticipant(UUID organizationId, UUID courseSessionId, UUID userId);
}
