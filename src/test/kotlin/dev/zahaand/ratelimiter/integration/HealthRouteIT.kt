package dev.zahaand.ratelimiter.integration

import dev.zahaand.ratelimiter.infrastructure.config.AppConfig
import dev.zahaand.ratelimiter.infrastructure.config.RateLimitDefaults
import dev.zahaand.ratelimiter.infrastructure.config.RedisConfig
import dev.zahaand.ratelimiter.infrastructure.config.ServerConfig
import dev.zahaand.ratelimiter.module
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.ktor.client.request.*
import org.testcontainers.DockerClientFactory
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test

class HealthRouteIT {

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
        unpauseRedis()
    }

    private fun pauseRedis() {
        DockerClientFactory.instance().client()
            .pauseContainerCmd(RedisTestContainer.container.containerId).exec()
    }

    private fun unpauseRedis() {
        runCatching {
            DockerClientFactory.instance().client()
                .unpauseContainerCmd(RedisTestContainer.container.containerId).exec()
        }
    }

    @Test
    fun `returns 200 UP when Redis available`() = testApplication {
        application { module(testConfig) }

        val response = client.get("/health")

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertThat(body["status"]!!.jsonPrimitive.content).isEqualTo("UP")
        assertThat(body["redis"]!!.jsonPrimitive.content).isEqualTo("UP")
    }

    @Test
    fun `returns 503 DOWN when Redis unavailable`() = testApplication {
        application { module(testConfig) }
        pauseRedis()

        try {
            val response = client.get("/health")

            assertThat(response.status).isEqualTo(HttpStatusCode.ServiceUnavailable)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertThat(body["status"]!!.jsonPrimitive.content).isEqualTo("DOWN")
            assertThat(body["redis"]!!.jsonPrimitive.content).isEqualTo("DOWN")
        } finally {
            unpauseRedis()
        }
    }
}
