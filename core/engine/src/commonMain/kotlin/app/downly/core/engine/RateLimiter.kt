package app.downly.core.engine

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

/**
 * Shared token-bucket style limiter: every chunk "reserves" its transfer time on one timeline,
 * so all callers together never exceed [bytesPerSec]. 0 = unlimited.
 */
class RateLimiter(var bytesPerSec: Long = 0L) {
    private val lock = Mutex()
    private val base = TimeSource.Monotonic.markNow()
    private var nextFreeNs = 0L

    suspend fun acquire(bytes: Int) {
        val limit = bytesPerSec
        if (limit <= 0L) return
        val waitNs = lock.withLock {
            val now = base.elapsedNow().inWholeNanoseconds
            val start = maxOf(now, nextFreeNs)
            nextFreeNs = start + bytes * 1_000_000_000L / limit
            start - now
        }
        if (waitNs > 0L) delay(waitNs / 1_000_000L + 1L)
    }
}
