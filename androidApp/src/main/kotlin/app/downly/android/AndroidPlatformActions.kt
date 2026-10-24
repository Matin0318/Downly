package app.downly.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import app.downly.ui.PlatformActions
import app.downly.ui.stringsFor
import java.io.File

class AndroidPlatformActions(private val context: Context) : PlatformActions {

    override val appVersion: String = BuildConfig.VERSION_NAME

    override fun readClipboardText(): String? {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = manager.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()
    }

    override fun copyText(text: String) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        manager.setPrimaryClip(ClipData.newPlainText("Downly", text))
    }

    override fun openBrowser() {
        context.startActivity(Intent(context, BrowserActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun pickFolder(onPicked: (uri: String?, name: String?) -> Unit) {
        (context as? MainActivity)?.pickFolder(onPicked) ?: onPicked(null, null)
    }

    override fun openBatterySettings() {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** Opens a finished file. A chooser titled "Open with" lets the user pick the app, whatever the file type. */
    override fun openFile(path: String): Boolean {
        return try {
            val uri: Uri
            val mime: String
            if (path.startsWith("content://")) {
                uri = Uri.parse(path)
                mime = context.contentResolver.getType(uri) ?: "*/*"
            } else {
                val file = File(path)
                if (!file.exists()) return false
                uri = uriFor(file)
                mime = mimeOf(file)
            }
            val view = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            view.clipData = ClipData.newRawUri("", uri) // Lets the app the user picks read the file.
            val strings = stringsFor((context.applicationContext as DownlyApplication).controller.settings.value.language)
            val chooser = Intent.createChooser(view, strings.more.openWith)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            chooser.clipData = view.clipData
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun shareFile(path: String): Boolean = try {
        if (path.startsWith("content://")) {
            val uri = Uri.parse(path)
            val send = Intent(Intent.ACTION_SEND)
                .setType(context.contentResolver.getType(uri) ?: "*/*")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } else {
        val file = File(path)
        if (!file.exists()) {
            false
        } else {
            val send = Intent(Intent.ACTION_SEND)
                .setType(mimeOf(file))
                .putExtra(Intent.EXTRA_STREAM, uriFor(file))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            true
        }
        }
    } catch (e: Exception) {
        false
    }

    override fun canScheduleExactAlarms(): Boolean = ScheduleAlarms.canExact(context)

    override fun requestExactAlarms() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: Exception) {
            openBatterySettings()
        }
    }

    override fun isIgnoringBatteryOptimizations(): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)

    override fun requestIgnoreBatteryOptimizations() {
        try {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: Exception) {
            openBatterySettings()
        }
    }

    override fun hasFullStorageAccess(): Boolean = StorageAccess.has(context)

    override fun requestFullStorageAccess() {
        (context as? MainActivity)?.requestStorageAccess()
    }

    override fun exportBackup(fileName: String, content: String, onDone: (Boolean) -> Unit) {
        (context as? MainActivity)?.exportBackup(fileName, content, onDone) ?: onDone(false)
    }

    override fun importBackup(onPicked: (String?) -> Unit) {
        (context as? MainActivity)?.importBackup(onPicked) ?: onPicked(null)
    }

    override fun playMedia(path: String, title: String): Boolean {
        return try {
            val uri = if (path.startsWith("content://")) {
                Uri.parse(path)
            } else {
                val file = File(path)
                if (!file.exists()) return false
                uriFor(file)
            }
            val intent = Intent(context, PlayerActivity::class.java)
                .setData(uri)
                .putExtra(PlayerActivity.EXTRA_TITLE, title)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun uriFor(file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    private fun mimeOf(file: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
}
