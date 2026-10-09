package space.zenithw.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EngineArchiveTest {
    private val tag="2026.09.27.232945"
    private fun archive(prefix: String="#!/usr/bin/env python3\n", name: String="yt_dlp/version.py", text: String="__version__ = '$tag'\n"): ByteArray {
        val bytes=ByteArrayOutputStream()
        bytes.write(prefix.toByteArray())
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(text.toByteArray())
            zip.closeEntry()
        }
        return bytes.toByteArray()
    }
    private fun rejected(bytes: ByteArray): String {
        try { EngineArchive.version(bytes.inputStream()) }
        catch(error: IOException) { return error.message.orEmpty() }
        throw AssertionError("Invalid archive accepted")
    }
    @Test fun pythonPreambleIsHandledBeforeZipParsing() {
        val bytes=archive()
        // A normal ZIP stream cannot see an archive through the executable prefix.
        ZipInputStream(bytes.inputStream()).use { assertNull(it.nextEntry) }
        assertEquals(tag,EngineArchive.version(bytes.inputStream()))
    }
    @Test fun ordinaryZipIsAlsoReadable() {
        assertEquals(tag,EngineArchive.version(archive(prefix="").inputStream()))
    }
    @Test fun realBundledZipappIsReadable() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        val bytes=app.resources.openRawResource(R.raw.ytdlp).use { it.readBytes() }
        assertEquals("#!/usr/bin/env python3\n",String(bytes.copyOfRange(0,23),Charsets.UTF_8))
        assertEquals(BundledEngine.VERSION,EngineArchive.version(bytes.inputStream()))
    }
    @Test fun malformedArchivesHaveActionableErrors() {
        assertTrue(rejected("not a zip".toByteArray()).contains("missing ZIP header"))
        assertTrue(rejected(archive(name="other.py")).contains("version file missing"))
        assertTrue(rejected(archive(text="__version__ = 'unexpected'\n")).contains("invalid version declaration"))
        assertTrue(rejected(archive(prefix="#!"+"x".repeat(1100))).contains("invalid zipapp header"))
    }
    @Test fun oversizedVersionFileIsRejected() {
        assertTrue(rejected(archive(text="x".repeat(9000))).contains("version file too large"))
    }
}
