package app.downly.android

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import app.downly.ui.MediaMerger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.cancellation.CancellationException

/**
 * Joins a video file and an audio file with the phone's own MediaMuxer: the compressed data is copied,
 * nothing is re-encoded, so it is fast and loses no quality.
 */
class AndroidMediaMerger : MediaMerger {
    private class Source(val extractor: MediaExtractor, val track: Int)

    override suspend fun merge(video: String, audio: String, output: String): Boolean = withContext(Dispatchers.IO) {
        val extractors = ArrayList<MediaExtractor>()
        var muxer: MediaMuxer? = null
        try {
            val container = if (output.endsWith(".webm", ignoreCase = true)) {
                MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
            } else {
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            }
            val out = MediaMuxer(output, container)
            muxer = out

            val sources = ArrayList<Source>()
            for ((path, prefix) in listOf(video to "video/", audio to "audio/")) {
                val extractor = MediaExtractor()
                extractors += extractor
                extractor.setDataSource(path)
                var picked = -1
                for (i in 0 until extractor.trackCount) {
                    val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                    if (mime != null && mime.startsWith(prefix)) {
                        picked = i
                        break
                    }
                }
                if (picked < 0) return@withContext false
                extractor.selectTrack(picked)
                sources += Source(extractor, out.addTrack(extractor.getTrackFormat(picked)))
            }

            out.start()
            val buffer = ByteBuffer.allocateDirect(8 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            val active = sources.toMutableList()
            // Always copy the sample that comes first in time, so audio and video stay interleaved.
            while (active.isNotEmpty()) {
                ensureActive()
                val next = active.minByOrNull { it.extractor.sampleTime } ?: break
                buffer.clear()
                val size = next.extractor.readSampleData(buffer, 0)
                if (size < 0) {
                    active.remove(next)
                    continue
                }
                val key = next.extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
                info.set(0, size, next.extractor.sampleTime, if (key) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                out.writeSampleData(next.track, buffer, info)
                next.extractor.advance()
            }
            out.stop()
            true
        } catch (e: CancellationException) {
            File(output).delete()
            throw e
        } catch (e: Exception) {
            File(output).delete()
            false
        } finally {
            extractors.forEach { runCatching { it.release() } }
            runCatching { muxer?.release() }
        }
    }
}
