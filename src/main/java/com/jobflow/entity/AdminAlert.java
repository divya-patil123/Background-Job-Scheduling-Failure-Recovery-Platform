package com.jobflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "admin_alerts", indexes = {
        @Index(name = "idx_alert_ack_created", columnList = "acknowledged,created_at")
})
public class AdminAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private ScheduledJob job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_id", nullable = false)
    private JobExecution execution;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(nullable = false)
    private boolean acknowledged;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    protected AdminAlert() {
    }

    public AdminAlert(ScheduledJob job, JobExecution execution, String message, int failureCount) {
        this.job = job;
        this.execution = execution;
        this.message = message;
        this.failureCount = failureCount;
        this.createdAt = Instant.now();
    }

    public void acknowledge() {
        if (!acknowledged) {
            acknowledged = true;
            acknowledgedAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public ScheduledJob getJob() { return job; }
    public JobExecution getExecution() { return execution; }
    public String getMessage() { return message; }
    public int getFailureCount() { return failureCount; }
    public boolean isAcknowledged() { return acknowledged; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
}
