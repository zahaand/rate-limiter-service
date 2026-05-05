package dev.zahaand.ratelimiter.domain.algorithm

import dev.zahaand.ratelimiter.domain.model.RateLimitDecision
import dev.zahaand.ratelimiter.domain.model.RateLimitPolicy
import java.time.Duration
import java.time.Instant

object TokenBucketAlgorithm {

    data class State(val tokens: Double, val lastRefillAt: Instant)

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
