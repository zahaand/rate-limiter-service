package dev.zahaand.ratelimiter.routes.dto

import dev.zahaand.ratelimiter.routes.dto.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class CheckResponse(
    val allowed: Boolean,
    val remaining: Int,
    @Serializable(with = InstantSerializer::class) val resetAt: Instant
)
