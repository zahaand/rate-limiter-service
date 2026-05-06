package dev.zahaand.ratelimiter

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addResourceSource
import dev.zahaand.ratelimiter.infrastructure.config.AppConfig
import dev.zahaand.ratelimiter.infrastructure.redis.RedisConfigRepository
import dev.zahaand.ratelimiter.infrastructure.redis.RedisRateLimitRepository
import dev.zahaand.ratelimiter.routes.checkRoute
import dev.zahaand.ratelimiter.routes.dto.ErrorResponse
import dev.zahaand.ratelimiter.service.RateLimiterService
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.coroutines
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.UnsupportedMediaType
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main() {
    embeddedServer(
        factory = Netty,
        configure = {
            connector {
                host = "0.0.0.0"
                port = 8080
            }
            shutdownGracePeriod = 5_000L
            shutdownTimeout = 5_000L
        },
    ) { module() }.start(wait = true)
}

@OptIn(ExperimentalLettuceCoroutinesApi::class)
fun Application.module(overrideConfig: AppConfig? = null) {
    val appConfig = overrideConfig ?: ConfigLoaderBuilder.default()
        .addResourceSource("/application.yaml")
        .build()
        .loadConfigOrThrow<AppConfig>()

    val redisClient = RedisClient.create(RedisURI.create(appConfig.redis.host, appConfig.redis.port))
    val connection = redisClient.connect()
    val commands = connection.coroutines()

    val rateLimitRepository = RedisRateLimitRepository(commands)
    val configRepository = RedisConfigRepository(commands)
    val rateLimiterService = RateLimiterService(rateLimitRepository, configRepository, appConfig)

    install(ContentNegotiation) { json() }
    install(StatusPages) {
        exception<UnsupportedMediaTypeException> { call, _ ->
            call.respond(UnsupportedMediaType, ErrorResponse("unsupported media type"))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(BadRequest, ErrorResponse("invalid request body"))
        }
        exception<JsonConvertException> { call, _ ->
            call.respond(BadRequest, ErrorResponse("invalid request body"))
        }
        exception<Throwable> { call, _ ->
            call.respond(InternalServerError, ErrorResponse("internal server error"))
        }
    }

    routing {
        route("/v1") {
            checkRoute(rateLimiterService)
        }
    }

    monitor.subscribe(ApplicationStopped) {
        connection.close()
        redisClient.shutdown()
    }
}
