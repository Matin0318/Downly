package app.downly.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Things only the host OS can do. Android implements it now; Desktop later. */
interface PlatformActions {
    val appVersion: String
    fun readClipboardText(): String?
    fun copyText(text: String)
    fun openFile(path: String): Boolean
    fun shareFile(path: String): Boolean

    /** Lets the user pick a folder. Calls [onPicked] with an address and a display name, or nulls when cancelled. */
    fun pickFolder(onPicked: (uri: String?, name: String?) -> Unit) {}

    /** Opens the system screen where the user allows Downly to run in the background. */
    fun openBatterySettings() {}

    /** Opens the built-in browser that catches downloads and videos. Hosts without one do nothing. */
    fun openBrowser() {}

    /** Lets the user choose where to save a backup file, writes [content] there and calls [onDone] with the result. */
    fun exportBackup(fileName: String, content: String, onDone: (Boolean) -> Unit) { onDone(false) }

    /** Lets the user pick a backup file and passes its text to [onPicked] (null when cancelled or unreadable). */
    fun importBackup(onPicked: (String?) -> Unit) { onPicked(null) }

    /**
     * True when downloads may be written straight into the public Downloads folder (no copy, no space used
     * inside the app). On Android 11+ this is the "All files access" switch of the app.
     */
    fun hasFullStorageAccess(): Boolean = true

    /** Opens the system screen or dialog where the user allows direct saving. */
    fun requestFullStorageAccess() {}

    /** True when Android lets the app set alarms that fire exactly on time (scheduled downloads). */
    fun canScheduleExactAlarms(): Boolean = true

    /** Opens the system screen where the user allows exact alarms. */
    fun requestExactAlarms() {}

    /** True when the app is not restricted by battery saving in the background. */
    fun isIgnoringBatteryOptimizations(): Boolean = true

    /** Asks Android to leave the app unrestricted in the background. */
    fun requestIgnoreBatteryOptimizations() {}

    /** Plays a finished video or audio file in the built-in player. Returns false when that is not possible. */
    fun playMedia(path: String, title: String): Boolean = false
}

/** Events pushed from the host (Activity) into the UI. */
class HostSignals {
    /** A link received from another app's Share menu. */
    var incomingUrl by mutableStateOf<String?>(null)

    /** Incremented every time the app comes to the foreground. */
    var resumeCount by mutableIntStateOf(0)
}
