package app.downly.core.engine

import kotlin.math.ceil

/**
 * One video or audio quality of a DASH stream. [segments] are the media links in order and [initUrl] the
 * initialization segment; a track made of a single file has [singleUrl] instead.
 */
class DashTrack internal constructor(
    val id: String,
    val bandwidth: Long,
    val width: Int,
    val height: Int,
    val mime: String,
    val codecs: String,
    internal val segments: List<String>,
    internal val initUrl: String?,
    internal val singleUrl: String?,
) {
    val isVideo: Boolean get() = mime.startsWith("video")
    val isWebm: Boolean get() = mime.contains("webm")
}

class DashManifest(val video: List<DashTrack>, val audio: List<DashTrack>)

/** Reads a DASH manifest (.mpd) of an on-demand (not live, not DRM protected) stream. */
object DashParser {
    fun looksLikeDash(url: String): Boolean = url.substringBefore('#').substringBefore('?').lowercase().endsWith(".mpd")

    private val token = Regex("""\$(RepresentationID|Bandwidth|Number|Time)(?:%0(\d+)d)?\$""")
    private val isoDuration = Regex("""P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:([\d.]+)S)?)?""")

    fun parse(text: String, manifestUrl: String): DashManifest {
        val mpd = MiniXml.parse(text)
        if (mpd.name != "MPD") throw DownloadException("This is not a DASH manifest")
        if (mpd.attr("type") == "dynamic") throw DownloadException("Live streams cannot be downloaded")
        val periods = mpd.all("Period").filter { it.all("AdaptationSet").isNotEmpty() }
        if (periods.isEmpty()) throw DownloadException("The manifest has no media")
        if (periods.size > 1) throw DownloadException("DASH streams with several periods are not supported")
        val period = periods.first()
        val totalSeconds = seconds(period.attr("duration")) ?: seconds(mpd.attr("mediaPresentationDuration"))
        val periodBase = baseOf(period, baseOf(mpd, manifestUrl))

        val video = ArrayList<DashTrack>()
        val audio = ArrayList<DashTrack>()
        for (set in period.all("AdaptationSet")) {
            if (set.all("ContentProtection").isNotEmpty()) throw DownloadException("Protected (DRM) streams cannot be downloaded")
            val setBase = baseOf(set, periodBase)
            for (rep in set.all("Representation")) {
                if (rep.all("ContentProtection").isNotEmpty()) throw DownloadException("Protected (DRM) streams cannot be downloaded")
                val mime = rep.attr("mimeType") ?: set.attr("mimeType") ?: ""
                val kind = (set.attr("contentType") ?: mime.substringBefore('/')).lowercase()
                if (kind != "video" && kind != "audio") continue
                val track = track(rep, set, baseOf(rep, setBase), mime.ifEmpty { "$kind/mp4" }, totalSeconds)
                if (kind == "video") video += track else audio += track
            }
        }
        if (video.isEmpty() && audio.isEmpty()) throw DownloadException("The manifest has no video or audio")
        return DashManifest(video, audio)
    }

    private fun track(rep: XmlNode, set: XmlNode, base: String, mime: String, totalSeconds: Double?): DashTrack {
        val id = rep.attr("id").orEmpty()
        val bandwidth = rep.attr("bandwidth")?.toLongOrNull() ?: 0L
        val width = (rep.attr("width") ?: set.attr("width"))?.toIntOrNull() ?: 0
        val height = (rep.attr("height") ?: set.attr("height"))?.toIntOrNull() ?: 0
        val codecs = rep.attr("codecs") ?: set.attr("codecs") ?: ""
        val template = rep.child("SegmentTemplate") ?: set.child("SegmentTemplate")
        val list = rep.child("SegmentList") ?: set.child("SegmentList")

        if (template != null) {
            val timescale = template.attr("timescale")?.toLongOrNull() ?: 1L
            val startNumber = template.attr("startNumber")?.toLongOrNull() ?: 1L
            val media = template.attr("media") ?: throw DownloadException("The manifest has no segment links")
            val init = template.attr("initialization")?.let { link(base, expand(it, id, bandwidth, 0L, 0L)) }
            val urls = ArrayList<String>()
            val timeline = template.child("SegmentTimeline")
            if (timeline != null) {
                var time = 0L
                var number = startNumber
                for (s in timeline.all("S")) {
                    s.attr("t")?.toLongOrNull()?.let { time = it }
                    val d = s.attr("d")?.toLongOrNull() ?: throw DownloadException("Bad segment timeline")
                    var extra = s.attr("r")?.toIntOrNull() ?: 0
                    if (extra < 0) {
                        val total = totalSeconds ?: throw DownloadException("The stream length is unknown")
                        extra = maxOf(1, ceil((total * timescale - time) / d).toInt()) - 1
                    }
                    repeat(extra + 1) {
                        urls += link(base, expand(media, id, bandwidth, number, time))
                        number++
                        time += d
                    }
                }
            } else {
                val duration = template.attr("duration")?.toLongOrNull() ?: throw DownloadException("Bad segment duration")
                val total = totalSeconds ?: throw DownloadException("The stream length is unknown")
                val count = ceil(total * timescale / duration).toInt()
                for (k in 0 until count) urls += link(base, expand(media, id, bandwidth, startNumber + k, k * duration))
            }
            return DashTrack(id, bandwidth, width, height, mime, codecs, urls, init, null)
        }

        if (list != null) {
            val init = list.child("Initialization")?.attr("sourceURL")?.let { link(base, it) }
            val urls = list.all("SegmentURL").map { seg ->
                if (seg.attr("mediaRange") != null || seg.attr("media") == null) {
                    throw DownloadException("This kind of segment list is not supported")
                }
                link(base, seg.attr("media")!!)
            }
            return DashTrack(id, bandwidth, width, height, mime, codecs, urls, init, null)
        }

        // One file per track (on-demand profile): the BaseURL is the whole track.
        if (rep.child("BaseURL") == null) throw DownloadException("The manifest has no segment links")
        return DashTrack(id, bandwidth, width, height, mime, codecs, emptyList(), null, base)
    }

    private fun baseOf(node: XmlNode, parent: String): String {
        val ref = node.child("BaseURL")?.text?.trim().orEmpty()
        if (ref.isEmpty()) return parent
        return SiteGrabber.resolve(parent, ref) ?: parent
    }

    private fun link(base: String, ref: String): String =
        SiteGrabber.resolve(base, ref) ?: throw DownloadException("Bad segment link")

    internal fun expand(template: String, id: String, bandwidth: Long, number: Long, time: Long): String =
        token.replace(template) { m ->
            val kind = m.groupValues[1]
            val value = when (kind) {
                "RepresentationID" -> id
                "Bandwidth" -> bandwidth.toString()
                "Number" -> number.toString()
                else -> time.toString()
            }
            val width = m.groupValues[2].toIntOrNull()
            if (width != null && kind != "RepresentationID") value.padStart(width, '0') else value
        }.replace("\$\$", "\$")

    private fun seconds(iso: String?): Double? {
        if (iso == null) return null
        val m = isoDuration.matchEntire(iso.trim()) ?: return null
        val days = m.groupValues[1].toDoubleOrNull() ?: 0.0
        val hours = m.groupValues[2].toDoubleOrNull() ?: 0.0
        val minutes = m.groupValues[3].toDoubleOrNull() ?: 0.0
        val secs = m.groupValues[4].toDoubleOrNull() ?: 0.0
        val total = days * 86_400 + hours * 3_600 + minutes * 60 + secs
        return if (total > 0) total else null
    }
}

/** DASH on top of the HLS downloader: every track becomes a list of segments written into one file. */
class DashSupport(private val hls: HlsDownloader) {
    suspend fun load(url: String, headers: Map<String, String> = emptyMap()): DashManifest {
        val credentials = UrlCredentials.parse(url)
        return DashParser.parse(hls.fetchText(url, headers), credentials.cleanUrl)
    }

    /** The video qualities, best first. The `url` of each [HlsVariant] is the representation id. */
    suspend fun qualities(url: String, headers: Map<String, String> = emptyMap()): List<HlsVariant> =
        load(url, headers).video
            .sortedByDescending { it.bandwidth }
            .map { HlsVariant(it.bandwidth, it.id, "${it.width}x${it.height}") }

    suspend fun plan(track: DashTrack, headers: Map<String, String> = emptyMap()): HlsPlan {
        val single = track.singleUrl
        if (single != null) return hls.rangedPlan(single, headers)
        return HlsPlan(track.segments.mapIndexed { i, u -> HlsSegment(u, i.toLong(), null, null) }, track.initUrl)
    }
}
