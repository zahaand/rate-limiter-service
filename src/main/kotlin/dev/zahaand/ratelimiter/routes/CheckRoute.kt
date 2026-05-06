package dev.zahaand.ratelimiter.routes

import dev.zahaand.ratelimiter.domain.model.RateLimitKey
import dev.zahaand.ratelimiter.routes.dto.CheckRequest
import dev.zahaand.ratelimiter.routes.dto.CheckResponse
import dev.zahaand.ratelimiter.routes.dto.ErrorResponse
import dev.zahaand.ratelimiter.service.RateLimiterService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.checkRoute(service: RateLimiterService) {
    post("/check") {
        val request = call.receive<CheckRequest>()
        if (request.key.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("key must not be blank"))
            return@post
        }
        if (request.key.length > 512) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("key must not exceed 512 characters"))
            return@post
        }
        val decision = service.check(RateLimitKey(request.key))
        call.respond(HttpStatusCode.OK, CheckResponse(decision.allowed, decision.remaining, decision.resetAt))
    }
}
