package app.downly.android

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.downly.ui.formatSpeed
import app.downly.ui.stringsFor

/** Home-screen widget: how many downloads are active and the total speed. Tap it to open Downly. */
class DownlyWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_TOGGLE) return
        // The round button: pauses everything that runs, or resumes what was paused.
        val app = context.applicationContext as DownlyApplication
        val pending = goAsync()
        app.runWhenLoaded {
            val items = app.controller.items.value
            if (items.any { it.isActive }) app.controller.pauseAll() else app.controller.resumeAll()
            refresh(context)
            pending.finish()
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        update(context, manager, appWidgetIds)
    }

    companion object {
        private const val ACTION_TOGGLE = "app.downly.android.WIDGET_TOGGLE"

        /** Redraws every placed widget. Cheap when none is placed. */
        fun refresh(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, DownlyWidget::class.java))
                if (ids.isNotEmpty()) update(context, manager, ids)
            } catch (e: Exception) {
                // A widget problem must never close the app.
            }
        }

        private fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val controller = (context.applicationContext as DownlyApplication).controller
            val strings = stringsFor(controller.settings.value.language)
            val items = controller.items.value
            val active = items.count { it.isActive }
            val speed = items.sumOf { it.speedBytesPerSec }

            val views = RemoteViews(context.packageName, R.layout.widget_downly)
            views.setTextViewText(
                R.id.widget_count,
                if (active > 0) "${strings.digits(active.toString())} ${strings.activeNow}" else strings.more.widgetIdle,
            )
            views.setTextViewText(R.id.widget_speed, if (active > 0) formatSpeed(speed, strings) else "")
            val paused = items.count { it.status == app.downly.core.model.DownloadStatus.PAUSED }
            if (active > 0 || paused > 0) {
                views.setViewVisibility(R.id.widget_toggle, android.view.View.VISIBLE)
                views.setTextViewText(R.id.widget_toggle, if (active > 0) "❚❚" else "▶")
                val toggle = PendingIntent.getBroadcast(
                    context,
                    1,
                    Intent(context, DownlyWidget::class.java).setAction(ACTION_TOGGLE),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                views.setOnClickPendingIntent(R.id.widget_toggle, toggle)
            } else {
                views.setViewVisibility(R.id.widget_toggle, android.view.View.GONE)
            }
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            ids.forEach { manager.updateAppWidget(it, views) }
        }
    }
}
