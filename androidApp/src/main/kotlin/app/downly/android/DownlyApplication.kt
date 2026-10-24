package app.downly.android

import android.app.Application
import android.content.Intent
import android.os.Environment
import androidx.core.content.ContextCompat
import app.downly.core.engine.DownlyHttpClient
import app.downly.core.engine.SegmentedDownloader
import app.downly.core.model.DownloadStatus
import app.downly.data.JsonDownloadRepository
import app.downly.data.JsonSettingsRepository
import app.downly.ui.downloads.DownloadsController
import app.downly.ui.stringsFor
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path.Companion.toPath

class DownlyApplication : Application() {
    lateinit var controller: DownloadsController
        private set
    lateinit var notifications: DownloadNotifications
        private set

    // An unexpected error in background work is written to a file instead of closing the app.
    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, error -> logProblem(error) },
    )
    private var lastServiceRequest = 0L
    private var lastWidgetRefresh = 0L

    override fun onCreate() {
        super.onCreate()
        notifications = DownloadNotifications(this)
        installCrashLog()

        val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
        val dataDir = filesDir.absolutePath.toPath() / "data"
        val publicStorage = AndroidPublicStorage(this) { controller.settings.value.customFolderUri }
        controller = DownloadsController(
            initialSettings = LookCache.settings(this),
            downloader = SegmentedDownloader(DownlyHttpClient.create(), FileSystem.SYSTEM),
            fileSystem = FileSystem.SYSTEM,
            downloadDir = downloadsDir.absolutePath.toPath(),
            scope = appScope,
            repository = JsonDownloadRepository(FileSystem.SYSTEM, dataDir),
            settingsRepository = JsonSettingsRepository(FileSystem.SYSTEM, dataDir, KeystoreSecretCodec()),
            clock = { System.currentTimeMillis() },
            isUnmeteredNetwork = { isUnmeteredNetwork() },
            publicStorage = publicStorage,
            publicDirectory = {
                // A folder picked in Settings keeps the old way (copy at the end): it cannot be written directly.
                if (controller.settings.value.customFolderUri.isBlank() && StorageAccess.has(this)) {
                    StorageAccess.publicDirectory().absolutePath.toPath()
                } else {
                    null
                }
            },
            dayOfWeek = { java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) - 1 },
            onQueueFinished = { queue ->
                val strings = stringsFor(controller.settings.value.language)
                notifications.notifyQueueFinished(queue.name.ifBlank { strings.queueMain }, strings)
            },
            mediaMerger = AndroidMediaMerger(),
            torrentEngine = AndroidTorrentEngine(this),
            utcOffsetMinutes = { java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000 },
            minuteOfDay = {
                val c = java.util.Calendar.getInstance()
                c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)
            },
        )
        controller.restore()
        // Remove half-finished copies a closed app left in the Downloads folder.
        runWhenLoaded { Thread { publicStorage.deleteStalePending() }.start() }
        // A queue whose start time passed while the app was closed or the phone off starts now (if still in its window).
        runWhenLoaded { ScheduleAlarms.catchUp(this, controller) }
        observeDownloads()
        observeSchedule()
        observeLook()
    }

    /** Keeps the cached language and theme in line with the real settings. */
    private fun observeLook() {
        appScope.launch {
            controller.loaded.first { it }
            controller.settings
                .map { it.language to it.theme }
                .distinctUntilChanged()
                .collect { (language, theme) -> LookCache.save(this@DownlyApplication, language, theme) }
        }
    }

    private fun isUnmeteredNetwork(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        return !cm.isActiveNetworkMetered
    }

    /** Runs [block] on the main thread once saved settings and downloads are loaded. */
    fun runWhenLoaded(block: () -> Unit) {
        appScope.launch {
            controller.loaded.first { it }
            block()
        }
    }

    /** Keeps the system alarms in line with the schedule settings. */
    private fun observeSchedule() {
        appScope.launch {
            controller.loaded.first { it }
            controller.settings
                .map { s -> s.queues.filter { it.scheduleEnabled && it.enabled }.map { Triple(it.id, it.startMinute, it.stopMinute) } }
                .distinctUntilChanged()
                .collect { ScheduleAlarms.apply(this@DownlyApplication, controller.settings.value.queues) }
        }
    }

    /** Starts the foreground service while something downloads and posts finish/fail notifications. */
    private fun observeDownloads() {
        appScope.launch {
            var previous = emptyMap<Long, DownloadStatus>()
            controller.items.collect { items ->
                if (items.any { it.isActive } && !DownloadService.running) requestService()

                val settings = controller.settings.value
                if (settings.notificationsEnabled) {
                    val strings = stringsFor(settings.language)
                    for (item in items) {
                        val before = previous[item.id] ?: continue
                        val wasActive = before == DownloadStatus.DOWNLOADING || before == DownloadStatus.QUEUED
                        if (!wasActive) continue
                        when (item.status) {
                            DownloadStatus.COMPLETED -> notifications.notifyCompleted(item, strings)
                            DownloadStatus.FAILED -> notifications.notifyFailed(item, strings)
                            else -> Unit
                        }
                    }
                }
                val now = System.currentTimeMillis()
                val statusChanged = previous.keys != items.map { it.id }.toSet() ||
                    items.any { previous[it.id] != it.status }
                if (statusChanged || now - lastWidgetRefresh > 2000) {
                    lastWidgetRefresh = now
                    DownlyWidget.refresh(this@DownlyApplication)
                }
                previous = items.associate { it.id to it.status }
            }
        }
    }

    private fun logProblem(error: Throwable) {
        android.util.Log.e("Downly", "Background error", error)
        try {
            val folder = getExternalFilesDir(null) ?: filesDir
            java.io.File(folder, "last_error.txt").writeText(android.util.Log.getStackTraceString(error))
        } catch (ignored: Exception) {
            // Best effort.
        }
    }

    /**
     * Saves the stack trace of a crash to `last_crash.txt` in the app's files folder
     * (Android/data/app.downly.android/files), so a crash can be read and reported even without a computer.
     */
    private fun installCrashLog() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val folder = getExternalFilesDir(null) ?: filesDir
                java.io.File(folder, "last_crash.txt").writeText(
                    "Downly crash in thread ${thread.name}\n" + android.util.Log.getStackTraceString(error),
                )
            } catch (ignored: Exception) {
                // Nothing to do when even writing fails.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun requestService() {
        val now = System.currentTimeMillis()
        if (now - lastServiceRequest < 1500) return
        lastServiceRequest = now
        try {
            ContextCompat.startForegroundService(this, Intent(this, DownloadService::class.java))
        } catch (e: Exception) {
            // Android may refuse to start a foreground service from the background.
        }
    }
}
