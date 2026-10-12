package com.pickleball.booking.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pickleball.booking.notification.application.NotificationDeliveryPort.NotificationMessage;
import com.pickleball.booking.notification.infrastructure.WorkerQueueRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class NotificationWorkerServiceTest {
    private final WorkerQueueRepository repository = mock(WorkerQueueRepository.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<NotificationDeliveryPort> deliveryPort = mock(ObjectProvider.class);
    private final WorkerRetryPolicy retryPolicy = new WorkerRetryPolicy();

    @Test
    void claimsWithALeaseAndRetriesWhenNoDeliveryPortIsAvailable() {
        Duration claimTimeout = Duration.ofMinutes(5);
        NotificationMessage message = new NotificationMessage(
                UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(),
                "LINE", "COURSE_CONFIRMED", "Course", UUID.randomUUID(), Map.of(), 1);
        when(repository.claimNotifications(50, claimTimeout)).thenReturn(List.of(message));
        when(deliveryPort.getIfAvailable()).thenReturn(null);
        var service = new NotificationWorkerService(
                repository, deliveryPort, retryPolicy, claimTimeout.toMillis());

        assertThat(service.runBatch(50)).isOne();

        verify(repository).markNotificationFailed(
                message.id(), false, Duration.ofSeconds(30),
                "NOTIFICATION_DELIVERY_PORT_UNAVAILABLE",
                "No notification delivery adapter is configured");
    }
}
