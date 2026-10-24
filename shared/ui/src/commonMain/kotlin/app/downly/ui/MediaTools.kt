package app.downly.ui

import kotlinx.coroutines.flow.Flow

/** Puts a separately downloaded video track and audio track into one playable file (no re-encoding). */
interface MediaMerger {
    /** Writes [output] (container chosen by its extension: .mp4 or .webm). Returns false when it failed. */
    suspend fun merge(video: String, audio: String, output: String): Boolean
}

/** BitTorrent support. Magnet links and `.torrent` links are handled by the platform. */
interface TorrentEngine {
    class Meta(val name: String, val totalBytes: Long)

    class Progress(val doneBytes: Long, val totalBytes: Long, val bytesPerSecond: Long, val finished: Boolean)

    /** Name and size of the torrent. For a magnet link this asks other peers and can take up to a minute. */
    suspend fun meta(source: String): Meta

    /**
     * Downloads the torrent into [saveDir] (the content is `<saveDir>/<name>`) and emits progress until it is
     * finished. Cancelling the flow stops the torrent but keeps the files, so it can be resumed later.
     */
    fun download(source: String, saveDir: String): Flow<Progress>
}
