package dev.zahaand.ratelimiter.routes

import dev.zahaand.ratelimiter.domain.model.RateLimitPolicy
import dev.zahaand.ratelimiter.domain.model.RateLimitStrategy
import dev.zahaand.ratelimiter.domain.port.ConfigRepository
import dev.zahaand.ratelimiter.routes.dto.ErrorResponse
import dev.zahaand.ratelimiter.routes.dto.LimitConfigRequest
import dev.zahaand.ratelimiter.routes.dto.LimitConfigResponse
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private val validStrategies = setOf("FIXED_WINDOW", "SLIDING_WINDOW", "TOKEN_BUCKET")

fun Route.limitsRoute(configRepository: ConfigRepository) {
    post("/limits") {
        val request = call.receive<LimitConfigRequest>()
        if (request.key.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("key must not be blank"))
            return@post
        }
        if (request.key.length > 512) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("key must not exceed 512 characters"))
            return@post
        }
        if (request.limit < 0) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("limit must be >= 0"))
            return@post
        }
        if (request.windowSeconds <= 0) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("windowSeconds must be > 0"))
            return@post
        }
        if (request.strategy.uppercase() !in validStrategies) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("strategy must be one of: FIXED_WINDOW, SLIDING_WINDOW, TOKEN_BUCKET"))
            return@post
        }
        val policy = RateLimitPolicy(
            limit = request.limit,
            windowSeconds = request.windowSeconds,
            strategy = RateLimitStrategy.fromConfigName(request.strategy)
        )
        configRepository.save(request.key, policy)
        call.respond(HttpStatusCode.Created, LimitConfigResponse(
            key = request.key,
            limit = policy.limit,
            windowSeconds = policy.windowSeconds,
            strategy = policy.strategy.configName
        ))
    }

    get("/limits/{key}") {
        val key = call.parameters["key"] ?: return@get call.respond(
            HttpStatusCode.BadRequest, ErrorResponse("missing key")
        )
        val policy = configRepository.get(key)
        if (policy == null) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("policy not found for key: $key"))
        } else {
            call.respond(HttpStatusCode.OK, LimitConfigResponse(
                key = key,
                limit = policy.limit,
                windowSeconds = policy.windowSeconds,
                strategy = policy.strategy.configName
            ))
        }
    }

    delete("/limits/{key}") {
        val key = call.parameters["key"] ?: return@delete call.respond(
            HttpStatusCode.BadRequest, ErrorResponse("missing key")
        )
        if (configRepository.delete(key)) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("policy not found for key: $key"))
        }
    }
}
