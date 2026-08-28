package com.pickleball.booking.notification.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class NotificationProjectionRepository {
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public NotificationProjectionRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> coursePayload(UUID organizationId, UUID courseId) {
        return jdbc.query("""
                select c.course_no, cs.scheduled_start_at, va.venue_name_snapshot
                from courses c
                left join course_sessions cs on cs.course_id=c.id and cs.sequence_no=1
                left join session_venue_arrangements va on va.course_session_id=cs.id and va.status='CONFIRMED'
                where c.organization_id=? and c.id=?
                """, rs -> {
            if (!rs.next()) throw new IllegalStateException("Course notification source was not found");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("courseNo", rs.getString("course_no"));
            payload.put("firstSessionStart", format(rs.getTimestamp("scheduled_start_at")));
            payload.put("venueName", fallback(rs.getString("venue_name_snapshot"), "待確認"));
            return payload;
        }, organizationId, courseId);
    }

    public List<UUID> courseRecipients(UUID organizationId, UUID courseId) {
        return jdbc.queryForList("""
                select distinct user_id from (
                    select cca.user_id
                    from course_contact_assignments cca
                    where cca.organization_id=? and cca.course_id=? and cca.effective_to is null
                    union
                    select e.user_id
                    from enrollments e
                    join course_sessions cs on cs.id=e.course_session_id
                    where e.organization_id=? and cs.course_id=? and e.status in ('SCHEDULED','ATTENDED','ABSENT')
                    union
                    select cp.user_id
                    from session_coach_assignments sca
                    join course_sessions cs on cs.id=sca.course_session_id
                    join coach_profiles cp on cp.id=sca.coach_profile_id
                    where sca.organization_id=? and cs.course_id=? and sca.status in ('ACCEPTED','CANCEL_PENDING')
                ) recipients
                order by user_id
                """, UUID.class,
                organizationId, courseId,
                organizationId, courseId,
                organizationId, courseId);
    }

    public Map<String, Object> sessionPayload(UUID organizationId, UUID sessionId) {
        return jdbc.query("""
                select c.course_no, cs.scheduled_start_at, va.venue_name_snapshot
                from course_sessions cs
                join courses c on c.id=cs.course_id
                left join session_venue_arrangements va on va.course_session_id=cs.id and va.status='CONFIRMED'
                where cs.organization_id=? and cs.id=?
                """, rs -> {
            if (!rs.next()) throw new IllegalStateException("Course session notification source was not found");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("courseNo", rs.getString("course_no"));
            payload.put("sessionStart", format(rs.getTimestamp("scheduled_start_at")));
            payload.put("venueName", fallback(rs.getString("venue_name_snapshot"), "待確認"));
            return payload;
        }, organizationId, sessionId);
    }

    public List<UUID> sessionRecipients(UUID organizationId, UUID sessionId) {
        return jdbc.queryForList("""
                select distinct user_id from (
                    select e.user_id
                    from enrollments e
                    where e.organization_id=? and e.course_session_id=? and e.status='SCHEDULED'
                    union
                    select cp.user_id
                    from session_coach_assignments sca
                    join coach_profiles cp on cp.id=sca.coach_profile_id
                    where sca.organization_id=? and sca.course_session_id=? and sca.status in ('ACCEPTED','CANCEL_PENDING')
                    union
                    select cca.user_id
                    from course_contact_assignments cca
                    join course_sessions cs on cs.course_id=cca.course_id
                    where cca.organization_id=? and cs.id=? and cca.effective_to is null
                ) recipients
                order by user_id
                """, UUID.class,
                organizationId, sessionId,
                organizationId, sessionId,
                organizationId, sessionId);
    }

    public List<NotificationTarget> activeTargets(String targetCode) {
        return jdbc.query("""
                select id, organization_id
                from notification_targets
                where channel='LINE' and status='ACTIVE' and target_code=?
                order by organization_id, id
                """, (rs, rowNum) -> new NotificationTarget(
                rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class)), targetCode);
    }

    public List<Map<String, Object>> scheduledSessions(UUID organizationId, Instant fromInclusive, Instant toExclusive) {
        return jdbc.query("""
                select c.course_no, cs.scheduled_start_at, va.venue_name_snapshot
                from course_sessions cs
                join courses c on c.id=cs.course_id
                left join session_venue_arrangements va on va.course_session_id=cs.id and va.status='CONFIRMED'
                where cs.organization_id=?
                  and cs.status='SCHEDULED'
                  and cs.scheduled_start_at>=?
                  and cs.scheduled_start_at<?
                order by cs.scheduled_start_at, c.course_no
                """, (rs, rowNum) -> {
            Map<String, Object> session = new LinkedHashMap<>();
            session.put("courseNo", rs.getString("course_no"));
            session.put("start", format(rs.getTimestamp("scheduled_start_at")));
            session.put("venueName", fallback(rs.getString("venue_name_snapshot"), "待確認"));
            return session;
        }, organizationId, Timestamp.from(fromInclusive), Timestamp.from(toExclusive));
    }

    public boolean enqueueUser(
            UUID organizationId,
            UUID recipientUserId,
            String templateCode,
            String businessType,
            UUID businessId,
            Map<String, Object> payload,
            String dedupeKey) {
        return insert(organizationId, null, recipientUserId, templateCode, businessType, businessId, payload, dedupeKey);
    }

    public boolean enqueueTarget(
            UUID organizationId,
            UUID notificationTargetId,
            String templateCode,
            String businessType,
            UUID businessId,
            Map<String, Object> payload,
            String dedupeKey) {
        return insert(organizationId, notificationTargetId, null, templateCode, businessType, businessId, payload, dedupeKey);
    }

    private boolean insert(
            UUID organizationId,
            UUID targetId,
            UUID recipientUserId,
            String templateCode,
            String businessType,
            UUID businessId,
            Map<String, Object> payload,
            String dedupeKey) {
        int inserted = jdbc.update("""
                insert into notifications(
                    id, organization_id, notification_target_id, recipient_user_id,
                    channel, template_code, business_type, business_id, payload,
                    status, attempt_count, dedupe_key)
                values (?, ?, ?, ?, 'LINE', ?, ?, ?, ?::jsonb, 'PENDING', 0, ?)
                on conflict (organization_id, dedupe_key) do nothing
                """, UUID.randomUUID(), organizationId, targetId, recipientUserId,
                templateCode, businessType, businessId, json(payload), dedupeKey);
        return inserted == 1;
    }

    private String json(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to serialize notification payload", ex);
        }
    }

    private static String format(Timestamp timestamp) {
        return timestamp == null ? "待確認" : DATE_TIME.format(timestamp.toInstant().atZone(TAIPEI));
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record NotificationTarget(UUID id, UUID organizationId) {}
}
