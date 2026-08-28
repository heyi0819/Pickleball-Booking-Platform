package com.pickleball.booking.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotificationTemplateRendererTest {
    private final NotificationTemplateRenderer renderer = new NotificationTemplateRenderer();

    @Test
    void rendersCourseAndSessionTemplates() {
        assertThat(renderer.render("COURSE_CONFIRMED", Map.of(
                "courseNo", "C-1001", "firstSessionStart", "2026-08-30 10:00", "venueName", "A場")))
                .contains("課程成立", "C-1001", "2026-08-30 10:00", "A場");

        assertThat(renderer.render("SESSION_RESCHEDULED", Map.of(
                "courseNo", "C-1001", "sessionStart", "2026-09-01 19:00", "venueName", "B場")))
                .contains("課程時間異動", "2026-09-01 19:00", "B場");
    }

    @Test
    void rendersDailyCoachReminder() {
        String text = renderer.render("COACH_DAILY_REMINDER", Map.of(
                "reminderDate", "2026-08-29",
                "sessions", List.of(
                        Map.of("start", "2026-08-29 09:00", "courseNo", "C-1", "venueName", "A場"),
                        Map.of("start", "2026-08-29 14:00", "courseNo", "C-2", "venueName", "B場"))));
        assertThat(text).contains("明日課程提醒", "C-1", "C-2", "A場", "B場");
    }

    @Test
    void rejectsUnknownTemplateAsPermanentDeliveryError() {
        assertThatThrownBy(() -> renderer.render("UNKNOWN", Map.of()))
                .isInstanceOf(NotificationDeliveryException.class)
                .satisfies(ex -> assertThat(((NotificationDeliveryException) ex).retryable()).isFalse());
    }
}
