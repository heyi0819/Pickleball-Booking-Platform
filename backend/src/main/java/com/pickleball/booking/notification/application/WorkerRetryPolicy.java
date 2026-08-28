package com.pickleball.booking.notification.application;

import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class WorkerRetryPolicy {
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration BASE_DELAY = Duration.ofSeconds(30);
    private static final Duration MAX_DELAY = Duration.ofMinutes(30);

    public boolean isDead(int attemptCount) {
        return attemptCount >= MAX_ATTEMPTS;
    }

    public Duration nextDelay(int attemptCount) {
        int exponent = Math.max(0, Math.min(attemptCount - 1, 10));
        long multiplier = 1L << exponent;
        Duration delay = BASE_DELAY.multipliedBy(multiplier);
        return delay.compareTo(MAX_DELAY) > 0 ? MAX_DELAY : delay;
    }

    public int maxAttempts() {
        return MAX_ATTEMPTS;
    }
}
