# Tasks: Sprint 2 — Service Layer & REST API

**Input**: Design documents from `specs/002-sprint2-service-api/`
**Prerequisites**: plan.md ✅ · spec.md ✅ · data-model.md ✅ · contracts/ ✅ · research.md ✅

**Tests**: Included per Constitution Principle IV (Test-First with Real Infrastructure).
Write each test task first. Confirm it fails (Red). Then implement until Green.

**Organization**: Tasks are grouped by user story to enable independent implementation,
testing, and delivery of each story.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no shared state)
- **[US1/US2/US3]**: User story this task belongs to

---

## Phase 1: Setup (Shared Test Infrastructure)

**Purpose**: Create the Testcontainers singleton used by all integration test classes.
Must exist before any integration test file can be written.

- [X] T001 Create `RedisTestContainer` Kotlin `object` — starts `redis:7-alpine` via `GenericContainer`, exposes `host` and `port` properties; started eagerly with `.also { it.start() }` in `src/test/kotlin/dev/zahaand/ratelimiter/integration/RedisTestContainer.kt`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Interface change and shared DTO infrastructure that ALL user stories depend on.
No user story work begins until this phase is complete.

**⚠️ CRITICAL**: T002–T005 must be completed sequentially in order. T006–T008 can follow once T002 is done.

- [X] T002 Change `ConfigRepository.delete` return type from `Unit` to `Boolean` (`true` = key deleted, `false` = key not found) in `src/main/kotlin/dev/zahaand/ratelimiter/domain/port/ConfigRepository.kt`
- [X] T003 [P] Update `InMemoryConfigRepository.delete` to `return policies.remove(key) != null` in `src/main/kotlin/dev/zahaand/ratelimiter/infrastructure/memory/InMemoryConfigRepository.kt`
- [X] T004 [P] Update `RedisConfigRepository.delete` to `return (commands.del("config:$key") ?: 0L) > 0L` in `src/main/kotlin/dev/zahaand/ratelimiter/infrastructure/redis/RedisConfigRepository.kt`
- [X] T005 Update `InMemoryConfigRepositoryTest` delete assertions to assert Boolean return: `true` when key existed and was removed, `false` when key was never stored, in `src/test/kotlin/dev/zahaand/ratelimiter/infrastructure/memory/InMemoryConfigRepositoryTest.kt`
- [X] T006 [P] Create `ErrorResponse(@Serializable data class, val error: String)` in `src/main/kotlin/dev/zahaand/ratelimiter/routes/dto/ErrorResponse.kt`
- [X] T007 [P] Create `InstantSerializer` (`object : KSerializer<Instant>`) — `serialize` calls `encoder.encodeString(value.toString())`, `deserialize` calls `Instant.parse(decoder.decodeString())` in `src/main/kotlin/dev/zahaand/ratelimiter/routes/dto/InstantSerializer.kt`
- [X] T008 Add `overrideConfig: AppConfig? = null` parameter to `Application.module()`, configure Netty engine with `shutdownGracePeriod = 5_000L` / `shutdownTimeout = 5_000L` (FR-013), add `install(ContentNegotiation) { json() }`, add `install(StatusPages)` with four handlers in most-specific-first order: `UnsupportedMediaTypeException` → 415 `{"error":"unsupported media type"}`, `BadRequestException` → 400 `{"error":"invalid request body"}`, `JsonConvertException` → 400 `{"error":"invalid request body"}`, `Throwable` → 500 `{"error":"internal server error"}` — covers FR-012, FR-014, FR-015 in `src/main/kotlin/dev/zahaand/ratelimiter/Application.kt`

**Checkpoint**: Foundation ready — interface compiles, shared DTOs exist, module accepts test config.

---

## Phase 3: User Story 1 — Rate Limit Check via HTTP (Priority: P1) 🎯 MVP

**Goal**: `POST /v1/check` returns a rate-limit decision (allowed/rejected) for any client key,
falling back to the default policy when none is configured.

**Independent Test**: Configure limit=3/10s for `"user-123"`. POST three times → `allowed:true`,
`remaining` 2→1→0. POST fourth → `allowed:false, remaining:0`. POST with blank key → HTTP 400.
POST with unconfigured key → HTTP 200 with default-policy `resetAt`.

> ⚠️ **TDD**: Write T009 first. Run `./gradlew test` — all four cases MUST fail before proceeding.

- [ ] T009 Write `CheckRouteIT` with 4 failing integration test cases: (1) allowed=true when under limit, (2) allowed=false when limit exceeded, (3) 400 for blank key, (4) default policy when no policy configured — each case uses `testApplication { application { module(testConfig) } }` with `testConfig` pointing at `RedisTestContainer` in `src/test/kotlin/dev/zahaand/ratelimiter/integration/CheckRouteIT.kt`
- [ ] T010 [P] Create `RateLimitKey` — `@JvmInline value class RateLimitKey(val value: String)` with `init { require(value.isNotBlank()) { "key must not be blank" } }` in `src/main/kotlin/dev/zahaand/ratelimiter/domain/model/RateLimitKey.kt`
- [ ] T011 [P] Create `CheckRequest` — `@Serializable data class CheckRequest(val key: String)` in `src/main/kotlin/dev/zahaand/ratelimiter/routes/dto/CheckRequest.kt`
- [ ] T012 [P] Create `CheckResponse` — `@Serializable data class CheckResponse(val allowed: Boolean, val remaining: Int, @Serializable(with = InstantSerializer::class) val resetAt: Instant)` in `src/main/kotlin/dev/zahaand/ratelimiter/routes/dto/CheckResponse.kt`
- [ ] T013 Implement `RateLimiterService(rateLimitRepository, configRepository, appConfig)` with `suspend fun check(key: RateLimitKey): RateLimitDecision` — calls `configRepository.get(key.value)` (falls back to `appConfig.rateLimit.toPolicy()` on null), calls `rateLimitRepository.check(key.value, policy, Instant.now())`, logs WARN with five structured fields (`key`, `strategy`, `limit`, `windowSeconds`, `timestamp`) on every rejected decision in `src/main/kotlin/dev/zahaand/ratelimiter/service/RateLimiterService.kt`
- [ ] T014 Implement `fun Route.checkRoute(service: RateLimiterService)` — `post("/check") { }`: receive `CheckRequest`, validate `key` is not blank (400 + `ErrorResponse("key must not be blank")`), validate `key.length <= 512` (400 + `ErrorResponse("key must not exceed 512 characters")`), call `service.check(RateLimitKey(request.key))`, respond 200 with `CheckResponse` in `src/main/kotlin/dev/zahaand/ratelimiter/routes/CheckRoute.kt`
- [ ] T015 Wire into `Application.module()`: create `RedisClient` from `appConfig.redis`, open connection, get `coroutines()` commands, instantiate `RedisRateLimitRepository` and `RedisConfigRepository`, instantiate `RateLimiterService`, add `routing { route("/v1") { checkRoute(rateLimiterService) } }` in `src/main/kotlin/dev/zahaand/ratelimiter/Application.kt`

**Checkpoint**: `POST /v1/check` works end-to-end. All 4 `CheckRouteIT` tests green. MVP deliverable.

---

## Phase 4: User Story 2 — Rate Limit Policy Management (Priority: P2)

**Goal**: `POST /v1/limits` creates/replaces a policy (201), `GET /v1/limits/{key}` returns it (200/404),
`DELETE /v1/limits/{key}` removes it (204/404).

**Independent Test**: POST policy for `"tenant-A"` → 201. GET → 200 with matching fields.
DELETE → 204. GET again → 404. POST with `limit=-1` → 400 with exact error message.

> ⚠️ **TDD**: Write T016 first. Run `./gradlew test` — all nine cases MUST fail before proceeding.

- [ ] T016 Write `LimitsRouteIT` with 9 failing integration test cases: (1) POST creates policy → 201, (2) POST blank key → 400, (3) POST limit=-1 → 400 `"limit must be >= 0"`, (4) POST windowSeconds=0 → 400 `"windowSeconds must be > 0"`, (5) POST unknown strategy → 400 `"strategy must be one of: FIXED_WINDOW, SLIDING_WINDOW, TOKEN_BUCKET"`, (6) GET existing → 200, (7) GET missing → 404, (8) DELETE existing → 204 + subsequent GET 404, (9) DELETE missing → 404 in `src/test/kotlin/dev/zahaand/ratelimiter/integration/LimitsRouteIT.kt`
- [ ] T017 [P] Create `LimitConfigRequest` — `@Serializable data class LimitConfigRequest(val key: String, val limit: Int, val windowSeconds: Int, val strategy: String)` in `src/main/kotlin/dev/zahaand/ratelimiter/routes/dto/LimitConfigRequest.kt`
- [ ] T018 [P] Create `LimitConfigResponse` — `@Serializable data class LimitConfigResponse(val key: String, val limit: Int, val windowSeconds: Int, val strategy: String)` in `src/main/kotlin/dev/zahaand/ratelimiter/routes/dto/LimitConfigResponse.kt`
- [ ] T019 Implement `fun Route.limitsRoute(configRepository: ConfigRepository)` with three operations: `post("/limits")` validates all five conditions (exact error messages per FR-007), calls `configRepository.save`, responds 201 with `LimitConfigResponse`; `get("/limits/{key}")` calls `configRepository.get`, responds 200 or 404; `delete("/limits/{key}")` calls `configRepository.delete`, responds 204 if `true` or 404 if `false` in `src/main/kotlin/dev/zahaand/ratelimiter/routes/LimitsRoute.kt`
- [ ] T020 Extend `routing { route("/v1") { … } }` in `Application.module()` to include `limitsRoute(configRepository)` alongside the existing `checkRoute` in `src/main/kotlin/dev/zahaand/ratelimiter/Application.kt`

**Checkpoint**: All CRUD operations on `/v1/limits` work. All 9 `LimitsRouteIT` tests green.

---

## Phase 5: User Story 3 — Service Health Visibility (Priority: P3)

**Goal**: `GET /health` returns `{"status":"UP","redis":"UP"}` (HTTP 200) when Redis is reachable,
and `{"status":"DOWN","redis":"DOWN"}` (HTTP 503) when Redis is unreachable or PING times out.

**Independent Test**: With Redis running → HTTP 200 UP/UP. With Redis container paused →
HTTP 503 DOWN/DOWN. Response within 2 seconds in both cases.

> ⚠️ **TDD**: Write T021 first. Run `./gradlew test` — both cases MUST fail before proceeding.

- [ ] T021 Write `HealthRouteIT` with 2 failing integration test cases: (1) `GET /health` returns 200 `{"status":"UP","redis":"UP"}` when Redis available, (2) `GET /health` returns 503 `{"status":"DOWN","redis":"DOWN"}` when Redis container paused via `RedisTestContainer.container.pause()` / `.unpause()` in `src/test/kotlin/dev/zahaand/ratelimiter/integration/HealthRouteIT.kt`
- [ ] T022 Create `HealthResponse` — `@Serializable data class HealthResponse(val status: String, val redis: String)` in `src/main/kotlin/dev/zahaand/ratelimiter/routes/dto/HealthResponse.kt`
- [ ] T023 Implement `fun Route.healthRoute(commands: RedisCoroutinesCommands<String, String>)` — `get("/health") { }`: call `withTimeout(1_000L) { commands.ping() }`, catch `TimeoutCancellationException` before `Exception` (both → `"DOWN"`), respond with `HealthResponse` and `HttpStatusCode.OK` or `HttpStatusCode.ServiceUnavailable` in `src/main/kotlin/dev/zahaand/ratelimiter/routes/HealthRoute.kt`
- [ ] T024 Add `healthRoute(commands)` to the bare (non-`/v1`) routing block and add `environment.monitor.subscribe(ApplicationStopped) { connection.close(); redisClient.shutdown() }` graceful shutdown hook; Netty engine shutdown grace period is already configured in T008 (`shutdownGracePeriod = 5_000L`, `shutdownTimeout = 5_000L`) and satisfies the FR-013 5-second in-flight wait in `src/main/kotlin/dev/zahaand/ratelimiter/Application.kt`

**Checkpoint**: All three user stories fully functional. All integration tests green. Service shuts down cleanly.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Verify correctness end-to-end and confirm the full system behaves as specified.

- [ ] T025 [P] Run full test suite `./gradlew test` — confirm all unit tests (domain, infrastructure/memory) and all integration tests (CheckRouteIT, LimitsRouteIT, HealthRouteIT) pass with zero failures
- [ ] T025b Write `ConcurrentCheckIT` — configure `limit=10`/`windowSeconds=60` for key `"concurrent-test"`, launch 50 coroutines simultaneously via `(1..50).map { async { client.post("/v1/check") { … } } }.awaitAll()`, assert exactly 10 responses have `allowed=true`, exactly 40 have `allowed=false`, all 50 have HTTP 200; use `@BeforeEach` FLUSHDB for isolation — implements SC-002 in `src/test/kotlin/dev/zahaand/ratelimiter/integration/ConcurrentCheckIT.kt`
- [ ] T026 [P] Run smoke tests from `specs/002-sprint2-service-api/quickstart.md` against a local Redis instance on `localhost:6379` — exercise all five endpoints manually and confirm responses match contract in `specs/002-sprint2-service-api/contracts/http-api.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 — **BLOCKS all user stories**
- **Phase 3 (US1)**: Depends on Phase 2 — first deliverable (MVP)
- **Phase 4 (US2)**: Depends on Phase 2; integrates `configRepository` already wired in Phase 3
- **Phase 5 (US3)**: Depends on Phase 2; uses `commands` already created in Phase 3
- **Phase 6 (Polish)**: Depends on all prior phases complete

### User Story Dependencies

- **US1 (P1)**: Can start after Phase 2 — no dependency on US2 or US3
- **US2 (P2)**: Can start after Phase 2 — `configRepository` is wired in T015; T020 extends it
- **US3 (P3)**: Can start after Phase 2 — `commands` are wired in T015; T024 extends it

### Within Each User Story

1. Write integration tests first (TDD Red phase)
2. Create DTOs marked `[P]` in parallel
3. Implement service / route (depends on DTOs)
4. Wire into `Application.module()` (always last in the story)
5. Run tests — confirm Green before advancing to next story

---

## Parallel Execution Examples

### Phase 2 (Foundational)

```
Sequential: T002 (interface change)
Then parallel: T003 [P] + T004 [P]  (update implementations)
Then: T005 (update tests — depends on T003)
Parallel any time in Phase 2: T006 [P] + T007 [P]
Then: T008 (Application.module() base)
```

### Phase 3 (US1)

```
T009 first (failing tests)
Then parallel: T010 [P] + T011 [P] + T012 [P]  (value class + DTOs)
Then sequential: T013 → T014 → T015
```

### Phase 4 (US2)

```
T016 first (failing tests)
Then parallel: T017 [P] + T018 [P]
Then sequential: T019 → T020
```

### Phase 5 (US3)

```
T021 first (failing tests)
Then: T022 (HealthResponse DTO)
Then: T023 → T024
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 (Setup)
2. Complete Phase 2 (Foundational — CRITICAL)
3. Complete Phase 3 (US1: `POST /v1/check`)
4. **STOP and VALIDATE**: `./gradlew test`, smoke test check endpoint
5. Service is already useful at this point

### Incremental Delivery

1. Setup + Foundational → shared infrastructure ready
2. US1 → `POST /v1/check` works → **MVP**
3. US2 → CRUD `/v1/limits` works → operators can configure per-key policies
4. US3 → `GET /health` works → service is production-observable
5. Each phase is independently deployable

---

## Notes

- `[P]` tasks touch different files — safe to run in parallel with no merge conflicts
- TDD is mandatory per Constitution Principle IV: write the test, see it fail, implement, see it pass
- All Application.kt changes (T008, T015, T020, T024) are sequential — each extends the previous
- Integration tests use `RedisTestContainer` (T001) — Phase 1 must be complete before writing any IT
- `@BeforeEach` in each IT class connects to `RedisTestContainer`, calls `FLUSHDB`, then disconnects
- `testConfig` = `AppConfig(server = ServerConfig(0), redis = RedisConfig(host, port), rateLimit = RateLimitDefaults())`
