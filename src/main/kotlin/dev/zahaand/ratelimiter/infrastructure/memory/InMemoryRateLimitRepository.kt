package dev.zahaand.ratelimiter.infrastructure.memory

import dev.zahaand.ratelimiter.domain.algorithm.FixedWindowAlgorithm
import dev.zahaand.ratelimiter.domain.algorithm.SlidingWindowAlgorithm
import dev.zahaand.ratelimiter.domain.algorithm.TokenBucketAlgorithm
import dev.zahaand.ratelimiter.domain.model.RateLimitDecision
import dev.zahaand.ratelimiter.domain.model.RateLimitPolicy
import dev.zahaand.ratelimiter.domain.model.RateLimitStrategy
import dev.zahaand.ratelimiter.domain.port.RateLimitRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class InMemoryRateLimitRepository(
    private val clock: Clock = Clock.systemUTC()
) : RateLimitRepository {

    private val mutexes = ConcurrentHashMap<String, Mutex>()
    private val fixedWindowStates = ConcurrentHashMap<String, FixedWindowAlgorithm.State>()
    private val slidingWindowStates = ConcurrentHashMap<String, List<Instant>>()
    private val tokenBucketStates = ConcurrentHashMap<String, TokenBucketAlgorithm.State>()

    override suspend fun check(key: String, policy: RateLimitPolicy, now: Instant): RateLimitDecision {
        val mutex = mutexes.computeIfAbsent(key) { Mutex() }
        return mutex.withLock {
            when (policy.strategy) {
                is RateLimitStrategy.FixedWindow -> checkFixedWindow(key, policy, now)
                is RateLimitStrategy.SlidingWindow -> checkSlidingWindow(key, policy, now)
                is RateLimitStrategy.TokenBucket -> checkTokenBucket(key, policy, now)
            }
        }
    }

    private fun checkFixedWindow(key: String, policy: RateLimitPolicy, now: Instant): RateLimitDecision {
        val (decision, newState) = FixedWindowAlgorithm.check(policy, fixedWindowStates[key], now)
        fixedWindowStates[key] = newState
        return decision
    }

    private fun checkSlidingWindow(key: String, policy: RateLimitPolicy, now: Instant): RateLimitDecision {
        val (decision, newTimestamps) = SlidingWindowAlgorithm.check(policy, slidingWindowStates[key] ?: emptyList(), now)
        slidingWindowStates[key] = newTimestamps
        return decision
    }

    private fun checkTokenBucket(key: String, policy: RateLimitPolicy, now: Instant): RateLimitDecision {
        val (decision, newState) = TokenBucketAlgorithm.check(policy, tokenBucketStates[key], now)
        tokenBucketStates[key] = newState
        return decision
    }
}
