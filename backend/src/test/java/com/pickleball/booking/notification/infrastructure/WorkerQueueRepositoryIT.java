package com.pickleball.booking.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class WorkerQueueRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired JdbcTemplate jdbc;
    @Autowired WorkerQueueRepository repository;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("security.jwt.signing-secret",
                () -> "test-only-signing-secret-with-at-least-thirty-two-characters");
    }

    @Test
    void claimsOnlyDueOutboxRowsAndSupportsRetryThenProcessed() {
        UUID org = insertOrganization();
        UUID due = UUID.randomUUID();
        UUID future = UUID.randomUUID();
        insertOutbox(due, org, "PENDING", 0, "now()");
        insertOutbox(future, org, "FAILED", 2, "now() + interval '1 hour'");

        var claimed = repository.claimOutbox(10);

        assertThat(claimed).extracting(message -> message.id()).contains(due).doesNotContain(future);
        assertThat(claimed.getFirst().attemptCount()).isEqualTo(1);
        assertThat(status("outbox_events", due)).isEqualTo("PROCESSING");

        repository.markOutboxFailed(due, false, Duration.ofSeconds(30), "temporary");
        assertThat(status("outbox_events", due)).isEqualTo("FAILED");

        jdbc.update("update outbox_events set available_at=now() where id=?", due);
        var retried = repository.claimOutbox(10);
        assertThat(retried).hasSize(1);
        assertThat(retried.getFirst().attemptCount()).isEqualTo(2);

        repository.markOutboxProcessed(due);
        assertThat(status("outbox_events", due)).isEqualTo("PROCESSED");
        assertThat(jdbc.queryForObject(
                "select processed_at is not null from outbox_events where id=?", Boolean.class, due)).isTrue();
    }

    @Test
    void notificationQueueTransitionsSendingFailedDeadAndSent() {
        UUID org = insertOrganization();
        UUID notification = insertNotification(org, 0);

        var claimed = repository.claimNotifications(10);
        assertThat(claimed).hasSize(1);
        assertThat(claimed.getFirst().attemptCount()).isEqualTo(1);
        assertThat(status("notifications", notification)).isEqualTo("SENDING");

        repository.markNotificationFailed(notification, false, Duration.ofSeconds(30), "TEMP", "temporary");
        assertThat(status("notifications", notification)).isEqualTo("FAILED");

        jdbc.update("update notifications set next_attempt_at=now(), attempt_count=4 where id=?", notification);
        var lastAttempt = repository.claimNotifications(10);
        assertThat(lastAttempt).hasSize(1);
        assertThat(lastAttempt.getFirst().attemptCount()).isEqualTo(5);
        repository.markNotificationFailed(notification, true, Duration.ZERO, "PERM", "dead");
        assertThat(status("notifications", notification)).isEqualTo("DEAD");

        UUID sent = insertNotification(org, 0);
        repository.claimNotifications(10);
        repository.markNotificationSent(sent);
        assertThat(status("notifications", sent)).isEqualTo("SENT");
        assertThat(jdbc.queryForObject(
                "select sent_at is not null from notifications where id=?", Boolean.class, sent)).isTrue();
    }

    private UUID insertOrganization() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into organizations(id, code, name) values (?, ?, 'worker test')",
                id, "worker-" + id.toString().substring(0, 8));
        return id;
    }

    private void insertOutbox(UUID id, UUID org, String state, int attempts, String availableExpression) {
        jdbc.update("""
                insert into outbox_events(
                    id, organization_id, aggregate_type, aggregate_id, event_type,
                    payload, status, attempt_count, available_at, created_at)
                values (?, ?, 'Course', ?, 'CourseChanged', '{}'::jsonb, ?, ?, %s, now())
                """.formatted(availableExpression), id, org, UUID.randomUUID(), state, attempts);
    }

    private UUID insertNotification(UUID org, int attempts) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into notifications(
                    id, organization_id, channel, template_code, business_type,
                    business_id, payload, status, attempt_count, dedupe_key)
                values (?, ?, 'LINE', 'WORKER_TEST', 'COURSE', ?, '{}'::jsonb,
                        'PENDING', ?, ?)
                """, id, org, UUID.randomUUID(), attempts, "worker-" + id);
        return id;
    }

    private String status(String table, UUID id) {
        return jdbc.queryForObject("select status from " + table + " where id=?", String.class, id);
    }
}
