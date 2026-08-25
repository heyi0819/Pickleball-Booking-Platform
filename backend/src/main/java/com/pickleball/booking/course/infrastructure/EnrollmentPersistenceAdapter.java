package com.pickleball.booking.course.infrastructure;

import com.pickleball.booking.course.domain.Enrollment;
import com.pickleball.booking.course.domain.EnrollmentRepository;
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
public class EnrollmentPersistenceAdapter implements EnrollmentRepository {
    private final JdbcTemplate jdbc;

    public EnrollmentPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Enrollment> findById(UUID enrollmentId) {
        List<Enrollment> rows = jdbc.query("""
                select id, organization_id, course_membership_id, course_session_id,
                       user_id, status, enrolled_at, cancelled_at, attendance_marked_at, version
                  from enrollments
                 where id = ?
                 for update
                """, this::map, enrollmentId);
        return rows.stream().findFirst();
    }

    @Override
    public Enrollment save(Enrollment enrollment) {
        int updated = jdbc.update("""
                update enrollments
                   set status = ?, cancelled_at = ?, attendance_marked_at = ?,
                       updated_at = now(), version = version + 1
                 where id = ? and version = ?
                """,
                enrollment.status().name(), timestamp(enrollment.cancelledAt()),
                timestamp(enrollment.attendanceMarkedAt()), enrollment.id(), enrollment.version());
        if (updated != 1) {
            throw new OptimisticLockingFailureException("Enrollment was concurrently modified: " + enrollment.id());
        }
        return Enrollment.rehydrate(
                enrollment.id(), enrollment.organizationId(), enrollment.courseMembershipId(),
                enrollment.courseSessionId(), enrollment.userId(), enrollment.status(),
                enrollment.enrolledAt(), enrollment.cancelledAt(), enrollment.attendanceMarkedAt(),
                enrollment.version() + 1);
    }

    private Enrollment map(ResultSet rs, int rowNum) throws SQLException {
        return Enrollment.rehydrate(
                rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getObject("course_membership_id", UUID.class),
                rs.getObject("course_session_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                Enrollment.Status.valueOf(rs.getString("status")),
                instant(rs, "enrolled_at"),
                nullableInstant(rs, "cancelled_at"),
                nullableInstant(rs, "attendance_marked_at"),
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
