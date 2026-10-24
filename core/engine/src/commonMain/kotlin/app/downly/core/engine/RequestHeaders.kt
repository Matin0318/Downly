package app.downly.core.engine

/** Cleans user supplied request headers so they cannot break the download. */
object RequestHeaders {
    private val blocked = setOf("range", "host", "content-length", "accept-encoding", "connection", "transfer-encoding")

    fun sanitize(headers: Map<String, String>): Map<String, String> =
        headers.filter { (name, value) ->
            name.isNotBlank() &&
                name.none { it.isWhitespace() || it == ':' } &&
                name.lowercase() !in blocked &&
                value.none { it == '\n' || it == '\r' }
        }
}
