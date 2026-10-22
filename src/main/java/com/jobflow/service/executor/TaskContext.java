package com.jobflow.service.executor;

public record TaskContext(long jobId, long executionId, int attemptNumber) {

    public String idempotencyKey() {
        return "jobflow-execution-" + executionId;
    }
}
