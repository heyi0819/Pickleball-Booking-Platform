package com.pickleball.booking.receivable.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Narrow, fail-closed acceptance exception. Both explicit pilot controls must
 * be enabled; missing configuration always denies self-review.
 */
@Component
public class PilotRefundSelfReviewPolicy {
    private final boolean enabled;
    private final boolean zeroCostPilotEnabled;

    public PilotRefundSelfReviewPolicy(
            @Value("${app.pilot.refund-self-review.enabled:false}") boolean enabled,
            @Value("${app.pilot.zero-cost-pilot.enabled:false}") boolean zeroCostPilotEnabled) {
        this.enabled = enabled;
        this.zeroCostPilotEnabled = zeroCostPilotEnabled;
    }

    public boolean permits(boolean platformAdministrator) {
        return enabled
                && platformAdministrator
                && zeroCostPilotEnabled;
    }
}
