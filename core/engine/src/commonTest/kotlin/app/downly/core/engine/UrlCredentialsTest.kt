package app.downly.core.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UrlCredentialsTest {
    @Test
    fun plainUrlIsUnchanged() {
        val r = UrlCredentials.parse("https://example.com/a/b.zip?x=1")
        assertEquals("https://example.com/a/b.zip?x=1", r.cleanUrl)
        assertNull(r.authorization)
    }

    @Test
    fun extractsBasicAuthAndStripsUserInfo() {
        val r = UrlCredentials.parse("https://user:pass@example.com:8080/f.bin?q=a@b")
        assertEquals("https://example.com:8080/f.bin?q=a@b", r.cleanUrl)
        // base64("user:pass")
        assertEquals("Basic dXNlcjpwYXNz", r.authorization)
    }

    @Test
    fun decodesPercentEncodedPassword() {
        val r = UrlCredentials.parse("http://me:p%40ss@host/x")
        assertEquals("http://host/x", r.cleanUrl)
        // base64("me:p@ss")
        assertEquals("Basic bWU6cEBzcw==", r.authorization)
    }

    @Test
    fun hostIgnoresLoginPortAndCase() {
        assertEquals("example.com", UrlCredentials.hostOf("https://U:P@Example.com:443/x"))
    }
}
