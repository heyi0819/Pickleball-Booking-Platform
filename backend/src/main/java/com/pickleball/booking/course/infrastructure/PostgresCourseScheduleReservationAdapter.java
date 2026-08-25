package com.pickleball.booking.course.infrastructure;

import com.pickleball.booking.course.application.CourseScheduleReservationPort;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PostgresCourseScheduleReservationAdapter implements CourseScheduleReservationPort {
    private final JdbcTemplate jdbc;

    public PostgresCourseScheduleReservationAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int shiftActiveReservations(UUID courseSessionId, Instant newStartAt, Instant newEndAt) {
        return jdbc.update("""
                update schedule_reservations
                   set reserved_period = tstzrange(?::timestamptz, ?::timestamptz, '[)'),
                       updated_at = now(), version = version + 1
                 where course_session_id = ?
                   and status in ('HELD','CONFIRMED')
                """, Timestamp.from(newStartAt), Timestamp.from(newEndAt), courseSessionId);
    }

    @Override
    public int releaseParticipantReservation(UUID courseSessionId, UUID userId, String reason) {
        return jdbc.update("""
                update schedule_reservations
                   set status = 'RELEASED', released_at = now(), release_reason = ?,
                       updated_at = now(), version = version + 1
                 where course_session_id = ? and user_id = ?
                   and reservation_role = 'PARTICIPANT'
                   and status in ('HELD','CONFIRMED')
                """, reason, courseSessionId, userId);
    }

    @Override
    public int releaseAllActiveReservations(UUID courseSessionId, String reason) {
        return jdbc.update("""
                update schedule_reservations
                   set status = 'RELEASED', released_at = now(), release_reason = ?,
                       updated_at = now(), version = version + 1
                 where course_session_id = ?
                   and status in ('HELD','CONFIRMED')
                """, reason, courseSessionId);
    }
}
