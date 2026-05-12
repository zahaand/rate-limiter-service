package dev.zahaand.ratelimiter.domain.model

/**
 * Immutable configuration describing how many requests a key may make within a time window.
 *
 * [limit] is the maximum number of allowed requests (or token bucket capacity) per [windowSeconds].
 * [strategy] selects which algorithm evaluates the count. Invariants are enforced in [init]:
 * a negative limit or non-positive windowSeconds is a programming error, not a user-input error
 * (the HTTP layer validates before constructing this value).
 */
data class RateLimitPolicy(
    val limit: Int,
    val windowSeconds: Int,
    val strategy: RateLimitStrategy
) {
    init {
        require(limit >= 0) { "limit must be non-negative, was $limit" }
        require(windowSeconds > 0) { "windowSeconds must be positive, was $windowSeconds" }
    }
}
