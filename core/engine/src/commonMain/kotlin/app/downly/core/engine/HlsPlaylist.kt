package app.downly.core.engine

/** One media segment of an HLS stream. [keyUri] / [iv] are set when the segment is AES-128 encrypted. */
class HlsSegment(
    val url: String,
    val sequence: Long,
    val keyUri: String?,
    val iv: ByteArray?,
    /** When set, only these bytes of [url] are fetched (a big single file is split into pieces). */
    val byteRange: LongRange? = null,
)

class HlsVariant(val bandwidth: Long, val url: String, val resolution: String = "") {
    /** "1080p" when the playlist tells the picture size, otherwise the bit rate, e.g. "2.4 Mbps". */
    val label: String
        get() {
            val height = resolution.substringAfter('x', "").toIntOrNull()
            val rate = if (bandwidth > 0) " • " + ((bandwidth / 100_000) / 10.0) + " Mbps" else ""
            return if (height != null) "${height}p$rate" else rate.removePrefix(" • ").ifBlank { "?" }
        }
}

/** What a downloader needs to fetch a stream. */
class HlsPlan(val segments: List<HlsSegment>, val initUrl: String?) {
    /** `.mp4` for fragmented MP4 streams, otherwise `.ts` (plain concatenation of MPEG-TS pieces). */
    val extension: String get() = if (initUrl != null) "mp4" else "ts"
}

sealed class HlsPlaylist {
    class Master(val variants: List<HlsVariant>) : HlsPlaylist()
    class Media(val plan: HlsPlan, val isLive: Boolean) : HlsPlaylist()

    companion object {
        fun looksLikeHls(url: String): Boolean =
            url.substringBefore('#').substringBefore('?').lowercase().endsWith(".m3u8")

        private val attributes = Regex("""([A-Z0-9-]+)=("[^"]*"|[^,]*)""")

        private fun attrs(line: String): Map<String, String> =
            attributes.findAll(line.substringAfter(':')).associate { it.groupValues[1] to it.groupValues[2].trim('"') }

        fun parse(text: String, baseUrl: String): HlsPlaylist {
            val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            if (lines.firstOrNull()?.startsWith("#EXTM3U") != true) throw DownloadException("Not an HLS playlist")

            if (lines.any { it.startsWith("#EXT-X-STREAM-INF") }) {
                val variants = ArrayList<HlsVariant>()
                var pending: Long? = null
                var pendingSize = ""
                for (line in lines) {
                    if (line.startsWith("#EXT-X-STREAM-INF")) {
                        val a = attrs(line)
                        pending = a["BANDWIDTH"]?.toLongOrNull() ?: 0L
                        pendingSize = a["RESOLUTION"].orEmpty()
                    } else if (!line.startsWith("#") && pending != null) {
                        SiteGrabber.resolve(baseUrl, line)?.let { variants += HlsVariant(pending!!, it, pendingSize) }
                        pending = null
                    }
                }
                return Master(variants)
            }

            var sequence = 0L
            var keyUri: String? = null
            var keyIv: ByteArray? = null
            var initUrl: String? = null
            var endList = false
            var expectUri = false
            val segments = ArrayList<HlsSegment>()
            for (line in lines) {
                when {
                    line.startsWith("#EXT-X-MEDIA-SEQUENCE") -> sequence = line.substringAfter(':').trim().toLongOrNull() ?: 0L
                    line.startsWith("#EXT-X-ENDLIST") -> endList = true
                    line.startsWith("#EXT-X-BYTERANGE") -> throw DownloadException("HLS byte-range playlists are not supported")
                    line.startsWith("#EXT-X-KEY") -> {
                        val a = attrs(line)
                        when (a["METHOD"]) {
                            null, "NONE" -> { keyUri = null; keyIv = null }
                            "AES-128" -> {
                                keyUri = a["URI"]?.let { SiteGrabber.resolve(baseUrl, it) }
                                    ?: throw DownloadException("HLS key has no URI")
                                keyIv = a["IV"]?.let { parseIv(it) }
                            }
                            else -> throw DownloadException("HLS encryption ${a["METHOD"]} is not supported")
                        }
                    }
                    line.startsWith("#EXT-X-MAP") -> initUrl = attrs(line)["URI"]?.let { SiteGrabber.resolve(baseUrl, it) }
                    line.startsWith("#EXTINF") -> expectUri = true
                    !line.startsWith("#") && expectUri -> {
                        val url = SiteGrabber.resolve(baseUrl, line) ?: throw DownloadException("Bad HLS segment link")
                        segments += HlsSegment(url, sequence, keyUri, keyIv)
                        sequence++
                        expectUri = false
                    }
                }
            }
            if (segments.isEmpty()) throw DownloadException("The playlist has no segments")
            return Media(HlsPlan(segments, initUrl), isLive = !endList)
        }

        /** "0x1A2B..." -> 16 bytes (left padded with zeros). */
        internal fun parseIv(value: String): ByteArray {
            val hex = value.removePrefix("0x").removePrefix("0X").padStart(32, '0').takeLast(32)
            return ByteArray(16) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        }

        /** IV used when the playlist gives none: the segment number as a 128-bit big-endian value. */
        internal fun ivFromSequence(sequence: Long): ByteArray {
            val iv = ByteArray(16)
            for (i in 0 until 8) iv[15 - i] = (sequence shr (8 * i)).toByte()
            return iv
        }
    }
}
