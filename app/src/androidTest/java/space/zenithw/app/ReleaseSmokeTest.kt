package space.zenithw.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReleaseSmokeTest {
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ZenithApplication
    private fun options()=DownloadOptions(mode=MediaMode.AUDIO,height=720,container="mkv",audioFormat="mp3",
        audioBitrate=192,subtitles=true,autoSubtitles=true,subtitleLanguages="tr.*",playlist=true,
        wifiOnly=true,embedMetadata=false,embedThumbnail=true,downloadThumbnail=true,thumbnailFormat="png",
        sponsorBlock=true,useAria2=true,ariaConnections=8,fragments=8,retries=5,speedLimitKbps=512,
        networkMode="ipv4",filenameTemplate="%(title)s.%(ext)s")

    @Test fun saveSettings() {
        app.store.autoUpdate=false
        app.store.channel="nightly"
        app.store.defaultOptions=options().copy(scheduledAt=1234,selectedFormat="temporary",cookieId="temporary")
        val profile=app.vault.capture("example.org","session=test")
        app.store.selectedCookieId=profile.id
        // Flush outstanding apply writes before the shell force-stops this process.
        assertTrue(app.getSharedPreferences("zenithw_v2",0).edit().putString("smokeCookie",profile.id).commit())
    }

    @Test fun restoreSettings() {
        val restored=LocalStore(app)
        assertFalse(restored.autoUpdate)
        assertEquals("nightly",restored.channel)
        assertEquals(options(),restored.defaultOptions)
        val id=app.getSharedPreferences("zenithw_v2",0).getString("smokeCookie",null)
        assertNotNull(id)
        assertEquals(id,restored.selectedCookieId)
        assertTrue(app.vault.profiles().any { it.id==id })
        val model=ZenithViewModel(app)
        assertEquals(id,model.screen.value.cookieId)
        assertEquals("nightly",model.screen.value.channel)
        assertFalse(model.screen.value.autoUpdate)
    }

    @Test fun updateChannels()=runBlocking {
        for(channel in listOf("stable","nightly","stable")) {
            app.store.channel=channel
            app.engine.initializeAndUpdate(force=true)
            val status=app.engine.status.value
            assertTrue("$channel engine was not ready",status.ready)
            assertNull("$channel update failed: ${status.warning}",status.warning)
            assertTrue(status.version.startsWith("$channel · "))
            assertEquals(status.version,app.store.lastEngineVersion)
            assertTrue(app.store.lastEngineCheck>0)
        }
    }
}
