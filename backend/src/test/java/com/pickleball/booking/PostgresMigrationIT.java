package com.pickleball.booking;

import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PostgresMigrationIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("security.jwt.signing-secret", () -> "test-only-signing-secret-with-at-least-thirty-two-characters");
    }

    @Test
    void appliesTheBaselineMigrationToAnEmptyPostgresDatabase() throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'btree_gist')")) {
            result.next();
            assertThat(result.getBoolean(1)).isTrue();
        }
    }

    @Test
    void sliceTwoConstraintsAllowDraftSharingButProtectTheActiveClaimAndVenueForeignKeys() {
        UUID organization = UUID.randomUUID(); UUID coachUser = UUID.randomUUID(); UUID studentOne = UUID.randomUUID(); UUID studentTwo = UUID.randomUUID();
        UUID profile = UUID.randomUUID(); UUID proposal = UUID.randomUUID(); UUID requestOne = UUID.randomUUID(); UUID requestTwo = UUID.randomUUID();
        jdbc.update("insert into organizations(id, code, name) values (?, ?, ?)", organization, "slice2-" + organization, "Slice 2 test");
        for (UUID user : java.util.List.of(coachUser, studentOne, studentTwo)) jdbc.update("insert into users(id, display_name) values (?, ?)", user, "test user");
        jdbc.update("insert into coach_profiles(id, organization_id, user_id, approval_status) values (?, ?, ?, 'APPROVED')", profile, organization, coachUser);
        jdbc.update("insert into coach_availability_proposals(id, organization_id, coach_profile_id, start_at, end_at, status, submitted_at, reviewed_by, reviewed_at, review_note) values (?, ?, ?, now() + interval '2 hours', now() + interval '3 hours', 'APPROVED', now(), ?, now(), 'approved for test')", proposal, organization, profile, coachUser);
        for (UUID request : java.util.List.of(requestOne, requestTwo)) jdbc.update("insert into lesson_requests(id, organization_id, requester_user_id, preferred_coach_profile_id, selected_availability_proposal_id, lesson_type, schedule_type, billing_mode, participant_count, guest_participant_count, requested_session_count, status) values (?, ?, ?, ?, ?, 'PRIVATE', 'SINGLE', 'PER_SESSION', 1, 0, 1, 'DRAFT')", request, organization, request.equals(requestOne) ? studentOne : studentTwo, profile, proposal);
        assertThat(jdbc.queryForObject("select count(*) from lesson_requests where selected_availability_proposal_id = ?", Integer.class, proposal)).isEqualTo(2);
        jdbc.update("insert into coach_availability_claims(id, organization_id, coach_availability_proposal_id, lesson_request_id, status) values (?, ?, ?, ?, 'ACTIVE')", UUID.randomUUID(), organization, proposal, requestOne);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> jdbc.update("insert into coach_availability_claims(id, organization_id, coach_availability_proposal_id, lesson_request_id, status) values (?, ?, ?, ?, 'ACTIVE')", UUID.randomUUID(), organization, proposal, requestTwo))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> jdbc.update("insert into coach_availability_proposals(id, organization_id, coach_profile_id, start_at, end_at, preferred_venue_id, status) values (?, ?, ?, now() + interval '4 hours', now() + interval '5 hours', ?, 'DRAFT')", UUID.randomUUID(), organization, profile, UUID.randomUUID()))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
