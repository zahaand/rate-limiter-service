package dev.zahaand.ratelimiter.integration

import dev.zahaand.ratelimiter.infrastructure.config.AppConfig
import dev.zahaand.ratelimiter.infrastructure.config.RateLimitDefaults
import dev.zahaand.ratelimiter.infrastructure.config.RedisConfig
import dev.zahaand.ratelimiter.infrastructure.config.ServerConfig
import dev.zahaand.ratelimiter.module
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test

class ConcurrentCheckIT {

    private val testConfig = AppConfig(
        server = ServerConfig(0),
        redis = RedisConfig(RedisTestContainer.host, RedisTestContainer.port),
        rateLimit = RateLimitDefaults()
    )

    @BeforeEach
    fun setUp() {
        val redisClient = RedisClient.create(RedisURI.create(RedisTestContainer.host, RedisTestContainer.port))
        val connection = redisClient.connect()
        connection.sync().flushdb()
        connection.close()
        redisClient.shutdown()
    }

    @Test
    fun `exactly 10 of 50 concurrent requests are allowed when limit is 10`() = testApplication {
        application { module(testConfig) }

        val redisClient = RedisClient.create(RedisURI.create(RedisTestContainer.host, RedisTestContainer.port))
        val connection = redisClient.connect()
        connection.sync().hset(
            "config:concurrent-test",
            mapOf("limit" to "10", "windowSeconds" to "60", "strategy" to "FIXED_WINDOW")
        )
        connection.close()
        redisClient.shutdown()

        val results = coroutineScope {
            (1..50).map {
                async {
                    client.post("/v1/check") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"key":"concurrent-test"}""")
                    }
                }
            }.awaitAll()
        }

        assertThat(results.all { it.status == HttpStatusCode.OK }).isTrue()
        val bodies = results.map { Json.parseToJsonElement(it.bodyAsText()).jsonObject }
        assertThat(bodies.count { it["allowed"]!!.jsonPrimitive.boolean }).isEqualTo(10)
        assertThat(bodies.count { !it["allowed"]!!.jsonPrimitive.boolean }).isEqualTo(40)
    }
}
