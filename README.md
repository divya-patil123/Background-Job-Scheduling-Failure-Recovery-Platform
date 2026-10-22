# JobFlow — Background Job Scheduling & Failure Recovery Platform

JobFlow is a single-instance background-job platform with a React operations console and a Spring Boot API. It manages scheduled work, dispatches tasks to a bounded worker pool, records execution attempts, retries failures, and raises persisted administrator alerts.

## Features

- REST APIs for job CRUD, enable/disable, manual triggers, execution history, and alerts.
- Interval and six-field cron schedules, persisted in MySQL and discovered by a polling scheduler.
- Controlled handlers for simulated notifications, sample reports, and sample batch processing.
- Bounded worker pool with explicit rejection behavior and graceful shutdown.
- Durable execution and per-attempt history, exponential retry backoff, and threshold-based alert generation.
- Single-instance database claim to prevent overlapping runs of the same job.
- Bean Validation, a global error response format, OpenAPI UI, and Actuator endpoints.
- A responsive React + TypeScript console, packaged in the executable Spring Boot JAR.
- Auth0 authentication with permission-based viewer, operator, and administrator access.

## Architecture and design

Controllers use DTOs and delegate to services. Job configuration and history use separate JPA entities and repositories. The scheduler identifies due work and atomically claims it before submission; workers perform task logic outside database transactions. A `JobExecution` is one trigger, while a `JobExecutionAttempt` is each try within that execution.

The detailed lifecycle, schema, API examples, and Java concepts are in:

- [Architecture](./docs/ARCHITECTURE.md)
- [Database design](./docs/DATABASE_DESIGN.md)
- [API documentation](./docs/API_DOCUMENTATION.md)
- [Project learnings](./docs/PROJECT_LEARNINGS.md)

## Technology stack

Java 17, Spring Boot 3.4.5, Spring Web, Spring Data JPA/Hibernate, MySQL 8, Bean Validation, Spring Scheduling, `ThreadPoolTaskExecutor`, Actuator, springdoc OpenAPI, Maven, React 18, TypeScript, Vite, Auth0 React SDK, JUnit 5, Mockito, and H2.

## Prerequisites

- JDK 17
- Maven 3.6.3 or newer
- Node.js 22 and npm 10 (the Maven build installs these into `target` automatically)
- MySQL 8 for the default application configuration

## Local development without MySQL

For development only, the opt-in `h2-development` Maven profile adds H2 at runtime. Start the app with the `dev-h2` Spring profile:

```powershell
mvn -Ph2-development spring-boot:run "-Dspring-boot.run.profiles=dev-h2"
```

Alternatively, package with `mvn -Ph2-development package` and run the JAR with:

```powershell
java -jar target/jobflow-1.0.0.jar --spring.profiles.active=dev-h2
```

This profile uses an in-memory database and creates its schema on startup. All jobs and execution history are temporary and are lost when the application stops. It is for local development and API exploration only; it does not verify MySQL compatibility and must not be used as a production database. Without this profile, the application continues to use the configured MySQL datasource and schema validation.

## Setup

1. Create the database using MySQL:

   ```sql
   SOURCE src/main/resources/db/create-database.sql;
   ```

2. Create the schema:

   ```sql
   USE jobflow;
   SOURCE src/main/resources/db/schema.sql;
   ```

3. Set connection values in the shell where Maven will run. `.env.example` documents variable names, but Spring Boot does not load `.env` files automatically.

   ```powershell
   $env:DB_URL = "jdbc:mysql://localhost:3306/jobflow?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
   $env:DB_USERNAME = "your-mysql-user"
   $env:DB_PASSWORD = "your-local-password"
   ```

4. Start the application:

   ```powershell
   mvn spring-boot:run
   ```

Hibernate validates the database schema at startup; it will not create or modify tables. Sample jobs can be inserted from [sample-data.sql](./samples/sample-data.sql).

## Auth0 authentication and roles

The single-page app uses Auth0 for sign-in; Spring Security validates Auth0 access tokens on API requests. For a production deployment:

1. Create an Auth0 **API** with an identifier such as `https://jobflow-api`, enable RBAC, and enable adding permissions to access tokens.
2. Define these API permissions and assign them to users/roles:
   - `jobflow:read` — read-only viewer access.
   - `jobflow:operate` — viewer access plus create, edit, enable/disable, trigger, and acknowledge alerts.
   - `jobflow:admin` — operator access plus job deletion.
3. Create an Auth0 Single Page Application. Add the deployed application origin to **Allowed Callback URLs**, **Allowed Logout URLs**, and **Allowed Web Origins** (for local use, `http://localhost:8080`).
4. Set the server environment variables below. `AUTH0_ISSUER_URI` must be the tenant issuer URL, including its trailing slash; `AUTH0_AUDIENCE` must exactly match the API identifier.
5. Start the app and open its origin. The browser requests an API access token; no Auth0 client secret is stored in the frontend or project files.

```powershell
$env:AUTH0_ENABLED = "true"
$env:AUTH0_ISSUER_URI = "https://your-tenant.region.auth0.com/"
$env:AUTH0_AUDIENCE = "https://jobflow-api"
$env:AUTH0_CLIENT_ID = "your-auth0-spa-client-id"
```

When Auth0 is disabled, API access is denied except when using the explicit `dev-h2` development profile. Configure Auth0 before exposing the application outside a trusted local development environment.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/jobflow?...` | MySQL JDBC URL |
| `DB_USERNAME` | Required | MySQL user |
| `DB_PASSWORD` | Required | MySQL password |
| `SERVER_PORT` | `8080` | HTTP port |
| `SCHEDULER_POLL_INTERVAL` | `1000` ms | Delay between scheduler polls |
| `SCHEDULER_BATCH_SIZE` | `100` | Maximum due jobs examined per poll |
| `WORKER_CORE_POOL_SIZE` | `4` | Core worker threads |
| `WORKER_MAX_POOL_SIZE` | `8` | Maximum worker threads |
| `WORKER_QUEUE_CAPACITY` | `100` | Bounded queue capacity |
| `WORKER_SHUTDOWN_WAIT_SECONDS` | `30` | Graceful worker shutdown wait |
| `AUTH0_ENABLED` | `false` | Enables Auth0 JWT validation and API authorization |
| `AUTH0_ISSUER_URI` | Empty | Auth0 tenant issuer URI (required when enabled) |
| `AUTH0_AUDIENCE` | Empty | Auth0 API identifier (required when enabled) |
| `AUTH0_CLIENT_ID` | Empty | Auth0 SPA client ID returned to the browser |

## API and monitoring

- OpenAPI UI: `http://localhost:8080/swagger-ui.html`
- Actuator: `/actuator/health`, `/actuator/info`, `/actuator/metrics`
- API examples and status codes: [API documentation](./docs/API_DOCUMENTATION.md)

### Create a scheduled notification job

```http
POST /api/jobs
Content-Type: application/json
```

```json
{
  "name": "nightly-notification",
  "taskType": "NOTIFICATION",
  "scheduleType": "INTERVAL",
  "intervalSeconds": 3600,
  "configuration": {
    "message": "Nightly processing completed"
  },
  "maxRetries": 3,
  "initialRetryDelayMs": 1000,
  "maxRetryDelayMs": 60000,
  "failureThreshold": 3
}
```

## Tests

Run all unit tests:

```powershell
mvn test
```

Run the opt-in MySQL integration test against a dedicated database with the schema already applied:

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/jobflow_it?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:DB_USERNAME = "your-mysql-user"
$env:DB_PASSWORD = "your-local-password"
mvn -Pmysql-integration verify
```

First create `jobflow_it` and apply `src/main/resources/db/schema.sql` to that database. The integration test validates that the connection is MySQL, round-trips JSON configuration and execution/attempt history, and races two independent transactions to ensure only one can claim a job. It creates uniquely named test rows and removes them afterward. Use a disposable database: schema creation is not automatic, and any rows already present in that database are retained.

Build and package the complete product. Maven installs the pinned Node/npm toolchain, type-checks and builds the frontend, copies its assets into Spring Boot's static-resource directory, and creates one executable JAR:

```powershell
mvn -Ph2-development clean package
```

The development build can be launched without MySQL or Auth0:

```powershell
java -jar target/jobflow-1.0.0.jar --spring.profiles.active=dev-h2
```

Open `http://localhost:8080`. For the frontend-only Vite development server, start the Spring Boot API on port 8080 and run `node .\node_modules\vite\bin\vite.js --host 127.0.0.1` from the `frontend` directory; the Vite proxy forwards `/api` requests to that backend.

The default suite focuses on retry delay calculations, scheduling calculations, task configuration validation, job validation, failure retry decisions, alert thresholds, and resetting the consecutive-failure count. `JobSchedulerRecoveryIntegrationTest` uses H2 to verify persisted restart recovery for queued, interrupted, and due-retry executions, successful completion after recovery, safe handling of a running execution without an attempt, and one-time retry claiming. The opt-in MySQL integration test is not part of the default suite; it requires the configured database above.

## Screenshots

<!-- Add API client or Swagger UI screenshots here when available. -->

## Reliability limitations

Execution state, attempt history, and retry timestamps are persisted in MySQL. On startup, JobFlow recovers queued executions, marks attempts left `RUNNING` as interrupted, and applies the normal retry limit/backoff policy. Due retries are claimed by a conditional database update, so one retry execution is only queued once. A `RUNNING` execution with no corresponding running attempt is failed and its job lock released instead of blindly replaying a potentially completed side effect.

Recovery is at-least-once, not exactly-once. A process can stop after an external side effect succeeds but before success is persisted; a restart may retry that attempt. External task handlers should pass the stable `TaskContext.idempotencyKey()` (the same for all attempts of one execution) to downstream systems that support idempotency, and should reconcile ambiguous outcomes where possible. The built-in handlers are simulations and do not perform external side effects. Recovery assumes one application instance: multiple instances need distributed coordination and fencing before production use. Scheduler polling advances missed schedules to the next future occurrence rather than replaying every missed interval.

## Future improvements

Possible follow-ups include distributed locking, task-specific idempotency keys, metrics for execution outcomes, email alert delivery, and database migration tooling.
