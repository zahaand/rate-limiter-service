# Tasks: Sprint 3 — Documentation & Portfolio Polish

**Input**: Design documents from `specs/003-sprint3-docs-polish/`
**Prerequisites**: plan.md ✅ · spec.md ✅ · data-model.md ✅ · contracts/ ✅ · research.md ✅

**Tests**: ConcurrentCheckIT (T020) is the automated test for this sprint — it IS the
deliverable for US4. Swagger, KDoc, and README are verified manually per plan.md Test Cases
Checklist. Constitution Principle IV (Test-First) satisfied: T020 must precede T023 (full
test suite run).

**Organization**: Phases 1–2 are prerequisites for US1 (Swagger) only. US2 (KDoc), US3
(README), and US4 (Tech Debt) have no dependency on Phases 1–2 and may begin immediately
after Phase 1 is staged.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no shared state)
- **[US1/US2/US3/US4]**: User story this task belongs to

---

## Phase 1: Setup (Swagger Infrastructure)

**Purpose**: Add ktor-swagger-ui to the classpath. Prerequisite for all US1 work.
US2, US3, and US4 tasks may begin in parallel with this phase.

- [ ] T001 Add `val swaggerUiVersion = "5.7.0"` version variable and `implementation("io.github.smiley4:ktor-swagger-ui:$swaggerUiVersion")` to the dependencies block in `build.gradle.kts`; run `./gradlew dependencies | grep swagger` to confirm no transitive conflicts

---

## Phase 2: Foundational (Swagger Plugin Wiring)

**Purpose**: Install SwaggerUI plugin in `Application.module()`. Must be complete before
any `documentation { }` block can compile.

**⚠️ CRITICAL**: T002 depends on T001. US1 route documentation tasks (T003–T007) cannot
compile until this phase is complete.

- [ ] T002 Install `SwaggerUI` plugin in `Application.module()` in `src/main/kotlin/dev/zahaand/ratelimiter/Application.kt` — gate with `if (overrideConfig == null) { install(SwaggerUI) { ... } }` (prevents plugin activation under `testApplication`); configure title `"Rate Limiter Service API"`, version `"1.0.0"`, `swaggerUrl = "swagger"`, three tags (`Rate Limit`, `Policy Management`, `Observability`), server URL `"http://localhost:8080"` per plan.md § SwaggerUI Plugin; import `io.github.smiley4.ktorswaggerui.SwaggerUI`; verify exact DSL method names against library README before writing (see research.md Decision 2)

**Checkpoint**: `./gradlew build` compiles clean with SwaggerUI imported. `GET /swagger` returns HTML when service runs.

---

## Phase 3: User Story 1 — Interactive API Documentation (Priority: P1) 🎯

**Goal**: All five endpoints visible in Swagger UI at `/swagger` with imperative-phrase
summaries, all required response codes, named realistic examples. `/openapi.json` returns
valid OpenAPI JSON.

**Independent Test**: Start service with local Redis. Open `http://localhost:8080/swagger` —
confirm all 5 endpoints listed, summaries are imperative phrases, no placeholder values.
Execute `POST /v1/check` via "Try it out" — live response returned. `GET /openapi.json`
returns JSON. Existing routes `/v1/check`, `/v1/limits`, `/health` unchanged.

> ⚠️ **Verify library DSL before T003**: confirm exact method names in `documentation { }`,
> `request { }`, `response { }` blocks against ktor-swagger-ui 5.x README. The patterns
> in plan.md are known-stable but inner DSL names may differ. Verify once, then apply
> consistently to T003–T007.

- [ ] T003 [P] [US1] Add `documentation { }` block to `post("/check") { }` in `src/main/kotlin/dev/zahaand/ratelimiter/routes/CheckRoute.kt` — `operationId = "checkRateLimit"`, `summary = "Check rate limit for a client key"`, tag `"Rate Limit"`, request body `CheckRequest` with named examples `"tenant-api"` and `"user-session"`, responses: 200 with `CheckResponse` examples `"allowed"` / `"rejected"`, 400 `ErrorResponse` example `"blank-key"`, 415 example `"wrong-content-type"`, 500 example `"internal"` — per `contracts/swagger-annotations.md § POST /v1/check`; existing handler body unchanged
- [ ] T004 [US1] Add `documentation { }` block to `post("/limits") { }` in `src/main/kotlin/dev/zahaand/ratelimiter/routes/LimitsRoute.kt` — `operationId = "upsertPolicy"`, `summary = "Create or replace a rate limit policy"`, tag `"Policy Management"`, request body `LimitConfigRequest` with named examples `"fixed-window-policy"` and `"sliding-window-policy"`, responses: 201 `LimitConfigResponse` example `"tenant-a-policy"`, 400 `ErrorResponse` examples `"negative-limit"` / `"zero-window"` / `"bad-strategy"`, 415, 500 — per contracts/swagger-annotations.md
- [ ] T005 [US1] Add `documentation { }` block to `get("/limits/{key}") { }` in `src/main/kotlin/dev/zahaand/ratelimiter/routes/LimitsRoute.kt` — `operationId = "getPolicy"`, `summary = "Retrieve a rate limit policy by key"`, tag `"Policy Management"`, path param `key` (string, percent-decoded by Ktor routing), responses: 200 `LimitConfigResponse` example `"tenant-a-policy"`, 404 `ErrorResponse` example `"not-found"`, 500 example `"internal"` — per contracts/swagger-annotations.md
- [ ] T006 [US1] Add `documentation { }` block to `delete("/limits/{key}") { }` in `src/main/kotlin/dev/zahaand/ratelimiter/routes/LimitsRoute.kt` — `operationId = "deletePolicy"`, `summary = "Delete a rate limit policy by key"`, tag `"Policy Management"`, path param `key`, responses: 204 (no body/schema), 404 `ErrorResponse` example `"not-found"`, 500 example `"internal"` — per contracts/swagger-annotations.md
- [ ] T007 [P] [US1] Add `documentation { }` block to `get("/health") { }` in `src/main/kotlin/dev/zahaand/ratelimiter/routes/HealthRoute.kt` — `operationId = "getHealth"`, `summary = "Check service and Redis health"`, tag `"Observability"`, no request body, responses: 200 `HealthResponse` example `"healthy"`, 503 `HealthResponse` example `"degraded"` — per contracts/swagger-annotations.md; note: 503 not 500 for Redis-DOWN case
- [ ] T008 [US1] Manual verification: start service with `./gradlew run` and local Redis; open `http://localhost:8080/swagger` — confirm all 5 endpoints listed, all summaries are imperative phrases (not URLs or HTTP verbs), all required status codes visible per FR-018 matrix, no `"string"` / `"example"` / unnamed placeholder values anywhere; run `curl http://localhost:8080/openapi.json` and confirm valid JSON; run `curl -s -X POST http://localhost:8080/v1/check -H "Content-Type: application/json" -d '{"key":"test"}' | jq .allowed` and confirm `true` (existing route unaffected)

**Checkpoint**: `/swagger` interactive UI fully functional. SC-009 and SC-010 satisfied.

---

## Phase 4: User Story 2 — Developer Code Documentation (Priority: P2)

**Goal**: KDoc on 12 classes/interfaces — 5 high-priority (invariants + atomicity) and 7
medium-priority (contract + purpose). All existing tests remain green.

**Independent Test**: Open each high-priority class in IDE. Without reading implementation
code, verify KDoc explains the non-obvious invariant for that class (see data-model.md § KDoc
Scope Matrix for per-class requirements). Run `./gradlew test` — zero new failures.

> All T009–T018 touch different files — they may be executed in any order or in parallel.

- [ ] T009 [P] [US2] Add KDoc to `FixedWindowAlgorithm` object and `FixedWindowAlgorithm.State` data class in `src/main/kotlin/dev/zahaand/ratelimiter/domain/algorithm/FixedWindowAlgorithm.kt` — document: epoch-aligned window formula (`epochSecond / windowSeconds * windowSeconds` floors to nearest boundary in Unix time, not service-start time), new-window detection (`state.windowStart != windowStart` → reset count to 0), `resetAt = windowStart + windowSeconds`, pure-function contract (no I/O, state passed in and returned); `State`: `count` = requests in current window, `windowStart` = epoch-aligned start instant
- [ ] T010 [P] [US2] Add KDoc to `SlidingWindowAlgorithm` object in `src/main/kotlin/dev/zahaand/ratelimiter/domain/algorithm/SlidingWindowAlgorithm.kt` — document: cutoff `now - windowSeconds` with `>= cutoff` being inclusive (entry AT cutoff is within window), `resetAt` at-limit: `newTimestamps.first() + windowSeconds` (moment oldest entry leaves window), `resetAt` below-limit: `now` (no wait needed), pure function; Redis ZSET implementation in `RedisRateLimitRepository` handles persistence
- [ ] T011 [P] [US2] Add KDoc to `TokenBucketAlgorithm` object and `TokenBucketAlgorithm.State` data class in `src/main/kotlin/dev/zahaand/ratelimiter/domain/algorithm/TokenBucketAlgorithm.kt` — document: `refillRate = capacity / windowSeconds` (tokens/second), zero-rate guard (`windowSeconds > 0` check prevents division by zero; zero rate means bucket never refills), fractional accumulation capped at `capacity`, allow only if `refilled >= 1.0` (requires one full token), `remaining = newTokens.toInt()` floors via truncation; `State`: `tokens` is fractional double, `lastRefillAt` marks last refill timestamp
- [ ] T012 [P] [US2] Add KDoc to `RateLimitRepository` interface in `src/main/kotlin/dev/zahaand/ratelimiter/domain/port/RateLimitRepository.kt` — document: `check()` is atomic (Redis Lua EVAL executes check + increment as single transaction, no race between concurrent callers), `now: Instant` is explicit parameter for deterministic testing without mocking time, returns complete decision (no separate "record usage" call needed)
- [ ] T013 [P] [US2] Add KDoc to `RateLimiterService` class in `src/main/kotlin/dev/zahaand/ratelimiter/service/RateLimiterService.kt` — document: default-policy fallback (`configRepository.get(key.value) ?: appConfig.rateLimit.toPolicy()` — no 500 for unconfigured keys, always returns a decision), WARN log contract (5 required fields: `key`, `strategy`, `limit`, `windowSeconds`, `timestamp`; fire-and-forget — logging failure does NOT affect the decision or HTTP response), timestamp skew: log `timestamp` is `Instant.now()` called AFTER `check()` — close to but not identical to `decision.resetAt`
- [ ] T014 [P] [US2] Add KDoc to `RateLimitPolicy` data class in `src/main/kotlin/dev/zahaand/ratelimiter/domain/model/RateLimitPolicy.kt` — document: `limit = 0` is valid (all requests rejected — not a validation error), `init` block enforces `limit >= 0` and `windowSeconds > 0`, dual-layer validation rationale (domain guards against internal misuse; route layer validates external input separately and explicitly per FR-007)
- [ ] T015 [P] [US2] Add KDoc to `RateLimitDecision` data class in `src/main/kotlin/dev/zahaand/ratelimiter/domain/model/RateLimitDecision.kt` — document: `remaining >= 0` always (concurrent races at limit boundary floor at 0, never go negative), `resetAt` semantics differ by algorithm: next window start for Fixed Window, oldest-entry-expiry for Sliding Window, refill-time for Token Bucket
- [ ] T016 [P] [US2] Add KDoc to `RateLimitStrategy` sealed class and each `data object` in `src/main/kotlin/dev/zahaand/ratelimiter/domain/model/RateLimitStrategy.kt` — document: sealed class forces exhaustive `when` dispatch (compiler error if new subtype added without updating dispatch sites), `configName` is the canonical API string representation used in HTTP requests and Redis storage, `fromConfigName` uses lazy `byName` map for O(1) lookup; `error()` (not an exception) thrown for unknown names
- [ ] T017 [P] [US2] Add KDoc to `ConfigRepository` interface in `src/main/kotlin/dev/zahaand/ratelimiter/domain/port/ConfigRepository.kt` — document: `delete()` returns `Boolean` (`true` = key existed and was deleted, `false` = key not found — avoids a `get()+delete()` round trip for the 404 response on DELETE), `save()` is upsert (existing policy silently replaced), `get()` returns `null` for unconfigured keys (never throws for missing key)
- [ ] T018 [P] [US2] Add KDoc to `RedisRateLimitRepository` class in `src/main/kotlin/dev/zahaand/ratelimiter/infrastructure/redis/RedisRateLimitRepository.kt` — document: three Lua scripts in companion object (EVAL executes as single Redis transaction — no race between check and increment), key prefix scheme (`rl:fw:{key}:{windowStart}` one key per window period, `rl:sw:{key}` ZSET + `rl:sw:{key}:seq` sequence counter, `rl:tb:{key}` HMSET), Fixed Window uses `EXPIREAT resetAt` (auto-expires at window end), Sliding Window uses `ZREMRANGEBYSCORE` with exclusive lower bound for cleanup, Token Bucket uses `EXPIRE windowSeconds * 2` buffer

**Checkpoint**: All 12 KDoc targets documented. `./gradlew test` passes with zero failures.

---

## Phase 5: User Story 3 — Project README (Priority: P3)

**Goal**: Complete `README.md` at repository root with all 9 sections per FR-022. A first-time
visitor can clone, start service, and send their first check request in under 5 minutes.

**Independent Test**: Clone to a clean directory. Follow only the Quick Start section.
Receive a successful `POST /v1/check` response. All `curl` examples work without modification.

- [ ] T019 [US3] Create `README.md` at repository root with exactly these 9 sections in order: (1) header `# Rate Limiter Service` + one-line description; (2) Overview — what it does, standalone HTTP service backed by Redis, where it fits in a request pipeline; (3) Three Algorithms — Fixed Window / Sliding Window / Token Bucket with when-to-use and trade-off table columns: Algorithm | Model | Memory | Reset; (4) Tech Stack — Kotlin 2.1.20, Ktor 3.1.3, Lettuce, Redis, Hoplite, Logback+logstash, JUnit 5, Testcontainers; (5) Package Structure — top-level packages with one-line description each (`domain/algorithm`, `domain/model`, `domain/port`, `infrastructure/redis`, `infrastructure/memory`, `infrastructure/config`, `service`, `routes`, `routes/dto`); (6) Quick Start — prerequisites (Java 21, Docker Desktop), start Redis (`docker run -d -p 6379:6379 redis:7-alpine`), run service (`./gradlew run`), open Swagger (`http://localhost:8080/swagger`); (7) API Reference — `curl` examples for all 5 endpoints using `localhost:8080` with no env vars (copy from `specs/003-sprint3-docs-polish/quickstart.md` § Smoke Tests); (8) Running Tests — unit (no Docker: `./gradlew test --tests "dev.zahaand.ratelimiter.domain.*"`), integration (Docker required: `./gradlew test`), note ConcurrentCheckIT verifies atomicity; (9) Configuration — table of `application.yaml` fields with name, default, and description (`server.port=8080`, `redis.host=localhost`, `redis.port=6379`, `rateLimit.defaultLimit=100`, `rateLimit.defaultWindowSeconds=60`, `rateLimit.defaultStrategy=FIXED_WINDOW`)

**Checkpoint**: README.md complete. SC-012 satisfied (clone + Quick Start ≤ 5 min on macOS/Linux).

---

## Phase 6: User Story 4 — Technical Debt Closure (Priority: P4)

**Goal**: SC-002 (concurrency correctness) verified by automated test. Sprint 2 tasks.md
corrected for Phase 6 ordering and inline code legend.

**Independent Test**: `./gradlew test --tests "*.ConcurrentCheckIT"` passes with exactly 10
`allowed=true` and 40 `allowed=false` in 100% of runs. Sprint 2 tasks.md shows T025b before
T025 with dependency note, and legend at top of Format section.

- [ ] T020 [US4] Write `ConcurrentCheckIT` in `src/test/kotlin/dev/zahaand/ratelimiter/integration/ConcurrentCheckIT.kt` — use existing `RedisTestContainer` singleton; `@BeforeEach` FLUSHDB (same pattern as `CheckRouteIT`); `testConfig = AppConfig(ServerConfig(0), RedisConfig(host, port), RateLimitDefaults())`; before launching coroutines, set policy via direct Redis HSET: `"config:concurrent-test"` → `{limit:"10", windowSeconds:"60", strategy:"FIXED_WINDOW"}`; test body: `runTest { testApplication { application { module(testConfig) } ... } }`, launch `(1..50).map { async { client.post("/v1/check") { contentType(ContentType.Application.Json); setBody("""{"key":"concurrent-test"}""") } } }.awaitAll()`; assert `results.all { it.status == HttpStatusCode.OK }`, `bodies.count { it["allowed"]!!.jsonPrimitive.boolean } == 10`, `bodies.count { !it["allowed"]!!.jsonPrimitive.boolean } == 40`; run `./gradlew test --tests "*.ConcurrentCheckIT"` and confirm passes — closes SC-002 / SC-013
- [ ] T021 [US4] Fix Phase 6 task ordering in `specs/002-sprint2-service-api/tasks.md` — move T025b (write ConcurrentCheckIT) to appear before T025 (run full test suite) in the Phase 6 section; add dependency note immediately before T025: `> ⚠️ **Dependency**: T025b must complete before T025 — ConcurrentCheckIT must exist before the full test suite can validate SC-002.`; do NOT modify any task ID, description, or `[X]`/`[ ]` marker — editorial only (FR-024)
- [ ] T022 [US4] Add inline code legend to `specs/002-sprint2-service-api/tasks.md` immediately after the `## Format: [ID] [P?] [Story?] Description` heading — insert: `**Inline code legend**: G = Coverage Gap (spec vs. tasks), U = Underspecification (vague or missing requirement detail), I = Inconsistency (conflict between artifacts)`; do NOT modify any task ID, description, or `[X]`/`[ ]` marker — editorial only (FR-025)

**Checkpoint**: ConcurrentCheckIT green. Sprint 2 tasks.md editorial fixes applied. SC-013 satisfied.

---

## Phase 7: Polish & Verification

**Purpose**: Confirm all deliverables are correct and the full test suite passes.

- [ ] T023 [P] Run full test suite `./gradlew test` with Docker Desktop running — confirm ALL unit tests (domain algorithms, model, memory infrastructure) and ALL integration tests (CheckRouteIT, LimitsRouteIT, HealthRouteIT, ConcurrentCheckIT) pass with zero failures; satisfies SC-014
- [ ] T024 [P] Run manual smoke tests per `specs/003-sprint3-docs-polish/quickstart.md` § Smoke Tests — all 7 checks pass; confirm WARN-level structured JSON log appears in service output on the 4th request (SC-008 manual verification from Sprint 2); confirm Swagger UI quality: CHK001–CHK015 from `checklists/documentation-review.md` all pass

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 (T001) — **BLOCKS US1 route documentation only**
- **Phase 3 (US1)**: Depends on Phase 2 — Swagger route tasks (T003–T007) require SwaggerUI on classpath
- **Phase 4 (US2)**: **Independent of Phases 1–2** — KDoc edits compile without ktor-swagger-ui; may begin in parallel with Phase 1
- **Phase 5 (US3)**: **Independent of all other phases** — README is a new text file; may begin immediately
- **Phase 6 (US4)**: **Independent of Phases 1–2** — ConcurrentCheckIT and Sprint 2 editorial have no Swagger dependency; T021 must precede T022 (same file); T020 must precede T023
- **Phase 7 (Polish)**: Depends on all phases complete — T020 must precede T023

### User Story Dependencies

- **US1 (P1)**: Depends on Phase 2 (T002) — first to need Swagger on classpath
- **US2 (P2)**: Independent — can start after T001 (or even before)
- **US3 (P3)**: Independent — can start any time
- **US4 (P4)**: Independent for T020; T021 → T022 sequential (same file)

### Within Each Phase

1. **US1**: T002 (plugin install) → T003/T007 [P] → T004 → T005 → T006 → T008 (manual)
2. **US2**: T009–T018 all [P] — any order
3. **US3**: T019 (single task)
4. **US4**: T020 [P relative to T021/T022] → T021 → T022; T020 must precede T023
5. **Polish**: T023/T024 [P] after all above complete

---

## Parallel Execution Examples

### Phases 1–2 + US2 + US3 + US4 (concurrent start)

```
Immediately after kick-off:
  Sequential: T001 → T002          (Swagger infrastructure)
  Parallel:   T009–T018             (US2 KDoc — all different files)
  Parallel:   T019                  (US3 README)
  Parallel:   T020 + T021 → T022   (US4 ConcurrentCheckIT + editorial)
```

### Phase 3 (US1) — after T002 completes

```
Parallel group 1: T003 [CheckRoute.kt] + T007 [HealthRoute.kt]
Sequential group: T004 → T005 → T006 [all LimitsRoute.kt]
After all above: T008 (manual browser verification)
```

### Phase 7 (Polish) — after everything

```
Parallel: T023 (./gradlew test) + T024 (smoke tests)
```

---

## Implementation Strategy

### MVP First (US1 — Swagger UI)

1. Complete Phase 1 (T001) — add dependency
2. Complete Phase 2 (T002) — install plugin
3. Complete Phase 3 (T003–T008) — route documentation
4. **STOP and VALIDATE**: open `/swagger`, check quality, run existing test suite
5. Portfolio is already presentable at this point

### Incremental Delivery

1. Phases 1–2 → Swagger infrastructure ready
2. US1 (Phase 3) → `/swagger` works → portfolio demonstration ready (**MVP**)
3. US2 (Phase 4) → KDoc complete → algorithms self-documenting
4. US3 (Phase 5) → README complete → repository is self-contained
5. US4 (Phase 6) → SC-002 closed → full correctness verified
6. Phase 7 → everything green

---

## Notes

- `[P]` tasks touch different files — safe to run in parallel
- T004–T006 all modify `LimitsRoute.kt` — must run sequentially
- T021 and T022 both modify `specs/002-sprint2-service-api/tasks.md` — T021 must precede T022
- Verify ktor-swagger-ui 5.x DSL method names before T003 (one verification, applied to T003–T007)
- `if (overrideConfig == null)` guard in T002 is critical — prevents Swagger plugin from activating in `testApplication` context (see plan.md § Test context note)
- `ConcurrentCheckIT` uses `runTest { testApplication { ... } }` — coroutine concurrency inside the in-process Ktor test engine exercises the real Lua atomicity
