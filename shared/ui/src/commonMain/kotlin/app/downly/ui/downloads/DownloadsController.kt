package app.downly.ui.downloads

import app.downly.core.engine.ChecksumAlgorithm
import app.downly.core.engine.CurlParser
import app.downly.core.engine.DownloadException
import app.downly.core.engine.DashParser
import app.downly.core.engine.DashTrack
import app.downly.core.engine.FileChecksum
import app.downly.core.engine.Ftp
import app.downly.core.engine.ProxyConfig
import app.downly.core.engine.ProxyHolder
import app.downly.core.engine.ProxyKind
import app.downly.core.engine.HlsPlaylist
import app.downly.core.engine.SiteGrabber
import app.downly.core.engine.DownloadSnapshot
import app.downly.core.engine.RateLimiter
import app.downly.core.engine.RemoteFileInfo
import app.downly.core.engine.Segment
import app.downly.core.engine.SegmentProgress
import app.downly.core.engine.SegmentedDownloader
import app.downly.core.model.DownloadStatus
import app.downly.data.AppSettings
import app.downly.data.Backup
import app.downly.data.ProxyMode
import app.downly.data.DownloadQueue
import app.downly.data.MAIN_QUEUE_ID
import app.downly.data.QueueFinishAction
import app.downly.data.DownloadRepository
import app.downly.data.SettingsRepository
import app.downly.data.StoredDownload
import app.downly.data.StoredSegment
import app.downly.ui.MediaMerger
import app.downly.ui.PublicStorage
import app.downly.ui.TorrentEngine
import app.downly.ui.components.categoryOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

/**
 * Presentation + persistence logic for downloads. Platform-independent.
 * Use it from a single-threaded scope (Android: Dispatchers.Main.immediate).
 */
class DownloadsController(
    private val downloader: SegmentedDownloader,
    private val fileSystem: FileSystem,
    downloadDir: Path,
    private val scope: CoroutineScope,
    private val repository: DownloadRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: () -> Long,
    /** Current local time as minutes since midnight (0..1439). Return -1 when the platform cannot tell. */
    private val minuteOfDay: () -> Int = { -1 },
    /** False on a metered network (mobile data). Used by the "Wi-Fi only" setting. */
    private val isUnmeteredNetwork: () -> Boolean = { true },
    /** Called once when a queue finished its downloads and has a "when finished" action. */
    private val onQueueFinished: (DownloadQueue) -> Unit = {},
    /** Current weekday, 0 = Sunday ... 6 = Saturday, or -1 when unknown. */
    private val dayOfWeek: () -> Int = { -1 },
    /** Moves finished files to the public Downloads folder. */
    private val publicStorage: PublicStorage? = null,
    /** Offset of the user's time zone from UTC in minutes (for the per-day usage chart). */
    private val utcOffsetMinutes: () -> Int = { 0 },
    /** Joins the audio and video tracks of a DASH stream into one file. */
    private val mediaMerger: MediaMerger? = null,
    /** BitTorrent downloads (magnet links and .torrent files). */
    private val torrentEngine: TorrentEngine? = null,
    /**
     * The public folder new downloads are written into directly, or null when the app may not write there.
     * Saving directly means no copy at the end and no space used inside the app, however many downloads run.
     */
    private val publicDirectory: () -> Path? = { null },
    /**
     * Settings shown until the saved ones are read from disk. The host passes the last known language and theme
     * here, so the very first frame already looks right instead of flashing the English defaults.
     */
    initialSettings: AppSettings = AppSettings(),
) {
    /** The folder inside the app: used when direct saving is not available, and for old downloads. */
    private val appDownloadDir: Path = downloadDir

    /** Where new downloads are saved right now. */
    private val saveDir: Path get() = publicDirectory() ?: appDownloadDir

    private class Entry(val id: Long, var url: String, val createdAt: Long) {
        var waiting: Boolean = false
        var fileName: String = ""
        var info: RemoteFileInfo? = null
        var destination: Path? = null
        var snapshot: DownloadSnapshot? = null
        var job: Job? = null
        var status: DownloadStatus = DownloadStatus.QUEUED
        var error: String? = null
        var speed: Long = 0L
        var completedAt: Long? = null
        var needsReprobe: Boolean = false
        var interrupted: Boolean = false
        var retryAttempt: Int = 0
            set(value) {
                field = value
                if (value == 0) mirrorTries = 0
            }
        var mirrorTries: Int = 0
        var mirrors: List<String> = emptyList()
        var hlsVariant: String = ""
        var retryPending: Boolean = false
        var headers: Map<String, String> = emptyMap()
        var priority: Int = 0
        var speedLimitKBps: Int = 0
        var heldByNetwork: Boolean = false
        var hlsDone: Int = 0
        var hlsTotal: Int = 0
        var hlsBytes: Long = 0L
        var queueId: Long = MAIN_QUEUE_ID
        var subFolder: String = ""
        var customName: String = ""
        var publicUri: String = ""
    }

    private val entries = LinkedHashMap<Long, Entry>()
    private var nextId = 1L
    private var lastPersist = TimeSource.Monotonic.markNow()
    private var lastScheduleMinute = -1
    private val queueLimiters = HashMap<Long, RateLimiter>()
    /** Queue id -> number of completed files when the queue started working (for the "when finished" action). */
    private val finishArmed = HashMap<Long, Int>()

    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    private val _settings = MutableStateFlow(initialSettings)
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _usage = MutableStateFlow<Map<Long, Long>>(emptyMap())

    /** Bytes downloaded per day (key = days since 1970 in the user's time zone). */
    val usage: StateFlow<Map<Long, Long>> = _usage.asStateFlow()
    private var usageDirty = false

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    val downloadDirPath: String get() = saveDir.toString()

    /** Loads saved settings and downloads. Call once at app start. */
    fun restore() {
        scope.launch {
            val loadedSettings = settingsRepository.load()
            _usage.value = loadedSettings.usage
            _settings.value = migrateSettings(loadedSettings.copy(usage = emptyMap()))
            applyProxy()
            applySpeedLimit()
            applyQueueLimits()
            for (stored in repository.loadAll()) {
                val entry = fromStored(stored)
                entries[entry.id] = entry
                if (entry.id >= nextId) nextId = entry.id + 1
            }
            emit()
            _loaded.value = true
            if (_settings.value.autoResume) {
                entries.values.filter { it.interrupted }.forEach { start(it) }
            }
            // A little after start: finish moving files that could not be moved before and remove leftovers.
            scope.launch {
                delay(8_000)
                try {
                    cleanLeftovers()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Cleaning is a bonus: never let it take the app down.
                }
            }
            runScheduler() // Never returns: keep it last.
        }
    }

    /** Checks the clock every few seconds and fires the schedule's start / stop moments. */
    private suspend fun runScheduler() {
        while (true) {
            tickNetwork()
            tickSchedule()
            tickFinish()
            delay(5.seconds)
        }
    }

    private fun queueOf(id: Long): DownloadQueue =
        _settings.value.queues.firstOrNull { it.id == id } ?: _settings.value.queues.firstOrNull { it.id == MAIN_QUEUE_ID }
            ?: DownloadQueue(MAIN_QUEUE_ID)

    private fun networkBlocked(entry: Entry): Boolean =
        (_settings.value.wifiOnly || queueOf(entry.queueId).wifiOnly) && !isUnmeteredNetwork()

    /** "Wi-Fi only": pauses running downloads on mobile data and continues them when Wi-Fi is back. */
    internal fun tickNetwork() {
        val running = entries.values.filter {
            (it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED) && networkBlocked(it)
        }
        if (running.isNotEmpty()) {
            running.forEach {
                pauseEntry(it)
                it.heldByNetwork = true
            }
            emit()
            persist(force = true)
        }
        entries.values.filter { it.heldByNetwork && !networkBlocked(it) }.forEach {
            it.heldByNetwork = false
            start(it)
        }
    }

    /** Called by the platform when a queue's start / stop moment arrives (e.g. from an Android alarm). */
    fun fireSchedule(queueId: Long, start: Boolean) {
        if (start) {
            if (runsToday(queueOf(queueId))) startQueue(queueId)
        } else {
            pauseQueue(queueId)
        }
    }

    /** True when the queue's schedule is allowed to start downloads today. */
    private fun runsToday(queue: DownloadQueue): Boolean {
        val day = dayOfWeek()
        return day < 0 || ((queue.daysMask shr day) and 1) == 1
    }

    internal fun tickSchedule() {
        val now = minuteOfDay()
        val previous = lastScheduleMinute
        lastScheduleMinute = now
        if (now < 0 || previous < 0 || previous == now) return
        for (q in _settings.value.queues) {
            if (!q.scheduleEnabled || !q.enabled) continue
            if (crossed(previous, now, q.startMinute) && runsToday(q)) startQueue(q.id)
            if (crossed(previous, now, q.stopMinute)) pauseQueue(q.id)
        }
    }

    /** True while the queue's schedule says its downloads should be running (start..stop, on an allowed day). */
    private fun insideScheduleWindow(queue: DownloadQueue): Boolean {
        if (!queue.scheduleEnabled || !queue.enabled) return false
        val now = minuteOfDay()
        if (now < 0 || !runsToday(queue)) return false
        val start = queue.startMinute
        val stop = queue.stopMinute
        return when {
            start == stop -> true
            start < stop -> now in start until stop
            else -> now >= start || now < stop
        }
    }

    /** True when [moment] lies in (previous, now], also across midnight. */
    private fun crossed(previous: Int, now: Int, moment: Int): Boolean =
        if (previous < now) moment in (previous + 1)..now else moment > previous || moment <= now

    /** Fires each queue's "when finished" action once, after the queue completed something and went idle. */
    internal fun tickFinish() {
        for (q in _settings.value.queues) {
            val inQueue = entries.values.filter { it.queueId == q.id }
            val busy = inQueue.any {
                it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED || it.retryPending
            }
            val completed = inQueue.count { it.status == DownloadStatus.COMPLETED }
            if (busy) {
                if (q.id !in finishArmed) finishArmed[q.id] = completed
            } else {
                val armedAt = finishArmed.remove(q.id) ?: continue
                if (completed > armedAt && q.onFinish != QueueFinishAction.NOTHING) onQueueFinished(q)
            }
        }
    }

    // ---------- queues

    private fun migrateSettings(loaded: AppSettings): AppSettings {
        // Old versions had one global schedule; it now belongs to the main queue.
        if (!loaded.scheduleEnabled) return loaded
        val queues = loaded.queues.map {
            if (it.id == MAIN_QUEUE_ID) {
                it.copy(scheduleEnabled = true, startMinute = loaded.scheduleStartMinute, stopMinute = loaded.scheduleStopMinute)
            } else {
                it
            }
        }
        val migrated = loaded.copy(scheduleEnabled = false, queues = queues)
        scope.launch { settingsRepository.save(migrated.copy(usage = _usage.value)) }
        return migrated
    }

    /** Creates a queue and returns its id. */
    fun addQueue(name: String): Long {
        val id = (_settings.value.queues.maxOfOrNull { it.id } ?: MAIN_QUEUE_ID) + 1
        updateSettings { it.copy(queues = it.queues + DownloadQueue(id = id, name = name.trim())) }
        return id
    }

    fun updateQueue(queue: DownloadQueue) {
        updateSettings { s -> s.copy(queues = s.queues.map { if (it.id == queue.id) queue else it }) }
        applyQueueLimits()
    }

    /** Turns a queue off or on. Turning it off pauses its downloads and ignores its schedule. */
    fun setQueueEnabled(id: Long, enabled: Boolean) {
        val queue = _settings.value.queues.firstOrNull { it.id == id } ?: return
        updateQueue(queue.copy(enabled = enabled))
        if (!enabled) pauseQueue(id)
    }

    /** Deletes a queue. Its downloads move to the main queue. The main queue cannot be deleted. */
    fun deleteQueue(id: Long) {
        if (id == MAIN_QUEUE_ID) return
        entries.values.filter { it.queueId == id }.forEach { it.queueId = MAIN_QUEUE_ID }
        updateSettings { s -> s.copy(queues = s.queues.filterNot { it.id == id }) }
        queueLimiters.remove(id)
        finishArmed.remove(id)
        emit()
        persist(force = true)
    }

    /** Moves downloads (one or many) into [queueId]. */
    fun moveToQueue(ids: Set<Long>, queueId: Long) {
        if (_settings.value.queues.none { it.id == queueId }) return
        ids.mapNotNull { entries[it] }.forEach { it.queueId = queueId }
        emit()
        persist(force = true)
    }

    /** Starts every unfinished download of a queue. */
    fun startQueue(queueId: Long) {
        entries.values.filter { it.queueId == queueId && (it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.FAILED) }
            .forEach {
                it.retryAttempt = 0
                start(it)
            }
    }

    fun pauseQueue(queueId: Long) {
        entries.values
            .filter { it.queueId == queueId && (it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED) }
            .forEach { pauseEntry(it) }
        emit()
        persist(force = true)
    }

    private fun applyQueueLimits() {
        for (q in _settings.value.queues) {
            val bytes = q.speedLimitKBps.coerceAtLeast(0) * 1024L
            if (bytes > 0L) queueLimiters.getOrPut(q.id) { RateLimiter() }.bytesPerSec = bytes else queueLimiters.remove(q.id)
        }
    }

    // ---------- bulk actions for the selection mode

    fun startIds(ids: Set<Long>) {
        ids.mapNotNull { entries[it] }
            .filter { it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.FAILED }
            .forEach {
                it.retryAttempt = 0
                start(it)
            }
    }

    fun pauseIds(ids: Set<Long>) {
        ids.mapNotNull { entries[it] }
            .filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }
            .forEach { pauseEntry(it) }
        emit()
        persist(force = true)
    }

    fun removeIds(ids: Set<Long>, deleteFiles: Boolean) {
        ids.forEach { remove(it, deleteFile = deleteFiles) }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        _settings.update(transform)
        applyProxy()
        applySpeedLimit()
        applyQueueLimits()
        saveSettings()
    }

    private fun saveSettings() {
        val value = _settings.value.copy(usage = _usage.value)
        scope.launch { settingsRepository.save(value) }
    }

    /** Sends every new connection through the proxy typed in Settings (if any). */
    private fun applyProxy() {
        val s = _settings.value
        ProxyHolder.current = ProxyConfig(
            kind = when (s.proxyMode) {
                ProxyMode.NONE -> ProxyKind.NONE
                ProxyMode.HTTP -> ProxyKind.HTTP
                ProxyMode.SOCKS -> ProxyKind.SOCKS
            },
            host = s.proxyHost.trim(),
            port = s.proxyPort,
            user = s.proxyUser,
            password = s.proxyPassword,
        )
    }

    /** Days since 1970 in the user's time zone. */
    fun todayEpochDay(): Long = (clock() + utcOffsetMinutes() * 60_000L) / 86_400_000L

    private fun recordBytes(delta: Long) {
        if (delta <= 0L) return
        val day = todayEpochDay()
        _usage.update { old ->
            val map = HashMap(old)
            map[day] = (map[day] ?: 0L) + delta
            if (map.size > 60) map.keys.filter { it < day - 59 }.forEach { map.remove(it) }
            map
        }
        usageDirty = true
    }

    private fun applySpeedLimit() {
        downloader.speedLimitBytesPerSec = _settings.value.speedLimitKBps.coerceAtLeast(0) * 1024L
    }

    /** Options chosen in the Add dialog. */
    data class AddOptions(
        val headers: Map<String, String> = emptyMap(),
        val queueId: Long = MAIN_QUEUE_ID,
        /** File name typed by the user. Blank = take it from the server. */
        val customName: String = "",
        /** Sub-folder inside the download folder. Blank = automatic. */
        val subFolder: String = "",
        val speedLimitKBps: Int = 0,
        /** false = only put it in the list (it waits for the queue schedule or for a tap on Start). */
        val startNow: Boolean = true,
        /** Other links of the same file (tried when the main link keeps failing). */
        val mirrors: List<String> = emptyList(),
        /** HLS streams: link of the chosen quality. Blank = best. */
        val hlsVariant: String = "",
    )

    /** One quality choice of an HLS stream. */
    data class QualityOption(val label: String, val url: String)

    /** What the server says about a link: used by the Add dialog to fill in the name and size. */
    data class LinkInfo(val fileName: String, val totalBytes: Long)

    /** Returns false when [rawUrl] is not a valid http(s) link. */
    fun add(rawUrl: String): Boolean = add(rawUrl, AddOptions())

    /** Adds a link with extra request headers (cookies, referer...), e.g. from the in-app browser. */
    fun addWithHeaders(rawUrl: String, headers: Map<String, String>): Boolean = add(rawUrl, AddOptions(headers = headers))

    fun add(rawUrl: String, options: AddOptions): Boolean {
        val url = rawUrl.trim()
        if (!isSupportedLink(url)) {
            return false
        }
        val duplicate = entries.values.firstOrNull {
            it.status != DownloadStatus.COMPLETED && it.url.equals(url, ignoreCase = true)
        }
        if (duplicate != null) {
            // Same unfinished link already in the list: continue it instead of downloading twice.
            if (options.startNow && (duplicate.status == DownloadStatus.PAUSED || duplicate.status == DownloadStatus.FAILED)) {
                duplicate.retryAttempt = 0
                start(duplicate)
            }
            return true
        }
        val entry = Entry(nextId++, url, clock())
        entry.headers = options.headers
        entry.queueId = if (_settings.value.queues.any { it.id == options.queueId }) options.queueId else MAIN_QUEUE_ID
        entry.customName = options.customName.trim()
        entry.subFolder = options.subFolder.trim()
        entry.speedLimitKBps = options.speedLimitKBps.coerceAtLeast(0)
        entry.mirrors = cleanMirrors(url, options.mirrors)
        entry.hlsVariant = options.hlsVariant.trim()
        entry.fileName = entry.customName.ifBlank { url.substringBefore('?').substringAfterLast('/').ifBlank { url } }
        entries[entry.id] = entry
        if (options.startNow) {
            emit()
            start(entry)
        } else {
            entry.status = DownloadStatus.PAUSED
            entry.needsReprobe = true
            emit()
            persist(force = true)
        }
        return true
    }

    /** Asks the server for the real file name and size of [rawUrl] (null when it cannot be reached). */
    suspend fun inspect(rawUrl: String, headers: Map<String, String> = emptyMap()): LinkInfo? {
        val url = rawUrl.trim()
        if (!isSupportedLink(url)) return null
        return try {
            if (isTorrentLink(url)) {
                val named = Regex("[?&]dn=([^&]+)").find(url)?.groupValues?.get(1)?.replace('+', ' ')?.replace("%20", " ")
                val raw = url.substringBefore('#').substringBefore('?').substringAfterLast('/').removeSuffix(".torrent")
                LinkInfo(named ?: raw.ifBlank { "torrent" }, -1L)
            } else if (DashParser.looksLikeDash(url)) {
                val raw = url.substringBefore('#').substringBefore('?').substringAfterLast('/')
                LinkInfo(raw.removeSuffix(".mpd").ifBlank { "video" } + ".mp4", -1L)
            } else if (Ftp.isFtp(url)) {
                val meta = Ftp.probe(url)
                LinkInfo(meta.fileName, meta.size)
            } else if (HlsPlaylist.looksLikeHls(url)) {
                val raw = url.substringBefore('#').substringBefore('?').substringAfterLast('/')
                LinkInfo(raw.removeSuffix(".m3u8").ifBlank { "video" } + ".ts", -1L)
            } else {
                val info = downloader.probe(url, headers)
                LinkInfo(info.fileName, info.totalBytes)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Adds every http(s) link found in [raw] (one per line, or separated by spaces). Returns how many were added. */
    fun addMany(raw: String): Int {
        // "Copy as cURL" from a browser keeps cookies / referer, which many sites need.
        CurlParser.parse(raw)?.let { return if (add(it.url, AddOptions(headers = it.headers))) 1 else 0 }
        val links = Regex("(?:(?:https?|ftp)://|magnet:\\?)\\S+", RegexOption.IGNORE_CASE)
            .findAll(raw)
            .map { it.value.trimEnd(',', ';') }
            .distinct()
            .toList()
        return links.count { add(it) }
    }

    /** Replaces the link of an unfinished download (e.g. an expired link) and continues it. */
    fun updateUrl(id: Long, rawUrl: String): Boolean {
        val entry = entries[id] ?: return false
        if (entry.status == DownloadStatus.COMPLETED) return false
        val url = rawUrl.trim()
        val valid = isSupportedLink(url) && url.none { it.isWhitespace() }
        if (!valid) return false
        pauseEntry(entry)
        entry.url = url
        entry.retryAttempt = 0
        entry.needsReprobe = true
        emit()
        persist(force = true)
        start(entry)
        return true
    }

    /** Pauses a running download, or resumes a paused / failed one. */
    fun toggle(id: Long) {
        val entry = entries[id] ?: return
        when (entry.status) {
            DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                pauseEntry(entry)
                emit()
                persist(force = true)
            }
            DownloadStatus.PAUSED, DownloadStatus.FAILED -> {
                entry.retryAttempt = 0
                start(entry)
            }
            DownloadStatus.COMPLETED -> Unit
        }
    }

    fun pauseAll() {
        entries.values.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }
            .forEach { pauseEntry(it) }
        emit()
        persist(force = true)
    }

    fun resumeAll() {
        entries.values.filter { it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.FAILED }
            .forEach {
                it.retryAttempt = 0
                start(it)
            }
    }

    fun remove(id: Long, deleteFile: Boolean = false) {
        val entry = entries.remove(id) ?: return
        entry.job?.cancel()
        val destination = entry.destination
        if (destination != null) {
            if (entry.status != DownloadStatus.COMPLETED) {
                deleteQuietly("$destination.part".toPath())
                for (i in 0..1) {
                    deleteQuietly("$destination.t$i".toPath())
                    deleteQuietly("$destination.t$i.part".toPath())
                }
                if (isTorrentLink(entry.url)) deleteTreeQuietly(destination)
            } else if (deleteFile) {
                if (isTorrentLink(entry.url)) deleteTreeQuietly(destination) else deleteQuietly(destination)
            }
        }
        if (deleteFile && entry.publicUri.isNotEmpty()) {
            runCatching { publicStorage?.delete(entry.publicUri) }
        }
        emit()
        persist(force = true)
    }

    fun clearCompleted() {
        entries.values.filter { it.status == DownloadStatus.COMPLETED }.map { it.id }.forEach { remove(it) }
    }

    // ---------- internals

    private fun pauseEntry(entry: Entry) {
        entry.job?.cancel()
        entry.status = DownloadStatus.PAUSED
        entry.waiting = false
        entry.retryPending = false
        entry.heldByNetwork = false
        entry.speed = 0L
    }

    private fun start(entry: Entry) {
        if (entry.job?.isActive == true) return
        entry.heldByNetwork = false
        if (networkBlocked(entry)) {
            entry.heldByNetwork = true
            entry.status = DownloadStatus.PAUSED
            emit()
            return
        }
        entry.job = scope.launch {
            try {
                entry.status = DownloadStatus.QUEUED
                entry.error = null
                entry.speed = 0L
                emit()

                if (isTorrentLink(entry.url)) {
                    runTorrent(entry)
                    return@launch
                }
                if (Ftp.isFtp(entry.url)) {
                    runFtp(entry)
                    return@launch
                }
                if (HlsPlaylist.looksLikeHls(entry.url)) {
                    runHls(entry)
                    return@launch
                }
                if (DashParser.looksLikeDash(entry.url)) {
                    runDash(entry)
                    return@launch
                }

                val previous = entry.info
                val info = if (previous == null || entry.needsReprobe) downloader.probe(entry.url, entry.headers) else previous
                entry.needsReprobe = false
                if (previous != null && (previous.totalBytes != info.totalBytes || previous.acceptsRanges != info.acceptsRanges)) {
                    // The file changed on the server: old partial data is useless.
                    entry.snapshot = null
                    entry.destination?.let { deleteQuietly("$it.part".toPath()) }
                }
                entry.info = info

                val destination = entry.destination ?: run {
                    val name = finalName(entry, info.fileName)
                    uniquePath(name, directoryFor(entry, name)).also { entry.destination = it }
                }
                entry.fileName = destination.name
                val snapshot = entry.snapshot
                    ?: DownloadSnapshot.initial(info, _settings.value.maxConnections).also { entry.snapshot = it }

                awaitSlot(entry) // Queue: only [maxSimultaneous] files download at once.

                entry.status = DownloadStatus.DOWNLOADING
                emit()
                persist(force = true)

                var lastMark = TimeSource.Monotonic.markNow()
                var lastBytes = snapshot.downloadedBytes
                var counted = snapshot.downloadedBytes
                var speed = 0L

                downloader.download(
                    info,
                    destination,
                    snapshot,
                    maxConnections = _settings.value.maxConnections,
                    speedLimitBytesPerSec = entry.speedLimitKBps.coerceAtLeast(0) * 1024L,
                    extraLimiter = queueLimiters[entry.queueId],
                ).collect { snap ->
                    entry.snapshot = snap
                    if (snap.downloadedBytes > counted) recordBytes(snap.downloadedBytes - counted)
                    counted = snap.downloadedBytes
                    val elapsed = lastMark.elapsedNow()
                    if (elapsed >= 500.milliseconds) {
                        val instant = ((snap.downloadedBytes - lastBytes) / elapsed.toDouble(DurationUnit.SECONDS)).toLong()
                        speed = if (speed == 0L) instant else (speed * 0.6 + instant * 0.4).toLong()
                        lastMark = TimeSource.Monotonic.markNow()
                        lastBytes = snap.downloadedBytes
                    }
                    entry.speed = speed
                    emitProgress()
                    persist(force = false)
                }

                publish(entry)
                entry.status = DownloadStatus.COMPLETED
                entry.retryAttempt = 0
                entry.completedAt = clock()
                entry.speed = 0L
                emit()
                persist(force = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                entry.waiting = false
                entry.speed = 0L
                entry.error = e.message ?: e::class.simpleName ?: "Unknown error"
                val retryable = (e as? DownloadException)?.retryable ?: true
                if (entry.mirrors.isNotEmpty() && entry.mirrorTries < entry.mirrors.size && (!retryable || entry.retryAttempt >= 1)) {
                    scheduleMirror(entry)
                } else if (retryable &&
                    (entry.retryAttempt < _settings.value.autoRetryCount || insideScheduleWindow(queueOf(entry.queueId)))
                ) {
                    // Inside a schedule window the download keeps trying (every minute at most) until the window
                    // closes: right after the phone wakes up at night the network is often not ready for a while.
                    scheduleAutoRetry(entry)
                } else {
                    entry.status = DownloadStatus.FAILED
                }
                emit()
                persist(force = true)
            }
        }
    }

    /** The main link keeps failing: swaps it with the next mirror and starts again. */
    private fun scheduleMirror(entry: Entry) {
        val tries = entry.mirrorTries + 1
        val next = entry.mirrors.first()
        entry.mirrors = entry.mirrors.drop(1) + entry.url
        entry.url = next
        entry.needsReprobe = true
        entry.retryAttempt = 0
        entry.mirrorTries = tries
        entry.retryPending = true
        entry.status = DownloadStatus.QUEUED
        scope.launch {
            delay(500.milliseconds)
            if (entry.retryPending && entry.status == DownloadStatus.QUEUED && entries[entry.id] === entry) {
                entry.retryPending = false
                start(entry)
            }
        }
    }

    /** Downloads an FTP link into one file, continuing from the part file after a pause. */
    private suspend fun runFtp(entry: Entry) {
        val meta = Ftp.probe(entry.url)
        if (meta.size <= 0L) throw DownloadException("The FTP server did not report the file size")
        entry.info = RemoteFileInfo(
            url = entry.url,
            finalUrl = entry.url,
            totalBytes = meta.size,
            acceptsRanges = true,
            fileName = meta.fileName,
        )
        val destination = entry.destination ?: run {
            val name = finalName(entry, meta.fileName)
            uniquePath(name, directoryFor(entry, name)).also { entry.destination = it }
        }
        entry.fileName = destination.name
        val part = "$destination.part".toPath()
        var have = if (fileSystem.exists(part)) fileSystem.metadata(part).size ?: 0L else 0L
        if (have > meta.size) {
            deleteQuietly(part)
            have = 0L
        }
        entry.snapshot = ftpSnapshot(meta.size, have)
        awaitSlot(entry)

        entry.status = DownloadStatus.DOWNLOADING
        emit()
        persist(force = true)

        var lastMark = TimeSource.Monotonic.markNow()
        var lastBytes = have
        var counted = have
        var speed = 0L
        val limiters = listOfNotNull(
            downloader.globalSpeedLimiter,
            entry.speedLimitKBps.takeIf { it > 0 }?.let { RateLimiter(it * 1024L) },
            queueLimiters[entry.queueId],
        )
        if (have < meta.size) {
            Ftp.download(entry.url, destination, fileSystem, have, limiters).collect { bytes ->
                entry.snapshot = ftpSnapshot(meta.size, bytes)
                if (bytes > counted) recordBytes(bytes - counted)
                counted = bytes
                val elapsed = lastMark.elapsedNow()
                if (elapsed >= 500.milliseconds) {
                    val instant = ((bytes - lastBytes) / elapsed.toDouble(DurationUnit.SECONDS)).toLong()
                    speed = if (speed == 0L) instant else (speed * 0.6 + instant * 0.4).toLong()
                    lastMark = TimeSource.Monotonic.markNow()
                    lastBytes = bytes
                }
                entry.speed = speed
                emitProgress()
                persist(force = false)
            }
        }
        val written = if (fileSystem.exists(part)) fileSystem.metadata(part).size ?: 0L else 0L
        if (written < meta.size) throw DownloadException("The FTP connection ended early", true)
        fileSystem.atomicMove(part, destination)

        publish(entry)
        entry.status = DownloadStatus.COMPLETED
        entry.retryAttempt = 0
        entry.completedAt = clock()
        entry.speed = 0L
        emit()
        persist(force = true)
    }

    private fun ftpSnapshot(total: Long, done: Long) =
        DownloadSnapshot(total, listOf(SegmentProgress(Segment(0, 0L, total - 1), done.coerceIn(0L, total))))

    /** Waits a growing delay (5s, 10s, 20s... max 60s) and starts the download again. */
    private fun scheduleAutoRetry(entry: Entry) {
        entry.retryAttempt++
        entry.retryPending = true
        entry.status = DownloadStatus.QUEUED
        val wait = minOf(60L, 5L shl (entry.retryAttempt - 1).coerceAtMost(4))
        scope.launch {
            delay(wait.seconds)
            if (entry.retryPending && entry.status == DownloadStatus.QUEUED && entries[entry.id] === entry) {
                entry.retryPending = false
                start(entry)
            }
        }
    }

    /** Moves the finished file from app storage to the public Downloads folder (when enabled and possible). */
    private suspend fun publish(entry: Entry) {
        val direct = publicDirectory()
        if (direct != null) {
            val saved = entry.destination
            if (saved != null && fileSystem.exists(saved)) {
                if (saved.toString().startsWith("$direct/")) {
                    publicStorage?.scan(saved) // Already in the public folder: nothing to copy.
                    return
                }
                // An older file that is still inside the app: move it (a rename, no copy) to the public folder.
                try {
                    val target = uniquePath(saved.name, directoryFor(entry, saved.name))
                    target.parent?.let { fileSystem.createDirectories(it) }
                    fileSystem.atomicMove(saved, target)
                    entry.destination = target
                    entry.fileName = target.name
                    publicStorage?.scan(target)
                    emit()
                    return
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Could not rename (other volume): fall through and copy it like before.
                }
            }
        }
        val storage = publicStorage ?: return
        if (!_settings.value.savePublic && direct == null) return
        val file = entry.destination ?: return
        if (!fileSystem.exists(file)) return
        entry.speed = 0L
        emit()
        val sub = sanitizeFolder(entry.subFolder).ifEmpty {
            if (_settings.value.organizeByCategory) categoryOf(file.name).name else ""
        }
        // One copy at a time: copying needs as much free space as the file itself, and ten copies at once
        // on a nearly full phone would all fail and leave the files inside the app.
        val size = fileSystem.metadataOrNull(file)?.size ?: 0L
        val address = exportLock.withLock {
            if (storage.freeBytes() < size + 32L * 1024 * 1024) {
                null // Not enough room for the copy right now. "Clean up" in Settings (or the next start) tries again.
            } else {
                try {
                    storage.export(file, file.name, sub)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
        }
        if (address != null) {
            entry.publicUri = address
            deleteQuietly(file)
        }
    }

    private val exportLock = Mutex()

    /** What Downly keeps inside the app: unfinished downloads, finished files not yet moved, and leftovers. */
    class StorageReport(val unfinishedBytes: Long, val finishedBytes: Long, val leftoverBytes: Long)

    class CleanResult(val freedBytes: Long, val movedFiles: Int)

    private class StoragePlan(
        val unfinished: Set<Path>,
        val unfinishedRoots: List<String>,
        val finished: Set<Path>,
        val finishedRoots: List<String>,
    )

    private class StorageScan(var unfinished: Long = 0L, var finished: Long = 0L, var leftover: Long = 0L) {
        val orphans = ArrayList<Path>()
    }

    /** Which files in the download folder belong to which downloads. Must run on the controller's thread. */
    private fun buildStoragePlan(): StoragePlan {
        val unfinished = HashSet<Path>()
        val unfinishedRoots = ArrayList<String>()
        val finished = HashSet<Path>()
        val finishedRoots = ArrayList<String>()
        for (entry in entries.values) {
            val destination = entry.destination ?: continue
            val torrent = isTorrentLink(entry.url)
            if (entry.status == DownloadStatus.COMPLETED) {
                finished += destination
                if (torrent) finishedRoots += "$destination/"
            } else {
                unfinished += destination
                for (suffix in listOf(".part", ".t0", ".t1", ".t0.part", ".t1.part")) unfinished += "$destination$suffix".toPath()
                if (torrent) unfinishedRoots += "$destination/"
            }
        }
        return StoragePlan(unfinished, unfinishedRoots, finished, finishedRoots)
    }

    private fun scanFiles(plan: StoragePlan): StorageScan {
        val scan = StorageScan()
        // Only the app's own folder is scanned: files in the public Downloads folder belong to the user.
        if (!fileSystem.exists(appDownloadDir)) return scan
        for (file in fileSystem.listRecursively(appDownloadDir)) {
            val meta = fileSystem.metadataOrNull(file) ?: continue
            if (!meta.isRegularFile) continue
            val size = meta.size ?: 0L
            val text = file.toString()
            when {
                file in plan.unfinished || plan.unfinishedRoots.any { text.startsWith(it) } -> scan.unfinished += size
                file in plan.finished || plan.finishedRoots.any { text.startsWith(it) } -> scan.finished += size
                else -> {
                    scan.leftover += size
                    scan.orphans += file
                }
            }
        }
        return scan
    }

    private fun sizeOf(path: Path): Long {
        val meta = fileSystem.metadataOrNull(path) ?: return 0L
        if (!meta.isDirectory) return meta.size ?: 0L
        return fileSystem.listRecursively(path).sumOf { fileSystem.metadataOrNull(it)?.takeIf { m -> m.isRegularFile }?.size ?: 0L }
    }

    suspend fun storageReport(): StorageReport = try {
        val plan = buildStoragePlan()
        val scan = withContext(Dispatchers.Default) { scanFiles(plan) }
        StorageReport(scan.unfinished, scan.finished, scan.leftover)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        StorageReport(0L, 0L, 0L)
    }

    /**
     * Gives back space that nothing needs: finished files that were already copied to Downloads, files no
     * download refers to (old part files of removed downloads, crashed copies) and, when there is room now,
     * moves finished files that are still inside the app to the Downloads folder.
     */
    suspend fun cleanLeftovers(): CleanResult {
        var freed = 0L
        var moved = 0
        for (entry in entries.values.toList()) {
            if (entry.status != DownloadStatus.COMPLETED) continue
            val path = entry.destination ?: continue
            if (!fileSystem.exists(path)) continue
            if (!path.toString().startsWith("$appDownloadDir/")) continue // Only files inside the app are ours to move or delete.
            val size = sizeOf(path)
            if (entry.publicUri.isNotEmpty()) {
                deleteTreeQuietly(path)
                freed += size
            } else {
                if (isTorrentLink(entry.url)) publishTorrent(entry, path) else publish(entry)
                if (entry.publicUri.isNotEmpty()) {
                    moved++
                    freed += size
                }
            }
        }
        val plan = buildStoragePlan()
        val orphans = withContext(Dispatchers.Default) { scanFiles(plan).orphans }
        val recent = clock() - 5 * 60_000L // A file written in the last minutes may belong to a download that is just starting.
        for (file in orphans) {
            val meta = fileSystem.metadataOrNull(file) ?: continue
            if ((meta.lastModifiedAtMillis ?: 0L) > recent) continue
            freed += meta.size ?: 0L
            deleteQuietly(file)
        }
        emit()
        persist(force = true)
        return CleanResult(freed, moved)
    }

    private suspend fun awaitSlot(entry: Entry) {
        if (hasFreeSlot(entry)) return
        entry.waiting = true
        emit()
        while (!hasFreeSlot(entry)) delay(400)
        entry.waiting = false
    }

    /** Downloads an HLS (.m3u8) stream into a single .ts / .mp4 file. Progress is counted in segments. */
    private suspend fun runHls(entry: Entry) {
        val plan = downloader.hls.prepare(entry.url, entry.headers, entry.hlsVariant.ifBlank { null })
        val total = plan.segments.size
        val existing = entry.destination
        val partExists = existing != null && fileSystem.exists("$existing.part".toPath())
        if (entry.hlsTotal != total || !partExists) {
            entry.hlsDone = 0
            entry.hlsBytes = 0L
            existing?.let { deleteQuietly("$it.part".toPath()) }
        }
        entry.hlsTotal = total

        val destination = existing ?: run {
            val raw = entry.url.substringBefore('#').substringBefore('?').substringAfterLast('/')
            val stem = raw.removeSuffix(".m3u8").removeSuffix(".M3U8").ifBlank { "video" }
            val name = finalName(entry, "$stem.${plan.extension}")
            uniquePath(name, directoryFor(entry, name)).also { entry.destination = it }
        }
        entry.fileName = destination.name
        awaitSlot(entry)

        entry.status = DownloadStatus.DOWNLOADING
        emit()
        persist(force = true)

        var lastMark = TimeSource.Monotonic.markNow()
        var lastBytes = entry.hlsBytes
        var counted = entry.hlsBytes
        var speed = 0L
        downloader.hls.download(
            plan = plan,
            destination = destination,
            startSegment = entry.hlsDone,
            startBytes = entry.hlsBytes,
            parallel = _settings.value.maxConnections,
            headers = entry.headers,
            ownLimiter = entry.speedLimitKBps.takeIf { it > 0 }?.let { RateLimiter(it * 1024L) },
            extraLimiter = queueLimiters[entry.queueId],
        ).collect { p ->
            entry.hlsDone = p.doneSegments
            entry.hlsBytes = p.bytes
            if (p.bytes > counted) recordBytes(p.bytes - counted)
            counted = p.bytes
            val elapsed = lastMark.elapsedNow()
            if (elapsed >= 500.milliseconds) {
                val instant = ((p.bytes - lastBytes) / elapsed.toDouble(DurationUnit.SECONDS)).toLong()
                speed = if (speed == 0L) instant else (speed * 0.6 + instant * 0.4).toLong()
                lastMark = TimeSource.Monotonic.markNow()
                lastBytes = p.bytes
            }
            entry.speed = speed
            emitProgress()
            persist(force = false)
        }

        publish(entry)
        entry.status = DownloadStatus.COMPLETED
        entry.retryAttempt = 0
        entry.completedAt = clock()
        entry.speed = 0L
        emit()
        persist(force = true)
    }

    /**
     * Downloads a DASH (.mpd) stream: the video and the audio track are downloaded as separate files
     * (segments in order) and then joined into one .mp4 / .webm without re-encoding.
     * Progress is counted in segments. A finished track is kept, so a retry only downloads what is missing.
     */
    private suspend fun runDash(entry: Entry) {
        val manifest = downloader.dash.load(entry.url, entry.headers)
        val video = manifest.video.firstOrNull { it.id == entry.hlsVariant } ?: manifest.video.maxByOrNull { it.bandwidth }
        val audio = pickAudio(manifest.audio, video)
        val tracks = listOfNotNull(video, audio)
        if (tracks.isEmpty()) throw DownloadException("The manifest has no video or audio")
        val webm = tracks.all { it.isWebm }
        val extension = if (video != null) (if (webm) "webm" else "mp4") else (if (webm) "webm" else "m4a")

        val destination = entry.destination ?: run {
            val raw = entry.url.substringBefore('#').substringBefore('?').substringAfterLast('/')
            val stem = raw.removeSuffix(".mpd").removeSuffix(".MPD").ifBlank { "video" }
            val name = finalName(entry, "$stem.$extension")
            uniquePath(name, directoryFor(entry, name)).also { entry.destination = it }
        }
        entry.fileName = destination.name
        val plans = tracks.map { downloader.dash.plan(it, entry.headers) }
        entry.hlsTotal = plans.sumOf { it.segments.size }
        awaitSlot(entry)

        entry.status = DownloadStatus.DOWNLOADING
        emit()
        persist(force = true)

        val ownLimiter = entry.speedLimitKBps.takeIf { it > 0 }?.let { RateLimiter(it * 1024L) }
        var doneBefore = 0
        var bytesBefore = 0L
        var counted = 0L
        var lastMark = TimeSource.Monotonic.markNow()
        var lastBytes = 0L
        var speed = 0L
        val files = ArrayList<Path>()
        plans.forEachIndexed { index, plan ->
            val file = "$destination.t$index".toPath()
            files += file
            if (fileSystem.exists(file)) {
                doneBefore += plan.segments.size
                bytesBefore += fileSystem.metadata(file).size ?: 0L
                counted = bytesBefore
                lastBytes = bytesBefore
                return@forEachIndexed
            }
            downloader.hls.download(
                plan = plan,
                destination = file,
                startSegment = 0,
                startBytes = 0L,
                parallel = _settings.value.maxConnections,
                headers = entry.headers,
                ownLimiter = ownLimiter,
                extraLimiter = queueLimiters[entry.queueId],
            ).collect { p ->
                val total = bytesBefore + p.bytes
                entry.hlsDone = doneBefore + p.doneSegments
                entry.hlsBytes = total
                if (total > counted) recordBytes(total - counted)
                counted = total
                val elapsed = lastMark.elapsedNow()
                if (elapsed >= 500.milliseconds) {
                    val instant = ((total - lastBytes) / elapsed.toDouble(DurationUnit.SECONDS)).toLong()
                    speed = if (speed == 0L) instant else (speed * 0.6 + instant * 0.4).toLong()
                    lastMark = TimeSource.Monotonic.markNow()
                    lastBytes = total
                }
                entry.speed = speed
                emitProgress()
                persist(force = false)
            }
            doneBefore += plan.segments.size
            bytesBefore += fileSystem.metadata(file).size ?: 0L
        }

        entry.speed = 0L
        if (files.size == 2) {
            val merger = mediaMerger
            val merged = merger != null && merger.merge(files[0].toString(), files[1].toString(), destination.toString())
            if (!merged) {
                deleteQuietly(destination)
                throw DownloadException("Could not join the audio and video on this device")
            }
            files.forEach { deleteQuietly(it) }
        } else {
            fileSystem.atomicMove(files[0], destination)
        }

        publish(entry)
        entry.status = DownloadStatus.COMPLETED
        entry.retryAttempt = 0
        entry.completedAt = clock()
        emit()
        persist(force = true)
    }

    /** The best audio track that fits the container of the video (WebM with WebM, MP4 with MP4). */
    private fun pickAudio(audio: List<DashTrack>, video: DashTrack?): DashTrack? {
        if (audio.isEmpty()) return null
        val sameContainer = if (video != null) audio.filter { it.isWebm == video.isWebm } else audio
        return sameContainer.ifEmpty { audio }.maxByOrNull { it.bandwidth }
    }

    /**
     * Downloads a torrent (magnet link or .torrent file). The files are written to the download folder and
     * copied to the public Downloads folder when finished. Seeding is not done: the torrent stops when complete.
     */
    private suspend fun runTorrent(entry: Entry) {
        val engine = torrentEngine ?: throw DownloadException("Torrent downloads are not available on this device")
        val meta = engine.meta(entry.url)
        if (meta.totalBytes <= 0L) throw DownloadException("The torrent has no files")
        entry.info = RemoteFileInfo(
            url = entry.url,
            finalUrl = entry.url,
            totalBytes = meta.totalBytes,
            acceptsRanges = true,
            fileName = meta.name,
        )
        val destination = entry.destination ?: (directoryFor(entry, meta.name) / meta.name).also { entry.destination = it }
        val torrentDir = destination.parent ?: saveDir
        entry.fileName = meta.name
        entry.snapshot = ftpSnapshot(meta.totalBytes, entry.snapshot?.downloadedBytes ?: 0L)
        awaitSlot(entry)

        entry.status = DownloadStatus.DOWNLOADING
        emit()
        persist(force = true)

        var counted = -1L
        engine.download(entry.url, torrentDir.toString()).collect { p ->
            val total = if (p.totalBytes > 0L) p.totalBytes else meta.totalBytes
            entry.snapshot = ftpSnapshot(total, p.doneBytes)
            if (counted < 0L) counted = p.doneBytes
            if (p.doneBytes > counted) recordBytes(p.doneBytes - counted)
            counted = p.doneBytes
            entry.speed = p.bytesPerSecond
            emitProgress()
            persist(force = false)
        }

        entry.speed = 0L
        publishTorrent(entry, destination)
        entry.status = DownloadStatus.COMPLETED
        entry.retryAttempt = 0
        entry.completedAt = clock()
        emit()
        persist(force = true)
    }

    /**
     * A single-file torrent is published like any download. A torrent with a folder is copied file by file into
     * a folder with the torrent's name; the list entry then points to its biggest file.
     */
    private suspend fun publishTorrent(entry: Entry, root: Path) {
        val direct = publicDirectory()
        if (direct != null && fileSystem.metadataOrNull(root)?.isDirectory == true) {
            if (root.toString().startsWith("$direct/")) {
                publicStorage?.scan(root)
                return
            }
            try {
                val target = uniquePath(root.name, directoryFor(entry, root.name))
                target.parent?.let { fileSystem.createDirectories(it) }
                fileSystem.atomicMove(root, target)
                entry.destination = target
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Fall through: copy the files one by one.
            }
        }
        if (fileSystem.metadataOrNull(root)?.isDirectory != true) {
            publish(entry)
            return
        }
        val storage = publicStorage ?: return
        if (!_settings.value.savePublic) return
        val base = sanitizeFolder(entry.subFolder)
        var biggest: String? = null
        var biggestSize = -1L
        var failed = false
        val files = fileSystem.listRecursively(root).filter { fileSystem.metadataOrNull(it)?.isRegularFile == true }.toList()
        for (file in files) {
            val inner = file.relativeTo(root).parent?.toString().orEmpty()
            val sub = listOf(base, root.name, inner).filter { it.isNotBlank() }.joinToString("/")
            val fileSize = fileSystem.metadataOrNull(file)?.size ?: 0L
            val address = exportLock.withLock {
                if (storage.freeBytes() < fileSize + 32L * 1024 * 1024) {
                    null
                } else {
                    try {
                        storage.export(file, file.name, sub)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            if (address == null) {
                failed = true
                continue
            }
            val size = fileSystem.metadataOrNull(file)?.size ?: 0L
            deleteQuietly(file)
            if (size > biggestSize) {
                biggestSize = size
                biggest = address
            }
        }
        if (biggest != null) entry.publicUri = biggest
        if (!failed) deleteTreeQuietly(root)
    }

    /**
     * A waiting download may start when a slot is free (app-wide and in its queue) and fewer better-ranked
     * downloads wait than there are free slots.
     */
    private fun hasFreeSlot(entry: Entry): Boolean {
        val globalFree = maxParallel() - runningCount()
        if (globalFree <= 0) return false
        val better = entries.values.filter { it !== entry && it.waiting && ranksBefore(it, entry) }
        if (better.size >= globalFree) return false
        val limit = queueOf(entry.queueId).maxSimultaneous
        if (limit > 0) {
            val queueFree = limit - entries.values.count { it.queueId == entry.queueId && it.status == DownloadStatus.DOWNLOADING }
            if (queueFree <= 0) return false
            if (better.count { it.queueId == entry.queueId } >= queueFree) return false
        }
        return true
    }

    private fun ranksBefore(a: Entry, b: Entry): Boolean = when {
        a.priority != b.priority -> a.priority > b.priority
        a.createdAt != b.createdAt -> a.createdAt < b.createdAt
        else -> a.id < b.id
    }

    /** Puts the download first in the queue (it starts before the others when slots are limited). */
    fun moveToTop(id: Long) {
        val entry = entries[id] ?: return
        entry.priority = (entries.values.maxOfOrNull { it.priority } ?: 0) + 1
        emit()
        persist(force = true)
    }

    fun moveToBottom(id: Long) {
        val entry = entries[id] ?: return
        entry.priority = (entries.values.minOfOrNull { it.priority } ?: 0) - 1
        emit()
        persist(force = true)
    }

    /** Limits one download (KB/s, 0 = none). A running download restarts to apply it. */
    fun setSpeedLimit(id: Long, kbPerSec: Int) {
        val entry = entries[id] ?: return
        entry.speedLimitKBps = kbPerSec.coerceAtLeast(0)
        val wasActive = entry.status == DownloadStatus.DOWNLOADING || entry.status == DownloadStatus.QUEUED
        if (wasActive) pauseEntry(entry)
        emit()
        persist(force = true)
        if (wasActive) start(entry)
    }

    /** The quality choices of an HLS link, best first. Empty when the link is not a multi-quality playlist. */
    suspend fun hlsQualities(rawUrl: String, headers: Map<String, String> = emptyMap()): List<QualityOption> {
        val url = rawUrl.trim()
        val dash = DashParser.looksLikeDash(url)
        if (!isSupportedLink(url) || !(dash || HlsPlaylist.looksLikeHls(url))) return emptyList()
        return try {
            val choices = if (dash) downloader.dash.qualities(url, headers) else downloader.hls.variants(url, headers)
            choices.map { QualityOption(it.label, it.url) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Sets the other links of the same file (one per element). Invalid and repeated links are dropped. */
    fun setMirrors(id: Long, links: List<String>) {
        val entry = entries[id] ?: return
        entry.mirrors = cleanMirrors(entry.url, links)
        emit()
        persist(force = true)
    }

    private fun cleanMirrors(main: String, links: List<String>): List<String> =
        links.map { it.trim() }
            .filter { isSupportedLink(it) && it.none { c -> c.isWhitespace() } && !it.equals(main, ignoreCase = true) }
            .distinct()

    /** The whole list of downloads and the settings as text, for the Backup button. */
    fun exportBackup(): String =
        Backup.encode(_settings.value.copy(usage = _usage.value), entries.values.map { it.toStored() })

    /**
     * Restores settings and adds the downloads of a backup that are not in the list yet (they start paused,
     * because the files themselves are not part of a backup). Returns how many downloads were added,
     * or null when [text] is not a Downly backup.
     */
    fun importBackup(text: String): Int? {
        val file = Backup.decode(text) ?: return null
        updateSettings {
            file.settings.copy(
                onboardingDone = true,
                usage = emptyMap(),
                customFolderUri = it.customFolderUri,
                customFolderName = it.customFolderName,
            )
        }
        var added = 0
        for (stored in file.downloads) {
            if (entries.values.any { it.url == stored.url }) continue
            val fresh = stored.copy(
                id = nextId++,
                status = DownloadStatus.PAUSED.name,
                destination = null,
                publicUri = "",
                finalUrl = null,
                totalBytes = -1L,
                segments = emptyList(),
                error = null,
                completedAt = null,
                hlsDone = 0,
                hlsTotal = 0,
                hlsBytes = 0L,
            )
            val entry = fromStored(fresh)
            entries[entry.id] = entry
            added++
        }
        emit()
        persist(force = true)
        return added
    }

    /** Renames a finished file (in the public Downloads folder or in app storage). Returns false when that failed. */
    suspend fun rename(id: Long, rawName: String): Boolean {
        val entry = entries[id] ?: return false
        if (entry.status != DownloadStatus.COMPLETED) return false
        val clean = rawName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        if (clean.isBlank() || clean == entry.fileName) return false
        if (entry.publicUri.isNotEmpty()) {
            val address = try {
                publicStorage?.rename(entry.publicUri, clean)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } ?: return false
            entry.publicUri = address
        } else {
            val old = entry.destination ?: return false
            if (!fileSystem.exists(old)) return false
            val target = (old.parent ?: return false) / clean
            if (fileSystem.exists(target)) return false
            fileSystem.atomicMove(old, target)
            entry.destination = target
        }
        entry.fileName = clean
        emit()
        persist(force = true)
        return true
    }

    /** Reads a web page and returns the file links on it (empty list when none, null when it cannot be read). */
    suspend fun grabLinks(pageUrl: String): List<String>? {
        val url = pageUrl.trim()
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return null
        return try {
            val html = downloader.fetchText(url)
            SiteGrabber.fileLinks(SiteGrabber.extractLinks(html, url))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Adds the chosen links to the list. Returns how many were added. */
    fun addLinks(links: List<String>): Int = links.count { add(it) }

    enum class ChecksumResult { MATCH, MISMATCH, INVALID_INPUT, NO_FILE }

    /** Compares the finished file with a hash the website published (MD5, SHA-1 or SHA-256). */
    suspend fun verifyChecksum(id: Long, expectedInput: String): ChecksumResult {
        val entry = entries[id] ?: return ChecksumResult.NO_FILE
        val path = entry.destination
        val publicSource = if (entry.publicUri.isNotEmpty()) publicStorage?.open(entry.publicUri) else null
        val onDisk = path != null && fileSystem.exists(path)
        if (entry.status != DownloadStatus.COMPLETED || (publicSource == null && !onDisk)) {
            publicSource?.close()
            return ChecksumResult.NO_FILE
        }
        val expected = FileChecksum.normalize(expectedInput)
        val algorithm = ChecksumAlgorithm.fromHexLength(expected.length)
        if (!FileChecksum.isValid(expected) || algorithm == null) {
            publicSource?.close()
            return ChecksumResult.INVALID_INPUT
        }
        return try {
            val actual = if (publicSource != null) {
                FileChecksum.computeFrom(publicSource, algorithm)
            } else {
                FileChecksum.compute(fileSystem, path!!, algorithm)
            }
            if (actual == expected) ChecksumResult.MATCH else ChecksumResult.MISMATCH
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ChecksumResult.NO_FILE
        }
    }

    private fun runningCount(): Int = entries.values.count { it.status == DownloadStatus.DOWNLOADING }

    /** The app-wide limit of downloads running at once. 0 in Settings means unlimited. */
    private fun maxParallel(): Int = _settings.value.maxSimultaneous.let { if (it <= 0) Int.MAX_VALUE else it }

    private var lastProgressEmit = TimeSource.Monotonic.markNow()
    private var progressEmitPending = false

    /**
     * Download progress arrives many times a second. Every [emit] rebuilds the whole list and redraws the screens,
     * which makes swiping and scrolling stutter while something downloads. So progress reaches the screen at most
     * four times a second; the last update of a burst is always delivered. Real changes (start, pause, finish)
     * still use [emit] and show at once.
     */
    private fun emitProgress() {
        if (lastProgressEmit.elapsedNow() >= 250.milliseconds) {
            lastProgressEmit = TimeSource.Monotonic.markNow()
            emit()
            return
        }
        if (progressEmitPending) return
        progressEmitPending = true
        scope.launch {
            delay(250.milliseconds)
            progressEmitPending = false
            lastProgressEmit = TimeSource.Monotonic.markNow()
            emit()
        }
    }

    private fun emit() {
        _items.value = entries.values
            .sortedWith(compareByDescending<Entry> { it.createdAt }.thenByDescending { it.id })
            .map { it.toItem() }
    }

    private fun persist(force: Boolean) {
        if (!force && lastPersist.elapsedNow() < 2.seconds) return
        lastPersist = TimeSource.Monotonic.markNow()
        if (usageDirty) {
            usageDirty = false
            saveSettings()
        }
        val list = entries.values.map { it.toStored() }
        scope.launch { repository.saveAll(list) }
    }

    private fun Entry.toItem(): DownloadItem {
        val snap = snapshot
        val isHls = hlsTotal > 0
        val total = when {
            !isHls -> info?.totalBytes ?: -1L
            status == DownloadStatus.COMPLETED -> hlsBytes
            hlsDone > 0 -> hlsBytes * hlsTotal / hlsDone // estimate from the segments finished so far
            else -> -1L
        }
        return DownloadItem(
            id = id,
            url = url,
            fileName = fileName,
            totalBytes = total,
            downloadedBytes = when {
                isHls -> hlsBytes
                status == DownloadStatus.COMPLETED && total > 0 -> total
                else -> snap?.downloadedBytes ?: 0L
            },
            speedBytesPerSec = speed,
            status = status,
            error = error,
            destinationPath = publicUri.ifEmpty { null } ?: destination?.toString(),
            segmentProgress = snap?.segments?.map {
                if (it.segment.length > 0) (it.downloaded.toFloat() / it.segment.length).coerceIn(0f, 1f) else 1f
            } ?: emptyList(),
            createdAt = createdAt,
            waiting = waiting,
            priority = priority,
            speedLimitKBps = speedLimitKBps,
            queueId = queueId,
            mirrors = mirrors,
            completedAt = completedAt ?: 0L,
        )
    }

    private fun Entry.toStored(): StoredDownload = StoredDownload(
        id = id,
        url = url,
        fileName = fileName,
        status = status.name,
        destination = destination?.toString(),
        finalUrl = info?.finalUrl,
        totalBytes = info?.totalBytes ?: -1L,
        acceptsRanges = info?.acceptsRanges ?: false,
        segments = snapshot?.segments?.map {
            StoredSegment(it.segment.index, it.segment.start, it.segment.endInclusive, it.downloaded)
        } ?: emptyList(),
        error = error,
        createdAt = createdAt,
        completedAt = completedAt,
        headers = headers,
        priority = priority,
        speedLimitKBps = speedLimitKBps,
        hlsDone = hlsDone,
        hlsTotal = hlsTotal,
        hlsBytes = hlsBytes,
        queueId = queueId,
        subFolder = subFolder,
        customName = customName,
        publicUri = publicUri,
        mirrors = mirrors,
        hlsVariant = hlsVariant,
    )

    private fun fromStored(s: StoredDownload): Entry {
        val entry = Entry(s.id, s.url, s.createdAt)
        entry.fileName = s.fileName
        entry.destination = s.destination?.toPath()
        entry.error = s.error
        entry.completedAt = s.completedAt
        entry.headers = s.headers
        entry.priority = s.priority
        entry.speedLimitKBps = s.speedLimitKBps
        entry.hlsDone = s.hlsDone
        entry.hlsTotal = s.hlsTotal
        entry.hlsBytes = s.hlsBytes
        entry.queueId = if (_settings.value.queues.any { it.id == s.queueId }) s.queueId else MAIN_QUEUE_ID
        entry.subFolder = s.subFolder
        entry.customName = s.customName
        entry.publicUri = s.publicUri
        entry.mirrors = s.mirrors
        entry.hlsVariant = s.hlsVariant

        val stored = runCatching { DownloadStatus.valueOf(s.status) }.getOrDefault(DownloadStatus.PAUSED)
        entry.interrupted = stored == DownloadStatus.DOWNLOADING || stored == DownloadStatus.QUEUED
        entry.status = if (entry.interrupted) DownloadStatus.PAUSED else stored

        val finalUrl = s.finalUrl
        if (finalUrl != null && s.totalBytes > 0) {
            entry.info = RemoteFileInfo(
                url = s.url,
                finalUrl = finalUrl,
                totalBytes = s.totalBytes,
                acceptsRanges = s.acceptsRanges,
                fileName = s.fileName,
            )
            if (s.segments.isNotEmpty()) {
                entry.snapshot = DownloadSnapshot(
                    s.totalBytes,
                    s.segments.map { SegmentProgress(Segment(it.index, it.start, it.end), it.downloaded) },
                )
            }
        }
        entry.needsReprobe = entry.status != DownloadStatus.COMPLETED
        return entry
    }

    private fun deleteTreeQuietly(path: Path) {
        try {
            fileSystem.deleteRecursively(path, mustExist = false)
        } catch (e: Exception) {
            // Best effort cleanup.
        }
    }

    private fun deleteQuietly(path: Path) {
        try {
            fileSystem.delete(path, mustExist = false)
        } catch (e: Exception) {
            // Best effort cleanup.
        }
    }

    /** Avoids overwriting existing files: "a.zip" -> "a (1).zip". */
    private fun directoryFor(entry: Entry, fileName: String): Path {
        val sub = sanitizeFolder(entry.subFolder)
        return when {
            sub.isNotEmpty() -> saveDir / sub
            _settings.value.organizeByCategory -> saveDir / categoryOf(fileName).name
            else -> saveDir
        }
    }

    private fun sanitizeFolder(raw: String): String =
        raw.split('/', '\\').map { it.trim() }.filter { it.isNotEmpty() && it != "." && it != ".." }
            .joinToString("/") { it.replace(Regex("[:*?\"<>|]"), "_") }

    /** Uses the name typed by the user (keeping the real extension when they leave it out). */
    private fun finalName(entry: Entry, detected: String): String {
        val custom = entry.customName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        if (custom.isEmpty()) return detected
        if (custom.contains('.')) return custom
        val ext = detected.substringAfterLast('.', "")
        return if (ext.isEmpty()) custom else "$custom.$ext"
    }

    private fun uniquePath(fileName: String, directory: Path): Path {
        val dot = fileName.lastIndexOf('.')
        val base = if (dot > 0) fileName.substring(0, dot) else fileName
        val ext = if (dot > 0) fileName.substring(dot) else ""
        var candidate = directory / fileName
        var n = 1
        while (isTaken(candidate)) {
            candidate = directory / "$base ($n)$ext"
            n++
        }
        return candidate
    }

    private fun isTaken(path: Path): Boolean =
        fileSystem.exists(path) ||
            fileSystem.exists("$path.part".toPath()) ||
            entries.values.any { it.destination == path }
}

private fun isSupportedLink(url: String): Boolean =
    url.startsWith("http://", ignoreCase = true) ||
        url.startsWith("https://", ignoreCase = true) ||
        url.startsWith("ftp://", ignoreCase = true) ||
        url.startsWith("magnet:", ignoreCase = true)

/** Magnet links and links to .torrent files (add `#.torrent` to a link whose address does not end in .torrent). */
private fun isTorrentLink(url: String): Boolean {
    val lower = url.lowercase()
    if (lower.startsWith("magnet:")) return true
    return lower.substringBefore('#').substringBefore('?').endsWith(".torrent") || lower.endsWith("#.torrent")
}
