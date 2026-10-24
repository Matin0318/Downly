package app.downly.core.engine

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DynamicSegmentationTest {
    private val data = ByteArray(8 * 1024 * 1024 + 7) { (it % 253).toByte() }
    private val fs = FakeFileSystem()
    private val dest = "/d/big.bin".toPath()
    private val seenHeaders = mutableListOf<Map<String, String?>>()

    private fun downloader(): SegmentedDownloader {
        val engine = MockEngine { request ->
            seenHeaders += mapOf(
                "auth" to request.headers[HttpHeaders.Authorization],
                "cookie" to request.headers[HttpHeaders.Cookie],
            )
            val m = Regex("bytes=(\\d+)-(\\d*)").matchEntire(request.headers[HttpHeaders.Range]!!)!!
            val start = m.groupValues[1].toInt()
            val end = m.groupValues[2].ifEmpty { (data.size - 1).toString() }.toInt().coerceAtMost(data.size - 1)
            respond(
                content = data.copyOfRange(start, end + 1),
                status = HttpStatusCode.PartialContent,
                headers = headersOf(HttpHeaders.ContentRange, "bytes $start-$end/${data.size}"),
            )
        }
        return SegmentedDownloader(HttpClient(engine), fs)
    }

    @Test
    fun idleWorkersSplitTheRemainingSegment() = runTest {
        val d = downloader()
        val info = d.probe("https://example.com/big.bin")
        // One big segment, four connections allowed: the extra workers must split it.
        val start = DownloadSnapshot.initial(info, 1)
        val result = d.download(info, dest, start, maxConnections = 4).last()

        assertTrue(result.isComplete)
        assertTrue(result.segments.size > 1, "expected the segment to be split")
        // Segments must tile the file exactly, without gaps or overlaps.
        val sorted = result.segments.map { it.segment }.sortedBy { it.start }
        assertEquals(0L, sorted.first().start)
        assertEquals(data.size - 1L, sorted.last().endInclusive)
        sorted.zipWithNext { a, b -> assertEquals(a.endInclusive + 1, b.start) }
        assertContentEquals(data, fs.read(dest) { readByteArray() })
    }

    @Test
    fun loginAndHeadersAreSentOnEveryRequest() = runTest {
        val d = downloader()
        val info = d.probe("https://user:pass@example.com/big.bin", mapOf("Cookie" to "a=b"))
        d.download(info, dest, DownloadSnapshot.initial(info, 2)).last()
        assertTrue(seenHeaders.isNotEmpty())
        assertTrue(seenHeaders.all { it["auth"] == "Basic dXNlcjpwYXNz" && it["cookie"] == "a=b" })
    }
}
