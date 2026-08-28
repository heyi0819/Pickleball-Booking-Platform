package com.pickleball.booking.notification.application;

import com.pickleball.booking.notification.infrastructure.NotificationProjectionRepository;
import com.pickleball.booking.notification.infrastructure.NotificationProjectionRepository.NotificationTarget;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyCoachReminderService {
    public static final String TARGET_CODE = "COACH_REMINDER_GROUP";
    public static final String TEMPLATE_CODE = "COACH_DAILY_REMINDER";

    private final NotificationProjectionRepository repository;
    private final ZoneId zoneId;
    private final Clock clock;

    public DailyCoachReminderService(
            NotificationProjectionRepository repository,
            @Value("${app.workers.coach-reminder-zone:Asia/Taipei}") String zone) {
        this(repository, ZoneId.of(zone), Clock.system(ZoneId.of(zone)));
    }

    DailyCoachReminderService(NotificationProjectionRepository repository, ZoneId zoneId, Clock clock) {
        this.repository = repository;
        this.zoneId = zoneId;
        this.clock = clock;
    }

    @Transactional
    public int enqueueNextDayReminders() {
        LocalDate reminderDate = LocalDate.now(clock).plusDays(1);
        Instant from = reminderDate.atStartOfDay(zoneId).toInstant();
        Instant to = reminderDate.plusDays(1).atStartOfDay(zoneId).toInstant();
        int created = 0;

        for (NotificationTarget target : repository.activeTargets(TARGET_CODE)) {
            List<Map<String, Object>> sessions = repository.scheduledSessions(target.organizationId(), from, to);
            if (sessions.isEmpty()) continue;

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("reminderDate", reminderDate.toString());
            payload.put("sessions", sessions);
            boolean inserted = repository.enqueueTarget(
                    target.organizationId(), target.id(), TEMPLATE_CODE,
                    "NotificationTarget", target.id(), Map.copyOf(payload),
                    TEMPLATE_CODE + ":" + target.id() + ":" + reminderDate);
            if (inserted) created++;
        }
        return created;
    }
}
