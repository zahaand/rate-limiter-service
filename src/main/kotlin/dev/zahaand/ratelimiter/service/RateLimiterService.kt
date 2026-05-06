package dev.zahaand.ratelimiter.service

import dev.zahaand.ratelimiter.domain.model.RateLimitDecision
import dev.zahaand.ratelimiter.domain.model.RateLimitKey
import dev.zahaand.ratelimiter.domain.port.ConfigRepository
import dev.zahaand.ratelimiter.domain.port.RateLimitRepository
import dev.zahaand.ratelimiter.infrastructure.config.AppConfig
import org.slf4j.LoggerFactory
import java.time.Instant

class RateLimiterService(
    private val rateLimitRepository: RateLimitRepository,
    private val configRepository: ConfigRepository,
    private val appConfig: AppConfig
) {
    private val log = LoggerFactory.getLogger(RateLimiterService::class.java)

    suspend fun check(key: RateLimitKey): RateLimitDecision {
        val policy = configRepository.get(key.value) ?: appConfig.rateLimit.toPolicy()
        val decision = rateLimitRepository.check(key.value, policy, Instant.now())
        if (!decision.allowed) {
            log.warn(
                "Rate limit exceeded: key={}, strategy={}, limit={}, windowSeconds={}, timestamp={}",
                key.value, policy.strategy.configName, policy.limit, policy.windowSeconds, Instant.now()
            )
        }
        return decision
    }
}
