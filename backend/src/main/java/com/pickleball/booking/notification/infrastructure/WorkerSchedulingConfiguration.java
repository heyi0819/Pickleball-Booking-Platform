package com.pickleball.booking.notification.infrastructure;

import com.pickleball.booking.notification.application.NotificationWorkerService;
import com.pickleball.booking.notification.application.OutboxWorkerService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "app.workers", name = "enabled", havingValue = "true")
public class WorkerSchedulingConfiguration {
    private final OutboxWorkerService outboxWorkerService;
    private final NotificationWorkerService notificationWorkerService;

    public WorkerSchedulingConfiguration(
            OutboxWorkerService outboxWorkerService,
            NotificationWorkerService notificationWorkerService) {
        this.outboxWorkerService = outboxWorkerService;
        this.notificationWorkerService = notificationWorkerService;
    }

    @Scheduled(fixedDelayString = "${app.workers.outbox-delay-ms:5000}")
    public void runOutbox() {
        outboxWorkerService.runBatch(50);
    }

    @Scheduled(fixedDelayString = "${app.workers.notification-delay-ms:5000}")
    public void runNotifications() {
        notificationWorkerService.runBatch(50);
    }
}
