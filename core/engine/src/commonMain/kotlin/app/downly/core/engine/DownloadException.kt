package app.downly.core.engine

/** Error raised by the download engine. [retryable] tells whether trying again can help. */
class DownloadException(
    message: String,
    val retryable: Boolean = false,
    cause: Throwable? = null,
) : Exception(message, cause)

internal fun isRetryableStatus(status: Int): Boolean = status >= 500 || status == 408 || status == 429
