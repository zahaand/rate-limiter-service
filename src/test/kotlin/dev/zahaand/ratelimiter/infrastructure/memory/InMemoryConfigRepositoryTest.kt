package dev.zahaand.ratelimiter.infrastructure.memory

import dev.zahaand.ratelimiter.domain.model.RateLimitPolicy
import dev.zahaand.ratelimiter.domain.model.RateLimitStrategy
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import kotlin.test.Test

class InMemoryConfigRepositoryTest {

    private val repo = InMemoryConfigRepository()
    private val policy = RateLimitPolicy(limit = 10, windowSeconds = 60, strategy = RateLimitStrategy.FixedWindow)

    @Nested
    inner class `Save and get` {

        @Test
        fun `should return exact policy after save`() = runTest {
            repo.save("key1", policy)
            assertThat(repo.get("key1")).isEqualTo(policy)
        }

        @Test
        fun `should return null for absent key`() = runTest {
            assertThat(repo.get("missing")).isNull()
        }

        @Test
        fun `should return latest policy after overwrite`() = runTest {
            val updated = RateLimitPolicy(limit = 99, windowSeconds = 30, strategy = RateLimitStrategy.SlidingWindow)
            repo.save("key2", policy)
            repo.save("key2", updated)
            assertThat(repo.get("key2")).isEqualTo(updated)
        }
    }

    @Nested
    inner class Delete {

        @Test
        fun `should return true and remove policy when key exists`() = runTest {
            repo.save("key3", policy)
            assertThat(repo.delete("key3")).isTrue()
            assertThat(repo.get("key3")).isNull()
        }

        @Test
        fun `should return false when deleting absent key`() = runTest {
            assertThat(repo.delete("nonexistent")).isFalse()
        }
    }
}
