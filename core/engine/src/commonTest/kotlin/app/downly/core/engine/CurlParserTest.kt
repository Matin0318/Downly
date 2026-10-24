package app.downly.core.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CurlParserTest {
    @Test
    fun parsesUrlHeadersAndCookie() {
        val r = CurlParser.parse(
            "curl 'https://a.com/f.zip' -H 'Referer: https://a.com/' -H 'Accept-Encoding: gzip' " +
                "-b 'sid=1; t=2' -A 'Mozilla/5.0' --compressed",
        )!!
        assertEquals("https://a.com/f.zip", r.url)
        assertEquals("https://a.com/", r.headers["Referer"])
        assertEquals("sid=1; t=2", r.headers["Cookie"])
        assertEquals("Mozilla/5.0", r.headers["User-Agent"])
        assertNull(r.headers["Accept-Encoding"]) // would corrupt the byte ranges
    }

    @Test
    fun handlesLineContinuationAndIgnoredOptions() {
        val r = CurlParser.parse("curl -X GET \\\n  -H \"Cookie: a=b\" \\\n  \"https://a.com/x\"")!!
        assertEquals("https://a.com/x", r.url)
        assertEquals("a=b", r.headers["Cookie"])
    }

    @Test
    fun rejectsNonCurlText() {
        assertNull(CurlParser.parse("https://a.com/x"))
        assertTrue(CurlParser.looksLikeCurl("  curl https://a.com"))
    }
}
