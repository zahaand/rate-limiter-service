package dev.zahaand.ratelimiter.infrastructure.redis

import dev.zahaand.ratelimiter.domain.model.RateLimitDecision
import dev.zahaand.ratelimiter.domain.model.RateLimitPolicy
import dev.zahaand.ratelimiter.domain.model.RateLimitStrategy
import dev.zahaand.ratelimiter.domain.port.RateLimitRepository
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import java.time.Clock
import java.time.Instant

/**
 * Redis-backed implementation of [RateLimitRepository] using Lua scripts for atomic evaluation.
 *
 * Each algorithm has a dedicated Lua script loaded as a string constant. Scripts are executed
 * via `EVAL` (not `EVALSHA`), so no pre-loading step is required — Redis compiles the script
 * on first invocation and caches it by SHA internally.
 *
 * Atomicity is guaranteed by Redis's single-threaded command execution: the entire Lua script
 * runs without interruption, so the read-modify-write cycle cannot be interleaved with another
 * client's script for the same key. This is the production enforcement of the atomicity
 * contract declared on [RateLimitRepository].
 *
 * Key prefixes isolate algorithm state: `rl:fw:` for Fixed Window, `rl:sw:` for Sliding Window,
 * `rl:tb:` for Token Bucket. Each algorithm's Lua script returns `{allowed, remaining, resetAt}`
 * as a list of integers, parsed by [parseDecision].
 */
class RedisRateLimitRepository(
    private val commands: RedisCoroutinesCommands<String, String>,
    private val clock: Clock = Clock.systemUTC()
) : RateLimitRepository {

    companion object {
        private val FIXED_WINDOW_SCRIPT = """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local windowSeconds = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local windowStart = math.floor(now / windowSeconds) * windowSeconds
            local resetAt = windowStart + windowSeconds
            local count = tonumber(redis.call('GET', key) or '0')
            if count < limit then
                redis.call('INCR', key)
                redis.call('EXPIREAT', key, resetAt)
                return {1, limit - count - 1, resetAt}
            else
                return {0, 0, resetAt}
            end
        """.trimIndent()

        private val SLIDING_WINDOW_SCRIPT = """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local windowSeconds = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local cutoff = now - windowSeconds
            redis.call('ZREMRANGEBYSCORE', key, '-inf', '(' .. cutoff)
            local count = tonumber(redis.call('ZCARD', key))
            if count < limit then
                redis.call('ZADD', key, now, now .. '-' .. redis.call('INCR', key .. ':seq'))
                redis.call('EXPIRE', key, windowSeconds + 1)
                local newCount = count + 1
                local oldest = tonumber(redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')[2] or now)
                local remaining = limit - newCount
                local resetAt = remaining > 0 and now or (oldest + windowSeconds)
                return {1, remaining, resetAt}
            else
                local oldest = tonumber(redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')[2] or now)
                return {0, 0, oldest + windowSeconds}
            end
        """.trimIndent()

        private val TOKEN_BUCKET_SCRIPT = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local windowSeconds = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local refillRate = capacity / windowSeconds
            local data = redis.call('HMGET', key, 'tokens', 'lastRefillAt')
            local tokens = tonumber(data[1]) or capacity
            local lastRefillAt = tonumber(data[2]) or now
            local elapsed = now - lastRefillAt
            local refilled = math.min(tokens + elapsed * refillRate, capacity)
            if refilled >= 1.0 then
                local newTokens = refilled - 1.0
                local remaining = math.floor(newTokens)
                local resetAt = now + (capacity - newTokens) / refillRate
                redis.call('HMSET', key, 'tokens', newTokens, 'lastRefillAt', now)
                redis.call('EXPIRE', key, windowSeconds * 2)
                return {1, remaining, math.floor(resetAt)}
            else
                local resetAt = now + (1.0 - refilled) / refillRate
                redis.call('HMSET', key, 'tokens', refilled, 'lastRefillAt', now)
                redis.call('EXPIRE', key, windowSeconds * 2)
                return {0, 0, math.floor(resetAt)}
            end
        """.trimIndent()
    }

    override suspend fun check(key: String, policy: RateLimitPolicy, now: Instant): RateLimitDecision {
        return when (policy.strategy) {
            is RateLimitStrategy.FixedWindow -> checkFixedWindow(key, policy, now)
            is RateLimitStrategy.SlidingWindow -> checkSlidingWindow(key, policy, now)
            is RateLimitStrategy.TokenBucket -> checkTokenBucket(key, policy, now)
        }
    }

    private suspend fun checkFixedWindow(key: String, policy: RateLimitPolicy, now: Instant): RateLimitDecision {
        val windowSeconds = policy.windowSeconds.toLong()
        val windowStart = now.epochSecond / windowSeconds * windowSeconds
        val redisKey = "rl:fw:$key:$windowStart"
        val result = commands.eval<List<Long>>(
            FIXED_WINDOW_SCRIPT,
            ScriptOutputType.MULTI,
            arrayOf(redisKey),
            policy.limit.toString(),
            policy.windowSeconds.toString(),
            now.epochSecond.toString()
        )
        return parseDecision(result)
    }

    private suspend fun checkSlidingWindow(key: String, policy: RateLimitPolicy, now: Instant): RateLimitDecision {
        val redisKey = "rl:sw:$key"
        val result = commands.eval<List<Long>>(
            SLIDING_WINDOW_SCRIPT,
            ScriptOutputType.MULTI,
            arrayOf(redisKey, "$redisKey:seq"),
            policy.limit.toString(),
            policy.windowSeconds.toString(),
            now.epochSecond.toString()
        )
        return parseDecision(result)
    }

    private suspend fun checkTokenBucket(key: String, policy: RateLimitPolicy, now: Instant): RateLimitDecision {
        val redisKey = "rl:tb:$key"
        val result = commands.eval<List<Long>>(
            TOKEN_BUCKET_SCRIPT,
            ScriptOutputType.MULTI,
            arrayOf(redisKey),
            policy.limit.toString(),
            policy.windowSeconds.toString(),
            now.epochSecond.toString()
        )
        return parseDecision(result)
    }

    private fun parseDecision(result: List<Long>?): RateLimitDecision {
        requireNotNull(result) { "Lua script returned null" }
        return RateLimitDecision(
            allowed = result[0] == 1L,
            remaining = result[1].toInt(),
            resetAt = Instant.ofEpochSecond(result[2])
        )
    }
}
