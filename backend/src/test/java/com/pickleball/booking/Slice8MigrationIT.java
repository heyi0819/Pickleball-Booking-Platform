package com.pickleball.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class Slice8MigrationIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("security.jwt.signing-secret",
                () -> "test-only-signing-secret-with-at-least-thirty-two-characters");
    }

    @Test
    void emptyDatabaseMigratesThroughSliceEightPersistence() {
        assertThat(latestVersion(jdbc, "flyway_schema_history")).isEqualTo("12");
        assertThat(tableExists("notification_targets")).isTrue();
        assertThat(tableExists("notifications")).isTrue();
        assertThat(tableExists("outbox_events")).isTrue();
    }

    @Test
    void v11RowsForwardMigrateWithoutRewritingHistory() {
        String schema = "slice8_upgrade_" + compact(UUID.randomUUID());
        DriverManagerDataSource upgradeDataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        JdbcTemplate upgradeJdbc = new JdbcTemplate(upgradeDataSource);
        upgradeJdbc.execute("create schema " + schema);

        Flyway.configure()
                .dataSource(upgradeDataSource)
                .schemas(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("11"))
                .load()
                .migrate();

        UUID organizationId = UUID.randomUUID();
        upgradeJdbc.update("insert into " + schema + ".organizations(id, code, name) values (?, ?, ?)",
                organizationId, "s8-" + compact(organizationId), "Slice 8 upgrade");
        UUID outboxId = UUID.randomUUID();
        upgradeJdbc.update("""
                insert into %s.outbox_events(
                    id, organization_id, aggregate_type, aggregate_id, event_type,
                    payload, status, attempt_count, available_at, created_at)
                values (?, ?, 'Course', ?, 'CourseChanged', '{}'::jsonb,
                        'PENDING', 0, now(), now())
                """.formatted(schema), outboxId, organizationId, UUID.randomUUID());

        Flyway.configure()
                .dataSource(upgradeDataSource)
                .schemas(schema)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertThat(latestVersion(upgradeJdbc, schema + ".flyway_schema_history")).isEqualTo("12");
        assertThat(upgradeJdbc.queryForObject(
                "select status from " + schema + ".outbox_events where id=?", String.class, outboxId))
                .isEqualTo("PENDING");
        assertThat(schemaTableExists(upgradeJdbc, schema, "notification_targets")).isTrue();
        assertThat(schemaTableExists(upgradeJdbc, schema, "notifications")).isTrue();
    }

    @Test
    void notificationRelationalGuardsProtectDedupeAndDeliveryState() {
        UUID organizationId = UUID.randomUUID();
        UUID recipientUserId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        jdbc.update("insert into organizations(id, code, name) values (?, ?, ?)",
                organizationId, "slice8-" + compact(organizationId), "Slice 8 test");
        jdbc.update("insert into users(id, display_name) values (?, 'slice8 recipient')", recipientUserId);
        jdbc.update("""
                insert into notification_targets(
                    id, organization_id, channel, target_type, target_code, external_target_id)
                values (?, ?, 'LINE', 'GROUP', 'COACH_REMINDER_GROUP', 'line-group-secret')
                """, targetId, organizationId);

        UUID notificationId = UUID.randomUUID();
        jdbc.update("""
                insert into notifications(
                    id, organization_id, notification_target_id, recipient_user_id,
                    channel, template_code, business_type, business_id, payload,
                    dedupe_key)
                values (?, ?, ?, ?, 'LINE', 'COACH_DAILY_REMINDER', 'COURSE_SESSION', ?, ?::jsonb, ?)
                """, notificationId, organizationId, targetId, recipientUserId,
                UUID.randomUUID(), "{\"date\":\"2030-08-01\"}", "daily-reminder-2030-08-01");

        Map<String, Object> row = jdbc.queryForMap(
                "select status, attempt_count from notifications where id=?", notificationId);
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(((Number) row.get("attempt_count")).intValue()).isZero();

        assertThat(catchThrowable(() -> jdbc.update("""
                insert into notifications(
                    id, organization_id, channel, template_code, business_type,
                    business_id, payload, dedupe_key)
                values (?, ?, 'LINE', 'COACH_DAILY_REMINDER', 'COURSE_SESSION', ?, '{}'::jsonb, ?)
                """, UUID.randomUUID(), organizationId, UUID.randomUUID(), "daily-reminder-2030-08-01")))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(catchThrowable(() -> jdbc.update(
                "update notifications set status='SENT', updated_at=now() where id=?", notificationId)))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("update notifications set status='SENT', sent_at=now(), updated_at=now() where id=?",
                notificationId);
        assertThat(jdbc.queryForObject("select status from notifications where id=?", String.class, notificationId))
                .isEqualTo("SENT");

        assertThat(catchThrowable(() -> jdbc.update("""
                insert into notification_targets(
                    id, organization_id, channel, target_type, target_code, external_target_id)
                values (?, ?, 'EMAIL', 'GROUP', 'INVALID_CHANNEL', 'external')
                """, UUID.randomUUID(), organizationId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private boolean tableExists(String tableName) {
        return schemaTableExists(jdbc, currentSchema(jdbc), tableName);
    }

    private static boolean schemaTableExists(JdbcTemplate template, String schema, String tableName) {
        return Boolean.TRUE.equals(template.queryForObject("""
                select exists(select 1 from information_schema.tables
                    where table_schema=? and table_name=?)
                """, Boolean.class, schema, tableName));
    }

    private static String currentSchema(JdbcTemplate template) {
        return template.queryForObject("select current_schema()", String.class);
    }

    private static String latestVersion(JdbcTemplate template, String historyTable) {
        return template.queryForObject(
                "select version from " + historyTable + " where success=true order by installed_rank desc limit 1",
                String.class);
    }

    private static String compact(UUID id) {
        return id.toString().replace("-", "").substring(0, 12);
    }
}
