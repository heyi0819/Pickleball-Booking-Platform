package com.pickleball.booking.course.infrastructure;

import com.pickleball.booking.course.application.CourseOperationsAccessPort;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PostgresCourseOperationsAccessAdapter implements CourseOperationsAccessPort {
    private final JdbcTemplate jdbc;

    public PostgresCourseOperationsAccessAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isAssignedCoach(UUID organizationId, UUID courseSessionId, UUID userId) {
        Integer count = jdbc.queryForObject("""
                select count(*)
                  from session_coach_assignments sca
                  join coach_profiles cp on cp.id = sca.coach_profile_id
                 where sca.organization_id = ?
                   and sca.course_session_id = ?
                   and cp.user_id = ?
                   and sca.status in ('ACCEPTED','CANCEL_PENDING')
                """, Integer.class, organizationId, courseSessionId, userId);
        return count != null && count > 0;
    }

    @Override
    public boolean isScheduledParticipant(UUID organizationId, UUID courseSessionId, UUID userId) {
        Integer count = jdbc.queryForObject("""
                select count(*)
                  from enrollments
                 where organization_id = ?
                   and course_session_id = ?
                   and user_id = ?
                   and status = 'SCHEDULED'
                """, Integer.class, organizationId, courseSessionId, userId);
        return count != null && count > 0;
    }
}
