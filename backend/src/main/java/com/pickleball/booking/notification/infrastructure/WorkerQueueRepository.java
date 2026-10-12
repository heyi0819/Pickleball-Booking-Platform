package com.pickleball.booking.notification.infrastructure;

import com.pickleball.booking.notification.application.NotificationDeliveryPort.NotificationMessage;
import com.pickleball.booking.notification.application.OutboxEventProcessor.OutboxMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
public class WorkerQueueRepository {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final ObjectMapper objectMapper;

    public WorkerQueueRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbc);
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<OutboxMessage> claimOutbox(
            int batchSize,
            Set<String> supportedEventTypes,
            Instant createdAtOrAfter,
            Duration claimTimeout) {
        if (supportedEventTypes.isEmpty()) return List.of();
        return namedJdbc.query("""
                with claimed as (
                    select id
                    from outbox_events
                    where status in ('PENDING','FAILED','PROCESSING')
                      and available_at <= now()
                      and created_at >= :createdAtOrAfter
                      and event_type in (:supportedEventTypes)
                    order by available_at, created_at
                    for update skip locked
                    limit :batchSize
                )
                update outbox_events e
                set status = 'PROCESSING', attempt_count = attempt_count + 1,
                    available_at = now() + (:claimTimeoutMs * interval '1 millisecond')
                from claimed
                where e.id = claimed.id
                returning e.id, e.organization_id, e.aggregate_type, e.aggregate_id,
                          e.event_type, e.payload::text, e.attempt_count
                """, new MapSqlParameterSource()
                        .addValue("createdAtOrAfter", Timestamp.from(createdAtOrAfter))
                        .addValue("supportedEventTypes", supportedEventTypes)
                        .addValue("claimTimeoutMs", claimTimeout.toMillis())
                        .addValue("batchSize", batchSize),
                (rs, rowNum) -> mapOutbox(rs));
    }

    public void markOutboxProcessed(UUID id) {
        jdbc.update("""
                update outbox_events
                set status='PROCESSED', processed_at=now(), last_error=null
                where id=? and status='PROCESSING'
                """, id);
    }

    public void markOutboxFailed(UUID id, boolean dead, Duration delay, String error) {
        jdbc.update("""
                update outbox_events
                set status=?, available_at=now() + (? * interval '1 millisecond'), last_error=?
                where id=? and status='PROCESSING'
                """, dead ? "DEAD" : "FAILED", delay.toMillis(), truncate(error, 4000), id);
    }

    @Transactional
    public List<NotificationMessage> claimNotifications(int batchSize, Duration claimTimeout) {
        return jdbc.query("""
                with claimed as (
                    select id
                    from notifications
                    where status in ('PENDING','FAILED','SENDING')
                      and (next_attempt_at is null or next_attempt_at <= now())
                    order by coalesce(next_attempt_at, created_at), created_at
                    for update skip locked
                    limit ?
                )
                update notifications n
                set status='SENDING', attempt_count=attempt_count+1,
                    next_attempt_at=now() + (? * interval '1 millisecond'), updated_at=now()
                from claimed
                where n.id=claimed.id
                returning n.id, n.organization_id, n.notification_target_id,
                          n.recipient_user_id, n.channel, n.template_code,
                          n.business_type, n.business_id, n.payload::text, n.attempt_count
                """, (rs, rowNum) -> mapNotification(rs), batchSize, claimTimeout.toMillis());
    }

    public void markNotificationSent(UUID id) {
        jdbc.update("""
                update notifications
                set status='SENT', sent_at=now(), next_attempt_at=null,
                    last_error_code=null, last_error_message=null, updated_at=now()
                where id=? and status='SENDING'
                """, id);
    }

    public void markNotificationFailed(UUID id, boolean dead, Duration delay, String code, String message) {
        jdbc.update("""
                update notifications
                set status=?, next_attempt_at=case when ? then null else now() + (? * interval '1 millisecond') end,
                    last_error_code=?, last_error_message=?, updated_at=now()
                where id=? and status='SENDING'
                """, dead ? "DEAD" : "FAILED", dead, delay.toMillis(), truncate(code, 100), truncate(message, 4000), id);
    }

    private OutboxMessage mapOutbox(ResultSet rs) throws SQLException {
        return new OutboxMessage(
                rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getString("aggregate_type"),
                rs.getObject("aggregate_id", UUID.class),
                rs.getString("event_type"),
                readMap(rs.getString("payload")),
                rs.getInt("attempt_count"));
    }

    private NotificationMessage mapNotification(ResultSet rs) throws SQLException {
        return new NotificationMessage(
                rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getObject("notification_target_id", UUID.class),
                rs.getObject("recipient_user_id", UUID.class),
                rs.getString("channel"),
                rs.getString("template_code"),
                rs.getString("business_type"),
                rs.getObject("business_id", UUID.class),
                readMap(rs.getString("payload")),
                rs.getInt("attempt_count"));
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to deserialize worker payload", ex);
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
