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
5. **Given** a policy was previously set for key `"removed-key"` and then deleted via
   `DELETE /v1/limits/removed-key`,
   **When** `POST /v1/check {"key": "removed-key"}` is called,
   **Then** the service-wide default policy is applied immediately — no error, no 404

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
   **Then** the response is HTTP 400 with body `{"error": "limit must be >= 0"}`
7. **Given** a policy with `limit = 100` already exists for key `"tenant-A"`,
   **When** `POST /v1/limits {"key":"tenant-A","limit":5,"windowSeconds":30,"strategy":"SLIDING_WINDOW"}` is called,
   **Then** the response is HTTP 201 with the new values, and the previous policy is replaced
8. **Given** a policy with `limit = 0` is configured for key `"zero-limit"`,
   **When** `POST /v1/check {"key": "zero-limit"}` is called,
   **Then** the response is HTTP 200 with `allowed: false` and `remaining: 0`

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

- `POST /v1/check` with a blank key — where "blank" means empty string (`""`) or
  whitespace-only (`"   "`) — MUST be rejected with HTTP 400 and
  `{"error": "key must not be blank"}`. A missing `key` field in the JSON body is a
  deserialization failure and returns HTTP 400 with `{"error": "invalid request body"}`
  (FR-015), not "key must not be blank".
- `POST /v1/limits` with `limit = 0` is valid — all requests for that key will be rejected
  (`allowed: false, remaining: 0`). This is a legitimate configuration, not a validation error.
- `POST /v1/limits` with `windowSeconds = 0` must be rejected with HTTP 400.
- The `remaining` field in every check response is always ≥ 0. If concurrent requests race
  past the limit simultaneously, `remaining` returns 0 — it never goes negative.
- A client key is an opaque UTF-8 string with a maximum length of 512 characters. Keys
  exceeding 512 characters MUST be rejected with HTTP 400 and
  `{"error": "key must not exceed 512 characters"}` on both `POST /v1/check` and
  `POST /v1/limits`.
- An unhandled server-side exception must produce HTTP 500 with
  `{"error": "internal server error"}` — never a raw stack trace.
- The health endpoint must not expose sensitive connection details in its response body.
- If the Redis PING takes longer than 1 second, the health check must treat it as a
  failure and return HTTP 503 — the endpoint must never hang waiting for Redis.
- If the Redis PING succeeds but returns an unexpected non-error response (not `PONG`), the
  health check treats it as UP — only a timeout or connection error results in DOWN.
- Graceful shutdown must allow in-flight requests to complete (up to 5 seconds) and then
  release the Redis connection cleanly; no connection leak on restart.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST expose a `POST /v1/check` endpoint that accepts a client key,
  evaluates the applicable rate limit policy, and returns a decision containing: `allowed`
  (boolean), `remaining` (integer ≥ 0, never negative), and `resetAt` (ISO-8601 UTC string,
  seconds precision, Z suffix — e.g. `"2026-05-05T03:45:00Z"`).
- **FR-002**: The `POST /v1/check` endpoint MUST always respond with HTTP 200 when the request
  is structurally valid, regardless of whether the rate-limit decision is allowed or rejected.
  This applies only to valid check requests — it does not override validation errors (FR-003)
  or other failure responses. HTTP 429 is explicitly out of scope.
- **FR-003**: The `POST /v1/check` endpoint MUST reject requests where `key` is blank. "Blank"
  means the field is present but contains only whitespace or is an empty string. Both cases
  MUST return HTTP 400 with `{"error": "key must not be blank"}`. A missing `key` field is
  treated as a deserialization failure (see FR-015) and returns `{"error": "invalid request body"}`,
  not the blank-key message. `CheckRequest` keeps `key: String` (non-nullable); a missing
  field causes `BadRequestException` → FR-015 handler.
- **FR-004**: When no per-key policy is stored, the check MUST fall back to the service-wide
  default policy defined at startup. No error may surface to callers in this scenario.
- **FR-005**: Every REJECTED decision MUST be logged at **WARN** level as a structured JSON
  log entry containing exactly these five fields:
  - `key` — the client key identifier (string)
  - `strategy` — the strategy enum name (e.g. `"FIXED_WINDOW"`)
  - `limit` — the configured limit (integer)
  - `windowSeconds` — the configured window duration (integer)
  - `timestamp` — the ISO-8601 UTC timestamp of the decision (seconds precision, Z suffix)
  The LogstashEncoder (already on the classpath) produces structured JSON output for SLF4J
  log statements.
- **FR-006**: The system MUST expose a `POST /v1/limits` endpoint that creates or replaces a
  per-key policy and returns HTTP 201 with the stored policy echoed back.
- **FR-007**: The system MUST validate all `POST /v1/limits` fields and return HTTP 400 with
  `{"error": "<description>"}` for any violation. Exact error messages:
  - `key` blank → `{"error": "key must not be blank"}`
  - `key` longer than 512 characters → `{"error": "key must not exceed 512 characters"}`
  - `limit < 0` → `{"error": "limit must be >= 0"}`
  - `windowSeconds <= 0` → `{"error": "windowSeconds must be > 0"}`
  - `strategy` not in the closed set → `{"error": "strategy must be one of: FIXED_WINDOW, SLIDING_WINDOW, TOKEN_BUCKET"}`
  The same 512-character limit applies to `key` on `POST /v1/check` (FR-003 covers blank;
  this covers length).
- **FR-008**: The system MUST expose a `GET /v1/limits/{key}` endpoint that returns the stored
  policy for the key (HTTP 200) or HTTP 404 with `{"error": "policy not found for key: {key}"}`.
- **FR-009**: The system MUST expose a `DELETE /v1/limits/{key}` endpoint that removes the
  policy and returns HTTP 204, or HTTP 404 if no policy exists for that key.
  `ConfigRepository.delete` returns `false` exclusively when the key does not exist — no
  other failure mode returns `false`; infrastructure errors throw exceptions. If two
  concurrent DELETE requests arrive for the same key, exactly one returns 204 and the other
  returns 404 — guaranteed by the atomicity of the Redis `DEL` command (returns 0 when key
  is already absent).
- **FR-010**: The system MUST expose a `GET /health` endpoint that reports the operational
  status of the service and the Redis dependency.
- **FR-011**: The health endpoint MUST return HTTP 200 with `{"status":"UP","redis":"UP"}`
  when Redis is reachable, and HTTP 503 with `{"status":"DOWN","redis":"DOWN"}` when Redis
  is unreachable. No other status combinations are defined. The Redis PING MUST be issued
  with a hard timeout of **1 second**; if no response is received within that window the
  result is treated as unreachable. A PING that completes without error — even if the response
  string is not exactly `PONG` — is treated as UP.
- **FR-012**: Any unhandled exception MUST produce HTTP 500 with
  `{"error": "internal server error"}`. Raw stack traces MUST NOT be exposed in responses.
- **FR-013**: The service MUST release its Redis connection cleanly on shutdown. An in-flight
  request is any request that has entered the routing pipeline but has not yet returned a
  response. The service MUST wait up to **5 seconds** for in-flight requests to complete
  before closing the connection. After 5 seconds, the shutdown proceeds regardless.
- **FR-014**: `POST /v1/check` and `POST /v1/limits` MUST require `Content-Type: application/json`
  on the request. A request with a missing or incorrect `Content-Type` MUST return HTTP 415
  with `{"error": "unsupported media type"}`.
- **FR-015**: A request body that cannot be parsed as valid JSON on `POST /v1/check` or
  `POST /v1/limits` MUST return HTTP 400 with `{"error": "invalid request body"}`. This
  applies to syntactically invalid JSON only; field-level validation errors use their own
  messages (FR-003, FR-007).
- **FR-016**: Path segments in `GET /v1/limits/{key}` and `DELETE /v1/limits/{key}` are
  percent-decoded by the HTTP layer before use. The decoded value is treated as the client key.
  The service has no special handling for reserved characters — correct percent-encoding is the
  caller's responsibility.

### Key Entities

- **Client Key**: The client-facing rate limit identifier — an opaque UTF-8 string, max 512
  characters, non-blank. "Key" throughout this spec always refers to this external identifier.
  Internal Redis key names (`rl:fw:`, `rl:sw:`, `rl:tb:`, `config:` prefixes) are
  implementation details not part of the API contract.
- **Rate Check Request**: Inbound payload for `POST /v1/check` — a single non-blank client key
  identifying the subject to be rate-limited. Maximum 512 characters.
- **Rate Check Response**: Outbound decision — `allowed` (boolean), `remaining` (integer ≥ 0,
  never negative — concurrent races at the limit boundary always yield 0, not a negative value),
  `resetAt` (ISO-8601 UTC string, seconds precision, Z suffix; e.g. `"2026-05-05T03:45:00Z"`).
- **Limit Config Request**: Inbound payload for `POST /v1/limits` — a client key (max 512 chars,
  non-blank), numeric limit (≥ 0), positive window in seconds (> 0), and a strategy name from
  the closed set (`FIXED_WINDOW`, `SLIDING_WINDOW`, or `TOKEN_BUCKET`).
- **Limit Config Response**: Outbound representation of a stored policy — same four fields as
  the request, always echoing exactly what is stored.
- **Health Response**: Outbound status report — `status` and `redis`, each either `"UP"` or
  `"DOWN"`.
- **Error Response**: Uniform error body used for HTTP 400, 404, 415, and 500 —
  a single `error` string field describing the problem.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All integration test cases listed in the testing requirements pass against a
  real Redis instance with zero failures.
- **SC-002**: Under a concurrency test of 50 simultaneous `POST /v1/check` calls for the same
  key with `limit = 10`, exactly 10 responses carry `allowed: true` and the remaining 40 carry
  `allowed: false` — in 100% of test runs. No over-allowance occurs.
- **SC-003**: `POST /v1/check` uses the service-wide default policy in 100% of cases where no
  per-key policy is stored — no HTTP 500 response is produced, and no exception escapes to
  the caller. Observable boundary: no HTTP 500 response for a valid check request.
- **SC-004**: All four CRUD operations on `/v1/limits` are consistent: a policy stored via
  POST is retrievable via GET with identical values, and is absent after DELETE.
- **SC-005**: Health endpoint returns the correct HTTP status code (`200` vs `503`) in both
  Redis-UP and Redis-DOWN scenarios, allowing monitoring tools to alert without body parsing.
- **SC-006**: No unhandled exception produces a raw stack trace in any HTTP response — all
  errors are mapped to structured JSON.
- **SC-007**: The application starts and stops without resource leaks — the Redis connection
  is closed cleanly on every shutdown, including error paths. Observable signal: the
  `RedisClient` is confirmed closed (`.isOpen` returns `false`) after shutdown, and no
  connection-related exception is thrown during test teardown.
- **SC-008**: Every REJECTED rate-limit decision produces a WARN-level structured log entry
  containing all five required fields: `key`, `strategy`, `limit`, `windowSeconds`,
  `timestamp`. For Sprint 2 MVP, verification is by manual log output inspection.
  Programmatic assertion via a test log appender (e.g. Logback `ListAppender`) is deferred
  post-MVP and is not a Sprint 2 gate.

## Assumptions

- Sprint 2 builds directly on the domain model, repository interfaces, and Redis
  implementations delivered in Sprint 1. Those contracts are stable, with **one deliberate
  exception**: `ConfigRepository.delete` return type is changed from `Unit` to `Boolean` in
  Sprint 2. This is a planned Sprint 1 interface revision; `InMemoryConfigRepository` and its
  existing Sprint 1 tests (`InMemoryConfigRepositoryTest`) require updates to assert the
  Boolean return value. This is an explicit Sprint 2 implementation task.
- The service runs as a standalone JVM process with no reverse proxy or API gateway in front
  of it during integration testing.
- A single Redis instance (no clustering or sentinel) is sufficient for this sprint.
- Policy creation via `POST /v1/limits` is an upsert — if a policy for the key already exists,
  it is replaced. Conflict handling (HTTP 409) is out of scope.
- The `resetAt` timestamp in all responses is serialized as an ISO-8601 string in UTC,
  seconds precision, Z suffix (e.g. `"2026-05-05T03:45:00Z"`). Millisecond precision is not
  used.
- There is no authentication or authorisation on any endpoint in this sprint. All endpoints
  are publicly accessible.
- The health check uses a Redis PING command to determine Redis availability. It does not
  attempt reconnection or retry — a single PING failure is sufficient to report `DOWN`.
- Logging of REJECTED decisions is fire-and-forget (best effort). A logging failure is
  silently accepted — it MUST NOT affect the rate-limit decision or the HTTP response. No
  fallback, metric, or counter is required when logging fails.
- `RateLimitPolicy` (Sprint 1) enforces `limit ≥ 0` at construction. Sprint 2's route layer
  also validates `limit ≥ 0` in FR-007. This dual-layer validation is intentional: the domain
  model protects against internal misuse; the route validates external input explicitly.
- Path segments in `/v1/limits/{key}` are percent-decoded by the HTTP layer before use.
  Correct percent-encoding of reserved characters in key values is the caller's responsibility.
- `GET /health` is intentionally unversioned (bare `/health`, not `/v1/health`). Health
  endpoints are infrastructure probes consumed by Kubernetes liveness/readiness checks and
  load balancers, which expect a fixed, stable path independent of API versioning.
- The p99 latency target for `POST /v1/check` is < 50 ms when Redis is on the same network.
  This is a design target for awareness, not a tested acceptance criterion for Sprint 2 MVP.
- Observability for Sprint 2 is limited to WARN-level structured rejection logging (FR-005).
  Metrics, distributed tracing, and request-level access logs are explicitly out of scope and
  deferred to post-MVP.
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
- Q: Is `GET /health` intentionally unversioned? → A: Yes — health endpoints are infrastructure probes (Kubernetes, load balancers) that require a fixed stable path; versioning them would break standard tooling.
