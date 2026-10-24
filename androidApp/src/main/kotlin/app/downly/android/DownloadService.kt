package app.downly.android

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import app.downly.ui.stringsFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Keeps the process alive and shows the progress notification while downloads run.
 * The downloads themselves live in DownloadsController; this service only hosts them.
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as DownlyApplication
        val controller = app.controller
        val notifications = app.notifications

        if (intent?.action == ACTION_PAUSE_ALL) controller.pauseAll()

        val strings = stringsFor(controller.settings.value.language)
        try {
            ServiceCompat.startForeground(
                this,
                DownloadNotifications.ID_PROGRESS,
                notifications.buildProgress(controller.items.value, strings),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } catch (e: Exception) {
            // Android does not allow a foreground service to start from the background right now.
            stopSelf()
            return START_NOT_STICKY
        }
        acquireWakeLock()
        acquireWifiLock()

        if (observer == null) {
            observer = scope.launch {
                var lastUpdate = TimeSource.Monotonic.markNow()
                controller.items.collect { items ->
                    if (items.none { it.isActive }) {
                        ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else if (lastUpdate.elapsedNow() >= 1.seconds) {
                        lastUpdate = TimeSource.Monotonic.markNow()
                        notifications.updateProgress(items, stringsFor(controller.settings.value.language))
                    }
                }
            }
        }
        // If the system stops the service while downloads run, it starts it again and downloads continue.
        return START_STICKY
    }

    /** Keeps Wi-Fi at full speed while the screen is off (otherwise it falls asleep and downloads crawl). */
    @Suppress("DEPRECATION")
    private fun acquireWifiLock() {
        if (wifiLock?.isHeld == true) return
        try {
            val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Downly::Wifi").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            // No Wi-Fi hardware or no permission: nothing to hold.
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Downly::Download").apply {
            acquire(6 * 60 * 60 * 1000L)
        }
    }

    override fun onDestroy() {
        running = false
        observer?.cancel()
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        wifiLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        const val ACTION_PAUSE_ALL = "app.downly.action.PAUSE_ALL"

        @Volatile
        var running: Boolean = false
    }
}
