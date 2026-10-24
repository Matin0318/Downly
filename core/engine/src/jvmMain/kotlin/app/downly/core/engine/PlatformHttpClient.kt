package app.downly.core.engine

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Authenticator
import okhttp3.ConnectionPool
import okhttp3.Credentials
import okhttp3.Dispatcher
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

internal actual fun createPlatformHttpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient {
    DownlyProxyAuth.install()
    return HttpClient(OkHttp) {
        config(this)
        engine {
            this.config {
                proxySelector(DownlyProxySelector)
                proxyAuthenticator(
                    Authenticator { _, response ->
                        val proxy = ProxyHolder.current
                        if (!proxy.enabled || proxy.user.isBlank() || response.request.header("Proxy-Authorization") != null) {
                            null
                        } else {
                            response.request.newBuilder()
                                .header("Proxy-Authorization", Credentials.basic(proxy.user, proxy.password))
                                .build()
                        }
                    },
                )

                // Speed. OkHttp allows only 5 requests per host by default, so 8 or 16 connections to one
                // server would mostly wait in line. Lift that limit.
                dispatcher(
                    Dispatcher().apply {
                        maxRequests = 512
                        maxRequestsPerHost = 256
                    },
                )
                connectionPool(ConnectionPool(64, 5, TimeUnit.MINUTES))
                // HTTP/2 would send every part of a file over ONE connection. Many servers limit the speed of a
                // single connection, so use HTTP/1.1: each part gets its own connection and speeds add up.
                protocols(listOf(Protocol.HTTP_1_1))
                socketFactory(TunedSocketFactory())
                retryOnConnectionFailure(true)
            }
        }
    }
}
