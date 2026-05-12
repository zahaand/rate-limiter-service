package dev.zahaand.ratelimiter.domain.model

/**
 * Sealed hierarchy of rate-limiting algorithm variants.
 *
 * Each variant carries a stable [configName] string that is persisted in Redis and accepted
 * via the API. Exhaustive `when` expressions on this hierarchy require no `else` branch,
 * allowing the compiler to enforce that new variants are handled at every callsite.
 *
 * Use [fromConfigName] to deserialize a stored or user-supplied strategy name. The lookup
 * is case-insensitive and throws [IllegalStateException] for unknown values.
 */
sealed class RateLimitStrategy {
    abstract val configName: String

    data object FixedWindow : RateLimitStrategy() {
        override val configName = "FIXED_WINDOW"
    }

    data object SlidingWindow : RateLimitStrategy() {
        override val configName = "SLIDING_WINDOW"
    }

    data object TokenBucket : RateLimitStrategy() {
        override val configName = "TOKEN_BUCKET"
    }

    companion object {
        private val byName by lazy {
            listOf(FixedWindow, SlidingWindow, TokenBucket)
                .associateBy { it.configName }
        }

        fun fromConfigName(name: String): RateLimitStrategy =
            byName[name.uppercase()] ?: error("Unknown strategy: $name")
    }
}
