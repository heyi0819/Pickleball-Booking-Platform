package com.pickleball.booking.course.application;

import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.identity.application.IdentityService;
import com.pickleball.booking.identity.domain.RoleCode;
import com.pickleball.booking.shared.application.BusinessException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseOperationsQueryService {
    private final NamedParameterJdbcTemplate jdbc;
    private final IdentityService identity;

    public CourseOperationsQueryService(NamedParameterJdbcTemplate jdbc, IdentityService identity) {
        this.jdbc = jdbc;
        this.identity = identity;
    }

    @Transactional(readOnly = true)
    public PageResult<CourseSummary> list(AuthenticatedPrincipal actor, CourseFilter filter) {
        requireActor(actor);
        CourseFilter normalized = normalize(filter);
        Scope scope = scope(actor);
        var params = new MapSqlParameterSource()
                .addValue("actorUserId", actor.userId())
                .addValue("limit", normalized.size())
                .addValue("offset", normalized.page() * normalized.size());
        String where = buildCourseWhere(scope, normalized, params);
        String orderBy = orderBy(normalized.sort());

        String sql = """
                select c.id, c.organization_id, c.course_no, c.course_type, c.schedule_type,
                       c.billing_mode, c.skill_level, c.expected_participant_count,
                       c.minimum_participants, c.maximum_participants, c.total_session_count,
                       c.status,
                       (select min(cs.scheduled_start_at)
                          from course_sessions cs
                         where cs.course_id=c.id and cs.status in ('SCHEDULED','POSTPONED')) as next_session_start_at,
                       (select count(*) from course_memberships cm
                         where cm.course_id=c.id and cm.status='ACTIVE') as active_membership_count
                  from courses c
                 where %s
                 order by %s
                 limit :limit offset :offset
                """.formatted(where, orderBy);

        List<CourseSummary> items = jdbc.query(sql, params, COURSE_SUMMARY_MAPPER);
        Long total = jdbc.queryForObject(
                "select count(*) from courses c where " + where,
                params,
                Long.class);
        return new PageResult<>(items, normalized.page(), normalized.size(), total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public CourseDetail detail(AuthenticatedPrincipal actor, UUID courseId) {
        requireActor(actor);
        Scope scope = scope(actor);
        var params = new MapSqlParameterSource()
                .addValue("actorUserId", actor.userId())
                .addValue("courseId", courseId);
        String where = buildCourseWhere(scope, CourseFilter.empty(), params) + " and c.id=:courseId";
        String sql = """
                select c.id, c.organization_id, c.course_no, c.source_match_id,
                       c.source_offering_id, c.created_by_user_id, c.course_type,
                       c.schedule_type, c.billing_mode, c.skill_level,
                       c.expected_participant_count, c.guest_participant_count,
                       c.minimum_participants, c.maximum_participants, c.total_session_count,
                       c.status, c.activated_at, c.completed_at, c.cancelled_at,
                       c.created_at, c.updated_at,
                       (select min(cs.scheduled_start_at)
                          from course_sessions cs
                         where cs.course_id=c.id and cs.status in ('SCHEDULED','POSTPONED')) as next_session_start_at,
                       (select count(*) from course_memberships cm
                         where cm.course_id=c.id and cm.status='ACTIVE') as active_membership_count
                  from courses c
                 where %s
                """.formatted(where);
        List<CourseDetail> rows = jdbc.query(sql, params, COURSE_DETAIL_MAPPER);
        if (rows.isEmpty()) {
            throw new BusinessException("RESOURCE_NOT_FOUND", "Course was not found");
        }
        return rows.getFirst();
    }

    @Transactional(readOnly = true)
    public List<SessionSummary> sessions(AuthenticatedPrincipal actor, UUID courseId) {
        requireActor(actor);
        Scope scope = scope(actor);
        var params = new MapSqlParameterSource()
                .addValue("actorUserId", actor.userId())
                .addValue("courseId", courseId);
        String visibility = buildCourseWhere(scope, CourseFilter.empty(), params);
        if (jdbc.queryForObject(
                "select count(*) from courses c where " + visibility + " and c.id=:courseId",
                params, Long.class) == 0L) {
            throw new BusinessException("RESOURCE_NOT_FOUND", "Course was not found");
        }
        return jdbc.query(sessionSelect() + " where s.course_id=:courseId order by s.sequence_no", params, SESSION_MAPPER);
    }

    @Transactional(readOnly = true)
    public SessionSummary session(AuthenticatedPrincipal actor, UUID sessionId) {
        requireActor(actor);
        Scope scope = scope(actor);
        var params = new MapSqlParameterSource()
                .addValue("actorUserId", actor.userId())
                .addValue("sessionId", sessionId);
        String visibility = buildCourseWhere(scope, CourseFilter.empty(), params);
        String sql = sessionSelect()
                + " join courses visible_course on visible_course.id=s.course_id"
                + " where s.id=:sessionId and exists (select 1 from courses c where c.id=s.course_id and "
                + visibility + ")";
        List<SessionSummary> rows = jdbc.query(sql, params, SESSION_MAPPER);
        if (rows.isEmpty()) {
            throw new BusinessException("RESOURCE_NOT_FOUND", "Course session was not found");
        }
        return rows.getFirst();
    }

    private String sessionSelect() {
        return """
                select s.id, s.organization_id, s.course_id, s.sequence_no,
                       s.scheduled_start_at, s.scheduled_end_at,
                       s.expected_participant_count, s.guest_participant_count,
                       s.actual_participant_count, s.status, s.cancellation_source,
                       s.cancellation_note, s.completed_at,
                       venue.venue_id, venue.venue_name_snapshot, venue.address_snapshot,
                       venue.status as venue_status,
                       coach.coach_profile_id, coach.coach_display_name,
                       own_enrollment.id as own_enrollment_id,
                       own_enrollment.status as own_enrollment_status
                  from course_sessions s
                  left join lateral (
                        select sva.venue_id, sva.venue_name_snapshot, sva.address_snapshot, sva.status
                          from session_venue_arrangements sva
                         where sva.course_session_id=s.id and sva.status='CONFIRMED'
                         order by sva.confirmed_at desc nulls last, sva.created_at desc
                         limit 1
                  ) venue on true
                  left join lateral (
                        select cp.id as coach_profile_id, u.display_name as coach_display_name
                          from session_coach_assignments sca
                          join coach_profiles cp on cp.id=sca.coach_profile_id
                          join users u on u.id=cp.user_id
                         where sca.course_session_id=s.id
                           and sca.is_primary=true
                           and sca.status in ('ACCEPTED','CANCEL_PENDING')
                         order by sca.updated_at desc
                         limit 1
                  ) coach on true
                  left join enrollments own_enrollment
                    on own_enrollment.course_session_id=s.id
                   and own_enrollment.user_id=:actorUserId
                """;
    }

    private String buildCourseWhere(Scope scope, CourseFilter filter, MapSqlParameterSource params) {
        List<String> visibility = new ArrayList<>();
        if (scope.platformAdmin()) {
            visibility.add("1=1");
        } else {
            if (!scope.committeeOrganizations().isEmpty()) {
                params.addValue("committeeOrgIds", scope.committeeOrganizations());
                visibility.add("c.organization_id in (:committeeOrgIds)");
            }
            if (!scope.coachOrganizations().isEmpty()) {
                params.addValue("coachOrgIds", scope.coachOrganizations());
                visibility.add("""
                        (c.organization_id in (:coachOrgIds) and exists (
                            select 1
                              from course_sessions cs
                              join session_coach_assignments sca on sca.course_session_id=cs.id
                              join coach_profiles cp on cp.id=sca.coach_profile_id
                             where cs.course_id=c.id and cp.user_id=:actorUserId
                               and sca.status in ('ACCEPTED','CANCEL_PENDING')
                        ))
                        """);
            }
            if (!scope.studentOrganizations().isEmpty()) {
                params.addValue("studentOrgIds", scope.studentOrganizations());
                visibility.add("""
                        (c.organization_id in (:studentOrgIds) and exists (
                            select 1 from course_memberships cm
                             where cm.course_id=c.id and cm.user_id=:actorUserId
                        ))
                        """);
            }
        }
        if (visibility.isEmpty()) {
            throw new BusinessException("AUTH_FORBIDDEN", "No active Course Operations role is available");
        }

        List<String> predicates = new ArrayList<>();
        predicates.add("(" + String.join(" or ", visibility) + ")");
        if (filter.organizationId() != null) {
            params.addValue("organizationId", filter.organizationId());
            predicates.add("c.organization_id=:organizationId");
        }
        if (filter.status() != null) {
            params.addValue("courseStatus", canonical(filter.status(), List.of("DRAFT", "ACTIVE", "COMPLETED", "CANCELLED")));
            predicates.add("c.status=:courseStatus");
        }
        if (filter.courseType() != null) {
            params.addValue("courseType", canonical(filter.courseType(), List.of("PRIVATE", "GROUP")));
            predicates.add("c.course_type=:courseType");
        }
        if (filter.coachProfileId() != null) {
            params.addValue("coachProfileId", filter.coachProfileId());
            predicates.add("""
                    exists (select 1 from course_sessions cs
                            join session_coach_assignments sca on sca.course_session_id=cs.id
                            where cs.course_id=c.id and sca.coach_profile_id=:coachProfileId
                              and sca.status in ('ACCEPTED','CANCEL_PENDING'))
                    """);
        }
        if (filter.studentUserId() != null) {
            params.addValue("studentUserId", filter.studentUserId());
            predicates.add("exists (select 1 from course_memberships cm where cm.course_id=c.id and cm.user_id=:studentUserId)");
        }
        if (filter.from() != null) {
            params.addValue("fromAt", Timestamp.from(filter.from()));
            predicates.add("exists (select 1 from course_sessions cs where cs.course_id=c.id and cs.scheduled_end_at > :fromAt)");
        }
        if (filter.to() != null) {
            params.addValue("toAt", Timestamp.from(filter.to()));
            predicates.add("exists (select 1 from course_sessions cs where cs.course_id=c.id and cs.scheduled_start_at < :toAt)");
        }
        return String.join(" and ", predicates);
    }

    private Scope scope(AuthenticatedPrincipal actor) {
        List<IdentityService.RoleView> roles = identity.roles(actor);
        boolean admin = roles.stream().anyMatch(r -> r.roleCode() == RoleCode.PLATFORM_ADMIN);
        return new Scope(
                admin,
                organizations(roles, RoleCode.COMMITTEE),
                organizations(roles, RoleCode.COACH),
                organizations(roles, RoleCode.STUDENT));
    }

    private List<UUID> organizations(List<IdentityService.RoleView> roles, RoleCode role) {
        return roles.stream()
                .filter(r -> r.roleCode() == role && r.organizationId() != null)
                .map(IdentityService.RoleView::organizationId)
                .distinct()
                .toList();
    }

    private CourseFilter normalize(CourseFilter filter) {
        CourseFilter value = filter == null ? CourseFilter.empty() : filter;
        if (value.page() < 0 || value.size() < 1 || value.size() > 100) {
            throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and 100");
        }
        if (value.from() != null && value.to() != null && !value.from().isBefore(value.to())) {
            throw new IllegalArgumentException("from must be before to");
        }
        return value;
    }

    private String orderBy(String sort) {
        if (sort == null || sort.isBlank() || sort.equals("createdAt,desc")) return "c.created_at desc, c.id";
        return switch (sort) {
            case "createdAt,asc" -> "c.created_at asc, c.id";
            case "courseNo,asc" -> "c.course_no asc, c.id";
            case "courseNo,desc" -> "c.course_no desc, c.id";
            case "nextSessionAt,asc" -> "next_session_start_at asc nulls last, c.id";
            case "nextSessionAt,desc" -> "next_session_start_at desc nulls last, c.id";
            default -> throw new IllegalArgumentException("Unsupported sort");
        };
    }

    private String canonical(String value, List<String> allowed) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new IllegalArgumentException("Unsupported filter value");
        return normalized;
    }

    private void requireActor(AuthenticatedPrincipal actor) {
        if (actor == null || actor.userId() == null) throw new BusinessException("AUTH_FORBIDDEN", "Authenticated user is required");
        identity.requireActiveUser(actor.userId());
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private static Integer integer(ResultSet rs, String column) throws SQLException {
        Number value = (Number) rs.getObject(column);
        return value == null ? null : value.intValue();
    }

    private static final RowMapper<CourseSummary> COURSE_SUMMARY_MAPPER = (rs, rowNum) -> new CourseSummary(
            uuid(rs, "id"), uuid(rs, "organization_id"), rs.getString("course_no"),
            rs.getString("course_type"), rs.getString("schedule_type"), rs.getString("billing_mode"),
            rs.getString("skill_level"), rs.getInt("expected_participant_count"),
            integer(rs, "minimum_participants"), integer(rs, "maximum_participants"),
            rs.getInt("total_session_count"), rs.getString("status"), instant(rs, "next_session_start_at"),
            rs.getInt("active_membership_count"));

    private static final RowMapper<CourseDetail> COURSE_DETAIL_MAPPER = (rs, rowNum) -> new CourseDetail(
            uuid(rs, "id"), uuid(rs, "organization_id"), rs.getString("course_no"),
            uuid(rs, "source_match_id"), uuid(rs, "source_offering_id"), uuid(rs, "created_by_user_id"),
            rs.getString("course_type"), rs.getString("schedule_type"), rs.getString("billing_mode"),
            rs.getString("skill_level"), rs.getInt("expected_participant_count"),
            rs.getInt("guest_participant_count"), integer(rs, "minimum_participants"),
            integer(rs, "maximum_participants"), rs.getInt("total_session_count"), rs.getString("status"),
            instant(rs, "activated_at"), instant(rs, "completed_at"), instant(rs, "cancelled_at"),
            instant(rs, "created_at"), instant(rs, "updated_at"), instant(rs, "next_session_start_at"),
            rs.getInt("active_membership_count"));

    private static final RowMapper<SessionSummary> SESSION_MAPPER = (rs, rowNum) -> new SessionSummary(
            uuid(rs, "id"), uuid(rs, "organization_id"), uuid(rs, "course_id"), rs.getInt("sequence_no"),
            instant(rs, "scheduled_start_at"), instant(rs, "scheduled_end_at"),
            rs.getInt("expected_participant_count"), rs.getInt("guest_participant_count"),
            integer(rs, "actual_participant_count"), rs.getString("status"),
            rs.getString("cancellation_source"), rs.getString("cancellation_note"), instant(rs, "completed_at"),
            uuid(rs, "venue_id"), rs.getString("venue_name_snapshot"), rs.getString("address_snapshot"),
            rs.getString("venue_status"), uuid(rs, "coach_profile_id"), rs.getString("coach_display_name"),
            uuid(rs, "own_enrollment_id"), rs.getString("own_enrollment_status"));

    public record CourseFilter(
            UUID organizationId,
            String status,
            Instant from,
            Instant to,
            UUID coachProfileId,
            UUID studentUserId,
            String courseType,
            int page,
            int size,
            String sort) {
        public static CourseFilter empty() {
            return new CourseFilter(null, null, null, null, null, null, null, 0, 20, null);
        }
    }

    public record PageResult<T>(List<T> items, int page, int size, long total) { }

    public record CourseSummary(
            UUID id,
            UUID organizationId,
            String courseNo,
            String courseType,
            String scheduleType,
            String billingMode,
            String skillLevel,
            int expectedParticipantCount,
            Integer minimumParticipants,
            Integer maximumParticipants,
            int totalSessionCount,
            String status,
            Instant nextSessionStartAt,
            int activeMembershipCount) { }

    public record CourseDetail(
            UUID id,
            UUID organizationId,
            String courseNo,
            UUID sourceMatchId,
            UUID sourceOfferingId,
            UUID createdByUserId,
            String courseType,
            String scheduleType,
            String billingMode,
            String skillLevel,
            int expectedParticipantCount,
            int guestParticipantCount,
            Integer minimumParticipants,
            Integer maximumParticipants,
            int totalSessionCount,
            String status,
            Instant activatedAt,
            Instant completedAt,
            Instant cancelledAt,
            Instant createdAt,
            Instant updatedAt,
            Instant nextSessionStartAt,
            int activeMembershipCount) { }

    public record SessionSummary(
            UUID id,
            UUID organizationId,
            UUID courseId,
            int sequenceNo,
            Instant scheduledStartAt,
            Instant scheduledEndAt,
            int expectedParticipantCount,
            int guestParticipantCount,
            Integer actualParticipantCount,
            String status,
            String cancellationSource,
            String cancellationNote,
            Instant completedAt,
            UUID venueId,
            String venueName,
            String venueAddress,
            String venueStatus,
            UUID coachProfileId,
            String coachDisplayName,
            UUID ownEnrollmentId,
            String ownEnrollmentStatus) { }

    private record Scope(
            boolean platformAdmin,
            List<UUID> committeeOrganizations,
            List<UUID> coachOrganizations,
            List<UUID> studentOrganizations) { }
}
