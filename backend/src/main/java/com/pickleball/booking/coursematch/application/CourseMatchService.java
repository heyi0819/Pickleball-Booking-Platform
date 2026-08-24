package com.pickleball.booking.coursematch.application;

import com.pickleball.booking.coach.domain.CoachProfileApprovalStatus;
import com.pickleball.booking.coach.infrastructure.CoachProfileEntity;
import com.pickleball.booking.coach.infrastructure.CoachProfileRepository;
import com.pickleball.booking.coursematch.domain.CourseMatchCoachStatus;
import com.pickleball.booking.coursematch.domain.VenueSnapshotType;
import com.pickleball.booking.coursematch.infrastructure.*;
import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.identity.application.IdentityService;
import com.pickleball.booking.identity.domain.RoleCode;
import com.pickleball.booking.lessonrequest.domain.LessonRequestStatus;
import com.pickleball.booking.lessonrequest.infrastructure.LessonRequestEntity;
import com.pickleball.booking.lessonrequest.infrastructure.LessonRequestRepository;
import com.pickleball.booking.shared.application.AuditOutboxService;
import com.pickleball.booking.shared.application.BusinessException;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CourseMatchService {
    private final IdentityService identity;
    private final LessonRequestRepository lessonRequests;
    private final CoachProfileRepository coachProfiles;
    private final CourseMatchRepository matches;
    private final CourseMatchSessionRepository sessions;
    private final CourseMatchSessionCoachRepository assignments;
    private final AuditOutboxService audit;
    private final JdbcTemplate jdbc;

    public CourseMatchService(
            IdentityService identity,
            LessonRequestRepository lessonRequests,
            CoachProfileRepository coachProfiles,
            CourseMatchRepository matches,
            CourseMatchSessionRepository sessions,
            CourseMatchSessionCoachRepository assignments,
            AuditOutboxService audit,
            JdbcTemplate jdbc) {
        this.identity = identity;
        this.lessonRequests = lessonRequests;
        this.coachProfiles = coachProfiles;
        this.matches = matches;
        this.sessions = sessions;
        this.assignments = assignments;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @Transactional
    public Detail create(AuthenticatedPrincipal actor, CreateCommand command) {
        identity.requireActiveUser(actor.userId());
        LessonRequestEntity request = lessonRequests.findLockedById(command.lessonRequestId())
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Lesson request was not found"));
        requireCommittee(actor, request.getOrganizationId());
        requireApproved(request);
        validateParticipantCount(request, command.participantCount());
        validateSessionPlan(command.sessionPlan(), request.getRequestedSessionCount());

        CourseMatchEntity match = matches.save(new CourseMatchEntity(
                request.getOrganizationId(),
                request.getId(),
                command.participantCount(),
                request.getMinimumParticipants(),
                request.getMaximumParticipants(),
                actor.userId()));

        Map<Short, CourseMatchSessionEntity> persistedSessions = persistSessions(
                match, request.getOrganizationId(), command.sessionPlan());
        replaceAssignments(match, request.getOrganizationId(), persistedSessions,
                command.coachAssignments(), actor.userId());

        audit.record(match.getOrganizationId(), actor.userId(), "COURSE_MATCH_CREATED",
                "CourseMatch", match.getId(), null);
        return loadDetail(match);
    }

    @Transactional
    public Detail patch(AuthenticatedPrincipal actor, UUID courseMatchId, PatchCommand command) {
        CourseMatchEntity match = matches.findLockedById(courseMatchId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Course match was not found"));
        requireCommittee(actor, match.getOrganizationId());
        match.requireDraft();
        if (command.participantCount() == null && command.sessionPlan() == null && command.coachAssignments() == null) {
            throw new BusinessException("VALIDATION_FAILED", "At least one editable field is required");
        }

        LessonRequestEntity request = lessonRequests.findById(match.getLessonRequestId())
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Lesson request was not found"));
        requireApproved(request);
        boolean pricingSensitiveChange = false;

        if (command.participantCount() != null) {
            validateParticipantCount(request, command.participantCount());
            match.updateParticipantCount(command.participantCount());
            pricingSensitiveChange = true;
        }

        List<CourseMatchSessionEntity> currentSessions = sessions.findByCourseMatchIdOrderBySessionIndexAsc(match.getId());
        Map<Short, CourseMatchSessionEntity> byIndex = indexSessions(currentSessions);
        if (command.sessionPlan() != null) {
            validateSessionPlan(command.sessionPlan(), request.getRequestedSessionCount());
            Set<Short> requestedIndexes = command.sessionPlan().stream().map(SessionPlanCommand::sessionIndex)
                    .collect(java.util.stream.Collectors.toSet());
            if (!requestedIndexes.equals(byIndex.keySet())) {
                throw new BusinessException("VALIDATION_FAILED",
                        "Draft session patch must preserve the approved session indexes");
            }
            for (SessionPlanCommand plan : command.sessionPlan()) {
                VenueSnapshot venue = resolveVenue(match.getOrganizationId(), plan);
                byIndex.get(plan.sessionIndex()).updatePlan(
                        plan.startAt(), plan.endAt(), venue.type(), venue.id(), venue.name(), venue.address(), venue.fingerprint());
            }
            pricingSensitiveChange = true;
        }

        if (command.coachAssignments() != null) {
            replaceAssignments(match, match.getOrganizationId(), byIndex, command.coachAssignments(), actor.userId());
            pricingSensitiveChange = true;
        }

        if (pricingSensitiveChange) supersedeConfirmedPricing(match.getId());
        audit.record(match.getOrganizationId(), actor.userId(), "COURSE_MATCH_UPDATED",
                "CourseMatch", match.getId(), null);
        return loadDetail(match);
    }

    @Transactional
    public Detail detail(AuthenticatedPrincipal actor, UUID courseMatchId) {
        identity.requireActiveUser(actor.userId());
        CourseMatchEntity match = matches.findById(courseMatchId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Course match was not found"));
        LessonRequestEntity request = lessonRequests.findById(match.getLessonRequestId())
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Lesson request was not found"));
        if (!request.getRequesterUserId().equals(actor.userId())
                && !isAssignedCoach(match.getId(), actor.userId())
                && !identity.isAuthorizedForOrganization(actor, RoleCode.COMMITTEE, match.getOrganizationId())) {
            throw new BusinessException("AUTH_FORBIDDEN", "Course match is outside your scope");
        }
        return loadDetail(match);
    }

    private Map<Short, CourseMatchSessionEntity> persistSessions(
            CourseMatchEntity match, UUID organizationId, List<SessionPlanCommand> plans) {
        Map<Short, CourseMatchSessionEntity> result = new LinkedHashMap<>();
        for (SessionPlanCommand plan : plans.stream().sorted(Comparator.comparingInt(SessionPlanCommand::sessionIndex)).toList()) {
            VenueSnapshot venue = resolveVenue(organizationId, plan);
            CourseMatchSessionEntity session = sessions.save(new CourseMatchSessionEntity(
                    match.getId(), plan.sessionIndex(), plan.startAt(), plan.endAt(), venue.type(), venue.id(),
                    venue.name(), venue.address(), venue.fingerprint()));
            result.put(plan.sessionIndex(), session);
        }
        return result;
    }

    private void replaceAssignments(
            CourseMatchEntity match,
            UUID organizationId,
            Map<Short, CourseMatchSessionEntity> sessionByIndex,
            List<CoachAssignmentCommand> requested,
            UUID actorUserId) {
        if (requested == null || requested.isEmpty()) {
            throw new BusinessException("VALIDATION_FAILED", "At least one coach assignment is required");
        }

        Set<String> pairs = new HashSet<>();
        Map<Short, Short> nextOrder = new HashMap<>();
        Set<Short> coveredSessions = new HashSet<>();
        List<PreparedAssignment> prepared = new ArrayList<>();

        for (CoachAssignmentCommand assignment : requested) {
            if (assignment.coachProfileId() == null || assignment.sessionIndexes() == null || assignment.sessionIndexes().isEmpty()) {
                throw new BusinessException("VALIDATION_FAILED", "Coach assignment is incomplete");
            }
            CoachProfileEntity coach = coachProfiles.findById(assignment.coachProfileId())
                    .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Coach profile was not found"));
            if (!coach.getOrganizationId().equals(organizationId)
                    || coach.getApprovalStatus() != CoachProfileApprovalStatus.APPROVED) {
                throw new BusinessException("COACH_NOT_APPROVED", "Coach is not approved for this organization");
            }
            for (Short index : assignment.sessionIndexes()) {
                CourseMatchSessionEntity session = sessionByIndex.get(index);
                if (session == null) {
                    throw new BusinessException("VALIDATION_FAILED", "Coach assignment references an unknown session index");
                }
                String pair = index + ":" + assignment.coachProfileId();
                if (!pairs.add(pair)) {
                    throw new BusinessException("VALIDATION_FAILED", "Coach assignment is duplicated for a session");
                }
                short order = (short) (nextOrder.getOrDefault(index, (short) 0) + 1);
                nextOrder.put(index, order);
                coveredSessions.add(index);
                prepared.add(new PreparedAssignment(session.getId(), assignment.coachProfileId(), order));
            }
        }

        if (!coveredSessions.equals(sessionByIndex.keySet())) {
            throw new BusinessException("MATCH_NOT_READY", "Every match session requires at least one coach assignment");
        }

        jdbc.update("""
                update course_match_session_coaches c
                set status = 'CANCELLED'
                from course_match_sessions s
                where c.course_match_session_id = s.id
                  and s.course_match_id = ?
                  and c.status in ('INVITED','ACCEPTED')
                """, match.getId());

        for (PreparedAssignment preparedAssignment : prepared) {
            assignments.save(new CourseMatchSessionCoachEntity(
                    preparedAssignment.sessionId(), preparedAssignment.coachProfileId(), null,
                    preparedAssignment.assignmentOrder(), actorUserId));
        }
    }

    private Detail loadDetail(CourseMatchEntity match) {
        List<CourseMatchSessionEntity> sessionList = sessions.findByCourseMatchIdOrderBySessionIndexAsc(match.getId());
        List<UUID> sessionIds = sessionList.stream().map(CourseMatchSessionEntity::getId).toList();
        List<CourseMatchSessionCoachEntity> coachList = sessionIds.isEmpty()
                ? List.of()
                : assignments.findByCourseMatchSessionIdInOrderByCourseMatchSessionIdAscAssignmentOrderAsc(sessionIds);
        LessonRequestEntity request = lessonRequests.findById(match.getLessonRequestId())
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Lesson request was not found"));

        boolean lessonApproved = request.getStatus() == LessonRequestStatus.APPROVED;
        boolean sessionsFuture = !sessionList.isEmpty()
                && sessionList.stream().allMatch(s -> s.getScheduledStartAt().isAfter(Instant.now()));
        boolean venueReady = !sessionList.isEmpty() && sessionList.stream().allMatch(this::venueReady);
        boolean coachesAccepted = coachesAccepted(sessionList, coachList);
        Optional<UUID> priceSnapshotId = confirmedPriceSnapshotId(match.getId());
        boolean pricingConfirmed = priceSnapshotId.isPresent();
        boolean participantCountValid = match.participantCountValid();
        boolean scheduleConflictFree = coachesAccepted && scheduleConflictFree(match.getId(), match.getOrganizationId());
        boolean readyToConfirm = lessonApproved && coachesAccepted && sessionsFuture && venueReady
                && pricingConfirmed && participantCountValid && scheduleConflictFree;

        Readiness readiness = new Readiness(lessonApproved, coachesAccepted, sessionsFuture,
                scheduleConflictFree, venueReady, pricingConfirmed, participantCountValid, readyToConfirm);
        PriceState pricing = new PriceState(pricingConfirmed ? "CONFIRMED" : "NOT_CONFIRMED",
                priceSnapshotId.orElse(null));
        return new Detail(match, sessionList, coachList, readiness, pricing);
    }

    private boolean coachesAccepted(List<CourseMatchSessionEntity> sessionList, List<CourseMatchSessionCoachEntity> coachList) {
        Map<UUID, List<CourseMatchSessionCoachEntity>> activeBySession = new HashMap<>();
        for (CourseMatchSessionCoachEntity assignment : coachList) {
            if (assignment.getStatus() == CourseMatchCoachStatus.INVITED
                    || assignment.getStatus() == CourseMatchCoachStatus.ACCEPTED) {
                activeBySession.computeIfAbsent(assignment.getCourseMatchSessionId(), ignored -> new ArrayList<>()).add(assignment);
            }
        }
        return !sessionList.isEmpty() && sessionList.stream().allMatch(session -> {
            List<CourseMatchSessionCoachEntity> active = activeBySession.getOrDefault(session.getId(), List.of());
            return !active.isEmpty() && active.stream().allMatch(a -> a.getStatus() == CourseMatchCoachStatus.ACCEPTED);
        });
    }

    private boolean scheduleConflictFree(UUID courseMatchId, UUID organizationId) {
        Boolean hasConflict = jdbc.queryForObject("""
                select exists (
                    select 1
                    from course_match_sessions ms
                    join course_match_session_coaches mc
                      on mc.course_match_session_id = ms.id
                     and mc.status = 'ACCEPTED'
                    join coach_profiles cp on cp.id = mc.coach_profile_id
                    join schedule_reservations r
                      on r.organization_id = ?
                     and r.user_id = cp.user_id
                     and r.status in ('HELD','CONFIRMED')
                     and r.reserved_period && tstzrange(ms.scheduled_start_at, ms.scheduled_end_at, '[)')
                    join course_sessions cs on cs.id = r.course_session_id
                    join courses c on c.id = cs.course_id
                    where ms.course_match_id = ?
                      and (c.source_match_id is null or c.source_match_id <> ?)
                )
                """, Boolean.class, organizationId, courseMatchId, courseMatchId);
        return !Boolean.TRUE.equals(hasConflict);
    }

    private boolean venueReady(CourseMatchSessionEntity session) {
        if (session.getVenueSnapshotName() == null || session.getVenueSnapshotName().isBlank()
                || session.getVenueFingerprint() == null || session.getVenueFingerprint().length() != 64) return false;
        return session.getVenueSnapshotType() == VenueSnapshotType.VENUE
                ? session.getVenueSnapshotId() != null
                : session.getVenueSnapshotId() == null;
    }

    private Optional<UUID> confirmedPriceSnapshotId(UUID courseMatchId) {
        return jdbc.query("""
                select id from course_match_price_snapshots
                where course_match_id = ? and status = 'CONFIRMED'
                order by version_no desc limit 1
                """, (rs, rowNum) -> rs.getObject("id", UUID.class), courseMatchId).stream().findFirst();
    }

    private void supersedeConfirmedPricing(UUID courseMatchId) {
        jdbc.update("""
                update course_match_price_snapshots
                set status = 'SUPERSEDED'
                where course_match_id = ? and status = 'CONFIRMED'
                """, courseMatchId);
    }

    private boolean isAssignedCoach(UUID courseMatchId, UUID userId) {
        Boolean allowed = jdbc.queryForObject("""
                select exists (
                    select 1
                    from course_match_session_coaches c
                    join course_match_sessions s on s.id = c.course_match_session_id
                    join coach_profiles p on p.id = c.coach_profile_id
                    where s.course_match_id = ? and p.user_id = ?
                )
                """, Boolean.class, courseMatchId, userId);
        return Boolean.TRUE.equals(allowed);
    }

    private void requireApproved(LessonRequestEntity request) {
        if (request.getStatus() != LessonRequestStatus.APPROVED) {
            throw new BusinessException("LESSON_REQUEST_NOT_APPROVED", "Lesson request must be approved before matching");
        }
    }

    private void requireCommittee(AuthenticatedPrincipal actor, UUID organizationId) {
        identity.requireActiveUser(actor.userId());
        if (!identity.isAuthorizedForOrganization(actor, RoleCode.COMMITTEE, organizationId)) {
            throw new BusinessException("AUTH_FORBIDDEN", "Committee or platform administrator permission is required");
        }
    }

    private void validateParticipantCount(LessonRequestEntity request, short participantCount) {
        if (participantCount <= 0) {
            throw new BusinessException("VALIDATION_FAILED", "Participant count must be positive");
        }
        Short max = request.getMaximumParticipants();
        if (max != null && participantCount > max) {
            throw new BusinessException("PARTICIPANT_ABOVE_MAX", "Participant count exceeds the approved maximum");
        }
        if ("PRIVATE".equals(request.getLessonType()) && participantCount > 4) {
            throw new BusinessException("PARTICIPANT_ABOVE_MAX", "Private lessons support at most four participants");
        }
    }

    private void validateSessionPlan(List<SessionPlanCommand> plans, short requestedSessionCount) {
        if (plans == null || plans.size() != requestedSessionCount) {
            throw new BusinessException("VALIDATION_FAILED", "Session plan must match the approved session count");
        }
        Set<Short> indexes = new HashSet<>();
        for (SessionPlanCommand plan : plans) {
            if (plan == null || plan.sessionIndex() <= 0 || !indexes.add(plan.sessionIndex())) {
                throw new BusinessException("VALIDATION_FAILED", "Session indexes must be positive and unique");
            }
            if (plan.startAt() == null || plan.endAt() == null || !plan.startAt().isBefore(plan.endAt())) {
                throw new BusinessException("VALIDATION_FAILED", "Session start must be before end");
            }
            if (!plan.startAt().isAfter(Instant.now())) {
                throw new BusinessException("BOOKING_TIME_NOT_FUTURE", "Match sessions must be in the future");
            }
        }
        List<SessionPlanCommand> sorted = plans.stream().sorted(Comparator.comparing(SessionPlanCommand::startAt)).toList();
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).startAt().isBefore(sorted.get(i - 1).endAt())) {
                throw new BusinessException("SCHEDULE_CONFLICT", "Match sessions cannot overlap each other");
            }
        }
    }

    private Map<Short, CourseMatchSessionEntity> indexSessions(List<CourseMatchSessionEntity> sessionList) {
        Map<Short, CourseMatchSessionEntity> result = new LinkedHashMap<>();
        for (CourseMatchSessionEntity session : sessionList) result.put(session.getSessionIndex(), session);
        return result;
    }

    private VenueSnapshot resolveVenue(UUID organizationId, SessionPlanCommand plan) {
        if (plan.venueId() != null) {
            VenueRow row = jdbc.query("""
                    select organization_id, name, address, status from venues where id = ?
                    """, (rs, rowNum) -> new VenueRow(
                    rs.getObject("organization_id", UUID.class), rs.getString("name"),
                    rs.getString("address"), rs.getString("status")), plan.venueId()).stream().findFirst()
                    .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Venue was not found"));
            if (!row.organizationId().equals(organizationId)) {
                throw new BusinessException("ORG_SCOPE_DENIED", "Venue is outside the organization");
            }
            if (!"ACTIVE".equals(row.status())) {
                throw new BusinessException("VALIDATION_FAILED", "Venue is not active");
            }
            return snapshot(VenueSnapshotType.VENUE, plan.venueId(), row.name(), row.address());
        }
        if (plan.venueName() == null || plan.venueName().isBlank()) {
            throw new BusinessException("VALIDATION_FAILED", "External venue name is required");
        }
        return snapshot(VenueSnapshotType.OTHER, null, plan.venueName(), plan.venueAddress());
    }

    private VenueSnapshot snapshot(VenueSnapshotType type, UUID id, String name, String address) {
        String cleanName = name == null ? null : name.trim();
        String cleanAddress = address == null || address.isBlank() ? null : address.trim();
        if (cleanName == null || cleanName.isBlank() || cleanName.length() > 150
                || (cleanAddress != null && cleanAddress.length() > 300)) {
            throw new BusinessException("VALIDATION_FAILED", "Venue snapshot exceeds allowed length");
        }
        String fingerprint = sha256(type.name() + "|" + Objects.toString(id, "") + "|"
                + cleanName + "|" + Objects.toString(cleanAddress, ""));
        return new VenueSnapshot(type, id, cleanName, cleanAddress, fingerprint);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public record SessionPlanCommand(short sessionIndex, Instant startAt, Instant endAt,
            UUID venueId, String venueName, String venueAddress) {}
    public record CoachAssignmentCommand(UUID coachProfileId, List<Short> sessionIndexes) {}
    public record CreateCommand(UUID lessonRequestId, List<CoachAssignmentCommand> coachAssignments,
            List<SessionPlanCommand> sessionPlan, short participantCount) {}
    public record PatchCommand(Short participantCount, List<CoachAssignmentCommand> coachAssignments,
            List<SessionPlanCommand> sessionPlan) {}
    public record Readiness(boolean lessonRequestApproved, boolean coachesAccepted, boolean sessionsFuture,
            boolean scheduleConflictFree, boolean venueReady, boolean pricingConfirmed,
            boolean participantCountValid, boolean readyToConfirm) {}
    public record PriceState(String status, UUID priceSnapshotId) {}
    public record Detail(CourseMatchEntity match, List<CourseMatchSessionEntity> sessions,
            List<CourseMatchSessionCoachEntity> coachAssignments, Readiness readiness, PriceState pricing) {}

    private record VenueRow(UUID organizationId, String name, String address, String status) {}
    private record VenueSnapshot(VenueSnapshotType type, UUID id, String name, String address, String fingerprint) {}
    private record PreparedAssignment(UUID sessionId, UUID coachProfileId, short assignmentOrder) {}
}
