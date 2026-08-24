package com.pickleball.booking.coursematch.application;

import com.pickleball.booking.coach.domain.CoachProfileApprovalStatus;
import com.pickleball.booking.coach.infrastructure.CoachProfileEntity;
import com.pickleball.booking.coach.infrastructure.CoachProfileRepository;
import com.pickleball.booking.coursematch.domain.CourseMatchCoachStatus;
import com.pickleball.booking.coursematch.infrastructure.CourseMatchEntity;
import com.pickleball.booking.coursematch.infrastructure.CourseMatchRepository;
import com.pickleball.booking.coursematch.infrastructure.CourseMatchSessionCoachEntity;
import com.pickleball.booking.coursematch.infrastructure.CourseMatchSessionCoachRepository;
import com.pickleball.booking.coursematch.infrastructure.CourseMatchSessionEntity;
import com.pickleball.booking.coursematch.infrastructure.CourseMatchSessionRepository;
import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.identity.application.IdentityService;
import com.pickleball.booking.identity.domain.RoleCode;
import com.pickleball.booking.lessonrequest.domain.LessonRequestStatus;
import com.pickleball.booking.lessonrequest.infrastructure.LessonRequestEntity;
import com.pickleball.booking.lessonrequest.infrastructure.LessonRequestRepository;
import com.pickleball.booking.shared.application.AuditOutboxService;
import com.pickleball.booking.shared.application.BusinessException;
import com.pickleball.booking.shared.application.IdempotencyService;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ConfirmCourseMatchService {
    private static final String IDEMPOTENCY_OPERATION = "COURSE_MATCH_CONFIRMATION";

    private final IdentityService identity;
    private final CourseMatchRepository matches;
    private final CourseMatchSessionRepository sessions;
    private final CourseMatchSessionCoachRepository assignments;
    private final CoachProfileRepository coachProfiles;
    private final LessonRequestRepository lessonRequests;
    private final MatchPricingService pricing;
    private final IdempotencyService idempotency;
    private final AuditOutboxService audit;
    private final JdbcTemplate jdbc;

    public ConfirmCourseMatchService(
            IdentityService identity,
            CourseMatchRepository matches,
            CourseMatchSessionRepository sessions,
            CourseMatchSessionCoachRepository assignments,
            CoachProfileRepository coachProfiles,
            LessonRequestRepository lessonRequests,
            MatchPricingService pricing,
            IdempotencyService idempotency,
            AuditOutboxService audit,
            JdbcTemplate jdbc) {
        this.identity = identity;
        this.matches = matches;
        this.sessions = sessions;
        this.assignments = assignments;
        this.coachProfiles = coachProfiles;
        this.lessonRequests = lessonRequests;
        this.pricing = pricing;
        this.idempotency = idempotency;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @Transactional
    public ConfirmationResult confirm(
            AuthenticatedPrincipal actor,
            UUID courseMatchId,
            String idempotencyKey,
            ConfirmCommand command) {
        identity.requireActiveUser(actor.userId());
        if (command == null || !command.confirm()) {
            throw new BusinessException("VALIDATION_FAILED", "Course match confirmation requires confirm=true");
        }

        CourseMatchEntity match = matches.findLockedById(courseMatchId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Course match was not found"));
        requireCommittee(actor, match.getOrganizationId());

        var idempotencyRecord = idempotency.begin(
                match.getOrganizationId(), actor.userId(), IDEMPOTENCY_OPERATION, idempotencyKey,
                courseMatchId + "|confirm=true");
        if (idempotencyRecord.getResultResourceId() != null) {
            return loadResult(match, idempotencyRecord.getResultResourceId());
        }

        match.requireDraft();
        LessonRequestEntity request = lessonRequests.findLockedById(match.getLessonRequestId())
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Lesson request was not found"));
        validateRequest(match, request);

        List<CourseMatchSessionEntity> matchSessions = sessions.findByCourseMatchIdOrderBySessionIndexAsc(match.getId());
        validateSessions(matchSessions);
        lockMatchAssignments(match.getId());
        List<CourseMatchSessionCoachEntity> matchAssignments = assignments
                .findByCourseMatchSessionIdInOrderByCourseMatchSessionIdAscAssignmentOrderAsc(
                        matchSessions.stream().map(CourseMatchSessionEntity::getId).toList());
        Map<UUID, CoachIdentity> coachIdentities = validateAcceptedCoaches(
                match.getOrganizationId(), matchSessions, matchAssignments);

        PriceSnapshotRow confirmedPrice = lockConfirmedPrice(match.getId());
        MatchPricingService.PricingPreview currentPricing = pricing.preview(actor, match.getId());
        if (!confirmedPrice.pricingFingerprint().equalsIgnoreCase(currentPricing.pricingFingerprint())
                || confirmedPrice.totalAmount().compareTo(currentPricing.totalAmount()) != 0
                || !confirmedPrice.currency().equals(currentPricing.currency())
                || !confirmedPrice.billingMode().equals(currentPricing.billingMode())) {
            throw new BusinessException("PRICE_CHANGED_RECALC_REQUIRED",
                    "Pricing inputs changed after pricing confirmation");
        }

        List<MatchPriceItemRow> matchPriceItems = loadMatchPriceItems(confirmedPrice.id());
        validateMatchPriceTotal(confirmedPrice, matchPriceItems);
        ClaimRow claim = lockSourceClaimIfAny(request);

        UUID courseId = UUID.randomUUID();
        insertCourse(courseId, match, request, actor.userId());
        insertCourseApproval(courseId, match.getOrganizationId(), actor.userId(), match.getId());
        insertPrimaryContact(courseId, match.getOrganizationId(), request.getRequesterUserId(), actor.userId());

        Map<UUID, FormalSession> formalSessions = insertFormalSessions(courseId, match, request, matchSessions);
        insertFormalCoachAssignmentsAndReservations(
                match, matchAssignments, coachIdentities, formalSessions, actor.userId());
        insertVenueArrangements(
                match, request, matchSessions, formalSessions, matchPriceItems, actor.userId());

        List<FormalPriceSnapshot> formalPrices = insertFormalPriceSnapshots(
                match, matchSessions, formalSessions, confirmedPrice, matchPriceItems, actor.userId());
        UUID receivableId = insertReceivable(
                courseId, match.getOrganizationId(), request, confirmedPrice, formalPrices);

        convertSourceClaimIfAny(request, match, claim);
        request.markMatched();
        match.confirm(actor.userId(), "Course formed from confirmed match");

        audit.record(match.getOrganizationId(), actor.userId(), "COURSE_MATCH_CONFIRMED",
                "CourseMatch", match.getId(), null);
        audit.record(match.getOrganizationId(), actor.userId(), "COURSE_CREATED_FROM_MATCH",
                "Course", courseId, null);
        idempotencyRecord.complete("Course", courseId, 201);

        return new ConfirmationResult(
                match.getId(), match.getStatus().name(), courseId, "ACTIVE",
                formalSessions.values().stream().map(FormalSession::id).toList(), List.of(receivableId));
    }

    private void validateRequest(CourseMatchEntity match, LessonRequestEntity request) {
        if (!match.getOrganizationId().equals(request.getOrganizationId())) {
            throw new BusinessException("ORG_SCOPE_DENIED", "Lesson request is outside the course match organization");
        }
        if (request.getStatus() != LessonRequestStatus.APPROVED) {
            throw new BusinessException("LESSON_REQUEST_NOT_APPROVED", "Lesson request must remain approved");
        }
        if (request.getRequestedSessionCount() <= 0 || match.getParticipantCount() <= 0) {
            throw new BusinessException("MATCH_NOT_READY", "Course match request data is incomplete");
        }
        if (request.getGuestParticipantCount() < 0
                || request.getGuestParticipantCount() > match.getParticipantCount()) {
            throw new BusinessException("VALIDATION_FAILED", "Guest participant count exceeds total participants");
        }
        if (match.getMinimumParticipantsSnapshot() != null
                && match.getParticipantCount() < match.getMinimumParticipantsSnapshot()) {
            throw new BusinessException("PARTICIPANT_BELOW_MIN", "Participant count is below the approved minimum");
        }
        if (match.getMaximumParticipantsSnapshot() != null
                && match.getParticipantCount() > match.getMaximumParticipantsSnapshot()) {
            throw new BusinessException("PARTICIPANT_ABOVE_MAX", "Participant count is above the approved maximum");
        }
    }

    private void validateSessions(List<CourseMatchSessionEntity> matchSessions) {
        if (matchSessions.isEmpty()) {
            throw new BusinessException("MATCH_NOT_READY", "Course match has no sessions");
        }
        Instant now = Instant.now();
        for (CourseMatchSessionEntity session : matchSessions) {
            if (!session.getScheduledStartAt().isAfter(now)) {
                throw new BusinessException("BOOKING_TIME_NOT_FUTURE", "Every course match session must be in the future");
            }
            if (session.getVenueSnapshotName() == null || session.getVenueSnapshotName().isBlank()
                    || session.getVenueFingerprint() == null || session.getVenueFingerprint().length() != 64) {
                throw new BusinessException("MATCH_NOT_READY", "Every course match session requires a complete venue snapshot");
            }
        }
        List<CourseMatchSessionEntity> sorted = matchSessions.stream()
                .sorted(Comparator.comparing(CourseMatchSessionEntity::getScheduledStartAt)).toList();
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).getScheduledStartAt().isBefore(sorted.get(i - 1).getScheduledEndAt())) {
                throw new BusinessException("SCHEDULE_CONFLICT", "Course match sessions overlap each other");
            }
        }
    }

    private void lockMatchAssignments(UUID courseMatchId) {
        jdbc.queryForList("""
                select c.id
                from course_match_session_coaches c
                join course_match_sessions s on s.id = c.course_match_session_id
                where s.course_match_id = ?
                order by s.session_index, c.assignment_order, c.id
                for update of c
                """, UUID.class, courseMatchId);
    }

    private Map<UUID, CoachIdentity> validateAcceptedCoaches(
            UUID organizationId,
            List<CourseMatchSessionEntity> matchSessions,
            List<CourseMatchSessionCoachEntity> matchAssignments) {
        Map<UUID, List<CourseMatchSessionCoachEntity>> bySession = new HashMap<>();
        for (CourseMatchSessionCoachEntity assignment : matchAssignments) {
            if (assignment.getStatus() == CourseMatchCoachStatus.INVITED
                    || assignment.getStatus() == CourseMatchCoachStatus.ACCEPTED) {
                bySession.computeIfAbsent(assignment.getCourseMatchSessionId(), ignored -> new ArrayList<>())
                        .add(assignment);
            }
        }

        Map<UUID, CoachIdentity> identities = new HashMap<>();
        for (CourseMatchSessionEntity session : matchSessions) {
            List<CourseMatchSessionCoachEntity> active = bySession.getOrDefault(session.getId(), List.of());
            if (active.isEmpty() || active.stream().anyMatch(a -> a.getStatus() != CourseMatchCoachStatus.ACCEPTED)) {
                throw new BusinessException("MATCH_NOT_READY", "All active coach invitations must be accepted");
            }
            for (CourseMatchSessionCoachEntity assignment : active) {
                CoachProfileEntity coach = coachProfiles.findById(assignment.getCoachProfileId())
                        .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Coach profile was not found"));
                if (!organizationId.equals(coach.getOrganizationId())
                        || coach.getApprovalStatus() != CoachProfileApprovalStatus.APPROVED) {
                    throw new BusinessException("COACH_NOT_APPROVED", "Assigned coach is no longer approved");
                }
                identities.put(coach.getId(), new CoachIdentity(coach.getId(), coach.getUserId()));
            }
        }
        return identities;
    }

    private PriceSnapshotRow lockConfirmedPrice(UUID courseMatchId) {
        return jdbc.query("""
                select id, billing_mode, currency, total_amount, pricing_fingerprint
                from course_match_price_snapshots
                where course_match_id = ? and status = 'CONFIRMED'
                for update
                """, (rs, rowNum) -> new PriceSnapshotRow(
                rs.getObject("id", UUID.class), rs.getString("billing_mode"), rs.getString("currency"),
                money(rs.getBigDecimal("total_amount")), rs.getString("pricing_fingerprint")), courseMatchId)
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException("MATCH_NOT_READY", "Confirmed match pricing is required"));
    }

    private List<MatchPriceItemRow> loadMatchPriceItems(UUID snapshotId) {
        return jdbc.query("""
                select id, course_match_session_id, item_type, description, quantity, unit_amount,
                       line_amount, source_reference_type, source_reference_id, sort_order
                from course_match_price_snapshot_items
                where course_match_price_snapshot_id = ?
                order by sort_order, id
                """, (rs, rowNum) -> matchPriceItem(rs), snapshotId);
    }

    private MatchPriceItemRow matchPriceItem(ResultSet rs) throws SQLException {
        return new MatchPriceItemRow(
                rs.getObject("id", UUID.class), rs.getObject("course_match_session_id", UUID.class),
                rs.getString("item_type"), rs.getString("description"), rs.getBigDecimal("quantity"),
                rs.getBigDecimal("unit_amount"), moneySigned(rs.getBigDecimal("line_amount")),
                rs.getString("source_reference_type"), rs.getObject("source_reference_id", UUID.class),
                rs.getInt("sort_order"));
    }

    private void validateMatchPriceTotal(PriceSnapshotRow snapshot, List<MatchPriceItemRow> items) {
        BigDecimal itemTotal = items.stream().map(MatchPriceItemRow::lineAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        if (itemTotal.compareTo(snapshot.totalAmount()) != 0) {
            throw new BusinessException("PRICE_CHANGED_RECALC_REQUIRED", "Confirmed match price items are inconsistent");
        }
    }

    private ClaimRow lockSourceClaimIfAny(LessonRequestEntity request) {
        if (request.getSelectedAvailabilityProposalId() == null) return null;
        return jdbc.query("""
                select id, status, converted_course_match_id
                from coach_availability_claims
                where lesson_request_id = ? and coach_availability_proposal_id = ?
                for update
                """, (rs, rowNum) -> new ClaimRow(
                rs.getObject("id", UUID.class), rs.getString("status"),
                rs.getObject("converted_course_match_id", UUID.class)),
                request.getId(), request.getSelectedAvailabilityProposalId()).stream().findFirst()
                .orElseThrow(() -> new BusinessException("MATCH_NOT_READY", "Selected availability claim is missing"));
    }

    private void insertCourse(UUID courseId, CourseMatchEntity match, LessonRequestEntity request, UUID actorUserId) {
        jdbc.update("""
                insert into courses(
                    id, organization_id, course_no, source_match_id, created_by_user_id,
                    course_type, schedule_type, billing_mode, skill_level,
                    expected_participant_count, guest_participant_count,
                    minimum_participants, maximum_participants, total_session_count,
                    status, activated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', now())
                """, courseId, match.getOrganizationId(), businessNo("C", courseId), match.getId(), actorUserId,
                request.getLessonType(), request.getScheduleType(), request.getBillingMode(), request.getSkillLevel(),
                match.getParticipantCount(), request.getGuestParticipantCount(),
                match.getMinimumParticipantsSnapshot(), match.getMaximumParticipantsSnapshot(),
                request.getRequestedSessionCount());
    }

    private void insertCourseApproval(UUID courseId, UUID organizationId, UUID actorUserId, UUID matchId) {
        jdbc.update("""
                insert into course_approvals(
                    id, organization_id, course_id, course_version, decision, reason, decided_by, decided_at)
                values (?, ?, ?, 0, 'APPROVED', ?, ?, now())
                """, UUID.randomUUID(), organizationId, courseId,
                "Confirmed from CourseMatch " + matchId, actorUserId);
    }

    private void insertPrimaryContact(UUID courseId, UUID organizationId, UUID requesterUserId, UUID actorUserId) {
        jdbc.update("""
                insert into course_contact_assignments(
                    id, organization_id, course_id, user_id, effective_from, assigned_by)
                values (?, ?, ?, ?, now(), ?)
                """, UUID.randomUUID(), organizationId, courseId, requesterUserId, actorUserId);
        // Contact and participant are intentionally separate concepts. Current LessonRequest
        // persistence has counts but no canonical registered-participant user IDs, so S3.4
        // does not create fake CourseMembership/Enrollment rows for the requester.
    }

    private Map<UUID, FormalSession> insertFormalSessions(
            UUID courseId,
            CourseMatchEntity match,
            LessonRequestEntity request,
            List<CourseMatchSessionEntity> matchSessions) {
        Map<UUID, FormalSession> result = new LinkedHashMap<>();
        for (CourseMatchSessionEntity source : matchSessions) {
            UUID formalId = UUID.randomUUID();
            jdbc.update("""
                    insert into course_sessions(
                        id, organization_id, course_id, sequence_no,
                        scheduled_start_at, scheduled_end_at,
                        expected_participant_count, guest_participant_count, status)
                    values (?, ?, ?, ?, ?, ?, ?, ?, 'SCHEDULED')
                    """, formalId, match.getOrganizationId(), courseId, source.getSessionIndex(),
                    Timestamp.from(source.getScheduledStartAt()), Timestamp.from(source.getScheduledEndAt()),
                    match.getParticipantCount(), request.getGuestParticipantCount());
            result.put(source.getId(), new FormalSession(
                    formalId, source.getId(), source.getSessionIndex(), source.getScheduledStartAt(), source.getScheduledEndAt()));
        }
        return result;
    }

    private void insertFormalCoachAssignmentsAndReservations(
            CourseMatchEntity match,
            List<CourseMatchSessionCoachEntity> sourceAssignments,
            Map<UUID, CoachIdentity> coachIdentities,
            Map<UUID, FormalSession> formalSessions,
            UUID actorUserId) {
        for (CourseMatchSessionCoachEntity source : sourceAssignments) {
            if (source.getStatus() != CourseMatchCoachStatus.ACCEPTED) continue;
            FormalSession target = formalSessions.get(source.getCourseMatchSessionId());
            if (target == null) throw new IllegalStateException("Formal session mapping is missing");
            CoachIdentity coach = coachIdentities.get(source.getCoachProfileId());
            if (coach == null) throw new BusinessException("COACH_NOT_APPROVED", "Coach identity is unavailable");

            jdbc.update("""
                    insert into session_coach_assignments(
                        id, organization_id, course_session_id, coach_profile_id,
                        source_type, status, is_primary, invited_at, responded_at,
                        response_reason, assigned_by)
                    values (?, ?, ?, ?, 'MATCHED', 'ACCEPTED', ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), match.getOrganizationId(), target.id(), coach.coachProfileId(),
                    source.getAssignmentOrder() == 1,
                    timestamp(source.getInvitationSentAt()), timestamp(source.getRespondedAt()),
                    source.getResponseNote(), actorUserId);

            insertScheduleReservation(match.getOrganizationId(), coach.userId(), target, "COACH");
        }
    }

    private void insertScheduleReservation(UUID organizationId, UUID userId, FormalSession session, String role) {
        if (Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(
                    select 1 from schedule_reservations
                    where organization_id = ? and user_id = ?
                      and status in ('HELD','CONFIRMED')
                      and reserved_period && tstzrange(?::timestamptz, ?::timestamptz, '[)')
                )
                """, Boolean.class, organizationId, userId,
                Timestamp.from(session.startAt()), Timestamp.from(session.endAt())))) {
            throw new BusinessException("SCHEDULE_CONFLICT", "Coach has an overlapping confirmed schedule");
        }
        try {
            jdbc.update("""
                    insert into schedule_reservations(
                        id, organization_id, user_id, course_session_id,
                        reservation_role, reserved_period, status)
                    values (?, ?, ?, ?, ?, tstzrange(?::timestamptz, ?::timestamptz, '[)'), 'CONFIRMED')
                    """, UUID.randomUUID(), organizationId, userId, session.id(), role,
                    Timestamp.from(session.startAt()), Timestamp.from(session.endAt()));
        } catch (DataIntegrityViolationException conflict) {
            throw new BusinessException("SCHEDULE_CONFLICT", "Schedule reservation overlaps another active reservation");
        }
    }

    private void insertVenueArrangements(
            CourseMatchEntity match,
            LessonRequestEntity request,
            List<CourseMatchSessionEntity> matchSessions,
            Map<UUID, FormalSession> formalSessions,
            List<MatchPriceItemRow> priceItems,
            UUID actorUserId) {
        Map<UUID, BigDecimal> venueCostBySession = new HashMap<>();
        for (MatchPriceItemRow item : priceItems) {
            if ("VENUE".equals(item.itemType()) && item.courseMatchSessionId() != null) {
                venueCostBySession.merge(item.courseMatchSessionId(), item.lineAmount(), BigDecimal::add);
            }
        }
        for (CourseMatchSessionEntity source : matchSessions) {
            FormalSession target = formalSessions.get(source.getId());
            BigDecimal cost = money(venueCostBySession.getOrDefault(source.getId(), BigDecimal.ZERO));
            boolean chargedToContact = cost.signum() > 0;
            jdbc.update("""
                    insert into session_venue_arrangements(
                        id, organization_id, course_session_id, source_type, venue_id,
                        venue_name_snapshot, address_snapshot, cost_amount,
                        cost_payer_type, cost_payer_user_id, status, confirmed_by, confirmed_at)
                    values (?, ?, ?, 'COMMITTEE', ?, ?, ?, ?, ?, ?, 'CONFIRMED', ?, now())
                    """, UUID.randomUUID(), match.getOrganizationId(), target.id(), source.getVenueSnapshotId(),
                    source.getVenueSnapshotName(), source.getVenueSnapshotAddress(), cost,
                    chargedToContact ? "STUDENT" : "NONE",
                    chargedToContact ? request.getRequesterUserId() : null,
                    actorUserId);
        }
    }

    private List<FormalPriceSnapshot> insertFormalPriceSnapshots(
            CourseMatchEntity match,
            List<CourseMatchSessionEntity> matchSessions,
            Map<UUID, FormalSession> formalSessions,
            PriceSnapshotRow confirmedPrice,
            List<MatchPriceItemRow> matchPriceItems,
            UUID actorUserId) {
        Map<UUID, List<MatchPriceItemRow>> itemsBySession = new LinkedHashMap<>();
        for (CourseMatchSessionEntity session : matchSessions) itemsBySession.put(session.getId(), new ArrayList<>());
        UUID firstSessionId = matchSessions.getFirst().getId();
        for (MatchPriceItemRow item : matchPriceItems) {
            UUID sourceSessionId = item.courseMatchSessionId() == null ? firstSessionId : item.courseMatchSessionId();
            List<MatchPriceItemRow> bucket = itemsBySession.get(sourceSessionId);
            if (bucket == null) throw new BusinessException("PRICE_CHANGED_RECALC_REQUIRED", "Price item references an unknown match session");
            bucket.add(item);
        }

        List<FormalPriceSnapshot> result = new ArrayList<>();
        BigDecimal aggregate = BigDecimal.ZERO;
        for (CourseMatchSessionEntity sourceSession : matchSessions) {
            FormalSession formalSession = formalSessions.get(sourceSession.getId());
            List<MatchPriceItemRow> items = itemsBySession.get(sourceSession.getId());
            BigDecimal tuition = sum(items, "TUITION");
            BigDecimal venue = sum(items, "VENUE");
            BigDecimal adjustment = sum(items, "ADJUSTMENT");
            BigDecimal total = tuition.add(venue).add(adjustment).setScale(2, RoundingMode.HALF_UP);
            if (total.signum() < 0) {
                throw new BusinessException("PRICE_CHANGED_RECALC_REQUIRED", "Formal session price cannot be negative");
            }
            UUID formalPriceId = UUID.randomUUID();
            String trace = "{\"sourceMatchPriceSnapshotId\":\"" + confirmedPrice.id() + "\"}";
            jdbc.update("""
                    insert into session_price_snapshots(
                        id, organization_id, course_session_id, version_no, status, currency,
                        tuition_amount, venue_fee, other_adjustment, total_receivable, rule_trace,
                        source_match_price_snapshot_id, confirmed_by, confirmed_at, created_by)
                    values (?, ?, ?, 1, 'CONFIRMED', ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, now(), ?)
                    """, formalPriceId, match.getOrganizationId(), formalSession.id(), confirmedPrice.currency(),
                    tuition, venue, adjustment, total, trace, confirmedPrice.id(), actorUserId, actorUserId);

            short sortOrder = 0;
            for (MatchPriceItemRow item : items) {
                FormalLine line = formalLine(item);
                jdbc.update("""
                        insert into session_price_snapshot_items(
                            id, price_snapshot_id, item_type, description,
                            quantity, unit_amount, line_amount,
                            source_reference_type, source_reference_id, sort_order)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, UUID.randomUUID(), formalPriceId, line.itemType(), line.description(),
                        line.quantity(), line.unitAmount(), item.lineAmount(),
                        line.sourceReferenceType(), line.sourceReferenceId(), sortOrder++);
            }
            aggregate = aggregate.add(total);
            result.add(new FormalPriceSnapshot(formalPriceId, formalSession.id(), total));
        }

        if (aggregate.setScale(2, RoundingMode.HALF_UP).compareTo(confirmedPrice.totalAmount()) != 0) {
            throw new BusinessException("PRICE_CHANGED_RECALC_REQUIRED",
                    "Formal session price allocation does not equal confirmed match total");
        }
        return result;
    }

    private BigDecimal sum(List<MatchPriceItemRow> items, String type) {
        return items.stream().filter(item -> type.equals(item.itemType())).map(MatchPriceItemRow::lineAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private FormalLine formalLine(MatchPriceItemRow item) {
        String formalType = switch (item.itemType()) {
            case "TUITION" -> "TUITION";
            case "VENUE" -> "VENUE_FEE";
            case "ADJUSTMENT" -> "MANUAL_ADJUSTMENT";
            default -> throw new BusinessException("PRICE_CHANGED_RECALC_REQUIRED", "Unsupported match price item type");
        };
        BigDecimal quantity = item.quantity() == null ? null : item.quantity().setScale(2, RoundingMode.HALF_UP);
        BigDecimal unit = item.unitAmount() == null ? null : item.unitAmount().setScale(2, RoundingMode.HALF_UP);
        if (quantity == null || quantity.signum() <= 0 || unit == null
                || quantity.multiply(unit).setScale(2, RoundingMode.HALF_UP).compareTo(item.lineAmount()) != 0) {
            quantity = BigDecimal.ONE.setScale(2);
            unit = item.lineAmount();
        }
        String description = Objects.toString(item.description(), item.itemType());
        if (description.length() > 300) description = description.substring(0, 300);
        String sourceType = item.sourceReferenceType();
        UUID sourceId = item.sourceReferenceId();
        if (sourceType != null && sourceType.length() > 30) {
            sourceType = "MATCH_PRICE_ITEM";
            sourceId = item.id();
        }
        return new FormalLine(formalType, description, quantity, unit, sourceType, sourceId);
    }

    private UUID insertReceivable(
            UUID courseId,
            UUID organizationId,
            LessonRequestEntity request,
            PriceSnapshotRow confirmedPrice,
            List<FormalPriceSnapshot> formalPrices) {
        UUID receivableId = UUID.randomUUID();
        jdbc.update("""
                insert into receivables(
                    id, organization_id, receivable_no, course_id, payer_user_id,
                    billing_mode, currency, total_amount, adjusted_amount,
                    paid_amount, refunded_amount, balance_amount, status)
                values (?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0, ?, 'OPEN')
                """, receivableId, organizationId, businessNo("R", receivableId), courseId,
                request.getRequesterUserId(), request.getBillingMode(), confirmedPrice.currency(),
                confirmedPrice.totalAmount(), confirmedPrice.totalAmount());
        short sortOrder = 0;
        for (FormalPriceSnapshot price : formalPrices) {
            jdbc.update("""
                    insert into receivable_items(
                        id, receivable_id, course_session_id, enrollment_id,
                        price_snapshot_id, amount, status, sort_order)
                    values (?, ?, ?, null, ?, ?, 'OPEN', ?)
                    """, UUID.randomUUID(), receivableId, price.courseSessionId(), price.id(), price.total(), sortOrder++);
        }
        BigDecimal itemTotal = jdbc.queryForObject(
                "select coalesce(sum(amount),0) from receivable_items where receivable_id=?",
                BigDecimal.class, receivableId);
        if (money(itemTotal).compareTo(confirmedPrice.totalAmount()) != 0) {
            throw new IllegalStateException("Receivable items do not equal the confirmed match total");
        }
        return receivableId;
    }

    private void convertSourceClaimIfAny(LessonRequestEntity request, CourseMatchEntity match, ClaimRow claim) {
        if (request.getSelectedAvailabilityProposalId() == null) return;
        if (claim == null || !"ACTIVE".equals(claim.status()) || claim.convertedCourseMatchId() != null) {
            throw new BusinessException("AVAILABILITY_ALREADY_CLAIMED", "Availability claim is no longer active");
        }
        if (jdbc.update("""
                update coach_availability_claims
                set status='CONVERTED', converted_course_match_id=?, updated_at=now()
                where id=? and status='ACTIVE' and converted_course_match_id is null
                """, match.getId(), claim.id()) != 1) {
            throw new BusinessException("CONCURRENT_MODIFICATION", "Availability claim changed concurrently");
        }
        if (jdbc.update("""
                update coach_availability_proposals
                set status='MATCHED', matched_at=now(), updated_at=now()
                where id=? and status='APPROVED'
                """, request.getSelectedAvailabilityProposalId()) != 1) {
            throw new BusinessException("MATCH_NOT_READY", "Selected availability is no longer approved");
        }
    }

    private ConfirmationResult loadResult(CourseMatchEntity match, UUID courseId) {
        CourseRow course = jdbc.query("""
                select id, status from courses where id=? and source_match_id=?
                """, (rs, rowNum) -> new CourseRow(rs.getObject("id", UUID.class), rs.getString("status")),
                courseId, match.getId()).stream().findFirst()
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Idempotent course result was not found"));
        List<UUID> sessionIds = jdbc.queryForList(
                "select id from course_sessions where course_id=? order by sequence_no", UUID.class, courseId);
        List<UUID> receivableIds = jdbc.queryForList(
                "select id from receivables where course_id=? order by created_at,id", UUID.class, courseId);
        return new ConfirmationResult(match.getId(), match.getStatus().name(), course.id(), course.status(),
                sessionIds, receivableIds);
    }

    private void requireCommittee(AuthenticatedPrincipal actor, UUID organizationId) {
        if (!identity.isAuthorizedForOrganization(actor, RoleCode.COMMITTEE, organizationId)) {
            throw new BusinessException("AUTH_FORBIDDEN", "Committee or platform administrator permission is required");
        }
    }

    private String businessNo(String prefix, UUID id) {
        return prefix + "-" + id.toString().replace("-", "").substring(0, 24).toUpperCase();
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private BigDecimal money(BigDecimal value) {
        BigDecimal normalized = value == null ? BigDecimal.ZERO : value;
        if (normalized.signum() < 0) throw new BusinessException("VALIDATION_FAILED", "Amount cannot be negative");
        return normalized.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal moneySigned(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    public record ConfirmCommand(boolean confirm) {}
    public record ConfirmationResult(
            UUID courseMatchId, String courseMatchStatus, UUID courseId, String courseStatus,
            List<UUID> sessionIds, List<UUID> receivableIds) {}

    private record CoachIdentity(UUID coachProfileId, UUID userId) {}
    private record PriceSnapshotRow(UUID id, String billingMode, String currency, BigDecimal totalAmount,
            String pricingFingerprint) {}
    private record MatchPriceItemRow(
            UUID id, UUID courseMatchSessionId, String itemType, String description,
            BigDecimal quantity, BigDecimal unitAmount, BigDecimal lineAmount,
            String sourceReferenceType, UUID sourceReferenceId, int sortOrder) {}
    private record ClaimRow(UUID id, String status, UUID convertedCourseMatchId) {}
    private record FormalSession(UUID id, UUID sourceMatchSessionId, short sequenceNo, Instant startAt, Instant endAt) {}
    private record FormalPriceSnapshot(UUID id, UUID courseSessionId, BigDecimal total) {}
    private record FormalLine(String itemType, String description, BigDecimal quantity, BigDecimal unitAmount,
            String sourceReferenceType, UUID sourceReferenceId) {}
    private record CourseRow(UUID id, String status) {}
}
