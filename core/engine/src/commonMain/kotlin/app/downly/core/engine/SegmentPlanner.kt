package app.downly.core.engine

/** A byte range [start, endInclusive] of the file, matching HTTP `Range: bytes=start-end`. */
data class Segment(val index: Int, val start: Long, val endInclusive: Long) {
    val length: Long get() = endInclusive - start + 1
}

/** Splits a file into contiguous segments for parallel (multi-connection) download. */
object SegmentPlanner {
    const val DEFAULT_MIN_SEGMENT_BYTES: Long = 1L * 1024 * 1024

    fun plan(
        totalBytes: Long,
        maxConnections: Int,
        minSegmentBytes: Long = DEFAULT_MIN_SEGMENT_BYTES,
    ): List<Segment> {
        require(totalBytes > 0) { "totalBytes must be > 0" }
        require(maxConnections >= 1) { "maxConnections must be >= 1" }
        require(minSegmentBytes >= 1) { "minSegmentBytes must be >= 1" }

        val count = minOf(maxConnections.toLong(), maxOf(1L, totalBytes / minSegmentBytes)).toInt()
        val base = totalBytes / count
        val remainder = totalBytes % count

        val result = ArrayList<Segment>(count)
        var start = 0L
        for (i in 0 until count) {
            val size = base + if (i < remainder) 1 else 0
            result += Segment(i, start, start + size - 1)
            start += size
        }
        return result
    }
}
