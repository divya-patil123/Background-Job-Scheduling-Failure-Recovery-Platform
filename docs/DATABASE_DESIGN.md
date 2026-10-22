# JobFlow database design

Create the database with `src/main/resources/db/create-database.sql`, then run `src/main/resources/db/schema.sql` inside the `jobflow` database. Hibernate uses `ddl-auto: validate` and does not mutate the schema.

## Tables and relationships

| Table | Purpose |
|---|---|
| `scheduled_jobs` | Job definition, task and schedule configuration, retry policy, next due time, single-instance execution lock, and consecutive failures. |
| `job_executions` | One row for each scheduled/manual trigger, with overall status, attempt count, timing, and next retry time. |
| `job_execution_attempts` | One row for each actual worker attempt, including status, timing, and a safe error summary. |
| `admin_alerts` | Persisted threshold alerts and acknowledgement state. |

Relationships: one scheduled job has many executions; one execution has many attempts; one job and one execution can each be referenced by an alert. The foreign keys restrict deleting jobs that have execution history. The API also refuses deletion while a job is active.

## Important columns and indexes

- IDs use MySQL auto-incrementing `BIGINT` primary keys.
- Enum values are stored as strings so database rows remain readable.
- `configuration_json` stores handler configuration as a MySQL JSON object.
- All persisted timestamps are UTC instants represented as microsecond timestamps.
- `idx_jobs_due` supports enabled/due-job discovery and lock checks.
- `idx_execution_job_created` supports per-job history.
- `idx_execution_status_retry` supports due retry discovery.
- `idx_execution_created` supports recent-execution ordering.
- `uq_attempt_number` prevents duplicate attempt numbers for one execution.
- `idx_alert_ack_created` supports listing recent/unacknowledged alerts.

## State semantics

- An **execution** is the lifecycle associated with one trigger.
- An **attempt** is one actual invocation of a task handler.
- A `RETRYING` execution remains associated with its original trigger and attempt rows. When its retry timestamp becomes due, it returns to `QUEUED`.
- `duration_ms` on an attempt is that attempt's duration. On an execution it is elapsed time from the first attempt start through final completion, including retry backoff periods.
- `next_run_at` is advanced atomically when the scheduler claims a scheduled job.

## Transaction boundaries

Claims and execution creation share a short transaction. Starting an attempt records `RUNNING` before task work begins. The handler runs with no database transaction held. Its result is committed in another short transaction, which also updates the job failure streak and possibly inserts an alert. This keeps long task execution from holding database locks.

## Sample queries

```sql
-- List the next due jobs
SELECT id, name, next_run_at
FROM scheduled_jobs
WHERE enabled = TRUE AND next_run_at <= UTC_TIMESTAMP(6)
ORDER BY next_run_at
LIMIT 100;

-- Inspect all attempts for one execution
SELECT attempt_number, status, started_at, ended_at, duration_ms, error_message
FROM job_execution_attempts
WHERE execution_id = 1
ORDER BY attempt_number;

-- View recent alerts awaiting acknowledgement
SELECT id, job_id, execution_id, message, created_at
FROM admin_alerts
WHERE acknowledged = FALSE
ORDER BY created_at DESC;
```

See [schema.sql](../src/main/resources/db/schema.sql) for the full DDL and [sample-data.sql](../samples/sample-data.sql) for development seed rows.
