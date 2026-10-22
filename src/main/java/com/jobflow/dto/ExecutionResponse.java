package com.jobflow.dto;

import com.jobflow.enums.ExecutionStatus;
import com.jobflow.enums.TriggerType;
import java.time.Instant;

public record ExecutionResponse(
        long id,
        long jobId,
        String jobName,
        ExecutionStatus status,
        TriggerType triggerType,
        int attemptCount,
        Instant createdAt,
        Instant startedAt,
        Instant endedAt,
        Long durationMs,
        Instant nextRetryAt,
        String errorMessage) {
}
