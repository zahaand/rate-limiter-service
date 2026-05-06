package dev.zahaand.ratelimiter

import dev.zahaand.ratelimiter.infrastructure.config.AppConfig
import dev.zahaand.ratelimiter.routes.dto.ErrorResponse
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

fun Application.module(overrideConfig: AppConfig? = null) {
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
}
