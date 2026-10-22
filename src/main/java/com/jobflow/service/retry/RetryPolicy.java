package com.jobflow.service.retry;

import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class RetryPolicy {

    public Duration delayForRetry(int failedAttemptNumber, long initialDelayMs, long maxDelayMs) {
        if (failedAttemptNumber < 1 || initialDelayMs < 0 || maxDelayMs < 0) {
            throw new IllegalArgumentException("Retry attempt and delay values must be non-negative");
        }
        if (initialDelayMs == 0 || maxDelayMs == 0) {
            return Duration.ZERO;
        }
        long delay = initialDelayMs;
        for (int i = 1; i < failedAttemptNumber && delay < maxDelayMs; i++) {
            if (delay > maxDelayMs / 2) {
                delay = maxDelayMs;
            } else {
                delay *= 2;
            }
        }
        return Duration.ofMillis(Math.min(delay, maxDelayMs));
    }
}
