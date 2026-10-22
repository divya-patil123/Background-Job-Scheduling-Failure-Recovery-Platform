package com.jobflow.service;

import com.jobflow.enums.ScheduleType;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

@Component
public class SchedulingCalculator {

    public Instant nextRun(ScheduleType type, Long intervalSeconds, String cronExpression, Instant from) {
        if (type == ScheduleType.INTERVAL) {
            return from.plus(Duration.ofSeconds(intervalSeconds));
        }
        Instant next = CronExpression.parse(cronExpression).next(from.atZone(ZoneOffset.UTC)).toInstant();
        if (next == null) {
            throw new IllegalArgumentException("Cron expression has no next execution time");
        }
        return next;
    }
}
