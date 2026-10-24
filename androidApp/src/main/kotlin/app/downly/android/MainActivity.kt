package app.downly.android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import app.downly.data.AppLanguage
import app.downly.data.ThemeMode
import app.downly.ui.DownlyApp
import app.downly.ui.HostSignals
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val signals = HostSignals()

    /** The language this window was built with (decides the layout direction of every dialog and menu). */
    private var appliedLanguage = AppLanguage.EN

    /**
     * Gives the window the app's own language and layout direction from the very first frame. Compose dialogs and
     * menus are separate windows that start with the direction of the Android configuration; without this they
     * open left-to-right for a moment and then jump to right-to-left when the app language is Persian.
     */
    override fun attachBaseContext(newBase: Context) {
        appliedLanguage = LookCache.language(newBase)
        val locale = Locale(if (appliedLanguage == AppLanguage.FA) "fa" else "en")
        applyOverrideConfiguration(Configuration().apply { setLocale(locale) })
        super.attachBaseContext(newBase)
    }

    private var folderCallback: ((String?, String?) -> Unit)? = null

    private val folderPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            val callback = folderCallback
            folderCallback = null
            if (uri == null) {
                callback?.invoke(null, null)
            } else {
                // Keep access after the app restarts.
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                val name = Uri.decode(uri.lastPathSegment.orEmpty()).substringAfterLast(':').ifBlank { "Folder" }
                callback?.invoke(uri.toString(), name)
            }
        }

    fun pickFolder(onPicked: (String?, String?) -> Unit) {
        folderCallback = onPicked
        folderPicker.launch(null)
    }

    private val storagePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { signals.resumeCount++ }

    /** Asks for direct saving: the "All files access" screen on Android 11+, the normal permission before. */
    fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val uri = android.net.Uri.parse("package:$packageName")
            try {
                startActivity(Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri))
            } catch (e: Exception) {
                startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private var exportCallback: ((Boolean) -> Unit)? = null
    private var pendingBackup: String? = null
    private var importCallback: ((String?) -> Unit)? = null

    private val backupCreator =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            val callback = exportCallback
            val text = pendingBackup
            exportCallback = null
            pendingBackup = null
            if (uri != null && text != null) {
                val ok = try {
                    contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write(text.toByteArray(Charsets.UTF_8))
                        true
                    } ?: false
                } catch (e: Exception) {
                    false
                }
                callback?.invoke(ok)
            }
        }

    private val backupPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val callback = importCallback
            importCallback = null
            if (uri != null) {
                val text = try {
                    contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                } catch (e: Exception) {
                    null
                }
                callback?.invoke(text ?: "")
            }
        }

    fun exportBackup(fileName: String, content: String, onDone: (Boolean) -> Unit) {
        exportCallback = onDone
        pendingBackup = content
        backupCreator.launch(fileName)
    }

    fun importBackup(onPicked: (String?) -> Unit) {
        importCallback = onPicked
        backupPicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val controller = (application as DownlyApplication).controller
        val platform = AndroidPlatformActions(this)

        requestNotificationPermission()
        handleIntent(intent)
        addOnNewIntentListener { newIntent -> handleIntent(newIntent) }

        setContent {
            val settings by controller.settings.collectAsState()
            // The window language is fixed when the window is created, so a language change rebuilds it.
            LaunchedEffect(settings.language) {
                if (settings.language != appliedLanguage) {
                    LookCache.save(this@MainActivity, settings.language, settings.theme)
                    recreate()
                }
            }
            val systemDark = isSystemInDarkTheme()
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            DisposableEffect(dark) {
                val transparent = android.graphics.Color.TRANSPARENT
                val style = if (dark) {
                    SystemBarStyle.dark(transparent)
                } else {
                    SystemBarStyle.light(transparent, transparent)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            DownlyApp(
                controller = controller,
                platform = platform,
                signals = signals,
                backHandler = { enabled, onBack -> BackHandler(enabled = enabled, onBack = onBack) },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        signals.resumeCount++
        // Alarms can be lost (cleared app data, changed permission): arm them again whenever the app opens.
        val app = application as DownlyApplication
        app.runWhenLoaded { ScheduleAlarms.apply(app, app.controller.settings.value.queues) }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            // A magnet link, or a link to a .torrent file, opened from another app.
            val data = intent.dataString ?: return
            val torrent = intent.type == "application/x-bittorrent" &&
                !data.substringBefore('?').lowercase().endsWith(".torrent") &&
                !data.startsWith("magnet:", ignoreCase = true)
            signals.incomingUrl = if (torrent) "$data#.torrent" else data
            return
        }
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val url = Regex("(?:https?://|magnet:\\?)\\S+").find(text)?.value ?: return
        signals.incomingUrl = url
    }
}
