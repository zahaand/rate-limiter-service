🇷🇺 Описание на русском ниже  /  🇬🇧 Russian description below

---

# Rate Limiter Service

![Kotlin](https://img.shields.io/badge/Kotlin-2.1.20-7F52FF?style=flat-square&logo=kotlin) ![Ktor](https://img.shields.io/badge/Ktor-3.1.3-7F52FF?style=flat-square&logo=kotlin) ![Gradle](https://img.shields.io/badge/Gradle-8.12.1-02303A?style=flat-square&logo=gradle) ![Version](https://img.shields.io/badge/version-0.1.0-blue?style=flat-square) ![Tests](https://img.shields.io/badge/tests-10%20passed-brightgreen?style=flat-square) ![SDD](https://img.shields.io/badge/SDD-Spec--Driven_Development-blueviolet?style=flat-square)

10 tests | 3 sprints | Standalone HTTP rate-limiting service — atomic per-key decisions backed by Redis

## Methodology

This project follows **SDD — Spec-Driven Development** using **Spec Kit** and **[Claude Code](https://claude.ai/code)**.

All architectural decisions are captured in `CONSTITUTION.md` before implementation. Each sprint starts with a feature spec (`spec.md`), goes through planning (`plan.md`), task decomposition (`tasks.md`), and cross-artifact analysis (`/speckit-analyze`) before any code is written. Development workflows are defined in `CLAUDE.md`.

## Features

- **Hexagonal Architecture** — domain layer has zero knowledge of Redis or HTTP; infrastructure adapts to domain ports, not the other way around
- Developed with **SDD — Spec-Driven Development**, **Spec Kit**, and **Claude Code**
- **Race-free decisions under concurrent load** — every algorithm is implemented as an atomic Redis Lua script; read-modify-write executes without interruption across 50 concurrent clients (verified by `ConcurrentCheckIT`)
- **Structured JSON logging** — all decisions for keys that fall back to the default policy are emitted as WARN-level structured log entries (logstash-logback-encoder), ready for Loki / ELK ingestion
- **Sealed algorithm hierarchy** — `RateLimitStrategy` is a `sealed class`; adding a new algorithm without updating every dispatch site is a compile error, not a runtime defect
- **Coroutine-first I/O** — Lettuce coroutine adapter; blocking I/O on Ktor's dispatcher is prohibited by `CONSTITUTION.md`
- **Runtime-configurable policies** — per-key rate limit policies are stored in Redis and can be changed via REST API without a service restart
- 6 unit tests (pure Kotlin, no I/O) | 4 integration tests (Testcontainers, real Redis)

## Architecture

```
dev.zahaand.ratelimiter
├── Application.kt          — entry point, plugin wiring, manual constructor injection
├── domain/
│   ├── algorithm/          — pure algorithm logic, no I/O (Fixed Window, Sliding Window, Token Bucket)
│   ├── model/              — immutable value objects (RateLimitPolicy, RateLimitDecision, RateLimitStrategy)
│   └── port/               — hexagonal ports: RateLimitRepository, ConfigRepository
├── infrastructure/
│   ├── config/             — Hoplite YAML configuration (immutable data classes)
│   ├── memory/             — in-memory port implementations for unit tests
│   └── redis/              — Redis-backed implementations via Lettuce coroutines + Lua scripts
├── routes/
│   ├── dto/                — serializable request/response data classes
│   ├── CheckRoute.kt       — POST /v1/check
│   ├── HealthRoute.kt      — GET /health
│   └── LimitsRoute.kt      — POST/GET/DELETE /v1/limits
└── service/
    └── RateLimiterService.kt — orchestration: policy lookup → default fallback → check → WARN log
```

### Three Algorithms

| Algorithm | Model | Memory | Reset |
|-----------|-------|--------|-------|
| **Fixed Window** | Epoch-aligned counter (`INCR` + `EXPIREAT`). Window boundaries are fixed multiples of `windowSeconds` since Unix epoch — never relative to service start. | O(1) — one counter key per client key per window period. | Next epoch-aligned boundary; up to 2× burst possible at boundary edges. |
| **Sliding Window** | Per-request timestamp in a Redis sorted set (`ZADD`). Entries older than `now − windowSeconds` are evicted before counting. | O(n) — one ZSET entry per request inside the window; grows with traffic volume. | Oldest surviving entry's timestamp + `windowSeconds` — smoothly shifts as old entries age out. |
| **Token Bucket** | Fractional token counter refilled continuously at `limit / windowSeconds` tokens/second, capped at `limit`. One token consumed per request. | O(1) — one hash key per client key, regardless of traffic. | Next-token-available time — derived from current token count and refill rate. |

## Quick Start

**Prerequisites**: Java 21, Docker Desktop (for Redis).

### 1. Start Redis

```bash
docker run -d --name redis-dev -p 6379:6379 redis:7-alpine
```

### 2. Run the service

```bash
./gradlew run
```

The service starts on `http://localhost:8080`.

### 3. Open Swagger UI

Navigate to **[http://localhost:8080/swagger-ui](http://localhost:8080/swagger-ui)**.  
All five endpoints are listed with realistic examples. Use **Try it out** to execute live requests.

### 4. Send your first check

```bash
curl -s -X POST http://localhost:8080/v1/check \
  -H "Content-Type: application/json" \
  -d '{"key": "my-client"}' | jq .
```

```json
{
  "allowed": true,
  "remaining": 99,
  "resetAt": "2026-05-12T10:01:00Z"
}
```

Stop Redis when done: `docker stop redis-dev && docker rm redis-dev`

## Usage

### Check rate limit — `POST /v1/check`

Returns an allow/reject decision for a client key. Always HTTP 200 — inspect `allowed`.

```bash
curl -s -X POST http://localhost:8080/v1/check \
  -H "Content-Type: application/json" \
  -d '{"key":"smoke-test"}' | jq .
```

```json
{"allowed": true, "remaining": 99, "resetAt": "2026-05-12T10:01:00Z"}
```

### Create or replace a policy — `POST /v1/limits`

Stores a rate limit policy for a key. Replaces any existing policy. Returns 201.

Valid strategies: `FIXED_WINDOW`, `SLIDING_WINDOW`, `TOKEN_BUCKET`.

```bash
curl -s -X POST http://localhost:8080/v1/limits \
  -H "Content-Type: application/json" \
  -d '{"key":"smoke-test","limit":3,"windowSeconds":60,"strategy":"FIXED_WINDOW"}' | jq .
```

### Get a policy — `GET /v1/limits/{key}`

Returns the stored policy. Returns 404 if no explicit policy has been configured for this key.

```bash
curl -s http://localhost:8080/v1/limits/smoke-test | jq .
```

```json
{"key": "smoke-test", "limit": 3, "windowSeconds": 60, "strategy": "FIXED_WINDOW"}
```

### Delete a policy — `DELETE /v1/limits/{key}`

Deletes the policy for a key. Returns 204 on success, 404 if no policy exists.

```bash
curl -s -o /dev/null -w "%{http_code}" -X DELETE http://localhost:8080/v1/limits/smoke-test
```

Expected: `204`

### Health check — `GET /health`

Returns service and Redis status. Redis health is determined by a PING with a 1-second timeout.

```bash
curl -s http://localhost:8080/health | jq .
```

```json
{"status": "UP", "redis": "UP"}
```

Returns HTTP 503 with `"status": "DOWN"` when Redis is unreachable.

## Technology Stack

| Concern | Technology |
|---|---|
| Language | Kotlin 2.1.20, JVM 21 |
| HTTP Framework | Ktor 3.1.3 (Netty engine) |
| Redis Client | Lettuce 6.5.5 (coroutine adapter) |
| API Documentation | ktor-openapi + ktor-swagger-ui 5.7.0 |
| Configuration | Hoplite 2.9.0 (YAML → typed data classes) |
| Logging | Logback 1.5.18 + logstash-logback-encoder 8.0 (structured JSON) |
| Build | Gradle 8.12.1 (Kotlin DSL) |
| Testing | JUnit 5, AssertJ, MockK 1.14.2, Testcontainers 1.21.0 |

## Running Locally

**Prerequisites**: Java 21, Docker Desktop.

```bash
git clone https://github.com/zahaand/rate-limiter-service
cd rate-limiter-service

# Start Redis
docker run -d --name redis-dev -p 6379:6379 redis:7-alpine

# Run the service
./gradlew run
```

Service: `http://localhost:8080` — Swagger UI: `http://localhost:8080/swagger-ui`

### Running Tests

Unit tests (no Docker required):

```bash
./gradlew test --tests "dev.zahaand.ratelimiter.domain.*"
./gradlew test --tests "dev.zahaand.ratelimiter.infrastructure.memory.*"
```

Full suite including integration tests (Docker Desktop required):

```bash
./gradlew test
```

Integration tests (`CheckRouteIT`, `LimitsRouteIT`, `HealthRouteIT`, `ConcurrentCheckIT`) start a real Redis container via Testcontainers. `ConcurrentCheckIT` fires 50 simultaneous requests for the same key with `limit=10` and asserts exactly 10 are allowed — this is the production atomicity guarantee.

## Configuration

All configuration lives in `src/main/resources/application.yaml`. Override any value before starting the service.

| Field | Default | Description |
|-------|---------|-------------|
| `server.port` | `8080` | HTTP port the service binds to. |
| `redis.host` | `localhost` | Redis server hostname. |
| `redis.port` | `6379` | Redis server port. |
| `rateLimit.defaultLimit` | `100` | Request limit for keys with no explicit policy. |
| `rateLimit.defaultWindowSeconds` | `60` | Window duration (seconds) for the default policy. |
| `rateLimit.defaultStrategy` | `FIXED_WINDOW` | Algorithm for the default policy (`FIXED_WINDOW`, `SLIDING_WINDOW`, `TOKEN_BUCKET`). |

---

# Rate Limiter Service

![Kotlin](https://img.shields.io/badge/Kotlin-2.1.20-7F52FF?style=flat-square&logo=kotlin) ![Ktor](https://img.shields.io/badge/Ktor-3.1.3-7F52FF?style=flat-square&logo=kotlin) ![Gradle](https://img.shields.io/badge/Gradle-8.12.1-02303A?style=flat-square&logo=gradle) ![Version](https://img.shields.io/badge/version-0.1.0-blue?style=flat-square) ![Tests](https://img.shields.io/badge/tests-10%20passed-brightgreen?style=flat-square) ![SDD](https://img.shields.io/badge/SDD-Spec--Driven_Development-blueviolet?style=flat-square)

10 тестов | 3 спринта | Standalone HTTP-сервис ограничения запросов — атомарные решения на ключ, хранилище Redis

## Методология

Проект разрабатывается по методологии **SDD — Spec-Driven Development** с использованием **Spec Kit** и **[Claude Code](https://claude.ai/code)**.

Все архитектурные решения фиксируются в `CONSTITUTION.md` до начала реализации. Каждый спринт начинается со спецификации фичи (`spec.md`), затем идут планирование (`plan.md`), декомпозиция задач (`tasks.md`) и межартефактный анализ (`/speckit-analyze`) — и только после этого пишется код. Рабочие процессы разработки описаны в `CLAUDE.md`.

## Возможности

- **Гексагональная архитектура** — доменный слой ничего не знает о Redis или HTTP; инфраструктура адаптируется к доменным портам, а не наоборот
- Разрабатывается по методологии **SDD — Spec-Driven Development**, **Spec Kit** и **Claude Code**
- **Атомарные решения при конкурентной нагрузке** — каждый алгоритм реализован как атомарный Lua-скрипт в Redis; цикл чтения-изменения-записи выполняется без прерываний при 50 конкурентных клиентах (проверено в `ConcurrentCheckIT`)
- **Структурированное JSON-логирование** — все решения по ключам, попадающим под дефолтную политику, логируются на уровне WARN в структурированном формате (logstash-logback-encoder), готовом для Loki / ELK
- **Sealed-иерархия алгоритмов** — `RateLimitStrategy` объявлен `sealed class`; добавление нового алгоритма без обновления всех точек диспетчеризации приводит к ошибке компиляции, а не к дефекту в runtime
- **Coroutine-first I/O** — адаптер корутин Lettuce; блокирующий I/O на диспетчере Ktor запрещён `CONSTITUTION.md`
- **Политики, настраиваемые в runtime** — лимиты на ключ хранятся в Redis и изменяются через REST API без перезапуска сервиса
- 6 unit-тестов (чистый Kotlin, без I/O) | 4 интеграционных теста (Testcontainers, реальный Redis)

## Архитектура

```
dev.zahaand.ratelimiter
├── Application.kt          — точка входа, подключение плагинов, ручной DI через конструктор
├── domain/
│   ├── algorithm/          — чистая логика алгоритмов, без I/O (Fixed Window, Sliding Window, Token Bucket)
│   ├── model/              — неизменяемые объекты-значения (RateLimitPolicy, RateLimitDecision, RateLimitStrategy)
│   └── port/               — гексагональные порты: RateLimitRepository, ConfigRepository
├── infrastructure/
│   ├── config/             — Hoplite YAML-конфигурация (неизменяемые data class)
│   ├── memory/             — in-memory реализации портов для unit-тестов
│   └── redis/              — Redis-реализации через корутины Lettuce + Lua-скрипты
├── routes/
│   ├── dto/                — сериализуемые классы запросов и ответов
│   ├── CheckRoute.kt       — POST /v1/check
│   ├── HealthRoute.kt      — GET /health
│   └── LimitsRoute.kt      — POST/GET/DELETE /v1/limits
└── service/
    └── RateLimiterService.kt — оркестрация: поиск политики → дефолтный фоллбэк → проверка → WARN-лог
```

### Три алгоритма

| Алгоритм | Модель | Память | Сброс |
|----------|--------|--------|-------|
| **Fixed Window** | Счётчик, выровненный по эпохе (`INCR` + `EXPIREAT`). Границы окон — кратные `windowSeconds` с Unix epoch, не с момента старта сервиса. | O(1) — один ключ-счётчик на клиентский ключ за период окна. | Следующая граница окна; возможен двойной burst на стыке окон. |
| **Sliding Window** | Временна́я метка каждого запроса в Redis sorted set (`ZADD`). Записи старше `now − windowSeconds` удаляются перед подсчётом. | O(n) — одна ZSET-запись на каждый запрос внутри окна; растёт с объёмом трафика. | Метка старейшей оставшейся записи + `windowSeconds` — плавный сдвиг по мере устаревания. |
| **Token Bucket** | Дробный счётчик токенов, пополняемый непрерывно со скоростью `limit / windowSeconds` токенов/сек, ограничен значением `limit`. Один токен — один запрос. | O(1) — один hash-ключ на клиент, независимо от трафика. | Время появления следующего токена — вычисляется из текущего количества и скорости пополнения. |

## Быстрый старт

**Требования**: Java 21, Docker Desktop (для Redis).

### 1. Запустить Redis

```bash
docker run -d --name redis-dev -p 6379:6379 redis:7-alpine
```

### 2. Запустить сервис

```bash
./gradlew run
```

Сервис запускается на `http://localhost:8080`.

### 3. Открыть Swagger UI

Перейти по адресу **[http://localhost:8080/swagger-ui](http://localhost:8080/swagger-ui)**.  
Все пять эндпоинтов представлены с реалистичными примерами. Кнопка **Try it out** позволяет выполнять живые запросы.

### 4. Отправить первый запрос

```bash
curl -s -X POST http://localhost:8080/v1/check \
  -H "Content-Type: application/json" \
  -d '{"key": "my-client"}' | jq .
```

```json
{
  "allowed": true,
  "remaining": 99,
  "resetAt": "2026-05-12T10:01:00Z"
}
```

Остановить Redis по завершении: `docker stop redis-dev && docker rm redis-dev`

## Использование

### Проверить лимит — `POST /v1/check`

Возвращает решение allow/reject для клиентского ключа. Всегда HTTP 200 — смотреть поле `allowed`.

```bash
curl -s -X POST http://localhost:8080/v1/check \
  -H "Content-Type: application/json" \
  -d '{"key":"smoke-test"}' | jq .
```

```json
{"allowed": true, "remaining": 99, "resetAt": "2026-05-12T10:01:00Z"}
```

### Создать или заменить политику — `POST /v1/limits`

Сохраняет политику лимита для ключа. Заменяет любую существующую. Возвращает 201.

Допустимые стратегии: `FIXED_WINDOW`, `SLIDING_WINDOW`, `TOKEN_BUCKET`.

```bash
curl -s -X POST http://localhost:8080/v1/limits \
  -H "Content-Type: application/json" \
  -d '{"key":"smoke-test","limit":3,"windowSeconds":60,"strategy":"FIXED_WINDOW"}' | jq .
```

### Получить политику — `GET /v1/limits/{key}`

Возвращает сохранённую политику. Возвращает 404, если явная политика для этого ключа не настроена.

```bash
curl -s http://localhost:8080/v1/limits/smoke-test | jq .
```

```json
{"key": "smoke-test", "limit": 3, "windowSeconds": 60, "strategy": "FIXED_WINDOW"}
```

### Удалить политику — `DELETE /v1/limits/{key}`

Удаляет политику для ключа. Возвращает 204 при успехе, 404 если политики нет.

```bash
curl -s -o /dev/null -w "%{http_code}" -X DELETE http://localhost:8080/v1/limits/smoke-test
```

Ожидаемый результат: `204`

### Проверка состояния — `GET /health`

Возвращает статус сервиса и Redis. Здоровье Redis определяется PING с таймаутом 1 секунда.

```bash
curl -s http://localhost:8080/health | jq .
```

```json
{"status": "UP", "redis": "UP"}
```

При недоступности Redis возвращает HTTP 503 с `"status": "DOWN"`.

## Технологический стек

| Область | Технология |
|---|---|
| Язык | Kotlin 2.1.20, JVM 21 |
| HTTP-фреймворк | Ktor 3.1.3 (движок Netty) |
| Redis-клиент | Lettuce 6.5.5 (адаптер корутин) |
| API-документация | ktor-openapi + ktor-swagger-ui 5.7.0 |
| Конфигурация | Hoplite 2.9.0 (YAML → типизированные data class) |
| Логирование | Logback 1.5.18 + logstash-logback-encoder 8.0 (структурированный JSON) |
| Сборка | Gradle 8.12.1 (Kotlin DSL) |
| Тестирование | JUnit 5, AssertJ, MockK 1.14.2, Testcontainers 1.21.0 |

## Локальный запуск

**Требования**: Java 21, Docker Desktop.

```bash
git clone https://github.com/zahaand/rate-limiter-service
cd rate-limiter-service

# Запустить Redis
docker run -d --name redis-dev -p 6379:6379 redis:7-alpine

# Запустить сервис
./gradlew run
```

Сервис: `http://localhost:8080` — Swagger UI: `http://localhost:8080/swagger-ui`

### Запуск тестов

Unit-тесты (Docker не требуется):

```bash
./gradlew test --tests "dev.zahaand.ratelimiter.domain.*"
./gradlew test --tests "dev.zahaand.ratelimiter.infrastructure.memory.*"
```

Полный набор включая интеграционные тесты (требуется Docker Desktop):

```bash
./gradlew test
```

Интеграционные тесты (`CheckRouteIT`, `LimitsRouteIT`, `HealthRouteIT`, `ConcurrentCheckIT`) запускают реальный Redis-контейнер через Testcontainers. `ConcurrentCheckIT` отправляет 50 одновременных запросов для одного ключа с `limit=10` и проверяет, что ровно 10 из них разрешены — это и есть гарантия атомарности в production.

## Конфигурация

Вся конфигурация находится в `src/main/resources/application.yaml`. Измените нужные значения перед запуском сервиса.

| Поле | По умолчанию | Описание |
|------|-------------|----------|
| `server.port` | `8080` | HTTP-порт, на котором слушает сервис. |
| `redis.host` | `localhost` | Хостнейм Redis-сервера. |
| `redis.port` | `6379` | Порт Redis-сервера. |
| `rateLimit.defaultLimit` | `100` | Лимит запросов для ключей без явной политики. |
| `rateLimit.defaultWindowSeconds` | `60` | Длина окна (в секундах) для дефолтной политики. |
| `rateLimit.defaultStrategy` | `FIXED_WINDOW` | Алгоритм для дефолтной политики (`FIXED_WINDOW`, `SLIDING_WINDOW`, `TOKEN_BUCKET`). |
