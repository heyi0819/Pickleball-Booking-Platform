package com.pickleball.booking.receivable.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.pickleball.booking.identity.application.PlatformTokenService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;
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
class Slice6FinanceHttpEndToEndIT {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("security.jwt.signing-secret",
                () -> "test-only-signing-secret-with-at-least-thirty-two-characters");
    }

    @LocalServerPort int port;
    @Autowired ObjectMapper objectMapper;
    @Autowired PlatformTokenService tokens;
    @Autowired JdbcTemplate jdbc;

    @Test
    void financeCommandsEnforceJwtRoleScopeAndRefundLifecycleThroughRealHttpAndPostgres() throws Exception {
        Fixture fixture = seedFixture();
        String committee = token(fixture.committeeId());
        String student = token(fixture.payerId());
        String foreignCommittee = token(fixture.foreignCommitteeId());
        String platformAdmin = token(fixture.platformAdminId());

        String paidAt = Instant.now().minusSeconds(60).toString();
        JsonNode payment = expectData(request(
                "POST", "/api/v1/receivables/" + fixture.receivableId() + "/payments",
                committee, "s6-http-payment-" + UUID.randomUUID(),
                paymentBody(fixture.payerId(), "1200.00", "BANK_TRANSFER", paidAt), 201));
        UUID paymentId = UUID.fromString(payment.get("paymentId").asText());
        assertThat(payment.get("paymentStatus").asText()).isEqualTo("PAID");
        assertThat(payment.get("outstandingAmount").asText()).isEqualTo("0.00");

        JsonNode studentForbidden = expectError(request(
                "POST", "/api/v1/receivables/" + fixture.receivableId() + "/payments",
                student, "s6-student-forbidden-" + UUID.randomUUID(),
                paymentBody(fixture.payerId(), "100.00", "CASH", paidAt), 403));
        assertThat(studentForbidden.get("code").asText()).isEqualTo("AUTH_FORBIDDEN");

        JsonNode foreignForbidden = expectError(request(
                "POST", "/api/v1/receivables/" + fixture.receivableId() + "/payments",
                foreignCommittee, "s6-foreign-forbidden-" + UUID.randomUUID(),
                paymentBody(fixture.payerId(), "100.00", "CASH", paidAt), 403));
        assertThat(foreignForbidden.get("code").asText()).isEqualTo("AUTH_FORBIDDEN");

        String refundRequestKey = "s6-refund-request-" + UUID.randomUUID();
        JsonNode requested = expectData(request(
                "POST", "/api/v1/receivables/" + fixture.receivableId() + "/refunds",
                committee, refundRequestKey,
                "{\"paymentId\":\"" + paymentId + "\",\"amount\":\"600.00\",\"reason\":\"Slice 6 closure\"}", 201));
        UUID refundId = UUID.fromString(requested.get("refundId").asText());
        assertThat(requested.get("status").asText()).isEqualTo("PENDING_APPROVAL");

        JsonNode requestReplay = expectData(request(
                "POST", "/api/v1/receivables/" + fixture.receivableId() + "/refunds",
                committee, refundRequestKey,
                "{\"paymentId\":\"" + paymentId + "\",\"amount\":\"600.00\",\"reason\":\"Slice 6 closure\"}", 201));
        assertThat(requestReplay.get("refundId").asText()).isEqualTo(refundId.toString());

        JsonNode beforeApproval = expectError(request(
                "POST", "/api/v1/refunds/" + refundId + "/execution",
                committee, "s6-refund-before-approval-" + UUID.randomUUID(),
                executionBody("BANK_TRANSFER", Instant.now().minusSeconds(30).toString(), "RF-BEFORE"), 422));
        assertThat(beforeApproval.get("code").asText()).isEqualTo("REFUND_NOT_APPROVED");

        JsonNode approved = expectData(request(
                "POST", "/api/v1/refunds/" + refundId + "/review",
                committee, "s6-refund-review-" + UUID.randomUUID(),
                "{\"decision\":\"APPROVE\",\"reason\":\"Approved in closure acceptance\"}", 200));
        assertThat(approved.get("status").asText()).isEqualTo("APPROVED");
        assertThat(approved.get("approvedBy").asText()).isEqualTo(fixture.committeeId().toString());

        String executionKey = "s6-refund-execution-" + UUID.randomUUID();
        String refundedAt = Instant.now().minusSeconds(10).toString();
        JsonNode completed = expectData(request(
                "POST", "/api/v1/refunds/" + refundId + "/execution",
                committee, executionKey,
                executionBody("BANK_TRANSFER", refundedAt, "RF-CLOSURE"), 200));
        JsonNode executionReplay = expectData(request(
                "POST", "/api/v1/refunds/" + refundId + "/execution",
                committee, executionKey,
                executionBody("BANK_TRANSFER", refundedAt, "RF-CLOSURE"), 200));
        assertThat(completed.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(executionReplay.get("refundId").asText()).isEqualTo(refundId.toString());
        assertThat(completed.get("processedBy").asText()).isEqualTo(fixture.committeeId().toString());

        JsonNode adminPayment = expectData(request(
                "POST", "/api/v1/receivables/" + fixture.adminReceivableId() + "/payments",
                platformAdmin, "s6-admin-payment-" + UUID.randomUUID(),
                paymentBody(fixture.payerId(), "100.00", "CASH", Instant.now().minusSeconds(5).toString()), 201));
        assertThat(adminPayment.get("paymentStatus").asText()).isEqualTo("PARTIALLY_PAID");
        assertThat(adminPayment.get("outstandingAmount").asText()).isEqualTo("1100.00");

        assertThat(jdbc.queryForObject(
                "select count(*) from refunds where id=?", Integer.class, refundId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_logs where entity_id=? and action='REFUND_REQUESTED'",
                Integer.class, refundId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_logs where entity_id=? and action='REFUND_APPROVED'",
                Integer.class, refundId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_logs where entity_id=? and action='REFUND_COMPLETED'",
                Integer.class, refundId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from outbox_events where aggregate_id=? and event_type='REFUND_REQUESTED'",
                Integer.class, refundId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from outbox_events where aggregate_id=? and event_type='REFUND_APPROVED'",
                Integer.class, refundId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from outbox_events where aggregate_id=? and event_type='REFUND_COMPLETED'",
                Integer.class, refundId)).isEqualTo(1);
    }

    private Fixture seedFixture() {
        UUID organizationId = UUID.randomUUID();
        UUID foreignOrganizationId = UUID.randomUUID();
        UUID payerId = UUID.randomUUID();
        UUID committeeId = UUID.randomUUID();
        UUID foreignCommitteeId = UUID.randomUUID();
        UUID platformAdminId = UUID.randomUUID();

        jdbc.update("insert into organizations(id, code, name) values (?, ?, ?)",
                organizationId, "s6-http-" + compact(organizationId), "Slice 6 HTTP Org");
        jdbc.update("insert into organizations(id, code, name) values (?, ?, ?)",
                foreignOrganizationId, "s6-foreign-" + compact(foreignOrganizationId), "Slice 6 Foreign Org");
        jdbc.update("insert into users(id, display_name) values (?, 'Slice 6 payer')", payerId);
        jdbc.update("insert into users(id, display_name) values (?, 'Slice 6 committee')", committeeId);
        jdbc.update("insert into users(id, display_name) values (?, 'Slice 6 foreign committee')", foreignCommitteeId);
        jdbc.update("insert into users(id, display_name) values (?, 'Slice 6 platform admin')", platformAdminId);
        role(organizationId, payerId, "STUDENT", committeeId);
        role(organizationId, committeeId, "COMMITTEE", committeeId);
        role(foreignOrganizationId, foreignCommitteeId, "COMMITTEE", foreignCommitteeId);
        role(null, platformAdminId, "PLATFORM_ADMIN", platformAdminId);

        UUID receivableId = seedReceivable(organizationId, payerId, committeeId, "primary");
        UUID adminReceivableId = seedReceivable(organizationId, payerId, committeeId, "admin");
        return new Fixture(
                organizationId,
                payerId,
                committeeId,
                foreignCommitteeId,
                platformAdminId,
                receivableId,
                adminReceivableId);
    }

    private UUID seedReceivable(UUID organizationId, UUID payerId, UUID committeeId, String tag) {
        UUID courseId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        UUID priceSnapshotId = UUID.randomUUID();
        UUID receivableId = UUID.randomUUID();
        UUID receivableItemId = UUID.randomUUID();

        jdbc.update("""
                insert into courses(
                    id, organization_id, course_no, created_by_user_id, course_type,
                    schedule_type, billing_mode, expected_participant_count,
                    guest_participant_count, total_session_count, status)
                values (?, ?, ?, ?, 'GROUP', 'SINGLE', 'FULL_COURSE', 1, 0, 1, 'ACTIVE')
                """, courseId, organizationId, "S6HTTP-" + tag + "-" + compact(courseId), committeeId);
        jdbc.update("""
                insert into course_sessions(
                    id, organization_id, course_id, sequence_no, scheduled_start_at, scheduled_end_at,
                    expected_participant_count, guest_participant_count, status)
                values (?, ?, ?, 1, '2030-09-01 10:00+00', '2030-09-01 11:00+00', 1, 0, 'SCHEDULED')
                """, sessionId, organizationId, courseId);
        jdbc.update("insert into course_memberships(id, organization_id, course_id, user_id, status) values (?, ?, ?, ?, 'ACTIVE')",
                membershipId, organizationId, courseId, payerId);
        jdbc.update("""
                insert into enrollments(id, organization_id, course_membership_id, course_session_id, user_id, status)
                values (?, ?, ?, ?, ?, 'SCHEDULED')
                """, enrollmentId, organizationId, membershipId, sessionId, payerId);
        jdbc.update("""
                insert into session_price_snapshots(
                    id, organization_id, course_session_id, version_no, status, tuition_amount, venue_fee,
                    other_adjustment, total_receivable, confirmed_by, confirmed_at, created_by)
                values (?, ?, ?, 1, 'CONFIRMED', 1200.00, 0, 0, 1200.00, ?, now(), ?)
                """, priceSnapshotId, organizationId, sessionId, committeeId, committeeId);
        jdbc.update("""
                insert into receivables(
                    id, organization_id, receivable_no, course_id, payer_user_id,
                    billing_mode, total_amount, balance_amount, status)
                values (?, ?, ?, ?, ?, 'FULL_COURSE', 1200.00, 1200.00, 'OPEN')
                """, receivableId, organizationId, "AR-" + tag + "-" + compact(receivableId), courseId, payerId);
        jdbc.update("""
                insert into receivable_items(
                    id, receivable_id, course_session_id, enrollment_id, price_snapshot_id,
                    amount, paid_amount, refunded_amount, status)
                values (?, ?, ?, ?, ?, 1200.00, 0, 0, 'OPEN')
                """, receivableItemId, receivableId, sessionId, enrollmentId, priceSnapshotId);
        return receivableId;
    }

    private void role(UUID organizationId, UUID userId, String role, UUID grantedBy) {
        jdbc.update("""
                insert into user_role_assignments(
                    id, organization_id, user_id, role_code, status, granted_by, granted_at)
                values (?, ?, ?, ?, 'ACTIVE', ?, now())
                """, UUID.randomUUID(), organizationId, userId, role, grantedBy);
    }

    private String token(UUID userId) {
        return tokens.issue(userId).value();
    }

    private String paymentBody(UUID payerId, String amount, String method, String paidAt) {
        return """
                {"amount":"%s","method":"%s","paidAt":"%s","payerUserId":"%s","note":"Slice 6 HTTP closure"}
                """.formatted(amount, method, paidAt, payerId);
    }

    private String executionBody(String method, String refundedAt, String reference) {
        return """
                {"method":"%s","refundedAt":"%s","reference":"%s"}
                """.formatted(method, refundedAt, reference);
    }

    private JsonNode expectData(HttpResponse<String> response) throws Exception {
        JsonNode envelope = objectMapper.readTree(response.body());
        assertThat(envelope.has("data")).withFailMessage("Missing data envelope: %s", response.body()).isTrue();
        return envelope.get("data");
    }

    private JsonNode expectError(HttpResponse<String> response) throws Exception {
        JsonNode envelope = objectMapper.readTree(response.body());
        assertThat(envelope.has("error")).withFailMessage("Missing error envelope: %s", response.body()).isTrue();
        return envelope.get("error");
    }

    private HttpResponse<String> request(
            String method, String path, String token, String idempotencyKey, String body, int expectedStatus)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token);
        if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);
        if (body != null) {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        HttpResponse<String> response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode())
                .withFailMessage("Expected HTTP %s but got %s: %s", expectedStatus, response.statusCode(), response.body())
                .isEqualTo(expectedStatus);
        return response;
    }

    private static String compact(UUID id) {
        return id.toString().replace("-", "").substring(0, 12);
    }

    private record Fixture(
            UUID organizationId,
            UUID payerId,
            UUID committeeId,
            UUID foreignCommitteeId,
            UUID platformAdminId,
            UUID receivableId,
            UUID adminReceivableId) { }
}
