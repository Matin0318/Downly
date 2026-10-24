package app.downly.core.model

enum class DownloadStatus { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED }

data class DownloadTask(
    val id: Long,
    val url: String,
    val fileName: String,
    val totalBytes: Long = -1L,
    val downloadedBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.QUEUED,
) {
    /** Progress between 0f and 1f, or null when the size is unknown. */
    val progress: Float?
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null
}
