# Research: Sprint 3 — Documentation & Portfolio Polish

**Branch**: `003-sprint3-docs-polish` | **Date**: 2026-05-12
**Input**: spec.md + source code scan

---

## Decision 1: ktor-swagger-ui Version

**Decision**: `io.github.smiley4:ktor-swagger-ui:5.7.0`

**Rationale**: Version 5.x is the Ktor 3.x line. Latest is 5.7.0 as of 2026-05-12 (verified via
Maven Central metadata). v4.x targets Ktor 2.x and is incompatible. No transitive conflicts with
the existing dependency set (kotlinx.serialization, Ktor plugins, Lettuce).

**Alternatives considered**:
- v4.x — Ktor 2.x only, rejected
- `swagger-codegen` / manual OpenAPI JSON — more work, no interactive UI, rejected

---

## Decision 2: ktor-swagger-ui Integration Pattern (v5.x, Ktor 3.x)

**Decision**: Plugin install in `Application.module()` + inline `documentation { }` blocks in
existing route extension functions.

**Rationale**: Keeps OpenAPI documentation co-located with the handler it describes. Route
extension functions (`checkRoute`, `limitsRoute`, `healthRoute`) remain the single source of
truth for both the handler logic and the documentation.

**Integration pattern**:

```kotlin
// build.gradle.kts
implementation("io.github.smiley4:ktor-swagger-ui:5.7.0")

// Application.kt — install before routing block
install(SwaggerUI) {
    swagger {
        swaggerUrl = "swagger"
        forwardRoot = false
    }
    info {
        title = "Rate Limiter Service API"
        version = "1.0.0"
        description = "..."
    }
    server { url = "http://localhost:8080" }
}

// CheckRoute.kt — documentation{} wraps the existing post{} body
post("/check") {
    documentation {
        operationId = "checkRateLimit"
        summary = "Check rate limit for a client key"
        tags = listOf("Rate Limit")
        request {
            body<CheckRequest> {
                required = true
                example("tenant-api") { value = CheckRequest("tenant-api") }
            }
        }
        response {
            HttpStatusCode.OK to { ... }
            HttpStatusCode.BadRequest to { ... }
        }
    }
    // existing handler body unchanged
}
```

> **Note for implementer**: verify exact DSL method names against
> https://github.com/SMILEY4/ktor-swagger-ui/tree/main/ktor-swagger-ui#readme
> before writing code — v5.x broke several parameter names relative to v4.x.
> The `documentation { }` block shape is stable but nested DSL names may vary.

---

## Decision 3: Class Name Resolution (Spec vs. Source)

The spec used informal names. Actual source paths and class names (confirmed by file scan):

| Spec name (FR-020/FR-021) | Actual class | File |
|--------------------------|--------------|------|
| `FixedWindowAlgorithm` | `FixedWindowAlgorithm` (object) | `domain/algorithm/FixedWindowAlgorithm.kt` |
| `SlidingWindowAlgorithm` | `SlidingWindowAlgorithm` (object) | `domain/algorithm/SlidingWindowAlgorithm.kt` |
| `TokenBucketAlgorithm` | `TokenBucketAlgorithm` (object) | `domain/algorithm/TokenBucketAlgorithm.kt` |
| `RateLimiterRepository` interface | `RateLimitRepository` | `domain/port/RateLimitRepository.kt` |
| `LimitConfigRepository` interface | `ConfigRepository` | `domain/port/ConfigRepository.kt` |
| `RateLimiterService` | `RateLimiterService` | `service/RateLimiterService.kt` |
| `RateLimitPolicy` | `RateLimitPolicy` | `domain/model/RateLimitPolicy.kt` |
| `RateLimitDecision` | `RateLimitDecision` | `domain/model/RateLimitDecision.kt` |
| `Algorithm sealed class` | `RateLimitStrategy` (sealed class) | `domain/model/RateLimitStrategy.kt` |
| `RedisRateLimiterRepository` | `RedisRateLimitRepository` | `infrastructure/redis/RedisRateLimitRepository.kt` |

**Impact**: All tasks must use the actual class names. spec.md FRs are functionally correct
but use informal names — no spec update needed, plan and tasks use canonical names.

---

## Decision 4: KDoc Content Strategy per Class

### FixedWindowAlgorithm

**Non-obvious invariants to document**:
- Window alignment: `now.epochSecond / windowSeconds * windowSeconds` — integer division floors
  to the nearest window boundary relative to the Unix epoch (not service start time). E.g.,
  `windowSeconds=60`, `now=1234567890` → `windowStart=1234567860` (minute boundary).
- Implicit window index: `now.epochSecond / windowSeconds` — same window as long as index equals.
- New-window detection: `state.windowStart != windowStart` → reset count to 0.
- Pure function: no I/O, state passed in and returned.

### SlidingWindowAlgorithm

**Non-obvious invariants**:
- Cutoff: `now - windowSeconds` — any timestamp `>= cutoff` is within the window (inclusive).
- `resetAt` when at limit: `newTimestamps.first() + windowSeconds` — the moment the OLDEST
  request leaves the window, freeing one slot.
- `resetAt` when below limit: `now` — "you can always request right now."
- Pure function; Redis version uses ZSET for persistence.

### TokenBucketAlgorithm

**Non-obvious invariants**:
- `refillRate = capacity / windowSeconds` — token accumulation rate per second.
- Zero-rate guard: `if (policy.windowSeconds > 0) capacity / windowSeconds else 0.0` — prevents
  division by zero; zero rate means tokens never refill.
- `min(tokens + elapsed * refillRate, capacity)` — fractional accumulation, capped at capacity.
- Allow only if `refilled >= 1.0` — requires at least one full token.
- `newTokens.toInt()` — floors to integer (Kotlin truncates toward zero for positive doubles).
- Initial state: full bucket (`tokens = capacity`) on first request.

### RateLimitRepository (interface)

**Non-obvious invariants**:
- `check()` is atomic: the "is under limit?" check and the counter increment happen in one
  Redis EVAL, with no race condition between concurrent callers.
- `now: Instant` is explicit — not derived internally — enabling deterministic testing without
  mocking time.
- Returns a full decision; no separate "record usage" call needed.

### ConfigRepository (interface)

**Non-obvious invariants**:
- `delete()` returns `Boolean`: `true` = key existed and was deleted; `false` = key not found.
  This avoids a `get()+delete()` round trip for the 404 response on DELETE.
- `save()` is upsert — existing policy for the key is silently replaced.

### RateLimiterService

**Non-obvious invariants**:
- Default fallback: `configRepository.get(key.value) ?: appConfig.rateLimit.toPolicy()` — no
  exception, no 500 for unconfigured keys; always returns a decision.
- WARN log: 5 fields — `key`, `strategy`, `limit`, `windowSeconds`, `timestamp`. Fire-and-forget:
  a logging failure does not affect the decision or the HTTP response.
- Slight timestamp skew: `timestamp` in the log is `Instant.now()` called AFTER `check()` — it
  is close to but not identical to `decision.resetAt`.

### RateLimitPolicy

**Non-obvious invariant**: `limit = 0` is valid (all requests rejected). `init` block enforces
`limit >= 0` and `windowSeconds > 0`. Dual-layer: route also validates to distinguish external
input errors from internal misuse.

### RateLimitDecision

**Non-obvious invariant**: `remaining >= 0` always — concurrent races at the limit boundary
floor at 0. `resetAt` semantics vary by algorithm (next window for Fixed, oldest-entry-expiry
for Sliding, refill-time for Token Bucket).

### RateLimitStrategy (sealed class)

**Non-obvious invariant**: `when` dispatch on sealed type is exhaustive — compiler rejects new
subtypes added without updating every dispatch site. `fromConfigName` uses a lazy `byName` map
for O(1) lookup; throws `error()` (not exception) for unknown names.

### RedisRateLimitRepository

**Non-obvious invariants**:
- Three Lua scripts in companion object — `EVAL` executes as a single Redis transaction.
- Key prefix scheme: `rl:fw:{key}:{windowStart}` (one key per window), `rl:sw:{key}` + `:seq`
  (ZSET + sequence counter), `rl:tb:{key}` (HMSET with tokens/lastRefillAt).
- Fixed window: `EXPIREAT resetAt` so the key auto-expires at window end.
- Sliding window: `ZREMRANGEBYSCORE ... '(' .. cutoff` (exclusive lower bound) cleans expired
  entries; `:seq` counter provides unique ZADD members.
- Token bucket: `EXPIRE windowSeconds * 2` — gives refill time plus a full window buffer.

---

## Decision 5: ConcurrentCheckIT Pattern

Uses the existing `RedisTestContainer` singleton. Pattern:

```kotlin
class ConcurrentCheckIT {
    @BeforeEach fun setUp() { /* FLUSHDB */ }

    @Test
    fun `50 concurrent checks with limit=10 yield exactly 10 allowed`() = runTest {
        testApplication {
            application { module(testConfig) }
            // set policy: limit=10, strategy=FIXED_WINDOW, windowSeconds=60
            val results = (1..50).map {
                async {
                    client.post("/v1/check") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"key":"concurrent-test"}""")
                    }
                }
            }.awaitAll()
            
            val bodies = results.map { Json.parseToJsonElement(it.bodyAsText()).jsonObject }
            assertThat(bodies.count { it["allowed"]!!.jsonPrimitive.boolean }).isEqualTo(10)
            assertThat(bodies.count { !it["allowed"]!!.jsonPrimitive.boolean }).isEqualTo(40)
            assertThat(results.all { it.status == HttpStatusCode.OK }).isTrue()
        }
    }
}
```

**Note**: `testApplication` uses an in-process HTTP engine. Concurrency via coroutines in
`runTest` context exercises the real Redis fixed-window Lua script atomicity.

---

## Decision 6: README Structure

Nine required sections (FR-022). Algorithm trade-off table:

| Algorithm | Model | Memory | Reset |
|-----------|-------|--------|-------|
| Fixed Window | Hard boundary per period | O(1) | Burst at boundary |
| Sliding Window | Continuous rolling window | O(requests in window) | Smooth |
| Token Bucket | Continuous refill | O(1) | Gradual, never hard reset |
