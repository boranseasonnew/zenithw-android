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
        app.store.language="de"
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
        assertEquals("de",restored.language)
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
        assertEquals("de",model.screen.value.language)
    }

    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun nodes(): List<android.view.accessibility.AccessibilityNodeInfo> {
        val result=mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
        fun visit(node: android.view.accessibility.AccessibilityNodeInfo) {
            result.add(node)
            for(index in 0 until node.childCount) node.getChild(index)?.let(::visit)
        }
        instrumentation.uiAutomation.rootInActiveWindow?.let(::visit)
        return result
    }
    private fun waitForLabel(label: String): android.view.accessibility.AccessibilityNodeInfo {
        val deadline=android.os.SystemClock.uptimeMillis()+8000
        while(android.os.SystemClock.uptimeMillis()<deadline) {
            // Compose exposes virtual accessibility nodes. Walk them directly instead of
            // using the platform text-search method, which need not search virtual children.
            nodes().firstOrNull { it.text?.toString()==label }?.let { return it }
            android.os.SystemClock.sleep(100)
        }
        error("UI label missing: $label; visible: ${nodes().mapNotNull { it.text?.toString() }}")
    }
    private fun clickLabel(label: String) {
        var node: android.view.accessibility.AccessibilityNodeInfo?=waitForLabel(label)
        while(node!=null) {
            if(node.isClickable) {
                assertTrue(node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
                return
            }
            node=node.parent
        }
        error("UI label not clickable: $label")
    }
    @Test fun languages() {
        assertEquals("Einstellungen",AppLanguage.context(app).getString(R.string.settings))
        app.startActivity(android.content.Intent(app,MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        clickLabel("Einstellungen")
        val labels=listOf("tr" to "Dil","en" to "Language","de" to "Sprache","fr" to "Langue","ru" to "Язык")
        for((tag,label) in labels) {
            clickLabel(AppLanguage.choices.first { it.first==tag }.second)
            waitForLabel(label)
            assertEquals(tag,app.store.language)
            val localized=AppLanguage.context(app,tag)
            assertEquals(localized.getString(R.string.download_error),
                AppLanguage.message(localized,"İndirme tamamlanamadı. Bağlantıyı veya seçili oturumu kontrol et."))
            val paste=mapOf("tr" to "Yapıştır","en" to "Paste","de" to "Einfügen","fr" to "Coller","ru" to "Вставить")
            assertEquals(paste[tag],localized.getString(R.string.paste))
            val eta=AppLanguage.message(localized,"Yaklaşık 2 dk 5 sn")
            assertTrue(eta.contains("2") && eta.contains("5"))
        }
        clickLabel("Türkçe")
        waitForLabel("Dil")
        app.getSharedPreferences("zenithw_v2",0).edit().putBoolean("languagesChecked",true).commit()
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
