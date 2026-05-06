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

class LimitsRouteIT {

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
    fun `POST creates policy and returns 201`() = testApplication {
        application { module(testConfig) }

        val response = client.post("/v1/limits") {
            contentType(ContentType.Application.Json)
            setBody("""{"key":"tenant-A","limit":100,"windowSeconds":60,"strategy":"FIXED_WINDOW"}""")
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.Created)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertThat(body["key"]!!.jsonPrimitive.content).isEqualTo("tenant-A")
        assertThat(body["limit"]!!.jsonPrimitive.int).isEqualTo(100)
        assertThat(body["windowSeconds"]!!.jsonPrimitive.int).isEqualTo(60)
        assertThat(body["strategy"]!!.jsonPrimitive.content).isEqualTo("FIXED_WINDOW")
    }

    @Test
    fun `POST returns 400 for blank key`() = testApplication {
        application { module(testConfig) }

        val response = client.post("/v1/limits") {
            contentType(ContentType.Application.Json)
            setBody("""{"key":"","limit":100,"windowSeconds":60,"strategy":"FIXED_WINDOW"}""")
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertThat(body["error"]!!.jsonPrimitive.content).isEqualTo("key must not be blank")
    }

    @Test
    fun `POST returns 400 for negative limit`() = testApplication {
        application { module(testConfig) }

        val response = client.post("/v1/limits") {
            contentType(ContentType.Application.Json)
            setBody("""{"key":"tenant-A","limit":-1,"windowSeconds":60,"strategy":"FIXED_WINDOW"}""")
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertThat(body["error"]!!.jsonPrimitive.content).isEqualTo("limit must be >= 0")
    }

    @Test
    fun `POST returns 400 for zero windowSeconds`() = testApplication {
        application { module(testConfig) }

        val response = client.post("/v1/limits") {
            contentType(ContentType.Application.Json)
            setBody("""{"key":"tenant-A","limit":100,"windowSeconds":0,"strategy":"FIXED_WINDOW"}""")
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertThat(body["error"]!!.jsonPrimitive.content).isEqualTo("windowSeconds must be > 0")
    }

    @Test
    fun `POST returns 400 for unknown strategy`() = testApplication {
        application { module(testConfig) }

        val response = client.post("/v1/limits") {
            contentType(ContentType.Application.Json)
            setBody("""{"key":"tenant-A","limit":100,"windowSeconds":60,"strategy":"UNKNOWN"}""")
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertThat(body["error"]!!.jsonPrimitive.content)
            .isEqualTo("strategy must be one of: FIXED_WINDOW, SLIDING_WINDOW, TOKEN_BUCKET")
    }

    @Test
    fun `GET returns policy when exists`() = testApplication {
        application { module(testConfig) }
        client.post("/v1/limits") {
            contentType(ContentType.Application.Json)
            setBody("""{"key":"tenant-A","limit":100,"windowSeconds":60,"strategy":"FIXED_WINDOW"}""")
        }

        val response = client.get("/v1/limits/tenant-A")

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertThat(body["key"]!!.jsonPrimitive.content).isEqualTo("tenant-A")
        assertThat(body["limit"]!!.jsonPrimitive.int).isEqualTo(100)
        assertThat(body["windowSeconds"]!!.jsonPrimitive.int).isEqualTo(60)
        assertThat(body["strategy"]!!.jsonPrimitive.content).isEqualTo("FIXED_WINDOW")
    }

    @Test
    fun `GET returns 404 when not found`() = testApplication {
        application { module(testConfig) }

        val response = client.get("/v1/limits/unknown")

        assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertThat(body["error"]!!.jsonPrimitive.content).isEqualTo("policy not found for key: unknown")
    }

    @Test
    fun `DELETE returns 204 and policy is gone`() = testApplication {
        application { module(testConfig) }
        client.post("/v1/limits") {
            contentType(ContentType.Application.Json)
            setBody("""{"key":"tenant-A","limit":100,"windowSeconds":60,"strategy":"FIXED_WINDOW"}""")
        }

        val deleteResponse = client.delete("/v1/limits/tenant-A")
        assertThat(deleteResponse.status).isEqualTo(HttpStatusCode.NoContent)

        val getResponse = client.get("/v1/limits/tenant-A")
        assertThat(getResponse.status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun `DELETE returns 404 when not found`() = testApplication {
        application { module(testConfig) }

        val response = client.delete("/v1/limits/ghost")

        assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertThat(body["error"]!!.jsonPrimitive.content).isEqualTo("policy not found for key: ghost")
    }

    @Test
    fun `POST returns 400 for key exceeding 512 characters`() = testApplication {
        application { module(testConfig) }
        val longKey = "a".repeat(513)

        val response = client.post("/v1/limits") {
            contentType(ContentType.Application.Json)
            setBody("""{"key":"$longKey","limit":100,"windowSeconds":60,"strategy":"FIXED_WINDOW"}""")
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertThat(body["error"]!!.jsonPrimitive.content).isEqualTo("key must not exceed 512 characters")
    }

    @Test
    fun `POST with limit=0 is valid and check always rejects`() = testApplication {
        application { module(testConfig) }

        val postResponse = client.post("/v1/limits") {
            contentType(ContentType.Application.Json)
            setBody("""{"key":"limit-zero-test","limit":0,"windowSeconds":60,"strategy":"FIXED_WINDOW"}""")
        }
        assertThat(postResponse.status).isEqualTo(HttpStatusCode.Created)

        val checkResponse = client.post("/v1/check") {
            contentType(ContentType.Application.Json)
            setBody("""{"key":"limit-zero-test"}""")
        }
        assertThat(checkResponse.status).isEqualTo(HttpStatusCode.OK)
        val body = Json.parseToJsonElement(checkResponse.bodyAsText()).jsonObject
        assertThat(body["allowed"]!!.jsonPrimitive.boolean).isFalse()
        assertThat(body["remaining"]!!.jsonPrimitive.int).isEqualTo(0)
    }
}
