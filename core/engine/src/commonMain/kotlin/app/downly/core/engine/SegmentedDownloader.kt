package app.downly.core.engine

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.FileHandle
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Multi-connection downloader.
 *
 * Pause = cancel the collecting coroutine. Resume = call [download] again with the last
 * [DownloadSnapshot] you received (persist it to survive app restarts).
 */
class SegmentedDownloader(
    private val client: HttpClient,
    private val fileSystem: FileSystem,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxRetriesPerSegment: Int = 3,
    private val retryDelayMillis: Long = 1_000,
    /** A running segment is only split when at least twice this many bytes are still missing. */
    private val minSplitBytes: Long = 1L * 1024 * 1024,
) {
    private val globalLimiter = RateLimiter()

    /** The app-wide limiter, for transfers that do not go through this class (FTP). */
    val globalSpeedLimiter: RateLimiter get() = globalLimiter

    /** Global speed limit shared by every running download, in bytes per second. 0 = unlimited. */
    var speedLimitBytesPerSec: Long
        get() = globalLimiter.bytesPerSec
        set(value) {
            globalLimiter.bytesPerSec = value
        }

    /** HLS (.m3u8) downloads share this downloader's connection, dispatcher and global speed limit. */
    val hls: HlsDownloader = HlsDownloader(client, fileSystem, globalLimiter, ioDispatcher)
    val dash: DashSupport = DashSupport(hls)

    /** Downloads a small text page (for the Site Grabber). Refuses pages bigger than [maxBytes]. */
    suspend fun fetchText(url: String, headers: Map<String, String> = emptyMap(), maxBytes: Long = 5L * 1024 * 1024): String {
        val credentials = UrlCredentials.parse(url)
        val extra = RequestHeaders.sanitize(headers)
        val response = client.get(credentials.cleanUrl) {
            credentials.authorization?.let { header(HttpHeaders.Authorization, it) }
            extra.forEach { (name, value) -> header(name, value) }
        }
        val status = response.status.value
        if (status >= 400) throw DownloadException("HTTP $status", isRetryableStatus(status))
        val length = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (length != null && length > maxBytes) throw DownloadException("Page is too large")
        return response.bodyAsText()
    }

    /** Asks the server for file size, Range support and file name. */
    suspend fun probe(url: String, headers: Map<String, String> = emptyMap()): RemoteFileInfo {
        val credentials = UrlCredentials.parse(url)
        val extra = RequestHeaders.sanitize(headers)
        return client.prepareGet(credentials.cleanUrl) {
            header(HttpHeaders.Range, "bytes=0-0")
            credentials.authorization?.let { header(HttpHeaders.Authorization, it) }
            extra.forEach { (name, value) -> header(name, value) }
        }.execute { response ->
            val status = response.status.value
            if (status >= 400) throw DownloadException("HTTP $status", isRetryableStatus(status))

            val partial = status == 206
            val total = if (partial) {
                response.headers[HttpHeaders.ContentRange]?.substringAfterLast('/')?.trim()?.toLongOrNull() ?: -1L
            } else {
                response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L
            }
            val finalUrl = response.call.request.url.toString()
            val sameHost = UrlCredentials.hostOf(finalUrl) == UrlCredentials.hostOf(credentials.cleanUrl)
            RemoteFileInfo(
                url = url,
                finalUrl = finalUrl,
                totalBytes = total,
                acceptsRanges = partial && total > 0,
                fileName = FileNameResolver.resolve(response.headers[HttpHeaders.ContentDisposition], finalUrl),
                // Never forward the login to a different host after a redirect.
                authorization = credentials.authorization.takeIf { sameHost },
                headers = if (sameHost) extra else emptyMap(),
            )
        }
    }

    /**
     * Downloads to [destination] (written to `<destination>.part` first, renamed on success).
     * Emits throttled progress snapshots; the last emission is the complete state.
     *
     * [maxConnections] workers run at once. When a worker runs out of work it splits the segment
     * with the most bytes left (dynamic segmentation, like IDM / ADM), so fast connections never idle.
     * [speedLimitBytesPerSec] limits this download only (0 = no per-download limit).
     */
    fun download(
        info: RemoteFileInfo,
        destination: Path,
        snapshot: DownloadSnapshot,
        maxConnections: Int = snapshot.segments.size,
        speedLimitBytesPerSec: Long = 0L,
        /** A limiter shared with other downloads (e.g. a whole queue). */
        extraLimiter: RateLimiter? = null,
    ): Flow<DownloadSnapshot> =
        channelFlow {
            if (info.totalBytes <= 0) throw DownloadException("File size is unknown or zero")
            val partPath = "$destination.part".toPath()
            destination.parent?.let { fileSystem.createDirectories(it) }

            // Only trust saved progress if the partial file still exists and the server supports Range.
            val canResume = info.acceptsRanges && fileSystem.exists(partPath)
            val live = ArrayList<LiveSegment>()
            snapshot.segments.forEachIndexed { i, sp ->
                live += LiveSegment(i, sp.segment.start, sp.segment.endInclusive, if (canResume) sp.downloaded else 0L)
            }
            val lock = Mutex()
            val ownLimiter = if (speedLimitBytesPerSec > 0L) RateLimiter(speedLimitBytesPerSec) else null

            // Call only while holding [lock].
            fun current() = DownloadSnapshot(
                info.totalBytes,
                live.map { SegmentProgress(Segment(it.index, it.start, it.endInclusive), it.done) },
            )

            // Call only while holding [lock]. Gives the next piece of work, splitting a busy segment if needed.
            fun claimNext(): LiveSegment? {
                live.firstOrNull { !it.claimed && it.done < it.length }?.let {
                    it.claimed = true
                    return it
                }
                if (!info.acceptsRanges) return null
                val victim = live
                    .filter { it.claimed && it.length - it.done >= 2 * minSplitBytes }
                    .maxByOrNull { it.length - it.done } ?: return null
                val mid = victim.start + victim.done + (victim.length - victim.done) / 2
                val stolen = LiveSegment(live.size, mid, victim.endInclusive, 0L)
                stolen.claimed = true
                victim.endInclusive = mid - 1
                live += stolen
                return stolen
            }

            trySend(lock.withLock { current() })

            var lastEmit = TimeSource.Monotonic.markNow()
            val onBytes: suspend (LiveSegment, Int) -> Unit = { seg, n ->
                val snap = lock.withLock {
                    seg.done += n.toLong()
                    if (lastEmit.elapsedNow() >= 100.milliseconds) {
                        lastEmit = TimeSource.Monotonic.markNow()
                        current()
                    } else {
                        null
                    }
                }
                if (snap != null) trySend(snap)
                ownLimiter?.acquire(n)
                extraLimiter?.acquire(n)
            }

            val workers = if (info.acceptsRanges) maxConnections.coerceAtLeast(1) else 1
            // Connections still allowed to work. When the server answers "too many connections" (429, 403,
            // 503) a worker leaves and the others carry on, so the download slows down instead of failing.
            var allowed = workers
            val backOff: suspend (LiveSegment) -> Boolean = { seg ->
                lock.withLock {
                    if (allowed > 1) {
                        allowed--
                        seg.claimed = false // Another worker picks this piece up.
                        true
                    } else {
                        false
                    }
                }
            }
            val handle = fileSystem.openReadWrite(partPath, mustCreate = false, mustExist = false)
            try {
                // No pre-allocation: okio's resize() writes zeros into a ByteArray as big as the file (OOM on big files).
                // Writing at an offset past the end makes the file grow on its own.
                coroutineScope {
                    repeat(workers) {
                        launch(ioDispatcher) {
                            while (true) {
                                val seg = lock.withLock { claimNext() } ?: break
                                val leave = fetchSegment(info, seg, handle, onBytes, backOff)
                                if (leave) break
                            }
                        }
                    }
                }
            } finally {
                handle.close()
            }

            val writtenSize = fileSystem.metadata(partPath).size
            if (writtenSize != info.totalBytes) {
                throw DownloadException("File size mismatch: expected ${info.totalBytes}, got $writtenSize")
            }
            fileSystem.atomicMove(partPath, destination)
            send(lock.withLock { current() })
        }

    /** A segment while it is downloading. [endInclusive] can shrink when another worker takes its tail. */
    private class LiveSegment(val index: Int, val start: Long, var endInclusive: Long, var done: Long) {
        var claimed: Boolean = false
        val length: Long get() = endInclusive - start + 1
    }

    /** Returns true when this worker should leave because the server limits the number of connections. */
    private suspend fun fetchSegment(
        info: RemoteFileInfo,
        seg: LiveSegment,
        handle: FileHandle,
        onBytes: suspend (LiveSegment, Int) -> Unit,
        backOff: suspend (LiveSegment) -> Boolean,
    ): Boolean {
        var failures = 0
        while (seg.done < seg.length) {
            val before = seg.done
            try {
                transfer(info, seg, handle, onBytes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (seg.done > before) failures = 0
                failures++
                if (isConnectionLimit(e) && backOff(seg)) return true
                val retryable = (e as? DownloadException)?.retryable ?: true
                if (!retryable || failures > maxRetriesPerSegment) throw e
                delay(retryDelayMillis * failures)
            }
        }
        return false
    }

    private fun isConnectionLimit(e: Exception): Boolean {
        val text = (e as? DownloadException)?.message ?: return false
        return text.startsWith("HTTP 429") || text.startsWith("HTTP 403") || text.startsWith("HTTP 503") || text.startsWith("HTTP 509")
    }

    private suspend fun transfer(
        info: RemoteFileInfo,
        seg: LiveSegment,
        handle: FileHandle,
        onBytes: suspend (LiveSegment, Int) -> Unit,
    ) {
        val ranged = info.acceptsRanges
        val from = seg.start + seg.done
        val to = seg.endInclusive

        client.prepareGet(info.finalUrl) {
            if (ranged) header(HttpHeaders.Range, "bytes=$from-$to")
            // Files are already compressed (or must stay byte for byte): no gzip, no extra work.
            header(HttpHeaders.AcceptEncoding, "identity")
            applyAuth(info)
        }.execute { response ->
            val status = response.status.value
            if (status >= 400) throw DownloadException("HTTP $status", isRetryableStatus(status))
            if (ranged && status != 206) {
                throw DownloadException("Server ignored the Range request (HTTP $status)")
            }

            val channel = response.bodyAsChannel()
            val buffer = ByteArray(BUFFER_SIZE)
            // seg.length can shrink while we read (another worker took the tail): stop at the new end.
            while (seg.done < seg.length) {
                val want = minOf(buffer.size.toLong(), seg.length - seg.done).toInt()
                val n = channel.readAvailable(buffer, 0, want)
                if (n == -1) break
                if (n == 0) continue
                handle.write(seg.start + seg.done, buffer, 0, n)
                onBytes(seg, n)
                globalLimiter.acquire(n)
            }
        }

        if (seg.done < seg.length) {
            throw DownloadException("Connection closed before the segment finished", retryable = true)
        }
    }

    private fun HttpRequestBuilder.applyAuth(info: RemoteFileInfo) {
        info.authorization?.let { header(HttpHeaders.Authorization, it) }
        info.headers.forEach { (name, value) -> header(name, value) }
    }

    private companion object {
        const val BUFFER_SIZE = 256 * 1024
    }
}
