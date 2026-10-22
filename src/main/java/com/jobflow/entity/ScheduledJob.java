package com.jobflow.entity;

import com.jobflow.enums.ScheduleType;
import com.jobflow.enums.TaskType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "scheduled_jobs", indexes = {
        @Index(name = "idx_jobs_due", columnList = "enabled,next_run_at,execution_locked")
})
public class ScheduledJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 40)
    private TaskType taskType;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", nullable = false, length = 20)
    private ScheduleType scheduleType;

    @Column(name = "interval_seconds")
    private Long intervalSeconds;

    @Column(name = "cron_expression", length = 120)
    private String cronExpression;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configuration_json", nullable = false, columnDefinition = "JSON")
    private Map<String, Object> configuration;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries;

    @Column(name = "initial_retry_delay_ms", nullable = false)
    private long initialRetryDelayMs;

    @Column(name = "max_retry_delay_ms", nullable = false)
    private long maxRetryDelayMs;

    @Column(name = "failure_threshold", nullable = false)
    private int failureThreshold;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;

    @Column(name = "execution_locked", nullable = false)
    private boolean executionLocked;

    @Column(name = "lock_acquired_at")
    private Instant lockAcquiredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected ScheduledJob() {
    }

    public ScheduledJob(String name, String description, TaskType taskType, ScheduleType scheduleType,
                        Long intervalSeconds, String cronExpression, Map<String, Object> configuration,
                        boolean enabled, int maxRetries,
                        long initialRetryDelayMs, long maxRetryDelayMs, int failureThreshold,
                        Instant nextRunAt) {
        this.name = name;
        this.description = description;
        this.taskType = taskType;
        this.scheduleType = scheduleType;
        this.intervalSeconds = intervalSeconds;
        this.cronExpression = cronExpression;
        this.configuration = Collections.unmodifiableMap(new LinkedHashMap<>(configuration));
        this.enabled = enabled;
        this.maxRetries = maxRetries;
        this.initialRetryDelayMs = initialRetryDelayMs;
        this.maxRetryDelayMs = maxRetryDelayMs;
        this.failureThreshold = failureThreshold;
        this.nextRunAt = nextRunAt;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void update(String name, String description, TaskType taskType, ScheduleType scheduleType,
                       Long intervalSeconds, String cronExpression, Map<String, Object> configuration,
                       int maxRetries, long initialRetryDelayMs,
                       long maxRetryDelayMs, int failureThreshold, Instant nextRunAt) {
        this.name = name;
        this.description = description;
        this.taskType = taskType;
        this.scheduleType = scheduleType;
        this.intervalSeconds = intervalSeconds;
        this.cronExpression = cronExpression;
        this.configuration = Collections.unmodifiableMap(new LinkedHashMap<>(configuration));
        this.maxRetries = maxRetries;
        this.initialRetryDelayMs = initialRetryDelayMs;
        this.maxRetryDelayMs = maxRetryDelayMs;
        this.failureThreshold = failureThreshold;
        this.nextRunAt = nextRunAt;
        this.updatedAt = Instant.now();
    }

    public void setEnabled(boolean enabled, Instant nextRunAt) {
        this.enabled = enabled;
        this.nextRunAt = nextRunAt;
        this.updatedAt = Instant.now();
    }

    public void recordFailure() {
        consecutiveFailures++;
        updatedAt = Instant.now();
    }

    public void recordSuccess() {
        consecutiveFailures = 0;
        updatedAt = Instant.now();
    }

    public void setExecutionLocked(boolean executionLocked, Instant lockAcquiredAt) {
        this.executionLocked = executionLocked;
        this.lockAcquiredAt = lockAcquiredAt;
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public TaskType getTaskType() { return taskType; }
    public ScheduleType getScheduleType() { return scheduleType; }
    public Long getIntervalSeconds() { return intervalSeconds; }
    public String getCronExpression() { return cronExpression; }
    public Map<String, Object> getConfiguration() { return configuration; }
    public boolean isEnabled() { return enabled; }
    public int getMaxRetries() { return maxRetries; }
    public long getInitialRetryDelayMs() { return initialRetryDelayMs; }
    public long getMaxRetryDelayMs() { return maxRetryDelayMs; }
    public int getFailureThreshold() { return failureThreshold; }
    public int getConsecutiveFailures() { return consecutiveFailures; }
    public Instant getNextRunAt() { return nextRunAt; }
    public boolean isExecutionLocked() { return executionLocked; }
    public Instant getLockAcquiredAt() { return lockAcquiredAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
