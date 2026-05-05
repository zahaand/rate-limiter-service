    # Feature Specification: Sprint 1 — Domain Model & Rate Limiting Infrastructure

**Feature Branch**: `feature/001-sprint1-domain-redis`
**Created**: 2026-05-05
**Status**: Draft
**Sprint Goal**: Domain model and rate-limiting algorithms fully operational with a green unit test suite.

## User Scenarios & Testing

### User Story 1 — Rate Limit Decision (Priority: P1)

An operator has configured a rate limit policy for a client key. When any request arrives
for that key, the system immediately evaluates whether it falls within the allowed limit and
returns a decision: allow or reject, how many requests remain, and when the window resets.

**Why this priority**: This is the core contract of the entire service. Nothing else works
without a functioning rate-limit decision engine.

**Independent Test**: Configure a limit of 10 per 60 seconds for a key. Submit 10 requests
and verify each is allowed. Submit an 11th and verify it is rejected. Advance time past 60
seconds and verify the key is allowed again. Fully testable with in-memory state.

**Acceptance Scenarios**:

1. **Given** a client key configured with limit 10 / 60 s, **When** the 10th request arrives
   within the window, **Then** the system allows it and returns `remaining = 0`
2. **Given** a client key that has exhausted its limit, **When** the 11th request arrives,
   **Then** the system rejects it and returns `allowed = false`
3. **Given** a client key that has hit its limit, **When** the configured window expires,
   **Then** the next request is allowed and the counter starts fresh

---

### User Story 2 — Rate-Limiting Strategy Selection (Priority: P2)

An operator can choose from three rate-limiting strategies for each client key: Fixed Window,
Sliding Window, or Token Bucket. Each strategy offers different fairness and burst-control
trade-offs; operators pick based on traffic patterns.

**Why this priority**: Strategy flexibility is a core product differentiator but depends on
US1 being complete first.

**Independent Test**: Configure the same key and limit under each strategy independently.
Verify Fixed Window resets at hard boundaries, Sliding Window uses a rolling count, and Token
Bucket refills proportionally. Each algorithm testable in isolation with no shared state.

**Acceptance Scenarios**:

1. **Given** a Fixed Window strategy (10 req / 60 s), **When** the window boundary is crossed,
   **Then** the counter resets to zero and up to 10 new requests are permitted
2. **Given** a Sliding Window strategy (10 req / 60 s), **When** a request arrives,
   **Then** only requests made within the last 60 seconds count toward the limit — no hard
   boundary reset occurs
3. **Given** a Token Bucket strategy (capacity 10, refill 10 per 60 s), **When** the bucket
   is empty and 30 seconds pass, **Then** 5 new tokens are available for consumption

---

### User Story 3 — Runtime Config Management (Priority: P3)

An operator can save, retrieve, and delete a rate limit policy for any client key at runtime
without restarting the service. If no policy is saved for a key, the system falls back to a
service-wide default.

**Why this priority**: Config persistence enables the runtime-configurable behaviour required
by the sprint goal; the HTTP API surface exposing this comes in a later sprint.

**Independent Test**: Save a config, retrieve it and verify values match. Delete it, retrieve
again and verify null is returned. Submit a check for a key with no config and verify the
default limit is applied. All testable in-memory.

**Acceptance Scenarios**:

1. **Given** no config exists for a key, **When** an operator saves a limit policy,
   **Then** subsequent retrieval returns the exact same limit and window values
2. **Given** a saved config, **When** an operator deletes it,
   **Then** the operation succeeds and retrieval returns nothing (null / absent)
3. **Given** no config exists for a key, **When** the system performs a rate check,
   **Then** the service-wide default limit is applied without error

---

### Edge Cases

- `limit = 0`: all requests are rejected immediately; remaining is always 0.
- `windowSeconds` must be a positive integer; zero or negative values are invalid and must be
  rejected at construction time.
- Concurrent requests at the exact limit boundary: atomicity must be guaranteed — no more
  requests than the configured limit can be allowed within a window.
- Token Bucket initial state: the bucket starts full (all tokens available on first request).
- Requests with equal timestamps in Sliding Window: all count as distinct entries.

## Requirements

### Functional Requirements

- **FR-001**: The system MUST evaluate a rate limit check for a given client key and policy,
  returning a decision that includes: whether the request is allowed, the number of requests
  remaining in the current window, and the timestamp when the window resets.
- **FR-002**: The system MUST support three selectable rate-limiting strategies: Fixed Window,
  Sliding Window, and Token Bucket. The set of strategies is closed; no open-ended extension
  point is required in Sprint 1.
- **FR-003**: All rate limit check operations MUST be atomic — concurrent requests for the
  same key MUST NOT collectively exceed the configured limit.
- **FR-004**: Fixed Window strategy MUST reset its counter at fixed wall-clock boundaries
  aligned to multiples of the window size (e.g., every 60 s at :00), not rolling from the
  first request.
- **FR-005**: Sliding Window strategy MUST count only requests made within the last N seconds
  from the current moment. There is no hard boundary reset.
- **FR-006**: Token Bucket strategy MUST refill tokens continuously at the rate of
  `capacity / windowSeconds` tokens per second. The internal token count MUST be tracked as
  a `Double` to avoid accumulated rounding drift. Tokens MUST NOT exceed capacity. The
  `remaining` field in `RateLimitDecision` MUST be the floor of the current token count
  (`toInt()`). The bucket MUST start full on first use.
- **FR-007**: The system MUST store rate limit policies per client key and support save,
  retrieve, and delete operations on those policies.
- **FR-008**: When no policy is stored for a client key, the system MUST apply a
  service-wide default policy defined at startup. The default policy MUST declare all three
  `RateLimitPolicy` fields (`limit`, `windowSeconds`, `strategy`) in `application.yaml` and
  be bound as a typed `data class` via Hoplite at startup. No error or fallback exception
  may surface to callers.
- **FR-009**: All domain model values MUST be immutable. Null values are not permitted in
  any domain model field; invalid construction arguments MUST be rejected immediately.
- **FR-010**: Unit tests MUST cover all three algorithms, each with the following scenarios:
  allow when under limit, reject when limit is reached, allow again after the window expires,
  and concurrent requests do not collectively exceed the limit.

### Key Entities

- **Client Key**: An opaque string identifier for a subject being rate-limited. Must be
  non-empty; no further format constraints in Sprint 1.
- **Rate Limit Policy**: The governing rule for a client key — a maximum allowed request
  count, the duration of the measurement window in seconds, and the rate-limiting strategy
  to apply. All three fields are required; `limit` and `windowSeconds` must be positive.
- **Rate Limit Decision**: The outcome of a check — whether the request is permitted (`allowed:
  Boolean`), how many more requests are allowed before the next rejection (`remaining: Int`),
  and the absolute moment the current window resets (`resetAt: java.time.Instant`).
- **Rate Limiting Strategy**: The algorithm governing how the count is tracked and reset.
  The strategy is a closed, exhaustive set: Fixed Window, Sliding Window, Token Bucket.

## Success Criteria

### Measurable Outcomes

- **SC-001**: A rate limit check for any of the three strategies returns a decision for
  every request — no unhandled exceptions, no missing fields.
- **SC-002**: Under concurrent load, the total number of allowed requests for a single
  client key never exceeds the configured limit for any strategy.
- **SC-003**: All unit test scenarios defined in FR-010 pass with zero failures across all
  three algorithm implementations.
- **SC-004**: Config save/retrieve/delete operations are fully consistent — the retrieved
  policy matches exactly what was saved; delete causes subsequent retrieval to return absent.
- **SC-005**: When no policy is stored for a key, the default limit is applied silently and
  correctly in 100% of cases — no error, no fallback exception surfaced to callers.

## Assumptions

- Sprint 1 is scoped to the domain model and repository layer only. The HTTP API exposing
  rate-check and config-management endpoints is out of scope for this sprint.
- All Sprint 1 tests use an in-memory repository implementation. Redis connectivity and
  Lua-script atomicity are validated in Sprint 2.
- The service-wide default policy (`limit`, `windowSeconds`, `strategy`) is fully declared
  in `application.yaml` and bound via Hoplite at startup. It is immutable at runtime (no
  API to change it without restart).
- Client keys are opaque strings; no format validation, ownership verification, or
  namespacing is performed in Sprint 1.
- The in-memory repository used in unit tests guarantees per-key atomicity via
  `kotlinx.coroutines.sync.Mutex` (one `Mutex` per client key). This suspends rather than
  blocks, aligning with Principle I, and mirrors the single-key atomicity that Redis Lua
  scripts will provide in Sprint 2. It does not replicate full Redis semantics.
- Token Bucket always starts with a full bucket regardless of when the service started.

## Clarifications

### Session 2026-05-05

- Q: Should the rate-limiting strategy be a field of `RateLimitPolicy`, or a separate dimension stored independently? → A: Strategy IS a field inside `RateLimitPolicy` (single stored object per key)
- Q: What type should the `resetAt` field in `RateLimitDecision` use? → A: `java.time.Instant` — absolute timestamp of the reset moment
- Q: What strategy does the service-wide default policy use? → A: Configurable — all three fields (`limit`, `windowSeconds`, `strategy`) declared in `application.yaml` via Hoplite
- Q: Does the Token Bucket track fractional tokens internally? → A: Yes — `Double` internally, floor-truncated to `Int` only when computing `remaining` in the response
- Q: What concurrency mechanism should the in-memory repository use for per-key atomicity? → A: `kotlinx.coroutines.sync.Mutex` per key — suspends the coroutine, aligns with Principle I
