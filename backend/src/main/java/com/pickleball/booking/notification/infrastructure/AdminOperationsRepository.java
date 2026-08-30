package com.pickleball.booking.notification.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AdminOperationsRepository {
    private final JdbcClient jdbc;

    public AdminOperationsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Page<OutboxRow> listOutbox(UUID organizationId, String status, boolean retryDue, int page, int size) {
        String filter = """
                from outbox_events
                where organization_id = :organizationId
                  and (cast(:status as varchar) is null or status = :status)
                  and (not :retryDue or (status = 'FAILED' and available_at <= now()))
                """;
        var count = jdbc.sql("select count(*) " + filter)
                .param("organizationId", organizationId).param("status", status).param("retryDue", retryDue)
                .query(Long.class).single();
        var items = jdbc.sql("""
                select id, organization_id, aggregate_type, aggregate_id, event_type, status,
                       attempt_count, available_at, processed_at, last_error, created_at
                """ + filter + " order by created_at desc limit :size offset :offset")
                .param("organizationId", organizationId).param("status", status).param("retryDue", retryDue)
                .param("size", size).param("offset", page * size)
                .query(AdminOperationsRepository::mapOutbox).list();
        return new Page<>(items, page, size, count);
    }

    public Page<NotificationRow> listNotifications(UUID organizationId, String status, boolean retryDue, int page, int size) {
        String filter = """
                from notifications
                where organization_id = :organizationId
                  and (cast(:status as varchar) is null or status = :status)
                  and (not :retryDue or (status = 'FAILED' and (next_attempt_at is null or next_attempt_at <= now())))
                """;
        var count = jdbc.sql("select count(*) " + filter)
                .param("organizationId", organizationId).param("status", status).param("retryDue", retryDue)
                .query(Long.class).single();
        var items = jdbc.sql("""
                select id, organization_id, notification_target_id, recipient_user_id, channel,
                       template_code, business_type, business_id, status, attempt_count,
                       next_attempt_at, sent_at, last_error_code, last_error_message, created_at, updated_at
                """ + filter + " order by created_at desc limit :size offset :offset")
                .param("organizationId", organizationId).param("status", status).param("retryDue", retryDue)
                .param("size", size).param("offset", page * size)
                .query(AdminOperationsRepository::mapNotification).list();
        return new Page<>(items, page, size, count);
    }

    public Optional<OutboxRow> findOutboxLocked(UUID id) {
        return jdbc.sql("""
                select id, organization_id, aggregate_type, aggregate_id, event_type, status,
                       attempt_count, available_at, processed_at, last_error, created_at
                from outbox_events where id=:id for update
                """).param("id", id).query(AdminOperationsRepository::mapOutbox).optional();
    }

    public Optional<NotificationRow> findNotificationLocked(UUID id) {
        return jdbc.sql("""
                select id, organization_id, notification_target_id, recipient_user_id, channel,
                       template_code, business_type, business_id, status, attempt_count,
                       next_attempt_at, sent_at, last_error_code, last_error_message, created_at, updated_at
                from notifications where id=:id for update
                """).param("id", id).query(AdminOperationsRepository::mapNotification).optional();
    }

    public boolean recoverOutbox(UUID id, String expectedStatus, boolean resetAttempts) {
        return jdbc.sql("""
                update outbox_events
                set status='PENDING', attempt_count=case when :resetAttempts then 0 else attempt_count end,
                    available_at=now(), processed_at=null, last_error=null
                where id=:id and status=:expectedStatus
                """).param("id", id).param("expectedStatus", expectedStatus).param("resetAttempts", resetAttempts)
                .update() == 1;
    }

    public boolean recoverNotification(UUID id, String expectedStatus, boolean resetAttempts) {
        return jdbc.sql("""
                update notifications
                set status='PENDING', attempt_count=case when :resetAttempts then 0 else attempt_count end,
                    next_attempt_at=now(), sent_at=null, last_error_code=null, last_error_message=null, updated_at=now()
                where id=:id and status=:expectedStatus
                """).param("id", id).param("expectedStatus", expectedStatus).param("resetAttempts", resetAttempts)
                .update() == 1;
    }

    private static OutboxRow mapOutbox(ResultSet rs, int rowNum) throws SQLException {
        return new OutboxRow(rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                rs.getString("aggregate_type"), rs.getObject("aggregate_id", UUID.class), rs.getString("event_type"),
                rs.getString("status"), rs.getInt("attempt_count"), instant(rs, "available_at"),
                instant(rs, "processed_at"), rs.getString("last_error"), instant(rs, "created_at"));
    }

    private static NotificationRow mapNotification(ResultSet rs, int rowNum) throws SQLException {
        return new NotificationRow(rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                rs.getObject("notification_target_id", UUID.class), rs.getObject("recipient_user_id", UUID.class),
                rs.getString("channel"), rs.getString("template_code"), rs.getString("business_type"),
                rs.getObject("business_id", UUID.class), rs.getString("status"), rs.getInt("attempt_count"),
                instant(rs, "next_attempt_at"), instant(rs, "sent_at"), rs.getString("last_error_code"),
                rs.getString("last_error_message"), instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record Page<T>(List<T> items, int page, int size, long totalElements) {}
    public record OutboxRow(UUID id, UUID organizationId, String aggregateType, UUID aggregateId, String eventType,
            String status, int attemptCount, Instant availableAt, Instant processedAt, String lastError, Instant createdAt) {}
    public record NotificationRow(UUID id, UUID organizationId, UUID notificationTargetId, UUID recipientUserId,
            String channel, String templateCode, String businessType, UUID businessId, String status, int attemptCount,
            Instant nextAttemptAt, Instant sentAt, String lastErrorCode, String lastErrorMessage,
            Instant createdAt, Instant updatedAt) {}
}
