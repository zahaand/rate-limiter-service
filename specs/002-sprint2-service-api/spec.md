# Feature Specification: Sprint 2 — Service Layer & REST API

**Feature Branch**: `002-sprint2-service-api`
**Created**: 2026-05-05
**Status**: Draft
**Sprint Goal**: Service layer and REST API — fully working HTTP API with integration tests against real Redis.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Rate Limit Check via HTTP (Priority: P1)

A calling service sends a request to check whether a particular client key is within its rate
limit. It receives an immediate decision: allowed or rejected, how many requests remain, and
when the window resets. The caller alone decides what to do with the answer — the rate limiter
never blocks or rejects at the transport layer.

**Why this priority**: This is the primary public interface of the service. Every other
feature either supports it (config management, health) or enables it (service wiring). Nothing
delivers value until this endpoint works end-to-end.

**Independent Test**: Configure a limit of 3 per 10 seconds for key `"user-123"`. POST to
`/v1/check` three times and confirm each response carries `allowed: true` with a decrementing
`remaining`. POST a fourth time and confirm `allowed: false, remaining: 0`. Wait for the
window to expire and confirm the fifth call is allowed again. Testable against real Redis with
Testcontainers.

**Acceptance Scenarios**:

1. **Given** a policy of 3 requests per 10 seconds is configured for key `"user-123"`,
   **When** `POST /v1/check {"key": "user-123"}` is called three times within the window,
   **Then** each response is HTTP 200 with `allowed: true` and `remaining` decreasing from 2 to 0
2. **Given** the same key has exhausted its limit,
   **When** a fourth `POST /v1/check {"key": "user-123"}` arrives,
   **Then** the response is HTTP 200 with `allowed: false` and `remaining: 0`
3. **Given** no policy is stored for key `"new-key"`,
   **When** `POST /v1/check {"key": "new-key"}` is called,
   **Then** the service-wide default policy is applied and the response reflects that policy's
   window and limit
4. **Given** a request with an empty or blank key,
   **When** `POST /v1/check {"key": ""}` arrives,
   **Then** the response is HTTP 400 with body `{"error": "key must not be blank"}`

---

### User Story 2 — Rate Limit Policy Management (Priority: P2)

An operator needs to create, inspect, and remove per-key rate limit policies at runtime
without restarting the service. Policies determine which limit, window, and strategy apply to
any client key being checked.

**Why this priority**: Policy management enables per-key customisation, which is a core
product capability. It depends on the check endpoint (P1) and must be in place before
integration tests can cover custom-policy scenarios.

**Independent Test**: POST a new policy for key `"tenant-A"` to `/v1/limits`. GET
`/v1/limits/tenant-A` and confirm the response matches exactly. DELETE the same path. GET
again and confirm 404. Each operation testable in isolation against real Redis.

**Acceptance Scenarios**:

1. **Given** no policy exists for key `"tenant-A"`,
   **When** `POST /v1/limits {"key":"tenant-A","limit":100,"windowSeconds":60,"strategy":"FIXED_WINDOW"}` is called,
   **Then** the response is HTTP 201 with the same four fields echoed back
2. **Given** a policy exists for key `"tenant-A"`,
   **When** `GET /v1/limits/tenant-A` is called,
   **Then** the response is HTTP 200 with the stored limit, windowSeconds, and strategy
3. **Given** no policy exists for key `"unknown"`,
   **When** `GET /v1/limits/unknown` is called,
   **Then** the response is HTTP 404 with body `{"error": "policy not found for key: unknown"}`
4. **Given** a policy exists for key `"tenant-A"`,
   **When** `DELETE /v1/limits/tenant-A` is called,
   **Then** the response is HTTP 204 with no body, and a subsequent GET returns 404
5. **Given** no policy exists for key `"ghost"`,
   **When** `DELETE /v1/limits/ghost` is called,
   **Then** the response is HTTP 404 with body `{"error": "policy not found for key: ghost"}`
6. **Given** a `POST /v1/limits` request with `limit = -1`,
   **When** it arrives,
   **Then** the response is HTTP 400 with an error message describing the invalid field

---

### User Story 3 — Service Health Visibility (Priority: P3)

An operator or automated monitor can query a health endpoint to determine whether the service
and its Redis dependency are operational. Monitoring systems use the HTTP status code to
trigger alerts without parsing the body.

**Why this priority**: Operability is a hard requirement before going live but does not block
functional development. It depends on Redis connectivity being established (P1, P2).

**Independent Test**: With Redis running, GET `/health` and confirm HTTP 200 with both fields
`UP`. Stop the Redis container. GET `/health` again and confirm HTTP 503 with `redis: "DOWN"`
and `status: "DOWN"`.

**Acceptance Scenarios**:

1. **Given** Redis is reachable,
   **When** `GET /health` is called,
   **Then** the response is HTTP 200 with `{"status":"UP","redis":"UP"}`
2. **Given** Redis is unreachable (e.g., container stopped),
   **When** `GET /health` is called,
   **Then** the response is HTTP 503 with `{"status":"DOWN","redis":"DOWN"}`

---

### Edge Cases

- `POST /v1/check` with a key that contains only whitespace characters must be treated as blank
  and rejected with HTTP 400.
- `POST /v1/limits` with `limit = 0` is valid (all requests for that key will be rejected).
- `POST /v1/limits` with `windowSeconds = 0` must be rejected with HTTP 400.
- An unhandled server-side exception must produce HTTP 500 with
  `{"error": "internal server error"}` — never a raw stack trace.
- The health endpoint must not expose sensitive connection details in its response body.
- If the Redis PING takes longer than 1 second, the health check must treat it as a
  failure and return HTTP 503 — the endpoint must never hang waiting for Redis.
- Graceful shutdown must allow in-flight requests to complete and then release the Redis
  connection cleanly; no connection leak on restart.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST expose a `POST /v1/check` endpoint that accepts a client key,
  evaluates the applicable rate limit policy, and returns a decision containing: `allowed`
  (boolean), `remaining` (integer), and `resetAt` (ISO-8601 timestamp string).
- **FR-002**: The `POST /v1/check` endpoint MUST always respond with HTTP 200 regardless of
  whether the request is allowed or rejected. HTTP 429 is explicitly out of scope.
- **FR-003**: The `POST /v1/check` endpoint MUST reject requests where `key` is blank (empty
  or whitespace-only) with HTTP 400 and body `{"error": "key must not be blank"}`.
- **FR-004**: When no per-key policy is stored, the check MUST fall back to the service-wide
  default policy defined at startup. No error may surface to callers in this scenario.
- **FR-005**: Every REJECTED decision MUST be logged at **WARN** level with: the client key,
  strategy, limit, window duration, and the timestamp of the decision.
- **FR-006**: The system MUST expose a `POST /v1/limits` endpoint that creates or replaces a
  per-key policy and returns HTTP 201 with the stored policy echoed back.
- **FR-007**: The system MUST validate all `POST /v1/limits` fields:
  - `key` must not be blank
  - `limit` must be ≥ 0
  - `windowSeconds` must be > 0
  - `strategy` must be one of `FIXED_WINDOW`, `SLIDING_WINDOW`, `TOKEN_BUCKET`
  - Any violation MUST return HTTP 400 with `{"error": "<description>"}`.
- **FR-008**: The system MUST expose a `GET /v1/limits/{key}` endpoint that returns the stored
  policy for the key (HTTP 200) or HTTP 404 with `{"error": "policy not found for key: {key}"}`.
- **FR-009**: The system MUST expose a `DELETE /v1/limits/{key}` endpoint that removes the
  policy and returns HTTP 204, or HTTP 404 if no policy exists for that key.
- **FR-010**: The system MUST expose a `GET /health` endpoint that reports the operational
  status of the service and the Redis dependency.
- **FR-011**: The health endpoint MUST return HTTP 200 with `{"status":"UP","redis":"UP"}`
  when Redis is reachable, and HTTP 503 with `{"status":"DOWN","redis":"DOWN"}` when Redis
  is unreachable. No other status combinations are defined. The Redis PING MUST be issued
  with a hard timeout of **1 second**; if no response is received within that window the
  result is treated as unreachable.
- **FR-012**: Any unhandled exception MUST produce HTTP 500 with
  `{"error": "internal server error"}`. Raw stack traces MUST NOT be exposed in responses.
- **FR-013**: The service MUST release its Redis connection cleanly on shutdown. In-flight
  requests at the moment of shutdown MUST be allowed to complete before the connection is
  closed.

### Key Entities

- **Rate Check Request**: Inbound payload for `POST /v1/check` — a single non-blank client key
  identifying the subject to be rate-limited.
- **Rate Check Response**: Outbound decision — `allowed` (boolean), `remaining` (integer ≥ 0),
  `resetAt` (ISO-8601 UTC string).
- **Limit Config Request**: Inbound payload for `POST /v1/limits` — a client key, numeric limit
  (≥ 0), positive window in seconds, and a strategy name from the closed set.
- **Limit Config Response**: Outbound representation of a stored policy — same four fields as
  the request, always echoing exactly what is stored.
- **Health Response**: Outbound status report — `status` and `redis`, each either `"UP"` or
  `"DOWN"`.
- **Error Response**: Uniform error body — a single `error` string field describing the problem.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All integration test cases listed in the testing requirements pass against a
  real Redis instance with zero failures.
- **SC-002**: `POST /v1/check` correctly returns `allowed: false` after the configured limit is
  exhausted in 100% of test runs, including concurrent calls at the boundary.
- **SC-003**: `POST /v1/check` uses the service-wide default policy in 100% of cases where no
  per-key policy is stored — no error is surfaced, no fallback exception escapes.
- **SC-004**: All four CRUD operations on `/v1/limits` are consistent: a policy stored via
  POST is retrievable via GET with identical values, and is absent after DELETE.
- **SC-005**: Health endpoint returns the correct HTTP status code (`200` vs `503`) in both
  Redis-UP and Redis-DOWN scenarios, allowing monitoring tools to alert without body parsing.
- **SC-006**: No unhandled exception produces a raw stack trace in any HTTP response — all
  errors are mapped to structured JSON.
- **SC-007**: The application starts and stops without resource leaks — Redis connection is
  closed cleanly on every shutdown, including error paths.

## Assumptions

- Sprint 2 builds directly on the domain model, repository interfaces, and Redis
  implementations delivered in Sprint 1. Those contracts are stable and not subject to
  revision in this sprint.
- The service runs as a standalone JVM process with no reverse proxy or API gateway in front
  of it during integration testing.
- A single Redis instance (no clustering or sentinel) is sufficient for this sprint.
- Policy creation via `POST /v1/limits` is an upsert — if a policy for the key already exists,
  it is replaced. Conflict handling (HTTP 409) is out of scope.
- The `resetAt` timestamp in all responses is serialized as an ISO-8601 string in UTC
  (e.g., `"2026-05-05T03:45:00Z"`).
- There is no authentication or authorisation on any endpoint in this sprint. All endpoints
  are publicly accessible.
- The health check uses a Redis PING command to determine Redis availability. It does not
  attempt reconnection or retry — a single PING failure is sufficient to report `DOWN`.
- Logging of REJECTED decisions is fire-and-forget (best effort). A logging failure MUST NOT
  affect the rate-limit decision returned to the caller.
- Integration tests use Testcontainers with a real Redis container. A singleton container
  pattern is used across all test classes to reduce startup overhead. Redis state is cleared
  between individual tests via FLUSHDB.
- The HTTP framework is Ktor with coroutines. All Redis I/O uses suspend functions. JSON
  serialization uses kotlinx.serialization. Dependencies are injected via constructor only;
  no dependency injection framework is used.
- `val` is used over `var` throughout; the `!!` (non-null assertion) operator is not
  permitted anywhere in production or test code.

## Clarifications

### Session 2026-05-05

- Q: What timeout should the health check use when PINGing Redis? → A: 1 second hard timeout; failure to respond within 1 s is treated as unreachable.
- Q: Should API endpoints carry a version prefix? → A: Yes — `/v1/` prefix on data endpoints (`/v1/check`, `/v1/limits/{key}`); health endpoint remains bare (`/health`).
- Q: At what log level should REJECTED decisions be logged? → A: WARN — rejections are expected, non-exceptional outcomes and must not trigger alerting pipelines.
