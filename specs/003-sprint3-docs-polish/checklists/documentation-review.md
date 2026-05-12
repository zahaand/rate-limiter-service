# Documentation Review Checklist: Sprint 3 — Swagger UI, KDoc, README

**Purpose**: PR review gate — validates that documentation requirements in spec.md, plan.md,
and contracts/ are complete, unambiguous, and sufficient to implement and verify.
**Created**: 2026-05-12
**Feature**: [spec.md](../spec.md) · [plan.md](../plan.md) · [contracts/swagger-annotations.md](../contracts/swagger-annotations.md)
**Scope**: Swagger UI (FR-017–FR-019), KDoc (FR-020–FR-021), README (FR-022),
ConcurrentCheckIT (FR-023). Sprint 2 editorial fixes (FR-024–FR-025) excluded.
**Depth**: PR review gate (thorough)
**Weight**: Swagger-heavy (primary portfolio deliverable, "non-negotiable" quality rules)

---

## Swagger UI — Endpoint Documentation Coverage (FR-017, FR-018)

- [ ] CHK001 — Are all five endpoint summaries required to be imperative phrases — and is "imperative phrase" defined precisely enough for a reviewer to distinguish it from a noun phrase or URL without ambiguity? [Clarity, Spec §FR-018]
- [ ] CHK002 — Are the minimum required response codes for `POST /v1/check` (200, 400, 415, 500) exhaustively enumerated in the requirements, with no status code left to implementer discretion? [Completeness, Spec §FR-018]
- [ ] CHK003 — Are the minimum required response codes for `POST /v1/limits` (201, 400, 415, 500) exhaustively enumerated? [Completeness, Spec §FR-018]
- [ ] CHK004 — Are the minimum required response codes for `GET /v1/limits/{key}` (200, 404, 500) exhaustively enumerated? [Completeness, Spec §FR-018]
- [ ] CHK005 — Are the minimum required response codes for `DELETE /v1/limits/{key}` (204, 404, 500) exhaustively enumerated? [Completeness, Spec §FR-018]
- [ ] CHK006 — Are the minimum required response codes for `GET /health` (200, 503) exhaustively enumerated — specifically, is 503 (not 500) required for the Redis-DOWN case? [Completeness, Spec §FR-011, FR-018]
- [ ] CHK007 — Does the `POST /v1/check` documentation requirement explicitly state that HTTP 200 is used for BOTH allowed and rejected decisions, and that HTTP 429 is out of scope? [Completeness, Spec §FR-002, FR-018]
- [ ] CHK008 — Is the requirement that `DELETE /v1/limits/{key}` 204 response has no body (schema = none/empty) explicitly captured in the contract? [Completeness, contracts/swagger-annotations.md]
- [ ] CHK009 — Are the five distinct HTTP 400 error messages for `POST /v1/limits` required to be individually documented as separate named examples (not collapsed into a single generic "validation error")? [Completeness, Spec §FR-007, FR-018]
- [ ] CHK010 — Does the requirement for `GET /health` documentation specify that only two response combinations exist (`UP/UP` and `DOWN/DOWN`) and that mixed-state combinations are undefined? [Completeness, Spec §FR-011]

---

## Swagger UI — Example Quality (FR-019)

- [ ] CHK011 — Is "realistic domain value" defined with enough specificity that a reviewer can objectively distinguish compliant from non-compliant examples, without relying on personal judgment? [Clarity, Spec §FR-019]
- [ ] CHK012 — Are all prohibited placeholder values explicitly enumerated — including `"string"`, `"example"`, unnamed/auto-generated example names, and `0` for non-zero-typical fields? [Completeness, Spec §FR-019]
- [ ] CHK013 — Is the minimum number of named examples per endpoint (stated as "at least one" in FR-018) sufficient, or does the "non-negotiable" quality signal imply a higher floor for endpoints with multiple distinct response shapes (e.g., `POST /v1/check` has allowed + rejected)? [Clarity, Spec §FR-018, FR-019]
- [ ] CHK014 — Are example `resetAt` values required to be valid ISO-8601 UTC strings with seconds precision and Z suffix (e.g., `"2026-05-12T14:00:00Z"`), rather than any timestamp format? [Completeness, Spec §FR-001, FR-019]
- [ ] CHK015 — Are the domain key values used across all examples consistent — e.g., `"tenant-A"` in `POST /v1/limits` examples matches `"tenant-A"` in `GET /v1/limits/{key}` and `DELETE` examples, forming a coherent story? [Consistency, contracts/swagger-annotations.md, Spec §FR-019]

---

## Swagger UI — Integration & Access Requirements (FR-017)

- [ ] CHK016 — Is the requirement that `/swagger` must not conflict with `/v1/*` routes or `/health` stated as a verifiable constraint, and is the detection method defined (e.g., "all existing routes must return correct responses while Swagger plugin is installed")? [Completeness, Spec §Edge Cases]
- [ ] CHK017 — Is the raw OpenAPI endpoint (e.g., `/openapi.json`) intentionally undocumented as a requirement, or is it a gap — given that Key Entities references "Served at `/openapi.json` or equivalent path"? [Gap, Spec §Key Entities]
- [ ] CHK018 — Does the plan specify the behavior of the SwaggerUI plugin when `overrideConfig` is passed (i.e., during `testApplication` in integration tests) — specifically, does it need to be disabled, or does it function correctly? [Completeness, Gap, Plan]
- [ ] CHK019 — Is the ktor-swagger-ui version (5.7.0) and its Ktor 3.x compatibility constraint documented as a hard gate — meaning the build must fail if a version mismatch is introduced? [Completeness, Plan § research.md]

---

## KDoc — High-Priority Documentation Requirements (FR-020)

- [ ] CHK020 — Is "explaining the invariant" in FR-020 specific enough for a reviewer to verify — or does it require a per-class definition of what counts as an invariant vs. a general description? [Clarity, Spec §FR-020]
- [ ] CHK021 — For `FixedWindowAlgorithm`, is the epoch-aligned window formula (`epochSecond / windowSeconds * windowSeconds`) explicitly required to be documented — not just "window alignment" generically? [Completeness, data-model.md, Plan]
- [ ] CHK022 — For `SlidingWindowAlgorithm`, are both `resetAt` behaviors (at-limit → oldest-expiry, below-limit → now) required to be documented as distinct cases? [Completeness, data-model.md, Plan]
- [ ] CHK023 — For `TokenBucketAlgorithm`, is the zero-rate guard (`windowSeconds=0 → refillRate=0.0`) required to be documented — and is this edge case covered by the spec's edge cases section? [Completeness, Spec §FR-020, Plan]
- [ ] CHK024 — For `RateLimitRepository`, is the atomicity guarantee required to reference the specific mechanism (Redis Lua EVAL), or is "atomically checks and increments" sufficient? [Clarity, Spec §FR-020, Plan]
- [ ] CHK025 — For `RateLimiterService`, are the five WARN log fields (`key`, `strategy`, `limit`, `windowSeconds`, `timestamp`) required to be explicitly listed in KDoc — consistent with FR-005? [Completeness, Spec §FR-020, FR-005]

---

## KDoc — Medium-Priority, Scope, and Consistency Requirements (FR-021)

- [ ] CHK026 — Is the scope of FR-020 vs FR-021 unambiguous for the nested `State` data classes (`FixedWindowAlgorithm.State`, `TokenBucketAlgorithm.State`) — are they in scope for either FR? [Ambiguity, data-model.md, Spec §FR-020]
- [ ] CHK027 — For `ConfigRepository`, is the Boolean return contract of `delete()` (`true` = deleted, `false` = key not found) required to be documented — and is this consistent with FR-009's atomicity guarantee? [Completeness, Spec §FR-021, FR-009]
- [ ] CHK028 — For `RedisRateLimitRepository`, are the three Redis key prefix patterns (`rl:fw:`, `rl:sw:`, `rl:tb:`) required to be documented, or is the key scheme an unspecified implementation detail? [Completeness, Plan, data-model.md]
- [ ] CHK029 — Is the "out of scope" KDoc list (DTOs, route functions, AppConfig hierarchy) specific enough that a reviewer can unambiguously classify `RateLimitKey`, `InstantSerializer`, and `InMemoryConfigRepository`? [Clarity, Spec §F-16 Out of scope]
- [ ] CHK030 — Do the class names in FR-020/FR-021 match the actual source file names confirmed in the plan — specifically, does `RateLimitRepository` (not "RateLimiterRepository") and `ConfigRepository` (not "LimitConfigRepository") appear in the implementation requirements? [Consistency, Spec §FR-020, FR-021, Plan § research.md Decision 3]

---

## README — Completeness & Usability Requirements (FR-022)

- [ ] CHK031 — Are the required columns for the algorithm trade-off table defined (e.g., Algorithm, Model, Memory, Reset behaviour) — or is the table structure left to the implementer? [Completeness, Plan, Spec §FR-022]
- [ ] CHK032 — Is the required depth of the package structure section defined (top-level packages only, or nested subpackages such as `routes/dto`, `infrastructure/redis`) to avoid ambiguity in what counts as "complete"? [Clarity, Gap, Spec §FR-022]
- [ ] CHK033 — Are the Quick Start instructions required to be verified on both macOS and Linux — and is Windows explicitly excluded — with sufficient clarity that a reviewer knows which platform to test against? [Completeness, Spec §Assumptions, FR-022]
- [ ] CHK034 — Is the SC-012 "within 5 minutes" gate measurable during review — is there a defined method for a reviewer to confirm this (e.g., a stopwatch test, or a self-contained smoke test script)? [Measurability, Spec §SC-012]
- [ ] CHK035 — Are the API Reference `curl` examples required to use `localhost:8080` with no environment variables or pre-configuration — and is this constraint explicitly stated? [Completeness, Spec §Edge Cases, FR-022]
- [ ] CHK036 — Are the required fields for the Configuration section defined (field name, type, default value, meaning), or is the format and depth left unspecified? [Clarity, Spec §FR-022]

---

## ConcurrentCheckIT — Test Requirement Specification (FR-023)

- [ ] CHK037 — Is the strategy (`FIXED_WINDOW`) for `ConcurrentCheckIT` specified and its rationale stated (verifies Lua atomicity in `RedisRateLimitRepository`) — or could a reviewer accept an implementation using `SLIDING_WINDOW`? [Completeness, Spec §FR-023]
- [ ] CHK038 — Is the `FLUSHDB` isolation requirement for `ConcurrentCheckIT` specified in both the spec edge cases and the plan, ensuring a reviewer can verify it was applied? [Completeness, Spec §Edge Cases]
- [ ] CHK039 — Is the "100% of runs" requirement in SC-013 testable as stated — does it require a defined number of consecutive CI runs to constitute "100%", or is a single green run sufficient? [Measurability, Spec §SC-013]
- [ ] CHK040 — Is the concurrency mechanism (50 simultaneous coroutines via `async`/`awaitAll`) specified in the requirements, or is it intentionally left as an implementation detail for the developer to choose? [Clarity, Spec §FR-023, Plan]

---

## Notes

- Mark items complete with `[x]` during PR review
- Items marked `[Gap]` indicate a missing requirement — consider updating spec or plan before merging
- Items marked `[Ambiguity]` indicate a requirement that may be interpreted differently by implementer and reviewer — resolve before task breakdown
- CHK017, CHK018 are the highest-risk gaps identified: the `/openapi.json` endpoint and Swagger behaviour under `testApplication` are unspecified
