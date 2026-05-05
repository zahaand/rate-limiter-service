package dev.zahaand.ratelimiter.domain.algorithm

import dev.zahaand.ratelimiter.domain.model.RateLimitDecision
import dev.zahaand.ratelimiter.domain.model.RateLimitPolicy
import java.time.Instant

object SlidingWindowAlgorithm {

    fun check(
        policy: RateLimitPolicy,
        timestamps: List<Instant>,
        now: Instant
    ): Pair<RateLimitDecision, List<Instant>> {
        val cutoff = now.minusSeconds(policy.windowSeconds.toLong())
        val valid = timestamps.filter { it >= cutoff }

        val allowed = valid.size < policy.limit
        val newTimestamps = if (allowed) valid + now else valid
        val remaining = maxOf(0, policy.limit - newTimestamps.size)

        val resetAt = if (remaining == 0 && newTimestamps.isNotEmpty()) {
            newTimestamps.first().plusSeconds(policy.windowSeconds.toLong())
        } else {
            now
        }

        return RateLimitDecision(allowed, remaining, resetAt) to newTimestamps
    }
}
