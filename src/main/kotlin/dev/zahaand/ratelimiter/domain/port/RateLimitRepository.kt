package dev.zahaand.ratelimiter.domain.port

import dev.zahaand.ratelimiter.domain.model.RateLimitDecision
import dev.zahaand.ratelimiter.domain.model.RateLimitPolicy
import java.time.Instant

interface RateLimitRepository {
    suspend fun check(key: String, policy: RateLimitPolicy, now: Instant): RateLimitDecision
}
