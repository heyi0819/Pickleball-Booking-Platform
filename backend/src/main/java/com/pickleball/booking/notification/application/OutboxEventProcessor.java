package com.pickleball.booking.notification.application;

import java.util.Map;
import java.util.UUID;

public interface OutboxEventProcessor {
    boolean supports(String eventType);

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
