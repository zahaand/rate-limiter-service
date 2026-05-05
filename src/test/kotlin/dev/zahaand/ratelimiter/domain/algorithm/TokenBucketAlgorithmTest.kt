package dev.zahaand.ratelimiter.domain.algorithm

import dev.zahaand.ratelimiter.domain.model.RateLimitPolicy
import dev.zahaand.ratelimiter.domain.model.RateLimitStrategy
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Nested
import java.time.Instant
import kotlin.test.Test

class TokenBucketAlgorithmTest {

    private val policy = RateLimitPolicy(limit = 10, windowSeconds = 60, strategy = RateLimitStrategy.TokenBucket)
    private val now = Instant.ofEpochSecond(1_000)

    @Nested
    inner class `Full bucket` {

        @Test
        fun `should allow first request when state is null (bucket starts full)`() {
            val (decision, newState) = TokenBucketAlgorithm.check(policy, null, now)

            assertThat(decision.allowed).isTrue()
            assertThat(newState.tokens).isCloseTo(9.0, within(0.001))
        }

        @Test
        fun `should return remaining as floor of tokens after deduction`() {
            val (decision, _) = TokenBucketAlgorithm.check(policy, null, now)

            assertThat(decision.remaining).isEqualTo(9)
        }

        @Test
        fun `should allow limit consecutive requests from full bucket`() {
            var state: TokenBucketAlgorithm.State? = null
            var lastDecision = TokenBucketAlgorithm.check(policy, state, now).also { (d, s) ->
                state = s
            }.first

            repeat(9) {
                val (d, s) = TokenBucketAlgorithm.check(policy, state, now)
                state = s
                lastDecision = d
            }

            assertThat(lastDecision.allowed).isTrue()
            assertThat(lastDecision.remaining).isEqualTo(0)
        }
    }

    @Nested
    inner class `Empty bucket` {

        @Test
        fun `should reject when bucket is empty`() {
            val emptyState = TokenBucketAlgorithm.State(tokens = 0.0, lastRefillAt = now)
            val (decision, _) = TokenBucketAlgorithm.check(policy, emptyState, now)

            assertThat(decision.allowed).isFalse()
            assertThat(decision.remaining).isEqualTo(0)
        }

        @Test
        fun `should reject when bucket has less than one token`() {
            val partialState = TokenBucketAlgorithm.State(tokens = 0.5, lastRefillAt = now)
            val (decision, _) = TokenBucketAlgorithm.check(policy, partialState, now)

            assertThat(decision.allowed).isFalse()
        }
    }

    @Nested
    inner class `CHK011 Token Bucket refill — US2 Scenario 3` {

        @Test
        fun `should refill 5 tokens after 30s and remaining is 4 after consuming one`() {
            val emptyAt = now
            val emptyState = TokenBucketAlgorithm.State(tokens = 0.0, lastRefillAt = emptyAt)
            val thirtySecondsLater = now.plusSeconds(30)

            val (decision, _) = TokenBucketAlgorithm.check(policy, emptyState, thirtySecondsLater)

            assertThat(decision.allowed).isTrue()
            assertThat(decision.remaining).isEqualTo(4)
        }
    }

    @Nested
    inner class `Fractional token accumulation` {

        @Test
        fun `should accumulate fractional tokens across multiple calls`() {
            val policy1s = RateLimitPolicy(limit = 10, windowSeconds = 10, strategy = RateLimitStrategy.TokenBucket)
            val emptyState = TokenBucketAlgorithm.State(tokens = 0.0, lastRefillAt = now)

            val halfSecondLater = now.plusNanos(500_000_000)
            val (d1, s1) = TokenBucketAlgorithm.check(policy1s, emptyState, halfSecondLater)

            assertThat(d1.allowed).isFalse()

            val fullSecondLater = now.plusSeconds(1)
            val (d2, _) = TokenBucketAlgorithm.check(policy1s, emptyState, fullSecondLater)

            assertThat(d2.allowed).isTrue()
        }
    }

    @Nested
    inner class `Limit zero — no division by zero` {

        @Test
        fun `should reject all and not throw when limit is zero`() {
            val zeroPolicy = RateLimitPolicy(limit = 0, windowSeconds = 60, strategy = RateLimitStrategy.TokenBucket)

            val (decision, _) = TokenBucketAlgorithm.check(zeroPolicy, null, now)

            assertThat(decision.allowed).isFalse()
            assertThat(decision.remaining).isEqualTo(0)
        }

        @Test
        fun `should set resetAt to now plus windowSeconds when refillRate is zero`() {
            val zeroPolicy = RateLimitPolicy(limit = 0, windowSeconds = 60, strategy = RateLimitStrategy.TokenBucket)

            val (decision, _) = TokenBucketAlgorithm.check(zeroPolicy, null, now)

            assertThat(decision.resetAt).isEqualTo(now.plusSeconds(60))
        }
    }
}
