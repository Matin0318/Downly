package app.downly.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Direct saving: with access to files, downloads are written straight into Downloads/Downly while they run.
 * Nothing is kept inside the app and nothing is copied at the end.
 */
object StorageAccess {
    fun has(context: Context): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
        Build.VERSION.SDK_INT == Build.VERSION_CODES.Q ->
            Environment.isExternalStorageLegacy() && hasWritePermission(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> hasWritePermission(context)
        else -> true
    }

    private fun hasWritePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    /** `Download/Downly` on the main storage. */
    fun publicDirectory(): File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Downly")
}
