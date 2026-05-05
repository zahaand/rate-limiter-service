package dev.zahaand.ratelimiter.domain.algorithm

import dev.zahaand.ratelimiter.domain.model.RateLimitPolicy
import dev.zahaand.ratelimiter.domain.model.RateLimitStrategy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import java.time.Instant
import kotlin.test.Test

class SlidingWindowAlgorithmTest {

    private val policy = RateLimitPolicy(limit = 3, windowSeconds = 60, strategy = RateLimitStrategy.SlidingWindow)
    private val now = Instant.ofEpochSecond(1_000)

    @Nested
    inner class `Allow and reject` {

        @Test
        fun `should allow request when under limit`() {
            val (decision, _) = SlidingWindowAlgorithm.check(policy, emptyList(), now)

            assertThat(decision.allowed).isTrue()
            assertThat(decision.remaining).isEqualTo(2)
        }

        @Test
        fun `should reject when limit is reached`() {
            val full = listOf(
                now.minusSeconds(10),
                now.minusSeconds(5),
                now.minusSeconds(1)
            )
            val (decision, _) = SlidingWindowAlgorithm.check(policy, full, now)

            assertThat(decision.allowed).isFalse()
            assertThat(decision.remaining).isEqualTo(0)
        }

        @Test
        fun `should not append timestamp on reject`() {
            val full = listOf(now.minusSeconds(10), now.minusSeconds(5), now.minusSeconds(1))
            val (_, newTimestamps) = SlidingWindowAlgorithm.check(policy, full, now)

            assertThat(newTimestamps).hasSize(3)
            assertThat(newTimestamps).doesNotContain(now)
        }

        @Test
        fun `should append current timestamp on allow`() {
            val (_, newTimestamps) = SlidingWindowAlgorithm.check(policy, emptyList(), now)

            assertThat(newTimestamps).contains(now)
        }

        @Test
        fun `should reject all requests when limit is zero`() {
            val zeroPolicy = RateLimitPolicy(limit = 0, windowSeconds = 60, strategy = RateLimitStrategy.SlidingWindow)
            val (decision, _) = SlidingWindowAlgorithm.check(zeroPolicy, emptyList(), now)

            assertThat(decision.allowed).isFalse()
            assertThat(decision.remaining).isEqualTo(0)
        }
    }

    @Nested
    inner class `Window rolling` {

        @Test
        fun `should prune timestamps older than window`() {
            val oldEntry = now.minusSeconds(61)
            val recentEntry = now.minusSeconds(30)
            val timestamps = listOf(oldEntry, recentEntry)

            val (decision, newTimestamps) = SlidingWindowAlgorithm.check(policy, timestamps, now)

            assertThat(decision.allowed).isTrue()
            assertThat(newTimestamps).doesNotContain(oldEntry)
            assertThat(newTimestamps).contains(recentEntry)
        }

        @Test
        fun `should allow again after old entries are pruned below limit`() {
            val old1 = now.minusSeconds(61)
            val old2 = now.minusSeconds(65)
            val recent = now.minusSeconds(10)
            val timestamps = listOf(old2, old1, recent)

            val (decision, _) = SlidingWindowAlgorithm.check(policy, timestamps, now)

            assertThat(decision.allowed).isTrue()
        }
    }

    @Nested
    inner class `CHK005 inclusive boundary` {

        @Test
        fun `should keep entry at exactly cutoff boundary (inclusive)`() {
            val atCutoff = now.minusSeconds(60)
            val (_, newTimestamps) = SlidingWindowAlgorithm.check(policy, listOf(atCutoff), now)

            assertThat(newTimestamps).contains(atCutoff)
        }

        @Test
        fun `should prune entry one nanosecond before cutoff (strict)`() {
            val justBeforeCutoff = now.minusSeconds(60).minusNanos(1)
            val (_, newTimestamps) = SlidingWindowAlgorithm.check(policy, listOf(justBeforeCutoff), now)

            assertThat(newTimestamps).doesNotContain(justBeforeCutoff)
        }
    }

    @Nested
    inner class `Equal timestamps` {

        @Test
        fun `should count equal timestamps as distinct entries`() {
            val ts = now.minusSeconds(5)
            val timestamps = listOf(ts, ts, ts)

            val (decision, _) = SlidingWindowAlgorithm.check(policy, timestamps, now)

            assertThat(decision.allowed).isFalse()
        }
    }

    @Nested
    inner class `CHK006 resetAt semantics` {

        @Test
        fun `should return resetAt as now when remaining greater than zero`() {
            val (decision, _) = SlidingWindowAlgorithm.check(policy, emptyList(), now)

            assertThat(decision.remaining).isGreaterThan(0)
            assertThat(decision.resetAt).isEqualTo(now)
        }

        @Test
        fun `should return resetAt as oldest plus windowSeconds when window is full`() {
            val oldest = now.minusSeconds(30)
            val full = listOf(oldest, now.minusSeconds(20), now.minusSeconds(10))

            val (decision, _) = SlidingWindowAlgorithm.check(policy, full, now)

            assertThat(decision.allowed).isFalse()
            assertThat(decision.resetAt).isEqualTo(oldest.plusSeconds(60))
        }

        @Test
        fun `should return resetAt as now when window is empty`() {
            val (decision, _) = SlidingWindowAlgorithm.check(
                RateLimitPolicy(limit = 0, windowSeconds = 60, strategy = RateLimitStrategy.SlidingWindow),
                emptyList(),
                now
            )

            assertThat(decision.resetAt).isEqualTo(now)
        }
    }
}
