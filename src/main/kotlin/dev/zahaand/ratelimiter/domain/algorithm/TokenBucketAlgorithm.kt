package dev.zahaand.ratelimiter.domain.algorithm

import dev.zahaand.ratelimiter.domain.model.RateLimitDecision
import dev.zahaand.ratelimiter.domain.model.RateLimitPolicy
import java.time.Duration
import java.time.Instant

/**
 * Rate-limiting algorithm that models a bucket of tokens refilled continuously over time.
 *
 * Tokens accumulate at `refillRate = limit / windowSeconds` tokens per second up to a
 * maximum of `limit` (the bucket capacity). Each allowed request consumes exactly one token.
 *
 * The token count is stored and computed as a `Double` to support fractional accumulation
 * between calls — a refill rate of 0.5 tokens/s accumulates correctly across two 1-second
 * intervals. The `remaining` field in the decision applies `floor` (via `toInt()`) because
 * partial tokens cannot be consumed.
 *
 * When `windowSeconds` is zero, `refillRate` is set to `0.0` to guard against division by
 * zero; in this state the bucket never refills and every request after the first is rejected.
 *
 * The production path duplicates this arithmetic in a Lua script inside
 * [RedisRateLimitRepository], which reads, updates, and writes the token hash atomically
 * on the Redis server — preventing two concurrent requests from both consuming the same
 * last token.
 */
object TokenBucketAlgorithm {

    data class State(val tokens: Double, val lastRefillAt: Instant)

    /**
     * Evaluates a rate-limit check by computing the current token count after refill.
     *
     * @param policy The active policy — limit (bucket capacity) and windowSeconds (refill period).
     * @param state  The persisted bucket state, or null on first request (starts full).
     * @param now    Current timestamp used to compute elapsed time and the refilled token count.
     * @return Decision paired with updated state. The new [State.tokens] value must be persisted
     *         atomically before the next call.
     */
    fun check(
        policy: RateLimitPolicy,
        state: State?,
        now: Instant
    ): Pair<RateLimitDecision, State> {
        val capacity = policy.limit.toDouble()
        val refillRate = if (policy.windowSeconds > 0) capacity / policy.windowSeconds else 0.0

        val currentState = state ?: State(capacity, now)
        val elapsed = Duration.between(currentState.lastRefillAt, now).toNanos() / 1_000_000_000.0
        val refilled = minOf(currentState.tokens + elapsed * refillRate, capacity)

        return if (refilled >= 1.0) {
            val newTokens = refilled - 1.0
            val remaining = newTokens.toInt()
            val resetAt = if (refillRate > 0.0) {
                now.plusNanos(((capacity - newTokens) / refillRate * 1_000_000_000).toLong())
            } else {
                now.plusSeconds(policy.windowSeconds.toLong())
            }
            RateLimitDecision(true, remaining, resetAt) to State(newTokens, now)
        } else {
            val resetAt = if (refillRate > 0.0) {
                now.plusNanos(((1.0 - refilled) / refillRate * 1_000_000_000).toLong())
            } else {
                now.plusSeconds(policy.windowSeconds.toLong())
            }
            RateLimitDecision(false, 0, resetAt) to State(refilled, now)
        }
    }
}
