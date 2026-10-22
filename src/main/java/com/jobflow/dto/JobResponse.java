package com.jobflow.dto;

import com.jobflow.enums.ScheduleType;
import com.jobflow.enums.TaskType;
import java.time.Instant;
import java.util.Map;

public record JobResponse(
        long id,
        String name,
        String description,
        TaskType taskType,
        ScheduleType scheduleType,
        Long intervalSeconds,
        String cronExpression,
        Map<String, Object> configuration,
        boolean enabled,
        int maxRetries,
        long initialRetryDelayMs,
        long maxRetryDelayMs,
        int failureThreshold,
        int consecutiveFailures,
        Instant nextRunAt,
        Instant createdAt,
        Instant updatedAt) {
}
