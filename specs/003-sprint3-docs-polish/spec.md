# Feature Specification: Sprint 3 — Documentation & Portfolio Polish

**Feature Branch**: `003-sprint3-docs-polish`
**Created**: 2026-05-12
**Status**: Draft
**Sprint Goal**: Final polish — interactive API documentation, developer code documentation, project README, and all deferred technical debt closed. Project ready for portfolio demonstration.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Interactive API Documentation (Priority: P1)

An API consumer or operator opens a browser, navigates to `/swagger`, and sees all five
endpoints of the rate limiter service listed with their request/response shapes, status codes,
and realistic examples. They can fill in a client key, click "Execute", and observe the live
response — without reading a single line of code or an external contract file.

**Why this priority**: The Swagger UI is the primary deliverable that makes the project
immediately understandable to a reviewer or interviewer. It also closes the gap between the
HTTP contract defined in Sprint 2 and what a visitor can actually discover and try.

**Independent Test**: Start the service against a local Redis instance. Open
`http://localhost:8080/swagger` in a browser. Confirm all five endpoints are visible with
their summaries, all documented response codes (200, 201, 204, 400, 404, 500), and at least
one named example per endpoint that uses realistic domain values (e.g., key `"tenant-api"`,
limit `100`, strategy `"FIXED_WINDOW"`). Execute `POST /v1/check` via the UI and confirm a
live response appears.

**Acceptance Scenarios**:

1. **Given** the service is running,
   **When** a visitor opens `/swagger` in a browser,
   **Then** all five endpoints are listed with an imperative-phrase summary (not a URL or
   HTTP method) and each endpoint's purpose is clear without prior knowledge of the API
2. **Given** a visitor is viewing `POST /v1/check` in Swagger UI,
   **When** they inspect the documented responses,
   **Then** HTTP 200 (decision returned), 400 (blank or too-long key), 415 (wrong
   Content-Type), and 500 (internal error) are all documented with accurate descriptions
3. **Given** a visitor is viewing `POST /v1/limits` in Swagger UI,
   **When** they inspect the documented responses,
   **Then** HTTP 201 (policy stored), 400 (validation error for any of the five violation
   types), 415 (wrong Content-Type), and 500 are all documented
4. **Given** a visitor is viewing `GET /v1/limits/{key}` or `DELETE /v1/limits/{key}`,
   **When** they inspect the documented responses,
   **Then** HTTP 200/204 (success), 404 (policy not found), and 500 are documented
5. **Given** a visitor is viewing `GET /health`,
   **When** they inspect the documented responses,
   **Then** HTTP 200 (service UP) and 503 (service DOWN, Redis unreachable) are documented
6. **Given** a visitor expands any endpoint in Swagger UI,
   **When** they look at request/response examples,
   **Then** at least one named example with realistic domain data is present (no placeholders
   like `"string"`, `0`, or `"example"`)
7. **Given** the service is running,
   **When** a visitor executes `POST /v1/check` via the Swagger UI "Try it out" feature,
   **Then** a live HTTP response with the correct shape is returned in the UI

---

### User Story 2 — Developer Code Documentation (Priority: P2)

A developer reading the source code wants to understand how each rate-limiting algorithm
works — specifically the non-obvious invariants: epoch-aligned window indexing, ZSET cutoff
boundaries, fractional token refill math, and Lua atomicity guarantees. They can read the
KDoc on the class or method and understand the contract and reasoning without reading the
Lua script or the Redis commands.

**Why this priority**: The algorithms are the intellectual core of the project and the most
likely focus of a technical interview or code review. KDoc makes the non-obvious reasoning
legible without sacrificing implementation detail.

**Independent Test**: Open `FixedWindowAlgorithm`, `SlidingWindowAlgorithm`, and
`TokenBucketAlgorithm` in an IDE. Without reading any implementation code, confirm that
the KDoc on each class/method explains: (a) the invariant being maintained, (b) at least
one non-obvious behavioral detail (window alignment, cutoff boundary, token floor), and
(c) the atomicity guarantee and why it matters. Open `RateLimiterService` and confirm the
KDoc describes the fallback behavior and WARN logging contract without referencing
implementation classes.

**Acceptance Scenarios**:

1. **Given** a developer opens `FixedWindowAlgorithm`,
   **When** they read the KDoc,
   **Then** the epoch-aligned window mechanism, the window index derivation, and the Lua
   atomicity guarantee are explained in terms of the invariant and its purpose
2. **Given** a developer opens `SlidingWindowAlgorithm`,
   **When** they read the KDoc,
   **Then** the ZSET-based sliding window, the cutoff boundary semantics, and the
   `resetAt` derivation are documented with their behavioral implications
3. **Given** a developer opens `TokenBucketAlgorithm`,
   **When** they read the KDoc,
   **Then** fractional token accumulation, the `floor` application, and the zero-rate
   guard are explained with their rationale
4. **Given** a developer opens `RateLimiterRepository` or `LimitConfigRepository`,
   **When** they read the KDoc,
   **Then** the atomicity guarantee, the contract for `delete` returning `Boolean`, and
   the fallback/error semantics are unambiguous
5. **Given** a developer opens `RateLimiterService`,
   **When** they read the KDoc,
   **Then** the orchestration role, the default-policy fallback, and the WARN-logging
   contract (five required fields, fire-and-forget) are described

---

### User Story 3 — Project README for Portfolio Demonstration (Priority: P3)

A first-time visitor to the repository (an interviewer, a recruiter, or a peer engineer)
reads the README and understands what the service does, how it fits into a wider
architecture, what the three rate-limiting algorithms are and when to use each, and how to
run the project locally within five minutes — without needing any prior context.

**Why this priority**: The README is the face of the project for asynchronous reviewers who
will not have a live walkthrough. A clear, complete README dramatically increases the
project's perceived quality.

**Independent Test**: Clone the repository to a clean machine. Follow only the README Quick
Start section. Confirm you can start Redis, start the service, and get a successful response
from `POST /v1/check` in under five minutes. Open the API Reference section and confirm
every `curl` example works without modification.

**Acceptance Scenarios**:

1. **Given** a visitor reads the README Overview,
   **When** they finish the first two paragraphs,
   **Then** they understand what the service does (rate limiting by client key), that it
   runs as a standalone HTTP service backed by Redis, and where it fits in a typical
   request pipeline
2. **Given** a visitor reads the Three Algorithms section,
   **When** they finish reading,
   **Then** they can articulate when to prefer Fixed Window vs. Sliding Window vs. Token
   Bucket, including the key trade-off for each
3. **Given** a visitor follows the Quick Start section,
   **When** they complete each step,
   **Then** they have a running service, can open Swagger UI at `/swagger`, and can
   execute a rate-limit check within five minutes on macOS or Linux
4. **Given** a visitor reads the API Reference section,
   **When** they copy-paste any `curl` example,
   **Then** it works against a locally running service without modification
5. **Given** a visitor reads the Configuration section,
   **When** they finish reading,
   **Then** they know what each field in `application.yaml` controls and what the
   defaults are

---

### User Story 4 — Technical Debt Closure (Priority: P4)

The concurrency correctness guarantee from Sprint 2 (SC-002) is verified by an automated
integration test. Sprint 2 task-tracking artifacts are corrected for ordering and have a
legend explaining inline reference codes, so future reviewers can read the task list without
confusion.

**Why this priority**: SC-002 was explicitly deferred and is the only unverified success
criterion from Sprint 2. The task-ordering fix and the legend are editorial corrections that
close the findings from the Sprint 2 `/speckit-analyze` report (findings F1 and F2).

**Independent Test**: Run `./gradlew test` on a machine with Docker Desktop running. Confirm
`ConcurrentCheckIT` passes: 50 simultaneous `POST /v1/check` calls for the same key with
`limit=10` produce exactly 10 `allowed=true` responses and 40 `allowed=false` responses, all
HTTP 200. Open `specs/002-sprint2-service-api/tasks.md` and confirm Phase 6 shows
T025b → T025 → T026 order with a dependency note, and the inline code legend appears at
the top of the file.

**Acceptance Scenarios**:

1. **Given** Docker Desktop is running and `./gradlew test` is executed,
   **When** `ConcurrentCheckIT` runs,
   **Then** all assertions pass: exactly 10 `allowed=true`, exactly 40 `allowed=false`,
   all 50 responses are HTTP 200 — in 100% of runs
2. **Given** a reviewer opens `specs/002-sprint2-service-api/tasks.md`,
   **When** they read Phase 6,
   **Then** T025b (write ConcurrentCheckIT) appears before T025 (run full test suite),
   with an explicit dependency note explaining the ordering
3. **Given** a reviewer reads any task in `specs/002-sprint2-service-api/tasks.md` that
   contains an inline code like `(G2)`, `(U1)`, or `(I1)`,
   **When** they look at the legend at the top of the file,
   **Then** the code is explained: G = Coverage Gap, U = Underspecification, I = Inconsistency

---

### Edge Cases

- `GET /swagger` must not interfere with or shadow any `/v1/*` API route or `/health`
- KDoc MUST NOT change any method signatures, return types, or behavioral contracts — it is
  purely additive
- The README Quick Start must not assume a globally installed Gradle wrapper; the repo
  already provides `./gradlew`
- `ConcurrentCheckIT` must call `FLUSHDB` in `@BeforeEach` to prevent interference from
  other integration tests that may run in the same JVM session
- The Swagger UI dependency must not introduce a conflicting version of any transitive
  dependency already present (e.g., kotlinx.serialization, Ktor plugins)
- README `curl` examples must use `localhost:8080` and work with the default
  `application.yaml` settings without any environment configuration
- The inline code legend in Sprint 2 tasks.md must be added without modifying any of the
  existing task IDs, descriptions, or completion markers (`[X]` / `[ ]`)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-017**: The system MUST serve Swagger UI at the `/swagger` path, rendering an
  OpenAPI-compliant description of all five endpoints: `POST /v1/check`,
  `POST /v1/limits`, `GET /v1/limits/{key}`, `DELETE /v1/limits/{key}`, and `GET /health`.
  The UI must be accessible from a browser without authentication.
- **FR-018**: Each documented endpoint MUST include: an imperative-phrase summary (not a URL
  or HTTP verb), a description, all applicable response status codes with descriptions, and
  at least one named example per request/response with realistic domain data. The minimum
  required status codes per endpoint are:
  - `POST /v1/check`: 200, 400, 415, 500
  - `POST /v1/limits`: 201, 400, 415, 500
  - `GET /v1/limits/{key}`: 200, 404, 500
  - `DELETE /v1/limits/{key}`: 204, 404, 500
  - `GET /health`: 200, 503
- **FR-019**: Examples in Swagger documentation MUST use realistic domain values. Prohibited
  placeholder values include: `"string"`, `0` for IDs or counts where non-zero is the
  typical case, `"example"`, unnamed or auto-generated example names, and empty objects.
- **FR-020**: KDoc MUST be written for the following high-priority classes/interfaces, each
  explaining the invariant, at least one non-obvious behavioral detail, and the atomicity
  guarantee where applicable: `FixedWindowAlgorithm`, `SlidingWindowAlgorithm`,
  `TokenBucketAlgorithm`, `RateLimiterRepository` interface, `RateLimiterService`.
- **FR-021**: KDoc MUST be written for the following medium-priority classes/interfaces with
  standard documentation covering purpose and contract: `RateLimitPolicy`, `RateLimitDecision`,
  the `Algorithm` sealed class hierarchy, `RedisRateLimiterRepository`,
  `LimitConfigRepository` interface.
- **FR-022**: The project `README.md` at the repository root MUST contain exactly the
  following nine sections in order: (1) header with one-line description, (2) overview,
  (3) three algorithms with trade-off table, (4) tech stack table, (5) package structure,
  (6) quick start, (7) API reference with `curl` examples for all five endpoints,
  (8) running tests, (9) configuration reference.
- **FR-023**: An automated integration test `ConcurrentCheckIT` MUST be implemented that
  launches 50 simultaneous coroutines each sending `POST /v1/check` for the same key with
  a policy of `limit=10` / `strategy=FIXED_WINDOW`, then asserts: exactly 10 responses
  have `allowed=true`, exactly 40 have `allowed=false`, all 50 have HTTP 200.
- **FR-024**: `specs/002-sprint2-service-api/tasks.md` Phase 6 MUST be reordered so that
  T025b (write `ConcurrentCheckIT`) precedes T025 (run full test suite), with an explicit
  dependency note added.
- **FR-025**: An inline code legend MUST be added near the top of
  `specs/002-sprint2-service-api/tasks.md`, defining: G = Coverage Gap, U = Underspecification,
  I = Inconsistency.

### Key Entities

- **OpenAPI Description**: Machine-readable contract describing all five endpoints, their
  schemas, response codes, and named examples. Served at `/openapi.json` or equivalent
  path consumed by the Swagger UI.
- **KDoc Entry**: A documentation block attached to a Kotlin class or function, describing
  purpose, parameters, return value, and non-obvious invariants. Does not appear in the
  compiled bytecode.
- **Integration Test**: An automated test that verifies behavioral correctness by exercising
  the full HTTP stack against a real Redis instance via Testcontainers.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-009**: A first-time visitor can open the Swagger UI at `/swagger`, find all five
  endpoints, read their purpose, and successfully execute at least one live call — without
  consulting any other documentation.
- **SC-010**: All five endpoints have every applicable error response code documented in
  Swagger, with no placeholder example values anywhere in the documentation.
- **SC-011**: A developer reading only the KDoc on each high-priority class can accurately
  describe the non-obvious behavioral invariant (window alignment, cutoff boundary, token
  floor, atomicity guarantee) without reading implementation code or Lua scripts.
- **SC-012**: A first-time visitor can clone the repository, follow the README Quick Start,
  and receive a successful `POST /v1/check` response within five minutes on macOS or Linux.
- **SC-013**: The concurrency correctness guarantee (SC-002 from Sprint 2) is verified by
  an automated test that passes in 100% of runs: 50 simultaneous requests with `limit=10`
  yield exactly 10 `allowed=true` responses.
- **SC-014**: The full test suite (`./gradlew test`) passes with zero failures, including
  `ConcurrentCheckIT`, when Docker Desktop is running.

## Assumptions

- Sprint 3 builds directly on the completed Sprint 1 and Sprint 2 deliverables. All Sprint 2
  integration tests (CheckRouteIT, LimitsRouteIT, HealthRouteIT) are already green on `main`.
- The Swagger UI library is added as a Gradle dependency; no changes to the HTTP routing
  logic, domain model, or service layer are required beyond adding the plugin and writing the
  OpenAPI description.
- KDoc is purely additive — no method signatures, return types, or behavioral contracts are
  changed. All existing tests remain green after KDoc is added.
- `ConcurrentCheckIT` uses the same `RedisTestContainer` singleton as the other IT classes
  and calls `FLUSHDB` in `@BeforeEach`. It is placed in the existing
  `src/test/kotlin/dev/zahaand/ratelimiter/integration/` package.
- The README targets developers on macOS or Linux with Docker Desktop, Java 21, and network
  access. Windows is out of scope for Quick Start instructions.
- All five endpoints documented in Swagger are the same five already implemented in Sprint 2.
  No new endpoints are added in Sprint 3.
- The ktor-swagger-ui library version is resolved at implementation time from Maven Central;
  the latest stable release compatible with Ktor 3.x is used.
- `FR-024` and `FR-025` (Sprint 2 tasks.md corrections) are editorial-only changes to the
  spec artifact, not to any production or test code.
- TD-02 (smoke tests) is a manual verification step, not an automated test. It is documented
  as a checklist entry in the plan rather than a testable assertion.
