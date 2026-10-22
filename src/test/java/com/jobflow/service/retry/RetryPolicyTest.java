package com.jobflow.service.retry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    private final RetryPolicy retryPolicy = new RetryPolicy();

    @Test
    void calculatesExponentialBackoffAndCapsIt() {
        assertEquals(Duration.ofMillis(250), retryPolicy.delayForRetry(1, 250, 900));
        assertEquals(Duration.ofMillis(500), retryPolicy.delayForRetry(2, 250, 900));
        assertEquals(Duration.ofMillis(900), retryPolicy.delayForRetry(3, 250, 900));
        assertEquals(Duration.ofMillis(900), retryPolicy.delayForRetry(8, 250, 900));
    }

    @Test
    void supportsImmediateRetryWhenDelayIsZero() {
        assertEquals(Duration.ZERO, retryPolicy.delayForRetry(1, 0, 0));
    }

    @Test
    void rejectsInvalidAttemptOrDelayValues() {
        assertThrows(IllegalArgumentException.class, () -> retryPolicy.delayForRetry(0, 10, 100));
        assertThrows(IllegalArgumentException.class, () -> retryPolicy.delayForRetry(1, -1, 100));
    }
}
