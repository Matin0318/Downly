package app.downly.android

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import app.downly.ui.PublicStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path
import okio.Source
import okio.source
import java.io.File

/**
 * Moves finished files to the user-visible Downloads folder.
 * - A folder picked by the user ("tree" address) is used when set.
 * - Otherwise Android 10+ files go to Downloads/Downly through MediaStore (no permission needed).
 * - On Android 8-9 without a picked folder the file stays in app storage (returns null).
 */
class AndroidPublicStorage(
    private val context: Context,
    private val treeUri: () -> String,
) : PublicStorage {

    override suspend fun export(file: Path, displayName: String, subFolder: String): String? =
        withContext(Dispatchers.IO) {
            val source = File(file.toString())
            val mime = mimeOf(displayName)
            val tree = treeUri()
            when {
                tree.isNotBlank() -> exportToTree(source, displayName, mime, subFolder, Uri.parse(tree))
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> exportToMediaStore(source, displayName, mime, subFolder)
                else -> null
            }
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun exportToMediaStore(source: File, name: String, mime: String, sub: String): String? {
        val resolver = context.contentResolver
        val relative = buildString {
            append(Environment.DIRECTORY_DOWNLOADS).append("/Downly")
            if (sub.isNotBlank()) append('/').append(sub)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out, 256 * 1024) } }
                ?: throw IllegalStateException("No output stream")
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            uri.toString()
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    private fun exportToTree(source: File, name: String, mime: String, sub: String, tree: Uri): String? {
        val resolver = context.contentResolver
        var created: Uri? = null
        return try {
            var parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            for (part in sub.split('/').filter { it.isNotBlank() }) {
                parent = findOrCreateDirectory(tree, parent, part) ?: return null
            }
            val doc = DocumentsContract.createDocument(resolver, parent, mime, name) ?: return null
            created = doc
            resolver.openOutputStream(doc)?.use { out -> source.inputStream().use { it.copyTo(out, 256 * 1024) } }
                ?: throw IllegalStateException("No output stream")
            doc.toString()
        } catch (e: Exception) {
            // A half-written copy would stay in the user's folder (maybe gigabytes): remove it.
            created?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
            null
        }
    }

    override fun scan(file: okio.Path) {
        try {
            android.media.MediaScannerConnection.scanFile(context, arrayOf(file.toString()), null, null)
        } catch (e: Exception) {
            // The file is saved anyway; only its listing in other apps is late.
        }
    }

    override fun freeBytes(): Long = try {
        context.getExternalFilesDir(null)?.usableSpace ?: Long.MAX_VALUE
    } catch (e: Exception) {
        Long.MAX_VALUE
    }

    /**
     * Removes copies that were never finished (the app was closed or killed while copying). Android hides them
     * as "pending" files; they are only visible to the app that made them. Anything older than an hour is stale.
     */
    fun deleteStalePending() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            val resolver = context.contentResolver
            val limit = System.currentTimeMillis() / 1000 - 3600
            val args = Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_ONLY)
                putString(
                    ContentResolver.QUERY_ARG_SQL_SELECTION,
                    "${MediaStore.MediaColumns.IS_PENDING}=1 AND ${MediaStore.MediaColumns.DATE_ADDED}<?",
                )
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(limit.toString()))
            }
            val stale = ArrayList<Long>()
            resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.MediaColumns._ID), args, null)?.use {
                while (it.moveToNext()) stale += it.getLong(0)
            }
            for (id in stale) {
                resolver.delete(ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id), null, null)
            }
        } catch (e: Exception) {
            // Cleaning is best effort.
        }
    }

    private fun findOrCreateDirectory(tree: Uri, parent: Uri, name: String): Uri? {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent))
        resolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val isDir = cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                if (isDir && cursor.getString(1) == name) {
                    return DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
                }
            }
        }
        return DocumentsContract.createDocument(resolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, name)
    }

    override fun open(address: String): Source? = try {
        context.contentResolver.openInputStream(Uri.parse(address))?.source()
    } catch (e: Exception) {
        null
    }

    override fun delete(address: String): Boolean = try {
        val uri = Uri.parse(address)
        if (DocumentsContract.isDocumentUri(context, uri)) {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } else {
            context.contentResolver.delete(uri, null, null) > 0
        }
    } catch (e: Exception) {
        false
    }

    override fun rename(address: String, newName: String): String? = try {
        val uri = Uri.parse(address)
        if (DocumentsContract.isDocumentUri(context, uri)) {
            DocumentsContract.renameDocument(context.contentResolver, uri, newName)?.toString()
        } else {
            val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, newName) }
            if (context.contentResolver.update(uri, values, null, null) > 0) uri.toString() else null
        }
    } catch (e: Exception) {
        null
    }

    private fun mimeOf(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"
}
