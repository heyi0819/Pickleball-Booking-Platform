package com.pickleball.booking.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pickleball.booking.notification.infrastructure.NotificationProjectionRepository;
import com.pickleball.booking.notification.infrastructure.NotificationProjectionRepository.NotificationTarget;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DailyCoachReminderServiceTest {
    @Test
    void enqueuesOneDedupedGroupReminderForNextDaySessions() {
        NotificationProjectionRepository repository = mock(NotificationProjectionRepository.class);
        UUID organizationId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(repository.activeTargets(DailyCoachReminderService.TARGET_CODE))
                .thenReturn(List.of(new NotificationTarget(targetId, organizationId)));
        when(repository.scheduledSessions(eq(organizationId), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(Map.of(
                        "courseNo", "C-1", "start", "2026-08-29 09:00", "venueName", "A場")));
        when(repository.enqueueTarget(
                eq(organizationId), eq(targetId), eq(DailyCoachReminderService.TEMPLATE_CODE),
                eq("NotificationTarget"), eq(targetId), any(), any())).thenReturn(true);

        ZoneId zone = ZoneId.of("Asia/Taipei");
        Clock clock = Clock.fixed(Instant.parse("2026-08-28T06:00:00Z"), zone);
        DailyCoachReminderService service = new DailyCoachReminderService(repository, zone, clock);

        assertThat(service.enqueueNextDayReminders()).isEqualTo(1);
        verify(repository).enqueueTarget(
                eq(organizationId), eq(targetId), eq("COACH_DAILY_REMINDER"),
                eq("NotificationTarget"), eq(targetId), any(),
                eq("COACH_DAILY_REMINDER:" + targetId + ":2026-08-29"));
    }
}
