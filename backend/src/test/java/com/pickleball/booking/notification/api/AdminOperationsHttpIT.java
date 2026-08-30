package com.pickleball.booking.notification.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.pickleball.booking.identity.application.PlatformTokenService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AdminOperationsHttpIT {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("security.jwt.signing-secret",
                () -> "test-only-signing-secret-with-at-least-thirty-two-characters");
    }

    @LocalServerPort int port;
    @Autowired PlatformTokenService tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void queriesAndRecoveryEnforceRoleScopeEligibilityIdempotencyAndAudit() throws Exception {
        Fixture fixture = seed();
        String ownPath = "/api/v1/admin/outbox-events?organizationId=" + fixture.organizationId();
        assertThat(request("GET", ownPath, token(fixture.committeeId()), null, null).statusCode()).isEqualTo(200);
        assertThat(request("GET", ownPath, token(fixture.platformAdminId()), null, null).statusCode()).isEqualTo(200);
        assertThat(request("GET", ownPath, token(fixture.foreignCommitteeId()), null, null).statusCode()).isEqualTo(403);
        assertThat(request("GET", ownPath, token(fixture.studentId()), null, null).statusCode()).isEqualTo(403);

        String key = "outbox-retry-" + UUID.randomUUID();
        JsonNode recovered = data(request("POST", "/api/v1/admin/outbox-events/" + fixture.failedOutboxId() + "/retry",
                token(fixture.committeeId()), key, "{\"reason\":\"dependency restored\"}"), 200);
        assertThat(recovered.get("status").asText()).isEqualTo("PENDING");
        assertThat(recovered.get("attemptCount").asInt()).isEqualTo(2);
        assertThat(recovered.get("lastError").isNull()).isTrue();

        assertThat(request("POST", "/api/v1/admin/outbox-events/" + fixture.failedOutboxId() + "/retry",
                token(fixture.committeeId()), key, "{\"reason\":\"dependency restored\"}").statusCode()).isEqualTo(200);
        JsonNode conflict = error(request("POST", "/api/v1/admin/outbox-events/" + fixture.failedOutboxId() + "/retry",
                token(fixture.committeeId()), key, "{\"reason\":\"different request\"}"), 409);
        assertThat(conflict.get("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");

        JsonNode ineligible = error(request("POST", "/api/v1/admin/outbox-events/" + fixture.processedOutboxId() + "/retry",
                token(fixture.committeeId()), "processed-" + UUID.randomUUID(), "{\"reason\":\"must be rejected\"}"), 409);
        assertThat(ineligible.get("code").asText()).isEqualTo("RECOVERY_NOT_ELIGIBLE");

        JsonNode notification = data(request("POST", "/api/v1/admin/notifications/" + fixture.deadNotificationId() + "/retry",
                token(fixture.platformAdminId()), "notification-" + UUID.randomUUID(), "{\"reason\":\"poison payload corrected\"}"), 200);
        assertThat(notification.get("status").asText()).isEqualTo("PENDING");
        assertThat(notification.get("attemptCount").asInt()).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from audit_logs
                where entity_id=? and action='NOTIFICATION_REQUEUED' and actor_user_id=?
                  and reason='poison payload corrected'
                  and before_data->>'status'='DEAD' and after_data->>'status'='PENDING'
                """, Integer.class, fixture.deadNotificationId(), fixture.platformAdminId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from outbox_events where aggregate_id=? and event_type='NOTIFICATION_REQUEUED'",
                Integer.class, fixture.deadNotificationId())).isZero();
    }

    @Test
    void concurrentManualRecoveryAllowsExactlyOneStateTransitionAndAudit() throws Exception {
        Fixture fixture = seed();
        String path = "/api/v1/admin/outbox-events/" + fixture.failedOutboxId() + "/retry";
        String committee = token(fixture.committeeId());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var first = pool.submit(() -> {
                start.await();
                return request("POST", path, committee, "concurrent-a-" + UUID.randomUUID(), "{\"reason\":\"operator A\"}");
            });
            var second = pool.submit(() -> {
                start.await();
                return request("POST", path, committee, "concurrent-b-" + UUID.randomUUID(), "{\"reason\":\"operator B\"}");
            });
            start.countDown();
            List<Integer> statuses = List.of(first.get(20, TimeUnit.SECONDS).statusCode(),
                    second.get(20, TimeUnit.SECONDS).statusCode());
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_logs where entity_id=? and action='OUTBOX_EVENT_RETRY_REQUESTED'",
                Integer.class, fixture.failedOutboxId())).isEqualTo(1);
    }

    private Fixture seed() {
        UUID org = UUID.randomUUID();
        UUID foreignOrg = UUID.randomUUID();
        UUID committee = UUID.randomUUID();
        UUID foreignCommittee = UUID.randomUUID();
        UUID student = UUID.randomUUID();
        UUID platformAdmin = UUID.randomUUID();
        jdbc.update("insert into organizations(id, code, name) values (?, ?, 'S8.4 org')", org, "s84-" + compact(org));
        jdbc.update("insert into organizations(id, code, name) values (?, ?, 'S8.4 foreign')", foreignOrg, "s84f-" + compact(foreignOrg));
        jdbc.update("insert into users(id, display_name) values (?, 'S8.4 committee')", committee);
        jdbc.update("insert into users(id, display_name) values (?, 'S8.4 foreign committee')", foreignCommittee);
        jdbc.update("insert into users(id, display_name) values (?, 'S8.4 student')", student);
        jdbc.update("insert into users(id, display_name) values (?, 'S8.4 platform admin')", platformAdmin);
        role(org, committee, "COMMITTEE", committee);
        role(foreignOrg, foreignCommittee, "COMMITTEE", foreignCommittee);
        role(org, student, "STUDENT", committee);
        role(null, platformAdmin, "PLATFORM_ADMIN", platformAdmin);
        UUID failed = outbox(org, "FAILED", 2, "dependency timeout");
        UUID processed = outbox(org, "PROCESSED", 1, null);
        UUID deadNotification = notification(org, "DEAD", 5, "POISON", "invalid template payload");
        return new Fixture(org, committee, foreignCommittee, student, platformAdmin, failed, processed, deadNotification);
    }

    private UUID outbox(UUID org, String status, int attempts, String lastError) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into outbox_events(id, organization_id, aggregate_type, aggregate_id, event_type,
                    payload, status, attempt_count, available_at, processed_at, last_error)
                values (?, ?, 'Course', ?, 'CourseChanged', '{}'::jsonb, ?, ?, now() - interval '1 minute',
                    case when ?='PROCESSED' then now() else null end, ?)
                """, id, org, UUID.randomUUID(), status, attempts, status, lastError);
        return id;
    }

    private UUID notification(UUID org, String status, int attempts, String errorCode, String errorMessage) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into notifications(id, organization_id, channel, template_code, business_type, business_id,
                    payload, status, attempt_count, next_attempt_at, last_error_code, last_error_message, dedupe_key)
                values (?, ?, 'LINE', 'S8_4_TEST', 'COURSE', ?, '{}'::jsonb, ?, ?, null, ?, ?, ?)
                """, id, org, UUID.randomUUID(), status, attempts, errorCode, errorMessage, "s84-" + id);
        return id;
    }

    private void role(UUID organizationId, UUID userId, String role, UUID grantedBy) {
        jdbc.update("""
                insert into user_role_assignments(id, organization_id, user_id, role_code, status, granted_by, granted_at)
                values (?, ?, ?, ?, 'ACTIVE', ?, now())
                """, UUID.randomUUID(), organizationId, userId, role, grantedBy);
    }

    private String token(UUID userId) { return tokens.issue(userId).value(); }

    private HttpResponse<String> request(String method, String path, String token, String idempotencyKey, String body)
            throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "application/json").header("Authorization", "Bearer " + token);
        if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);
        if (body == null) builder.method(method, HttpRequest.BodyPublishers.noBody());
        else builder.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body));
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode data(HttpResponse<String> response, int status) throws Exception {
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(status);
        return objectMapper.readTree(response.body()).get("data");
    }

    private JsonNode error(HttpResponse<String> response, int status) throws Exception {
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(status);
        return objectMapper.readTree(response.body()).get("error");
    }

    private static String compact(UUID id) { return id.toString().replace("-", "").substring(0, 12); }

    private record Fixture(UUID organizationId, UUID committeeId, UUID foreignCommitteeId, UUID studentId,
            UUID platformAdminId, UUID failedOutboxId, UUID processedOutboxId, UUID deadNotificationId) {}
}
