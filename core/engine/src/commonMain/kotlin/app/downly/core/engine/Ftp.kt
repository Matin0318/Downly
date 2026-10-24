package app.downly.core.engine

import kotlinx.coroutines.flow.Flow
import okio.FileSystem
import okio.Path

/** What an FTP server says about one file. */
class FtpFileInfo(val size: Long, val fileName: String)

/**
 * Minimal FTP client (passive mode, binary, resume with REST). Links look like
 * `ftp://user:password@host:21/folder/file.zip`; without a login the anonymous account is used.
 */
object Ftp {
    fun isFtp(url: String): Boolean = url.startsWith("ftp://", ignoreCase = true)

    suspend fun probe(url: String): FtpFileInfo = ftpProbe(url)

    /**
     * Downloads to `<destination>.part` starting at byte [startAt] and emits the total number of bytes
     * the part file holds. The caller renames the part file when the flow completes.
     */
    fun download(
        url: String,
        destination: Path,
        fileSystem: FileSystem,
        startAt: Long,
        limiters: List<RateLimiter>,
    ): Flow<Long> = ftpDownload(url, destination, fileSystem, startAt, limiters)
}

internal expect suspend fun ftpProbe(url: String): FtpFileInfo

internal expect fun ftpDownload(
    url: String,
    destination: Path,
    fileSystem: FileSystem,
    startAt: Long,
    limiters: List<RateLimiter>,
): Flow<Long>
