package dev.zahaand.ratelimiter.infrastructure.memory

import dev.zahaand.ratelimiter.domain.model.RateLimitPolicy
import dev.zahaand.ratelimiter.domain.model.RateLimitStrategy
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test

class InMemoryRateLimitRepositoryTest {

    private val fixedNow = Instant.ofEpochSecond(1_000_000)
    private val clock = Clock.fixed(fixedNow, ZoneOffset.UTC)
    private val repo = InMemoryRateLimitRepository(clock)

    @Nested
    inner class `Fixed Window dispatch` {

        @Test
        fun `should allow requests up to limit`() = runTest {
            val policy = RateLimitPolicy(limit = 3, windowSeconds = 60, strategy = RateLimitStrategy.FixedWindow)

            val r1 = repo.check("key", policy, fixedNow)
            val r2 = repo.check("key", policy, fixedNow)
            val r3 = repo.check("key", policy, fixedNow)
            val r4 = repo.check("key", policy, fixedNow)

            assertThat(r1.allowed).isTrue()
            assertThat(r2.allowed).isTrue()
            assertThat(r3.allowed).isTrue()
            assertThat(r4.allowed).isFalse()
        }
    }

    @Nested
    inner class `Concurrency` {

        @Test
        fun `should not exceed limit under concurrent load`() = runTest {
            val policy = RateLimitPolicy(limit = 50, windowSeconds = 60, strategy = RateLimitStrategy.FixedWindow)
            val freshRepo = InMemoryRateLimitRepository(clock)

            val results = List(100) {
                async { freshRepo.check("key", policy, fixedNow) }
            }.awaitAll()

            assertThat(results.count { it.allowed }).isEqualTo(50)
            assertThat(results.count { !it.allowed }).isEqualTo(50)
        }

        @Test
        fun `should maintain isolation between different keys`() = runTest {
            val policy = RateLimitPolicy(limit = 1, windowSeconds = 60, strategy = RateLimitStrategy.FixedWindow)
            val freshRepo = InMemoryRateLimitRepository(clock)

            val keyA = freshRepo.check("keyA", policy, fixedNow)
            val keyB = freshRepo.check("keyB", policy, fixedNow)

            assertThat(keyA.allowed).isTrue()
            assertThat(keyB.allowed).isTrue()
        }
    }
}
