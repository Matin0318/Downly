package app.downly.core.engine

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** A link plus the headers copied from a browser's "Copy as cURL". */
data class ParsedRequest(val url: String, val headers: Map<String, String>)

/**
 * Reads a pasted `curl ...` command (browser DevTools -> Copy as cURL) so a download can reuse the
 * browser's cookies, referer and user agent. Only the options that matter for a plain GET are used.
 */
object CurlParser {
    private val ignoredWithArg = setOf(
        "-X", "--request", "-d", "--data", "--data-raw", "--data-binary", "--data-urlencode", "--data-ascii",
        "-o", "--output", "-x", "--proxy", "-m", "--max-time", "--connect-timeout", "-F", "--form", "-T",
        "--upload-file", "--retry", "-w", "--write-out", "-K", "--config", "--cacert", "--cert", "-E", "--key",
    )

    fun looksLikeCurl(text: String): Boolean = text.trimStart().startsWith("curl ", ignoreCase = true)

    @OptIn(ExperimentalEncodingApi::class)
    fun parse(text: String): ParsedRequest? {
        if (!looksLikeCurl(text)) return null
        val tokens = tokenize(text.replace("\\\r\n", " ").replace("\\\n", " "))
        val headers = LinkedHashMap<String, String>()
        var url: String? = null
        var i = 1 // token 0 is "curl"
        while (i < tokens.size) {
            val t = tokens[i]
            fun next(): String? = tokens.getOrNull(++i)
            when {
                t == "-H" || t == "--header" -> next()?.let { h ->
                    val name = h.substringBefore(':').trim()
                    if (h.contains(':') && name.isNotEmpty()) headers[name] = h.substringAfter(':').trim()
                }
                t == "-b" || t == "--cookie" -> next()?.let { if (it.contains('=')) headers["Cookie"] = it }
                t == "-A" || t == "--user-agent" -> next()?.let { headers["User-Agent"] = it }
                t == "-e" || t == "--referer" -> next()?.let { headers["Referer"] = it }
                t == "-u" || t == "--user" -> next()?.let {
                    headers["Authorization"] = "Basic " + Base64.encode(it.encodeToByteArray())
                }
                t == "--url" -> next()?.let { if (url == null) url = it }
                t in ignoredWithArg -> next()
                t.startsWith("http://", true) || t.startsWith("https://", true) -> if (url == null) url = t
            }
            i++
        }
        val found = url ?: return null
        return ParsedRequest(found, RequestHeaders.sanitize(headers))
    }

    /** Splits like a POSIX shell: spaces separate, '...' and "..." group, backslash escapes inside "...". */
    internal fun tokenize(input: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var inToken = false
        var quote = ' '
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                quote == '\'' -> if (c == '\'') quote = ' ' else cur.append(c)
                quote == '"' -> when {
                    c == '"' -> quote = ' '
                    c == '\\' && i + 1 < input.length && input[i + 1] in "\"\\$`" -> cur.append(input[++i])
                    else -> cur.append(c)
                }
                c == '\'' || c == '"' -> { quote = c; inToken = true }
                c == '$' && i + 1 < input.length && input[i + 1] == '\'' -> { quote = '\''; inToken = true; i++ }
                c.isWhitespace() -> if (inToken) { out += cur.toString(); cur.clear(); inToken = false }
                else -> { cur.append(c); inToken = true }
            }
            i++
        }
        if (inToken) out += cur.toString()
        return out
    }
}
