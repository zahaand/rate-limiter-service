# Contract: RateLimitRepository

**Package**: `dev.zahaand.ratelimiter.repository`
**File**: `src/main/kotlin/dev/zahaand/ratelimiter/repository/RateLimitRepository.kt`

## Interface

```kotlin
interface RateLimitRepository {

    /**
     * Atomically evaluates a rate limit check for [key] under [policy] at moment [now].
     *
     * Atomicity guarantee: concurrent calls for the same [key] must not collectively
     * allow more requests than [policy.limit] within the measurement window.
     *
     * @param key     opaque client identifier; must be non-empty
     * @param policy  governing rule including strategy, limit, and window duration
     * @param now     the moment of the request; injected for testability
     * @return        decision with allowed/remaining/resetAt fields always populated
     */
    suspend fun check(key: String, policy: RateLimitPolicy, now: Instant): RateLimitDecision
}
```

## Implementations

| Class | Location | Atomicity Mechanism |
|---|---|---|
| `InMemoryRateLimitRepository` | `repository/inmemory/` | `Mutex` per key (`kotlinx.coroutines.sync`) |
| `RedisRateLimitRepository` | `repository/redis/` | Lua script per strategy (single round-trip) |

## Behaviour Contracts

| Scenario | Expected outcome |
|---|---|
| `policy.limit = 0` | Always returns `allowed=false, remaining=0` |
| First request for a new key (no state) | Allowed (counts as 1 of N); state initialised |
| Request exactly at the limit | `allowed=false, remaining=0` |
| Request after window expires (Fixed Window) | Counter resets; request allowed |
| Concurrent requests at the boundary | At most `policy.limit` allowed total — no over-allowance |
| Token Bucket: bucket starts full | First `limit` rapid requests all allowed |

## Constraints

- `check()` MUST be declared `suspend` — no blocking I/O on dispatcher threads.
- Implementations MUST NOT contain business logic outside of algorithm dispatch.
- Implementations MUST inject `Clock` for deterministic testing.
