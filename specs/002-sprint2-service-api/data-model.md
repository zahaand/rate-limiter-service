# Data Model: Sprint 2 — Service Layer & REST API

**Branch**: `002-sprint2-service-api` | **Date**: 2026-05-05

---

## Domain Types (Sprint 1 — unchanged)

These types are defined in Sprint 1 and are consumed but not modified in Sprint 2
(except `ConfigRepository.delete` return type — see Interface Changes).

| Type | Location | Notes |
|------|----------|-------|
| `RateLimitPolicy` | `domain/model/RateLimitPolicy.kt` | `data class`; `limit ≥ 0`, `windowSeconds > 0` |
| `RateLimitDecision` | `domain/model/RateLimitDecision.kt` | `data class`; `allowed`, `remaining`, `resetAt: Instant` |
| `RateLimitStrategy` | `domain/model/RateLimitStrategy.kt` | `sealed class`; `FixedWindow`, `SlidingWindow`, `TokenBucket` |
| `AppConfig` | `infrastructure/config/AppConfig.kt` | Hoplite-bound; `toPolicy()` on `RateLimitDefaults` |

---

## New Domain Type

### `RateLimitKey`

```
Location: domain/model/RateLimitKey.kt
```

| Field | Type | Constraints |
|-------|------|-------------|
| `value` | `String` | non-blank (validated at construction; also validated at route layer) |

- `@JvmInline value class` — zero heap overhead, erased to `String` on JVM
- Constructor `init { require(value.isNotBlank()) { "key must not be blank" } }`
- Used as the single parameter of `RateLimiterService.check(key: RateLimitKey)`

---

## Service Type

### `RateLimiterService`

```
Location: service/RateLimiterService.kt
```

Constructor parameters (all `val`):

| Parameter | Type |
|-----------|------|
| `rateLimitRepository` | `RateLimitRepository` |
| `configRepository` | `ConfigRepository` |
| `appConfig` | `AppConfig` |

Method:

```
suspend fun check(key: RateLimitKey): RateLimitDecision
```

Internal flow:
1. `configRepository.get(key.value)` — `null` → fall back to `appConfig.rateLimit.toPolicy()`
2. `rateLimitRepository.check(key.value, policy, Instant.now())`
3. If `!decision.allowed` → log WARN with key, strategy, limit, windowSeconds, timestamp
4. Return `decision`

---

## Interface Change

### `ConfigRepository.delete` — return type changed to `Boolean`

```
Location: domain/port/ConfigRepository.kt
```

| Method | Before | After |
|--------|--------|-------|
| `delete(key: String)` | `Unit` | `Boolean` — `true` = key existed and was deleted; `false` = key not found |

Implementations updated:
- `InMemoryConfigRepository`: `policies.remove(key) != null`
- `RedisConfigRepository`: `(commands.del("config:$key") ?: 0L) > 0L`

---

## HTTP DTOs

All DTOs are in `routes/dto/`. All are `@Serializable` data classes with `val` fields.

### `CheckRequest`

```
Direction: inbound (POST /v1/check body)
```

| Field | Type | Constraints |
|-------|------|-------------|
| `key` | `String` | validated non-blank at route layer before use |

---

### `CheckResponse`

```
Direction: outbound (POST /v1/check response body)
```

| Field | Type | Serialization |
|-------|------|---------------|
| `allowed` | `Boolean` | native |
| `remaining` | `Int` | native |
| `resetAt` | `Instant` | ISO-8601 string via `InstantSerializer` |

---

### `LimitConfigRequest`

```
Direction: inbound (POST /v1/limits body)
```

| Field | Type | Constraints |
|-------|------|-------------|
| `key` | `String` | non-blank |
| `limit` | `Int` | ≥ 0 |
| `windowSeconds` | `Int` | > 0 |
| `strategy` | `String` | one of `FIXED_WINDOW`, `SLIDING_WINDOW`, `TOKEN_BUCKET` |

Route validates all four fields before creating `RateLimitPolicy`.

---

### `LimitConfigResponse`

```
Direction: outbound (POST /v1/limits 201, GET /v1/limits/{key} 200)
```

| Field | Type |
|-------|------|
| `key` | `String` |
| `limit` | `Int` |
| `windowSeconds` | `Int` |
| `strategy` | `String` |

`strategy` is always `policy.strategy.configName` (the canonical string value).

---

### `HealthResponse`

```
Direction: outbound (GET /health 200 / 503)
```

| Field | Type | Values |
|-------|------|--------|
| `status` | `String` | `"UP"` or `"DOWN"` |
| `redis` | `String` | `"UP"` or `"DOWN"` |

---

### `ErrorResponse`

```
Direction: outbound (all error responses)
```

| Field | Type |
|-------|------|
| `error` | `String` |

Used for HTTP 400, 404, 415, 500.

---

## Serialization Helper

### `InstantSerializer`

```
Location: routes/dto/InstantSerializer.kt
```

Custom `KSerializer<Instant>`:
- `serialize`: `encoder.encodeString(value.toString())` → `"2026-05-05T03:45:00Z"`
- `deserialize`: `Instant.parse(decoder.decodeString())`

Applied on `CheckResponse.resetAt` via `@Serializable(with = InstantSerializer::class)`.

---

## Source Layout (Sprint 2 additions)

```
src/main/kotlin/dev/zahaand/ratelimiter/
├── Application.kt                          ← updated: full wiring
├── domain/
│   ├── model/
│   │   └── RateLimitKey.kt                ← new
│   └── port/
│       └── ConfigRepository.kt            ← changed: delete → Boolean
├── infrastructure/
│   ├── memory/
│   │   └── InMemoryConfigRepository.kt    ← updated: delete → Boolean
│   └── redis/
│       └── RedisConfigRepository.kt       ← updated: delete → Boolean
├── service/
│   └── RateLimiterService.kt              ← new
└── routes/
    ├── CheckRoute.kt                      ← new
    ├── HealthRoute.kt                     ← new
    ├── LimitsRoute.kt                     ← new
    └── dto/
        ├── CheckRequest.kt                ← new
        ├── CheckResponse.kt               ← new
        ├── ErrorResponse.kt               ← new
        ├── HealthResponse.kt              ← new
        ├── InstantSerializer.kt           ← new
        ├── LimitConfigRequest.kt          ← new
        └── LimitConfigResponse.kt         ← new

src/test/kotlin/dev/zahaand/ratelimiter/
└── integration/
    ├── RedisTestContainer.kt              ← new: singleton object
    ├── CheckRouteIT.kt                    ← new
    ├── LimitsRouteIT.kt                   ← new
    └── HealthRouteIT.kt                   ← new
```
