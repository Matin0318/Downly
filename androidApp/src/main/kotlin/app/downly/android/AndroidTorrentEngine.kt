package app.downly.android

import android.content.Context
import app.downly.core.engine.DownloadException
import app.downly.ui.TorrentEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.libtorrent4j.SessionManager
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** BitTorrent with libtorrent4j. The session starts the first time a torrent is added. */
class AndroidTorrentEngine(private val context: Context) : TorrentEngine {
    private val torrentDir: File
        get() = File(context.cacheDir, "torrents").also { it.mkdirs() }

    private val session: SessionManager by lazy { SessionManager().also { it.start() } }

    private fun cacheFile(source: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(source.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(torrentDir, "$hash.torrent")
    }

    /** The .torrent file's bytes: from the cache, from other peers (magnet link) or from the web. */
    private suspend fun torrentBytes(source: String): ByteArray = withContext(Dispatchers.IO) {
        val cached = cacheFile(source)
        if (cached.exists() && cached.length() > 0L) return@withContext cached.readBytes()
        val bytes: ByteArray = if (source.startsWith("magnet:", ignoreCase = true)) {
            session.fetchMagnet(source, 45, torrentDir)
                ?: throw DownloadException("Could not get the torrent information. No peers answered.", true)
        } else {
            val connection = URL(source.substringBefore('#')).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", "Downly")
            try {
                if (connection.responseCode >= 400) throw DownloadException("HTTP ${connection.responseCode}", connection.responseCode >= 500)
                connection.inputStream.use { it.readBytes() }
            } finally {
                connection.disconnect()
            }
        }
        cached.writeBytes(bytes)
        bytes
    }

    override suspend fun meta(source: String): TorrentEngine.Meta {
        val info = TorrentInfo(torrentBytes(source))
        return TorrentEngine.Meta(info.name(), info.totalSize())
    }

    override fun download(source: String, saveDir: String): Flow<TorrentEngine.Progress> = flow {
        val info = TorrentInfo(torrentBytes(source))
        val dir = File(saveDir).also { it.mkdirs() }
        session.download(info, dir)
        var handle: TorrentHandle? = null
        try {
            while (true) {
                if (handle == null) handle = session.find(info.infoHash())
                val current = handle
                if (current != null && current.isValid) {
                    val status = current.status()
                    val finished = status.isFinished
                    emit(
                        TorrentEngine.Progress(
                            doneBytes = status.totalDone(),
                            totalBytes = info.totalSize(),
                            bytesPerSecond = status.downloadPayloadRate().toLong(),
                            finished = finished,
                        ),
                    )
                    if (finished) break
                }
                delay(500)
            }
        } finally {
            // Stops the torrent (no seeding). The downloaded files stay, so a later resume continues.
            handle?.let { runCatching { session.remove(it) } }
        }
    }.flowOn(Dispatchers.IO)
}
