package com.jobflow.entity;

import com.jobflow.enums.ExecutionStatus;
import com.jobflow.enums.TriggerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.Duration;

@Entity
@Table(name = "job_executions", indexes = {
        @Index(name = "idx_execution_job_created", columnList = "job_id,created_at"),
        @Index(name = "idx_execution_status_retry", columnList = "status,next_retry_at"),
        @Index(name = "idx_execution_created", columnList = "created_at")
})
public class JobExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private ScheduledJob job;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExecutionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private TriggerType triggerType;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    protected JobExecution() {
    }

    public JobExecution(ScheduledJob job, TriggerType triggerType) {
        this.job = job;
        this.triggerType = triggerType;
        this.status = ExecutionStatus.QUEUED;
        this.createdAt = Instant.now();
    }

    public void startAttempt(Instant now) {
        status = ExecutionStatus.RUNNING;
        attemptCount++;
        nextRetryAt = null;
        if (startedAt == null) {
            startedAt = now;
        }
    }

    public void succeed(Instant now) {
        status = ExecutionStatus.SUCCESS;
        endedAt = now;
        this.durationMs = elapsedSinceStart(now);
        nextRetryAt = null;
        errorMessage = null;
    }

    public void failFinally(Instant now, String errorMessage) {
        status = ExecutionStatus.FAILED;
        endedAt = now;
        this.durationMs = elapsedSinceStart(now);
        nextRetryAt = null;
        this.errorMessage = errorMessage;
    }

    public void retryAt(Instant retryAt, String errorMessage) {
        status = ExecutionStatus.RETRYING;
        nextRetryAt = retryAt;
        this.errorMessage = errorMessage;
    }

    public void markRejected(Instant now, String errorMessage) {
        status = ExecutionStatus.FAILED;
        endedAt = now;
        this.errorMessage = errorMessage;
    }

    private long elapsedSinceStart(Instant now) {
        return startedAt == null ? 0 : Math.max(0, Duration.between(startedAt, now).toMillis());
    }

    public Long getId() { return id; }
    public ScheduledJob getJob() { return job; }
    public ExecutionStatus getStatus() { return status; }
    public TriggerType getTriggerType() { return triggerType; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public Long getDurationMs() { return durationMs; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public String getErrorMessage() { return errorMessage; }
}
