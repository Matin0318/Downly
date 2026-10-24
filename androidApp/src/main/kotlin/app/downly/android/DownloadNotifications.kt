package app.downly.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.downly.ui.AppStrings
import app.downly.ui.downloads.DownloadItem
import app.downly.ui.formatSpeed

class DownloadNotifications(private val context: Context) {

    init {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_PROGRESS, context.getString(R.string.channel_progress), NotificationManager.IMPORTANCE_LOW),
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_DONE, context.getString(R.string.channel_done), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    fun buildProgress(items: List<DownloadItem>, strings: AppStrings): Notification {
        val active = items.filter { it.isActive }
        val known = active.filter { it.totalBytes > 0 }
        val total = known.sumOf { it.totalBytes }
        val done = known.sumOf { it.downloadedBytes }
        val speed = active.sumOf { it.speedBytesPerSec }
        val percent = if (total > 0) (done * 100 / total).toInt() else 0

        val title = when {
            active.isEmpty() -> strings.appName
            active.size == 1 -> active[0].fileName
            else -> strings.notifDownloadingMany(active.size)
        }
        val text = buildString {
            if (total > 0) append(strings.digits("$percent%"))
            if (speed > 0) {
                if (isNotEmpty()) append(" • ")
                append(formatSpeed(speed, strings))
            }
        }

        val pauseIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, DownloadService::class.java).setAction(DownloadService.ACTION_PAUSE_ALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, percent, total <= 0)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openAppIntent())
            .addAction(0, strings.pauseAll, pauseIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun updateProgress(items: List<DownloadItem>, strings: AppStrings) {
        post(ID_PROGRESS, buildProgress(items, strings))
    }

    fun notifyCompleted(item: DownloadItem, strings: AppStrings) {
        val notification = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_done)
            .setContentTitle(strings.notifDone)
            .setContentText(item.fileName)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        post(ID_DONE_BASE + (item.id % 100000).toInt(), notification)
    }

    fun notifyFailed(item: DownloadItem, strings: AppStrings) {
        val notification = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_error)
            .setContentTitle(strings.notifFailed)
            .setContentText(item.fileName)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        post(ID_DONE_BASE + (item.id % 100000).toInt(), notification)
    }

    /**
     * Android does not allow apps to switch Wi-Fi / mobile data off. This notification opens the system
     * internet panel (or the wireless settings on old versions) where it takes one tap.
     */
    fun notifyQueueFinished(queueName: String, strings: AppStrings) {
        val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            android.provider.Settings.Panel.ACTION_INTERNET_CONNECTIVITY
        } else {
            android.provider.Settings.ACTION_WIRELESS_SETTINGS
        }
        val pending = PendingIntent.getActivity(
            context,
            1,
            Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_done)
            .setContentTitle("${strings.finishedNotifTitle} • $queueName")
            .setContentText(strings.finishedNotifBody)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        post(ID_QUEUE_FINISHED, notification)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun post(id: Int, notification: Notification) {
        if (hasPermission()) NotificationManagerCompat.from(context).notify(id, notification)
    }

    companion object {
        const val CHANNEL_PROGRESS = "downloads_progress"
        const val CHANNEL_DONE = "downloads_done"
        const val ID_PROGRESS = 1001
        const val ID_DONE_BASE = 2000
        const val ID_QUEUE_FINISHED = 1002
    }
}
