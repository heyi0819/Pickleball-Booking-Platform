package com.pickleball.booking.course.application;

import com.pickleball.booking.course.domain.CourseCancellationRequest;
import com.pickleball.booking.course.domain.CourseCancellationRequestRepository;
import com.pickleball.booking.course.domain.CourseOperationsDomainError;
import com.pickleball.booking.course.domain.CourseOperationsDomainException;
import com.pickleball.booking.course.domain.CourseSession;
import com.pickleball.booking.course.domain.CourseSessionRepository;
import com.pickleball.booking.course.domain.Enrollment;
import com.pickleball.booking.course.domain.EnrollmentRepository;
import com.pickleball.booking.course.domain.MemberCancellationRecord;
import com.pickleball.booking.course.domain.MemberCancellationRecordRepository;
import com.pickleball.booking.course.domain.SessionChangeRequest;
import com.pickleball.booking.course.domain.SessionChangeRequestRepository;
import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.identity.application.IdentityService;
import com.pickleball.booking.identity.domain.RoleCode;
import com.pickleball.booking.shared.application.AuditOutboxService;
import com.pickleball.booking.shared.application.BusinessException;
import com.pickleball.booking.shared.application.IdempotencyService;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
public class CourseOperationsApplicationService {
    private static final String CREATE_RESCHEDULE_OPERATION = "COURSE_SESSION_RESCHEDULE_REQUEST";
    private static final String REVIEW_RESCHEDULE_OPERATION = "COURSE_SESSION_RESCHEDULE_REVIEW";
    private static final String DIRECT_RESCHEDULE_OPERATION = "COURSE_SESSION_DIRECT_RESCHEDULE";

    private final IdentityService identity;
    private final CourseSessionRepository sessions;
    private final EnrollmentRepository enrollments;
    private final MemberCancellationRecordRepository cancellationRecords;
    private final CourseCancellationRequestRepository cancellationRequests;
    private final SessionChangeRequestRepository changeRequests;
    private final CourseScheduleReservationPort reservations;
    private final CourseOperationsAccessPort access;
    private final AuditOutboxService audit;
    private final IdempotencyService idempotency;

    public CourseOperationsApplicationService(
            IdentityService identity,
            CourseSessionRepository sessions,
            EnrollmentRepository enrollments,
            MemberCancellationRecordRepository cancellationRecords,
            CourseCancellationRequestRepository cancellationRequests,
            SessionChangeRequestRepository changeRequests,
            CourseScheduleReservationPort reservations,
            CourseOperationsAccessPort access,
            AuditOutboxService audit,
            IdempotencyService idempotency) {
        this.identity = identity;
        this.sessions = sessions;
        this.enrollments = enrollments;
        this.cancellationRecords = cancellationRecords;
        this.cancellationRequests = cancellationRequests;
        this.changeRequests = changeRequests;
        this.reservations = reservations;
        this.access = access;
        this.audit = audit;
        this.idempotency = idempotency;
    }

    @Transactional
    public EnrollmentCancellationResult cancelEnrollment(
            AuthenticatedPrincipal actor, UUID enrollmentId, String reason, String requestId) {
        requireActor(actor);
        Enrollment enrollment = lockedEnrollment(enrollmentId);
        CourseSession session = lockedSession(enrollment.courseSessionId());
        if (!enrollment.organizationId().equals(session.organizationId())) {
            throw new BusinessException("ORG_SCOPE_DENIED", "Enrollment and session organization do not match");
        }
        requireEnrollmentCancellationActor(actor, enrollment);

        Instant now = Instant.now();
        Map<String, Object> before = enrollmentSnapshot(enrollment);
        try {
            MemberCancellationRecord record = enrollment.cancel(
                    UUID.randomUUID(), reason, now, session.scheduledStartAt());
            cancellationRecords.save(record);
            Enrollment saved = enrollments.save(enrollment);
            reservations.releaseParticipantReservation(session.id(), enrollment.userId(), "STUDENT_CANCELLED");
            audit.record(
                    enrollment.organizationId(), actor.userId(), "SESSION_ENROLLMENT_CANCELLED",
                    "Enrollment", enrollment.id(), reason, before, enrollmentSnapshot(saved), requestId);
            return new EnrollmentCancellationResult(saved, record, session.status());
        } catch (CourseOperationsDomainException ex) {
            throw business(ex);
        } catch (OptimisticLockingFailureException ex) {
            throw concurrent(ex);
        }
    }

    @Transactional
    public CourseCancellationRequest requestCoachCancellation(
            AuthenticatedPrincipal actor, UUID sessionId, String reason, String requestId) {
        requireActor(actor);
        CourseSession session = lockedSession(sessionId);
        requireAssignedCoach(actor, session);
        Instant now = Instant.now();
        Map<String, Object> before = sessionSnapshot(session);
        try {
            session.markCoachCancellationPending(now);
            CourseCancellationRequest request = CourseCancellationRequest.createPending(
                    UUID.randomUUID(), session.organizationId(), session.id(), actor.userId(), reason, now);
            cancellationRequests.save(request);
            CourseSession saved = sessions.save(session);
            audit.record(
                    session.organizationId(), actor.userId(), "COACH_SESSION_CANCELLATION_REQUESTED",
                    "CourseCancellationRequest", request.id(), reason,
                    before, sessionSnapshot(saved), requestId);
            return request;
        } catch (CourseOperationsDomainException ex) {
            throw business(ex);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("STATE_TRANSITION_INVALID", "A pending coach cancellation request already exists");
        } catch (OptimisticLockingFailureException ex) {
            throw concurrent(ex);
        }
    }

    @Transactional
    public CourseCancellationReviewResult reviewCoachCancellation(
            AuthenticatedPrincipal actor,
            UUID cancellationRequestId,
            ReviewDecision decision,
            String reviewNote,
            String requestId) {
        requireActor(actor);
        requireReviewDecision(decision);
        CourseCancellationRequest request = lockedCancellationRequest(cancellationRequestId);
        requireCommittee(actor, request.organizationId());
        CourseSession session = lockedSession(request.courseSessionId());
        Map<String, Object> before = sessionSnapshot(session);
        Instant now = Instant.now();
        try {
            CourseSession saved;
            if (decision == ReviewDecision.APPROVE) {
                request.approve(actor.userId(), now, reviewNote);
                session.approveCoachCancellation(reviewNote, now);
                reservations.releaseAllActiveReservations(session.id(), "COACH_CANCELLATION_APPROVED");
                saved = sessions.save(session);
            } else {
                request.reject(actor.userId(), now, reviewNote);
                session.rejectCoachCancellation();
                saved = sessions.save(session);
            }
            cancellationRequests.save(request);
            audit.record(
                    request.organizationId(), actor.userId(),
                    decision == ReviewDecision.APPROVE
                            ? "COACH_SESSION_CANCELLATION_APPROVED"
                            : "COACH_SESSION_CANCELLATION_REJECTED",
                    "CourseCancellationRequest", request.id(), reviewNote,
                    before, sessionSnapshot(saved), requestId);
            return new CourseCancellationReviewResult(request, saved);
        } catch (CourseOperationsDomainException ex) {
            throw business(ex);
        } catch (OptimisticLockingFailureException ex) {
            throw concurrent(ex);
        }
    }

    @Transactional
    public SessionChangeRequest requestReschedule(
            AuthenticatedPrincipal actor,
            UUID sessionId,
            Instant proposedStartAt,
            Instant proposedEndAt,
            String reason,
            String idempotencyKey,
            String requestId) {
        requireActor(actor);
        CourseSession session = lockedSession(sessionId);
        requireRescheduleRequester(actor, session);
        Instant now = Instant.now();
        requireRescheduleRequestable(session, now);
        validateFutureRange(proposedStartAt, proposedEndAt, now);

        var idem = idempotency.begin(
                session.organizationId(), actor.userId(), CREATE_RESCHEDULE_OPERATION, idempotencyKey,
                session.id() + "|" + proposedStartAt + "|" + proposedEndAt + "|" + normalized(reason));
        if (idem.getResultResourceId() != null) {
            return lockedChangeRequest(idem.getResultResourceId());
        }

        try {
            SessionChangeRequest request = SessionChangeRequest.createPending(
                    UUID.randomUUID(), session.organizationId(), session.id(), SessionChangeRequest.Type.RESCHEDULE,
                    actor.userId(), reason, proposedStartAt, proposedEndAt, null, null, now);
            changeRequests.save(request);
            audit.record(
                    session.organizationId(), actor.userId(), "SESSION_RESCHEDULE_REQUESTED",
                    "SessionChangeRequest", request.id(), reason,
                    null, changeRequestSnapshot(request), requestId);
            idem.complete("SessionChangeRequest", request.id(), 201);
            return request;
        } catch (CourseOperationsDomainException ex) {
            throw business(ex);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("STATE_TRANSITION_INVALID", "Session change request could not be persisted");
        }
    }

    @Transactional
    public RescheduleResult reviewReschedule(
            AuthenticatedPrincipal actor,
            UUID changeRequestId,
            ReviewDecision decision,
            String decisionReason,
            String idempotencyKey,
            String requestId) {
        requireActor(actor);
        requireReviewDecision(decision);
        SessionChangeRequest request = lockedChangeRequest(changeRequestId);
        if (request.type() != SessionChangeRequest.Type.RESCHEDULE) {
            throw new BusinessException("STATE_TRANSITION_INVALID", "Only RESCHEDULE requests are supported by this command");
        }
        requireCommittee(actor, request.organizationId());
        CourseSession session = lockedSession(request.courseSessionId());
        var idem = idempotency.begin(
                request.organizationId(), actor.userId(), REVIEW_RESCHEDULE_OPERATION, idempotencyKey,
                request.id() + "|" + decision + "|" + normalized(decisionReason));
        if (idem.getResultResourceId() != null) {
            return new RescheduleResult(lockedChangeRequest(idem.getResultResourceId()), session);
        }

        Map<String, Object> before = sessionSnapshot(session);
        Instant now = Instant.now();
        try {
            CourseSession saved = session;
            if (decision == ReviewDecision.APPROVE) {
                validateFutureRange(request.proposedStartAt(), request.proposedEndAt(), now);
                session.applyReschedule(request.proposedStartAt(), request.proposedEndAt(), now);
                shiftReservations(session.id(), request.proposedStartAt(), request.proposedEndAt());
                request.approve(actor.userId(), now, decisionReason);
                saved = sessions.save(session);
                changeRequests.save(request);
                audit.record(
                        request.organizationId(), actor.userId(), "SESSION_RESCHEDULE_APPROVED",
                        "CourseSession", session.id(), decisionReason,
                        before, sessionSnapshot(saved, request.id()), requestId);
            } else {
                request.reject(actor.userId(), now, decisionReason);
                changeRequests.save(request);
                audit.record(
                        request.organizationId(), actor.userId(), "SESSION_RESCHEDULE_REJECTED",
                        "SessionChangeRequest", request.id(), decisionReason,
                        changeRequestSnapshotBeforeDecision(request), changeRequestSnapshot(request), requestId);
            }
            idem.complete("SessionChangeRequest", request.id(), 200);
            return new RescheduleResult(request, saved);
        } catch (CourseOperationsDomainException ex) {
            throw business(ex);
        } catch (OptimisticLockingFailureException ex) {
            throw concurrent(ex);
        }
    }

    @Transactional
    public RescheduleResult directReschedule(
            AuthenticatedPrincipal actor,
            UUID sessionId,
            Instant startAt,
            Instant endAt,
            String reason,
            String idempotencyKey,
            String requestId) {
        requireActor(actor);
        CourseSession session = lockedSession(sessionId);
        requireCommittee(actor, session.organizationId());
        Instant now = Instant.now();
        validateFutureRange(startAt, endAt, now);

        var idem = idempotency.begin(
                session.organizationId(), actor.userId(), DIRECT_RESCHEDULE_OPERATION, idempotencyKey,
                session.id() + "|" + startAt + "|" + endAt + "|" + normalized(reason));
        if (idem.getResultResourceId() != null) {
            return new RescheduleResult(lockedChangeRequest(idem.getResultResourceId()), session);
        }

        Map<String, Object> before = sessionSnapshot(session);
        try {
            session.applyReschedule(startAt, endAt, now);
            SessionChangeRequest request = SessionChangeRequest.createApprovedDirectReschedule(
                    UUID.randomUUID(), session.organizationId(), session.id(), actor.userId(),
                    reason, startAt, endAt, now);
            shiftReservations(session.id(), startAt, endAt);
            CourseSession saved = sessions.save(session);
            changeRequests.save(request);
            audit.record(
                    session.organizationId(), actor.userId(), "SESSION_DIRECT_RESCHEDULED",
                    "CourseSession", session.id(), reason,
                    before, sessionSnapshot(saved, request.id()), requestId);
            idem.complete("SessionChangeRequest", request.id(), 200);
            return new RescheduleResult(request, saved);
        } catch (CourseOperationsDomainException ex) {
            throw business(ex);
        } catch (OptimisticLockingFailureException ex) {
            throw concurrent(ex);
        }
    }

    @Transactional
    public Enrollment markAttendance(
            AuthenticatedPrincipal actor,
            UUID enrollmentId,
            AttendanceDecision attendance,
            String requestId) {
        requireActor(actor);
        requireAttendanceDecision(attendance);
        Enrollment enrollment = lockedEnrollment(enrollmentId);
        CourseSession session = lockedSession(enrollment.courseSessionId());
        requireAttendanceActor(actor, session);
        Map<String, Object> before = enrollmentSnapshot(enrollment);
        Instant now = Instant.now();
        try {
            if (attendance == AttendanceDecision.ATTENDED) {
                enrollment.markAttended(now, session.scheduledStartAt());
            } else {
                enrollment.markAbsent(now, session.scheduledStartAt());
            }
            Enrollment saved = enrollments.save(enrollment);
            audit.record(
                    enrollment.organizationId(), actor.userId(), "SESSION_ATTENDANCE_MARKED",
                    "Enrollment", enrollment.id(), attendance.name(),
                    before, enrollmentSnapshot(saved), requestId);
            return saved;
        } catch (CourseOperationsDomainException ex) {
            throw business(ex);
        } catch (OptimisticLockingFailureException ex) {
            throw concurrent(ex);
        }
    }

    private void shiftReservations(UUID sessionId, Instant startAt, Instant endAt) {
        try {
            reservations.shiftActiveReservations(sessionId, startAt, endAt);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("SCHEDULE_CONFLICT", "New session time conflicts with an active schedule reservation");
        }
    }

    private Enrollment lockedEnrollment(UUID id) {
        return enrollments.findById(id)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Enrollment was not found"));
    }

    private CourseSession lockedSession(UUID id) {
        return sessions.findById(id)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Course session was not found"));
    }

    private CourseCancellationRequest lockedCancellationRequest(UUID id) {
        return cancellationRequests.findById(id)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Course cancellation request was not found"));
    }

    private SessionChangeRequest lockedChangeRequest(UUID id) {
        return changeRequests.findById(id)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Session change request was not found"));
    }

    private void requireActor(AuthenticatedPrincipal actor) {
        Objects.requireNonNull(actor, "actor");
        identity.requireActiveUser(actor.userId());
    }

    private void requireEnrollmentCancellationActor(AuthenticatedPrincipal actor, Enrollment enrollment) {
        boolean self = actor.userId().equals(enrollment.userId())
                && identity.isAuthorizedForOrganization(actor, RoleCode.STUDENT, enrollment.organizationId());
        if (!self && !identity.isAuthorizedForOrganization(actor, RoleCode.COMMITTEE, enrollment.organizationId())) {
            throw new BusinessException("AUTH_FORBIDDEN", "Only the enrollment owner or authorized committee can cancel enrollment");
        }
    }

    private void requireAssignedCoach(AuthenticatedPrincipal actor, CourseSession session) {
        if (!identity.isAuthorizedForOrganization(actor, RoleCode.COACH, session.organizationId())
                || !access.isAssignedCoach(session.organizationId(), session.id(), actor.userId())) {
            throw new BusinessException("AUTH_FORBIDDEN", "Only the assigned coach can request session cancellation");
        }
    }

    private void requireRescheduleRequester(AuthenticatedPrincipal actor, CourseSession session) {
        if (identity.isAuthorizedForOrganization(actor, RoleCode.COMMITTEE, session.organizationId())) return;
        if (identity.isAuthorizedForOrganization(actor, RoleCode.STUDENT, session.organizationId())
                && access.isScheduledParticipant(session.organizationId(), session.id(), actor.userId())) return;
        if (identity.isAuthorizedForOrganization(actor, RoleCode.COACH, session.organizationId())
                && access.isAssignedCoach(session.organizationId(), session.id(), actor.userId())) return;
        throw new BusinessException("AUTH_FORBIDDEN", "Actor is not related to this course session");
    }

    private void requireAttendanceActor(AuthenticatedPrincipal actor, CourseSession session) {
        if (identity.isAuthorizedForOrganization(actor, RoleCode.COMMITTEE, session.organizationId())) return;
        if (identity.isAuthorizedForOrganization(actor, RoleCode.COACH, session.organizationId())
                && access.isAssignedCoach(session.organizationId(), session.id(), actor.userId())) return;
        throw new BusinessException("AUTH_FORBIDDEN", "Attendance can be marked only by the assigned coach or committee");
    }

    private void requireCommittee(AuthenticatedPrincipal actor, UUID organizationId) {
        if (!identity.isAuthorizedForOrganization(actor, RoleCode.COMMITTEE, organizationId)) {
            throw new BusinessException("AUTH_FORBIDDEN", "Committee or platform administrator permission is required");
        }
    }

    private static void requireRescheduleRequestable(CourseSession session, Instant now) {
        if (!now.isBefore(session.scheduledStartAt())) {
            throw new BusinessException(
                    "SESSION_ALREADY_STARTED", "Session reschedule request is allowed only before the session starts");
        }
        if (session.status() != CourseSession.Status.SCHEDULED
                && session.status() != CourseSession.Status.POSTPONED) {
            throw new BusinessException(
                    "STATE_TRANSITION_INVALID", "Only scheduled or postponed sessions can accept a reschedule request");
        }
    }

    private static void requireReviewDecision(ReviewDecision decision) {
        if (decision == null) {
            throw new BusinessException("VALIDATION_FAILED", "decision is required");
        }
    }

    private static void requireAttendanceDecision(AttendanceDecision attendance) {
        if (attendance == null) {
            throw new BusinessException("VALIDATION_FAILED", "attendance is required");
        }
    }

    private static void validateFutureRange(Instant startAt, Instant endAt, Instant now) {
        if (startAt == null || endAt == null || !startAt.isBefore(endAt)) {
            throw new BusinessException("VALIDATION_FAILED", "startAt must be before endAt");
        }
        if (!startAt.isAfter(now)) {
            throw new BusinessException("BOOKING_TIME_NOT_FUTURE", "Rescheduled start time must be in the future");
        }
    }

    private static BusinessException business(CourseOperationsDomainException ex) {
        String code = switch (ex.error()) {
            case INVALID_STATE -> "STATE_TRANSITION_INVALID";
            case SESSION_ALREADY_STARTED -> "SESSION_ALREADY_STARTED";
            case ACTOR_NOT_ALLOWED -> "AUTH_FORBIDDEN";
            case INVALID_TIME_RANGE, INVALID_CHANGE_PROPOSAL, INVALID_PARTICIPANT_COUNT, REQUIRED_FIELD -> "VALIDATION_FAILED";
        };
        return new BusinessException(code, ex.getMessage());
    }

    private static BusinessException concurrent(Exception ex) {
        return new BusinessException("CONCURRENT_MODIFICATION", ex.getMessage());
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    private static Map<String, Object> sessionSnapshot(CourseSession session) {
        return sessionSnapshot(session, null);
    }

    private static Map<String, Object> sessionSnapshot(CourseSession session, UUID changeRequestId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", session.id());
        snapshot.put("courseId", session.courseId());
        snapshot.put("sequenceNo", session.sequenceNo());
        snapshot.put("scheduledStartAt", session.scheduledStartAt());
        snapshot.put("scheduledEndAt", session.scheduledEndAt());
        snapshot.put("status", session.status().name());
        snapshot.put("cancellationSource", session.cancellationSource() == null ? null : session.cancellationSource().name());
        snapshot.put("cancellationNote", session.cancellationNote());
        snapshot.put("version", session.version());
        if (changeRequestId != null) snapshot.put("changeRequestId", changeRequestId);
        return snapshot;
    }

    private static Map<String, Object> enrollmentSnapshot(Enrollment enrollment) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", enrollment.id());
        snapshot.put("courseSessionId", enrollment.courseSessionId());
        snapshot.put("userId", enrollment.userId());
        snapshot.put("status", enrollment.status().name());
        snapshot.put("cancelledAt", enrollment.cancelledAt());
        snapshot.put("attendanceMarkedAt", enrollment.attendanceMarkedAt());
        snapshot.put("version", enrollment.version());
        return snapshot;
    }

    private static Map<String, Object> changeRequestSnapshot(SessionChangeRequest request) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", request.id());
        snapshot.put("courseSessionId", request.courseSessionId());
        snapshot.put("type", request.type().name());
        snapshot.put("status", request.status().name());
        snapshot.put("proposedStartAt", request.proposedStartAt());
        snapshot.put("proposedEndAt", request.proposedEndAt());
        snapshot.put("requestedBy", request.requestedBy());
        snapshot.put("decidedBy", request.decidedBy());
        snapshot.put("decidedAt", request.decidedAt());
        return snapshot;
    }

    private static Map<String, Object> changeRequestSnapshotBeforeDecision(SessionChangeRequest request) {
        Map<String, Object> snapshot = changeRequestSnapshot(request);
        snapshot.put("status", SessionChangeRequest.Status.PENDING.name());
        snapshot.put("decidedBy", null);
        snapshot.put("decidedAt", null);
        return snapshot;
    }

    public enum ReviewDecision { APPROVE, REJECT }
    public enum AttendanceDecision { ATTENDED, ABSENT }

    public record EnrollmentCancellationResult(
            Enrollment enrollment,
            MemberCancellationRecord cancellationRecord,
            CourseSession.Status courseSessionStatus) { }

    public record CourseCancellationReviewResult(
            CourseCancellationRequest request,
            CourseSession session) { }

    public record RescheduleResult(
            SessionChangeRequest request,
            CourseSession session) { }
}
