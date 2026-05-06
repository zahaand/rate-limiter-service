package dev.zahaand.ratelimiter.routes

import dev.zahaand.ratelimiter.routes.dto.HealthResponse
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalLettuceCoroutinesApi::class)
fun Route.healthRoute(commands: RedisCoroutinesCommands<String, String>) {
    get("/health") {
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
