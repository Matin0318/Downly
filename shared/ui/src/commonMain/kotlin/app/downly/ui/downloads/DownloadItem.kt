package app.downly.ui.downloads

import app.downly.core.model.DownloadStatus

/** What the UI shows for one download row. */
data class DownloadItem(
    val id: Long,
    val url: String,
    val fileName: String,
    val totalBytes: Long = -1L,
    val downloadedBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val error: String? = null,
    val destinationPath: String? = null,
    val segmentProgress: List<Float> = emptyList(),
    val createdAt: Long = 0L,
    /** Probed and ready, but waiting for a free download slot. */
    val waiting: Boolean = false,
    val priority: Int = 0,
    val speedLimitKBps: Int = 0,
    val queueId: Long = app.downly.data.MAIN_QUEUE_ID,
    val mirrors: List<String> = emptyList(),
    val completedAt: Long = 0L,
) {
    val progress: Float?
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null

    val remainingSeconds: Long?
        get() = if (status == DownloadStatus.DOWNLOADING && speedBytesPerSec > 0 && totalBytes > 0) {
            (totalBytes - downloadedBytes) / speedBytesPerSec
        } else {
            null
        }

    val isActive: Boolean
        get() = status == DownloadStatus.DOWNLOADING || status == DownloadStatus.QUEUED
}
