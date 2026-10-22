USE jobflow;

INSERT INTO scheduled_jobs (
    name, description, task_type, schedule_type, interval_seconds, cron_expression,
    configuration_json, enabled, max_retries, initial_retry_delay_ms, max_retry_delay_ms,
    failure_threshold, consecutive_failures, next_run_at, execution_locked,
    lock_acquired_at, version, created_at, updated_at
) VALUES
(
    'sample-notification',
    'Simulate a periodic notification',
    'NOTIFICATION',
    'INTERVAL',
    300,
    NULL,
    JSON_OBJECT('message', 'Sample notification task completed'),
    TRUE,
    3,
    1000,
    60000,
    3,
    0,
    UTC_TIMESTAMP(6) + INTERVAL 5 MINUTE,
    FALSE,
    NULL,
    0,
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6)
),
(
    'sample-report',
    'Generate a sample report',
    'REPORT',
    'CRON',
    NULL,
    '0 0 * * * *',
    JSON_OBJECT('reportName', 'Hourly sample summary'),
    TRUE,
    2,
    2000,
    120000,
    2,
    0,
    UTC_TIMESTAMP(6) + INTERVAL 1 HOUR,
    FALSE,
    NULL,
    0,
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6)
);
