package app.downly.core.engine

/** Finds downloadable file links inside a web page (IDM "Site Grabber" / ADM "Grab links"). */
object SiteGrabber {
    val defaultExtensions: Set<String> = setOf(
        "zip", "rar", "7z", "tar", "gz", "xz", "bz2", "tgz", "iso", "img", "exe", "msi", "dmg", "deb", "rpm", "apk", "aab", "xapk",
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "epub", "txt", "csv",
        "mp3", "flac", "wav", "m4a", "ogg", "aac", "opus",
        "mp4", "mkv", "avi", "mov", "webm", "flv", "m4v", "3gp", "wmv",
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg",
    )

    private val attr = Regex("""(?:href|src|data-src)\s*=\s*(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE)

    /** Every http(s) link on the page, made absolute, without duplicates, in page order. */
    fun extractLinks(html: String, pageUrl: String): List<String> {
        val seen = LinkedHashSet<String>()
        for (m in attr.findAll(html)) {
            val raw = m.groupValues[1].ifEmpty { m.groupValues[2] }.trim().replace("&amp;", "&")
            resolve(pageUrl, raw)?.let { seen += it }
        }
        return seen.toList()
    }

    /** Only the links that look like files (by extension). */
    fun fileLinks(links: List<String>, extensions: Set<String> = defaultExtensions): List<String> =
        links.filter { link ->
            val path = link.substringBefore('#').substringBefore('?').substringAfter("://").substringAfter('/', "")
            val name = path.substringAfterLast('/')
            name.contains('.') && name.substringAfterLast('.').lowercase() in extensions
        }

    internal fun resolve(base: String, ref: String): String? {
        if (ref.isEmpty() || ref.startsWith("#")) return null
        val lower = ref.lowercase()
        if (lower.startsWith("javascript:") || lower.startsWith("mailto:") || lower.startsWith("data:") || lower.startsWith("tel:")) {
            return null
        }
        val schemeEnd = base.indexOf("://")
        if (schemeEnd < 0) return null
        val scheme = base.substring(0, schemeEnd)
        val noFragment = ref.substringBefore('#')
        if (noFragment.startsWith("http://", true) || noFragment.startsWith("https://", true)) return noFragment

        val authorityEnd = base.indexOf('/', schemeEnd + 3).let { if (it < 0) base.length else it }
        val origin = base.substring(0, authorityEnd)
        if (noFragment.startsWith("//")) return "$scheme:$noFragment"
        if (noFragment.startsWith("/")) return origin + normalizePath(noFragment)

        val basePath = base.substring(authorityEnd).substringBefore('#').substringBefore('?').ifEmpty { "/" }
        val dir = basePath.substring(0, basePath.lastIndexOf('/') + 1)
        if (noFragment.startsWith("?")) return origin + basePath + noFragment
        return origin + normalizePath(dir + noFragment)
    }

    private fun normalizePath(path: String): String {
        val query = if (path.contains('?')) "?" + path.substringAfter('?') else ""
        val parts = path.substringBefore('?').split('/')
        val out = ArrayList<String>()
        for (p in parts) {
            when (p) {
                "." -> Unit
                ".." -> if (out.size > 1) out.removeAt(out.lastIndex)
                else -> out += p
            }
        }
        return out.joinToString("/") + query
    }
}
