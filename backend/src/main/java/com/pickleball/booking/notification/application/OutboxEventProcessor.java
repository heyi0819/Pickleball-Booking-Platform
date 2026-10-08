package com.pickleball.booking.notification.application;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface OutboxEventProcessor {
    Set<String> supportedEventTypes();

    default boolean supports(String eventType) {
        return supportedEventTypes().contains(eventType);
    }

    void process(OutboxMessage message);

    record OutboxMessage(
            UUID id,
            UUID organizationId,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            Map<String, Object> payload,
            int attemptCount) {}
}
