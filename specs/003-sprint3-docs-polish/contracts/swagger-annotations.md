# Swagger Annotation Contract: rate-limiter-service

**Sprint**: 3 | **Library**: ktor-swagger-ui 5.7.0

This document defines the required OpenAPI documentation for each endpoint. It is the
reference contract for implementing `documentation { }` blocks in the route files.

---

## Plugin Configuration (Application.kt)

```kotlin
install(SwaggerUI) {
    swagger {
        swaggerUrl = "swagger"
        forwardRoot = false
    }
    info {
        title = "Rate Limiter Service API"
        version = "1.0.0"
        description = "Standalone rate limiting service. Evaluates per-key policies via " +
                "Fixed Window, Sliding Window, or Token Bucket algorithms. All decisions " +
                "are atomic and backed by Redis."
    }
    server { url = "http://localhost:8080" }
    tag("Rate Limit") { description = "Check and manage per-key rate limit policies" }
    tag("Policy Management") { description = "CRUD operations on per-key rate limit policies" }
    tag("Observability") { description = "Service health and Redis connectivity status" }
}
```

---

## POST /v1/check

**Summary** (imperative phrase): `Check rate limit for a client key`
**Tags**: `Rate Limit`
**Operation ID**: `checkRateLimit`

### Request body

Schema: `CheckRequest`
Required: yes

Named examples:

| Name | Value |
|------|-------|
| `"tenant-api"` | `{ "key": "tenant-api" }` |
| `"user-session"` | `{ "key": "user-abc-session-9f2e" }` |

### Responses

| Code | Description | Schema | Named example |
|------|-------------|--------|---------------|
| 200 | Rate limit decision returned. Always 200 for a structurally valid request — check the `allowed` field for the decision. | `CheckResponse` | `"allowed"`: `{ "allowed": true, "remaining": 7, "resetAt": "2026-05-12T14:00:00Z" }` · `"rejected"`: `{ "allowed": false, "remaining": 0, "resetAt": "2026-05-12T14:00:00Z" }` |
| 400 | Validation error — blank key, key exceeds 512 characters, or malformed JSON body. | `ErrorResponse` | `"blank-key"`: `{ "error": "key must not be blank" }` |
| 415 | Content-Type is not `application/json`. | `ErrorResponse` | `"wrong-content-type"`: `{ "error": "unsupported media type" }` |
| 500 | Unhandled server error. Raw stack traces are never exposed. | `ErrorResponse` | `"internal"`: `{ "error": "internal server error" }` |

---

## POST /v1/limits

**Summary**: `Create or replace a rate limit policy`
**Tags**: `Policy Management`
**Operation ID**: `upsertPolicy`

### Request body

Schema: `LimitConfigRequest`
Required: yes

Named examples:

| Name | Value |
|------|-------|
| `"fixed-window-policy"` | `{ "key": "tenant-A", "limit": 100, "windowSeconds": 60, "strategy": "FIXED_WINDOW" }` |
| `"sliding-window-policy"` | `{ "key": "payment-api", "limit": 50, "windowSeconds": 30, "strategy": "SLIDING_WINDOW" }` |

### Responses

| Code | Description | Schema | Named example |
|------|-------------|--------|---------------|
| 201 | Policy stored. Body echoes the stored values exactly. | `LimitConfigResponse` | `"tenant-a-policy"`: `{ "key": "tenant-A", "limit": 100, "windowSeconds": 60, "strategy": "FIXED_WINDOW" }` |
| 400 | Validation error. Five possible messages: blank key, key >512 chars, limit < 0, windowSeconds ≤ 0, unknown strategy. | `ErrorResponse` | `"negative-limit"`: `{ "error": "limit must be >= 0" }` · `"zero-window"`: `{ "error": "windowSeconds must be > 0" }` · `"bad-strategy"`: `{ "error": "strategy must be one of: FIXED_WINDOW, SLIDING_WINDOW, TOKEN_BUCKET" }` |
| 415 | Content-Type is not `application/json`. | `ErrorResponse` | `"wrong-content-type"`: `{ "error": "unsupported media type" }` |
| 500 | Unhandled server error. | `ErrorResponse` | `"internal"`: `{ "error": "internal server error" }` |

---

## GET /v1/limits/{key}

**Summary**: `Retrieve a rate limit policy by key`
**Tags**: `Policy Management`
**Operation ID**: `getPolicy`

### Path parameters

| Name | Type | Description |
|------|------|-------------|
| `key` | string | The client key (percent-decoded by HTTP layer). |

### Responses

| Code | Description | Schema | Named example |
|------|-------------|--------|---------------|
| 200 | Policy found. | `LimitConfigResponse` | `"tenant-a-policy"`: `{ "key": "tenant-A", "limit": 100, "windowSeconds": 60, "strategy": "FIXED_WINDOW" }` |
| 404 | No policy stored for this key. | `ErrorResponse` | `"not-found"`: `{ "error": "policy not found for key: tenant-A" }` |
| 500 | Unhandled server error. | `ErrorResponse` | `"internal"`: `{ "error": "internal server error" }` |

---

## DELETE /v1/limits/{key}

**Summary**: `Delete a rate limit policy by key`
**Tags**: `Policy Management`
**Operation ID**: `deletePolicy`

### Path parameters

| Name | Type | Description |
|------|------|-------------|
| `key` | string | The client key (percent-decoded by HTTP layer). |

### Responses

| Code | Description | Schema | Named example |
|------|-------------|--------|---------------|
| 204 | Policy deleted. Empty body. | — | — |
| 404 | No policy found for this key. | `ErrorResponse` | `"not-found"`: `{ "error": "policy not found for key: tenant-A" }` |
| 500 | Unhandled server error. | `ErrorResponse` | `"internal"`: `{ "error": "internal server error" }` |

---

## GET /health

**Summary**: `Check service and Redis health`
**Tags**: `Observability`
**Operation ID**: `getHealth`

No request body. No path parameters.

### Responses

| Code | Description | Schema | Named example |
|------|-------------|--------|---------------|
| 200 | Service and Redis are operational. | `HealthResponse` | `"healthy"`: `{ "status": "UP", "redis": "UP" }` |
| 503 | Redis is unreachable or PING timed out (1-second timeout). | `HealthResponse` | `"degraded"`: `{ "status": "DOWN", "redis": "DOWN" }` |

---

## Quality Checklist (FR-018, FR-019)

Before considering Swagger implementation complete, verify:

- [ ] Every summary is an imperative phrase (not a URL, not "GET ...", not "Returns ...")
- [ ] All required status codes present per FR-018 matrix
- [ ] Every named example uses domain values: `tenant-A`, `payment-api`, `user-abc-session-9f2e`
- [ ] No placeholder values: `"string"`, unnamed `example`, `0` where non-zero is typical
- [ ] "Try it out" on `POST /v1/check` returns a live response against running service
- [ ] `/swagger` loads in browser without 404 or JS console errors
- [ ] `/v1/check` and `/health` remain accessible while Swagger UI is installed
