package app.downly.core.engine

data class SegmentProgress(val segment: Segment, val downloaded: Long = 0L) {
    val isDone: Boolean get() = downloaded >= segment.length
}

/**
 * Full state of a download. Persist this to resume after pause, crash or reboot.
 */
data class DownloadSnapshot(val totalBytes: Long, val segments: List<SegmentProgress>) {
    val downloadedBytes: Long get() = segments.sumOf { it.downloaded }
    val isComplete: Boolean get() = segments.all { it.isDone }

    companion object {
        fun initial(info: RemoteFileInfo, maxConnections: Int): DownloadSnapshot {
            if (info.totalBytes <= 0) throw DownloadException("File size is unknown or zero")
            val segments = if (info.acceptsRanges) {
                SegmentPlanner.plan(info.totalBytes, maxConnections)
            } else {
                listOf(Segment(0, 0L, info.totalBytes - 1))
            }
            return DownloadSnapshot(info.totalBytes, segments.map { SegmentProgress(it) })
        }
    }
}
