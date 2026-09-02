package com.pickleball.booking.receivable.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.pickleball.booking.identity.application.PlatformTokenService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
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
class AdminFinanceReadHttpIT {
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
    @Autowired ObjectMapper json;
    @Autowired PlatformTokenService tokens;
    @Autowired JdbcTemplate jdbc;
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void allSixReadsEnforceAuthenticationOrganizationAndResourceScope() throws Exception {
        Fixture f = fixture();
        for (String kind : List.of("receivables", "payments", "refunds")) {
            String list = "/api/v1/admin/" + kind;
            String detail = list + "/" + f.own().ids().get(kind);
            for (String path : List.of(list, detail)) {
                get(path + "?organizationId=" + f.own().org(), null, 401);
                get(path + "?organizationId=" + f.own().org(), f.member(), 403);
                get(path + "?organizationId=" + f.foreign().org(), f.committee(), 403);
                get(path, f.admin(), 400);
                get(path + "?organizationId=not-a-uuid", f.admin(), 400);
                get(path + "?organizationId=" + UUID.randomUUID(), f.admin(), 404);
            }
            var rows = data(get(list + "?organizationId=" + f.own().org(), f.committee(), 200));
            assertThat(rows.get("totalElements").asLong()).isEqualTo(1);
            assertThat(rows.get("items").get(0).get("id").asText()).isEqualTo(f.own().ids().get(kind).toString());
            assertThat(data(get(detail + "?organizationId=" + f.own().org(), f.committee(), 200))
                    .get("organizationId").asText()).isEqualTo(f.own().org().toString());

            // A real foreign ID and an unknown ID both remain indistinguishable within the permitted scope.
            for (UUID id : List.of(f.foreign().ids().get(kind), UUID.randomUUID())) {
                var error = get(list + "/" + id + "?organizationId=" + f.own().org(), f.committee(), 404);
                assertThat(error.get("error").get("code").asText()).isEqualTo("RESOURCE_NOT_FOUND");
            }
            for (Ledger ledger : List.of(f.own(), f.foreign())) {
                var adminRows = data(get(list + "?organizationId=" + ledger.org(), f.admin(), 200));
                assertThat(adminRows.get("totalElements").asLong()).isEqualTo(1);
                assertThat(adminRows.get("items").get(0).get("organizationId").asText()).isEqualTo(ledger.org().toString());
                get(list + "/" + ledger.ids().get(kind) + "?organizationId=" + ledger.org(), f.admin(), 200);
            }
        }
    }

    @Test
    void revokedCommitteeAndInactiveUserCannotReadWithPreviouslyIssuedToken() throws Exception {
        Fixture f = fixture();
        String token = tokens.issue(f.committee()).value();
        jdbc.update("update user_role_assignments set status='REVOKED' where user_id=?", f.committee());
        for (String kind : List.of("receivables", "payments", "refunds")) {
            getWithToken("/api/v1/admin/" + kind + "?organizationId=" + f.own().org(), token, 403);
            getWithToken("/api/v1/admin/" + kind + "/" + f.own().ids().get(kind) + "?organizationId=" + f.own().org(), token, 403);
        }
        String adminToken = tokens.issue(f.admin()).value();
        jdbc.update("update users set status='SUSPENDED' where id=?", f.admin());
        for (String kind : List.of("receivables", "payments", "refunds")) {
            getWithToken("/api/v1/admin/" + kind + "?organizationId=" + f.own().org(), adminToken, 403);
            getWithToken("/api/v1/admin/" + kind + "/" + f.own().ids().get(kind) + "?organizationId=" + f.own().org(), adminToken, 403);
        }
    }

    @Test
    void listsValidateFiltersAndPaginationWithoutOverflowAndOrderTiesDeterministically() throws Exception {
        Fixture f = fixture();
        Ledger second = ledger(f.own().org(), f.member(), f.committee());
        for (String kind : List.of("receivables", "payments", "refunds")) {
            String timestamp = kind.equals("payments") ? "paid_at" : kind.equals("refunds") ? "requested_at" : "created_at";
            jdbc.update("update " + kind + " set " + timestamp + "='2030-01-01T00:00:00Z' where organization_id=?", f.own().org());
            String path = "/api/v1/admin/" + kind + "?organizationId=" + f.own().org();
            for (String bad : List.of("&page=-1", "&size=0", "&size=101", "&status=UNKNOWN", "&memberId=invalid")) {
                assertThat(get(path + bad, f.committee(), 400).get("error").get("code").asText()).isEqualTo("VALIDATION_FAILED");
            }
            var first = data(get(path + "&size=1", f.committee(), 200));
            var next = data(get(path + "&page=1&size=1", f.committee(), 200));
            var ids = List.of(f.own().ids().get(kind).toString(), second.ids().get(kind).toString())
                    .stream().sorted(Comparator.reverseOrder()).toList();
            assertThat(first.get("totalElements").asLong()).isEqualTo(2);
            assertThat(first.get("items").get(0).get("id").asText()).isEqualTo(ids.get(0));
            assertThat(next.get("items").get(0).get("id").asText()).isEqualTo(ids.get(1));
            assertThat(data(get(path + "&page=2147483647&size=100", f.committee(), 200)).get("items").size()).isZero();
            assertThat(data(get(path + "&memberId=" + UUID.randomUUID(), f.committee(), 200)).get("totalElements").asLong()).isZero();
            assertThat(data(get(path + "&memberId=" + f.member(), f.committee(), 200)).get("totalElements").asLong()).isEqualTo(2);
        }
        String org = "?organizationId=" + f.own().org();
        assertOne("/api/v1/admin/receivables" + org + "&courseId=" + f.own().course() + "&status=PARTIALLY_PAID", f.committee());
        assertOne("/api/v1/admin/payments" + org + "&receivableId=" + f.own().ids().get("receivables") + "&status=COMPLETED", f.committee());
        assertOne("/api/v1/admin/refunds" + org + "&paymentId=" + f.own().ids().get("payments") + "&status=PENDING_APPROVAL", f.committee());
        // Relationship filters never broaden organization scope.
        assertThat(data(get("/api/v1/admin/payments" + org + "&receivableId=" + f.foreign().ids().get("receivables"), f.admin(), 200))
                .get("totalElements").asLong()).isZero();
        assertThat(data(get("/api/v1/admin/refunds" + org + "&paymentId=" + f.foreign().ids().get("payments"), f.admin(), 200))
                .get("totalElements").asLong()).isZero();
    }

    @Test
    void safeReadableProjectionsPreserveLedgerAmountsReferencesAndNullableTimestampsWithoutWrites() throws Exception {
        Fixture f = fixture();
        var before = snapshot();
        String org = "?organizationId=" + f.own().org();
        JsonNode receivable = data(get("/api/v1/admin/receivables/" + f.own().ids().get("receivables") + org, f.committee(), 200));
        assertThat(receivable.get("memberName").asText()).isEqualTo("Finance fixture member");
        assertThat(receivable.get("courseNo").asText()).startsWith("S6HTTP-");
        assertThat(receivable.get("totalAmount").asText()).isEqualTo("1200.00");
        assertThat(receivable.get("adjustedAmount").asText()).isEqualTo("0.00");
        assertThat(receivable.get("paidAmount").asText()).isEqualTo("600.00");
        assertThat(receivable.get("outstandingAmount").asText()).isEqualTo("600.00");
        assertThat(receivable.get("dueAt").isNull()).isTrue();
        JsonNode payment = data(get("/api/v1/admin/payments/" + f.own().ids().get("payments") + org, f.committee(), 200));
        assertThat(payment.get("amount").asText()).isEqualTo("600.00");
        assertThat(payment.get("refundableAmount").asText()).isEqualTo("550.00");
        assertThat(payment.get("receivables").size()).isEqualTo(1);
        assertThat(payment.get("receivables").get(0).get("id").asText()).isEqualTo(f.own().ids().get("receivables").toString());
        JsonNode refund = data(get("/api/v1/admin/refunds/" + f.own().ids().get("refunds") + org, f.committee(), 200));
        assertThat(refund.get("amount").asText()).isEqualTo("50.00");
        assertThat(refund.get("paymentNo").asText()).isEqualTo(payment.get("paymentNo").asText());
        assertThat(refund.get("refundableAmount").asText()).isEqualTo("600.00");
        assertThat(refund.get("approvedAt").isNull()).isTrue();
        assertThat(refund.get("refundedAt").isNull()).isTrue();
        for (String kind : List.of("receivables", "payments", "refunds")) {
            var listItem = data(get("/api/v1/admin/" + kind + org, f.committee(), 200)).get("items").get(0);
            var detail = data(get("/api/v1/admin/" + kind + "/" + f.own().ids().get(kind) + org, f.committee(), 200));
            assertThat(listItem).isEqualTo(detail);
            assertThat(detail.get("currency").asText()).isEqualTo("TWD");
            assertThat(detail.toString()).doesNotContain("email", "idempotencyKey", "failureReason", "referenceNo", "recordedBy", "providerSubject", "note", "version");
        }
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void refundableAmountsUseExistingReservedStatesAndExcludeCurrentReviewRequest() throws Exception {
        Fixture f = fixture();
        UUID payment = f.own().ids().get("payments");
        for (String status : List.of("APPROVED", "COMPLETED", "REJECTED", "FAILED", "CANCELLED")) {
            UUID id = UUID.randomUUID();
            jdbc.update("""
                    insert into refunds(id,organization_id,refund_no,payment_id,amount,status,reason,requested_by,
                        approved_by,approved_at,processed_by,refunded_at,refund_method)
                    values (?,?,?,?,25.00,?,'Synthetic reserve fixture',?,?,now(),?,now(),'CASH')
                    """, id,f.own().org(),"RF-"+compact(id),payment,status,f.committee(),f.committee(),f.committee());
        }
        String org = "?organizationId=" + f.own().org();
        var p = data(get("/api/v1/admin/payments/" + payment + org, f.committee(), 200));
        assertThat(p.get("refundableAmount").asText()).isEqualTo("500.00"); // 600 - pending 50 - approved 25 - completed 25
        var r = data(get("/api/v1/admin/refunds/" + f.own().ids().get("refunds") + org, f.committee(), 200));
        assertThat(r.get("refundableAmount").asText()).isEqualTo("550.00"); // current pending request excluded
        for (String status : List.of("PENDING_APPROVAL", "APPROVED", "COMPLETED", "REJECTED", "FAILED", "CANCELLED")) {
            assertOne("/api/v1/admin/refunds" + org + "&status=" + status, f.committee());
        }
    }

    @Test
    void allReceivableAndPaymentStatusesAreReadWithoutSemanticRemapping() throws Exception {
        Fixture f = fixture();
        String org = "?organizationId=" + f.own().org();
        for (String status : List.of("OPEN", "PARTIALLY_PAID", "PAID", "OVERDUE", "CANCELLED", "REFUNDED")) {
            jdbc.update("update receivables set status=? where id=?", status, f.own().ids().get("receivables"));
            assertOne("/api/v1/admin/receivables" + org + "&status=" + status, f.committee());
        }
        for (String status : List.of("COMPLETED", "PARTIALLY_REFUNDED", "REFUNDED", "VOIDED")) {
            jdbc.update("update payments set status=? where id=?", status, f.own().ids().get("payments"));
            assertOne("/api/v1/admin/payments" + org + "&status=" + status, f.committee());
        }
    }

    private void assertOne(String path, UUID user) throws Exception {
        assertThat(data(get(path,user,200)).get("totalElements").asLong()).isEqualTo(1);
    }

    private Map<String,List<Map<String,Object>>> snapshot() {
        var result = new LinkedHashMap<String,List<Map<String,Object>>>();
        for (String table : List.of("receivables", "receivable_items", "receivable_adjustments", "payments", "payment_allocations",
                "refunds", "audit_logs", "outbox_events", "api_idempotency_keys")) {
            result.put(table,jdbc.queryForList("select * from " + table + " order by id"));
        }
        return result;
    }

    private Fixture fixture() throws Exception {
        UUID org = UUID.randomUUID(), foreignOrg = UUID.randomUUID();
        UUID member = user("Finance fixture member"), committee = user("Finance fixture committee");
        UUID admin = user("Finance fixture administrator"), foreignCommittee = user("Finance foreign committee");
        for (UUID id : List.of(org,foreignOrg)) {
            jdbc.update("insert into organizations(id,code,name) values (?,?,'Finance fixture organization')",id,"P5F-"+compact(id));
        }
        role(org,committee,"COMMITTEE",committee);
        role(org,member,"STUDENT",committee);
        role(foreignOrg,foreignCommittee,"COMMITTEE",foreignCommittee);
        role(null,admin,"PLATFORM_ADMIN",admin);
        return new Fixture(member,committee,admin,ledger(org,member,committee),ledger(foreignOrg,member,foreignCommittee));
    }

    private UUID user(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into users(id,display_name) values (?,?)",id,name);
        return id;
    }

    private Ledger ledger(UUID org, UUID member, UUID committee) throws Exception {
        UUID receivable = seedReceivable(org,member,committee,"p5f");
        var payment = post("/api/v1/receivables/" + receivable + "/payments", committee,
                """
                {"amount":"600.00","method":"CASH","paidAt":"2026-01-01T00:00:00Z","payerUserId":"%s","note":"Synthetic private note"}
                """.formatted(member));
        UUID paymentId = UUID.fromString(payment.get("paymentId").asText());
        var refund = post("/api/v1/receivables/" + receivable + "/refunds",committee,
                """
                {"paymentId":"%s","amount":"50.00","reason":"Synthetic refund request"}
                """.formatted(paymentId));
        UUID course = jdbc.queryForObject("select course_id from receivables where id=?",UUID.class,receivable);
        return new Ledger(org,course,Map.of("receivables",receivable,"payments",paymentId,
                "refunds",UUID.fromString(refund.get("refundId").asText())));
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


    private JsonNode get(String path, UUID user, int expected) throws Exception {
        return getWithToken(path,user == null ? null : tokens.issue(user).value(),expected);
    }

    private JsonNode getWithToken(String path, String token, int expected) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (token != null) request.header("Authorization","Bearer " + token);
        var response = http.send(request.build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).withFailMessage("%s: expected %s; got %s %s",path,expected,response.statusCode(),response.body()).isEqualTo(expected);
        return json.readTree(response.body());
    }

    private JsonNode post(String path, UUID user, String body) throws Exception {
        var response = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization","Bearer " + tokens.issue(user).value())
                .header("Content-Type","application/json").header("Idempotency-Key","p5f-"+UUID.randomUUID())
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).withFailMessage("%s: %s",path,response.body()).isEqualTo(201);
        return data(json.readTree(response.body()));
    }

    private static JsonNode data(JsonNode envelope) { return envelope.get("data"); }
    private static String compact(UUID id) { return id.toString().replace("-","").substring(0,12); }
    private record Ledger(UUID org, UUID course, Map<String,UUID> ids) {}
    private record Fixture(UUID member, UUID committee, UUID admin, Ledger own, Ledger foreign) {}
}
