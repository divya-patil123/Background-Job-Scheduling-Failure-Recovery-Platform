package com.jobflow.dto;

import com.jobflow.enums.AttemptStatus;
import java.time.Instant;

public record AttemptResponse(
        long id,
        int attemptNumber,
        AttemptStatus status,
        Instant startedAt,
        Instant endedAt,
        Long durationMs,
        String errorMessage) {
}
