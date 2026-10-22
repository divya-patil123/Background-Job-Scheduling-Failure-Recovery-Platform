package com.jobflow.dto;

import java.time.Instant;

public record AlertResponse(
        long id,
        long jobId,
        String jobName,
        long executionId,
        String message,
        int failureCount,
        boolean acknowledged,
        Instant createdAt,
        Instant acknowledgedAt) {
}
