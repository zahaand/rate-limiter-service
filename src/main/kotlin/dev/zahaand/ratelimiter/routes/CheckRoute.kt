package dev.zahaand.ratelimiter.routes

import dev.zahaand.ratelimiter.domain.model.RateLimitKey
import dev.zahaand.ratelimiter.routes.dto.CheckRequest
import dev.zahaand.ratelimiter.routes.dto.CheckResponse
import dev.zahaand.ratelimiter.routes.dto.ErrorResponse
import dev.zahaand.ratelimiter.service.RateLimiterService
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant

fun Route.checkRoute(service: RateLimiterService) {
    post("/check", {
        operationId = "checkRateLimit"
        summary = "Check rate limit for a client key"
        tags("Rate Limit")
        description = "Returns a rate limit decision for the given key. " +
            "If no policy is configured for the key, the service default policy applies. " +
            "Always returns HTTP 200 — inspect the `allowed` field for the actual decision."
        request {
            body<CheckRequest> {
                required = true
                example("tenant-api") { value = CheckRequest("tenant-acme-prod") }
                example("user-session") { value = CheckRequest("user-uuid-f47ac10b") }
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Rate limit decision returned. Always 200 for a structurally valid request — check the `allowed` field."
                body<CheckResponse> {
                    example("allowed") { value = CheckResponse(true, 42, Instant.parse("2026-05-12T10:00:00Z")) }
                    example("rejected") { value = CheckResponse(false, 0, Instant.parse("2026-05-12T10:00:00Z")) }
                }
            }
            HttpStatusCode.BadRequest to {
                description = "Validation error — blank key, key exceeds 512 characters, or malformed JSON body."
                body<ErrorResponse> {
                    example("blank-key") { value = ErrorResponse("key must not be blank") }
                    example("key-too-long") { value = ErrorResponse("key must not exceed 512 characters") }
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
