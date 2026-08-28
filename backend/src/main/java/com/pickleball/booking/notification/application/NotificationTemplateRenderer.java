package com.pickleball.booking.notification.application;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class NotificationTemplateRenderer {
    private static final int MAX_TEXT_LENGTH = 4500;

    public String render(String templateCode, Map<String, Object> payload) {
        String text = switch (templateCode) {
            case "COURSE_CONFIRMED" -> courseConfirmed(payload);
            case "SESSION_RESCHEDULED" -> sessionRescheduled(payload);
            case "COACH_DAILY_REMINDER" -> coachDailyReminder(payload);
            default -> throw NotificationDeliveryException.permanent(
                    "NOTIFICATION_TEMPLATE_UNSUPPORTED",
                    "Unsupported notification template: " + templateCode);
        };
        return text.length() <= MAX_TEXT_LENGTH ? text : text.substring(0, MAX_TEXT_LENGTH - 1) + "…";
    }

    private String courseConfirmed(Map<String, Object> payload) {
        return "【課程成立】\n課程：" + value(payload, "courseNo", "未提供")
                + "\n第一堂：" + value(payload, "firstSessionStart", "待確認")
                + "\n場地：" + value(payload, "venueName", "待確認");
    }

    private String sessionRescheduled(Map<String, Object> payload) {
        return "【課程時間異動】\n課程：" + value(payload, "courseNo", "未提供")
                + "\n新時間：" + value(payload, "sessionStart", "待確認")
                + "\n場地：" + value(payload, "venueName", "待確認");
    }

    private String coachDailyReminder(Map<String, Object> payload) {
        StringBuilder text = new StringBuilder("【明日課程提醒】\n日期：")
                .append(value(payload, "reminderDate", "待確認"));
        Object rawSessions = payload.get("sessions");
        if (!(rawSessions instanceof List<?> sessions) || sessions.isEmpty()) {
            return text.append("\n明日無已排定課程").toString();
        }
        int index = 1;
        for (Object raw : sessions) {
            if (!(raw instanceof Map<?, ?> session)) continue;
            text.append("\n").append(index++).append(". ")
                    .append(value(session, "start", "待確認"))
                    .append("｜").append(value(session, "courseNo", "未提供"))
                    .append("｜").append(value(session, "venueName", "待確認"));
        }
        return text.toString();
    }

    private static String value(Map<?, ?> payload, String key, String fallback) {
        Object value = payload.get(key);
        if (value == null) return fallback;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }
}
