package dev.zahaand.ratelimiter.routes.dto

import kotlinx.serialization.Serializable

@Serializable
data class LimitConfigRequest(
    val key: String,
    val limit: Int,
    val windowSeconds: Int,
    val strategy: String
)
