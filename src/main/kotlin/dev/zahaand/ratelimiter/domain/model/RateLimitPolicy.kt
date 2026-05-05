package dev.zahaand.ratelimiter.domain.model

data class RateLimitPolicy(
    val limit: Int,
    val windowSeconds: Int,
    val strategy: RateLimitStrategy
) {
    init {
        require(limit >= 0) { "limit must be non-negative, was $limit" }
        require(windowSeconds > 0) { "windowSeconds must be positive, was $windowSeconds" }
    }
}
