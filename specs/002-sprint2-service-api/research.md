# Research: Sprint 2 — Service Layer & REST API

**Branch**: `002-sprint2-service-api` | **Date**: 2026-05-05

---

## 1. `ConfigRepository.delete` return type

**Decision**: Change `suspend fun delete(key: String)` → `suspend fun delete(key: String): Boolean`

**Rationale**: `DELETE /v1/limits/{key}` must return HTTP 404 when the key does not exist.
Returning `Boolean` from `delete` avoids a `get()+delete()` round trip (two Redis calls) and
lets the route handler check existence in a single atomic operation.

**Alternatives considered**:
- `get(key)` first, then `delete(key)` if non-null — two round trips; non-atomic in theory
  (another caller could delete between the two calls), though harmless in practice here.
- Throw `NoSuchElementException` from the repository — leaks domain semantics into exception
  control flow, contra Constitution Principle II.

**Affected files**:
- `domain/port/ConfigRepository.kt` — signature change only
- `infrastructure/memory/InMemoryConfigRepository.kt` — `remove()` returns the removed value
- `infrastructure/redis/RedisConfigRepository.kt` — `DEL` returns number of deleted keys > 0
- `InMemoryConfigRepositoryTest` — update delete test assertions

---

## 2. `RateLimitKey` value class

**Decision**: `@JvmInline value class RateLimitKey(val value: String)` in `domain/model/`.

**Rationale**: The service method signature in the feature spec is
`suspend fun check(key: RateLimitKey): RateLimitDecision`. A Kotlin inline value class
carries zero runtime overhead (erased to `String` on the JVM) but provides type safety —
prevents passing arbitrary strings to the service where a validated key is expected.
Blank validation (`isNotBlank`) is added to the constructor as a defense-in-depth guard;
the route layer still validates explicitly and returns HTTP 400 before constructing the type.

**Alternatives considered**:
- Plain `typealias RateLimitKey = String` — no type safety, no validation guard.
- Data class `data class RateLimitKey(val value: String)` — heap allocation on every request;
  unnecessary overhead for a hot path.

---

## 3. `Instant` serialization with `kotlinx.serialization`

**Decision**: Custom `object InstantSerializer : KSerializer<Instant>` that encodes to/decodes
from ISO-8601 string via `Instant.toString()` / `Instant.parse(...)`. Applied as
`@Serializable(with = InstantSerializer::class)` on the `resetAt` field in `CheckResponse`.

**Rationale**: `java.time.Instant` has no built-in `kotlinx.serialization` support. The project
already uses kotlinx-serialization-json (build.gradle.kts). Adding `kotlinx-datetime` would
bring in an extra transitive dependency for a single field; a 10-line custom serializer is
simpler and has zero additional dependencies.

**Alternatives considered**:
- `kotlinx-datetime` `Instant` — requires adding a dependency and replacing all `java.time.Instant`
  usages in domain model with `kotlinx.datetime.Instant`. Too invasive for one field.
- Serialize `resetAt` as a `Long` (epoch seconds) — violates the spec's requirement for
  ISO-8601 string format.

---

## 4. Health check coroutine timeout

**Decision**: `withTimeout(1_000L) { commands.ping() }` wrapped in a try/catch that catches
`TimeoutCancellationException` and `Exception` in separate branches.

**Rationale**: `withTimeout` throws `TimeoutCancellationException` (a subclass of
`CancellationException`) on deadline exceeded. Catching `Exception` alone would swallow
genuine coroutine cancellations from the parent scope — a subtle bug under load where Ktor
cancels request coroutines. By catching `TimeoutCancellationException` explicitly first, the
handler only silences the expected timeout and lets true cancellations propagate.

```kotlin
val redisStatus = try {
    withTimeout(1_000L) { commands.ping() }
    "UP"
} catch (_: TimeoutCancellationException) {
    "DOWN"
} catch (_: Exception) {
    "DOWN"
}
```

**Alternatives considered**:
- `runCatching { withTimeout(1_000L) { ... } }` — catches `Throwable`, including
  `CancellationException`; unsafe in structured concurrency.
- No timeout — health endpoint could hang for connection-timeout duration (default ≥ 60 s).

---

## 5. Application module testability (`overrideConfig` parameter)

**Decision**: `fun Application.module(overrideConfig: AppConfig? = null)`. When `null`, loads
from `application.yaml` via Hoplite. Integration tests pass a pre-built `AppConfig` pointing
at the Testcontainers Redis port.

**Rationale**: Ktor's `testApplication` builder needs to call `application { module() }`. If
the module hard-codes Hoplite loading from the classpath, it picks up the production
`application.yaml` (pointing at `localhost:6379`) rather than the container port. The optional
parameter avoids duplicating the production loading path and keeps test wiring explicit.

**Alternatives considered**:
- Ktor `MapApplicationConfig` override — requires switching all config reads from Hoplite to
  Ktor's `ApplicationConfig`, which conflicts with the Constitution's Hoplite mandate.
- Separate test `application.yaml` on the test classpath — fragile; container port is dynamic
  and unknown at compile time.

---

## 6. Testcontainers singleton pattern

**Decision**: Kotlin `object RedisTestContainer` with a `GenericContainer` started eagerly
(`.also { it.start() }` at object initialization time). All integration test classes reference
`RedisTestContainer.host` / `RedisTestContainer.port`.

**Rationale**: The spec requires the singleton pattern to reduce startup overhead. Kotlin
`object` initialization is thread-safe and lazy-on-first-access by JVM semantics, but the
container is started immediately (`.also { it.start() }`) so tests never hit a cold-start
race. No `@Container` annotations needed; lifecycle is tied to the JVM process.

**Alternatives considered**:
- `@Container` on a `companion object @JvmStatic` field with `@Testcontainers` on each test
  class — starts one container per class unless `@Container` is static, which Testcontainers
  supports but only within one class hierarchy.
- `beforeAll` / `afterAll` on a base class — more boilerplate; start/stop per test class.

---

## 7. `FLUSHDB` between tests

**Decision**: Each integration test class gets a `@BeforeEach` that calls `FLUSHDB` via
a dedicated `LettuceRedisClient` connected to the singleton container.

**Rationale**: The spec mandates clean Redis state between individual tests. `FLUSHDB` (not
`FLUSHALL`) clears only the currently selected database (default DB 0), which is all tests
use. Each test class opens its own connection in the `@BeforeEach` setup; the connection is
closed in `@AfterEach`.

---

## 8. Lettuce connection lifecycle

**Decision**: `Application.module()` creates `RedisClient` → `connection = client.connect()` →
`commands = connection.coroutines()`. Both are closed in `environment.monitor.subscribe(ApplicationStopped)`.

Shutdown order: close `connection` first (drains in-flight commands), then `client.shutdown()`.

**Rationale**: `RedisClient.shutdown()` without closing the connection first triggers a
warning log from Lettuce. Closing the connection first ensures in-flight coroutines complete
before the underlying I/O threads are terminated.
