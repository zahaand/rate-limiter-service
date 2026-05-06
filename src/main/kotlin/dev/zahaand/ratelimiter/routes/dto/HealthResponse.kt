package dev.zahaand.ratelimiter.routes.dto

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String, val redis: String)
