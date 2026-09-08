package com.pickleball.booking.receivable.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class PilotRefundSelfReviewPolicyTest {
    @Test
    void failsClosedUnlessThePilotProfileFlagAndPlatformRoleAreAllPresent() {
        MockEnvironment pilot = new MockEnvironment();
        pilot.setActiveProfiles("zero-cost-pilot");
        assertThat(new PilotRefundSelfReviewPolicy(false, pilot).permits(true)).isFalse();
        assertThat(new PilotRefundSelfReviewPolicy(true, new MockEnvironment()).permits(true)).isFalse();
        assertThat(new PilotRefundSelfReviewPolicy(true, pilot).permits(false)).isFalse();
        assertThat(new PilotRefundSelfReviewPolicy(true, pilot).permits(true)).isTrue();
    }
}
