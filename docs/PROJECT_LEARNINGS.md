# Project learnings

## Spring layers and dependency injection

Controllers handle HTTP concerns, services own business rules, repositories perform persistence, and DTOs define the API boundary. Constructor injection makes dependencies explicit and allows focused tests with Mockito.

## Scheduling versus execution

Spring `@Scheduled` is used as a lightweight polling trigger. It discovers due database rows and sends work to a bounded `ThreadPoolTaskExecutor`; scheduler threads do not execute business tasks. The queue capacity and worker counts are backpressure controls. Rejection is an observable failure path, not silently discarded work.

## Jobs, executions, and attempts

- A **job** is a persistent definition and schedule.
- An **execution** is one scheduled or manual trigger.
- An **attempt** is one invocation during that execution.

This model keeps retry history without overwriting prior failures. A final success may therefore have earlier failed attempts in its history.

## Transactions and concurrency

The database claim uses a conditional update to ensure that two scheduler polls do not both claim a job. Transactions cover state changes, not task execution. Holding a transaction open during external work would increase lock duration and make failures harder to recover.

The lock is a single-instance safeguard, not a distributed lock. Multi-instance deployment needs distributed coordination. A process crash can occur after a side effect but before success persistence; retry can repeat that side effect, so JobFlow does not promise exactly-once execution.

## Retry and failure tracking

Backoff is calculated with capped exponential growth and stored as a future retry timestamp. Polling for due retries avoids blocking a scheduler thread with `sleep`. Final failed executions—not every temporary failed attempt—increment the consecutive-failure streak. Success resets it; reaching the threshold persists one alert for that streak.

## Validation, errors, and logging

Bean Validation catches malformed REST requests; task-specific validation checks controlled handler configuration before persistence. Global exception handling returns useful HTTP statuses without exposing stack traces or database credentials. Worker failures are logged with job and execution identifiers; API-visible error summaries remain safe.

## Persistence and schema

Entities represent persisted state; controllers return DTOs so API shape does not depend on JPA internals. MySQL foreign keys preserve history relationships, indexes support polling/history lookups, and `ddl-auto: validate` catches schema drift without applying production schema changes automatically.
