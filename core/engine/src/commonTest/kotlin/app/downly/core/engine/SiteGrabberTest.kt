package app.downly.core.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class SiteGrabberTest {
    private val page = "https://example.com/dir/page.html?x=1"

    @Test
    fun resolvesRelativeLinks() {
        val html = """
            <a href="a.zip">1</a> <a href='/root/b.pdf'>2</a> <a href="../up/c.mp4">3</a>
            <img src="//cdn.example.com/p.png"> <a href="https://other.org/d.iso#frag">4</a>
            <a href="#top">x</a> <a href="mailto:a@b.c">m</a> <a href="a.zip">dup</a>
        """.trimIndent()
        assertEquals(
            listOf(
                "https://example.com/dir/a.zip",
                "https://example.com/root/b.pdf",
                "https://example.com/up/c.mp4",
                "https://cdn.example.com/p.png",
                "https://other.org/d.iso",
            ),
            SiteGrabber.extractLinks(html, page),
        )
    }

    @Test
    fun filtersByExtensionAndDecodesAmpersand() {
        val links = SiteGrabber.extractLinks("""<a href="/get.php?a=1&amp;b=2">x</a><a href="/f.ZIP?t=1">y</a><a href="/p/">z</a>""", page)
        assertEquals("https://example.com/get.php?a=1&b=2", links[0])
        assertEquals(listOf("https://example.com/f.ZIP?t=1"), SiteGrabber.fileLinks(links))
    }
}
