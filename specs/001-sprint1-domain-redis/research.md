# Research: Sprint 1 — Domain Model & Rate Limiting Infrastructure

## 1. Lettuce Coroutine API

**Decision**: Use `RedisCoroutinesCommands<String, String>` from `io.lettuce.core.api.coroutines`.

**How to obtain**:
```kotlin
val client = RedisClient.create("redis://localhost:6379")
val connection = client.connect()
val commands: RedisCoroutinesCommands<String, String> = connection.coroutines()
```

`coroutines()` is an extension function from `lettuce-core` + `kotlinx-coroutines-reactive`.
All commands (`get`, `set`, `eval`, etc.) are `suspend` functions — no blocking.

**Alternatives considered**:
- `ReactiveRedisCommands` (Project Reactor Flux/Mono): requires `kotlinx-coroutines-reactive` bridge and is verbose in Kotlin.
- Sync commands: PROHIBITED by Principle I.

---

## 2. Redis Data Structures per Algorithm

### Fixed Window
- **Structure**: String (atomic counter) with TTL
- **Key pattern**: `rl:fw:{clientKey}:{windowStart}` where `windowStart = floor(epochSec / windowSeconds) * windowSeconds`
- **Operations**: `INCR` + `EXPIREAT` (set TTL to window end)
- **Atomicity**: Lua script — INCR then conditional EXPIREAT in one round-trip

### Sliding Window
- **Structure**: Sorted Set (score = epoch milliseconds, member = `{epochMs}:{nano}` for uniqueness)
- **Key pattern**: `rl:sw:{clientKey}`
- **Operations (Lua)**:
  1. `ZREMRANGEBYSCORE key 0 (nowMs - windowMs)` — prune expired entries
  2. `ZADD key nowMs member` — record this request
  3. `ZCARD key` — count in-window requests
  4. If count > limit: `ZREM key member` — undo add, return rejected
  5. `EXPIRE key windowSeconds` — sliding TTL cleanup
- **Atomicity**: Single Lua script

### Token Bucket
- **Structure**: Hash with fields `tokens` (Double as string) and `lastRefillAt` (epoch ms as string)
- **Key pattern**: `rl:tb:{clientKey}`
- **Operations (Lua)**:
  1. Read `HGETALL key`
  2. Compute elapsed + refilled tokens (float arithmetic in Lua)
  3. If tokens >= 1.0: deduct 1, write back, return allowed
  4. Else: write back refilled count (no deduction), return rejected
- **Atomicity**: Single Lua script

---

## 3. Config Repository — Redis Storage

**Structure**: Redis Hash per client key
- **Key pattern**: `config:{clientKey}`
- **Hash fields**: `limit` (Int as string), `windowSeconds` (Int as string), `strategy` (String configName)

**Operations**: `HSET`, `HGETALL`, `DEL`

**Serialization**: Manual field-by-field (no JSON needed; 3 flat fields).

---

## 4. Hoplite Sealed Class Decoding

**Finding**: Hoplite 2.9.0 supports sealed class decoding via string matching against subclass names (case-insensitive). However, `data object FixedWindow` would require `"fixedwindow"` in YAML — not operator-friendly.

**Decision**: Use `String` field `defaultStrategy` in `RateLimitDefaults`; convert to `RateLimitStrategy` via companion factory `RateLimitStrategy.fromConfigName(name)`. YAML value: `FIXED_WINDOW`, `SLIDING_WINDOW`, `TOKEN_BUCKET`.

**Rationale**: Simple, explicit, readable YAML. The `fromConfigName` factory fails fast at startup if the value is unknown — same safety guarantee Hoplite would provide.

**Alternatives considered**:
- Custom Hoplite `Decoder<RateLimitStrategy>`: works but adds ~30 lines of decoder boilerplate for 3 values.
- Enum `StrategyType` mapped to sealed: introduces parallel type hierarchy.

---

## 5. AssertJ Gap in build.gradle.kts

**Finding**: Constitution mandates `JUnit 5 + MockK + AssertJ`. `build.gradle.kts` has `kotlin-test` and `mockk` but **no AssertJ dependency**.

**Decision**: Add `testImplementation("org.assertj:assertj-core:3.27.3")` to `build.gradle.kts`.

**JUnit 5 status**: `kotlin-test` in Kotlin 2.x with `useJUnitPlatform()` provides full JUnit 5 support (`@Test`, `@Nested`, `@BeforeEach`). No separate `junit-jupiter` artifact needed.

---

## 6. `RateLimitPolicy.limit = 0` Constraint Resolution

**Spec inconsistency**:
- Key Entities: "`limit` and `windowSeconds` must be positive" (positive = > 0)
- Edge Cases: "`limit = 0`: all requests are rejected immediately"

**Decision**: Treat `limit >= 0` as valid. `init` block uses `require(limit >= 0)`. All three algorithms naturally reject all requests when `limit = 0` without special casing:
- Fixed Window: `count < 0` → false → rejected ✓
- Sliding Window: `size < 0` → false → rejected ✓
- Token Bucket: capacity=0.0, refilled=0.0, `0.0 >= 1.0` → false → rejected ✓

Edge case for Token Bucket `resetAt` with `refillRate = 0.0`: `division by zero` produces `Infinity`. Use `coerceAtMost(Long.MAX_VALUE / 2)` when converting to milliseconds for `Instant.plusMillis()`.

---

## 7. Algorithm `resetAt` Semantics

| Algorithm | resetAt meaning |
|---|---|
| Fixed Window | `windowStart + windowSeconds` — hard boundary end |
| Sliding Window | `oldest_in_window_timestamp + windowSeconds` — when oldest request expires; `now + windowSeconds` if window empty |
| Token Bucket (allowed) | time until bucket refills to capacity from new token count |
| Token Bucket (rejected) | time until 1 token becomes available |

---

## 8. Clock Injection for Testability

**Decision**: All algorithms take `now: Instant` as a parameter — no `Instant.now()` calls inside algorithms. Tests inject deterministic `Instant` values.

Repository implementations take a `Clock` constructor parameter:
```kotlin
class InMemoryRateLimitRepository(
    private val clock: Clock = Clock.systemUTC()
) : RateLimitRepository
```

This allows `Clock.fixed(instant, ZoneOffset.UTC)` in tests per the unit test playbook.
