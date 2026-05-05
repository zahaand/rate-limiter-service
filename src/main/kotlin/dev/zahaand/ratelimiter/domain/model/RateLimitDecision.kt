package dev.zahaand.ratelimiter.domain.model

import java.time.Instant

data class RateLimitDecision(
    val allowed: Boolean,
    val remaining: Int,
    val resetAt: Instant
)
