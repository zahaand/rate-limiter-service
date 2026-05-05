package dev.zahaand.ratelimiter.domain.algorithm

import dev.zahaand.ratelimiter.domain.model.RateLimitDecision
import dev.zahaand.ratelimiter.domain.model.RateLimitPolicy
import java.time.Instant

object FixedWindowAlgorithm {

    data class State(val count: Int, val windowStart: Instant)

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
