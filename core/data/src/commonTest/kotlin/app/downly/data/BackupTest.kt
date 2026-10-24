package app.downly.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BackupTest {
    @Test
    fun roundTripKeepsProxyMirrorsAndQuality() {
        val settings = AppSettings(proxyMode = ProxyMode.SOCKS, proxyHost = "10.0.0.2", proxyPort = 1080)
        val download = StoredDownload(
            id = 1,
            url = "ftp://example.com/file.zip",
            fileName = "file.zip",
            status = "PAUSED",
            mirrors = listOf("https://mirror.example.com/file.zip"),
            hlsVariant = "https://cdn.example.com/720.m3u8",
        )
        val text = Backup.encode(settings, listOf(download))
        val restored = assertNotNull(Backup.decode(text))
        assertEquals(ProxyMode.SOCKS, restored.settings.proxyMode)
        assertEquals(1080, restored.settings.proxyPort)
        assertEquals(download.mirrors, restored.downloads.single().mirrors)
        assertEquals(download.hlsVariant, restored.downloads.single().hlsVariant)
    }

    @Test
    fun rejectsOtherJson() {
        assertNull(Backup.decode("{\"hello\":1}"))
        assertNull(Backup.decode("not json"))
    }
}

class BackupPasswordTest {
    @Test
    fun backupNeverContainsTheProxyPassword() {
        val settings = AppSettings(proxyMode = ProxyMode.HTTP, proxyHost = "h", proxyPort = 8080, proxyPassword = "secret")
        val text = Backup.encode(settings, emptyList())
        assertEquals(false, text.contains("secret"))
    }
}
