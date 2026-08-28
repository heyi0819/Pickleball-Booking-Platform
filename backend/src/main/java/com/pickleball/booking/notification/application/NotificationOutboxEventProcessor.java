package com.pickleball.booking.notification.application;

import com.pickleball.booking.notification.infrastructure.NotificationProjectionRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NotificationOutboxEventProcessor implements OutboxEventProcessor {
    private static final Set<String> COURSE_EVENTS = Set.of(
            "COURSE_CREATED_FROM_MATCH",
            "COURSE_CREATED_FROM_OFFERING");
    private static final Set<String> SESSION_EVENTS = Set.of(
            "SESSION_RESCHEDULE_APPROVED",
            "SESSION_DIRECT_RESCHEDULED");

    private final NotificationProjectionRepository repository;

    public NotificationOutboxEventProcessor(NotificationProjectionRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean supports(String eventType) {
        return COURSE_EVENTS.contains(eventType) || SESSION_EVENTS.contains(eventType);
    }

    @Override
    @Transactional
    public void process(OutboxMessage message) {
        if (COURSE_EVENTS.contains(message.eventType())) {
            projectCourse(message);
            return;
        }
        if (SESSION_EVENTS.contains(message.eventType())) {
            projectSession(message);
            return;
        }
        throw new IllegalArgumentException("Unsupported notification event: " + message.eventType());
    }

    private void projectCourse(OutboxMessage message) {
        Map<String, Object> payload = withEvent(
                repository.coursePayload(message.organizationId(), message.aggregateId()), message.eventType());
        for (UUID recipient : repository.courseRecipients(message.organizationId(), message.aggregateId())) {
            repository.enqueueUser(
                    message.organizationId(), recipient, "COURSE_CONFIRMED", "Course", message.aggregateId(),
                    payload, dedupe(message, recipient));
        }
    }

    private void projectSession(OutboxMessage message) {
        Map<String, Object> payload = withEvent(
                repository.sessionPayload(message.organizationId(), message.aggregateId()), message.eventType());
        for (UUID recipient : repository.sessionRecipients(message.organizationId(), message.aggregateId())) {
            repository.enqueueUser(
                    message.organizationId(), recipient, "SESSION_RESCHEDULED", "CourseSession", message.aggregateId(),
                    payload, dedupe(message, recipient));
        }
    }

    private static Map<String, Object> withEvent(Map<String, Object> source, String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>(source);
        payload.put("sourceEvent", eventType);
        return Map.copyOf(payload);
    }

    private static String dedupe(OutboxMessage message, UUID recipient) {
        return "OUTBOX:" + message.id() + ":" + recipient;
    }
}
