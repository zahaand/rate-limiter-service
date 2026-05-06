package dev.zahaand.ratelimiter.routes.dto

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val error: String)
