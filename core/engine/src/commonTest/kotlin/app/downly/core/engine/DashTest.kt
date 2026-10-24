package app.downly.core.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DashTest {
    // '@' stands for '$' so the manifest text is not read as a Kotlin string template.
    private val manifest = """<?xml version="1.0"?>
<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" mediaPresentationDuration="PT20S">
  <BaseURL>https://cdn.example.com/v/</BaseURL>
  <Period>
    <AdaptationSet contentType="video" mimeType="video/mp4">
      <SegmentTemplate timescale="1000" duration="10000" startNumber="1"
          initialization="@RepresentationID@/init.mp4" media="@RepresentationID@/seg-@Number%03d@.m4s"/>
      <Representation id="v1" bandwidth="800000" width="1280" height="720"/>
      <Representation id="v2" bandwidth="3000000" width="1920" height="1080"/>
    </AdaptationSet>
    <AdaptationSet contentType="audio" mimeType="audio/mp4">
      <Representation id="a1" bandwidth="128000">
        <SegmentTemplate timescale="48000" initialization="a/init.mp4" media="a/@Time@.m4s">
          <SegmentTimeline><S t="0" d="480000" r="1"/></SegmentTimeline>
        </SegmentTemplate>
      </Representation>
    </AdaptationSet>
  </Period>
</MPD>""".replace('@', '$')

    @Test
    fun readsQualitiesAndSegments() {
        val result = DashParser.parse(manifest, "https://example.com/stream/index.mpd")
        assertEquals(listOf("v1", "v2"), result.video.map { it.id })
        val best = result.video.first { it.id == "v2" }
        assertEquals(1080, best.height)
        assertEquals("https://cdn.example.com/v/v2/init.mp4", best.initUrl)
        assertEquals(
            listOf("https://cdn.example.com/v/v2/seg-001.m4s", "https://cdn.example.com/v/v2/seg-002.m4s"),
            best.segments,
        )
        val audio = result.audio.single()
        assertEquals(
            listOf("https://cdn.example.com/v/a/0.m4s", "https://cdn.example.com/v/a/480000.m4s"),
            audio.segments,
        )
        assertNull(audio.singleUrl)
    }

    @Test
    fun liveAndProtectedStreamsAreRefused() {
        val live = manifest.replace("type=\"static\"", "type=\"dynamic\"")
        assertFailsWith<DownloadException> { DashParser.parse(live, "https://example.com/a.mpd") }
        val drm = manifest.replace("<Representation id=\"v1\"", "<ContentProtection schemeIdUri=\"urn:uuid:x\"/><Representation id=\"v1\"")
        assertFailsWith<DownloadException> { DashParser.parse(drm, "https://example.com/a.mpd") }
    }

    @Test
    fun templateNumbersArePadded() {
        assertEquals("seg-007.m4s", DashParser.expand("seg-@Number%03d@.m4s".replace('@', '$'), "x", 1L, 7L, 0L))
    }

    @Test
    fun recognizesMpdLinks() {
        assertTrue(DashParser.looksLikeDash("https://a.com/x/manifest.mpd?token=1"))
        assertTrue(!DashParser.looksLikeDash("https://a.com/x/video.mp4"))
    }
}
