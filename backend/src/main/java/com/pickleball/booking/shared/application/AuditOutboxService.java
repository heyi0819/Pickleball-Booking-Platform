package com.pickleball.booking.shared.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class AuditOutboxService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AuditOutboxService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void record(UUID org, UUID actor, String action, String type, UUID id, String reason) {
        record(org, actor, action, type, id, reason, null, null, null);
    }

    public void record(
            UUID org,
            UUID actor,
            String action,
            String type,
            UUID id,
            String reason,
            Object before,
            Object after,
            String requestId) {
        recordAudit(org, actor, action, type, id, reason, before, after, requestId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", action);
        payload.put("entityType", type);
        payload.put("entityId", id);
        if (requestId != null) payload.put("requestId", requestId);
        if (after != null) payload.put("after", after);

        jdbc.update("""
                insert into outbox_events(
                    id, organization_id, aggregate_type, aggregate_id, event_type, payload,
                    status, attempt_count, available_at, created_at)
                values (?, ?, ?, ?, ?, cast(? as jsonb), 'PENDING', 0, now(), now())
                """, UUID.randomUUID(), org, type, id, action, json(payload));
    }

    /** Records an operator action without producing another event for the queue being recovered. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAudit(
            UUID org,
            UUID actor,
            String action,
            String type,
            UUID id,
            String reason,
            Object before,
            Object after,
            String requestId) {
        String beforeJson = json(before);
        String afterJson = json(after);
        jdbc.update("""
                insert into audit_logs(
                    organization_id, actor_user_id, actor_type, action, entity_type, entity_id,
                    before_data, after_data, reason, request_id, created_at)
                values (?, ?, 'USER', ?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?, ?, now())
                """, org, actor, action, type, id, beforeJson, afterJson, reason, requestId);
    }

    private String json(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Unable to serialize audit/outbox payload", ex);
        }
    }
}
