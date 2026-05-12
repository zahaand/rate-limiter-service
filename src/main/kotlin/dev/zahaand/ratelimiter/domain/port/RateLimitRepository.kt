package dev.zahaand.ratelimiter.domain.port

import dev.zahaand.ratelimiter.domain.model.RateLimitDecision
import dev.zahaand.ratelimiter.domain.model.RateLimitPolicy
import java.time.Instant

/**
 * Port for executing an atomic rate-limit check against persistent storage.
 *
 * The single [check] operation combines read and conditional write: it reads the current
 * request count (or token state), determines whether the request is allowed, and — if
 * allowed — increments the count (or consumes a token) in the same atomic step.
 * Implementations must guarantee this atomicity; without it, two concurrent callers can
 * both observe `count < limit` and both succeed, violating the limit invariant under load.
 *
 * The Redis implementation achieves atomicity via Lua scripts executed with `EVAL`, which
 * Redis runs serially on a single thread.
 */
interface RateLimitRepository {
    /**
     * Atomically checks the rate limit for [key] under [policy] and advances the counter if allowed.
     *
     * @param key    The client identifier whose limit is being checked.
     * @param policy The limit, window, and algorithm to apply.
     * @param now    The current timestamp passed in by the caller to avoid clock skew between
     *               the application and Redis.
     * @return The decision: whether the request is allowed, tokens/requests remaining, and the
     *         instant at which the window or bucket resets.
     */
    suspend fun check(key: String, policy: RateLimitPolicy, now: Instant): RateLimitDecision
}
