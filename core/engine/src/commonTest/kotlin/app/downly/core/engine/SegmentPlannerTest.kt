package app.downly.core.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SegmentPlannerTest {
    private val mb = 1024L * 1024

    @Test
    fun splitsLargeFileIntoRequestedConnections() {
        val segments = SegmentPlanner.plan(totalBytes = 10 * mb, maxConnections = 4)
        assertEquals(4, segments.size)
        assertEquals(10 * mb, segments.sumOf { it.length })
    }

    @Test
    fun segmentsAreContiguousWithoutGaps() {
        val segments = SegmentPlanner.plan(totalBytes = 10 * mb + 3, maxConnections = 8)
        assertEquals(0L, segments.first().start)
        assertEquals(10 * mb + 2, segments.last().endInclusive)
        segments.zipWithNext { a, b -> assertEquals(a.endInclusive + 1, b.start) }
    }

    @Test
    fun smallFileUsesSingleSegment() {
        val segments = SegmentPlanner.plan(totalBytes = 500, maxConnections = 8)
        assertEquals(1, segments.size)
        assertEquals(500L, segments[0].length)
    }

    @Test
    fun rejectsInvalidInput() {
        assertFailsWith<IllegalArgumentException> { SegmentPlanner.plan(0, 4) }
        assertFailsWith<IllegalArgumentException> { SegmentPlanner.plan(100, 0) }
    }
}
