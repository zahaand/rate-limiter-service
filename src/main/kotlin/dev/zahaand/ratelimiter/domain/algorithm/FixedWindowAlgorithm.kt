package dev.zahaand.ratelimiter.domain.algorithm

import dev.zahaand.ratelimiter.domain.model.RateLimitDecision
import dev.zahaand.ratelimiter.domain.model.RateLimitPolicy
import java.time.Instant

/**
 * Rate-limiting algorithm that counts requests within epoch-aligned, non-overlapping windows.
 *
 * Window boundaries are derived by integer division of the Unix epoch second:
 * `windowStart = floor(epochSecond / windowSeconds) * windowSeconds`.
 * This means a 60-second window always starts on a clock minute (00:00, 01:00, …),
 * and a new window starts exactly when the previous one expires — no coordination needed
 * between replicas observing the same clock.
 *
 * The pure-Kotlin [check] function is used in unit tests. The production path duplicates
 * this logic in a Lua script inside [RedisRateLimitRepository]: the Lua script runs
 * atomically on the Redis server, so the counter read, increment, and TTL extension are
 * a single indivisible operation — no two concurrent requests can both see `count < limit`
 * and both increment past it.
 */
object FixedWindowAlgorithm {

    data class State(val count: Int, val windowStart: Instant)

    /**
     * Evaluates a rate-limit check for the current window, returning the decision and updated state.
     *
     * @param policy The active policy — limit, windowSeconds, and strategy.
     * @param state  The persisted state from the previous call, or null on first request.
     *               A null state (or state from a different window) resets the count to zero.
     * @param now    Current timestamp; used to derive the window index and reset instant.
     * @return Decision paired with new state. The caller must persist the new state atomically.
     */
    fun check(
        policy: RateLimitPolicy,
        state: State?,
        now: Instant
    ): Pair<RateLimitDecision, State> {
        val windowSeconds = policy.windowSeconds.toLong()
        val windowStart = Instant.ofEpochSecond(now.epochSecond / windowSeconds * windowSeconds)
        val resetAt = windowStart.plusSeconds(windowSeconds)

        val currentCount = if (state == null || state.windowStart != windowStart) 0 else state.count

        val allowed = currentCount < policy.limit
        val newCount = if (allowed) currentCount + 1 else currentCount
        val remaining = maxOf(0, policy.limit - newCount)

        return RateLimitDecision(allowed, remaining, resetAt) to State(newCount, windowStart)
    }
}
