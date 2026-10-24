package app.downly.core.engine

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.coroutines.cancellation.CancellationException

/** Progress of an HLS download: finished segments and bytes written so far. */
class HlsProgress(val doneSegments: Int, val bytes: Long)

internal expect fun aesCbcDecrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray

/**
 * Downloads an HLS (.m3u8) stream into one file: segments are fetched [parallel] at a time and written in order.
 * Resume = pass the last [HlsProgress] back in. AES-128 encrypted streams are decrypted on the fly.
 */
class HlsDownloader(
    private val client: HttpClient,
    private val fileSystem: FileSystem,
    private val limiter: RateLimiter,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val retries: Int = 3,
    private val retryDelayMillis: Long = 1_000,
) {
    /** Reads the playlist (choosing the best quality of a master playlist) and returns what to download. */
    suspend fun prepare(url: String, headers: Map<String, String> = emptyMap(), variantUrl: String? = null): HlsPlan {
        var current = url
        repeat(3) {
            val credentials = UrlCredentials.parse(current)
            val text = getText(credentials, headers)
            when (val playlist = HlsPlaylist.parse(text, credentials.cleanUrl)) {
                is HlsPlaylist.Master -> {
                    current = playlist.variants.firstOrNull { it.url == variantUrl }?.url
                        ?: playlist.variants.maxByOrNull { it.bandwidth }?.url
                        ?: throw DownloadException("The playlist has no video streams")
                }
                is HlsPlaylist.Media -> {
                    if (playlist.isLive) throw DownloadException("Live streams cannot be downloaded")
                    return playlist.plan
                }
            }
        }
        throw DownloadException("Too many nested playlists")
    }

    /** The quality choices of a master playlist, best first. Empty for a plain (single quality) playlist. */
    suspend fun variants(url: String, headers: Map<String, String> = emptyMap()): List<HlsVariant> {
        val credentials = UrlCredentials.parse(url)
        val playlist = HlsPlaylist.parse(getText(credentials, headers), credentials.cleanUrl)
        return if (playlist is HlsPlaylist.Master) playlist.variants.sortedByDescending { it.bandwidth } else emptyList()
    }

    /** Downloads a text file (a manifest). */
    suspend fun fetchText(url: String, headers: Map<String, String> = emptyMap()): String =
        getText(UrlCredentials.parse(url), headers)

    /**
     * Splits one big file into pieces of [chunk] bytes (fetched with Range requests) so it can be downloaded
     * with [download] without holding the whole file in memory.
     */
    suspend fun rangedPlan(url: String, headers: Map<String, String> = emptyMap(), chunk: Long = 4L * 1024 * 1024): HlsPlan {
        val credentials = UrlCredentials.parse(url)
        val response = client.get(credentials.cleanUrl) {
            applyHeaders(credentials, headers)
            header("Range", "bytes=0-0")
        }
        check(response)
        val total = response.headers["Content-Range"]?.substringAfter('/')?.trim()?.toLongOrNull()
            ?: throw DownloadException("The server does not allow partial downloads")
        val segments = ArrayList<HlsSegment>()
        var start = 0L
        var index = 0L
        while (start < total) {
            val end = minOf(total - 1, start + chunk - 1)
            segments += HlsSegment(url, index++, null, null, start..end)
            start = end + 1
        }
        return HlsPlan(segments, null)
    }

    fun download(
        plan: HlsPlan,
        destination: Path,
        startSegment: Int,
        startBytes: Long,
        parallel: Int,
        headers: Map<String, String> = emptyMap(),
        ownLimiter: RateLimiter? = null,
        extraLimiter: RateLimiter? = null,
    ): Flow<HlsProgress> = channelFlow {
        val part = "$destination.part".toPath()
        destination.parent?.let { fileSystem.createDirectories(it) }
        val keys = HashMap<String, ByteArray>()
        var done = startSegment
        var offset = startBytes
        val handle = fileSystem.openReadWrite(part, mustCreate = false, mustExist = false)
        try {
            if (offset == 0L && done == 0 && plan.initUrl != null) {
                val bytes = fetchWithRetry(plan.initUrl, headers, ownLimiter, extraLimiter)
                handle.write(0, bytes, 0, bytes.size)
                offset += bytes.size
            }
            send(HlsProgress(done, offset))
            val width = parallel.coerceAtLeast(1)
            while (done < plan.segments.size) {
                val batch = plan.segments.subList(done, minOf(plan.segments.size, done + width))
                // Keys are fetched one by one before the batch so the cache needs no locking.
                for (seg in batch) {
                    val uri = seg.keyUri ?: continue
                    if (uri !in keys) keys[uri] = fetchWithRetry(uri, headers, null, null)
                }
                val results = coroutineScope {
                    batch.map { seg ->
                        async(ioDispatcher) {
                            val raw = fetchWithRetry(seg.url, headers, ownLimiter, extraLimiter, seg.byteRange)
                            val key = seg.keyUri?.let { keys.getValue(it) }
                            if (key == null) raw else aesCbcDecrypt(raw, key, seg.iv ?: HlsPlaylist.ivFromSequence(seg.sequence))
                        }
                    }.awaitAll()
                }
                for (bytes in results) {
                    handle.write(offset, bytes, 0, bytes.size)
                    offset += bytes.size
                    done++
                }
                send(HlsProgress(done, offset))
            }
            handle.resize(offset) // drops leftovers of an interrupted earlier run
        } finally {
            handle.close()
        }
        fileSystem.atomicMove(part, destination)
        send(HlsProgress(done, offset))
    }

    private suspend fun getText(credentials: UrlCredentials, headers: Map<String, String>): String {
        val response = client.get(credentials.cleanUrl) { applyHeaders(credentials, headers) }
        check(response)
        return response.bodyAsText()
    }

    private suspend fun fetchWithRetry(
        url: String,
        headers: Map<String, String>,
        ownLimiter: RateLimiter?,
        extraLimiter: RateLimiter?,
        range: LongRange? = null,
    ): ByteArray {
        var failures = 0
        while (true) {
            try {
                val credentials = UrlCredentials.parse(url)
                val response = client.get(credentials.cleanUrl) {
                    applyHeaders(credentials, headers)
                    if (range != null) header("Range", "bytes=${range.first}-${range.last}")
                }
                check(response)
                val bytes = response.readRawBytes()
                limiter.acquire(bytes.size)
                ownLimiter?.acquire(bytes.size)
                extraLimiter?.acquire(bytes.size)
                return bytes
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures++
                val retryable = (e as? DownloadException)?.retryable ?: true
                if (!retryable || failures > retries) throw e
                delay(retryDelayMillis * failures)
            }
        }
    }

    private fun check(response: HttpResponse) {
        val status = response.status.value
        if (status >= 400) throw DownloadException("HTTP $status", isRetryableStatus(status))
    }

    private fun io.ktor.client.request.HttpRequestBuilder.applyHeaders(credentials: UrlCredentials, headers: Map<String, String>) {
        credentials.authorization?.let { header("Authorization", it) }
        RequestHeaders.sanitize(headers).forEach { (name, value) -> header(name, value) }
    }
}
