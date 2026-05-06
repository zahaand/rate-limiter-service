# Quickstart: Sprint 2 — Service Layer & REST API

## Prerequisites

- JDK 21
- Docker (for Testcontainers integration tests)
- Redis running locally on `localhost:6379` (for manual smoke testing)

---

## Build

```bash
./gradlew build
```

---

## Run

```bash
./gradlew run
```

Service starts on `http://localhost:8080` (configurable in `src/main/resources/application.yaml`).

---

## Smoke test (manual)

```bash
# Check endpoint — should use default policy (100 req/60s, FIXED_WINDOW)
curl -s -X POST http://localhost:8080/v1/check \
  -H "Content-Type: application/json" \
  -d '{"key":"smoke-test"}' | jq

# Create a custom policy
curl -s -X POST http://localhost:8080/v1/limits \
  -H "Content-Type: application/json" \
  -d '{"key":"smoke-test","limit":3,"windowSeconds":10,"strategy":"FIXED_WINDOW"}' | jq

# Read it back
curl -s http://localhost:8080/v1/limits/smoke-test | jq

# Check with custom policy (3 allowed, then rejected)
for i in 1 2 3 4; do
  curl -s -X POST http://localhost:8080/v1/check \
    -H "Content-Type: application/json" \
    -d '{"key":"smoke-test"}' | jq .allowed
done

# Health
curl -s http://localhost:8080/health | jq

# Delete policy
curl -s -X DELETE http://localhost:8080/v1/limits/smoke-test -w "\nHTTP %{http_code}\n"

# Blank key → 400
curl -s -X POST http://localhost:8080/v1/check \
  -H "Content-Type: application/json" \
  -d '{"key":""}' | jq
```

---

## Run tests

```bash
# All tests (unit + integration — requires Docker)
./gradlew test

# Unit tests only (no Docker required)
./gradlew test --tests "dev.zahaand.ratelimiter.domain.*"
./gradlew test --tests "dev.zahaand.ratelimiter.infrastructure.memory.*"

# Integration tests only
./gradlew test --tests "dev.zahaand.ratelimiter.integration.*"
```

---

## Test structure

```
src/test/kotlin/dev/zahaand/ratelimiter/
├── domain/                          # Sprint 1 unit tests (unchanged)
│   ├── algorithm/
│   └── model/
├── infrastructure/
│   └── memory/                      # Sprint 1 in-memory repo tests (unchanged)
└── integration/                     # Sprint 2 integration tests
    ├── RedisTestContainer.kt         # Singleton Testcontainer (started once per JVM)
    ├── CheckRouteIT.kt               # POST /v1/check
    ├── LimitsRouteIT.kt              # CRUD /v1/limits
    └── HealthRouteIT.kt              # GET /health
```

---

## Configuration reference

`src/main/resources/application.yaml`:

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

All fields have defaults. Override via environment-specific YAML or by passing an `AppConfig`
instance directly to `Application.module(overrideConfig)` in tests.
