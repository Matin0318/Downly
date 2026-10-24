package app.downly.ui.components

import androidx.compose.ui.graphics.Color

/** File groups used for the badge, the category filter and its colours. */
enum class FileCategory(
    val icon: DIcon,
    val from: Color,
    val to: Color,
) {
    Video(DIcon.Video, Color(0xFFFF6B6B), Color(0xFFE63E68)),
    Audio(DIcon.Audio, Color(0xFFF472B6), Color(0xFFDB2777)),
    Image(DIcon.Image, Color(0xFF34D399), Color(0xFF0D9488)),
    Document(DIcon.Doc, Color(0xFF60A5FA), Color(0xFF3B5BDB)),
    Archive(DIcon.Archive, Color(0xFFFBBF24), Color(0xFFF97316)),
    App(DIcon.Package, Color(0xFF4ADE80), Color(0xFF16A34A)),
    Program(DIcon.Disk, Color(0xFF22D3EE), Color(0xFF0891B2)),
    Other(DIcon.Doc, Color(0xFF94A3B8), Color(0xFF64748B)),
}

fun fileExtension(fileName: String): String = fileName.substringAfterLast('.', "").lowercase()

fun categoryOf(fileName: String): FileCategory = when (fileExtension(fileName)) {
    "mp4", "mkv", "avi", "mov", "webm", "flv", "ts", "m4v", "3gp", "wmv" -> FileCategory.Video
    "mp3", "flac", "wav", "m4a", "ogg", "aac", "opus", "wma" -> FileCategory.Audio
    "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic" -> FileCategory.Image
    "pdf", "doc", "docx", "txt", "epub", "xls", "xlsx", "ppt", "pptx", "csv", "rtf", "odt" -> FileCategory.Document
    "zip", "rar", "7z", "tar", "gz", "xz", "bz2", "tgz" -> FileCategory.Archive
    "apk", "aab", "xapk", "apks" -> FileCategory.App
    "iso", "exe", "msi", "dmg", "deb", "rpm", "img" -> FileCategory.Program
    else -> FileCategory.Other
}
