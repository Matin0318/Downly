package app.downly.core.engine

import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileChecksumTest {
    @Test
    fun computesKnownHashes() = runTest {
        val fs = FakeFileSystem()
        val path = "/a.txt".toPath()
        fs.write(path) { writeUtf8("abc") }
        assertEquals("900150983cd24fb0d6963f7d28e17f72", FileChecksum.compute(fs, path, ChecksumAlgorithm.MD5))
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            FileChecksum.compute(fs, path, ChecksumAlgorithm.SHA256),
        )
    }

    @Test
    fun normalizesAndValidates() {
        val h = FileChecksum.normalize("  BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD  abc.txt")
        assertTrue(FileChecksum.isValid(h))
        assertFalse(FileChecksum.isValid("xyz"))
    }
}
