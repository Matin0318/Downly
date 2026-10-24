package app.downly.core.engine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.buffer

enum class ChecksumAlgorithm(val javaName: String, val hexLength: Int) {
    MD5("MD5", 32),
    SHA1("SHA-1", 40),
    SHA256("SHA-256", 64),
    ;

    companion object {
        fun fromHexLength(length: Int): ChecksumAlgorithm? = values().firstOrNull { it.hexLength == length }
    }
}

internal interface Digester {
    fun update(bytes: ByteArray, offset: Int, length: Int)
    fun hex(): String
}

internal expect fun createDigester(algorithm: ChecksumAlgorithm): Digester

object FileChecksum {
    /** Takes "HASH" or "HASH  file-name" (as in .sha256 files) and returns the lower-case hash. */
    fun normalize(input: String): String = input.trim().substringBefore(' ').substringBefore('\t').lowercase()

    fun isValid(hash: String): Boolean =
        ChecksumAlgorithm.fromHexLength(hash.length) != null && hash.all { it in '0'..'9' || it in 'a'..'f' }

    suspend fun compute(
        fileSystem: FileSystem,
        path: Path,
        algorithm: ChecksumAlgorithm,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): String = computeFrom(fileSystem.source(path), algorithm, dispatcher)

    /** Hashes everything [input] returns, then closes it. */
    suspend fun computeFrom(
        input: okio.Source,
        algorithm: ChecksumAlgorithm,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): String = withContext(dispatcher) {
        val digester = createDigester(algorithm)
        val buffer = ByteArray(64 * 1024)
        val source = input.buffer()
        try {
            while (true) {
                val n = source.read(buffer, 0, buffer.size)
                if (n == -1) break
                digester.update(buffer, 0, n)
            }
        } finally {
            source.close()
        }
        digester.hex()
    }
}
