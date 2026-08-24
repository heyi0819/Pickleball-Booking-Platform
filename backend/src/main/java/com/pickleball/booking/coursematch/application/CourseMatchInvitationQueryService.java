package com.pickleball.booking.coursematch.application;

import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.identity.application.IdentityService;
import com.pickleball.booking.identity.domain.RoleCode;
import com.pickleball.booking.shared.application.BusinessException;
import jakarta.transaction.Transactional;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CourseMatchInvitationQueryService {
    private final IdentityService identity;
    private final JdbcTemplate jdbc;

    public CourseMatchInvitationQueryService(IdentityService identity, JdbcTemplate jdbc) {
        this.identity = identity;
        this.jdbc = jdbc;
    }

    @Transactional
    public List<InvitationSummary> mine(AuthenticatedPrincipal actor) {
        identity.requireActiveUser(actor.userId());
        boolean coachRole = identity.roles(actor).stream()
                .anyMatch(role -> role.roleCode() == RoleCode.COACH);
        if (!coachRole) {
            throw new BusinessException("AUTH_FORBIDDEN", "Coach role is required");
        }
        return jdbc.query("""
                select c.id as invitation_id,
                       s.course_match_id,
                       s.id as course_match_session_id,
                       s.session_index,
                       s.scheduled_start_at,
                       s.scheduled_end_at,
                       s.venue_snapshot_name,
                       c.coach_profile_id,
                       c.status,
                       c.invitation_sent_at,
                       c.responded_at,
                       c.response_note
                from course_match_session_coaches c
                join course_match_sessions s on s.id = c.course_match_session_id
                join coach_profiles p on p.id = c.coach_profile_id
                where p.user_id = ?
                  and c.status in ('INVITED','ACCEPTED','REJECTED')
                order by s.scheduled_start_at asc, c.invitation_sent_at desc
                """, (rs, rowNum) -> new InvitationSummary(
                rs.getObject("invitation_id", UUID.class),
                rs.getObject("course_match_id", UUID.class),
                rs.getObject("course_match_session_id", UUID.class),
                rs.getShort("session_index"),
                instant(rs.getTimestamp("scheduled_start_at")),
                instant(rs.getTimestamp("scheduled_end_at")),
                rs.getString("venue_snapshot_name"),
                rs.getObject("coach_profile_id", UUID.class),
                rs.getString("status"),
                instant(rs.getTimestamp("invitation_sent_at")),
                instant(rs.getTimestamp("responded_at")),
                rs.getString("response_note")),
                actor.userId());
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record InvitationSummary(
            UUID invitationId,
            UUID courseMatchId,
            UUID courseMatchSessionId,
            short sessionIndex,
            Instant startAt,
            Instant endAt,
            String venueName,
            UUID coachProfileId,
            String status,
            Instant invitationSentAt,
            Instant respondedAt,
            String responseNote) {}
}
