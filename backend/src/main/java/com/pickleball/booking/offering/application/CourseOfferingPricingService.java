package com.pickleball.booking.offering.application;

import com.pickleball.booking.identity.application.AuthenticatedPrincipal;
import com.pickleball.booking.identity.application.IdentityService;
import com.pickleball.booking.identity.domain.RoleCode;
import com.pickleball.booking.offering.domain.CourseOffering;
import com.pickleball.booking.offering.domain.CourseOfferingPriceSnapshot;
import com.pickleball.booking.offering.domain.CourseOfferingSessionPlan;
import com.pickleball.booking.offering.domain.CourseOfferingStatus;
import com.pickleball.booking.offering.domain.OfferingDomainException;
import com.pickleball.booking.offering.infrastructure.CourseOfferingPersistenceAdapter;
import com.pickleball.booking.offering.infrastructure.CourseOfferingPriceSnapshotPersistenceAdapter;
import com.pickleball.booking.shared.application.AuditOutboxService;
import com.pickleball.booking.shared.application.BusinessException;
import com.pickleball.booking.shared.application.IdempotencyService;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CourseOfferingPricingService {
    private static final String PRICING_CONFIRMATION_OPERATION = "COURSE_OFFERING_PRICING_CONFIRMATION";
    private static final String PRICING_MODE = "MANUAL_PER_PARTICIPANT";

    private final CourseOfferingApplicationService core;
    private final CourseOfferingPersistenceAdapter offerings;
    private final CourseOfferingPriceSnapshotPersistenceAdapter prices;
    private final IdentityService identity;
    private final IdempotencyService idempotency;
    private final AuditOutboxService audit;

    public CourseOfferingPricingService(
            CourseOfferingApplicationService core,
            CourseOfferingPersistenceAdapter offerings,
            CourseOfferingPriceSnapshotPersistenceAdapter prices,
            IdentityService identity,
            IdempotencyService idempotency,
            AuditOutboxService audit) {
        this.core = core;
        this.offerings = offerings;
        this.prices = prices;
        this.identity = identity;
        this.idempotency = idempotency;
        this.audit = audit;
    }

    @Transactional
    public PricingPreview preview(
            AuthenticatedPrincipal actor,
            UUID offeringId,
            PriceCandidate candidate) {
        CourseOffering offering = requireCommitteeDraft(actor, offeringId, false);
        NormalizedPrice normalized = normalize(candidate.currency(), candidate.pricePerParticipant());
        return preview(offering, normalized);
    }

    @Transactional
    public CourseOfferingPriceSnapshot confirm(
            AuthenticatedPrincipal actor,
            UUID offeringId,
            String idempotencyKey,
            ConfirmPricingCommand command) {
        Objects.requireNonNull(command, "command");
        CourseOffering offering = requireCommitteeDraft(actor, offeringId, true);
        NormalizedPrice normalized = normalize(command.currency(), command.acceptedPricePerParticipant());
        String suppliedFingerprint = normalizeFingerprint(command.pricingFingerprint());
        String normalizedNote = normalizeOptional(command.confirmationNote());
        String requestIdentity = offeringId + "|" + normalized.currency() + "|"
                + normalized.pricePerParticipant().toPlainString() + "|" + suppliedFingerprint + "|"
                + Objects.toString(normalizedNote, "");
        var idem = idempotency.begin(
                offering.organizationId(), actor.userId(), PRICING_CONFIRMATION_OPERATION,
                idempotencyKey, requestIdentity);
        if (idem.getResultResourceId() != null) {
            return prices.findById(idem.getResultResourceId())
                    .orElseThrow(() -> new BusinessException(
                            "RESOURCE_NOT_FOUND", "Idempotent offering price result was not found"));
        }

        PricingPreview current = preview(offering, normalized);
        if (!current.pricingFingerprint().equals(suppliedFingerprint)) {
            throw new BusinessException(
                    "PRICE_CHANGED_RECALC_REQUIRED",
                    "Offering pricing inputs changed; preview and confirm the latest price again");
        }

        Map<String, Object> ruleTrace = new LinkedHashMap<>();
        ruleTrace.put("pricingMode", PRICING_MODE);
        ruleTrace.put("pricingFingerprint", current.pricingFingerprint());
        ruleTrace.put("currency", current.currency());
        ruleTrace.put("pricePerParticipant", current.pricePerParticipant().toPlainString());
        ruleTrace.put("billingMode", current.billingMode());
        ruleTrace.put("sessionCount", current.sessionCount());
        if (normalizedNote != null) {
            ruleTrace.put("confirmationNote", normalizedNote);
        }

        CourseOfferingPriceSnapshot draft = core.createPriceDraft(
                actor,
                offeringId,
                new CourseOfferingApplicationService.PriceCommand(
                        current.currency(), current.pricePerParticipant(), ruleTrace));
        CourseOfferingPriceSnapshot confirmed = core.confirmPrice(actor, offeringId, draft.id());
        idem.complete("CourseOfferingPriceSnapshot", confirmed.id(), 201);
        return confirmed;
    }

    /**
     * API-level DRAFT revision wrapper. The outer transaction makes the Offering revision and
     * confirmed-price invalidation atomic while preserving the existing S4.3 core service.
     */
    @Transactional
    public CourseOffering reviseDraft(
            AuthenticatedPrincipal actor,
            UUID offeringId,
            CourseOfferingApplicationService.DraftCommand command) {
        CourseOffering revised = core.reviseDraft(actor, offeringId, command);
        prices.findConfirmedByOfferingId(offeringId).ifPresent(current -> {
            try {
                current.supersede();
            } catch (OfferingDomainException ex) {
                throw new BusinessException("STATE_TRANSITION_INVALID", ex.getMessage());
            }
            prices.save(current);
            prices.flush();
            audit.record(
                    revised.organizationId(), actor.userId(), "COURSE_OFFERING_PRICE_SUPERSEDED",
                    "CourseOfferingPriceSnapshot", current.id(), "OFFERING_DRAFT_REVISED");
        });
        return revised;
    }

    private PricingPreview preview(CourseOffering offering, NormalizedPrice normalized) {
        String fingerprint = fingerprint(offering, normalized);
        return new PricingPreview(
                offering.id(),
                normalized.currency(),
                normalized.pricePerParticipant(),
                offering.spec().billingMode().name(),
                offering.sessionPlans().size(),
                fingerprint);
    }

    private CourseOffering requireCommitteeDraft(
            AuthenticatedPrincipal actor,
            UUID offeringId,
            boolean lock) {
        identity.requireActiveUser(actor.userId());
        CourseOffering offering = (lock ? offerings.findLockedById(offeringId) : offerings.findById(offeringId))
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Course offering was not found"));
        if (!identity.isAuthorizedForOrganization(actor, RoleCode.COMMITTEE, offering.organizationId())) {
            throw new BusinessException("AUTH_FORBIDDEN", "Committee or platform administrator permission is required");
        }
        if (offering.status() != CourseOfferingStatus.DRAFT) {
            throw new BusinessException(
                    "STATE_TRANSITION_INVALID", "Offering pricing can change only while offering is DRAFT");
        }
        return offering;
    }

    private NormalizedPrice normalize(String currency, BigDecimal amount) {
        if (currency == null || currency.isBlank()) {
            throw new BusinessException("VALIDATION_FAILED", "currency is required");
        }
        String normalizedCurrency = currency.trim().toUpperCase(Locale.ROOT);
        if (!normalizedCurrency.matches("[A-Z]{3}")) {
            throw new BusinessException("VALIDATION_FAILED", "currency must be a three-letter code");
        }
        if (amount == null || amount.signum() < 0) {
            throw new BusinessException("VALIDATION_FAILED", "pricePerParticipant must not be negative");
        }
        BigDecimal normalizedAmount;
        try {
            normalizedAmount = amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new BusinessException("VALIDATION_FAILED", "pricePerParticipant supports at most two decimals");
        }
        return new NormalizedPrice(normalizedCurrency, normalizedAmount);
    }

    private String normalizeFingerprint(String value) {
        if (value == null || !value.trim().matches("[0-9a-fA-F]{64}")) {
            throw new BusinessException("VALIDATION_FAILED", "pricingFingerprint must be a SHA-256 hex value");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String fingerprint(CourseOffering offering, NormalizedPrice normalized) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, offering.id());
        append(canonical, offering.organizationId());
        append(canonical, offering.spec().coachProfileId());
        append(canonical, offering.spec().title());
        append(canonical, offering.spec().description());
        append(canonical, offering.spec().scheduleType());
        append(canonical, offering.spec().billingMode());
        append(canonical, offering.spec().skillLevel());
        append(canonical, offering.spec().minimumParticipants());
        append(canonical, offering.spec().maximumParticipants());
        append(canonical, offering.spec().registrationOpenAt());
        append(canonical, offering.spec().registrationCloseAt());
        offering.sessionPlans().stream()
                .sorted(Comparator.comparingInt(CourseOfferingSessionPlan::sequenceNo))
                .forEach(session -> {
                    append(canonical, session.id());
                    append(canonical, session.sequenceNo());
                    append(canonical, session.startAt());
                    append(canonical, session.endAt());
                    append(canonical, session.venueId());
                    append(canonical, session.venueNameSnapshot());
                    append(canonical, session.venueAddressSnapshot());
                });
        append(canonical, normalized.currency());
        append(canonical, normalized.pricePerParticipant().toPlainString());
        return sha256(canonical.toString());
    }

    private void append(StringBuilder target, Object value) {
        String text = value == null ? null : value.toString();
        if (text == null) {
            target.append("-1:|");
        } else {
            target.append(text.length()).append(':').append(text).append('|');
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record PriceCandidate(String currency, BigDecimal pricePerParticipant) { }

    public record ConfirmPricingCommand(
            BigDecimal acceptedPricePerParticipant,
            String currency,
            String pricingFingerprint,
            String confirmationNote) { }

    public record PricingPreview(
            UUID offeringId,
            String currency,
            BigDecimal pricePerParticipant,
            String billingMode,
            int sessionCount,
            String pricingFingerprint) { }

    private record NormalizedPrice(String currency, BigDecimal pricePerParticipant) { }
}
