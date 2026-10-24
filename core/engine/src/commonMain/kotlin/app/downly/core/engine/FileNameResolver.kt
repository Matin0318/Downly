package app.downly.core.engine

/** Decides the file name from Content-Disposition or the URL, and makes it safe for disk. */
object FileNameResolver {
    private const val FALLBACK = "download"
    private const val INVALID_CHARS = "\\/:*?\"<>|"

    private val extendedRegex = Regex("""filename\*\s*=\s*[^']*'[^']*'([^;]+)""", RegexOption.IGNORE_CASE)
    private val plainRegex = Regex("""filename\s*=\s*(?:"([^"]*)"|([^;]+))""", RegexOption.IGNORE_CASE)

    fun resolve(contentDisposition: String?, url: String): String {
        val raw = contentDisposition?.let { fromDisposition(it) } ?: fromUrl(url)
        return sanitize(raw)
    }

    private fun fromDisposition(header: String): String? {
        extendedRegex.find(header)?.groupValues?.get(1)?.trim()?.let { value ->
            val decoded = percentDecode(value)
            if (decoded.isNotBlank()) return decoded
        }
        val match = plainRegex.find(header) ?: return null
        val value = match.groupValues[1].ifEmpty { match.groupValues[2] }.trim()
        return value.ifBlank { null }
    }

    private fun fromUrl(url: String): String {
        val withoutQuery = url.substringBefore('#').substringBefore('?')
        val path = withoutQuery.substringAfter("://", withoutQuery).substringAfter('/', "")
        return percentDecode(path.substringAfterLast('/'))
    }

    private fun sanitize(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = base
            .map { c -> if (c < ' ' || c in INVALID_CHARS) '_' else c }
            .joinToString("")
            .trim()
            .trim('.')
            .take(200)
        return cleaned.ifBlank { FALLBACK }
    }

    internal fun percentDecode(input: String): String {
        val out = ArrayList<Byte>(input.length)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '%' && i + 2 < input.length) {
                val high = input[i + 1].digitToIntOrNull(16)
                val low = input[i + 2].digitToIntOrNull(16)
                if (high != null && low != null) {
                    out.add(((high shl 4) or low).toByte())
                    i += 3
                    continue
                }
            }
            val step = if (c.isHighSurrogate() && i + 1 < input.length && input[i + 1].isLowSurrogate()) 2 else 1
            out.addAll(input.substring(i, i + step).encodeToByteArray().toList())
            i += step
        }
        return out.toByteArray().decodeToString()
    }
}
