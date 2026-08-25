package com.pickleball.booking.course.infrastructure;

import com.pickleball.booking.course.domain.CourseCancellationRequest;
import com.pickleball.booking.course.domain.CourseCancellationRequestRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CourseCancellationRequestPersistenceAdapter implements CourseCancellationRequestRepository {
    private final JdbcTemplate jdbc;

    public CourseCancellationRequestPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CourseCancellationRequest> findById(UUID requestId) {
        List<CourseCancellationRequest> rows = jdbc.query("""
                select id, organization_id, course_session_id, requested_by, requester_role,
                       reason, status, reviewed_by, reviewed_at, review_note, created_at
                  from course_cancellation_requests
                 where id = ?
                 for update
                """, this::map, requestId);
        return rows.stream().findFirst();
    }

    @Override
    public CourseCancellationRequest save(CourseCancellationRequest request) {
        jdbc.update("""
                insert into course_cancellation_requests(
                    id, organization_id, course_session_id, requested_by, requester_role,
                    reason, status, reviewed_by, reviewed_at, review_note, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (id) do update set
                    status = excluded.status,
                    reviewed_by = excluded.reviewed_by,
                    reviewed_at = excluded.reviewed_at,
                    review_note = excluded.review_note
                """,
                request.id(), request.organizationId(), request.courseSessionId(), request.requestedBy(),
                request.requesterRole().name(), request.reason(), request.status().name(),
                request.reviewedBy(), timestamp(request.reviewedAt()), request.reviewNote(),
                Timestamp.from(request.createdAt()));
        return request;
    }

    private CourseCancellationRequest map(ResultSet rs, int rowNum) throws SQLException {
        return CourseCancellationRequest.rehydrate(
                rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getObject("course_session_id", UUID.class),
                rs.getObject("requested_by", UUID.class),
                CourseCancellationRequest.RequesterRole.valueOf(rs.getString("requester_role")),
                rs.getString("reason"),
                CourseCancellationRequest.Status.valueOf(rs.getString("status")),
                rs.getObject("reviewed_by", UUID.class),
                nullableInstant(rs, "reviewed_at"),
                rs.getString("review_note"),
                rs.getTimestamp("created_at").toInstant());
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant nullableInstant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
