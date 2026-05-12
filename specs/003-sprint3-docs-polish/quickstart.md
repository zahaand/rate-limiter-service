# Quick Start: Sprint 3

## Prerequisites

- Java 21+ (verify: `java -version`)
- Docker Desktop running (for Redis and integration tests)

---

## Start Redis

```bash
docker run -d --name redis-dev -p 6379:6379 redis:7-alpine
```

---

## Run the Service

```bash
./gradlew run
```

Service starts on `http://localhost:8080`. Default config from `src/main/resources/application.yaml`:
```yaml
server.port:                    8080
redis.host:                     localhost
redis.port:                     6379
rateLimit.defaultLimit:         100
rateLimit.defaultWindowSeconds: 60
rateLimit.defaultStrategy:      FIXED_WINDOW
```

---

## Open Swagger UI

```
http://localhost:8080/swagger
```

All five endpoints should be listed: `POST /v1/check`, `POST /v1/limits`,
`GET /v1/limits/{key}`, `DELETE /v1/limits/{key}`, `GET /health`.

Click **"Try it out"** on `POST /v1/check`, enter `{ "key": "tenant-api" }`, click **Execute**.
Expected response: HTTP 200 with `allowed: true`.

---

## Smoke Tests (TD-02)

Run these manually against the running service to verify all endpoints and JSON log output.

### 1. Check rate limit (default policy)
```bash
curl -s -X POST http://localhost:8080/v1/check \
  -H "Content-Type: application/json" \
  -d '{"key":"smoke-test"}' | jq .
# Expected: {"allowed":true,"remaining":99,"resetAt":"..."}
```

### 2. Create a policy
```bash
curl -s -X POST http://localhost:8080/v1/limits \
  -H "Content-Type: application/json" \
  -d '{"key":"smoke-test","limit":3,"windowSeconds":60,"strategy":"FIXED_WINDOW"}' | jq .
# Expected: HTTP 201, body echoes policy
```

### 3. Retrieve the policy
```bash
curl -s http://localhost:8080/v1/limits/smoke-test | jq .
# Expected: {"key":"smoke-test","limit":3,"windowSeconds":60,"strategy":"FIXED_WINDOW"}
```

### 4. Exhaust the limit and check logs
```bash
for i in 1 2 3 4; do
  curl -s -X POST http://localhost:8080/v1/check \
    -H "Content-Type: application/json" \
    -d '{"key":"smoke-test"}' | jq .allowed
done
# Expected: true, true, true, false
# Check service stdout for a WARN JSON log line on the 4th call containing:
#   key, strategy, limit, windowSeconds, timestamp
```

### 5. Delete the policy
```bash
curl -s -o /dev/null -w "%{http_code}" -X DELETE http://localhost:8080/v1/limits/smoke-test
# Expected: 204
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/v1/limits/smoke-test
# Expected: 404
```

### 6. Health check
```bash
curl -s http://localhost:8080/health | jq .
# Expected: {"status":"UP","redis":"UP"}
```

### 7. Swagger UI
- Open `http://localhost:8080/swagger` in a browser
- Confirm all 5 endpoints visible with imperative-phrase summaries
- Confirm named examples are realistic (no `"string"` placeholders)

---

## Run Tests

```bash
# Unit tests only (no Docker required)
./gradlew test --tests "dev.zahaand.ratelimiter.domain.*"
./gradlew test --tests "dev.zahaand.ratelimiter.infrastructure.memory.*"

# All tests including integration (Docker required)
./gradlew test
```

Integration tests start a real Redis container via Testcontainers (`redis:7-alpine`).
`ConcurrentCheckIT` verifies atomicity: 50 simultaneous requests with limit=10 → exactly 10 allowed.

---

## Stop Redis

```bash
docker stop redis-dev && docker rm redis-dev
```
