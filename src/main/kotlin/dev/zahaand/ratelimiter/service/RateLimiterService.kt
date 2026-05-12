package dev.zahaand.ratelimiter.service

import dev.zahaand.ratelimiter.domain.model.RateLimitDecision
import dev.zahaand.ratelimiter.domain.model.RateLimitKey
import dev.zahaand.ratelimiter.domain.port.ConfigRepository
import dev.zahaand.ratelimiter.domain.port.RateLimitRepository
import dev.zahaand.ratelimiter.infrastructure.config.AppConfig
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Orchestrates rate-limit checks: policy resolution, decision evaluation, and rejection logging.
 *
 * For each incoming [check] call the service:
 * 1. Looks up an explicit policy for the key in [ConfigRepository].
 * 2. Falls back to the service-level default policy from [AppConfig] when none is found.
 *    Callers cannot distinguish a default-policy decision from an explicit-policy decision
 *    — the fallback is transparent.
 * 3. Delegates the atomic check-and-increment to [RateLimitRepository].
 * 4. Emits a WARN log when the request is rejected (allowed = false). The log entry always
 *    includes exactly five structured fields: key, strategy, limit, windowSeconds, timestamp.
 *    This contract is relied on by downstream log alerting — do not remove or rename fields.
 */
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
