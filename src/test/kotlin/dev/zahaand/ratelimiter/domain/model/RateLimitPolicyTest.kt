package dev.zahaand.ratelimiter.domain.model

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import kotlin.test.Test
import kotlin.test.assertFailsWith

class RateLimitPolicyTest {

    @Nested
    inner class `Init guards` {

        @Test
        fun `should throw when limit is negative`() {
            assertFailsWith<IllegalArgumentException> {
                RateLimitPolicy(limit = -1, windowSeconds = 60, strategy = RateLimitStrategy.FixedWindow)
            }
        }

        @Test
        fun `should throw when windowSeconds is zero`() {
            assertFailsWith<IllegalArgumentException> {
                RateLimitPolicy(limit = 10, windowSeconds = 0, strategy = RateLimitStrategy.FixedWindow)
            }
        }

        @Test
        fun `should throw when windowSeconds is negative`() {
            assertFailsWith<IllegalArgumentException> {
                RateLimitPolicy(limit = 10, windowSeconds = -1, strategy = RateLimitStrategy.FixedWindow)
            }
        }

        @Test
        fun `should include offending value in error message for limit`() {
            assertThatThrownBy {
                RateLimitPolicy(limit = -5, windowSeconds = 60, strategy = RateLimitStrategy.FixedWindow)
            }.hasMessageContaining("-5")
        }

        @Test
        fun `should include offending value in error message for windowSeconds`() {
            assertThatThrownBy {
                RateLimitPolicy(limit = 10, windowSeconds = -3, strategy = RateLimitStrategy.FixedWindow)
            }.hasMessageContaining("-3")
        }
    }

    @Nested
    inner class `Valid construction` {

        @Test
        fun `should accept limit zero (reject-all config)`() {
            val policy = RateLimitPolicy(limit = 0, windowSeconds = 60, strategy = RateLimitStrategy.FixedWindow)
            assert(policy.limit == 0)
        }

        @Test
        fun `should accept positive limit and windowSeconds`() {
            val policy = RateLimitPolicy(limit = 10, windowSeconds = 60, strategy = RateLimitStrategy.SlidingWindow)
            assert(policy.limit == 10)
            assert(policy.windowSeconds == 60)
        }

        @Test
        fun `should accept windowSeconds of one`() {
            val policy = RateLimitPolicy(limit = 5, windowSeconds = 1, strategy = RateLimitStrategy.TokenBucket)
            assert(policy.windowSeconds == 1)
        }
    }
}
