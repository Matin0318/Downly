package app.downly.data

import kotlinx.serialization.Serializable

/** Id of the built-in queue every download belongs to unless the user picks another one. */
const val MAIN_QUEUE_ID: Long = 1L

@Serializable
enum class QueueFinishAction { NOTHING, INTERNET_PANEL }

/**
 * A download queue with its own rules. Downloads in a queue follow its schedule, parallel limit,
 * speed limit and network rule.
 */
@Serializable
data class DownloadQueue(
    val id: Long,
    /** Empty for the main queue: the UI shows a translated default name. */
    val name: String = "",
    val scheduleEnabled: Boolean = false,
    /** Minutes since midnight, 0..1439 (any minute, not only :00 / :30). */
    val startMinute: Int = 2 * 60,
    val stopMinute: Int = 7 * 60,
    /** Files of this queue downloading at once. 0 = use the app-wide setting. */
    val maxSimultaneous: Int = 0,
    /** Speed limit shared by the whole queue, KB/s. 0 = none. */
    val speedLimitKBps: Int = 0,
    val wifiOnly: Boolean = false,
    val onFinish: QueueFinishAction = QueueFinishAction.NOTHING,
    /** Days the schedule starts downloads on. Bit 0 = Sunday ... bit 6 = Saturday. 127 = every day. */
    val daysMask: Int = 127,
    /** A disabled queue never starts by its schedule and its downloads are paused. */
    val enabled: Boolean = true,
)

@Serializable
data class StoredSegment(
    val index: Int,
    val start: Long,
    val end: Long,
    val downloaded: Long,
)

/** One download as saved on disk. Status is stored as text so new statuses never break old files. */
@Serializable
data class StoredDownload(
    val id: Long,
    val url: String,
    val fileName: String,
    val status: String,
    val destination: String? = null,
    val finalUrl: String? = null,
    val totalBytes: Long = -1L,
    val acceptsRanges: Boolean = false,
    val segments: List<StoredSegment> = emptyList(),
    val error: String? = null,
    val createdAt: Long = 0L,
    val completedAt: Long? = null,
    /** Extra request headers (Cookie, Referer...) copied from a cURL command. */
    val headers: Map<String, String> = emptyMap(),
    /** Higher number = starts earlier when the queue is full. */
    val priority: Int = 0,
    /** Limit for this download only, in KB/s. 0 = none. */
    val speedLimitKBps: Int = 0,
    /** HLS streams: finished segments, total segments and bytes written (0 for normal files). */
    val hlsDone: Int = 0,
    val hlsTotal: Int = 0,
    val hlsBytes: Long = 0L,
    val queueId: Long = MAIN_QUEUE_ID,
    /** Sub-folder inside the download folder chosen in the Add dialog. Empty = automatic. */
    val subFolder: String = "",
    /** File name typed by the user. Empty = use the name from the server. */
    val customName: String = "",
    /** content:// address of the finished file in the public Downloads folder (empty = file stays in app storage). */
    val publicUri: String = "",
    /** Other links of the same file. When the main link keeps failing, the next one is tried. */
    val mirrors: List<String> = emptyList(),
    /** HLS streams: the quality (variant playlist link) the user picked. Empty = best. */
    val hlsVariant: String = "",
)

@Serializable
enum class ProxyMode { NONE, HTTP, SOCKS }

@Serializable
enum class AppLanguage { EN, FA }

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class AppSettings(
    val language: AppLanguage = AppLanguage.EN,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val maxConnections: Int = 16,
    val autoResume: Boolean = false,
    val notificationsEnabled: Boolean = true,
    /** How many files may download at the same time; the rest wait in the queue. */
    val maxSimultaneous: Int = 3,
    /** Global speed limit in KB/s. 0 = unlimited. */
    val speedLimitKBps: Int = 0,
    /** How many times a failed download is retried automatically (with growing delay). 0 = never. */
    val autoRetryCount: Int = 3,
    /** Save files into sub-folders by type (Video, Audio, Documents...), like IDM categories. */
    val organizeByCategory: Boolean = false,
    /** Scheduler: resume everything at [scheduleStartMinute] and pause it at [scheduleStopMinute]. */
    val scheduleEnabled: Boolean = false,
    /** Minutes since midnight, 0..1439. */
    val scheduleStartMinute: Int = 2 * 60,
    val scheduleStopMinute: Int = 7 * 60,
    /** Only download on unmetered networks (Wi-Fi / Ethernet). */
    val wifiOnly: Boolean = false,
    val queues: List<DownloadQueue> = listOf(DownloadQueue(id = MAIN_QUEUE_ID)),
    /** Move finished files to the public Downloads folder (visible in Files / Gallery, survives uninstall). */
    val savePublic: Boolean = true,
    /** Folder chosen by the user (Android tree address). Empty = Downloads/Downly. */
    val customFolderUri: String = "",
    val customFolderName: String = "",
    val onboardingDone: Boolean = false,
    /** True once the "save directly to Downloads" question was shown. */
    val storagePromptShown: Boolean = false,
    val proxyMode: ProxyMode = ProxyMode.NONE,
    val proxyHost: String = "",
    val proxyPort: Int = 0,
    val proxyUser: String = "",
    val proxyPassword: String = "",
    /** Bytes downloaded per day (key = days since 1970, in the user's time zone). Kept for 60 days. */
    val usage: Map<Long, Long> = emptyMap(),
)
