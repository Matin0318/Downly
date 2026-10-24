package app.downly.core.engine

enum class ProxyKind { NONE, HTTP, SOCKS }

/** A proxy the user typed in Settings. Every download (HTTP, HLS) goes through it when [enabled]. */
data class ProxyConfig(
    val kind: ProxyKind = ProxyKind.NONE,
    val host: String = "",
    val port: Int = 0,
    val user: String = "",
    val password: String = "",
) {
    val enabled: Boolean get() = kind != ProxyKind.NONE && host.isNotBlank() && port in 1..65535
}

/** The proxy currently in use. The HTTP client reads it for every new connection, so changing it applies at once. */
object ProxyHolder {
    var current: ProxyConfig = ProxyConfig()
}
