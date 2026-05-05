package dev.zahaand.ratelimiter.domain.model

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
