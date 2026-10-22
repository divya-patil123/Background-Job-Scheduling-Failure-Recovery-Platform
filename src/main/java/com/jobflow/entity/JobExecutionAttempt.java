package com.jobflow.entity;

import com.jobflow.enums.AttemptStatus;
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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "job_execution_attempts", indexes = {
        @Index(name = "idx_attempt_execution", columnList = "execution_id,attempt_number")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_attempt_number", columnNames = {"execution_id", "attempt_number"})
})
public class JobExecutionAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_id", nullable = false)
    private JobExecution execution;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttemptStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    protected JobExecutionAttempt() {
    }

    public JobExecutionAttempt(JobExecution execution, int attemptNumber, Instant startedAt) {
        this.execution = execution;
        this.attemptNumber = attemptNumber;
        this.startedAt = startedAt;
        this.status = AttemptStatus.RUNNING;
    }

    public void succeed(Instant endedAt, long durationMs) {
        status = AttemptStatus.SUCCESS;
        this.endedAt = endedAt;
        this.durationMs = durationMs;
    }

    public void fail(Instant endedAt, long durationMs, String errorMessage) {
        status = AttemptStatus.FAILED;
        this.endedAt = endedAt;
        this.durationMs = durationMs;
        this.errorMessage = errorMessage;
    }

    public Long getId() { return id; }
    public JobExecution getExecution() { return execution; }
    public int getAttemptNumber() { return attemptNumber; }
    public AttemptStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public Long getDurationMs() { return durationMs; }
    public String getErrorMessage() { return errorMessage; }
}
