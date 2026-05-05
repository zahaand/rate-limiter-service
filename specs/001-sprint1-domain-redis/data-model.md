# Data Model: Sprint 1 — Domain Model & Rate Limiting Infrastructure

Package root: `dev.zahaand.ratelimiter`

---

## Domain Layer (`domain/`)

### `RateLimitStrategy` — sealed class

```kotlin
sealed class RateLimitStrategy {
    abstract val configName: String                      // serialization key for Redis + YAML

    data object FixedWindow : RateLimitStrategy() {
        override val configName = "FIXED_WINDOW"
    }
    data object SlidingWindow : RateLimitStrategy() {
        override val configName = "SLIDING_WINDOW"
    }
    data object TokenBucket : RateLimitStrategy() {
        override val configName = "TOKEN_BUCKET"
    }

    companion object {
        private val byName = listOf(FixedWindow, SlidingWindow, TokenBucket)
            .associateBy { it.configName }

        fun fromConfigName(name: String): RateLimitStrategy =
            byName[name.uppercase()] ?: error("Unknown strategy: $name")
    }
}
```

**Invariants**:
- The set is closed — `when` expressions must be exhaustive (no `else`).
- `configName` is the canonical serialization string (used in YAML config and Redis hash fields).

---

### `RateLimitPolicy` — data class

```kotlin
data class RateLimitPolicy(
    val limit: Int,             // max requests per window; >= 0 (0 = reject all)
    val windowSeconds: Int,     // measurement window duration; > 0
    val strategy: RateLimitStrategy
) {
    init {
        require(limit >= 0) { "limit must be non-negative, was $limit" }
        require(windowSeconds > 0) { "windowSeconds must be positive, was $windowSeconds" }
    }
}
```

**Note on `limit = 0`**: Spec Key Entities states "must be positive" but Edge Cases explicitly handles `limit = 0` (reject all). Design resolves to `>= 0`; see Complexity Tracking in `plan.md`.

---

### `RateLimitDecision` — data class

```kotlin
data class RateLimitDecision(
    val allowed: Boolean,           // true if request is permitted
    val remaining: Int,             // requests still allowed before next rejection
    val resetAt: java.time.Instant  // absolute moment current window resets / next token available
)
```

**`remaining` invariants**:
- Always `>= 0`.
- Token Bucket: floor of fractional internal token count (`tokens.toInt()`).
- When `allowed = false`: always `0`.

---

## Algorithm Layer (`domain/algorithm/`)

Each algorithm is a stateless `object` with a pure `check()` function. No I/O. No side effects. Repositories own state storage and atomicity; algorithms own computation.

### `FixedWindowAlgorithm`

```kotlin
object FixedWindowAlgorithm {

    data class State(val count: Int, val windowStart: java.time.Instant)

    fun check(
        policy: RateLimitPolicy,
        state: State?,
        now: java.time.Instant
    ): Pair<RateLimitDecision, State>
}
```

**Logic**:
- `windowStart = Instant.ofEpochSecond(now.epochSecond / windowSeconds * windowSeconds)` — Unix-epoch-aligned boundary.
- If `state == null` or `state.windowStart != windowStart`: reset counter to 0 (new window).
- If `count < limit`: increment, return `allowed=true, remaining=limit-newCount, resetAt=windowStart+windowSeconds`.
- Else: return `allowed=false, remaining=0, resetAt=windowStart+windowSeconds`.

---

### `SlidingWindowAlgorithm`

```kotlin
object SlidingWindowAlgorithm {

    fun check(
        policy: RateLimitPolicy,
        timestamps: List<java.time.Instant>,    // requests within current window, oldest-first
        now: java.time.Instant
    ): Pair<RateLimitDecision, List<java.time.Instant>>
}
```

**Logic**:
- `cutoff = now - windowSeconds`. Prune all `timestamps` where `t <= cutoff`.
- `resetAt`: oldest valid timestamp + windowSeconds (if any); else `now + windowSeconds`.
- If `valid.size < limit`: append `now`, return `allowed=true, remaining=limit-newSize`.
- Else: return `allowed=false, remaining=0` (do not append `now`).
- Equal timestamps count as distinct entries.

---

### `TokenBucketAlgorithm`

```kotlin
object TokenBucketAlgorithm {

    data class State(val tokens: Double, val lastRefillAt: java.time.Instant)

    fun check(
        policy: RateLimitPolicy,
        state: State?,
        now: java.time.Instant
    ): Pair<RateLimitDecision, State>
}
```

**Logic**:
- `capacity = limit.toDouble()`, `refillRate = capacity / windowSeconds` tokens/second.
- `state == null` → initial state: `State(tokens = capacity, lastRefillAt = now)` (bucket starts full).
- `elapsed = Duration.between(state.lastRefillAt, now).toNanos() / 1e9`
- `refilled = min(state.tokens + elapsed * refillRate, capacity)` — tracked as `Double`.
- `remaining = refilled.toInt()` (floor) — exposed in `RateLimitDecision`.
- If `refilled >= 1.0`: deduct 1 token, return `allowed=true`.
  - `resetAt = now + (capacity - newTokens) / refillRate` seconds.
- Else: return `allowed=false, remaining=0`.
  - `resetAt = now + (1.0 - refilled) / refillRate` seconds (when next token arrives).
- **Zero-capacity guard**: if `refillRate == 0.0`, `resetAt = now + windowSeconds` (avoid division by zero).

---

## Repository Layer (`repository/`)

### `RateLimitRepository` — interface

```kotlin
interface RateLimitRepository {
    suspend fun check(key: String, policy: RateLimitPolicy, now: Instant): RateLimitDecision
}
```

### `ConfigRepository` — interface

```kotlin
interface ConfigRepository {
    suspend fun save(key: String, policy: RateLimitPolicy)
    suspend fun get(key: String): RateLimitPolicy?
    suspend fun delete(key: String)
}
```

---

### `InMemoryRateLimitRepository`

```
State maps (ConcurrentHashMap, one entry per client key):
  fixedWindowStates:   String → FixedWindowAlgorithm.State
  slidingWindowStates: String → List<Instant>
  tokenBucketStates:   String → TokenBucketAlgorithm.State
  mutexes:             String → Mutex  (one Mutex per key)
```

- Constructor: `clock: Clock = Clock.systemUTC()`
- `check()`: `mutexes.computeIfAbsent(key) { Mutex() }.withLock { ... }`
- Dispatches to algorithm via exhaustive `when (policy.strategy)`.
- Writes new state back to map inside the lock.

### `InMemoryConfigRepository`

```
policies: ConcurrentHashMap<String, RateLimitPolicy>
```

- `save`: `policies[key] = policy`
- `get`: `policies[key]`
- `delete`: `policies.remove(key)`
- No Mutex needed: `ConcurrentHashMap` guarantees individual-operation atomicity; no compound read-modify-write required.

---

### `RedisRateLimitRepository`

```
Constructor: commands: RedisCoroutinesCommands<String, String>, clock: Clock = Clock.systemUTC()
```

- One Lua script per strategy (loaded once, evaluated per request).
- Key patterns: `rl:fw:{key}:{windowStart}`, `rl:sw:{key}`, `rl:tb:{key}`
- All operations via `commands.eval(script, ScriptOutputType.MULTI, keys, args)`.

### `RedisConfigRepository`

```
Constructor: commands: RedisCoroutinesCommands<String, String>
```

- Key pattern: `config:{clientKey}` (Redis Hash)
- `save`: `HSET config:{key} limit {n} windowSeconds {n} strategy {configName}`
- `get`: `HGETALL config:{key}` → parse 3 fields → `RateLimitPolicy`; return `null` if key absent
- `delete`: `DEL config:{key}`

---

## Config Layer (`infrastructure/config/`)

### `AppConfig` (updated)

```kotlin
data class AppConfig(
    val server: ServerConfig,
    val redis: RedisConfig,
    val rateLimit: RateLimitDefaults
)

data class RateLimitDefaults(
    val defaultLimit: Int = 100,
    val defaultWindowSeconds: Int = 60,
    val defaultStrategy: String = "FIXED_WINDOW"   // added field
)
```

`Application.kt` converts `RateLimitDefaults` to `RateLimitPolicy` at startup:
```kotlin
val defaultPolicy = RateLimitPolicy(
    limit = config.rateLimit.defaultLimit,
    windowSeconds = config.rateLimit.defaultWindowSeconds,
    strategy = RateLimitStrategy.fromConfigName(config.rateLimit.defaultStrategy)
)
```

---

## `application.yaml` (updated)

```yaml
server:
  port: 8080

redis:
  host: localhost
  port: 6379

rateLimit:
  defaultLimit: 100
  defaultWindowSeconds: 60
  defaultStrategy: FIXED_WINDOW
```

---

## Entity Relationship Summary

```
RateLimitPolicy ──────────────── RateLimitStrategy  (1:1 field)
      │
      │ used as input to
      ▼
  Algorithm.check(key, policy, state, now)
      │
      │ returns
      ▼
  RateLimitDecision

  ConfigRepository ─ stores/retrieves ─► RateLimitPolicy (by client key)
  RateLimitRepository ─ executes atomic check ─► RateLimitDecision
```
