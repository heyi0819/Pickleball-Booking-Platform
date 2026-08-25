package com.pickleball.booking.course.infrastructure;

import com.pickleball.booking.course.domain.SessionChangeRequest;
import com.pickleball.booking.course.domain.SessionChangeRequestRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SessionChangeRequestPersistenceAdapter implements SessionChangeRequestRepository {
    private final JdbcTemplate jdbc;

    public SessionChangeRequestPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<SessionChangeRequest> findById(UUID requestId) {
        List<SessionChangeRequest> rows = jdbc.query("""
                select id, organization_id, course_session_id, request_type, requested_by, reason,
                       proposed_start_at, proposed_end_at, proposed_coach_profile_id, proposed_venue_id,
                       status, decided_by, decided_at, decision_reason, created_at
                  from session_change_requests
                 where id = ?
                 for update
                """, this::map, requestId);
        return rows.stream().findFirst();
    }

    @Override
    public SessionChangeRequest save(SessionChangeRequest request) {
        jdbc.update("""
                insert into session_change_requests(
                    id, organization_id, course_session_id, request_type, requested_by, reason,
                    proposed_start_at, proposed_end_at, proposed_coach_profile_id, proposed_venue_id,
                    status, decided_by, decided_at, decision_reason, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (id) do update set
                    status = excluded.status,
                    decided_by = excluded.decided_by,
                    decided_at = excluded.decided_at,
                    decision_reason = excluded.decision_reason
                """,
                request.id(), request.organizationId(), request.courseSessionId(), request.type().name(),
                request.requestedBy(), request.reason(), request.proposedStartAt(), request.proposedEndAt(),
                request.proposedCoachProfileId(), request.proposedVenueId(), request.status().name(),
                request.decidedBy(), request.decidedAt(), request.decisionReason(), request.createdAt());
        return request;
    }

    private SessionChangeRequest map(ResultSet rs, int rowNum) throws SQLException {
        return SessionChangeRequest.rehydrate(
                rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getObject("course_session_id", UUID.class),
                SessionChangeRequest.Type.valueOf(rs.getString("request_type")),
                rs.getObject("requested_by", UUID.class),
                rs.getString("reason"),
                nullableInstant(rs, "proposed_start_at"),
                nullableInstant(rs, "proposed_end_at"),
                rs.getObject("proposed_coach_profile_id", UUID.class),
                rs.getObject("proposed_venue_id", UUID.class),
                SessionChangeRequest.Status.valueOf(rs.getString("status")),
                rs.getObject("decided_by", UUID.class),
                nullableInstant(rs, "decided_at"),
                rs.getString("decision_reason"),
                rs.getTimestamp("created_at").toInstant());
    }

    private static Instant nullableInstant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
