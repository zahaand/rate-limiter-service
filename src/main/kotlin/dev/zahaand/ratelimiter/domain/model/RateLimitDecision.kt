package dev.zahaand.ratelimiter.domain.model

import java.time.Instant

/**
 * The result of a single rate-limit check.
 *
 * [allowed] is false when the request was rejected. [remaining] is the number of requests
 * (or token-bucket tokens) available after this decision — always 0 when [allowed] is false.
 * [resetAt] is the earliest instant at which [remaining] may increase: the window expiry for
 * Fixed and Sliding Window, or the next-token-available time for Token Bucket.
 */
data class RateLimitDecision(
    val allowed: Boolean,
    val remaining: Int,
    val resetAt: Instant
)
