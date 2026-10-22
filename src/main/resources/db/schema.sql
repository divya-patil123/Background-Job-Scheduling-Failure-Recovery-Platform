CREATE TABLE scheduled_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000),
    task_type VARCHAR(40) NOT NULL,
    schedule_type VARCHAR(20) NOT NULL,
    interval_seconds BIGINT,
    cron_expression VARCHAR(120),
    configuration_json JSON NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    max_retries INT NOT NULL DEFAULT 0,
    initial_retry_delay_ms BIGINT NOT NULL DEFAULT 1000,
    max_retry_delay_ms BIGINT NOT NULL DEFAULT 60000,
    failure_threshold INT NOT NULL DEFAULT 3,
    consecutive_failures INT NOT NULL DEFAULT 0,
    next_run_at TIMESTAMP(6) NOT NULL,
    execution_locked BOOLEAN NOT NULL DEFAULT FALSE,
    lock_acquired_at TIMESTAMP(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_job_task_type CHECK (task_type IN ('NOTIFICATION', 'REPORT', 'BATCH_PROCESSING')),
    CONSTRAINT chk_job_schedule_type CHECK (schedule_type IN ('INTERVAL', 'CRON')),
    CONSTRAINT chk_job_schedule_values CHECK (
        (schedule_type = 'INTERVAL' AND interval_seconds BETWEEN 1 AND 31536000 AND cron_expression IS NULL)
        OR (schedule_type = 'CRON' AND interval_seconds IS NULL AND cron_expression IS NOT NULL)
    ),
    CONSTRAINT chk_job_retry_values CHECK (
        max_retries BETWEEN 0 AND 20
        AND initial_retry_delay_ms BETWEEN 0 AND 2592000000
        AND max_retry_delay_ms BETWEEN initial_retry_delay_ms AND 2592000000
        AND failure_threshold BETWEEN 1 AND 1000
    ),
    INDEX idx_jobs_due (enabled, next_run_at, execution_locked)
);

CREATE TABLE job_executions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    trigger_type VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    started_at TIMESTAMP(6) NULL,
    ended_at TIMESTAMP(6) NULL,
    duration_ms BIGINT NULL,
    next_retry_at TIMESTAMP(6) NULL,
    error_message VARCHAR(2000) NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_execution_status CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCESS', 'FAILED', 'RETRYING')),
    CONSTRAINT chk_execution_trigger CHECK (trigger_type IN ('SCHEDULED', 'MANUAL')),
    CONSTRAINT fk_execution_job FOREIGN KEY (job_id) REFERENCES scheduled_jobs (id),
    INDEX idx_execution_job_created (job_id, created_at),
    INDEX idx_execution_status_retry (status, next_retry_at),
    INDEX idx_execution_created (created_at)
);

CREATE TABLE job_execution_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    execution_id BIGINT NOT NULL,
    attempt_number INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP(6) NOT NULL,
    ended_at TIMESTAMP(6) NULL,
    duration_ms BIGINT NULL,
    error_message VARCHAR(2000) NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_attempt_status CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED')),
    CONSTRAINT fk_attempt_execution FOREIGN KEY (execution_id) REFERENCES job_executions (id),
    CONSTRAINT uq_attempt_number UNIQUE (execution_id, attempt_number),
    INDEX idx_attempt_execution (execution_id, attempt_number)
);

CREATE TABLE admin_alerts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_id BIGINT NOT NULL,
    execution_id BIGINT NOT NULL,
    message VARCHAR(500) NOT NULL,
    failure_count INT NOT NULL,
    acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL,
    acknowledged_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_alert_failure_count CHECK (failure_count > 0),
    CONSTRAINT fk_alert_job FOREIGN KEY (job_id) REFERENCES scheduled_jobs (id),
    CONSTRAINT fk_alert_execution FOREIGN KEY (execution_id) REFERENCES job_executions (id),
    INDEX idx_alert_ack_created (acknowledged, created_at)
);
