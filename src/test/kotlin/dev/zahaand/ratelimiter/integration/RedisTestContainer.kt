package dev.zahaand.ratelimiter.integration

import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

object RedisTestContainer {
    val container: GenericContainer<*> =
        GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .also { it.start() }

    val host: String get() = container.host
    val port: Int get() = container.getMappedPort(6379)
}
