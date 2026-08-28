package com.pickleball.booking.notification.application;

import com.pickleball.booking.notification.application.NotificationDeliveryPort.NotificationMessage;
import com.pickleball.booking.notification.infrastructure.WorkerQueueRepository;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class NotificationWorkerService {
    private final WorkerQueueRepository repository;
    private final ObjectProvider<NotificationDeliveryPort> deliveryPort;
    private final WorkerRetryPolicy retryPolicy;

    public NotificationWorkerService(
            WorkerQueueRepository repository,
            ObjectProvider<NotificationDeliveryPort> deliveryPort,
            WorkerRetryPolicy retryPolicy) {
        this.repository = repository;
        this.deliveryPort = deliveryPort;
        this.retryPolicy = retryPolicy;
    }

    public int runBatch(int batchSize) {
        List<NotificationMessage> messages = repository.claimNotifications(batchSize);
        NotificationDeliveryPort port = deliveryPort.getIfAvailable();
        for (NotificationMessage message : messages) {
            processOne(message, port);
        }
        return messages.size();
    }

    private void processOne(NotificationMessage message, NotificationDeliveryPort port) {
        if (port == null) {
            repository.markNotificationFailed(
                    message.id(),
                    false,
                    retryPolicy.nextDelay(message.attemptCount()),
                    "NOTIFICATION_DELIVERY_PORT_UNAVAILABLE",
                    "No notification delivery adapter is configured");
            return;
        }

        try {
            port.deliver(message);
            repository.markNotificationSent(message.id());
        } catch (RuntimeException ex) {
            repository.markNotificationFailed(
                    message.id(),
                    retryPolicy.isDead(message.attemptCount()),
                    retryPolicy.nextDelay(message.attemptCount()),
                    ex.getClass().getSimpleName(),
                    ex.getMessage() == null ? "notification delivery failed" : ex.getMessage());
        }
    }
}
