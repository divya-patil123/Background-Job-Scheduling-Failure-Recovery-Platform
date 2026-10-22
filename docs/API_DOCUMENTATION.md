# JobFlow API

Base URL: `http://localhost:8080`. Request/response bodies use JSON. API errors share the `ApiErrorResponse` structure and do not contain stack traces.

## Job endpoints

| Method and path | Behavior |
|---|---|
| `POST /api/jobs` | Create a validated scheduled job; returns `201 Created`. |
| `GET /api/jobs` | List jobs. |
| `GET /api/jobs/{id}` | Read one job; missing IDs return `404`. |
| `PUT /api/jobs/{id}` | Replace job configuration and reschedule it; active jobs return `409`. |
| `PATCH /api/jobs/{id}/enabled` | Enable or disable with `{"enabled":true}`; active jobs return `409`. |
| `DELETE /api/jobs/{id}` | Delete only when there is no execution history and it is not active; otherwise `409`. |
| `POST /api/jobs/{id}/trigger` | Trigger manually and return `202 Accepted` with its execution. Disabled jobs can still be triggered manually. |

### Create job body

```json
{
  "name": "report-hourly",
  "description": "Generate the sample hourly report",
  "taskType": "REPORT",
  "scheduleType": "INTERVAL",
  "intervalSeconds": 3600,
  "configuration": {
    "reportName": "Hourly summary"
  },
  "maxRetries": 3,
  "initialRetryDelayMs": 1000,
  "maxRetryDelayMs": 60000,
  "failureThreshold": 3
}
```

For interval schedules, provide `intervalSeconds` from 1 to 31536000 and omit `cronExpression`. For cron schedules, provide `cronExpression` and omit `intervalSeconds`. Cron syntax uses Spring's six-field format and is interpreted in UTC. Retry delays are limited to 30 days.

Supported handler configuration:

| `taskType` | Required configuration |
|---|---|
| `NOTIFICATION` | `{"message":"non-empty text"}` (up to 2000 characters) |
| `REPORT` | `{"reportName":"non-empty name"}` (up to 120 characters) |
| `BATCH_PROCESSING` | `{"batchSize":100}` (integer 1–100000) |

Missing retry settings default to 3 retries, a 1000 ms initial delay, a 60000 ms maximum delay, and a failure threshold of 3. The scheduler enables created jobs by default.

## Execution endpoints

- `GET /api/jobs/{jobId}/executions?page=0&size=20` — paginated history for a job.
- `GET /api/executions?page=0&size=20&status=FAILED&jobId=1` — recent executions with optional filters.
- `GET /api/executions/{id}` — execution details plus the ordered attempt list.

Page size is 1–100. Results are sorted newest-first.

## Alert endpoints

- `GET /api/alerts?page=0&size=20` — paginated alerts, newest-first.
- `PATCH /api/alerts/{id}/acknowledge` — acknowledge an alert; returns the updated alert.

## Error responses

Validation failure (`400 Bad Request`):

```json
{
  "timestamp": "2026-01-01T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Request validation failed",
  "path": "/api/jobs",
  "validationErrors": {
    "name": "must not be blank"
  }
}
```

Other common statuses: `404` for missing jobs/executions/alerts, `409` for active-job or database conflicts and worker saturation, and `500` for unexpected server errors. Internal stack traces and database details are logged server-side, not returned in API error bodies.

## Documentation and monitoring

- Swagger UI: `/swagger-ui.html`
- OpenAPI document: `/v3/api-docs`
- Actuator: `/actuator/health`, `/actuator/info`, `/actuator/metrics`
