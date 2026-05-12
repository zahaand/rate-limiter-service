package dev.zahaand.ratelimiter.routes

import dev.zahaand.ratelimiter.domain.model.RateLimitPolicy
import dev.zahaand.ratelimiter.domain.model.RateLimitStrategy
import dev.zahaand.ratelimiter.domain.port.ConfigRepository
import dev.zahaand.ratelimiter.routes.dto.ErrorResponse
import dev.zahaand.ratelimiter.routes.dto.LimitConfigRequest
import dev.zahaand.ratelimiter.routes.dto.LimitConfigResponse
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private val validStrategies = setOf("FIXED_WINDOW", "SLIDING_WINDOW", "TOKEN_BUCKET")

fun Route.limitsRoute(configRepository: ConfigRepository) {
    post("/limits", {
        operationId = "upsertPolicy"
        summary = "Create or replace a rate limit policy"
        tags("Policy Management")
        description = "Stores a rate limit policy for the given key. Replaces any existing " +
            "policy for the same key. Returns 201 with the stored values on success."
        request {
            body<LimitConfigRequest> {
                required = true
                example("fixed-window-policy") {
                    value = LimitConfigRequest("tenant-A", 100, 60, "FIXED_WINDOW")
                }
                example("sliding-window-policy") {
                    value = LimitConfigRequest("payment-api", 50, 30, "SLIDING_WINDOW")
                }
            }
        }
        response {
            HttpStatusCode.Created to {
                description = "Policy stored. Body echoes the stored values exactly."
                body<LimitConfigResponse> {
                    example("tenant-a-policy") {
                        value = LimitConfigResponse("tenant-A", 100, 60, "FIXED_WINDOW")
                    }
                }
            }
            HttpStatusCode.BadRequest to {
                description = "Validation error — blank key, key exceeds 512 characters, negative limit, zero or negative windowSeconds, or unknown strategy."
                body<ErrorResponse> {
                    example("blank-key") { value = ErrorResponse("key must not be blank") }
                    example("key-too-long") { value = ErrorResponse("key must not exceed 512 characters") }
                    example("negative-limit") { value = ErrorResponse("limit must be >= 0") }
                    example("zero-window") { value = ErrorResponse("windowSeconds must be > 0") }
                    example("bad-strategy") {
                        value = ErrorResponse("strategy must be one of: FIXED_WINDOW, SLIDING_WINDOW, TOKEN_BUCKET")
                    }
                }
            }
            HttpStatusCode.UnsupportedMediaType to {
                description = "Content-Type is not application/json."
                body<ErrorResponse> {
                    example("wrong-content-type") { value = ErrorResponse("unsupported media type") }
                }
            }
            HttpStatusCode.InternalServerError to {
                description = "Unhandled server error. Stack traces are never exposed."
                body<ErrorResponse> {
                    example("internal") { value = ErrorResponse("internal server error") }
                }
            }
        }
    }) {
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

    get("/limits/{key}", {
        operationId = "getPolicy"
        summary = "Retrieve a rate limit policy by key"
        tags("Policy Management")
        description = "Returns the stored rate limit policy for the given key. " +
            "Returns 404 if no policy has been explicitly configured for the key. " +
            "The service default policy is not returned by this endpoint."
        request {
            pathParameter<String>("key") {
                description = "The client key whose policy to retrieve."
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Policy found."
                body<LimitConfigResponse> {
                    example("tenant-a-policy") {
                        value = LimitConfigResponse("tenant-A", 100, 60, "FIXED_WINDOW")
                    }
                }
            }
            HttpStatusCode.NotFound to {
                description = "No policy configured for the given key."
                body<ErrorResponse> {
                    example("not-found") { value = ErrorResponse("policy not found for key: tenant-A") }
                }
            }
            HttpStatusCode.InternalServerError to {
                description = "Unhandled server error. Stack traces are never exposed."
                body<ErrorResponse> {
                    example("internal") { value = ErrorResponse("internal server error") }
                }
            }
        }
    }) {
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

    delete("/limits/{key}", {
        operationId = "deletePolicy"
        summary = "Delete a rate limit policy by key"
        tags("Policy Management")
        description = "Deletes the stored rate limit policy for the given key. " +
            "Returns 204 on success with no response body. " +
            "Returns 404 if no policy was configured for the key."
        request {
            pathParameter<String>("key") {
                description = "The client key whose policy to delete."
            }
        }
        response {
            HttpStatusCode.NoContent to {
                description = "Policy deleted successfully. No response body."
            }
            HttpStatusCode.NotFound to {
                description = "No policy configured for the given key."
                body<ErrorResponse> {
                    example("not-found") { value = ErrorResponse("policy not found for key: tenant-A") }
                }
            }
            HttpStatusCode.InternalServerError to {
                description = "Unhandled server error. Stack traces are never exposed."
                body<ErrorResponse> {
                    example("internal") { value = ErrorResponse("internal server error") }
                }
            }
        }
    }) {
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
