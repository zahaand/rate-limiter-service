package dev.zahaand.ratelimiter.domain.model

@JvmInline
value class RateLimitKey(val value: String) {
    init {
        require(value.isNotBlank()) { "key must not be blank" }
    }
}
