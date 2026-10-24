package app.downly.core.engine

/**
 * Result of probing a URL before downloading.
 * [totalBytes] is -1 when the server does not report a size.
 */
data class RemoteFileInfo(
    val url: String,
    val finalUrl: String,
    val totalBytes: Long,
    val acceptsRanges: Boolean,
    val fileName: String,
    /** HTTP Basic `Authorization` value taken from the link (`user:pass@host`), or null. Never saved to disk. */
    val authorization: String? = null,
    /** Extra request headers (Cookie, Referer, User-Agent...). Sent only to the original host. */
    val headers: Map<String, String> = emptyMap(),
)
