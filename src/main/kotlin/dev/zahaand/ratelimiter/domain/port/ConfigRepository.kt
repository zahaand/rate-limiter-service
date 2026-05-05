package dev.zahaand.ratelimiter.domain.port

import dev.zahaand.ratelimiter.domain.model.RateLimitPolicy

interface ConfigRepository {
    suspend fun save(key: String, policy: RateLimitPolicy)
    suspend fun get(key: String): RateLimitPolicy?
    suspend fun delete(key: String)
}
