package app.downly.data

import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RepositoriesTest {
    private val fs = FakeFileSystem()
    private val dir = "/data".toPath()

    @Test
    fun downloadsRoundTrip() = runTest {
        val items = listOf(
            StoredDownload(
                id = 1,
                url = "https://example.com/a.zip",
                fileName = "a.zip",
                status = "PAUSED",
                totalBytes = 100,
                segments = listOf(StoredSegment(0, 0, 99, 50)),
            ),
        )
        JsonDownloadRepository(fs, dir).saveAll(items)
        assertEquals(items, JsonDownloadRepository(fs, dir).loadAll())
    }

    @Test
    fun missingFileGivesEmptyList() = runTest {
        assertEquals(emptyList(), JsonDownloadRepository(fs, dir).loadAll())
    }

    @Test
    fun corruptFileIsBackedUpAndIgnored() = runTest {
        fs.createDirectories(dir)
        fs.write(dir / "downloads.json") { writeUtf8("{broken") }
        assertEquals(emptyList(), JsonDownloadRepository(fs, dir).loadAll())
        assertTrue(fs.exists(dir / "downloads.json.corrupt"))
    }

    @Test
    fun settingsDefaultToEnglishAndRoundTrip() = runTest {
        val repo = JsonSettingsRepository(fs, dir)
        assertEquals(AppLanguage.EN, repo.load().language)
        val changed = AppSettings(language = AppLanguage.FA, theme = ThemeMode.DARK, maxConnections = 4)
        repo.save(changed)
        assertEquals(changed, JsonSettingsRepository(fs, dir).load())
    }
}
