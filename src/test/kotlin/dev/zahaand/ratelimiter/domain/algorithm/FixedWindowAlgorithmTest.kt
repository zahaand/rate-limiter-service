package dev.zahaand.ratelimiter.domain.algorithm

import dev.zahaand.ratelimiter.domain.model.RateLimitPolicy
import dev.zahaand.ratelimiter.domain.model.RateLimitStrategy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import java.time.Instant
import kotlin.test.Test

class FixedWindowAlgorithmTest {

    private val policy = RateLimitPolicy(limit = 10, windowSeconds = 60, strategy = RateLimitStrategy.FixedWindow)
    private val windowStart = Instant.ofEpochSecond(120)
    private val now = Instant.ofEpochSecond(130)

    @Nested
    inner class `Allow and reject` {

        @Test
        fun `should allow request when under limit`() {
            val state = FixedWindowAlgorithm.State(count = 5, windowStart = windowStart)
            val (decision, newState) = FixedWindowAlgorithm.check(policy, state, now)

            assertThat(decision.allowed).isTrue()
            assertThat(decision.remaining).isEqualTo(4)
            assertThat(newState.count).isEqualTo(6)
        }

        @Test
        fun `should allow 10th request and return remaining zero`() {
            val state = FixedWindowAlgorithm.State(count = 9, windowStart = windowStart)
            val (decision, newState) = FixedWindowAlgorithm.check(policy, state, now)

            assertThat(decision.allowed).isTrue()
            assertThat(decision.remaining).isEqualTo(0)
            assertThat(newState.count).isEqualTo(10)
        }

        @Test
        fun `should reject 11th request with remaining zero`() {
            val state = FixedWindowAlgorithm.State(count = 10, windowStart = windowStart)
            val (decision, newState) = FixedWindowAlgorithm.check(policy, state, now)

            assertThat(decision.allowed).isFalse()
            assertThat(decision.remaining).isEqualTo(0)
            assertThat(newState.count).isEqualTo(10)
        }

        @Test
        fun `should reject all requests when limit is zero`() {
            val zeroPolicy = RateLimitPolicy(limit = 0, windowSeconds = 60, strategy = RateLimitStrategy.FixedWindow)
            val (decision, _) = FixedWindowAlgorithm.check(zeroPolicy, null, now)

            assertThat(decision.allowed).isFalse()
            assertThat(decision.remaining).isEqualTo(0)
        }
    }

    @Nested
    inner class `Window boundary` {

        @Test
        fun `should reset counter after window boundary is crossed`() {
            val oldWindowStart = Instant.ofEpochSecond(60)
            val state = FixedWindowAlgorithm.State(count = 10, windowStart = oldWindowStart)
            val afterBoundary = Instant.ofEpochSecond(130)

            val (decision, newState) = FixedWindowAlgorithm.check(policy, state, afterBoundary)

            assertThat(decision.allowed).isTrue()
            assertThat(decision.remaining).isEqualTo(9)
            assertThat(newState.count).isEqualTo(1)
            assertThat(newState.windowStart).isEqualTo(Instant.ofEpochSecond(120))
        }

        @Test
        fun `should allow first request when state is null`() {
            val (decision, newState) = FixedWindowAlgorithm.check(policy, null, now)

            assertThat(decision.allowed).isTrue()
            assertThat(decision.remaining).isEqualTo(9)
            assertThat(newState.count).isEqualTo(1)
        }

        @Test
        fun `should align window to epoch boundary`() {
            val t130 = Instant.ofEpochSecond(130)
            val (_, newState) = FixedWindowAlgorithm.check(policy, null, t130)

            assertThat(newState.windowStart).isEqualTo(Instant.ofEpochSecond(120))
        }

        @Test
        fun `should set resetAt to windowStart plus windowSeconds`() {
            val (decision, _) = FixedWindowAlgorithm.check(policy, null, now)

            assertThat(decision.resetAt).isEqualTo(Instant.ofEpochSecond(180))
        }
    }
}
