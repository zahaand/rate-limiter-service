# Tasks: Sprint 1 — Domain Model & Rate Limiting Infrastructure

**Input**: Design documents from `specs/001-sprint1-domain-redis/`
**Prerequisites**: plan.md, spec.md, data-model.md, contracts/
**Sprint Goal**: Domain model and Redis infrastructure — algorithms work, Redis connected, unit tests green.

**TDD Mandate** (Constitution §IV): Tests marked 🔴 MUST be committed and verified **failing** before the corresponding implementation task begins. Red → Green → Refactor — no exceptions.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no shared dependencies)
- **[Story]**: Maps to user story in spec.md (US1/US2/US3)
- **🔴**: Write this test first; confirm it fails before moving to the next task

---

## Phase 1: Setup

**Purpose**: Resolve the AssertJ build gap identified in research.md §5 before any implementation begins.

- [ ] T001 Add `testImplementation("org.assertj:assertj-core:3.27.3")` to `build.gradle.kts` (constitution mandates AssertJ for unit tests; currently absent — see research.md §5)

---

## Phase 2: Foundational — Domain Model & Repository Interfaces (F-01, F-05, F-06)

**Purpose**: Core types and interfaces that ALL three user stories and all algorithm implementations depend on.

**⚠️ CRITICAL**: No algorithm code or test can be written until this phase is complete.

- [ ] T002 [P] Create `src/main/kotlin/dev/zahaand/ratelimiter/domain/RateLimitStrategy.kt` — `sealed class` with `abstract val configName: String`; three `data object` subclasses: `FixedWindow` (`configName = "FIXED_WINDOW"`), `SlidingWindow` (`configName = "SLIDING_WINDOW"`), `TokenBucket` (`configName = "TOKEN_BUCKET"`); `companion object` with `fromConfigName(name: String): RateLimitStrategy` using a `byName` map — `error("Unknown strategy: $name")` on miss (see data-model.md §RateLimitStrategy)

- [ ] T003 [P] Create `src/main/kotlin/dev/zahaand/ratelimiter/domain/RateLimitPolicy.kt` — `data class` with `val limit: Int`, `val windowSeconds: Int`, `val strategy: RateLimitStrategy`; `init` block: `require(limit >= 0) { "limit must be non-negative, was $limit" }` and `require(windowSeconds > 0) { "windowSeconds must be positive, was $windowSeconds" }` (see data-model.md §RateLimitPolicy)

- [ ] T004 [P] Create `src/main/kotlin/dev/zahaand/ratelimiter/domain/RateLimitDecision.kt` — `data class` with `val allowed: Boolean`, `val remaining: Int`, `val resetAt: java.time.Instant`; no `init` validation (produced only by algorithms, never constructed by callers) (see data-model.md §RateLimitDecision)

- [ ] T005 Write `src/test/kotlin/dev/zahaand/ratelimiter/domain/RateLimitPolicyTest.kt` — `@Nested inner class` groups: (1) `Init guards`: `assertFailsWith<IllegalArgumentException>` for `limit = -1`, `windowSeconds = 0`, `windowSeconds = -1`; (2) `Valid construction`: no exception for `limit = 0`, `limit = 10`, `windowSeconds = 1` (see plan.md §F-01 Test)

- [ ] T006 [P] Create `src/main/kotlin/dev/zahaand/ratelimiter/repository/RateLimitRepository.kt` — `interface` with single method `suspend fun check(key: String, policy: RateLimitPolicy, now: Instant): RateLimitDecision` (see contracts/RateLimitRepository.md)

- [ ] T007 [P] Create `src/main/kotlin/dev/zahaand/ratelimiter/repository/ConfigRepository.kt` — `interface` with three methods: `suspend fun save(key: String, policy: RateLimitPolicy)`, `suspend fun get(key: String): RateLimitPolicy?`, `suspend fun delete(key: String)` (see contracts/ConfigRepository.md)

**Checkpoint**: All domain types compile; interfaces defined; `RateLimitPolicyTest` passes. No algorithm code exists yet.

---

## Phase 3: User Story 1 — Rate Limit Decision (Priority: P1) 🎯 MVP

**Goal**: A rate limit check for a given key and policy returns an allow/reject decision with remaining count and resetAt.

**Independent Test**: `FixedWindowAlgorithmTest` green. Concurrency: 100 coroutines on limit=50 → exactly 50 `allowed=true`.

- [ ] T008 [US1] 🔴 Write `src/test/kotlin/dev/zahaand/ratelimiter/domain/algorithm/FixedWindowAlgorithmTest.kt` — use `Clock.fixed(...)` / `Instant` literals throughout; test cases: (1) under limit → `allowed=true`, `remaining` decrements; (2) 10th request with limit=10 → `allowed=true`, `remaining=0`; (3) 11th request → `allowed=false`, `remaining=0`; (4) after window boundary → counter resets, request allowed; (5) `limit=0` → always `allowed=false`; (6) epoch alignment: `now = Instant.ofEpochSecond(130)`, `windowSeconds=60` → `windowStart = Instant.ofEpochSecond(120)`, `resetAt = Instant.ofEpochSecond(180)` (see plan.md §F-02, spec.md §FR-004)

- [ ] T009 [US1] Implement `src/main/kotlin/dev/zahaand/ratelimiter/domain/algorithm/FixedWindowAlgorithm.kt` — `object` with `data class State(val count: Int, val windowStart: Instant)` and `fun check(policy: RateLimitPolicy, state: State?, now: Instant): Pair<RateLimitDecision, State>`; logic: `windowStart = Instant.ofEpochSecond(now.epochSecond / windowSeconds * windowSeconds)`; reset if state null or windowStart changed; `allowed = count < limit`; `remaining = maxOf(0, limit - newCount)` (see plan.md §F-02 pseudocode)

- [ ] T010 [US1] Implement `src/main/kotlin/dev/zahaand/ratelimiter/repository/inmemory/InMemoryRateLimitRepository.kt` — `class InMemoryRateLimitRepository(private val clock: Clock = Clock.systemUTC()) : RateLimitRepository`; fields: `mutexes: ConcurrentHashMap<String, Mutex>`, `fixedWindowStates: ConcurrentHashMap<String, FixedWindowAlgorithm.State>`, `slidingWindowStates: ConcurrentHashMap<String, List<Instant>>`, `tokenBucketStates: ConcurrentHashMap<String, TokenBucketAlgorithm.State>`; `check()` uses `mutexes.computeIfAbsent(key) { Mutex() }.withLock { when(policy.strategy) { ... } }`; Sliding and TokenBucket branches added but `TODO()` until Phase 4 (see plan.md §F-05)

- [ ] T011 [US1] Write `src/test/kotlin/dev/zahaand/ratelimiter/repository/inmemory/InMemoryRateLimitRepositoryTest.kt` — concurrency test: fixed clock; `InMemoryRateLimitRepository` with FixedWindow limit=50; `runTest { List(100) { async { repo.check("key", policy, now) } }.awaitAll() }`; assert `results.count { it.allowed } == 50` and `results.count { !it.allowed } == 50` (see plan.md §F-05 §InMemoryRateLimitRepositoryTest)

**Checkpoint**: `FixedWindowAlgorithmTest` + `InMemoryRateLimitRepositoryTest` pass. US1 independently verified.

---

## Phase 4: User Story 2 — Rate-Limiting Strategy Selection (Priority: P2)

**Goal**: Sliding Window and Token Bucket algorithms are implemented, tested, and wired into InMemoryRateLimitRepository. All three strategies dispatch correctly.

**Independent Test**: `SlidingWindowAlgorithmTest` and `TokenBucketAlgorithmTest` each green in isolation. InMemoryRateLimitRepository routes to correct algorithm via `policy.strategy`.

- [ ] T012 [P] [US2] 🔴 Write `src/test/kotlin/dev/zahaand/ratelimiter/domain/algorithm/SlidingWindowAlgorithmTest.kt` — use fixed `Instant` values; test cases: (1) under limit → `allowed=true`; (2) at limit → `allowed=false`, `remaining=0`; (3) after window rolls (entries older than `now - windowSeconds` pruned) → allowed again; (4) equal timestamps count as distinct entries; (5) `limit=0` → always rejected; (6) **CHK005** inclusive boundary: entry at exactly `now - windowSeconds` must remain in window (not pruned); (7) **CHK006** `resetAt = now` when `remaining > 0`; `resetAt = oldest + windowSeconds` when window full (see plan.md §F-03, spec.md §FR-005)

- [ ] T013 [US2] Implement `src/main/kotlin/dev/zahaand/ratelimiter/domain/algorithm/SlidingWindowAlgorithm.kt` — `object` with `fun check(policy: RateLimitPolicy, timestamps: List<Instant>, now: Instant): Pair<RateLimitDecision, List<Instant>>`; `cutoff = now.minusSeconds(windowSeconds.toLong())`; prune with `timestamps.filter { it >= cutoff }` (inclusive); `allowed = valid.size < limit`; `newTimestamps = if (allowed) valid + now else valid`; `remaining = maxOf(0, limit - newTimestamps.size)`; `resetAt = if (remaining == 0 && newTimestamps.isNotEmpty()) newTimestamps.first().plusSeconds(windowSeconds.toLong()) else now` (see plan.md §F-03 pseudocode)

- [ ] T014 [P] [US2] 🔴 Write `src/test/kotlin/dev/zahaand/ratelimiter/domain/algorithm/TokenBucketAlgorithmTest.kt` — use `Clock.fixed` and `Instant` literals; test cases: (1) full bucket → first `limit` requests each `allowed=true`; (2) empty bucket → `allowed=false`, `remaining=0`; (3) **CHK011** US2 Scenario 3: capacity=10, windowSeconds=60, elapsed=30s → `allowed=true`, `remaining=4` (5 refilled, 1 consumed); (4) fractional accumulation: two checks with short elapsed verify Double precision; (5) `limit=0` → `allowed=false`, no division-by-zero exception; (6) `state=null` → bucket starts full, first request allowed (see plan.md §F-04, spec.md §US2 Scenario 3)

- [ ] T015 [US2] Implement `src/main/kotlin/dev/zahaand/ratelimiter/domain/algorithm/TokenBucketAlgorithm.kt` — `object` with `data class State(val tokens: Double, val lastRefillAt: Instant)` and `fun check(policy, state, now): Pair<RateLimitDecision, State>`; `capacity = limit.toDouble()`; `refillRate = capacity / windowSeconds`; null state → `State(capacity, now)`; `elapsed = Duration.between(state.lastRefillAt, now).toNanos() / 1_000_000_000.0`; `refilled = minOf(tokens + elapsed * refillRate, capacity)`; if allowed: `remaining = (refilled - 1.0).toInt()`; zero-guard: `if (refillRate == 0.0) resetAt = now.plusSeconds(windowSeconds.toLong())` (see plan.md §F-04 pseudocode)

- [ ] T016 [US2] Extend `src/main/kotlin/dev/zahaand/ratelimiter/repository/inmemory/InMemoryRateLimitRepository.kt` — replace the `TODO()` stubs in the `SlidingWindow` and `TokenBucket` branches with full algorithm dispatch + state read/write; pattern mirrors the FixedWindow branch already in place (see plan.md §F-05 §InMemoryRateLimitRepository)

**Checkpoint**: `SlidingWindowAlgorithmTest` + `TokenBucketAlgorithmTest` pass. InMemoryRateLimitRepository dispatches all three strategies.

---

## Phase 5: User Story 3 — Config Management & Redis Infrastructure (Priority: P3)

**Goal**: Policies are persisted per key with a service-wide configurable default. Redis implementations are coded and compile (integration tests are Sprint 2).

**Independent Test**: `InMemoryConfigRepositoryTest` green. AppConfig compiles with `defaultStrategy`. Redis classes compile cleanly.

- [ ] T017 [US3] Implement `src/main/kotlin/dev/zahaand/ratelimiter/repository/inmemory/InMemoryConfigRepository.kt` — `class InMemoryConfigRepository : ConfigRepository`; `private val policies = ConcurrentHashMap<String, RateLimitPolicy>()`; `save` = `policies[key] = policy`; `get` = `policies[key]`; `delete` = `policies.remove(key)`; no `Mutex` needed (see plan.md §F-06 §InMemoryConfigRepository)

- [ ] T018 [US3] Write `src/test/kotlin/dev/zahaand/ratelimiter/repository/inmemory/InMemoryConfigRepositoryTest.kt` — test cases: (1) save then get → equal policy (all three fields); (2) delete then get → `null`; (3) delete absent key → no exception; (4) overwrite existing → get returns latest; (5) get absent key → `null` (see contracts/ConfigRepository.md §Behaviour Contracts)

- [ ] T019 [P] [US3] Update `src/main/kotlin/dev/zahaand/ratelimiter/infrastructure/config/AppConfig.kt` — add `val defaultStrategy: String = "FIXED_WINDOW"` to `RateLimitDefaults`; add `fun toPolicy(): RateLimitPolicy = RateLimitPolicy(defaultLimit, defaultWindowSeconds, RateLimitStrategy.fromConfigName(defaultStrategy))` (see plan.md §Config Updates, research.md §4)

- [ ] T020 [P] [US3] Update `src/main/resources/application.yaml` — add `defaultStrategy: FIXED_WINDOW` as third field under the `rateLimit:` block (see plan.md §Config Updates)

- [ ] T021 [US3] Create `src/main/kotlin/dev/zahaand/ratelimiter/repository/redis/RedisRateLimitRepository.kt` — `class RedisRateLimitRepository(private val commands: RedisCoroutinesCommands<String, String>, private val clock: Clock = Clock.systemUTC()) : RateLimitRepository`; `companion object` holding three Lua script string constants (`FIXED_WINDOW_SCRIPT`, `SLIDING_WINDOW_SCRIPT`, `TOKEN_BUCKET_SCRIPT`); key patterns: `rl:fw:{key}:{windowStart}`, `rl:sw:{key}`, `rl:tb:{key}`; `check()` dispatches via exhaustive `when(policy.strategy)` calling `commands.eval(script, ScriptOutputType.MULTI, keys, args)`; no unit tests — integration tests Sprint 2 (see plan.md §F-05 §RedisRateLimitRepository, research.md §2 §3)

- [ ] T022 [US3] Create `src/main/kotlin/dev/zahaand/ratelimiter/repository/redis/RedisConfigRepository.kt` — `class RedisConfigRepository(private val commands: RedisCoroutinesCommands<String, String>) : ConfigRepository`; key pattern `config:{clientKey}`; `save`: `commands.hset("config:$key", mapOf("limit" to ..., "windowSeconds" to ..., "strategy" to policy.strategy.configName))`; `get`: `commands.hgetall("config:$key")` → null if empty → parse to `RateLimitPolicy` using `RateLimitStrategy.fromConfigName(map["strategy"]!!)`; `delete`: `commands.del("config:$key")` (see plan.md §F-06 §RedisConfigRepository, research.md §3)

**Checkpoint**: `InMemoryConfigRepositoryTest` passes. AppConfig + application.yaml consistent. Redis files compile.

---

## Phase 6: Polish

**Purpose**: Full suite gate before Sprint 1 is declared done.

- [ ] T023 Run `./gradlew test` and confirm zero failures across all six test classes: `RateLimitPolicyTest`, `FixedWindowAlgorithmTest`, `SlidingWindowAlgorithmTest`, `TokenBucketAlgorithmTest`, `InMemoryRateLimitRepositoryTest`, `InMemoryConfigRepositoryTest` (see spec.md §SC-003)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1** → no dependencies
- **Phase 2** → depends on Phase 1 (T001 must be done for tests to compile)
- **Phase 3** → depends on Phase 2; T009 needs `FixedWindowAlgorithm.State` type; T010 needs T009
- **Phase 4** → depends on Phase 2; T016 depends on T013 + T015
- **Phase 5** → depends on Phase 2; T021/T022 depend on domain types from Phase 2; T018 depends on T017
- **Phase 6** → depends on all phases complete

### Task-Level Dependencies

| Task | Depends on |
|------|-----------|
| T008 | T002, T003, T004, T006 |
| T009 | T008 (must be red first) |
| T010 | T009 |
| T011 | T010 |
| T012 | T002, T003, T004 |
| T013 | T012 (must be red first) |
| T014 | T002, T003, T004 |
| T015 | T014 (must be red first) |
| T016 | T013 + T015 |
| T017 | T006, T007 |
| T018 | T017 |
| T019 | T002, T003 |
| T021 | T002, T003, T004, T006 |
| T022 | T002, T003, T007 |

### Parallel Opportunities

```
# Phase 2 — all independent, launch together:
T002 RateLimitStrategy  ∥  T003 RateLimitPolicy  ∥  T004 RateLimitDecision
T006 RateLimitRepository interface  ∥  T007 ConfigRepository interface

# Phase 4 — Sliding Window and Token Bucket tracks are fully independent:
T012 SlidingWindowAlgorithmTest  ∥  T014 TokenBucketAlgorithmTest
T013 SlidingWindowAlgorithm      ∥  T015 TokenBucketAlgorithm

# Phase 5 — two parallel pairs:
T019 AppConfig.kt  ∥  T020 application.yaml
T021 RedisRateLimitRepository  ∥  T022 RedisConfigRepository
```

---

## Implementation Strategy

### MVP First (US1 Only)

1. T001 — Add AssertJ
2. T002–T007 — Domain types + interfaces
3. T008–T011 — Fixed Window + InMemory repo + tests
4. **STOP & VALIDATE**: `./gradlew test` — US1 acceptance scenarios proven

### Incremental Delivery

1. MVP above → `FixedWindowAlgorithmTest` + `InMemoryRateLimitRepositoryTest` green
2. T012–T016 → Sliding Window + Token Bucket → algorithm tests green
3. T017–T022 → Config management + Redis code → `InMemoryConfigRepositoryTest` green; Redis compiles
4. T023 → Full suite green → Sprint 1 done

---

## Notes

- `[P]` = different files, no shared incomplete dependencies — safe to parallelize
- Constitution: `val` over `var`; no `!!`; exhaustive `when` on `RateLimitStrategy` (no `else`)
- All test classes: `Clock.fixed(instant, ZoneOffset.UTC)` injected — never `Instant.now()`
- All `suspend` tests wrapped in `runTest { }`
- Test function names use backticks: `` `should allow request when under limit` ``
- CHK005, CHK006, CHK011 already resolved in spec.md — implement per §FR-005 and §FR-006
