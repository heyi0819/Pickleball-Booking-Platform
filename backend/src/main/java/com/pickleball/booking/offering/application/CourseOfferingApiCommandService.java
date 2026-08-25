package com.pickleball.booking.offering.application;

import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.identity.application.IdentityService;
import com.pickleball.booking.identity.domain.RoleCode;
import com.pickleball.booking.offering.domain.CourseOffering;
import com.pickleball.booking.offering.domain.CourseOfferingStatus;
import com.pickleball.booking.offering.domain.OfferingDomainException;
import com.pickleball.booking.offering.domain.OfferingRegistration;
import com.pickleball.booking.offering.infrastructure.CourseOfferingPersistenceAdapter;
import com.pickleball.booking.offering.infrastructure.OfferingRegistrationPersistenceAdapter;
import com.pickleball.booking.shared.application.AuditOutboxService;
import com.pickleball.booking.shared.application.BusinessException;
import com.pickleball.booking.shared.application.IdempotencyService;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CourseOfferingApiCommandService {
    private static final String PUBLICATION_OPERATION = "COURSE_OFFERING_PUBLICATION";
    private static final String CLOSURE_OPERATION = "COURSE_OFFERING_CLOSURE";
    private static final String CANCELLATION_OPERATION = "COURSE_OFFERING_CANCELLATION";
    private static final String REGISTRATION_CANCELLATION_OPERATION = "COURSE_OFFERING_REGISTRATION_CANCELLATION";

    private final CourseOfferingApplicationService core;
    private final CourseOfferingPersistenceAdapter offerings;
    private final OfferingRegistrationPersistenceAdapter registrations;
    private final IdentityService identity;
    private final IdempotencyService idempotency;
    private final AuditOutboxService audit;

    public CourseOfferingApiCommandService(
            CourseOfferingApplicationService core,
            CourseOfferingPersistenceAdapter offerings,
            OfferingRegistrationPersistenceAdapter registrations,
            IdentityService identity,
            IdempotencyService idempotency,
            AuditOutboxService audit) {
        this.core = core;
        this.offerings = offerings;
        this.registrations = registrations;
        this.identity = identity;
        this.idempotency = idempotency;
        this.audit = audit;
    }

    @Transactional
    public CourseOffering publish(AuthenticatedPrincipal actor, UUID offeringId, String idempotencyKey) {
        CourseOffering current = requireCommitteeOffering(actor, offeringId);
        var idem = idempotency.begin(
                current.organizationId(), actor.userId(), PUBLICATION_OPERATION, idempotencyKey,
                offeringId + "|publication");
        if (idem.getResultResourceId() != null) {
            return loadOffering(idem.getResultResourceId());
        }
        CourseOffering published = core.publish(actor, offeringId);
        idem.complete("CourseOffering", published.id(), 200);
        return published;
    }

    @Transactional
    public CourseOffering close(AuthenticatedPrincipal actor, UUID offeringId, String idempotencyKey) {
        CourseOffering current = requireCommitteeOffering(actor, offeringId);
        var idem = idempotency.begin(
                current.organizationId(), actor.userId(), CLOSURE_OPERATION, idempotencyKey,
                offeringId + "|closure");
        if (idem.getResultResourceId() != null) {
            return loadOffering(idem.getResultResourceId());
        }
        CourseOffering closed = core.close(actor, offeringId);
        idem.complete("CourseOffering", closed.id(), 200);
        return closed;
    }

    @Transactional
    public CourseOffering cancelOffering(
            AuthenticatedPrincipal actor, UUID offeringId, String idempotencyKey, String reason) {
        CourseOffering current = requireCommitteeOffering(actor, offeringId);
        String normalizedReason = normalize(reason);
        var idem = idempotency.begin(
                current.organizationId(), actor.userId(), CANCELLATION_OPERATION, idempotencyKey,
                offeringId + "|cancellation|" + Objects.toString(normalizedReason, ""));
        if (idem.getResultResourceId() != null) {
            return loadOffering(idem.getResultResourceId());
        }

        CourseOffering cancelled = core.cancelOffering(actor, offeringId, normalizedReason);
        Instant now = Instant.now();
        for (OfferingRegistration registration : registrations.findLockedActiveByOfferingId(offeringId)) {
            try {
                registration.cancelForOffering(now, normalizedReason == null ? "OFFERING_CANCELLED" : normalizedReason);
            } catch (OfferingDomainException ex) {
                throw new BusinessException("STATE_TRANSITION_INVALID", ex.getMessage());
            }
            registrations.save(registration);
            audit.record(
                    cancelled.organizationId(), actor.userId(), "COURSE_OFFERING_REGISTRATION_CANCELLED_BY_OFFERING",
                    "OfferingRegistration", registration.id(), normalizedReason);
        }
        idem.complete("CourseOffering", cancelled.id(), 200);
        return cancelled;
    }

    @Transactional
    public OfferingRegistration register(
            AuthenticatedPrincipal actor, UUID offeringId, String idempotencyKey) {
        CourseOffering current = requireStudentOffering(actor, offeringId);
        Instant now = Instant.now();
        if (current.status() != CourseOfferingStatus.OPEN) {
            throw new BusinessException("OFFERING_NOT_OPEN", "Offering is not open for registration");
        }
        if (now.isBefore(current.spec().registrationOpenAt()) || !now.isBefore(current.spec().registrationCloseAt())) {
            throw new BusinessException("OFFERING_REGISTRATION_CLOSED", "Offering registration window is not active");
        }
        try {
            return core.register(actor, offeringId, idempotencyKey);
        } catch (BusinessException ex) {
            if ("ALREADY_REGISTERED".equals(ex.code())) {
                throw new BusinessException("OFFERING_ALREADY_REGISTERED", ex.getMessage());
            }
            if ("OFFERING_FULL".equals(ex.code())) {
                throw new BusinessException("OFFERING_CAPACITY_FULL", ex.getMessage());
            }
            throw ex;
        }
    }

    @Transactional
    public OfferingRegistration cancelRegistration(
            AuthenticatedPrincipal actor, UUID registrationId, String idempotencyKey, String reason) {
        identity.requireActiveUser(actor.userId());
        OfferingRegistration current = registrations.findById(registrationId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Offering registration was not found"));
        if (!current.userId().equals(actor.userId())) {
            throw new BusinessException("AUTH_FORBIDDEN", "Student can cancel only their own offering registration");
        }
        CourseOffering offering = requireStudentOffering(actor, current.courseOfferingId());
        String normalizedReason = normalize(reason);
        var idem = idempotency.begin(
                offering.organizationId(), actor.userId(), REGISTRATION_CANCELLATION_OPERATION, idempotencyKey,
                registrationId + "|cancellation|" + Objects.toString(normalizedReason, ""));
        if (idem.getResultResourceId() != null) {
            return registrations.findById(idem.getResultResourceId())
                    .orElseThrow(() -> new BusinessException(
                            "RESOURCE_NOT_FOUND", "Idempotent registration cancellation result was not found"));
        }
        OfferingRegistration cancelled = core.cancelRegistration(actor, registrationId, normalizedReason);
        idem.complete("OfferingRegistration", cancelled.id(), 200);
        return cancelled;
    }

    private CourseOffering requireCommitteeOffering(AuthenticatedPrincipal actor, UUID offeringId) {
        identity.requireActiveUser(actor.userId());
        CourseOffering offering = loadOffering(offeringId);
        if (!identity.isAuthorizedForOrganization(actor, RoleCode.COMMITTEE, offering.organizationId())) {
            throw new BusinessException("AUTH_FORBIDDEN", "Committee or platform administrator permission is required");
        }
        return offering;
    }

    private CourseOffering requireStudentOffering(AuthenticatedPrincipal actor, UUID offeringId) {
        identity.requireActiveUser(actor.userId());
        CourseOffering offering = loadOffering(offeringId);
        if (!identity.isAuthorizedForOrganization(actor, RoleCode.STUDENT, offering.organizationId())) {
            throw new BusinessException("AUTH_FORBIDDEN", "Active student role is required for this organization");
        }
        return offering;
    }

    private CourseOffering loadOffering(UUID offeringId) {
        return offerings.findById(offeringId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Course offering was not found"));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
