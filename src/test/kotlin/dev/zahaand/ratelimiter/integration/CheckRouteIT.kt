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
import kotlinx.serialization.json.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test

class CheckRouteIT {

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

    private fun setPolicy(key: String, limit: Int, windowSeconds: Int, strategy: String = "FIXED_WINDOW") {
        val redisClient = RedisClient.create(RedisURI.create(RedisTestContainer.host, RedisTestContainer.port))
        val connection = redisClient.connect()
        connection.sync().hset(
            "config:$key",
            mapOf("limit" to limit.toString(), "windowSeconds" to windowSeconds.toString(), "strategy" to strategy)
        )
        connection.close()
        redisClient.shutdown()
    }

    @Test
    fun `returns allowed=true when under limit`() = testApplication {
        application { module(testConfig) }
        setPolicy("user-123", limit = 3, windowSeconds = 10)

        val response = client.post("/v1/check") {
            contentType(ContentType.Application.Json)
            setBody("""{"key":"user-123"}""")
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertThat(body["allowed"]!!.jsonPrimitive.boolean).isTrue()
        assertThat(body["remaining"]!!.jsonPrimitive.int).isEqualTo(2)
    }

    @Test
    fun `returns allowed=false when limit exceeded`() = testApplication {
        application { module(testConfig) }
        setPolicy("user-123", limit = 3, windowSeconds = 10)

        repeat(3) {
            client.post("/v1/check") {
                contentType(ContentType.Application.Json)
                setBody("""{"key":"user-123"}""")
            }
        }

        val response = client.post("/v1/check") {
            contentType(ContentType.Application.Json)
            setBody("""{"key":"user-123"}""")
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertThat(body["allowed"]!!.jsonPrimitive.boolean).isFalse()
        assertThat(body["remaining"]!!.jsonPrimitive.int).isEqualTo(0)
    }

    @Test
    fun `returns 400 when key is blank`() = testApplication {
        application { module(testConfig) }

        val response = client.post("/v1/check") {
            contentType(ContentType.Application.Json)
            setBody("""{"key":""}""")
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertThat(body["error"]!!.jsonPrimitive.content).isEqualTo("key must not be blank")
    }

    @Test
    fun `uses default policy when no policy configured`() = testApplication {
        application { module(testConfig) }

        val response = client.post("/v1/check") {
            contentType(ContentType.Application.Json)
            setBody("""{"key":"new-key"}""")
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertThat(body["allowed"]!!.jsonPrimitive.boolean).isTrue()
        assertThat(body["resetAt"]).isNotNull()
    }
}
