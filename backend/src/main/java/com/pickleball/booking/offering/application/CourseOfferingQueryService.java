package com.pickleball.booking.offering.application;

import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.identity.application.IdentityService;
import com.pickleball.booking.identity.domain.RoleAssignmentStatus;
import com.pickleball.booking.identity.domain.RoleCode;
import com.pickleball.booking.identity.infrastructure.RoleAssignmentEntity;
import com.pickleball.booking.identity.infrastructure.RoleAssignmentRepository;
import com.pickleball.booking.offering.domain.CourseOfferingStatus;
import com.pickleball.booking.offering.infrastructure.CourseOfferingPersistenceAdapter;
import com.pickleball.booking.organization.domain.OrganizationStatus;
import com.pickleball.booking.shared.application.BusinessException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseOfferingQueryService {
    private final IdentityService identity;
    private final RoleAssignmentRepository roleAssignments;
    private final CourseOfferingPersistenceAdapter offerings;
    private final JdbcTemplate jdbc;

    public CourseOfferingQueryService(
            IdentityService identity,
            RoleAssignmentRepository roleAssignments,
            CourseOfferingPersistenceAdapter offerings,
            JdbcTemplate jdbc) {
        this.identity = identity;
        this.roleAssignments = roleAssignments;
        this.offerings = offerings;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PageResult<OfferingSummary> list(AuthenticatedPrincipal actor, OfferingFilter filter) {
        ViewerScope scope = viewerScope(actor);
        OfferingFilter normalized = normalize(filter);
        SqlFragment visibility = visibility(scope, actor.userId());
        List<Object> whereParams = new ArrayList<>(visibility.params());
        StringBuilder where = new StringBuilder(" where org.status='ACTIVE' and (")
                .append(visibility.sql()).append(')');
        appendFilters(where, whereParams, normalized);

        String baseJoin = """
                from course_offerings o
                join organizations org on org.id=o.organization_id
                join coach_profiles cp on cp.id=o.coach_profile_id
                join users coach_user on coach_user.id=cp.user_id
                """;
        Long total = jdbc.queryForObject(
                "select count(*) " + baseJoin + where,
                Long.class,
                whereParams.toArray());

        String sql = """
                select o.id, o.organization_id, o.title, o.description, o.status, o.schedule_type,
                       o.billing_mode, o.skill_level, o.minimum_participants, o.maximum_participants,
                       o.registration_open_at, o.registration_close_at, o.version, o.created_at,
                       o.coach_profile_id, cp.user_id as coach_user_id, coach_user.display_name as coach_display_name,
                       fs.first_session_at,
                       coalesce(rc.registered_count,0) as registered_count,
                       ps.id as price_snapshot_id, ps.price_per_participant, trim(ps.currency) as currency,
                       own.id as own_registration_id, own.status as own_registration_status
                """ + baseJoin + """
                left join lateral (
                    select min(s.start_at) as first_session_at
                    from course_offering_sessions s where s.course_offering_id=o.id
                ) fs on true
                left join lateral (
                    select count(*) as registered_count
                    from course_offering_registrations r
                    where r.course_offering_id=o.id and r.status='ACTIVE'
                ) rc on true
                left join course_offering_price_snapshots ps
                    on ps.course_offering_id=o.id and ps.status='CONFIRMED'
                left join lateral (
                    select r.id, r.status
                    from course_offering_registrations r
                    where r.course_offering_id=o.id and r.user_id=?
                    order by r.registered_at desc, r.id desc limit 1
                ) own on true
                """ + where + orderBy(normalized.sort()) + " limit ? offset ?";

        List<Object> dataParams = new ArrayList<>();
        dataParams.add(actor.userId());
        dataParams.addAll(whereParams);
        dataParams.add(normalized.size());
        dataParams.add(normalized.page() * normalized.size());
        List<OfferingSummary> items = jdbc.query(sql, this::summary, dataParams.toArray());
        return new PageResult<>(items, normalized.page(), normalized.size(), total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public OfferingDetail detail(AuthenticatedPrincipal actor, UUID offeringId) {
        ViewerScope scope = viewerScope(actor);
        SqlFragment visibility = visibility(scope, actor.userId());
        List<Object> params = new ArrayList<>();
        params.add(actor.userId());
        params.addAll(visibility.params());
        params.add(offeringId);
        String sql = """
                select o.id, o.organization_id, o.title, o.description, o.status, o.schedule_type,
                       o.billing_mode, o.skill_level, o.minimum_participants, o.maximum_participants,
                       o.registration_open_at, o.registration_close_at, o.version, o.created_at,
                       o.coach_profile_id, cp.user_id as coach_user_id, coach_user.display_name as coach_display_name,
                       fs.first_session_at,
                       coalesce(rc.registered_count,0) as registered_count,
                       ps.id as price_snapshot_id, ps.price_per_participant, trim(ps.currency) as currency,
                       own.id as own_registration_id, own.status as own_registration_status
                from course_offerings o
                join organizations org on org.id=o.organization_id
                join coach_profiles cp on cp.id=o.coach_profile_id
                join users coach_user on coach_user.id=cp.user_id
                left join lateral (
                    select min(s.start_at) as first_session_at
                    from course_offering_sessions s where s.course_offering_id=o.id
                ) fs on true
                left join lateral (
                    select count(*) as registered_count
                    from course_offering_registrations r
                    where r.course_offering_id=o.id and r.status='ACTIVE'
                ) rc on true
                left join course_offering_price_snapshots ps
                    on ps.course_offering_id=o.id and ps.status='CONFIRMED'
                left join lateral (
                    select r.id, r.status
                    from course_offering_registrations r
                    where r.course_offering_id=o.id and r.user_id=?
                    order by r.registered_at desc, r.id desc limit 1
                ) own on true
                where org.status='ACTIVE' and (
                """ + visibility.sql() + ") and o.id=?";
        List<OfferingRow> rows = jdbc.query(sql, this::row, params.toArray());
        OfferingRow offering = rows.stream().findFirst()
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Course offering was not found"));
        List<SessionPlanView> sessions = jdbc.query("""
                select id, sequence_no, start_at, end_at, venue_id, venue_name_snapshot, venue_address_snapshot
                from course_offering_sessions
                where course_offering_id=? order by sequence_no
                """, (rs, rowNum) -> new SessionPlanView(
                        rs.getObject("id", UUID.class),
                        rs.getInt("sequence_no"),
                        instant(rs, "start_at"),
                        instant(rs, "end_at"),
                        rs.getObject("venue_id", UUID.class),
                        rs.getString("venue_name_snapshot"),
                        rs.getString("venue_address_snapshot")), offeringId);
        return new OfferingDetail(toSummary(offering), offering.description(), sessions);
    }

    @Transactional(readOnly = true)
    public PageResult<RegistrationView> registrations(
            AuthenticatedPrincipal actor, UUID offeringId, String status, int page, int size) {
        var offering = offerings.findById(offeringId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Course offering was not found"));
        identity.requireActiveUser(actor.userId());
        if (!identity.isAuthorizedForOrganization(actor, RoleCode.COMMITTEE, offering.organizationId())) {
            throw new BusinessException("AUTH_FORBIDDEN", "Committee or platform administrator permission is required");
        }
        int normalizedPage = page(page);
        int normalizedSize = size(size);
        String normalizedStatus = normalizeRegistrationStatus(status);
        List<Object> params = new ArrayList<>();
        params.add(offeringId);
        String statusSql = "";
        if (normalizedStatus != null) {
            statusSql = " and r.status=?";
            params.add(normalizedStatus);
        }
        Long total = jdbc.queryForObject(
                "select count(*) from course_offering_registrations r where r.course_offering_id=?" + statusSql,
                Long.class, params.toArray());
        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(normalizedSize);
        dataParams.add(normalizedPage * normalizedSize);
        List<RegistrationView> items = jdbc.query("""
                select r.id, r.user_id, u.display_name, r.status, r.registered_at, r.cancelled_at,
                       r.cancel_reason, r.converted_course_membership_id, cm.course_id,
                       case when r.status='ACTIVE' and
                            (select count(*) from schedule_reservations sr
                             where sr.user_id=r.user_id and sr.reservation_role='PARTICIPANT' and sr.status='HELD'
                               and sr.course_offering_session_id in
                                   (select id from course_offering_sessions where course_offering_id=r.course_offering_id))
                            <>
                            (select count(*) from course_offering_sessions where course_offering_id=r.course_offering_id)
                       then true else false end as schedule_conflict_indicator
                from course_offering_registrations r
                join users u on u.id=r.user_id
                left join course_memberships cm on cm.id=r.converted_course_membership_id
                where r.course_offering_id=?
                """ + statusSql + " order by r.registered_at, r.id limit ? offset ?",
                (rs, rowNum) -> registration(rs), dataParams.toArray());
        return new PageResult<>(items, normalizedPage, normalizedSize, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public PageResult<MyRegistrationView> mine(AuthenticatedPrincipal actor, int page, int size) {
        identity.requireActiveUser(actor.userId());
        Set<UUID> studentOrganizations = studentOrganizations(actor.userId());
        if (studentOrganizations.isEmpty()) {
            throw new BusinessException("AUTH_FORBIDDEN", "Active student role is required");
        }
        int normalizedPage = page(page);
        int normalizedSize = size(size);
        String orgIn = placeholders(studentOrganizations.size());
        List<Object> baseParams = new ArrayList<>();
        baseParams.add(actor.userId());
        baseParams.addAll(studentOrganizations);
        Long total = jdbc.queryForObject(
                "select count(*) from course_offering_registrations r where r.user_id=? and r.organization_id in ("
                        + orgIn + ")",
                Long.class, baseParams.toArray());
        List<Object> dataParams = new ArrayList<>(baseParams);
        dataParams.add(normalizedSize);
        dataParams.add(normalizedPage * normalizedSize);
        List<MyRegistrationView> items = jdbc.query("""
                select r.id, r.course_offering_id, o.title as offering_title, o.status as offering_status,
                       r.status, r.registered_at, r.cancelled_at, r.cancel_reason,
                       r.converted_course_membership_id, cm.course_id
                from course_offering_registrations r
                join course_offerings o on o.id=r.course_offering_id
                left join course_memberships cm on cm.id=r.converted_course_membership_id
                where r.user_id=? and r.organization_id in (
                """ + orgIn + ") order by r.registered_at desc, r.id desc limit ? offset ?",
                (rs, rowNum) -> new MyRegistrationView(
                        rs.getObject("id", UUID.class),
                        rs.getObject("course_offering_id", UUID.class),
                        rs.getString("offering_title"),
                        rs.getString("offering_status"),
                        rs.getString("status"),
                        instant(rs, "registered_at"),
                        nullableInstant(rs, "cancelled_at"),
                        rs.getString("cancel_reason"),
                        rs.getObject("converted_course_membership_id", UUID.class),
                        rs.getObject("course_id", UUID.class)), dataParams.toArray());
        return new PageResult<>(items, normalizedPage, normalizedSize, total == null ? 0 : total);
    }

    private ViewerScope viewerScope(AuthenticatedPrincipal actor) {
        identity.requireActiveUser(actor.userId());
        boolean globalAdmin = false;
        Map<UUID, EnumSet<RoleCode>> byOrganization = new HashMap<>();
        for (RoleAssignmentEntity assignment : roleAssignments.findByUserId(actor.userId())) {
            if (assignment.getStatus() != RoleAssignmentStatus.ACTIVE) continue;
            if (assignment.getRoleCode() == RoleCode.PLATFORM_ADMIN && assignment.getOrganization() == null) {
                globalAdmin = true;
                continue;
            }
            if (assignment.getOrganization() == null
                    || assignment.getOrganization().getStatus() != OrganizationStatus.ACTIVE) continue;
            byOrganization.computeIfAbsent(
                            assignment.getOrganization().getId(), ignored -> EnumSet.noneOf(RoleCode.class))
                    .add(assignment.getRoleCode());
        }
        if (!globalAdmin && byOrganization.isEmpty()) {
            throw new BusinessException("AUTH_FORBIDDEN", "No active organization role is available");
        }
        return new ViewerScope(globalAdmin, Map.copyOf(byOrganization));
    }

    private SqlFragment visibility(ViewerScope scope, UUID actorUserId) {
        if (scope.globalAdmin()) return new SqlFragment("true", List.of());
        List<UUID> committee = organizationsWith(scope, RoleCode.COMMITTEE);
        List<UUID> coach = organizationsWith(scope, RoleCode.COACH);
        List<UUID> student = organizationsWith(scope, RoleCode.STUDENT);
        List<String> clauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (!committee.isEmpty()) {
            clauses.add("o.organization_id in (" + placeholders(committee.size()) + ")");
            params.addAll(committee);
        }
        if (!coach.isEmpty()) {
            clauses.add("(o.organization_id in (" + placeholders(coach.size()) + ") and cp.user_id=?)");
            params.addAll(coach);
            params.add(actorUserId);
        }
        if (!student.isEmpty()) {
            clauses.add("(o.organization_id in (" + placeholders(student.size()) + ") and o.status='OPEN')");
            params.addAll(student);
        }
        if (clauses.isEmpty()) throw new BusinessException("AUTH_FORBIDDEN", "No offering visibility role is available");
        return new SqlFragment(String.join(" or ", clauses), params);
    }

    private List<UUID> organizationsWith(ViewerScope scope, RoleCode roleCode) {
        return scope.rolesByOrganization().entrySet().stream()
                .filter(entry -> entry.getValue().contains(roleCode))
                .map(Map.Entry::getKey)
                .toList();
    }

    private Set<UUID> studentOrganizations(UUID actorUserId) {
        return roleAssignments.findByUserId(actorUserId).stream()
                .filter(a -> a.getStatus() == RoleAssignmentStatus.ACTIVE)
                .filter(a -> a.getRoleCode() == RoleCode.STUDENT)
                .filter(a -> a.getOrganization() != null && a.getOrganization().getStatus() == OrganizationStatus.ACTIVE)
                .map(a -> a.getOrganization().getId())
                .collect(java.util.stream.Collectors.toSet());
    }

    private void appendFilters(StringBuilder where, List<Object> params, OfferingFilter filter) {
        if (filter.organizationId() != null) {
            where.append(" and o.organization_id=?");
            params.add(filter.organizationId());
        }
        if (filter.status() != null) {
            where.append(" and o.status=?");
            params.add(filter.status());
        }
        if (filter.from() != null) {
            where.append(" and exists (select 1 from course_offering_sessions s where s.course_offering_id=o.id and s.start_at>=?)");
            params.add(Timestamp.from(filter.from()));
        }
        if (filter.to() != null) {
            where.append(" and exists (select 1 from course_offering_sessions s where s.course_offering_id=o.id and s.start_at<?)");
            params.add(Timestamp.from(filter.to()));
        }
        if (filter.coachProfileId() != null) {
            where.append(" and o.coach_profile_id=?");
            params.add(filter.coachProfileId());
        }
        if (filter.skillLevel() != null) {
            where.append(" and o.skill_level=?");
            params.add(filter.skillLevel());
        }
    }

    private OfferingFilter normalize(OfferingFilter filter) {
        OfferingFilter source = filter == null
                ? new OfferingFilter(null, null, null, null, null, null, 0, 20, null)
                : filter;
        if (source.from() != null && source.to() != null && !source.to().isAfter(source.from())) {
            throw new BusinessException("VALIDATION_FAILED", "to must be after from");
        }
        String status = null;
        if (source.status() != null && !source.status().isBlank()) {
            try {
                status = CourseOfferingStatus.valueOf(source.status().trim().toUpperCase(Locale.ROOT)).name();
            } catch (IllegalArgumentException ex) {
                throw new BusinessException("VALIDATION_FAILED", "Unsupported offering status");
            }
        }
        String skillLevel = source.skillLevel() == null || source.skillLevel().isBlank()
                ? null : source.skillLevel().trim();
        return new OfferingFilter(
                source.organizationId(), status, source.from(), source.to(), source.coachProfileId(), skillLevel,
                page(source.page()), size(source.size()), source.sort());
    }

    private String orderBy(String sort) {
        if (sort == null || sort.isBlank() || "firstSessionAt,asc".equalsIgnoreCase(sort)) {
            return " order by fs.first_session_at asc nulls last, o.id asc";
        }
        if ("firstSessionAt,desc".equalsIgnoreCase(sort)) {
            return " order by fs.first_session_at desc nulls last, o.id asc";
        }
        if ("registrationCloseAt,asc".equalsIgnoreCase(sort)) {
            return " order by o.registration_close_at asc, o.id asc";
        }
        if ("registrationCloseAt,desc".equalsIgnoreCase(sort)) {
            return " order by o.registration_close_at desc, o.id asc";
        }
        if ("createdAt,desc".equalsIgnoreCase(sort)) {
            return " order by o.created_at desc, o.id desc";
        }
        throw new BusinessException("VALIDATION_FAILED", "Unsupported offering sort");
    }

    private OfferingSummary summary(ResultSet rs, int rowNum) throws SQLException {
        return toSummary(row(rs, rowNum));
    }

    private OfferingRow row(ResultSet rs, int rowNum) throws SQLException {
        return new OfferingRow(
                rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("status"),
                rs.getString("schedule_type"),
                rs.getString("billing_mode"),
                rs.getString("skill_level"),
                rs.getInt("minimum_participants"),
                rs.getInt("maximum_participants"),
                instant(rs, "registration_open_at"),
                instant(rs, "registration_close_at"),
                rs.getLong("version"),
                rs.getObject("coach_profile_id", UUID.class),
                rs.getObject("coach_user_id", UUID.class),
                rs.getString("coach_display_name"),
                nullableInstant(rs, "first_session_at"),
                rs.getInt("registered_count"),
                rs.getObject("price_snapshot_id", UUID.class),
                rs.getBigDecimal("price_per_participant"),
                rs.getString("currency"),
                rs.getObject("own_registration_id", UUID.class),
                rs.getString("own_registration_status"));
    }

    private OfferingSummary toSummary(OfferingRow row) {
        int remaining = Math.max(0, row.maximumParticipants() - row.registeredCount());
        return new OfferingSummary(
                row.id(), row.organizationId(), row.title(), row.status(),
                new CoachSummary(row.coachProfileId(), row.coachUserId(), row.coachDisplayName()),
                row.scheduleType(), row.firstSessionAt(), row.registrationOpenAt(), row.registrationCloseAt(),
                row.minimumParticipants(), row.maximumParticipants(), row.registeredCount(), remaining,
                row.billingMode(), row.skillLevel(), row.priceSnapshotId(), row.pricePerParticipant(), row.currency(),
                registrationState(row), row.ownRegistrationId(), row.ownRegistrationStatus(), row.version());
    }

    private String registrationState(OfferingRow row) {
        if ("ACTIVE".equals(row.ownRegistrationStatus())) return "REGISTERED";
        if (!"OPEN".equals(row.status())) return "CLOSED";
        Instant now = Instant.now();
        if (now.isBefore(row.registrationOpenAt())) return "NOT_OPEN";
        if (!now.isBefore(row.registrationCloseAt())) return "CLOSED";
        if (row.registeredCount() >= row.maximumParticipants()) return "FULL";
        return "OPEN";
    }

    private RegistrationView registration(ResultSet rs) throws SQLException {
        return new RegistrationView(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("display_name"),
                rs.getString("status"),
                instant(rs, "registered_at"),
                nullableInstant(rs, "cancelled_at"),
                rs.getString("cancel_reason"),
                rs.getBoolean("schedule_conflict_indicator"),
                rs.getObject("converted_course_membership_id", UUID.class),
                rs.getObject("course_id", UUID.class));
    }

    private String normalizeRegistrationStatus(String status) {
        if (status == null || status.isBlank()) return null;
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "CANCELLED", "CONVERTED").contains(normalized)) {
            throw new BusinessException("VALIDATION_FAILED", "Unsupported registration status");
        }
        return normalized;
    }

    private int page(int page) {
        if (page < 0) throw new BusinessException("VALIDATION_FAILED", "page must be zero or greater");
        return page;
    }

    private int size(int size) {
        if (size < 1 || size > 100) throw new BusinessException("VALIDATION_FAILED", "size must be between 1 and 100");
        return size;
    }

    private String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toInstant();
    }

    private Instant nullableInstant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    public record OfferingFilter(
            UUID organizationId,
            String status,
            Instant from,
            Instant to,
            UUID coachProfileId,
            String skillLevel,
            int page,
            int size,
            String sort) { }

    public record PageResult<T>(List<T> items, int page, int size, long total) { }

    public record CoachSummary(UUID coachProfileId, UUID userId, String displayName) { }

    public record OfferingSummary(
            UUID id,
            UUID organizationId,
            String title,
            String status,
            CoachSummary coach,
            String scheduleType,
            Instant firstSessionAt,
            Instant registrationOpenAt,
            Instant registrationCloseAt,
            int minimumParticipants,
            int maximumParticipants,
            int registeredCount,
            int remainingCapacity,
            String billingMode,
            String skillLevel,
            UUID priceSnapshotId,
            BigDecimal pricePerParticipant,
            String currency,
            String registrationState,
            UUID ownRegistrationId,
            String ownRegistrationStatus,
            long version) { }

    public record OfferingDetail(OfferingSummary summary, String description, List<SessionPlanView> sessionPlans) { }

    public record SessionPlanView(
            UUID id,
            int sequenceNo,
            Instant startAt,
            Instant endAt,
            UUID venueId,
            String venueName,
            String venueAddress) { }

    public record RegistrationView(
            UUID id,
            UUID userId,
            String displayName,
            String status,
            Instant registeredAt,
            Instant cancelledAt,
            String cancelReason,
            boolean scheduleConflictIndicator,
            UUID convertedCourseMembershipId,
            UUID courseId) { }

    public record MyRegistrationView(
            UUID id,
            UUID offeringId,
            String offeringTitle,
            String offeringStatus,
            String status,
            Instant registeredAt,
            Instant cancelledAt,
            String cancelReason,
            UUID convertedCourseMembershipId,
            UUID courseId) { }

    private record ViewerScope(boolean globalAdmin, Map<UUID, EnumSet<RoleCode>> rolesByOrganization) { }
    private record SqlFragment(String sql, List<Object> params) { }
    private record OfferingRow(
            UUID id,
            UUID organizationId,
            String title,
            String description,
            String status,
            String scheduleType,
            String billingMode,
            String skillLevel,
            int minimumParticipants,
            int maximumParticipants,
            Instant registrationOpenAt,
            Instant registrationCloseAt,
            long version,
            UUID coachProfileId,
            UUID coachUserId,
            String coachDisplayName,
            Instant firstSessionAt,
            int registeredCount,
            UUID priceSnapshotId,
            BigDecimal pricePerParticipant,
            String currency,
            UUID ownRegistrationId,
            String ownRegistrationStatus) { }
}
