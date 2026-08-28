package com.pickleball.booking.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class WorkerRetryPolicyTest {
    private final WorkerRetryPolicy policy = new WorkerRetryPolicy();

    @Test
    void usesBoundedExponentialBackoffAndDeadLettersAtMaxAttempts() {
        assertThat(policy.nextDelay(1)).isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.nextDelay(2)).isEqualTo(Duration.ofMinutes(1));
        assertThat(policy.nextDelay(5)).isEqualTo(Duration.ofMinutes(8));
        assertThat(policy.nextDelay(20)).isEqualTo(Duration.ofMinutes(30));
        assertThat(policy.isDead(4)).isFalse();
        assertThat(policy.isDead(5)).isTrue();
        assertThat(policy.maxAttempts()).isEqualTo(5);
    }
}
