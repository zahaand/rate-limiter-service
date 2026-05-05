package dev.zahaand.ratelimiter.infrastructure.redis

import dev.zahaand.ratelimiter.domain.model.RateLimitPolicy
import dev.zahaand.ratelimiter.domain.model.RateLimitStrategy
import dev.zahaand.ratelimiter.domain.port.ConfigRepository
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import kotlinx.coroutines.flow.collect

class RedisConfigRepository(
    private val commands: RedisCoroutinesCommands<String, String>
) : ConfigRepository {

    override suspend fun save(key: String, policy: RateLimitPolicy) {
        commands.hset(
            "config:$key",
            mapOf(
                "limit" to policy.limit.toString(),
                "windowSeconds" to policy.windowSeconds.toString(),
                "strategy" to policy.strategy.configName
            )
        )
    }

    override suspend fun get(key: String): RateLimitPolicy? {
        val map = mutableMapOf<String, String>()
        commands.hgetall("config:$key").collect { kv ->
            val value = kv.value
            if (value != null) map[kv.key] = value
        }
        if (map.isEmpty()) return null
        return RateLimitPolicy(
            limit = requireNotNull(map["limit"]) { "missing field: limit" }.toInt(),
            windowSeconds = requireNotNull(map["windowSeconds"]) { "missing field: windowSeconds" }.toInt(),
            strategy = RateLimitStrategy.fromConfigName(
                requireNotNull(map["strategy"]) { "missing field: strategy" }
            )
        )
    }

    override suspend fun delete(key: String) {
        commands.del("config:$key")
    }
}
