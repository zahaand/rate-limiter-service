package dev.zahaand.ratelimiter.routes

import dev.zahaand.ratelimiter.routes.dto.HealthResponse
import io.github.smiley4.ktoropenapi.get
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalLettuceCoroutinesApi::class)
fun Route.healthRoute(commands: RedisCoroutinesCommands<String, String>) {
    get("/health", {
        operationId = "getHealth"
        summary = "Check service and Redis health"
        tags("Observability")
        description = "Returns the operational status of the service and its Redis connection. " +
            "Redis health is determined by a PING with a 1-second timeout. " +
            "Returns 503 if Redis is unreachable."
        response {
            HttpStatusCode.OK to {
                description = "Service and Redis are operational."
                body<HealthResponse> {
                    example("healthy") { value = HealthResponse("UP", "UP") }
                }
            }
            HttpStatusCode.ServiceUnavailable to {
                description = "Redis is unreachable or PING timed out (1-second timeout)."
                body<HealthResponse> {
                    example("degraded") { value = HealthResponse("DOWN", "DOWN") }
                }
            }
        }
    }) {
        val redisStatus = try {
            withTimeout(1_000L) { commands.ping() }
            "UP"
        } catch (_: TimeoutCancellationException) {
            "DOWN"
        } catch (_: Exception) {
            "DOWN"
        }
        val status = if (redisStatus == "UP") "UP" else "DOWN"
        val httpStatus = if (status == "UP") HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
        call.respond(httpStatus, HealthResponse(status = status, redis = redisStatus))
    }
}
