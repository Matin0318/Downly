package app.downly.ui.downloads

import app.downly.core.engine.CurlParser

/** Links (and optional request headers) found in text the user pasted. */
class ParsedInput(val urls: List<String>, val headers: Map<String, String>)

object LinkInput {
    private val urlRegex = Regex("""(?:(?:https?|ftp)://|magnet:\?)[^\s"'<>]+""", RegexOption.IGNORE_CASE)

    /**
     * Understands a plain link, several links in any text, a browser "Copy as cURL" command,
     * or a bare address like `example.com/file.zip`.
     */
    fun parse(text: String): ParsedInput {
        CurlParser.parse(text)?.let { return ParsedInput(listOf(it.url), it.headers) }
        val urls = urlRegex.findAll(text).map { it.value.trimEnd(',', ';') }.distinct().toList()
        if (urls.isNotEmpty()) return ParsedInput(urls, emptyMap())
        val bare = text.trim()
        if (bare.contains('.') && bare.none { it.isWhitespace() } && !bare.startsWith("/")) {
            return ParsedInput(listOf("https://$bare"), emptyMap())
        }
        return ParsedInput(emptyList(), emptyMap())
    }
}
