package dev.zahaand.ratelimiter.domain.port

import dev.zahaand.ratelimiter.domain.model.RateLimitPolicy

/**
 * Port for storing and retrieving per-key rate-limit policies.
 *
 * Policies are keyed by client identifier. [get] returns null when no explicit policy has been
 * stored for a key — callers are expected to fall back to a service-level default in that case.
 * [delete] returns true if a policy existed and was removed, false if no policy was found;
 * callers use this boolean to distinguish 204 (deleted) from 404 (not found) responses.
 */
interface ConfigRepository {
    suspend fun save(key: String, policy: RateLimitPolicy)
    suspend fun get(key: String): RateLimitPolicy?
    /** Returns true if a policy was found and deleted, false if no policy existed for [key]. */
    suspend fun delete(key: String): Boolean
}
