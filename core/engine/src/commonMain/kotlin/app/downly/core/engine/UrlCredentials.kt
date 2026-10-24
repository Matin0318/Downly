package app.downly.core.engine

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * A link split into the URL without login details and an optional HTTP Basic `Authorization` value.
 * Supports links such as `https://user:pass@host/file.zip` (IDM / ADM "site login" for simple servers).
 */
data class UrlCredentials(val cleanUrl: String, val authorization: String?) {
    companion object {
        @OptIn(ExperimentalEncodingApi::class)
        fun parse(url: String): UrlCredentials {
            val schemeEnd = url.indexOf("://")
            if (schemeEnd < 0) return UrlCredentials(url, null)
            val authorityStart = schemeEnd + 3
            var authorityEnd = url.length
            for (i in authorityStart until url.length) {
                if (url[i] == '/' || url[i] == '?' || url[i] == '#') {
                    authorityEnd = i
                    break
                }
            }
            val authority = url.substring(authorityStart, authorityEnd)
            val at = authority.lastIndexOf('@')
            if (at < 0) return UrlCredentials(url, null)

            val userInfo = authority.substring(0, at)
            val user = FileNameResolver.percentDecode(userInfo.substringBefore(':'))
            val password = FileNameResolver.percentDecode(userInfo.substringAfter(':', ""))
            val clean = url.substring(0, authorityStart) + authority.substring(at + 1) + url.substring(authorityEnd)
            val token = Base64.encode("$user:$password".encodeToByteArray())
            return UrlCredentials(clean, "Basic $token")
        }

        /** Host name of [url] in lower case, ignoring login details and port. */
        fun hostOf(url: String): String =
            url.substringAfter("://", url)
                .substringBefore('/').substringBefore('?').substringBefore('#')
                .substringAfterLast('@').substringBefore(':')
                .lowercase()
    }
}
