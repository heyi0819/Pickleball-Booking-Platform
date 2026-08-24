package com.pickleball.booking.offering.application;

import com.pickleball.booking.coach.domain.CoachProfileApprovalStatus;
import com.pickleball.booking.coach.infrastructure.CoachProfileEntity;
import com.pickleball.booking.coach.infrastructure.CoachProfileRepository;
import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.identity.application.IdentityService;
import com.pickleball.booking.identity.domain.RoleAssignmentStatus;
import com.pickleball.booking.identity.domain.RoleCode;
import com.pickleball.booking.identity.infrastructure.RoleAssignmentRepository;
import com.pickleball.booking.offering.domain.*;
import com.pickleball.booking.offering.infrastructure.CourseOfferingPersistenceAdapter;
import com.pickleball.booking.offering.infrastructure.CourseOfferingPriceSnapshotPersistenceAdapter;
import com.pickleball.booking.offering.infrastructure.OfferingRegistrationPersistenceAdapter;
import com.pickleball.booking.shared.application.AuditOutboxService;
import com.pickleball.booking.shared.application.BusinessException;
import com.pickleball.booking.shared.application.IdempotencyService;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CourseOfferingApplicationService {
    private static final String REGISTER_OPERATION = "COURSE_OFFERING_REGISTRATION";
    private static final String CONFIRM_OPERATION = "COURSE_OFFERING_CONFIRMATION";

    private final IdentityService identity;
    private final RoleAssignmentRepository roles;
    private final CoachProfileRepository coachProfiles;
    private final CourseOfferingPersistenceAdapter offerings;
    private final CourseOfferingPriceSnapshotPersistenceAdapter prices;
    private final OfferingRegistrationPersistenceAdapter registrations;
    private final IdempotencyService idempotency;
    private final AuditOutboxService audit;
    private final JdbcTemplate jdbc;

    public CourseOfferingApplicationService(
            IdentityService identity,
            RoleAssignmentRepository roles,
            CoachProfileRepository coachProfiles,
            CourseOfferingPersistenceAdapter offerings,
            CourseOfferingPriceSnapshotPersistenceAdapter prices,
            OfferingRegistrationPersistenceAdapter registrations,
            IdempotencyService idempotency,
            AuditOutboxService audit,
            JdbcTemplate jdbc) {
        this.identity = identity;
        this.roles = roles;
        this.coachProfiles = coachProfiles;
        this.offerings = offerings;
        this.prices = prices;
        this.registrations = registrations;
        this.idempotency = idempotency;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @Transactional
    public CourseOffering createDraft(AuthenticatedPrincipal actor, UUID organizationId, DraftCommand command) {
        identity.requireActiveUser(actor.userId());
        requireCommittee(actor, organizationId);
        Objects.requireNonNull(command, "command");
        validateCapacityStorage(command.minimumParticipants(), command.maximumParticipants());
        CoachProfileEntity coach = approvedCoach(organizationId, command.coachProfileId());
        requireActiveCoachRole(coach);
        CourseOffering offering;
        try {
            offering = CourseOffering.createDraft(
                    UUID.randomUUID(), organizationId, actor.userId(), draftSpec(command), sessionPlans(command.sessions()));
        } catch (OfferingDomainException ex) {
            throw business(ex);
        }
        offerings.save(offering);
        audit.record(organizationId, actor.userId(), "COURSE_OFFERING_DRAFT_CREATED", "CourseOffering", offering.id(), null);
        return offering;
    }

    @Transactional
    public CourseOffering reviseDraft(AuthenticatedPrincipal actor, UUID offeringId, DraftCommand command) {
        identity.requireActiveUser(actor.userId());
        CourseOffering offering = lockedOffering(offeringId);
        requireCommittee(actor, offering.organizationId());
        Objects.requireNonNull(command, "command");
        validateCapacityStorage(command.minimumParticipants(), command.maximumParticipants());
        CoachProfileEntity coach = approvedCoach(offering.organizationId(), command.coachProfileId());
        requireActiveCoachRole(coach);
        try {
            offering.reviseDraft(draftSpec(command));
            offering.replaceSessionPlans(sessionPlans(command.sessions()));
        } catch (OfferingDomainException ex) {
            throw business(ex);
        }
        offerings.save(offering);
        audit.record(offering.organizationId(), actor.userId(), "COURSE_OFFERING_DRAFT_REVISED", "CourseOffering", offering.id(), null);
        return offering;
    }

    @Transactional
    public CourseOfferingPriceSnapshot createPriceDraft(
            AuthenticatedPrincipal actor, UUID offeringId, PriceCommand command) {
        identity.requireActiveUser(actor.userId());
        CourseOffering offering = lockedOffering(offeringId);
        requireCommittee(actor, offering.organizationId());
        if (offering.status() != CourseOfferingStatus.DRAFT) {
            throw new BusinessException("STATE_TRANSITION_INVALID", "Offering price can change only while offering is DRAFT");
        }
        Objects.requireNonNull(command, "command");
        CourseOfferingPriceSnapshot snapshot;
        try {
            snapshot = CourseOfferingPriceSnapshot.createDraft(
                    UUID.randomUUID(), offering.organizationId(), offering.id(), prices.nextVersion(offering.id()),
                    command.currency(), command.pricePerParticipant(), command.ruleTrace(), actor.userId());
        } catch (OfferingDomainException ex) {
            throw business(ex);
        }
        prices.save(snapshot);
        audit.record(offering.organizationId(), actor.userId(), "COURSE_OFFERING_PRICE_DRAFTED", "CourseOfferingPriceSnapshot", snapshot.id(), null);
        return snapshot;
    }

    @Transactional
    public CourseOfferingPriceSnapshot confirmPrice(
            AuthenticatedPrincipal actor, UUID offeringId, UUID snapshotId) {
        identity.requireActiveUser(actor.userId());
        CourseOffering offering = lockedOffering(offeringId);
        requireCommittee(actor, offering.organizationId());
        if (offering.status() != CourseOfferingStatus.DRAFT) {
            throw new BusinessException("STATE_TRANSITION_INVALID", "Offering price can be confirmed only while offering is DRAFT");
        }
        CourseOfferingPriceSnapshot target = prices.findLockedById(snapshotId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Offering price snapshot was not found"));
        if (!target.courseOfferingId().equals(offering.id()) || !target.organizationId().equals(offering.organizationId())) {
            throw new BusinessException("ORG_SCOPE_DENIED", "Price snapshot is outside the offering scope");
        }
        if (target.status() == OfferingPriceSnapshotStatus.CONFIRMED) return target;
        prices.findConfirmedByOfferingId(offering.id()).ifPresent(current -> {
            if (!current.id().equals(target.id())) {
                try { current.supersede(); } catch (OfferingDomainException ex) { throw business(ex); }
                prices.save(current);
                prices.flush();
            }
        });
        try { target.confirm(actor.userId(), Instant.now()); } catch (OfferingDomainException ex) { throw business(ex); }
        prices.save(target);
        prices.flush();
        audit.record(offering.organizationId(), actor.userId(), "COURSE_OFFERING_PRICE_CONFIRMED", "CourseOfferingPriceSnapshot", target.id(), null);
        return target;
    }

    @Transactional
    public CourseOffering publish(AuthenticatedPrincipal actor, UUID offeringId) {
        identity.requireActiveUser(actor.userId());
        CourseOffering offering = lockedOffering(offeringId);
        requireCommittee(actor, offering.organizationId());
        CoachProfileEntity coach = approvedCoach(offering.organizationId(), offering.spec().coachProfileId());
        requireActiveCoachRole(coach);
        CourseOfferingPriceSnapshot confirmedPrice = prices.findConfirmedByOfferingId(offering.id())
                .orElseThrow(() -> new BusinessException("OFFERING_NOT_READY", "Confirmed offering price is required"));
        holdCoachReservations(offering, coach.getUserId());
        try {
            offering.publish(actor.userId(), Instant.now(), new PublicationReadiness(true, confirmedPrice.id(), true));
        } catch (OfferingDomainException ex) {
            throw business(ex);
        }
        offerings.save(offering);
        audit.record(offering.organizationId(), actor.userId(), "COURSE_OFFERING_PUBLISHED", "CourseOffering", offering.id(), null);
        return offering;
    }

    @Transactional
    public OfferingRegistration register(
            AuthenticatedPrincipal actor, UUID offeringId, String idempotencyKey) {
        identity.requireActiveUser(actor.userId());
        CourseOffering offering = lockedOffering(offeringId);
        requireStudent(actor, offering.organizationId());
        var idem = idempotency.begin(
                offering.organizationId(), actor.userId(), REGISTER_OPERATION, idempotencyKey,
                offering.id() + "|" + actor.userId());
        if (idem.getResultResourceId() != null) {
            return registrations.findById(idem.getResultResourceId())
                    .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Idempotent registration result was not found"));
        }
        Instant now = Instant.now();
        if (!offering.isRegistrationOpenAt(now)) {
            throw new BusinessException("OFFERING_NOT_OPEN", "Offering is not accepting registrations now");
        }
        if (registrations.hasActive(offering.id(), actor.userId())) {
            throw new BusinessException("ALREADY_REGISTERED", "Student already has an active registration for this offering");
        }
        long activeCount = registrations.activeCount(offering.id());
        if (activeCount >= offering.spec().maximumParticipants()) {
            throw new BusinessException("OFFERING_FULL", "Offering has reached maximum participant capacity");
        }
        OfferingRegistration registration = OfferingRegistration.register(
                UUID.randomUUID(), offering.organizationId(), offering.id(), actor.userId(), now);
        holdParticipantReservations(offering, actor.userId());
        registrations.save(registration);
        audit.record(offering.organizationId(), actor.userId(), "COURSE_OFFERING_REGISTERED", "OfferingRegistration", registration.id(), null);
        idem.complete("OfferingRegistration", registration.id(), 201);
        return registration;
    }

    @Transactional
    public OfferingRegistration cancelRegistration(
            AuthenticatedPrincipal actor, UUID registrationId, String reason) {
        identity.requireActiveUser(actor.userId());
        OfferingRegistration lookup = registrations.findById(registrationId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Offering registration was not found"));
        CourseOffering offering = lockedOffering(lookup.courseOfferingId());
        OfferingRegistration registration = registrations.findLockedById(registrationId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Offering registration was not found"));
        requireStudent(actor, offering.organizationId());
        try { registration.cancelByStudent(actor.userId(), Instant.now(), reason); }
        catch (OfferingDomainException ex) { throw business(ex); }
        registrations.save(registration);
        releaseParticipantReservations(offering.id(), registration.userId(), "STUDENT_CANCELLED");
        audit.record(offering.organizationId(), actor.userId(), "COURSE_OFFERING_REGISTRATION_CANCELLED", "OfferingRegistration", registration.id(), reason);
        return registration;
    }

    @Transactional
    public CourseOffering close(AuthenticatedPrincipal actor, UUID offeringId) {
        identity.requireActiveUser(actor.userId());
        CourseOffering offering = lockedOffering(offeringId);
        requireCommittee(actor, offering.organizationId());
        try { offering.close(actor.userId(), Instant.now()); } catch (OfferingDomainException ex) { throw business(ex); }
        offerings.save(offering);
        audit.record(offering.organizationId(), actor.userId(), "COURSE_OFFERING_CLOSED", "CourseOffering", offering.id(), null);
        return offering;
    }

    @Transactional
    public CourseOffering cancelOffering(AuthenticatedPrincipal actor, UUID offeringId, String reason) {
        identity.requireActiveUser(actor.userId());
        CourseOffering offering = lockedOffering(offeringId);
        requireCommittee(actor, offering.organizationId());
        try { offering.cancel(actor.userId(), Instant.now(), reason); } catch (OfferingDomainException ex) { throw business(ex); }
        releaseAllOfferingReservations(offering.id(), "OFFERING_CANCELLED");
        offerings.save(offering);
        audit.record(offering.organizationId(), actor.userId(), "COURSE_OFFERING_CANCELLED", "CourseOffering", offering.id(), reason);
        return offering;
    }

    @Transactional
    public ConfirmationResult confirm(
            AuthenticatedPrincipal actor, UUID offeringId, String idempotencyKey) {
        identity.requireActiveUser(actor.userId());
        CourseOffering offering = lockedOffering(offeringId);
        requireCommittee(actor, offering.organizationId());
        var idem = idempotency.begin(
                offering.organizationId(), actor.userId(), CONFIRM_OPERATION, idempotencyKey,
                offering.id() + "|confirm=true");
        if (idem.getResultResourceId() != null) return loadConfirmationResult(offering, idem.getResultResourceId());

        List<OfferingRegistration> active = registrations.findLockedActiveByOfferingId(offering.id());
        try { offering.confirm(actor.userId(), active.size(), Instant.now()); }
        catch (OfferingDomainException ex) { throw business(ex); }

        CourseOfferingPriceSnapshot price = prices.findConfirmedByOfferingId(offering.id())
                .orElseThrow(() -> new BusinessException("OFFERING_NOT_READY", "Confirmed offering price is required"));
        CoachProfileEntity coach = approvedCoach(offering.organizationId(), offering.spec().coachProfileId());
        requireActiveCoachRole(coach);
        assertCoachReservationsHeld(offering, coach.getUserId());

        UUID courseId = UUID.randomUUID();
        insertCourse(courseId, offering, active.size(), actor.userId());
        insertCourseApproval(courseId, offering, actor.userId());
        insertPrimaryContact(courseId, offering.organizationId(), actor.userId());
        Map<UUID, FormalSession> formalSessions = insertFormalSessions(courseId, offering, active.size());
        List<FormalPrice> formalPrices = insertFormalPrices(offering, formalSessions, price, actor.userId());
        convertCoachReservationsAndAssignments(offering, coach, formalSessions, actor.userId());
        insertVenueArrangements(offering, formalSessions, actor.userId());

        List<UUID> receivableIds = new ArrayList<>();
        for (OfferingRegistration registration : active) {
            UUID membershipId = UUID.randomUUID();
            insertMembership(membershipId, courseId, offering.organizationId(), registration.userId());
            Map<UUID,UUID> enrollmentIds = insertEnrollments(membershipId, registration.userId(), offering.organizationId(), formalSessions);
            convertParticipantReservations(offering, registration.userId(), formalSessions);
            UUID receivableId = insertReceivable(
                    courseId, offering, registration.userId(), formalPrices, enrollmentIds);
            receivableIds.add(receivableId);
            registration.markConverted(membershipId);
            registrations.save(registration);
        }

        offerings.save(offering);
        audit.record(offering.organizationId(), actor.userId(), "COURSE_OFFERING_CONFIRMED", "CourseOffering", offering.id(), null);
        audit.record(offering.organizationId(), actor.userId(), "COURSE_CREATED_FROM_OFFERING", "Course", courseId, null);
        idem.complete("Course", courseId, 201);
        return new ConfirmationResult(
                offering.id(), offering.status().name(), courseId,
                formalSessions.values().stream().map(FormalSession::id).toList(), List.copyOf(receivableIds));
    }

    private CourseOffering lockedOffering(UUID offeringId) {
        return offerings.findLockedById(offeringId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Course offering was not found"));
    }

    private CoachProfileEntity approvedCoach(UUID organizationId, UUID coachProfileId) {
        CoachProfileEntity coach = coachProfiles.findById(coachProfileId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Coach profile was not found"));
        if (!organizationId.equals(coach.getOrganizationId()) || coach.getApprovalStatus()!=CoachProfileApprovalStatus.APPROVED) {
            throw new BusinessException("COACH_NOT_APPROVED", "Offering coach must be approved in this organization");
        }
        return coach;
    }

    private void requireActiveCoachRole(CoachProfileEntity coach) {
        if (!roles.existsByUserIdAndOrganizationIdAndRoleCodeAndStatus(
                coach.getUserId(), coach.getOrganizationId(), RoleCode.COACH, RoleAssignmentStatus.ACTIVE)) {
            throw new BusinessException("COACH_NOT_APPROVED", "Offering coach does not have an active coach role");
        }
    }

    private void requireCommittee(AuthenticatedPrincipal actor, UUID organizationId) {
        if (!identity.isAuthorizedForOrganization(actor, RoleCode.COMMITTEE, organizationId)) {
            throw new BusinessException("AUTH_FORBIDDEN", "Committee or platform administrator permission is required");
        }
    }

    private void requireStudent(AuthenticatedPrincipal actor, UUID organizationId) {
        if (!identity.isAuthorizedForOrganization(actor, RoleCode.STUDENT, organizationId)) {
            throw new BusinessException("AUTH_FORBIDDEN", "Active student role is required for this organization");
        }
    }

    private CourseOfferingDraftSpec draftSpec(DraftCommand command) {
        try {
            return new CourseOfferingDraftSpec(
                    command.coachProfileId(), command.title(), command.description(), command.scheduleType(),
                    command.billingMode(), command.skillLevel(), command.minimumParticipants(), command.maximumParticipants(),
                    command.registrationOpenAt(), command.registrationCloseAt());
        } catch (OfferingDomainException ex) { throw business(ex); }
    }

    private List<CourseOfferingSessionPlan> sessionPlans(List<SessionCommand> commands) {
        if (commands == null) return List.of();
        try {
            return commands.stream().map(s -> {
                if (s.sequenceNo() > Short.MAX_VALUE) throw new BusinessException("VALIDATION_FAILED", "Session sequence exceeds supported range");
                return new CourseOfferingSessionPlan(
                        UUID.randomUUID(), s.sequenceNo(), s.startAt(), s.endAt(), s.venueId(), s.venueNameSnapshot(), s.venueAddressSnapshot());
            }).toList();
        } catch (OfferingDomainException ex) { throw business(ex); }
    }

    private void validateCapacityStorage(int minimum, int maximum) {
        if (minimum > Short.MAX_VALUE || maximum > Short.MAX_VALUE) {
            throw new BusinessException("VALIDATION_FAILED", "Participant capacity exceeds supported range");
        }
    }

    private void holdCoachReservations(CourseOffering offering, UUID coachUserId) {
        for (CourseOfferingSessionPlan session : offering.sessionPlans()) {
            insertHeldReservation(offering.organizationId(), coachUserId, session, "COACH");
        }
    }

    private void holdParticipantReservations(CourseOffering offering, UUID studentUserId) {
        for (CourseOfferingSessionPlan session : offering.sessionPlans()) {
            insertHeldReservation(offering.organizationId(), studentUserId, session, "PARTICIPANT");
        }
    }

    private void insertHeldReservation(UUID organizationId, UUID userId, CourseOfferingSessionPlan session, String role) {
        try {
            jdbc.update("""
                    insert into schedule_reservations(
                        id, organization_id, user_id, course_offering_session_id,
                        reservation_role, reserved_period, status)
                    values (?, ?, ?, ?, ?, tstzrange(?::timestamptz, ?::timestamptz, '[)'), 'HELD')
                    """, UUID.randomUUID(), organizationId, userId, session.id(), role,
                    Timestamp.from(session.startAt()), Timestamp.from(session.endAt()));
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("SCHEDULE_CONFLICT", role + " has an overlapping active schedule");
        }
    }

    private void releaseParticipantReservations(UUID offeringId, UUID userId, String reason) {
        jdbc.update("""
                update schedule_reservations r
                set status='RELEASED', released_at=now(), release_reason=?, updated_at=now()
                where r.user_id=? and r.status='HELD' and r.reservation_role='PARTICIPANT'
                  and r.course_offering_session_id in (
                      select id from course_offering_sessions where course_offering_id=?
                  )
                """, reason, userId, offeringId);
    }

    private void releaseAllOfferingReservations(UUID offeringId, String reason) {
        jdbc.update("""
                update schedule_reservations r
                set status='RELEASED', released_at=now(), release_reason=?, updated_at=now()
                where r.status='HELD' and r.course_offering_session_id in (
                    select id from course_offering_sessions where course_offering_id=?
                )
                """, reason, offeringId);
    }

    private void assertCoachReservationsHeld(CourseOffering offering, UUID coachUserId) {
        Integer count = jdbc.queryForObject("""
                select count(*) from schedule_reservations r
                where r.organization_id=? and r.user_id=? and r.reservation_role='COACH' and r.status='HELD'
                  and r.course_offering_session_id in (
                      select id from course_offering_sessions where course_offering_id=?
                  )
                """, Integer.class, offering.organizationId(), coachUserId, offering.id());
        if (count == null || count != offering.sessionPlans().size()) {
            throw new BusinessException("OFFERING_NOT_READY", "Coach held reservations are incomplete");
        }
    }

    private void insertCourse(UUID courseId, CourseOffering offering, int participantCount, UUID actorUserId) {
        jdbc.update("""
                insert into courses(
                    id, organization_id, course_no, source_offering_id, created_by_user_id,
                    course_type, schedule_type, billing_mode, skill_level,
                    expected_participant_count, guest_participant_count,
                    minimum_participants, maximum_participants, total_session_count,
                    status, activated_at)
                values (?, ?, ?, ?, ?, 'GROUP', ?, ?, ?, ?, 0, ?, ?, ?, 'ACTIVE', now())
                """, courseId, offering.organizationId(), businessNo("C", courseId), offering.id(), actorUserId,
                offering.spec().scheduleType().name(), offering.spec().billingMode().name(), offering.spec().skillLevel(),
                participantCount, offering.spec().minimumParticipants(), offering.spec().maximumParticipants(), offering.sessionPlans().size());
    }

    private void insertCourseApproval(UUID courseId, CourseOffering offering, UUID actorUserId) {
        jdbc.update("""
                insert into course_approvals(
                    id, organization_id, course_id, course_version, decision, reason, decided_by, decided_at)
                values (?, ?, ?, 0, 'APPROVED', ?, ?, now())
                """, UUID.randomUUID(), offering.organizationId(), courseId,
                "Confirmed from CourseOffering " + offering.id(), actorUserId);
    }

    private void insertPrimaryContact(UUID courseId, UUID organizationId, UUID actorUserId) {
        jdbc.update("""
                insert into course_contact_assignments(
                    id, organization_id, course_id, user_id, effective_from, assigned_by, reason)
                values (?, ?, ?, ?, now(), ?, 'Open Enrollment committee contact')
                """, UUID.randomUUID(), organizationId, courseId, actorUserId, actorUserId);
    }

    private Map<UUID,FormalSession> insertFormalSessions(UUID courseId, CourseOffering offering, int participantCount) {
        Map<UUID,FormalSession> result = new LinkedHashMap<>();
        for (CourseOfferingSessionPlan source : offering.sessionPlans()) {
            UUID formalId=UUID.randomUUID();
            jdbc.update("""
                    insert into course_sessions(
                        id, organization_id, course_id, sequence_no,
                        scheduled_start_at, scheduled_end_at,
                        expected_participant_count, guest_participant_count, status)
                    values (?, ?, ?, ?, ?, ?, ?, 0, 'SCHEDULED')
                    """, formalId, offering.organizationId(), courseId, source.sequenceNo(),
                    Timestamp.from(source.startAt()), Timestamp.from(source.endAt()), participantCount);
            result.put(source.id(), new FormalSession(formalId, source.id(), source.sequenceNo(), source.startAt(), source.endAt()));
        }
        return result;
    }

    private List<FormalPrice> insertFormalPrices(
            CourseOffering offering,
            Map<UUID,FormalSession> formalSessions,
            CourseOfferingPriceSnapshot price,
            UUID actorUserId) {
        List<BigDecimal> charges = sessionCharges(offering, price.pricePerParticipant());
        List<FormalPrice> result = new ArrayList<>();
        for (int i=0;i<offering.sessionPlans().size();i++) {
            CourseOfferingSessionPlan source=offering.sessionPlans().get(i);
            FormalSession formal=formalSessions.get(source.id());
            BigDecimal charge=charges.get(i);
            UUID snapshotId=UUID.randomUUID();
            jdbc.update("""
                    insert into session_price_snapshots(
                        id, organization_id, course_session_id, version_no, status,
                        currency, tuition_amount, venue_fee, other_adjustment, total_receivable,
                        rule_trace, source_offering_price_snapshot_id,
                        confirmed_by, confirmed_at, created_by)
                    values (?, ?, ?, 1, 'CONFIRMED', ?, ?, 0, 0, ?, '{}'::jsonb, ?, ?, now(), ?)
                    """, snapshotId, offering.organizationId(), formal.id(), price.currency(), charge, charge,
                    price.id(), actorUserId, actorUserId);
            jdbc.update("""
                    insert into session_price_snapshot_items(
                        id, price_snapshot_id, item_type, description, quantity, unit_amount,
                        line_amount, source_reference_type, source_reference_id, sort_order)
                    values (?, ?, 'TUITION', 'Open Enrollment tuition', 1, ?, ?, 'COURSE_OFFERING_PRICE', ?, 1)
                    """, UUID.randomUUID(), snapshotId, charge, charge, price.id());
            result.add(new FormalPrice(formal.id(), snapshotId, charge));
        }
        return result;
    }

    private List<BigDecimal> sessionCharges(CourseOffering offering, BigDecimal rawPrice) {
        BigDecimal price=rawPrice.setScale(2, RoundingMode.HALF_UP);
        int count=offering.sessionPlans().size();
        if (offering.spec().billingMode()==OfferingBillingMode.PER_SESSION) {
            return java.util.stream.IntStream.range(0,count).mapToObj(i -> price).toList();
        }
        BigDecimal base=price.divide(BigDecimal.valueOf(count),2,RoundingMode.HALF_UP);
        List<BigDecimal> result=new ArrayList<>();
        for(int i=0;i<count-1;i++) result.add(base);
        result.add(price.subtract(base.multiply(BigDecimal.valueOf(count-1))).setScale(2,RoundingMode.HALF_UP));
        return result;
    }

    private void convertCoachReservationsAndAssignments(
            CourseOffering offering,
            CoachProfileEntity coach,
            Map<UUID,FormalSession> formalSessions,
            UUID actorUserId) {
        for (CourseOfferingSessionPlan source : offering.sessionPlans()) {
            FormalSession formal=formalSessions.get(source.id());
            int updated=jdbc.update("""
                    update schedule_reservations
                    set course_session_id=?, course_offering_session_id=null,
                        status='CONFIRMED', expires_at=null, updated_at=now()
                    where organization_id=? and user_id=? and course_offering_session_id=?
                      and reservation_role='COACH' and status='HELD'
                    """, formal.id(), offering.organizationId(), coach.getUserId(), source.id());
            if(updated!=1) throw new BusinessException("OFFERING_NOT_READY", "Coach reservation conversion failed");
            jdbc.update("""
                    insert into session_coach_assignments(
                        id, organization_id, course_session_id, coach_profile_id,
                        source_type, status, is_primary, responded_at, assigned_by)
                    values (?, ?, ?, ?, 'DIRECT', 'ACCEPTED', true, now(), ?)
                    """, UUID.randomUUID(), offering.organizationId(), formal.id(), coach.getId(), actorUserId);
        }
    }

    private void insertVenueArrangements(
            CourseOffering offering, Map<UUID,FormalSession> formalSessions, UUID actorUserId) {
        for (CourseOfferingSessionPlan source : offering.sessionPlans()) {
            FormalSession formal=formalSessions.get(source.id());
            jdbc.update("""
                    insert into session_venue_arrangements(
                        id, organization_id, course_session_id, source_type, venue_id,
                        venue_name_snapshot, address_snapshot, cost_amount, cost_payer_type,
                        status, confirmed_by, confirmed_at, note)
                    values (?, ?, ?, 'COMMITTEE', ?, ?, ?, 0, 'NONE', 'CONFIRMED', ?, now(), 'Converted from Open Enrollment session plan')
                    """, UUID.randomUUID(), offering.organizationId(), formal.id(), source.venueId(),
                    source.venueNameSnapshot(), source.venueAddressSnapshot(), actorUserId);
        }
    }

    private void insertMembership(UUID membershipId, UUID courseId, UUID organizationId, UUID userId) {
        jdbc.update("""
                insert into course_memberships(id, organization_id, course_id, user_id, status, joined_at)
                values (?, ?, ?, ?, 'ACTIVE', now())
                """, membershipId, organizationId, courseId, userId);
    }

    private Map<UUID,UUID> insertEnrollments(
            UUID membershipId, UUID userId, UUID organizationId, Map<UUID,FormalSession> formalSessions) {
        Map<UUID,UUID> result=new LinkedHashMap<>();
        for (FormalSession session : formalSessions.values()) {
            UUID enrollmentId=UUID.randomUUID();
            jdbc.update("""
                    insert into enrollments(
                        id, organization_id, course_membership_id, course_session_id, user_id, status, enrolled_at)
                    values (?, ?, ?, ?, ?, 'SCHEDULED', now())
                    """, enrollmentId, organizationId, membershipId, session.id(), userId);
            result.put(session.id(), enrollmentId);
        }
        return result;
    }

    private void convertParticipantReservations(
            CourseOffering offering, UUID userId, Map<UUID,FormalSession> formalSessions) {
        for (CourseOfferingSessionPlan source : offering.sessionPlans()) {
            FormalSession formal=formalSessions.get(source.id());
            int updated=jdbc.update("""
                    update schedule_reservations
                    set course_session_id=?, course_offering_session_id=null,
                        status='CONFIRMED', expires_at=null, updated_at=now()
                    where organization_id=? and user_id=? and course_offering_session_id=?
                      and reservation_role='PARTICIPANT' and status='HELD'
                    """, formal.id(), offering.organizationId(), userId, source.id());
            if(updated!=1) throw new BusinessException("OFFERING_NOT_READY", "Participant reservation conversion failed");
        }
    }

    private UUID insertReceivable(
            UUID courseId,
            CourseOffering offering,
            UUID payerUserId,
            List<FormalPrice> formalPrices,
            Map<UUID,UUID> enrollmentIds) {
        BigDecimal total=formalPrices.stream().map(FormalPrice::amount).reduce(BigDecimal.ZERO,BigDecimal::add).setScale(2,RoundingMode.HALF_UP);
        UUID receivableId=UUID.randomUUID();
        jdbc.update("""
                insert into receivables(
                    id, organization_id, receivable_no, course_id, payer_user_id,
                    billing_mode, currency, total_amount, adjusted_amount,
                    paid_amount, refunded_amount, balance_amount, status)
                values (?, ?, ?, ?, ?, ?, 'TWD', ?, 0, 0, 0, ?, 'OPEN')
                """, receivableId, offering.organizationId(), businessNo("R", receivableId), courseId,
                payerUserId, offering.spec().billingMode().name(), total, total);
        short sort=1;
        for (FormalPrice price : formalPrices) {
            jdbc.update("""
                    insert into receivable_items(
                        id, receivable_id, course_session_id, enrollment_id,
                        price_snapshot_id, amount, paid_amount, refunded_amount, status, sort_order)
                    values (?, ?, ?, ?, ?, ?, 0, 0, 'OPEN', ?)
                    """, UUID.randomUUID(), receivableId, price.courseSessionId(), enrollmentIds.get(price.courseSessionId()),
                    price.priceSnapshotId(), price.amount(), sort++);
        }
        return receivableId;
    }

    private ConfirmationResult loadConfirmationResult(CourseOffering offering, UUID courseId) {
        List<UUID> sessionIds=jdbc.queryForList("select id from course_sessions where course_id=? order by sequence_no",UUID.class,courseId);
        List<UUID> receivableIds=jdbc.queryForList("select id from receivables where course_id=? order by created_at,id",UUID.class,courseId);
        return new ConfirmationResult(offering.id(), offering.status().name(), courseId, sessionIds, receivableIds);
    }

    private BusinessException business(OfferingDomainException ex) {
        String code = switch (ex.error()) {
            case INVALID_STATE -> "STATE_TRANSITION_INVALID";
            case OFFERING_NOT_READY -> "OFFERING_NOT_READY";
            case PARTICIPANT_BELOW_MIN -> "PARTICIPANT_BELOW_MIN";
            case PARTICIPANT_ABOVE_MAX -> "PARTICIPANT_ABOVE_MAX";
            case REGISTRATION_ACTOR_FORBIDDEN -> "AUTH_FORBIDDEN";
            default -> "VALIDATION_FAILED";
        };
        return new BusinessException(code, ex.getMessage());
    }

    private String businessNo(String prefix, UUID id) {
        return prefix + "-" + id.toString().replace("-", "").substring(0, 24).toUpperCase();
    }

    public record SessionCommand(
            int sequenceNo, Instant startAt, Instant endAt, UUID venueId,
            String venueNameSnapshot, String venueAddressSnapshot) { }

    public record DraftCommand(
            UUID coachProfileId, String title, String description,
            OfferingScheduleType scheduleType, OfferingBillingMode billingMode, String skillLevel,
            int minimumParticipants, int maximumParticipants,
            Instant registrationOpenAt, Instant registrationCloseAt,
            List<SessionCommand> sessions) { }

    public record PriceCommand(String currency, BigDecimal pricePerParticipant, Map<String,Object> ruleTrace) { }
    public record ConfirmationResult(UUID offeringId, String offeringStatus, UUID courseId, List<UUID> sessionIds, List<UUID> receivableIds) { }
    private record FormalSession(UUID id, UUID sourceOfferingSessionId, int sequenceNo, Instant startAt, Instant endAt) { }
    private record FormalPrice(UUID courseSessionId, UUID priceSnapshotId, BigDecimal amount) { }
}
