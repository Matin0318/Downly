package app.downly.core.engine

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent

/** Creates the shared HTTP client (OkHttp engine on Android and Desktop JVM). */
object DownlyHttpClient {
    fun create(): HttpClient = createPlatformHttpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }
        install(UserAgent) { agent = "Downly/0.1" }
    }
}

internal expect fun createPlatformHttpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient
