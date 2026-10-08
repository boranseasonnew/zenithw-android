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
        networkMode="ipv4",playlistOrder="reverse",playlistErrors="stop",sponsorAction="mark",writeDescription=true,downloadArchive=true,keepOriginal=true,sleepSeconds=3,timeout=60,proxy="socks5://127.0.0.1:1080",filenameTemplate="%(title)s.%(ext)s")

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
        assertEquals(options(),model.screen.value.defaults)
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
        captureUi("missing-label")
        error("UI label missing: $label; visible: ${nodes().mapNotNull { it.text?.toString() }}")
    }
    private fun clickLabel(label: String) {
        val deadline=android.os.SystemClock.uptimeMillis()+8000
        while(android.os.SystemClock.uptimeMillis()<deadline) {
            var node=nodes().firstOrNull { it.text?.toString()==label && it.isVisibleToUser }
            while(node!=null) {
                if(node.isClickable && node.isEnabled && node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) {
                    println("Clicked: $label")
                    instrumentation.waitForIdleSync()
                    return
                }
                node=node.parent
            }
            android.os.SystemClock.sleep(100)
        }
        error("UI label not clickable: $label; visible: ${nodes().mapNotNull { it.text?.toString() }}")
    }
    @Test fun languages() {
        assertEquals("Einstellungen",AppLanguage.context(app).getString(R.string.settings))
        app.startActivity(android.content.Intent(app,MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        clickLabel("Einstellungen")
        clickLabel("Sprache")
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
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        app.getSharedPreferences("zenithw_v2",0).edit().putBoolean("languagesChecked",true).commit()
    }

    private fun scrollToLabel(label: String) {
        repeat(35) {
            if(nodes().any { it.text?.toString()==label && it.isVisibleToUser })return
            // A page-sized accessibility scroll can jump over a partially clipped row.
            // Move one category at a time with the same small swipe a person uses.
            val metrics=app.resources.displayMetrics
            val start=android.os.SystemClock.uptimeMillis()
            for(index in 0..10) {
                val action=when(index) { 0 -> android.view.MotionEvent.ACTION_DOWN; 10 -> android.view.MotionEvent.ACTION_UP; else -> android.view.MotionEvent.ACTION_MOVE }
                val event=android.view.MotionEvent.obtain(start,android.os.SystemClock.uptimeMillis(),action,
                    metrics.widthPixels*.5f,metrics.heightPixels*(.75f-.15f*index/10),0)
                event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
                instrumentation.uiAutomation.injectInputEvent(event,true)
                event.recycle();android.os.SystemClock.sleep(20)
            }
            android.os.SystemClock.sleep(200)
        }
        waitForLabel(label)
    }
    private fun captureUi(name: String) {
        val directory=java.io.File(app.getExternalFilesDir(null),"smoke-ui").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            java.io.FileOutputStream(java.io.File(directory,"$name.png")).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
        java.io.File(directory,"$name.txt").writeText(nodes().mapNotNull { it.text?.toString() }.joinToString("\n"))
    }
    private fun openSettingsRoot() {
        app.startActivity(android.content.Intent(app,MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
        android.os.SystemClock.sleep(400)
        clickLabel("Ayarlar")
        waitForLabel("Uygulama dili")
        android.os.SystemClock.sleep(250)
        captureUi("settings-root")
    }
    @Test fun settingsCategories() {
        app.store.language="tr"
        val entries=listOf("Dil" to "English","İndirme motoru" to "Şimdi güncelle","Video ve ses" to "Video kalitesi",
            "Dosya içeriği" to "Medya bilgilerini göm","Altyazılar" to "Altyazıları indir","SponsorBlock" to "Kategoriler",
            "Aria2c" to "Bağlantı sayısı","Bağlantı ve hız" to "Hız sınırı","Oynatma listeleri" to "Liste indirmeye izin ver",
            "Dosyalar ve arşiv" to "Dosya adı","Hazır profiller" to "Günlük","İşlem günlüğü" to "Temizle")
        for((entry,control) in entries) {
            openSettingsRoot()
            scrollToLabel(entry);clickLabel(entry);waitForLabel(control)
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            android.os.SystemClock.sleep(250)
            waitForLabel("Ayarlar")
        }
        openSettingsRoot()
        scrollToLabel("Tarayıcı oturumları");clickLabel("Tarayıcı oturumları");clickLabel("Yeni oturum bağla")
        waitForLabel("Site adresi")
        captureUi("cookie-address")
        assertFalse(nodes().any { it.text?.toString() in listOf("YouTube","Instagram","TikTok") })
        assertEquals("https://example.org",normalizeUrl("example.org"))
        assertEquals("https://example.org/login",normalizeUrl("https://example.org/login"))
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
    }

    @Test fun updateChannels()=runBlocking {
        for(channel in listOf("stable","nightly","stable")) {
            app.store.channel=channel
            app.engine.initializeAndUpdate(force=true)
            val status=app.engine.status.value
            assertTrue("$channel engine was not ready",status.ready)
            assertNull("$channel update failed: ${status.warning}",status.warning)
            assertTrue(status.version.startsWith("$channel · "))
            assertEquals(status.version.substringAfter(" · "),app.engine.runtimeVersion())
            assertEquals(status.version,app.store.lastEngineVersion)
            assertTrue(app.store.lastEngineCheck>0)
        }
    }
}
