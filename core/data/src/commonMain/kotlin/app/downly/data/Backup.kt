package app.downly.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Everything the user can restore on a new phone: settings (queues, proxy...) and the download list.
 * The proxy password is left out on purpose, so a backup file is safe to share.
 */
@Serializable
class BackupFile(
    val app: String = "Downly",
    val version: Int = 1,
    val settings: AppSettings,
    val downloads: List<StoredDownload>,
)

object Backup {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(settings: AppSettings, downloads: List<StoredDownload>): String =
        json.encodeToString(BackupFile.serializer(), BackupFile(settings = settings.copy(proxyPassword = ""), downloads = downloads))

    /** Returns null when [text] is not a Downly backup. */
    fun decode(text: String): BackupFile? = try {
        json.decodeFromString(BackupFile.serializer(), text).takeIf { it.app == "Downly" }
    } catch (e: Exception) {
        null
    }
}
