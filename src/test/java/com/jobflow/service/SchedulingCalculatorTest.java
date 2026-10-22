package com.jobflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jobflow.enums.ScheduleType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SchedulingCalculatorTest {

    private final SchedulingCalculator calculator = new SchedulingCalculator();

    @Test
    void calculatesIntervalBasedNextRun() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        assertEquals(Instant.parse("2026-01-01T00:00:30Z"),
                calculator.nextRun(ScheduleType.INTERVAL, 30L, null, start));
    }

    @Test
    void calculatesCronNextRunInUtc() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        assertEquals(Instant.parse("2026-01-01T00:05:00Z"),
                calculator.nextRun(ScheduleType.CRON, null, "0 */5 * * * *", start));
    }
}
