package com.pickleball.booking.receivable.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PilotRefundSelfReviewPolicyTest {
    @Test
    void failsClosedUnlessBothPilotControlsAndPlatformRoleArePresent() {
        assertThat(new PilotRefundSelfReviewPolicy(false, true).permits(true)).isFalse();
        assertThat(new PilotRefundSelfReviewPolicy(true, false).permits(true)).isFalse();
        assertThat(new PilotRefundSelfReviewPolicy(true, true).permits(false)).isFalse();
        assertThat(new PilotRefundSelfReviewPolicy(true, true).permits(true)).isTrue();
    }
}
