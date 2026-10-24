package app.downly.core.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class FileNameResolverTest {
    @Test
    fun usesQuotedFilenameFromHeader() {
        assertEquals("report.pdf", FileNameResolver.resolve("attachment; filename=\"report.pdf\"", "https://a.com/x"))
    }

    @Test
    fun decodesUtf8ExtendedFilename() {
        val header = "attachment; filename*=UTF-8''%D9%81%D8%A7%DB%8C%D9%84.pdf"
        assertEquals("فایل.pdf", FileNameResolver.resolve(header, "https://a.com/x"))
    }

    @Test
    fun fallsBackToUrlPathAndDecodesIt() {
        assertEquals("my video.mp4", FileNameResolver.resolve(null, "https://a.com/files/my%20video.mp4?token=abc"))
    }

    @Test
    fun usesDefaultNameWhenNothingAvailable() {
        assertEquals("download", FileNameResolver.resolve(null, "https://example.com/"))
    }

    @Test
    fun stripsPathTraversal() {
        assertEquals("passwd", FileNameResolver.resolve("attachment; filename=\"../../etc/passwd\"", "https://a.com/x"))
    }
}
