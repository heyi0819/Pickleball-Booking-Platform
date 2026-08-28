package com.pickleball.booking.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pickleball.booking.notification.application.OutboxEventProcessor.OutboxMessage;
import com.pickleball.booking.notification.infrastructure.NotificationProjectionRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationOutboxEventProcessorTest {
    private final NotificationProjectionRepository repository = mock(NotificationProjectionRepository.class);
    private final NotificationOutboxEventProcessor processor = new NotificationOutboxEventProcessor(repository);

    @Test
    void mapsCourseCreatedEventToCourseConfirmedTemplate() {
        UUID outboxId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        when(repository.coursePayload(organizationId, courseId)).thenReturn(Map.of("courseNo", "C-1"));
        when(repository.courseRecipients(organizationId, courseId)).thenReturn(java.util.List.of(recipientId));

        processor.process(new OutboxMessage(
                outboxId, organizationId, "Course", courseId,
                "COURSE_CREATED_FROM_MATCH", Map.of(), 1));

        verify(repository).enqueueUser(
                eq(organizationId), eq(recipientId), eq("COURSE_CONFIRMED"), eq("Course"), eq(courseId),
                org.mockito.ArgumentMatchers.argThat(payload ->
                        "COURSE_CREATED_FROM_MATCH".equals(payload.get("sourceEvent"))),
                eq("OUTBOX:" + outboxId + ":" + recipientId));
    }

    @Test
    void onlyClaimsExplicitNotificationEvents() {
        assertThat(processor.supports("COURSE_CREATED_FROM_OFFERING")).isTrue();
        assertThat(processor.supports("SESSION_DIRECT_RESCHEDULED")).isTrue();
        assertThat(processor.supports("PAYMENT_CAPTURED")).isFalse();
    }
}
