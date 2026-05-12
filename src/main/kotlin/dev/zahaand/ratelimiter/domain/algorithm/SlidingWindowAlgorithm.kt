package dev.zahaand.ratelimiter.domain.algorithm

import dev.zahaand.ratelimiter.domain.model.RateLimitDecision
import dev.zahaand.ratelimiter.domain.model.RateLimitPolicy
import java.time.Instant

/**
 * Rate-limiting algorithm that tracks individual request timestamps in a rolling window.
 *
 * State is a sorted list of timestamps (backed by a Redis ZSET in production). On each call,
 * all timestamps strictly older than `now - windowSeconds` are evicted before the count is
 * evaluated. The cutoff boundary is **exclusive** (`it >= cutoff`) — a timestamp exactly at
 * the boundary counts as still within the window, matching the ZSET query
 * `ZREMRANGEBYSCORE key -inf (cutoff` (open-interval notation).
 *
 * When the bucket is full, `resetAt` is the oldest surviving timestamp plus `windowSeconds`,
 * i.e., the earliest moment the window drops below the limit. When capacity remains, `resetAt`
 * is set to `now` (no meaningful future reset point).
 *
 * The production path executes equivalent logic atomically in Lua inside
 * [RedisRateLimitRepository]: eviction, count read, and conditional ZADD happen in a single
 * Redis command, preventing race conditions under concurrent load.
 */
object SlidingWindowAlgorithm {

    /**
     * Evaluates a rate-limit check against the current sliding window.
     *
     * @param policy     The active policy — limit and windowSeconds.
     * @param timestamps All request timestamps currently within or near the window.
     * @param now        Current timestamp used as both the cutoff reference and the new entry.
     * @return Decision paired with the updated timestamp list (old entries pruned, new entry
     *         appended when allowed). The caller must persist the list atomically.
     */
    fun check(
        policy: RateLimitPolicy,
        timestamps: List<Instant>,
        now: Instant
    ): Pair<RateLimitDecision, List<Instant>> {
        val cutoff = now.minusSeconds(policy.windowSeconds.toLong())
        val valid = timestamps.filter { it >= cutoff }

        val allowed = valid.size < policy.limit
        val newTimestamps = if (allowed) valid + now else valid
        val remaining = maxOf(0, policy.limit - newTimestamps.size)

        val resetAt = if (remaining == 0 && newTimestamps.isNotEmpty()) {
            newTimestamps.first().plusSeconds(policy.windowSeconds.toLong())
        } else {
            now
        }

        return RateLimitDecision(allowed, remaining, resetAt) to newTimestamps
    }
}
