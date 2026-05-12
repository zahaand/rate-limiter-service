# Data Model: Sprint 3 — Documentation & Portfolio Polish

**Branch**: `003-sprint3-docs-polish` | **Spec**: [spec.md](spec.md)

---

Sprint 3 introduces no new domain entities. This document serves two purposes:

1. **OpenAPI schema shapes** — the request/response schemas that will be declared in
   Swagger annotations, cross-referenced from the existing HTTP contract.
2. **KDoc scope matrix** — the complete list of classes/interfaces receiving documentation,
   their priority, and what must be covered.

---

## OpenAPI Schema Shapes

All schemas are derived from the existing `routes/dto/` classes. No new DTOs are added.

### CheckRequest

```
key: String  — non-blank, max 512 chars
```

Named example — `"tenant-api"`:
```json
{ "key": "tenant-api" }
```

### CheckResponse

```
allowed:   Boolean
remaining: Int    (>= 0)
resetAt:   String (ISO-8601 UTC, seconds precision, Z suffix)
```

Named examples:
- `"allowed"`: `{ "allowed": true, "remaining": 7, "resetAt": "2026-05-12T14:00:00Z" }`
- `"rejected"`: `{ "allowed": false, "remaining": 0, "resetAt": "2026-05-12T14:00:00Z" }`

### LimitConfigRequest

```
key:           String  — non-blank, max 512 chars
limit:         Int     — >= 0
windowSeconds: Int     — > 0
strategy:      String  — FIXED_WINDOW | SLIDING_WINDOW | TOKEN_BUCKET
```

Named example — `"tenant-a-policy"`:
```json
{ "key": "tenant-A", "limit": 100, "windowSeconds": 60, "strategy": "FIXED_WINDOW" }
```

### LimitConfigResponse

Same four fields as `LimitConfigRequest`. Named example — `"tenant-a-policy"`:
```json
{ "key": "tenant-A", "limit": 100, "windowSeconds": 60, "strategy": "FIXED_WINDOW" }
```

### HealthResponse

```
status: String  — "UP" | "DOWN"
redis:  String  — "UP" | "DOWN"
```

Named examples:
- `"healthy"`: `{ "status": "UP", "redis": "UP" }`
- `"degraded"`: `{ "status": "DOWN", "redis": "DOWN" }`

### ErrorResponse

```
error: String  — human-readable error message
```

Named examples (per endpoint):
- `"blank-key"`: `{ "error": "key must not be blank" }`
- `"not-found"`: `{ "error": "policy not found for key: tenant-A" }`
- `"internal"`: `{ "error": "internal server error" }`

---

## KDoc Scope Matrix

| Class / Interface | File | Priority | KDoc must cover |
|-------------------|------|----------|-----------------|
| `FixedWindowAlgorithm` | `domain/algorithm/FixedWindowAlgorithm.kt` | HIGH | Epoch-aligned window, window index, pure-function contract, atomicity note (Redis impl) |
| `FixedWindowAlgorithm.State` | same | HIGH | Fields: count, windowStart |
| `SlidingWindowAlgorithm` | `domain/algorithm/SlidingWindowAlgorithm.kt` | HIGH | Cutoff boundary (inclusive), resetAt-at-limit (oldest expiry), below-limit resetAt (now) |
| `TokenBucketAlgorithm` | `domain/algorithm/TokenBucketAlgorithm.kt` | HIGH | refillRate derivation, zero-rate guard, fractional accumulation, floor via toInt() |
| `TokenBucketAlgorithm.State` | same | HIGH | Fields: tokens (fractional), lastRefillAt |
| `RateLimitRepository` | `domain/port/RateLimitRepository.kt` | HIGH | Atomicity guarantee, `now` as explicit param for testability |
| `RateLimiterService` | `service/RateLimiterService.kt` | HIGH | Default-policy fallback, WARN log 5-field contract, fire-and-forget semantics |
| `RateLimitPolicy` | `domain/model/RateLimitPolicy.kt` | MEDIUM | limit=0 validity, init-block invariants, dual-layer validation rationale |
| `RateLimitDecision` | `domain/model/RateLimitDecision.kt` | MEDIUM | remaining>=0 guarantee, resetAt semantics vary by algorithm |
| `RateLimitStrategy` | `domain/model/RateLimitStrategy.kt` | MEDIUM | Sealed = exhaustive dispatch, configName for API string, fromConfigName O(1) lookup |
| `ConfigRepository` | `domain/port/ConfigRepository.kt` | MEDIUM | delete Boolean contract, save is upsert |
| `RedisRateLimitRepository` | `infrastructure/redis/RedisRateLimitRepository.kt` | MEDIUM | Lua atomicity, key prefix scheme, Fixed/Sliding/TokenBucket Redis structures |

**Out of scope**: all `routes/dto/` classes, route extension functions, `AppConfig` hierarchy,
`InMemoryConfigRepository`, `InMemoryRateLimitRepository`, `Application.kt`.
