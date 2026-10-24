package app.downly.core.engine

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HlsTest {
    private val base = "https://cdn.example.com/v/index.m3u8"

    @Test
    fun masterPlaylistPicksHighestBandwidth() {
        val text = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=500000,RESOLUTION=640x360\nlow/p.m3u8\n" +
            "#EXT-X-STREAM-INF:BANDWIDTH=2500000\nhigh/p.m3u8\n"
        val master = assertIs<HlsPlaylist.Master>(HlsPlaylist.parse(text, base))
        assertEquals("https://cdn.example.com/v/high/p.m3u8", master.variants.maxByOrNull { it.bandwidth }!!.url)
    }

    @Test
    fun mediaPlaylistListsSegmentsAndSequence() {
        val text = "#EXTM3U\n#EXT-X-MEDIA-SEQUENCE:7\n#EXTINF:4,\na.ts\n#EXTINF:4,\nb.ts\n#EXT-X-ENDLIST\n"
        val media = assertIs<HlsPlaylist.Media>(HlsPlaylist.parse(text, base))
        assertEquals(false, media.isLive)
        assertEquals(listOf("https://cdn.example.com/v/a.ts", "https://cdn.example.com/v/b.ts"), media.plan.segments.map { it.url })
        assertEquals(listOf(7L, 8L), media.plan.segments.map { it.sequence })
        assertEquals("ts", media.plan.extension)
    }

    @Test
    fun rejectsUnsupportedEncryption() {
        val text = "#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"k\"\n#EXTINF:4,\na.ts\n#EXT-X-ENDLIST\n"
        assertFailsWith<DownloadException> { HlsPlaylist.parse(text, base) }
    }

    @Test
    fun ivHelpers() {
        assertEquals(5, HlsPlaylist.ivFromSequence(5)[15].toInt())
        assertEquals(16, HlsPlaylist.parseIv("0x01").size)
    }

    @Test
    fun downloadsSegmentsInOrder() = runTest {
        val a = ByteArray(1000) { 1 }
        val b = ByteArray(500) { 2 }
        val c = ByteArray(700) { 3 }
        val playlist = "#EXTM3U\n#EXTINF:4,\na.ts\n#EXTINF:4,\nb.ts\n#EXTINF:4,\nc.ts\n#EXT-X-ENDLIST\n"
        val engine = MockEngine { request ->
            val body: ByteArray = when (request.url.encodedPath) {
                "/v/index.m3u8" -> playlist.encodeToByteArray()
                "/v/a.ts" -> a
                "/v/b.ts" -> b
                "/v/c.ts" -> c
                else -> return@MockEngine respond("", HttpStatusCode.NotFound)
            }
            respond(body, HttpStatusCode.OK)
        }
        val fs = FakeFileSystem()
        val dl = HlsDownloader(HttpClient(engine), fs, RateLimiter())
        val plan = dl.prepare(base)
        val result = dl.download(plan, "/d/out.ts".toPath(), 0, 0L, parallel = 2).last()
        assertEquals(3, result.doneSegments)
        assertEquals(2200L, result.bytes)
        assertContentEquals(a + b + c, fs.read("/d/out.ts".toPath()) { readByteArray() })
        assertTrue(!fs.exists("/d/out.ts.part".toPath()))
    }
}

class HlsVariantLabelTest {
    @Test
    fun labelUsesHeightWhenKnown() {
        assertEquals("1080p • 4.5 Mbps", HlsVariant(4_500_000, "u", "1920x1080").label)
    }

    @Test
    fun labelFallsBackToBitRate() {
        assertEquals("2.4 Mbps", HlsVariant(2_400_000, "u").label)
    }
}
