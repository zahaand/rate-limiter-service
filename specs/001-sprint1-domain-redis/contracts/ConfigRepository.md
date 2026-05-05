# Contract: ConfigRepository

**Package**: `dev.zahaand.ratelimiter.repository`
**File**: `src/main/kotlin/dev/zahaand/ratelimiter/repository/ConfigRepository.kt`

## Interface

```kotlin
interface ConfigRepository {

    /**
     * Persists [policy] for [key], overwriting any existing entry.
     */
    suspend fun save(key: String, policy: RateLimitPolicy)

    /**
     * Returns the policy for [key], or null if none has been saved.
     */
    suspend fun get(key: String): RateLimitPolicy?

    /**
     * Removes the policy for [key]. No-op if key is absent.
     */
    suspend fun delete(key: String)
}
```

## Implementations

| Class | Location | Storage |
|---|---|---|
| `InMemoryConfigRepository` | `repository/inmemory/` | `ConcurrentHashMap<String, RateLimitPolicy>` |
| `RedisConfigRepository` | `repository/redis/` | Redis Hash `config:{key}` with fields `limit`, `windowSeconds`, `strategy` |

## Behaviour Contracts

| Scenario | Expected outcome |
|---|---|
| `save(key, policy)` then `get(key)` | Returns an equal policy (all three fields match) |
| `delete(key)` then `get(key)` | Returns `null` |
| `delete(key)` when key absent | No error; completes normally |
| `get(key)` when key absent | Returns `null` (no exception) |
| Save overwrites existing | `get` returns latest saved policy |

## Constraints

- All three methods MUST be `suspend`.
- `RedisConfigRepository.save` serialises `RateLimitStrategy` as `strategy.configName` (e.g., `"FIXED_WINDOW"`).
- `RedisConfigRepository.get` deserialises strategy via `RateLimitStrategy.fromConfigName(value)`.
- `InMemoryConfigRepository` does NOT require a `Mutex` — individual `ConcurrentHashMap` operations are atomic and no compound read-modify-write is needed.
