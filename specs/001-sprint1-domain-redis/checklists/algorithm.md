# Algorithm Requirements Quality Checklist: Sprint 1 — Domain Model & Rate Limiting Infrastructure

**Purpose**: Validate that Fixed Window, Sliding Window, and Token Bucket algorithm requirements
are precise, consistent, and complete enough for deterministic implementation and unit testing.
**Audience**: Author self-review before implementation begins
**Created**: 2026-05-05
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md)

---

## Fixed Window Requirements Quality

- [ ] CHK001 - Is the epoch anchor for window boundary alignment explicitly stated in the spec rather than only in research/plan? FR-004 gives an example ("every 60 s at :00") but does not name Unix epoch as the anchor. [Clarity, Spec §FR-004, Gap]

- [ ] CHK002 - Is the behavior at the exact boundary moment defined? When `now` coincides precisely with `windowStart + windowSeconds`, is the request evaluated in the expiring window or the new window? [Ambiguity, Spec §FR-004]

- [ ] CHK003 - Does the acceptance scenario for window reset (US2 Scenario 1) specify whether the request that crosses the boundary is itself allowed (i.e., counts as the first request of the new window) or triggers the reset and is then evaluated? [Clarity, Spec §US2 Scenario 1]

- [ ] CHK004 - Is `remaining = 0` when `count == limit` (the last-allowed request) explicitly stated, or only inferable from the Key Entities definition? US1 Scenario 1 asserts `remaining = 0` for the 10th request — is this sufficient to derive the general formula? [Completeness, Spec §US1 Scenario 1, §Key Entities]

---

## Sliding Window Requirements Quality

- [x] CHK005 - Is the cutoff boundary inclusive or exclusive? FR-005 says "within the last N seconds" — is a request made exactly N seconds ago counted toward the limit or pruned? [Ambiguity, Spec §FR-005] → **Resolved 2026-05-05**: inclusive (`t >= now - windowSeconds`); strict prune of `t < now - windowSeconds`.

- [x] CHK006 - Is `resetAt` semantics defined for Sliding Window? FR-001 requires `resetAt` in every decision, but FR-005 describes no hard boundary — the spec does not state what `resetAt` represents for this algorithm. [Gap, Spec §FR-001, §FR-005] → **Resolved 2026-05-05**: `now` when capacity remains; `oldest + windowSeconds` when window full; `now` when window empty.

- [ ] CHK007 - Is the moment at which a new timestamp is recorded (before or after the allow/reject decision) specified? If a request is rejected, is it still appended to the window's timestamp list? [Gap, Spec §FR-005]

- [ ] CHK008 - Does the edge case "requests with equal timestamps count as distinct entries" specify the storage mechanism that guarantees uniqueness (e.g., separate list entries vs. a de-duplicating set)? [Clarity, Spec §Edge Cases]

- [ ] CHK009 - Is behavior defined for a full-flush scenario where all stored timestamps expire simultaneously between two requests? Are requirements consistent with the generic "allow again after window expires" scenario in FR-010? [Coverage, Spec §FR-010, §FR-005]

---

## Token Bucket Requirements Quality

- [ ] CHK010 - Is `capacity` explicitly defined as equal to `policy.limit`? FR-006 uses the formula `capacity / windowSeconds` but never states `capacity = limit`. US2 Scenario 3 uses "capacity 10, refill 10 per 60 s" which implies equality but does not state it. [Ambiguity, Spec §FR-006, §US2 Scenario 3]

- [x] CHK011 - Does US2 Scenario 3 ("5 new tokens are available for consumption") specify whether the assertion is about state before or after the current request consumes a token? If 5 tokens refill and the request consumes 1, `remaining` is 4 — not 5. [Ambiguity, Spec §US2 Scenario 3, §Key Entities] → **Resolved 2026-05-05**: scenario updated; `remaining = 4` after deduction. FR-006 now states `(tokens - 1.0).toInt()` when allowed.

- [ ] CHK012 - Is `resetAt` semantics defined for Token Bucket? The spec requires `resetAt: Instant` (Clarification §Q2) but FR-006 does not define whether it means "time until next token" (when rejected) or "time until full capacity" (when allowed). [Gap, Spec §FR-006, Clarification §Q2]

- [ ] CHK013 - Is the `lastRefillAt` timestamp update policy specified? The plan updates it on every check (allowed and rejected). The spec is silent on whether the refill calculation anchor advances on rejected requests. [Gap, Spec §FR-006]

- [ ] CHK014 - Is "first use" defined precisely for the "bucket starts full" requirement? Does it mean the first request ever for a key, or the first request after service startup? What happens when a key has no persisted state in Redis after a restart? [Clarity, Spec §FR-006, §Edge Cases]

- [ ] CHK015 - Is the division-by-zero scenario for `limit = 0` (refill rate = 0.0) addressed in the Token Bucket requirements? The edge case covers the reject-all behavior but not the `resetAt` calculation when `refillRate == 0`. [Edge Case, Gap, Spec §Edge Cases]

---

## Cross-Algorithm Consistency

- [ ] CHK016 - Is `remaining` consistently defined for rejected requests across all three algorithms? Key Entities defines it as "how many more requests are allowed before the next rejection" — for a rejected request this should always be 0, but this invariant is not stated in FR-001 or FR-010. [Consistency, Spec §FR-001, §Key Entities]

- [ ] CHK017 - Does FR-010's "allow again after the window expires" scenario map to distinct, algorithm-specific definitions of "expires"? For Fixed Window it is a hard boundary; for Sliding Window it is individual timestamp expiry; for Token Bucket it is token refill. Are these distinctions captured in the test requirements? [Clarity, Spec §FR-010]

- [ ] CHK018 - Is the concurrency test expectation in FR-010 ("concurrent requests do not collectively exceed the limit") quantified as "at most N allowed" or "exactly N allowed"? SC-002 says "never exceeds" (at-most) — is this consistent with what the test should assert? [Consistency, Spec §FR-003, §FR-010, §SC-002]

- [ ] CHK019 - Are requirements defined for what happens to existing per-key rate-limit state when a policy's strategy is changed (e.g., Fixed Window → Token Bucket for the same key)? The config layer supports policy overwrite but the spec is silent on state migration. [Gap, Spec §FR-007]

---

## Edge Cases & Boundary Conditions

- [ ] CHK020 - Is the maximum allowed value of `windowSeconds` specified? Large values (e.g., `Int.MAX_VALUE` ≈ 68 years) would cause epoch arithmetic overflow (`epochSecond / windowSeconds`) or extreme state retention in Sliding Window. [Completeness, Gap]

- [ ] CHK021 - Is `limit = 0` explicitly covered in FR-010's mandatory test scenarios, or only in the generic Edge Cases section? Confirming each algorithm handles it is a separate test from the FR-010 enumeration. [Completeness, Spec §FR-010, §Edge Cases]

- [ ] CHK022 - Are requirements defined for a request arriving with `now` equal to the previous request's `now` (zero elapsed time) in Token Bucket? This edge case is not addressed and exercises the `elapsed = 0 → no refill` path directly. [Edge Case, Gap, Spec §FR-006]

---

## Notes

- Check items off as completed: `[x]`
- Items marked `[Gap]` represent missing requirements — resolve by updating spec.md or documenting as intentional assumption.
- Items marked `[Ambiguity]` require a clarifying statement in spec.md before implementation.
- Items marked `[Consistency]` flag potential conflicts between sections — confirm alignment.
