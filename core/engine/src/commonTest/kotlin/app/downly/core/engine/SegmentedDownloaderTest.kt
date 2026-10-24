package app.downly.core.engine

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SegmentedDownloaderTest {
    private val mb = 1024 * 1024
    private val data = ByteArray(3 * mb + 123) { (it % 251).toByte() }
    private val requestedRanges = mutableListOf<String?>()
    private val rangesLock = Mutex()
    private val fs = FakeFileSystem()
    private val dest = "/downloads/data.bin".toPath()
    private val url = "https://example.com/files/data.bin"

    private fun downloader(supportsRange: Boolean = true): SegmentedDownloader {
        val engine = MockEngine { request ->
            val range = request.headers[HttpHeaders.Range]
            rangesLock.withLock { requestedRanges += range }
            if (range != null && supportsRange) {
                val m = Regex("bytes=(\\d+)-(\\d*)").matchEntire(range)!!
                val start = m.groupValues[1].toInt()
                val end = m.groupValues[2].ifEmpty { (data.size - 1).toString() }.toInt().coerceAtMost(data.size - 1)
                respond(
                    content = data.copyOfRange(start, end + 1),
                    status = HttpStatusCode.PartialContent,
                    headers = headersOf(HttpHeaders.ContentRange, "bytes $start-$end/${data.size}"),
                )
            } else {
                respond(
                    content = data,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentLength, data.size.toString()),
                )
            }
        }
        return SegmentedDownloader(HttpClient(engine), fs)
    }

    @Test
    fun probeDetectsRangeSupportSizeAndName() = runTest {
        val info = downloader().probe(url)
        assertTrue(info.acceptsRanges)
        assertEquals(data.size.toLong(), info.totalBytes)
        assertEquals("data.bin", info.fileName)
    }

    @Test
    fun probeDetectsMissingRangeSupport() = runTest {
        val info = downloader(supportsRange = false).probe(url)
        assertFalse(info.acceptsRanges)
        assertEquals(data.size.toLong(), info.totalBytes)
    }

    @Test
    fun segmentedDownloadProducesIdenticalFile() = runTest {
        val d = downloader()
        val info = d.probe(url)
        val result = d.download(info, dest, DownloadSnapshot.initial(info, 4)).last()
        assertTrue(result.isComplete)
        assertContentEquals(data, fs.read(dest) { readByteArray() })
        assertFalse(fs.exists("/downloads/data.bin.part".toPath()))
    }

    @Test
    fun downloadWorksWhenServerHasNoRangeSupport() = runTest {
        val d = downloader(supportsRange = false)
        val info = d.probe(url)
        val result = d.download(info, dest, DownloadSnapshot.initial(info, 4)).last()
        assertEquals(1, result.segments.size)
        assertContentEquals(data, fs.read(dest) { readByteArray() })
    }

    @Test
    fun resumeSkipsAlreadyDownloadedSegments() = runTest {
        val d = downloader()
        val info = d.probe(url)
        val initial = DownloadSnapshot.initial(info, 3)
        val first = initial.segments[0].segment

        fs.createDirectories("/downloads".toPath())
        fs.write("/downloads/data.bin.part".toPath()) { write(data, 0, first.length.toInt()) }
        val resumed = initial.copy(
            segments = initial.segments.mapIndexed { i, s ->
                if (i == 0) s.copy(downloaded = s.segment.length) else s
            },
        )
        requestedRanges.clear()

        val result = d.download(info, dest, resumed).last()

        assertTrue(result.isComplete)
        assertEquals(2, requestedRanges.size)
        assertTrue(requestedRanges.none { it == "bytes=${first.start}-${first.endInclusive}" })
        assertContentEquals(data, fs.read(dest) { readByteArray() })
    }
}
