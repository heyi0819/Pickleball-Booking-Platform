package com.pickleball.booking.receivable.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Narrow, fail-closed acceptance exception.  Production never enables the
 * zero-cost-pilot profile, so a true flag alone is insufficient.
 */
@Component
public class PilotRefundSelfReviewPolicy {
    private final boolean enabled;
    private final Environment environment;

    public PilotRefundSelfReviewPolicy(
            @Value("${app.pilot.refund-self-review.enabled:false}") boolean enabled,
            Environment environment) {
        this.enabled = enabled;
        this.environment = environment;
    }

    public boolean permits(boolean platformAdministrator) {
        return enabled
                && platformAdministrator
                && environment.matchesProfiles("zero-cost-pilot");
    }
}
