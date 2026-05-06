# API Requirements Checklist: Sprint 2 — Service Layer & REST API

**Purpose**: PR review gate — validates that requirements are complete, clear, consistent, and measurable before implementation is accepted
**Created**: 2026-05-05
**Feature**: [spec.md](../spec.md) · [plan.md](../plan.md)
**Depth**: Standard (PR review)
**Scope**: All five feature areas — F-07 service layer, F-08 check endpoint, F-09 limits CRUD, F-10 health, F-11 wiring + interface change risk

---

## Requirement Completeness

- [x] CHK001 - Are the exact field names, types, and constraints for all five endpoint request/response shapes fully specified — including the `remaining` field's lower bound? [Completeness, Spec §FR-001, FR-006, FR-008, FR-010]
- [x] CHK002 - Are all four `POST /v1/limits` validation error messages defined with exact wording, not just the condition? [Completeness, Spec §FR-007]
- [x] CHK003 - Is a `Content-Type: application/json` requirement specified for request bodies on `POST /v1/check` and `POST /v1/limits`? [Gap]
- [x] CHK004 - Are the five fields to be included in every REJECTED log entry fully enumerated and their formats defined? [Completeness, Spec §FR-005]
- [x] CHK005 - Is success criteria defined for REJECTED-decision logging — is there a success criterion that validates log output, or is logging only covered by FR-005 without a measurable outcome? [Completeness, Spec §SC-001–SC-007]

## Requirement Clarity

- [x] CHK006 - Is "blank key" defined precisely — does the spec explicitly enumerate empty string, whitespace-only string, and missing field as covered cases? [Clarity, Spec §FR-003, Edge Cases]
- [x] CHK007 - Is the `resetAt` ISO-8601 format defined at the precision level — seconds vs. milliseconds, `Z` suffix vs. numeric UTC offset? [Clarity, Spec §Key Entities]
- [x] CHK008 - Is "fire-and-forget" logging defined precisely — does a logging failure produce any observable side-effect (metric, counter, fallback log) or is total silent failure acceptable? [Clarity, Spec §Assumptions]
- [x] CHK009 - Is "in-flight requests" in FR-013 (graceful shutdown) defined with observable criteria — what constitutes an in-flight request at the moment of shutdown signal? [Clarity, Spec §FR-013]
- [x] CHK010 - Does FR-002 ("always HTTP 200") clearly scope itself to rate-limit decisions only, or could a reviewer interpret it as conflicting with FR-003's HTTP 400 for blank keys? [Clarity/Conflict, Spec §FR-002, FR-003]
- [x] CHK011 - Is the WARN log format for rejections specified as structured (JSON fields) or plain text — and are field names defined? [Clarity, Spec §FR-005]

## Requirement Consistency

- [x] CHK012 - Are the three strategy names (`FIXED_WINDOW`, `SLIDING_WINDOW`, `TOKEN_BUCKET`) used identically across FR-007, the Key Entities section, acceptance scenarios, and the contracts document? [Consistency, Spec §FR-007]
- [x] CHK013 - Is the `remaining` field type in the check response (`integer ≥ 0`) consistent with the Sprint 1 `RateLimitDecision.remaining: Int` domain model? [Consistency, Spec §Key Entities]
- [x] CHK014 - Is the upsert behavior of `POST /v1/limits` (Assumptions) reflected in at least one acceptance scenario — US2 scenarios only show the first-POST case, not the replace-existing case? [Consistency, Spec §Assumptions, US2]
- [x] CHK015 - Is the Error Response format (`{"error": "..."}`) consistently required for all four error status codes (400, 404, 500) across FR-003, FR-007, FR-008, FR-009, and FR-012? [Consistency, Spec §Key Entities]

## Acceptance Criteria Quality

- [x] CHK016 - Is SC-002 ("concurrent calls at the boundary") measurable — does the spec define what concurrency level or mechanism is required in the integration test? [Measurability, Spec §SC-002]
- [x] CHK017 - Is SC-007 ("starts and stops without resource leaks") objectively verifiable — is there a defined signal or assertion that confirms no leak occurred? [Measurability, Spec §SC-007]
- [x] CHK018 - Can SC-003 ("no fallback exception escapes") be observed at the HTTP boundary — is "escapes" defined as an HTTP 500 response, a thrown exception, or something else? [Measurability, Spec §SC-003]

## Scenario Coverage

- [x] CHK019 - Are requirements defined for `POST /v1/check` and `POST /v1/limits` receiving a malformed JSON body (parse error, not just a blank key)? [Coverage, Gap]
- [x] CHK020 - Are requirements defined for `GET /v1/limits/{key}` and `DELETE /v1/limits/{key}` where the path segment contains URL-reserved characters (e.g., `/`, `%`, `+`)? [Coverage, Gap]
- [x] CHK021 - Is the behavior defined for `POST /v1/check` on a key whose policy was just deleted — should it immediately fall back to the default, and is this an explicit requirement? [Coverage, Gap]
- [x] CHK022 - Is the behavior of `POST /v1/limits` with an unknown strategy value specified with a concrete error message, not just the condition "must be one of"? [Coverage, Spec §FR-007]
- [x] CHK023 - Are requirements defined for all three rate-limiting strategies in integration test scenarios for `POST /v1/check`, or only the default strategy? [Coverage, Spec §SC-001]

## Edge Case Coverage

- [x] CHK024 - Is behavior defined when the health endpoint's Redis PING succeeds within timeout but returns an unexpected response (not `PONG`)? [Edge Case, Gap]
- [x] CHK025 - Is behavior defined when `POST /v1/limits` is called with `limit = 0` — does the spec explicitly state this is accepted (not rejected), and does this appear in an acceptance scenario? [Edge Case, Spec §Edge Cases]
- [x] CHK026 - Is there a requirement covering what `POST /v1/check` returns when the `remaining` field would be negative due to concurrent requests racing past the limit? [Edge Case, Gap]

## Non-Functional Requirements

- [x] CHK027 - Are latency requirements defined for `POST /v1/check` — the spec defines only the health check timeout (1 s), but no end-to-end response time target for the rate-check hot path? [NFR, Gap]
- [x] CHK028 - Are observability requirements defined beyond WARN-level rejection logging — structured request/response logging, metrics, or distributed tracing signals? [NFR, Gap]
- [x] CHK029 - Is a maximum key length or key format constraint documented — or is the "opaque string" definition (from Sprint 1) explicitly carried forward as the only constraint? [NFR, Spec §Assumptions]

## Dependencies & Assumptions

- [x] CHK030 - Is the assumption that Sprint 1 domain contracts are "stable" qualified by the `ConfigRepository.delete` return-type change — does the spec acknowledge this is a deliberate revision of a Sprint 1 interface? [Assumption, Spec §Assumptions, Plan §Implementation Notes]
- [x] CHK031 - Is it documented that `InMemoryConfigRepository` and its existing Sprint 1 tests require updates as a consequence of the `delete → Boolean` change — or is this only captured in the plan? [Dependency, Gap]
- [x] CHK032 - Is the dependency on `RateLimitPolicy(limit ≥ 0)` constructor validation (Sprint 1) documented as a shared constraint with FR-007's `limit must be ≥ 0` validation in the route layer? [Assumption, Spec §FR-007]

## Interface Change Risk (ConfigRepository.delete → Boolean)

- [x] CHK033 - Is the contract between `DELETE /v1/limits/{key}` route and `ConfigRepository.delete` explicit — specifically, does `false` always mean "key not found" with no other failure mode? [Interface Change Risk, Spec §FR-009]
- [x] CHK034 - Are requirements defined for the atomicity of concurrent DELETE requests for the same key — should the second DELETE return 204 (already gone) or 404 (not found)? [Interface Change Risk, Spec §FR-009, Gap]
- [x] CHK035 - Is the backward-compatibility impact of `delete → Boolean` on existing `InMemoryConfigRepositoryTest` assertions captured in either the spec or a referenced plan document, not left implicit? [Interface Change Risk, Plan §Implementation Notes]

## Ambiguities & Conflicts

- [x] CHK036 - Is "key" used as a single canonical term throughout — does the spec avoid conflating the client-facing key identifier with any internal Redis key naming scheme? [Ambiguity, Gap]
- [x] CHK037 - Is it unambiguous that `GET /health` is intentionally unversioned (`/health`, not `/v1/health`) and that this is a deliberate design choice documented in the spec? [Ambiguity, Spec §Clarifications]
