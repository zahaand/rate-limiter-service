# HTTP API Contract: rate-limiter-service v1

**Base URL**: `http://localhost:8080`
**Content-Type**: `application/json` (all request and response bodies)

---

## POST /v1/check

Check whether a client key is within its configured rate limit.

### Request

```
POST /v1/check
Content-Type: application/json
```

```json
{
  "key": "user-123"
}
```

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `key` | string | yes | non-blank |

### Responses

**200 OK — allowed**
```json
{
  "allowed": true,
  "remaining": 42,
  "resetAt": "2026-05-05T03:45:00Z"
}
```

**200 OK — rejected**
```json
{
  "allowed": false,
  "remaining": 0,
  "resetAt": "2026-05-05T03:45:00Z"
}
```

> Note: The response is always HTTP 200 regardless of `allowed`. The caller decides how
> to handle rejection. HTTP 429 is not used.

**400 Bad Request — blank key**
```json
{
  "error": "key must not be blank"
}
```

| Response field | Type | Notes |
|----------------|------|-------|
| `allowed` | boolean | `true` = request permitted, `false` = rejected |
| `remaining` | integer ≥ 0 | requests left in current window after this one |
| `resetAt` | string (ISO-8601 UTC) | when the current window resets |

---

## POST /v1/limits

Create or replace the rate limit policy for a key.

### Request

```
POST /v1/limits
Content-Type: application/json
```

```json
{
  "key": "tenant-A",
  "limit": 100,
  "windowSeconds": 60,
  "strategy": "FIXED_WINDOW"
}
```

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `key` | string | yes | non-blank |
| `limit` | integer | yes | ≥ 0 |
| `windowSeconds` | integer | yes | > 0 |
| `strategy` | string | yes | `FIXED_WINDOW` \| `SLIDING_WINDOW` \| `TOKEN_BUCKET` |

### Responses

**201 Created**
```json
{
  "key": "tenant-A",
  "limit": 100,
  "windowSeconds": 60,
  "strategy": "FIXED_WINDOW"
}
```

**400 Bad Request — validation failure**
```json
{
  "error": "limit must be >= 0"
}
```

Possible error messages:
- `"key must not be blank"`
- `"limit must be >= 0"`
- `"windowSeconds must be > 0"`
- `"strategy must be one of: FIXED_WINDOW, SLIDING_WINDOW, TOKEN_BUCKET"`

---

## GET /v1/limits/{key}

Retrieve the stored policy for a key.

### Request

```
GET /v1/limits/tenant-A
```

### Responses

**200 OK**
```json
{
  "key": "tenant-A",
  "limit": 100,
  "windowSeconds": 60,
  "strategy": "FIXED_WINDOW"
}
```

**404 Not Found**
```json
{
  "error": "policy not found for key: tenant-A"
}
```

---

## DELETE /v1/limits/{key}

Delete the policy for a key.

### Request

```
DELETE /v1/limits/tenant-A
```

### Responses

**204 No Content** — policy deleted (empty body)

**404 Not Found**
```json
{
  "error": "policy not found for key: tenant-A"
}
```

---

## GET /health

Report the operational status of the service and its Redis dependency.

### Request

```
GET /health
```

### Responses

**200 OK — healthy**
```json
{
  "status": "UP",
  "redis": "UP"
}
```

**503 Service Unavailable — degraded**
```json
{
  "status": "DOWN",
  "redis": "DOWN"
}
```

The Redis check issues a single PING with a 1-second hard timeout. No retry is attempted.
No other `status`/`redis` combinations are defined.

---

## Error Responses (generic)

**500 Internal Server Error** — unhandled exception
```json
{
  "error": "internal server error"
}
```

Raw stack traces are never included in error responses.
