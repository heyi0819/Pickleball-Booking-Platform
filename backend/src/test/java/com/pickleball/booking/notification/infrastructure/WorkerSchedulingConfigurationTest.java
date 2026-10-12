package com.pickleball.booking.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.pickleball.booking.notification.application.DailyCoachReminderService;
import com.pickleball.booking.notification.application.NotificationWorkerService;
import com.pickleball.booking.notification.application.OutboxWorkerService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class WorkerSchedulingConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(OutboxWorkerService.class, () -> mock(OutboxWorkerService.class))
            .withBean(NotificationWorkerService.class, () -> mock(NotificationWorkerService.class))
            .withBean(DailyCoachReminderService.class, () -> mock(DailyCoachReminderService.class))
            .withUserConfiguration(WorkerSchedulingConfiguration.class);

    @Test
    void schedulingConfigurationIsAbsentByDefault() {
        contextRunner.run(context -> assertThat(context)
                .doesNotHaveBean(WorkerSchedulingConfiguration.class));
    }

    @Test
    void schedulingConfigurationLoadsWhenWorkersAreEnabled() {
        contextRunner.withPropertyValues(
                        "app.workers.enabled=true",
                        "app.workers.outbox-delay-ms=600000",
                        "app.workers.notification-delay-ms=600000")
                .run(context -> assertThat(context)
                        .hasSingleBean(WorkerSchedulingConfiguration.class));
    }
}
