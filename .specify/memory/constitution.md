<!--
Sync Impact Report
==================
Version change: 1.0.0 → 1.1.0
Bump type: MINOR — material change to a technology mandate in Technology Stack section.

Modified sections:
  - Technology Stack & Architecture Constraints:
      config mandate replaced — Ktor HOCON → Hoplite + YAML

Added sections: N/A
Removed sections: N/A

Rationale for change: Hoplite binds configuration directly to Kotlin data classes with val
fields, which aligns with Principle II (Immutability & Safety) better than HOCON string-map
lookups. Type safety is enforced at startup rather than at call-site.

Templates requiring updates:
  ✅ .specify/templates/plan-template.md  — no config-specific refs.
  ✅ .specify/templates/spec-template.md  — no config-specific refs.
  ✅ .specify/templates/tasks-template.md — no config-specific refs.

Follow-up TODOs: None.
-->

# rate-limiter-service Constitution

## Core Principles

### I. Coroutine-First Concurrency

All async I/O MUST use Kotlin coroutines (`suspend` functions, `await()` on Lettuce futures).
`CompletableFuture` and blocking calls on coroutine-dispatcher threads are PROHIBITED.
Every repository and service function that performs I/O MUST be declared `suspend`.

**Rationale**: Ktor's engine and request pipeline are coroutine-based. Blocking I/O on the
same dispatcher silently starves request handling and produces latency spikes under load that
are difficult to diagnose.

### II. Immutability & Safety

- `val` MUST be preferred over `var`; use of `var` requires explicit justification in the PR.
- The `!!` operator is PROHIBITED. Use `?: error(...)`, `requireNotNull(...)`, or safe-call
  chains (`?.let`, `?.also`) instead.
- All domain models MUST be Kotlin `data class`. Lombok and manual `equals`/`hashCode` are
  PROHIBITED.
- Dependency wiring MUST use constructor injection only. Field injection and setter injection
  are PROHIBITED.

**Rationale**: Immutability eliminates a class of concurrency bugs in coroutine-shared state.
Banning `!!` forces explicit, reviewable null handling at compile time rather than
`NullPointerException` in production.

### III. Algorithm Strategy via Sealed Classes

The rate-limiting algorithm hierarchy MUST be modeled as a Kotlin `sealed class`.
Each algorithm variant (Fixed Window, Sliding Window, Token Bucket) MUST be a direct subclass.
Algorithm dispatch MUST use exhaustive `when` expressions on the sealed type.
Algorithm-specific conditionals (`if`/`else` chains, stringly-typed dispatch) outside the
single strategy dispatch point are PROHIBITED.

**Rationale**: Sealed classes make the algorithm set closed and compiler-verified. Adding a
new algorithm without updating every dispatch site produces a compile error, not a silent
runtime defect.

### IV. Test-First with Real Infrastructure

Tests MUST be written before implementation code (Red → Green → Refactor strictly enforced).
Unit tests MUST use JUnit 5 + MockK + AssertJ.
Integration tests MUST use Testcontainers for Redis; mocking Redis in integration tests is
PROHIBITED. HTTP-layer integration tests MUST use Ktor's `testApplication`.
A fully-green test suite is a required gate before any PR is merged.

**Rationale**: Mocking Redis in integration tests has historically masked serialization and
pipeline-ordering bugs only visible against a real server. Testcontainers provides full
fidelity at acceptable CI cost.

### V. Pure Ktor Stack

Spring, Hibernate, and all external DI frameworks are PROHIBITED.
The service MUST be implemented with pure Ktor 3.x + Kotlin coroutines.
The Redis client MUST be Lettuce with its coroutine adapter.
All wiring MUST be explicit in `Application.kt` (manual DI via constructor injection).

**Rationale**: Introducing Spring would conflict with Ktor's coroutine dispatcher model, add
significant startup overhead, and invalidate the lightweight design goal of the service.

### VI. Runtime-Configurable Limits

Rate limit configurations (limit values, window size, algorithm selection per client key)
MUST be modifiable via the REST API at runtime without a service restart.
Configuration changes MUST take effect for requests immediately following the API call.

**Rationale**: Operators must respond to traffic spikes or abuse patterns without coordinating
a deployment cycle. Restart-based reconfiguration is unacceptable in production environments.

## Technology Stack & Architecture Constraints

- **Language**: Kotlin 2.1.20, JVM target JDK 21
- **Framework**: Ktor 3.1.3
- **Build**: Gradle with Kotlin DSL (`build.gradle.kts`)
- **Storage**: Redis via Lettuce coroutine client
- **Architecture**: Single-module, three-layer — `routes → service → repository`
  - **Routes**: HTTP concerns only — deserialization, status codes, error mapping.
  - **Services**: Business logic and algorithm orchestration. No HTTP or Redis knowledge.
  - **Repositories**: All Redis commands. No business logic.
- Configuration MUST use Hoplite with YAML (`application.yaml`). Config classes MUST be
  Kotlin `data class` with `val` fields only — mutable config objects are PROHIBITED.
  Hoplite binds YAML directly to typed data classes at startup, ensuring type safety and
  alignment with Principle II (Immutability & Safety).

## Development Workflow & Git Conventions

- **Commit format**: Conventional Commits — `feat`, `fix`, `chore`, `test`, `refactor`
- **Commit subject line**: English
- **Commit body**: Russian
- **Branch policy**: No WIP commits to `main`. All changes land via PR after review.
- **Code review gate**: Every PR MUST verify compliance with all six Core Principles.
- Deliberate departures from any principle MUST be justified in the PR description with a
  documented rationale and tracked in the Complexity Tracking section of the plan.

## Governance

This constitution supersedes all other coding conventions or practices for this repository.
Amendments MUST be proposed as a PR updating this file and MUST state:
1. The principle or section being changed.
2. The rationale — what problem does the current wording cause?
3. A migration plan for existing code if the change is backward-incompatible.

Versioning follows semantic rules:
- **MAJOR**: Backward-incompatible removal or redefinition of a principle.
- **MINOR**: New principle or section added, or material expansion of existing guidance.
- **PATCH**: Clarifications, wording fixes, non-semantic refinements.

All PRs and code reviews MUST verify compliance with the Core Principles above.
The `CLAUDE.md` runtime guidance file MUST remain consistent with this constitution;
discrepancies are resolved in favor of this document.

**Version**: 1.0.0 | **Ratified**: 2026-05-05 | **Last Amended**: 2026-05-05
