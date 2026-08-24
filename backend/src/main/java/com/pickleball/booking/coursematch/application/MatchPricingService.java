package com.pickleball.booking.coursematch.application;

import com.pickleball.booking.coach.domain.CoachProfileApprovalStatus;
import com.pickleball.booking.coach.infrastructure.CoachProfileEntity;
import com.pickleball.booking.coach.infrastructure.CoachProfileRepository;
import com.pickleball.booking.coursematch.domain.CourseMatchCoachStatus;
import com.pickleball.booking.coursematch.domain.VenueSnapshotType;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class MatchPricingService {
    private static final String CURRENCY = "TWD";
    private static final String IDEMPOTENCY_OPERATION = "COURSE_MATCH_PRICING_CONFIRMATION";

    private final IdentityService identity;
    private final CourseMatchRepository matches;
    private final CourseMatchSessionRepository sessions;
    private final CourseMatchSessionCoachRepository assignments;
    private final CoachProfileRepository coachProfiles;
    private final LessonRequestRepository lessonRequests;
    private final IdempotencyService idempotency;
    private final AuditOutboxService audit;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public MatchPricingService(
            IdentityService identity,
            CourseMatchRepository matches,
            CourseMatchSessionRepository sessions,
            CourseMatchSessionCoachRepository assignments,
            CoachProfileRepository coachProfiles,
            LessonRequestRepository lessonRequests,
            IdempotencyService idempotency,
            AuditOutboxService audit,
            JdbcTemplate jdbc,
            ObjectMapper json) {
        this.identity = identity;
        this.matches = matches;
        this.sessions = sessions;
        this.assignments = assignments;
        this.coachProfiles = coachProfiles;
        this.lessonRequests = lessonRequests;
        this.idempotency = idempotency;
        this.audit = audit;
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public PricingPreview preview(AuthenticatedPrincipal actor, UUID courseMatchId) {
        identity.requireActiveUser(actor.userId());
        CourseMatchEntity match = matches.findById(courseMatchId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Course match was not found"));
        requireCommittee(actor, match.getOrganizationId());
        match.requireDraft();
        LessonRequestEntity request = approvedRequest(match);
        return calculate(match, request, false);
    }

    @Transactional
    public PriceSnapshot confirm(
            AuthenticatedPrincipal actor,
            UUID courseMatchId,
            String idempotencyKey,
            ConfirmPricingCommand command) {
        identity.requireActiveUser(actor.userId());
        CourseMatchEntity match = matches.findLockedById(courseMatchId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Course match was not found"));
        requireCommittee(actor, match.getOrganizationId());
        match.requireDraft();
        validateConfirmationCommand(command);

        String normalizedCurrency = command.currency().trim().toUpperCase(Locale.ROOT);
        BigDecimal acceptedTotal = money(command.acceptedTotalAmount());
        String requestIdentity = courseMatchId + "|" + acceptedTotal.toPlainString() + "|"
                + normalizedCurrency + "|" + command.pricingFingerprint().trim().toLowerCase(Locale.ROOT) + "|"
                + Objects.toString(clean(command.confirmationNote()), "");
        var idempotencyRecord = idempotency.begin(
                match.getOrganizationId(), actor.userId(), IDEMPOTENCY_OPERATION, idempotencyKey, requestIdentity);
        if (idempotencyRecord.getResultResourceId() != null) {
            return loadSnapshot(idempotencyRecord.getResultResourceId())
                    .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Confirmed price snapshot was not found"));
        }

        if (confirmedSnapshotId(match.getId()).isPresent()) {
            throw new BusinessException("STATE_TRANSITION_INVALID", "Pricing is already confirmed for this match");
        }

        LessonRequestEntity request = approvedRequest(match);
        PricingPreview current = calculate(match, request, true);
        if (!CURRENCY.equals(normalizedCurrency)) {
            throw new BusinessException("VALIDATION_FAILED", "MVP pricing currency must be TWD");
        }
        if (!current.pricingFingerprint().equalsIgnoreCase(command.pricingFingerprint().trim())
                || current.totalAmount().compareTo(acceptedTotal) != 0) {
            throw new BusinessException("PRICE_CHANGED_RECALC_REQUIRED",
                    "Pricing inputs changed; preview and confirm the latest price again");
        }

        int versionNo = jdbc.queryForObject(
                "select coalesce(max(version_no), 0) + 1 from course_match_price_snapshots where course_match_id = ?",
                Integer.class, match.getId());
        UUID snapshotId = UUID.randomUUID();
        String traceJson = traceJson(current);
        jdbc.update("""
                insert into course_match_price_snapshots(
                    id, organization_id, course_match_id, version_no, status,
                    billing_mode, currency, total_amount, pricing_fingerprint, rule_trace,
                    confirmation_note, confirmed_by, confirmed_at, created_by)
                values (?, ?, ?, ?, 'CONFIRMED', ?, ?, ?, ?, cast(? as jsonb), ?, ?, now(), ?)
                """, snapshotId, match.getOrganizationId(), match.getId(), versionNo,
                request.getBillingMode(), CURRENCY, current.totalAmount(), current.pricingFingerprint(), traceJson,
                clean(command.confirmationNote()), actor.userId(), actor.userId());

        int sortOrder = 0;
        for (PriceItem item : current.breakdown()) {
            jdbc.update("""
                    insert into course_match_price_snapshot_items(
                        id, course_match_price_snapshot_id, course_match_session_id,
                        item_type, description, quantity, unit_amount, line_amount,
                        source_reference_type, source_reference_id, sort_order)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), snapshotId, item.courseMatchSessionId(), item.itemType(), item.description(),
                    item.quantity(), item.unitAmount(), item.lineAmount(), item.sourceReferenceType(),
                    item.sourceReferenceId(), sortOrder++);
        }

        BigDecimal persistedItemTotal = jdbc.queryForObject(
                "select coalesce(sum(line_amount), 0) from course_match_price_snapshot_items where course_match_price_snapshot_id = ?",
                BigDecimal.class, snapshotId);
        if (money(persistedItemTotal).compareTo(current.totalAmount()) != 0) {
            throw new IllegalStateException("Persisted pricing item total does not match snapshot total");
        }

        idempotencyRecord.complete("CourseMatchPriceSnapshot", snapshotId, 201);
        audit.record(match.getOrganizationId(), actor.userId(), "COURSE_MATCH_PRICING_CONFIRMED",
                "CourseMatchPriceSnapshot", snapshotId, null);
        return loadSnapshot(snapshotId).orElseThrow();
    }

    private PricingPreview calculate(CourseMatchEntity match, LessonRequestEntity request, boolean requireAccepted) {
        List<CourseMatchSessionEntity> sessionList = sessions.findByCourseMatchIdOrderBySessionIndexAsc(match.getId());
        if (sessionList.isEmpty()) {
            throw new BusinessException("MATCH_NOT_READY", "Course match has no sessions to price");
        }
        if (sessionList.stream().anyMatch(s -> !s.getScheduledStartAt().isAfter(Instant.now()))) {
            throw new BusinessException("BOOKING_TIME_NOT_FUTURE", "All match sessions must remain in the future");
        }

        List<UUID> sessionIds = sessionList.stream().map(CourseMatchSessionEntity::getId).toList();
        List<CourseMatchSessionCoachEntity> allAssignments = assignments
                .findByCourseMatchSessionIdInOrderByCourseMatchSessionIdAscAssignmentOrderAsc(sessionIds);
        Map<UUID, List<CourseMatchSessionCoachEntity>> activeBySession = new HashMap<>();
        for (CourseMatchSessionCoachEntity assignment : allAssignments) {
            if (assignment.getStatus() == CourseMatchCoachStatus.INVITED
                    || assignment.getStatus() == CourseMatchCoachStatus.ACCEPTED) {
                activeBySession.computeIfAbsent(assignment.getCourseMatchSessionId(), ignored -> new ArrayList<>())
                        .add(assignment);
            }
        }

        List<PriceItem> items = new ArrayList<>();
        List<FingerprintSession> fingerprintSessions = new ArrayList<>();
        Set<UUID> chargedFlatRules = new HashSet<>();
        Set<UUID> validatedCoachProfiles = new HashSet<>();

        for (CourseMatchSessionEntity session : sessionList) {
            List<CourseMatchSessionCoachEntity> active = activeBySession.getOrDefault(session.getId(), List.of()).stream()
                    .sorted(Comparator.comparingInt(CourseMatchSessionCoachEntity::getAssignmentOrder))
                    .toList();
            if (active.isEmpty()) {
                throw new BusinessException("MATCH_NOT_READY", "Every session needs an active coach assignment before pricing");
            }
            if (requireAccepted && active.stream().anyMatch(a -> a.getStatus() != CourseMatchCoachStatus.ACCEPTED)) {
                throw new BusinessException("MATCH_NOT_READY", "All active coach invitations must be accepted before pricing confirmation");
            }
            for (CourseMatchSessionCoachEntity assignment : active) {
                if (validatedCoachProfiles.add(assignment.getCoachProfileId())) {
                    requireApprovedCoach(match.getOrganizationId(), assignment.getCoachProfileId());
                }
            }

            CourseMatchSessionCoachEntity primary = active.getFirst();
            PricingRule rule = selectPricingRule(match.getOrganizationId(), primary.getCoachProfileId(),
                    request.getLessonType(), request.getSkillLevel(), match.getParticipantCount());
            VenueCost venue = venueCost(match.getOrganizationId(), session);

            BigDecimal tuitionQuantity;
            BigDecimal tuitionLine;
            boolean chargeTuition = true;
            switch (rule.pricingUnit()) {
                case "PER_SESSION" -> {
                    tuitionQuantity = BigDecimal.ONE;
                    tuitionLine = rule.baseAmount();
                }
                case "PER_PERSON" -> {
                    tuitionQuantity = BigDecimal.valueOf(match.getParticipantCount());
                    tuitionLine = rule.baseAmount().multiply(tuitionQuantity);
                }
                case "FLAT" -> {
                    tuitionQuantity = BigDecimal.ONE;
                    tuitionLine = rule.baseAmount();
                    chargeTuition = chargedFlatRules.add(rule.id());
                }
                default -> throw new IllegalStateException("Unsupported pricing unit " + rule.pricingUnit());
            }
            tuitionLine = money(tuitionLine);
            if (chargeTuition) {
                items.add(new PriceItem(session.getId(), "TUITION", rule.name(), tuitionQuantity,
                        rule.baseAmount(), tuitionLine, "PRICING_RULE", rule.id()));
            }
            if (venue.amount().signum() > 0) {
                items.add(new PriceItem(session.getId(), "VENUE", session.getVenueSnapshotName(), BigDecimal.ONE,
                        venue.amount(), venue.amount(), venue.sourceType(), venue.sourceId()));
            }

            List<AssignmentFingerprint> coachFingerprint = active.stream()
                    .map(a -> new AssignmentFingerprint(a.getCoachProfileId(), a.getAssignmentOrder()))
                    .toList();
            fingerprintSessions.add(new FingerprintSession(
                    session.getId(), session.getSessionIndex(), session.getScheduledStartAt(), session.getScheduledEndAt(),
                    session.getVenueFingerprint(), venue.amount(), primary.getCoachProfileId(),
                    rule.id(), rule.version(), rule.baseAmount(), rule.pricingUnit(), coachFingerprint));
        }

        BigDecimal total = items.stream().map(PriceItem::lineAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        total = money(total);
        String fingerprint = pricingFingerprint(match, request, fingerprintSessions);
        return new PricingPreview(match.getId(), CURRENCY, request.getBillingMode(), total, List.copyOf(items), fingerprint);
    }

    private PricingRule selectPricingRule(
            UUID organizationId, UUID coachProfileId, String courseType, String skillLevel, short participantCount) {
        List<PricingRule> rules = jdbc.query("""
                select id, name, priority, coach_profile_id, course_type, skill_level,
                       min_participants, max_participants, base_amount, pricing_unit, version
                from pricing_rules
                where organization_id = ?
                  and status = 'ACTIVE'
                  and deleted_at is null
                  and active_from <= now()
                  and (active_to is null or active_to > now())
                  and (coach_profile_id is null or coach_profile_id = ?)
                  and (course_type is null or course_type = ?)
                  and (skill_level is null or skill_level = ?)
                  and (min_participants is null or min_participants <= ?)
                  and (max_participants is null or max_participants >= ?)
                order by priority asc,
                         (coach_profile_id is not null) desc,
                         (course_type is not null) desc,
                         (skill_level is not null) desc,
                         id asc
                limit 1
                """, (rs, rowNum) -> pricingRule(rs), organizationId, coachProfileId, courseType,
                skillLevel, participantCount, participantCount);
        return rules.stream().findFirst().orElseThrow(() -> new BusinessException(
                "MATCH_NOT_READY", "No active pricing rule matches the current course match"));
    }

    private PricingRule pricingRule(ResultSet rs) throws SQLException {
        return new PricingRule(
                rs.getObject("id", UUID.class), rs.getString("name"), rs.getInt("priority"),
                rs.getObject("coach_profile_id", UUID.class), rs.getString("course_type"), rs.getString("skill_level"),
                (Short) rs.getObject("min_participants"), (Short) rs.getObject("max_participants"),
                money(rs.getBigDecimal("base_amount")), rs.getString("pricing_unit"), rs.getLong("version"));
    }

    private VenueCost venueCost(UUID organizationId, CourseMatchSessionEntity session) {
        if (session.getVenueSnapshotType() != VenueSnapshotType.VENUE) {
            return new VenueCost(BigDecimal.ZERO.setScale(2), "EXTERNAL_VENUE", null);
        }
        return jdbc.query("""
                select organization_id, default_cost_amount, status from venues where id = ?
                """, (rs, rowNum) -> {
            UUID venueOrg = rs.getObject("organization_id", UUID.class);
            if (!organizationId.equals(venueOrg)) {
                throw new BusinessException("ORG_SCOPE_DENIED", "Venue is outside the organization");
            }
            if (!"ACTIVE".equals(rs.getString("status"))) {
                throw new BusinessException("MATCH_NOT_READY", "Venue is no longer active");
            }
            return new VenueCost(money(rs.getBigDecimal("default_cost_amount")), "VENUE", session.getVenueSnapshotId());
        }, session.getVenueSnapshotId()).stream().findFirst()
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Venue was not found"));
    }

    private LessonRequestEntity approvedRequest(CourseMatchEntity match) {
        LessonRequestEntity request = lessonRequests.findById(match.getLessonRequestId())
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Lesson request was not found"));
        if (request.getStatus() != LessonRequestStatus.APPROVED) {
            throw new BusinessException("LESSON_REQUEST_NOT_APPROVED", "Lesson request must remain approved for pricing");
        }
        if (!request.getOrganizationId().equals(match.getOrganizationId())) {
            throw new BusinessException("ORG_SCOPE_DENIED", "Lesson request is outside the organization");
        }
        return request;
    }

    private void requireApprovedCoach(UUID organizationId, UUID coachProfileId) {
        CoachProfileEntity coach = coachProfiles.findById(coachProfileId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Coach profile was not found"));
        if (!organizationId.equals(coach.getOrganizationId())
                || coach.getApprovalStatus() != CoachProfileApprovalStatus.APPROVED) {
            throw new BusinessException("COACH_NOT_APPROVED", "Coach is not approved for this organization");
        }
    }

    private void requireCommittee(AuthenticatedPrincipal actor, UUID organizationId) {
        if (!identity.isAuthorizedForOrganization(actor, RoleCode.COMMITTEE, organizationId)) {
            throw new BusinessException("AUTH_FORBIDDEN", "Committee or platform administrator permission is required");
        }
    }

    private Optional<UUID> confirmedSnapshotId(UUID courseMatchId) {
        return jdbc.query("""
                select id from course_match_price_snapshots
                where course_match_id = ? and status = 'CONFIRMED'
                order by version_no desc limit 1
                """, (rs, rowNum) -> rs.getObject("id", UUID.class), courseMatchId).stream().findFirst();
    }

    private Optional<PriceSnapshot> loadSnapshot(UUID snapshotId) {
        return jdbc.query("""
                select id, course_match_id, status, billing_mode, currency, total_amount,
                       pricing_fingerprint, confirmed_by, confirmed_at
                from course_match_price_snapshots where id = ?
                """, (rs, rowNum) -> new PriceSnapshot(
                rs.getObject("id", UUID.class), rs.getObject("course_match_id", UUID.class),
                rs.getString("status"), rs.getString("billing_mode"), money(rs.getBigDecimal("total_amount")),
                rs.getString("currency"), rs.getString("pricing_fingerprint"),
                rs.getObject("confirmed_by", UUID.class), rs.getTimestamp("confirmed_at") == null
                        ? null : rs.getTimestamp("confirmed_at").toInstant()), snapshotId).stream().findFirst();
    }

    private void validateConfirmationCommand(ConfirmPricingCommand command) {
        if (command == null || command.acceptedTotalAmount() == null || command.currency() == null
                || command.currency().isBlank() || command.pricingFingerprint() == null
                || command.pricingFingerprint().isBlank()) {
            throw new BusinessException("VALIDATION_FAILED", "Pricing confirmation is incomplete");
        }
        if (command.pricingFingerprint().trim().length() != 64) {
            throw new BusinessException("VALIDATION_FAILED", "Pricing fingerprint must be SHA-256");
        }
    }

    private String pricingFingerprint(
            CourseMatchEntity match, LessonRequestEntity request, List<FingerprintSession> fingerprintSessions) {
        StringBuilder canonical = new StringBuilder()
                .append(match.getOrganizationId()).append('|')
                .append(match.getId()).append('|')
                .append(request.getBillingMode()).append('|')
                .append(request.getLessonType()).append('|')
                .append(Objects.toString(request.getSkillLevel(), "")).append('|')
                .append(match.getParticipantCount()).append('|');
        for (FingerprintSession session : fingerprintSessions) {
            canonical.append(session.sessionId()).append(':').append(session.sessionIndex()).append(':')
                    .append(session.startAt()).append(':').append(session.endAt()).append(':')
                    .append(session.venueFingerprint()).append(':').append(session.venueCost().toPlainString()).append(':')
                    .append(session.primaryCoachProfileId()).append(':').append(session.pricingRuleId()).append(':')
                    .append(session.pricingRuleVersion()).append(':').append(session.baseAmount().toPlainString()).append(':')
                    .append(session.pricingUnit()).append(':');
            for (AssignmentFingerprint assignment : session.assignments()) {
                canonical.append(assignment.coachProfileId()).append('@').append(assignment.assignmentOrder()).append(',');
            }
            canonical.append('|');
        }
        return sha256(canonical.toString());
    }

    private String traceJson(PricingPreview preview) {
        try {
            Map<String, Object> trace = new LinkedHashMap<>();
            trace.put("pricingFingerprint", preview.pricingFingerprint());
            trace.put("currency", preview.currency());
            trace.put("billingMode", preview.billingMode());
            trace.put("itemCount", preview.breakdown().size());
            trace.put("calculatorVersion", "MATCH_PRICING_V1");
            return json.writeValueAsString(trace);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize pricing trace", exception);
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO.setScale(2);
        if (value.signum() < 0) throw new BusinessException("VALIDATION_FAILED", "Price amounts cannot be negative");
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record ConfirmPricingCommand(
            BigDecimal acceptedTotalAmount, String currency, String pricingFingerprint, String confirmationNote) {}

    public record PricingPreview(
            UUID courseMatchId, String currency, String billingMode, BigDecimal totalAmount,
            List<PriceItem> breakdown, String pricingFingerprint) {}

    public record PriceItem(
            UUID courseMatchSessionId, String itemType, String description, BigDecimal quantity,
            BigDecimal unitAmount, BigDecimal lineAmount, String sourceReferenceType, UUID sourceReferenceId) {}

    public record PriceSnapshot(
            UUID priceSnapshotId, UUID courseMatchId, String status, String billingMode, BigDecimal totalAmount,
            String currency, String pricingFingerprint, UUID confirmedBy, Instant confirmedAt) {}

    private record PricingRule(
            UUID id, String name, int priority, UUID coachProfileId, String courseType, String skillLevel,
            Short minParticipants, Short maxParticipants, BigDecimal baseAmount, String pricingUnit, long version) {}
    private record VenueCost(BigDecimal amount, String sourceType, UUID sourceId) {}
    private record AssignmentFingerprint(UUID coachProfileId, short assignmentOrder) {}
    private record FingerprintSession(
            UUID sessionId, short sessionIndex, Instant startAt, Instant endAt, String venueFingerprint,
            BigDecimal venueCost, UUID primaryCoachProfileId, UUID pricingRuleId, long pricingRuleVersion,
            BigDecimal baseAmount, String pricingUnit, List<AssignmentFingerprint> assignments) {}
}
