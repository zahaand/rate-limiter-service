package dev.zahaand.ratelimiter.infrastructure.memory

import dev.zahaand.ratelimiter.domain.model.RateLimitPolicy
import dev.zahaand.ratelimiter.domain.port.ConfigRepository
import java.util.concurrent.ConcurrentHashMap

class InMemoryConfigRepository : ConfigRepository {
    private val policies = ConcurrentHashMap<String, RateLimitPolicy>()

    override suspend fun save(key: String, policy: RateLimitPolicy) {
        policies[key] = policy
    }

    override suspend fun get(key: String): RateLimitPolicy? = policies[key]

    override suspend fun delete(key: String): Boolean = policies.remove(key) != null
}
