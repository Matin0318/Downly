package app.downly.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import app.downly.data.DownloadQueue
import app.downly.ui.downloads.DownloadsController
import java.util.Calendar

/** Arms one pair of system alarms (start / stop) per scheduled queue, so schedules work with the app closed. */
object ScheduleAlarms {
    const val ACTION = "app.downly.action.SCHEDULE"
    const val ACTION_KEEP_ALIVE = "app.downly.action.KEEP_ALIVE"
    private const val KEEP_ALIVE_CODE = 1_000_000
    const val EXTRA_START = "start"
    const val EXTRA_QUEUE = "queue"
    private const val PREFS = "schedule_alarms"
    private const val KEY_CODES = "codes"

    fun apply(context: Context, queues: List<DownloadQueue>) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // Cancel what was armed before (deleted or changed queues), then arm the current schedules.
        val old = prefs.getStringSet(KEY_CODES, emptySet()).orEmpty()
        for (code in old) {
            val queueId = code.substringBefore(':').toLongOrNull() ?: continue
            val start = code.substringAfter(':') == "1"
            alarms.cancel(pending(context, queueId, start))
        }
        val armed = HashSet<String>()
        for (q in queues) {
            if (!q.scheduleEnabled || !q.enabled) continue
            arm(context, alarms, q.id, true, q.startMinute)
            arm(context, alarms, q.id, false, q.stopMinute)
            armed += "${q.id}:1"
            armed += "${q.id}:0"
        }
        prefs.edit().putStringSet(KEY_CODES, armed).apply()
    }

    private fun pending(context: Context, queueId: Long, start: Boolean): PendingIntent = PendingIntent.getBroadcast(
        context,
        (queueId * 2 + if (start) 0 else 1).toInt(),
        Intent(context, ScheduleReceiver::class.java)
            .setAction(ACTION)
            .putExtra(EXTRA_START, start)
            .putExtra(EXTRA_QUEUE, queueId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun arm(context: Context, alarms: AlarmManager, queueId: Long, start: Boolean, minute: Int) {
        val intent = pending(context, queueId, start)
        val at = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minute / 60)
            set(Calendar.MINUTE, minute % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis() + 1_000) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        try {
            if (canExact(context)) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            } else {
                // Without the "exact alarms" permission a plain alarm can be postponed for many minutes. An alarm-clock
                // alarm needs no permission and Android never delays it (Android shows its small alarm icon for it).
                alarms.setAlarmClock(AlarmManager.AlarmClockInfo(at, openApp(context)), intent)
            }
        } catch (e: Exception) {
            // The permission was taken away at this very moment: a plain alarm is better than none.
            try {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            } catch (ignored: Exception) {
                // Nothing more to try.
            }
        }
    }

    /** True when Android lets the app set exact alarms. */
    fun canExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Remembers that this queue's current start was handled, so it is never started twice. */
    fun markFired(context: Context, queueId: Long, time: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("fired_$queueId", time).apply()
    }

    /**
     * The phone was off (or the app was killed) at a queue's start time: if the time window is still open and
     * that start was not handled, start the queue now. Runs when the app process starts, e.g. after a reboot.
     */
    fun catchUp(context: Context, controller: DownloadsController) {
        try {
            catchUpNow(context, controller)
        } catch (e: Exception) {
            // Never let a schedule check crash the app.
        }
    }

    private fun catchUpNow(context: Context, controller: DownloadsController) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        for (q in controller.settings.value.queues) {
            if (!q.scheduleEnabled || !q.enabled) continue
            val lastStart = latestMoment(q.startMinute, now)
            val minutes = ((q.stopMinute - q.startMinute + 1440) % 1440).let { if (it == 0) 1440 else it }
            if (now - lastStart >= minutes * 60_000L) continue // The window is already closed.
            if (prefs.getLong("fired_${q.id}", 0L) >= lastStart) continue // This start was handled.
            markFired(context, q.id, now)
            controller.fireSchedule(q.id, true)
        }
    }

    /** The most recent time of day [minute] that is not in the future. */
    private fun latestMoment(minute: Int, now: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, minute / 60)
            set(Calendar.MINUTE, minute % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis > now) cal.add(Calendar.DAY_OF_YEAR, -1)
        return cal.timeInMillis
    }

    /**
     * Android 15 does not let a boot receiver start a data-transfer foreground service, so after a reboot the
     * service is started from a tiny alarm a couple of seconds later (alarms are allowed to start it).
     */
    fun keepAliveSoon(context: Context) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = PendingIntent.getBroadcast(
            context,
            KEEP_ALIVE_CODE,
            Intent(context, ScheduleReceiver::class.java).setAction(ACTION_KEEP_ALIVE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val at = System.currentTimeMillis() + 2_000
        try {
            if (canExact(context)) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            } else {
                alarms.setAlarmClock(AlarmManager.AlarmClockInfo(at, openApp(context)), intent)
            }
        } catch (e: Exception) {
            // No alarm possible: the downloads still run while the process lives.
        }
    }

    /** Starts the foreground service so the process lives on while the screen is off. */
    fun keepAlive(context: Context, controller: DownloadsController) {
        if (controller.items.value.none { it.isActive }) return
        try {
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        } catch (e: Exception) {
            // Android refused to start it from the background. The download continues while the process lives.
        }
    }
}

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val start = intent.getBooleanExtra(ScheduleAlarms.EXTRA_START, true)
        val queueId = intent.getLongExtra(ScheduleAlarms.EXTRA_QUEUE, -1L)
        val app = context.applicationContext as DownlyApplication
        if (intent.action == ScheduleAlarms.ACTION_KEEP_ALIVE) {
            val pending = goAsync()
            app.runWhenLoaded {
                try {
                    ScheduleAlarms.keepAlive(app, app.controller)
                } finally {
                    pending.finish()
                }
            }
            return
        }
        if (start && queueId >= 0) ScheduleAlarms.markFired(app, queueId)
        val result = goAsync()
        app.runWhenLoaded {
            try {
                if (queueId >= 0) app.controller.fireSchedule(queueId, start)
                // Started inside the alarm's short window, where Android still allows a foreground service.
                if (start) ScheduleAlarms.keepAlive(app, app.controller)
                // Alarms fire once: arm tomorrow's.
                ScheduleAlarms.apply(app, app.controller.settings.value.queues)
            } finally {
                result.finish()
            }
        }
    }
}

/**
 * After a reboot, an app update, a clock or time-zone change, or when exact alarms get allowed: arm the alarms
 * again and start a queue whose start time passed while the phone was off.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as DownlyApplication
        val result = goAsync()
        app.runWhenLoaded {
            try {
                ScheduleAlarms.apply(app, app.controller.settings.value.queues)
                ScheduleAlarms.catchUp(app, app.controller)
                ScheduleAlarms.keepAlive(app, app.controller)
                ScheduleAlarms.keepAliveSoon(app) // Android 15: a boot receiver may not start the service itself.
            } finally {
                result.finish()
            }
        }
    }
}
