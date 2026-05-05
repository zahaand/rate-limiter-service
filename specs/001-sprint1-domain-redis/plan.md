# Implementation Plan: Sprint 1 — Domain Model & Rate Limiting Infrastructure

**Branch**: `feature/001-sprint1-domain-redis` | **Date**: 2026-05-05 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/001-sprint1-domain-redis/spec.md`

## Summary

Build the closed, immutable domain model (`RateLimitStrategy`, `RateLimitPolicy`, `RateLimitDecision`), three pure-function algorithm objects (Fixed Window, Sliding Window, Token Bucket), and the full repository layer (`RateLimitRepository` + `ConfigRepository` — interface, InMemory, Redis implementations). All algorithms are fully covered by unit tests against the InMemory repository. Redis implementations are coded but not integration-tested until Sprint 2.

## Technical Context

**Language/Version**: Kotlin 2.1.20, JVM target JDK 21
**Primary Dependencies**: Ktor 3.1.3, Lettuce 6.5.5 (coroutine adapter), Hoplite 2.9.0 (YAML config), kotlinx-coroutines 1.10.2
**Storage**: Redis (Sprint 1 InMemory; Sprint 2 Testcontainers integration)
**Testing**: JUnit 5 (via `kotlin-test`), MockK 1.14.2, AssertJ 3.27.3, kotlinx-coroutines-test 1.10.2
**Target Platform**: JVM / Linux server
**Project Type**: Web service (single-module Ktor)
**Performance Goals**: N/A Sprint 1 (domain + unit tests only)
**Constraints**: No `!!`; no blocking I/O; no Spring; no shared mutable state without `Mutex`
**Scale/Scope**: Single-module; three-layer architecture (routes → service → repository)

**Build gap to fix**: Add `testImplementation("org.assertj:assertj-core:3.27.3")` to `build.gradle.kts`.

## Constitution Check

| Principle | Status | Evidence |
|---|---|---|
| I. Coroutine-First | ✅ PASS | All repository methods `suspend`; `Mutex.withLock {}` suspends |
| II. Immutability & Safety | ✅ PASS | All types `data class`/`data object`; `val` only; `require()` guards |
| III. Sealed Class Strategies | ✅ PASS | `RateLimitStrategy` sealed; all `when` expressions exhaustive |
| IV. Test-First / Real Infra | ✅ PASS | Unit tests: InMemory; Redis IT deferred to Sprint 2 per spec |
| V. Pure Ktor Stack | ✅ PASS | No Spring; manual DI in `Application.kt` |
| VI. Runtime-Configurable | ⚠️ PARTIAL | `ConfigRepository` built; REST API is Sprint 2 (spec-approved deferral) |

## Project Structure

### Documentation (this feature)

```text
specs/001-sprint1-domain-redis/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── contracts/
│   ├── RateLimitRepository.md
│   └── ConfigRepository.md
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code

```text
src/main/kotlin/dev/zahaand/ratelimiter/
├── Application.kt                                  # existing — add default policy wiring
├── domain/
│   ├── RateLimitStrategy.kt                        # NEW — sealed class + fromConfigName
│   ├── RateLimitPolicy.kt                          # NEW — data class, init guards
│   ├── RateLimitDecision.kt                        # NEW — data class
│   └── algorithm/
│       ├── FixedWindowAlgorithm.kt                 # NEW — pure function object + State
│       ├── SlidingWindowAlgorithm.kt               # NEW — pure function object
│       └── TokenBucketAlgorithm.kt                 # NEW — pure function object + State
├── repository/
│   ├── RateLimitRepository.kt                      # NEW — interface
│   ├── ConfigRepository.kt                         # NEW — interface
│   ├── inmemory/
│   │   ├── InMemoryRateLimitRepository.kt          # NEW
│   │   └── InMemoryConfigRepository.kt             # NEW
│   └── redis/
│       ├── RedisRateLimitRepository.kt             # NEW
│       └── RedisConfigRepository.kt                # NEW
└── infrastructure/
    └── config/
        └── AppConfig.kt                            # MODIFY — add defaultStrategy: String

src/main/resources/
└── application.yaml                                # MODIFY — add rateLimit.defaultStrategy

src/test/kotlin/dev/zahaand/ratelimiter/
├── domain/
│   ├── RateLimitPolicyTest.kt                      # NEW — init guard tests
│   └── algorithm/
│       ├── FixedWindowAlgorithmTest.kt             # NEW
│       ├── SlidingWindowAlgorithmTest.kt           # NEW
│       └── TokenBucketAlgorithmTest.kt             # NEW
└── repository/
    └── inmemory/
        ├── InMemoryRateLimitRepositoryTest.kt      # NEW — includes concurrency test
        └── InMemoryConfigRepositoryTest.kt         # NEW
```

**Structure Decision**: Single-module, three-layer Ktor service. Domain algorithms are pure objects under `domain/algorithm/`. Repository implementations live in `repository/{inmemory,redis}/`. No service layer in Sprint 1 (Sprint 2 adds `RateLimiterService` as orchestration).

## Implementation Guide

### F-01: Domain Model

**Files**: `domain/RateLimitStrategy.kt`, `domain/RateLimitPolicy.kt`, `domain/RateLimitDecision.kt`

`RateLimitStrategy` is a `sealed class` with three `data object` subclasses. The `configName` property on each object is the canonical string for YAML config and Redis serialisation. A `companion object` holds `fromConfigName(name: String)` for startup-time parsing.

`RateLimitPolicy` validates in `init`: `require(limit >= 0)` and `require(windowSeconds > 0)`. Strategy is a required constructor field.

`RateLimitDecision` is a plain `data class` — no validation needed (produced by algorithms, never user-constructed).

**Test**: `RateLimitPolicyTest` — verify `init` throws for negative limit, zero/negative windowSeconds, and accepts `limit = 0`.

---

### F-02: Fixed Window Algorithm

**File**: `domain/algorithm/FixedWindowAlgorithm.kt`

```
windowStart  = Instant.ofEpochSecond(now.epochSecond / windowSeconds * windowSeconds)
resetAt      = windowStart + windowSeconds
currentState = if (state == null || state.windowStart != windowStart) State(0, windowStart) else state
allowed      = currentState.count < policy.limit
newCount     = if allowed: currentState.count + 1 else currentState.count
remaining    = max(0, policy.limit - newCount)
```

**Test class**: `FixedWindowAlgorithmTest`
- Under limit → allowed, remaining decrements
- Exactly at limit → rejected, remaining=0
- After window boundary crossed → counter resets, allowed again
- `limit=0` → always rejected
- Boundary alignment: epochSecond=130 with windowSeconds=60 → windowStart=120

---

### F-03: Sliding Window Algorithm

**File**: `domain/algorithm/SlidingWindowAlgorithm.kt`

```
cutoff        = now - windowSeconds
valid         = timestamps.filter { it >= cutoff }            // inclusive boundary
allowed       = valid.size < policy.limit
newTimestamps = if allowed: valid + now else valid
remaining     = max(0, policy.limit - newTimestamps.size)
resetAt       = if (remaining == 0 && newTimestamps.isNotEmpty())
                    newTimestamps.first().plusSeconds(windowSeconds)
                else now
```

**Test class**: `SlidingWindowAlgorithmTest`
- Under limit → allowed
- At limit → rejected
- After window rolls (old entries pruned) → allowed again
- Equal timestamps count as distinct
- `limit=0` → always rejected

---

### F-04: Token Bucket Algorithm

**File**: `domain/algorithm/TokenBucketAlgorithm.kt`

```
capacity     = limit.toDouble()
refillRate   = capacity / windowSeconds   // tokens per second
currentState = state ?: State(capacity, now)   // starts full
elapsed      = nanosBetween(currentState.lastRefillAt, now) / 1e9
refilled     = min(currentState.tokens + elapsed * refillRate, capacity)
allowed      = refilled >= 1.0
```

If allowed:
```
newTokens  = refilled - 1.0
remaining  = newTokens.toInt()
resetAt    = now + (capacity - newTokens) / refillRate  [guard: refillRate > 0]
newState   = State(newTokens, now)
```

If rejected:
```
remaining  = 0
resetAt    = now + (1.0 - refilled) / refillRate  [guard: refillRate > 0]
newState   = State(refilled, now)   // update lastRefillAt so next call refills from now
```

Zero-guard: `if (refillRate == 0.0) resetAt = now + windowSeconds`

**Test class**: `TokenBucketAlgorithmTest`
- Full bucket → first N requests allowed
- Empty bucket → rejected immediately
- After 30 s with 10/60 s rate → allowed, `remaining = 4` (5 refilled, 1 consumed — US2 Scenario 3)
- `Double` precision: test that fractional refill accumulates correctly over multiple calls
- `limit=0` → always rejected, no division-by-zero

---

### F-05: Repository Layer

**Files**: `repository/RateLimitRepository.kt`, `repository/inmemory/InMemoryRateLimitRepository.kt`, `repository/redis/RedisRateLimitRepository.kt`

#### InMemoryRateLimitRepository

Constructor: `clock: Clock = Clock.systemUTC()`

State maps (all `ConcurrentHashMap`):
- `fixedWindowStates: ConcurrentHashMap<String, FixedWindowAlgorithm.State>`
- `slidingWindowStates: ConcurrentHashMap<String, List<Instant>>`
- `tokenBucketStates: ConcurrentHashMap<String, TokenBucketAlgorithm.State>`
- `mutexes: ConcurrentHashMap<String, Mutex>`

`check(key, policy, now)`:
```kotlin
val mutex = mutexes.computeIfAbsent(key) { Mutex() }
mutex.withLock {
    when (policy.strategy) {
        is RateLimitStrategy.FixedWindow -> { ... }
        is RateLimitStrategy.SlidingWindow -> { ... }
        is RateLimitStrategy.TokenBucket -> { ... }
    }
}
```

**Test class**: `InMemoryRateLimitRepositoryTest`
- Delegates correctly to each algorithm
- Concurrency test: 100 coroutines fire simultaneously for a key with limit=50 → exactly 50 allowed
  - Use `runTest` + `launch` × 100 + `CountDownLatch` or `Semaphore` for synchronisation

#### RedisRateLimitRepository

Constructor: `commands: RedisCoroutinesCommands<String, String>`, `clock: Clock = Clock.systemUTC()`

Three Lua scripts (one per strategy), defined as `companion object` constants:

```
FIXED_WINDOW_SCRIPT  — INCR key + conditional EXPIREAT
SLIDING_WINDOW_SCRIPT — ZADD + ZREMRANGEBYSCORE + ZCARD + conditional ZREM
TOKEN_BUCKET_SCRIPT   — HGETALL + float arithmetic + HSET
```

Key patterns:
- Fixed Window: `rl:fw:{key}:{windowStart}`  (windowStart = epoch-aligned boundary)
- Sliding Window: `rl:sw:{key}`
- Token Bucket: `rl:tb:{key}`

No unit tests for Redis implementation in Sprint 1 (integration tests via Testcontainers in Sprint 2).

---

### F-06: Config Storage

**Files**: `repository/ConfigRepository.kt`, `repository/inmemory/InMemoryConfigRepository.kt`, `repository/redis/RedisConfigRepository.kt`

#### InMemoryConfigRepository

```kotlin
class InMemoryConfigRepository : ConfigRepository {
    private val policies = ConcurrentHashMap<String, RateLimitPolicy>()
    override suspend fun save(key: String, policy: RateLimitPolicy) { policies[key] = policy }
    override suspend fun get(key: String): RateLimitPolicy? = policies[key]
    override suspend fun delete(key: String) { policies.remove(key) }
}
```

No `Mutex` needed. `ConcurrentHashMap` individual operations are atomic.

#### RedisConfigRepository

Constructor: `commands: RedisCoroutinesCommands<String, String>`
Key pattern: `config:{clientKey}` (Redis Hash)

```
save:   HSET config:{key} limit {n} windowSeconds {n} strategy {configName}
get:    HGETALL config:{key} → parse fields → RateLimitPolicy; null if absent
delete: DEL config:{key}
```

**Test class**: `InMemoryConfigRepositoryTest`
- save then get → equal policy
- delete then get → null
- delete absent key → no error
- Overwrite → get returns latest

---

### Config Updates

**`AppConfig.kt`**: Add `val defaultStrategy: String = "FIXED_WINDOW"` to `RateLimitDefaults`.

**`application.yaml`**: Add `defaultStrategy: FIXED_WINDOW` under `rateLimit`.

**`build.gradle.kts`**: Add `testImplementation("org.assertj:assertj-core:3.27.3")`.

**`Application.kt`**: Parse `defaultPolicy` from config at startup:
```kotlin
val defaultPolicy = RateLimitPolicy(
    limit = config.rateLimit.defaultLimit,
    windowSeconds = config.rateLimit.defaultWindowSeconds,
    strategy = RateLimitStrategy.fromConfigName(config.rateLimit.defaultStrategy)
)
```

---

## Test Strategy

### Unit Test Conventions (from backend-kotlin-unit-test playbook)

- `runTest { }` wraps all `suspend` calls
- `Clock.fixed(instant, ZoneOffset.UTC)` injected — no `Instant.now()` in tests
- Backtick function names: `` `should allow request when under limit` ``
- `@Nested inner class` per logical group
- `assertThat(...)` from AssertJ
- `assertFailsWith<E>` from `kotlin.test` for exception assertions
- No `else` in `when` on sealed types

### Concurrency Test Pattern (InMemoryRateLimitRepository)

```kotlin
@Test
fun `should not exceed limit under concurrent load`() = runTest {
    val repo = InMemoryRateLimitRepository(Clock.fixed(Instant.now(), ZoneOffset.UTC))
    val policy = RateLimitPolicy(limit = 50, windowSeconds = 60, strategy = RateLimitStrategy.FixedWindow)
    val results = (1..100).map {
        async { repo.check("key", policy, Instant.now()) }
    }.awaitAll()
    
    assertThat(results.count { it.allowed }).isEqualTo(50)
    assertThat(results.count { !it.allowed }).isEqualTo(50)
}
```

---

## Complexity Tracking

| Item | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| `limit >= 0` (not `> 0`) | Edge Cases spec explicitly handles `limit = 0` as "reject all" — a valid operational config | Enforcing `> 0` would prevent operators from using limit=0 to block a key; spec contradiction resolved toward operational utility |
| Algorithm logic inside `RateLimitRepository.check()` | No service layer in Sprint 1; service layer is Sprint 2 | Extracting a `RateLimiterService` now would be Sprint 2 scope and create an empty pass-through class |
| `String` for `defaultStrategy` in `RateLimitDefaults` | Hoplite sealed class decoding requires exact subclass name casing; YAML operator UX needs `FIXED_WINDOW` style | Custom Hoplite decoder works but adds ~30 lines of boilerplate for 3 enum-like values; convertible in Sprint 2 if needed |
