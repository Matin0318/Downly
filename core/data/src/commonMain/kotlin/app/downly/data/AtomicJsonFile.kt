package app.downly.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * Reads and writes one JSON file safely: writes go to a temp file first and are then moved
 * into place, so a crash in the middle never leaves a half-written file.
 * A corrupt file is renamed to `<name>.corrupt` and the default value is returned.
 */
internal class AtomicJsonFile<T>(
    private val fileSystem: FileSystem,
    private val path: Path,
    private val serializer: KSerializer<T>,
    private val default: () -> T,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val mutex = Mutex()

    suspend fun read(): T = mutex.withLock {
        withContext(dispatcher) {
            if (!fileSystem.exists(path)) {
                default()
            } else {
                try {
                    json.decodeFromString(serializer, fileSystem.read(path) { readUtf8() })
                } catch (e: Exception) {
                    try {
                        fileSystem.atomicMove(path, "$path.corrupt".toPath())
                    } catch (ignored: Exception) {
                        // Best effort backup.
                    }
                    default()
                }
            }
        }
    }

    suspend fun write(value: T) {
        mutex.withLock {
            withContext(dispatcher) {
                path.parent?.let { fileSystem.createDirectories(it) }
                val temp = "$path.tmp".toPath()
                fileSystem.write(temp) { writeUtf8(json.encodeToString(serializer, value)) }
                fileSystem.atomicMove(temp, path)
            }
        }
    }
}
