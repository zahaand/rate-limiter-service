package dev.zahaand.ratelimiter.infrastructure.config

import dev.zahaand.ratelimiter.domain.model.RateLimitPolicy
import dev.zahaand.ratelimiter.domain.model.RateLimitStrategy

data class AppConfig(
    val server: ServerConfig,
    val redis: RedisConfig,
    val rateLimit: RateLimitDefaults
)

data class ServerConfig(
    val port: Int = 8080
)

data class RedisConfig(
    val host: String = "localhost",
    val port: Int = 6379
)

data class RateLimitDefaults(
    val defaultLimit: Int = 100,
    val defaultWindowSeconds: Int = 60,
    val defaultStrategy: String = "FIXED_WINDOW"
) {
    fun toPolicy(): RateLimitPolicy = RateLimitPolicy(
        limit = defaultLimit,
        windowSeconds = defaultWindowSeconds,
        strategy = RateLimitStrategy.fromConfigName(defaultStrategy)
    )
}
