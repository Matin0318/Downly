package app.downly.ui

import okio.Path
import okio.Source

/**
 * Where finished files go. Downloads are written to private app storage first (fast, resumable);
 * when one completes it is moved to the user-visible Downloads folder through this bridge.
 */
interface PublicStorage {
    /** Tells the system about a file that was saved directly (so Downloads and the gallery show it). */
    fun scan(file: Path) {}

    /** Free bytes where finished files are copied to. Used to avoid starting a copy that cannot finish. */
    fun freeBytes(): Long = Long.MAX_VALUE

    /** Copies [file] to the public folder and returns its address, or null when that is not possible. */
    suspend fun export(file: Path, displayName: String, subFolder: String): String?

    fun open(address: String): Source?

    fun delete(address: String): Boolean

    /** Renames the file and returns its new address, or null when that is not possible. */
    fun rename(address: String, newName: String): String? = null
}
