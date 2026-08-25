package com.pickleball.booking.course.infrastructure;

import com.pickleball.booking.course.domain.CourseSession;
import com.pickleball.booking.course.domain.CourseSessionRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CourseSessionPersistenceAdapter implements CourseSessionRepository {
    private final JdbcTemplate jdbc;

    public CourseSessionPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CourseSession> findById(UUID courseSessionId) {
        List<CourseSession> rows = jdbc.query("""
                select id, organization_id, course_id, sequence_no,
                       scheduled_start_at, scheduled_end_at,
                       expected_participant_count, guest_participant_count,
                       actual_participant_count, status, cancellation_source,
                       cancellation_note, completed_at, version
                  from course_sessions
                 where id = ?
                 for update
                """, this::map, courseSessionId);
        return rows.stream().findFirst();
    }

    @Override
    public CourseSession save(CourseSession session) {
        int updated = jdbc.update("""
                update course_sessions
                   set scheduled_start_at = ?, scheduled_end_at = ?,
                       actual_participant_count = ?, status = ?,
                       cancellation_source = ?, cancellation_note = ?, completed_at = ?,
                       updated_at = now(), version = version + 1
                 where id = ? and version = ?
                """,
                Timestamp.from(session.scheduledStartAt()), Timestamp.from(session.scheduledEndAt()),
                session.actualParticipantCount(), session.status().name(),
                session.cancellationSource() == null ? null : session.cancellationSource().name(),
                session.cancellationNote(), timestamp(session.completedAt()), session.id(), session.version());
        if (updated != 1) {
            throw new OptimisticLockingFailureException("CourseSession was concurrently modified: " + session.id());
        }
        return CourseSession.rehydrate(
                session.id(), session.organizationId(), session.courseId(), session.sequenceNo(),
                session.scheduledStartAt(), session.scheduledEndAt(),
                session.expectedParticipantCount(), session.guestParticipantCount(),
                session.actualParticipantCount(), session.status(), session.cancellationSource(),
                session.cancellationNote(), session.completedAt(), session.version() + 1);
    }

    private CourseSession map(ResultSet rs, int rowNum) throws SQLException {
        Integer actualParticipantCount = rs.getObject("actual_participant_count", Integer.class);
        String cancellationSource = rs.getString("cancellation_source");
        return CourseSession.rehydrate(
                rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getObject("course_id", UUID.class),
                rs.getInt("sequence_no"),
                instant(rs, "scheduled_start_at"),
                instant(rs, "scheduled_end_at"),
                rs.getInt("expected_participant_count"),
                rs.getInt("guest_participant_count"),
                actualParticipantCount,
                CourseSession.Status.valueOf(rs.getString("status")),
                cancellationSource == null ? null : CourseSession.CancellationSource.valueOf(cancellationSource),
                rs.getString("cancellation_note"),
                nullableInstant(rs, "completed_at"),
                rs.getLong("version"));
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toInstant();
    }

    private static Instant nullableInstant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
