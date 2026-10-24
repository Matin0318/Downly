package app.downly.data

import kotlinx.serialization.builtins.ListSerializer
import okio.FileSystem
import okio.Path

interface DownloadRepository {
    suspend fun loadAll(): List<StoredDownload>
    suspend fun saveAll(items: List<StoredDownload>)
}

interface SettingsRepository {
    suspend fun load(): AppSettings
    suspend fun save(settings: AppSettings)
}

/** Simple, dependency-free storage. Can be swapped for a database later without touching other code. */
class JsonDownloadRepository(fileSystem: FileSystem, directory: Path) : DownloadRepository {
    private val file = AtomicJsonFile(
        fileSystem = fileSystem,
        path = directory / "downloads.json",
        serializer = ListSerializer(StoredDownload.serializer()),
        default = { emptyList() },
    )

    override suspend fun loadAll(): List<StoredDownload> = file.read()
    override suspend fun saveAll(items: List<StoredDownload>) = file.write(items)
}

/** Locks and unlocks small secrets (like the proxy password) with a key that never leaves the device. */
interface SecretCodec {
    fun encrypt(plain: String): String
    fun decrypt(stored: String): String
}

private const val ENCRYPTED_PREFIX = "enc:"

class JsonSettingsRepository(
    fileSystem: FileSystem,
    directory: Path,
    private val codec: SecretCodec? = null,
) : SettingsRepository {
    private val file = AtomicJsonFile(
        fileSystem = fileSystem,
        path = directory / "settings.json",
        serializer = AppSettings.serializer(),
        default = { AppSettings() },
    )

    override suspend fun load(): AppSettings {
        val settings = file.read()
        val stored = settings.proxyPassword
        if (codec == null || !stored.startsWith(ENCRYPTED_PREFIX)) return settings
        val plain = try {
            codec.decrypt(stored.removePrefix(ENCRYPTED_PREFIX))
        } catch (e: Exception) {
            "" // The key is gone (new phone, restored app data): the password has to be typed again.
        }
        return settings.copy(proxyPassword = plain)
    }

    override suspend fun save(settings: AppSettings) {
        val locked = if (codec != null && settings.proxyPassword.isNotEmpty()) {
            try {
                settings.copy(proxyPassword = ENCRYPTED_PREFIX + codec.encrypt(settings.proxyPassword))
            } catch (e: Exception) {
                settings.copy(proxyPassword = "") // Never write the password in plain text when locking failed.
            }
        } else {
            settings
        }
        file.write(locked)
    }
}
