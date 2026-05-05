package dev.zahaand.ratelimiter.infrastructure.config

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
    val defaultWindowSeconds: Int = 60
)
