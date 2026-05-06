package dev.zahaand.ratelimiter

import dev.zahaand.ratelimiter.infrastructure.config.AppConfig
import dev.zahaand.ratelimiter.routes.dto.ErrorResponse
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

fun main() {
    embeddedServer(
        factory = Netty,
        port = 8080,
        host = "0.0.0.0",
    ) { module() }.start(wait = true)
}

fun Application.module(overrideConfig: AppConfig? = null) {
    install(ContentNegotiation) { json() }
    install(StatusPages) {
        exception<Throwable> { call, _ ->
            call.respond(InternalServerError, ErrorResponse("internal server error"))
        }
    }
}
