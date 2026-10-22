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
            nodes().firstOrNull { it.text?.toString()==label || it.contentDescription?.toString()==label }?.let { return it }
            android.os.SystemClock.sleep(100)
        }
        captureUi("missing-label")
        error("UI label missing: $label; visible: ${nodes().mapNotNull { it.text?.toString() }}")
    }
    private fun clickLabel(label: String) {
        val deadline=android.os.SystemClock.uptimeMillis()+8000
        while(android.os.SystemClock.uptimeMillis()<deadline) {
            var node=nodes().firstOrNull { (it.text?.toString()==label || it.contentDescription?.toString()==label) && it.isVisibleToUser }
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
        captureUi("not-clickable")
        error("UI label not clickable: $label; visible: ${nodes().mapNotNull { it.text?.toString() }}")
    }
    @Test fun languages() {
        assertEquals("Einstellungen",AppLanguage.context(app).getString(R.string.settings))
        app.startActivity(android.content.Intent(app,MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        clickLabel("Einstellungen")
        clickLabel("Sprache")
        val labels=listOf("tr" to "Dil","en" to "Language","de" to "Sprache","fr" to "Langue","ru" to "Язык")
        var current="Deutsch"
        for((tag,label) in labels) {
            clickLabel(current)
            clickLabel(AppLanguage.choices.first { it.first==tag }.second)
            current=AppLanguage.choices.first { it.first==tag }.second
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
        clickLabel(current);clickLabel("Türkçe")
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
        val entries=listOf("Dil" to "Türkçe","İndirme motoru" to "Şimdi güncelle","Video ve ses" to "Video kalitesi",
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
            println("ENGINE VERIFIED: ${status.version}; actual runtime=${app.engine.runtimeVersion()}")
            assertEquals(status.version,app.store.lastEngineVersion)
            assertTrue(app.store.lastEngineCheck>0)
        }
    }
    @Test fun saveNightlyEngineForRestart()=runBlocking {
        app.store.autoUpdate=false
        app.store.channel="nightly"
        app.engine.initializeAndUpdate(force=true)
        assertNull(app.engine.status.value.warning)
        val actual=app.engine.runtimeVersion()
        assertEquals("nightly · $actual",app.store.lastEngineVersion)
        assertTrue(app.getSharedPreferences("zenithw_v2",0).edit().putString("smokeNightlyVersion",actual).commit())
        println("NIGHTLY SAVED: $actual")
    }
    @Test fun restoreNightlyEngineAfterRestart()=runBlocking {
        val saved=app.getSharedPreferences("zenithw_v2",0).getString("smokeNightlyVersion",null)
        assertNotNull(saved)
        assertEquals("nightly",app.store.channel)
        assertFalse(app.store.autoUpdate)
        app.engine.initializeAndUpdate(force=false)
        assertNull(app.engine.status.value.warning)
        assertEquals(saved,app.engine.runtimeVersion())
        assertEquals("nightly · $saved",app.store.lastEngineVersion)
        println("NIGHTLY RESTORED WITHOUT NETWORK UPDATE: $saved")
    }
    @Test fun captureHomeDownloadCard() {
        val language=app.store.language
        val directory=java.io.File(app.cacheDir,"home-card-fixture").apply { mkdirs() }
        val job=DownloadJob(url="https://example.org/video",title="Örnek video",state=JobState.RUNNING,
            progress=36f,progressKnown=true,phase="downloading",detail="İndiriliyor",durationSeconds=185,
            bytesDownloaded=123_000_000,totalBytes=340_000_000,etaSeconds=92)
        val bitmap=android.graphics.Bitmap.createBitmap(960,540,android.graphics.Bitmap.Config.ARGB_8888)
        try {
            val canvas=android.graphics.Canvas(bitmap)
            val paint=android.graphics.Paint().apply { shader=android.graphics.LinearGradient(0f,0f,960f,540f,
                android.graphics.Color.rgb(44,82,106),android.graphics.Color.rgb(109,93,137),android.graphics.Shader.TileMode.CLAMP) }
            canvas.drawRect(0f,0f,960f,540f,paint)
            paint.shader=null;paint.color=android.graphics.Color.rgb(196,207,213)
            canvas.drawCircle(730f,125f,48f,paint)
            paint.color=android.graphics.Color.rgb(25,47,61)
            val path=android.graphics.Path().apply { moveTo(0f,540f);lineTo(0f,410f);lineTo(310f,175f);lineTo(610f,440f);lineTo(820f,270f);lineTo(960f,400f);lineTo(960f,540f);close() }
            canvas.drawPath(path,paint)
            val input=java.io.File(directory,"cover.png")
            input.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            assertNotNull(JobArtwork(app).retain(job.id,listOf(input)))
            app.store.language="tr";app.store.put(job)
            app.startActivity(android.content.Intent(app,MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
            waitForLabel("Örnek video");waitForLabel("3:05")
            waitForLabel("36%")
            android.os.SystemClock.sleep(700)
            captureUi("home-download-card")
        } finally {
            app.store.change(job.id) { it.copy(state=JobState.CANCELLED) }
            JobArtwork(app).cached(job.id)?.delete()
            bitmap.recycle();directory.listFiles()?.forEach { it.delete() };directory.delete()
            app.store.language=language
        }
    }

    @Test fun settingsKeepsScrollWhenReturningFromCategory() {
        val language=app.store.language
        try {
            app.store.language="tr"
            app.startActivity(android.content.Intent(app,MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
            clickLabel("Ayarlar")
            scrollToLabel("Aralık, sıra ve hata davranışı")
            val before=android.graphics.Rect().also { waitForLabel("Aralık, sıra ve hata davranışı").getBoundsInScreen(it) }
            clickLabel("Oynatma listeleri")
            waitForLabel("Liste indirmeye izin ver")
            clickLabel("Ayarlara dön")
            val after=android.graphics.Rect().also { waitForLabel("Aralık, sıra ve hata davranışı").getBoundsInScreen(it) }
            assertTrue("Settings scroll moved: $before -> $after",kotlin.math.abs(before.top-after.top)<32)
            captureUi("settings-scroll-restored")
        } finally { app.store.language=language }
    }

    @Test fun singleMediaPlaylistSelection() {
        val source="https://www.instagram.com/reel/fixture/"
        val single=org.json.JSONObject("""{"id":"fixture","title":"Reel","url":"https://cdn.example.org/expiring.mp4"}""")
        assertEquals(listOf(PlaylistEntry(1,"Reel",source)),playlistEntries(single,source))
        assertTrue(playlistEntries(org.json.JSONObject("""{"_type":"playlist","entries":[]}"""),source).isEmpty())
        val list=org.json.JSONObject("""{"entries":[null,{"id":"dQw4w9WgXcQ","title":"First","playlist_index":1},{"url":"https://example.org/second","title":"Second","playlist_index":1}]}""")
        val entries=playlistEntries(list,source)
        assertEquals(2,entries.size)
        assertEquals(2,entries.map { it.index }.toSet().size)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ",entries.first().url)
    }

    @Test fun bulkLinkDraft() {
        instrumentation.runOnMainSync {
            val model=ZenithViewModel(app)
            val first=model.screen.value.bulkLinks.single().id
            model.bulkLink(first,"https://example.org/one")
            model.addBulkLink()
            val second=model.screen.value.bulkLinks.last().id
            model.bulkLink(second,"https://example.org/two\nhttps://example.org/three")
            model.bulkMode(MediaMode.AUDIO)
            model.tab(2);model.tab(4)
            assertEquals(listOf("https://example.org/one","https://example.org/two","https://example.org/three"),model.screen.value.bulkLinks.map { it.url })
            assertEquals(MediaMode.AUDIO,model.screen.value.bulkMode)
            model.removeBulkLink(first)
            assertEquals(second,model.screen.value.bulkLinks.first().id)
            repeat(60) { model.addBulkLink() }
            assertEquals(50,model.screen.value.bulkLinks.size)
            model.bulkLink(second,"https://example.org/a\nhttps://example.org/b")
            assertEquals("https://example.org/two",model.screen.value.bulkLinks.first().url)
            model.screen.value.bulkLinks.map { it.id }.forEach(model::removeBulkLink)
            assertEquals("",model.screen.value.bulkLinks.single().url)
        }
    }

    @Test fun bulkWorkspace() {
        app.store.language="tr"
        app.startActivity(android.content.Intent(app,MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
        clickLabel("Toplu indirme ve komutlar")
        waitForLabel("Bağlantı 1")
        clickLabel("Satır ekle")
        waitForLabel("Bağlantı 2")
        clickLabel("Ayarlar")
        waitForLabel("Uygulama dili")
        clickLabel("Toplu indirme ve komutlar")
        waitForLabel("Bağlantı 2")
        clickLabel("Bağlantı 2 satırını sil")
        waitForLabel("Bağlantı 1")
        captureUi("bulk-workspace")
    }

    @Test fun sealWorkspaceControls() {
        val previousPreview=app.store.showMediaPreview
        val previousConfigure=app.store.configureBeforeDownload
        val previousTheme=app.store.themeMode
        val previousOptions=app.store.defaultOptions
        try {
            app.store.language="tr"
            app.store.showMediaPreview=false;app.store.configureBeforeDownload=true;app.store.themeMode="dark"
            // Save/restore coverage intentionally leaves AUDIO selected. Start
            // this independent UI test in VIDEO so it exercises a real mode change.
            app.store.defaultOptions=previousOptions.copy(mode=MediaMode.VIDEO)
            app.startActivity(android.content.Intent(app,MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
            waitForLabel("İndir")
            val field=nodes().first { it.className?.toString()=="android.widget.EditText" }
            assertTrue(field.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,
                android.os.Bundle().apply { putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"https://example.org/video") }))
            clickLabel("İndir")
            waitForLabel("İndirmeyi ayarla")
            waitForLabel("İndirmeyi başlat")
            captureUi("configure-before-metadata")
            clickLabel("Ses");waitForLabel("Ses biçimi")
            captureUi("audio-configuration")
            clickLabel("Pencereyi kapat")
            clickLabel("Toplu indirme ve komutlar")
            clickLabel("Komutlar");waitForLabel("yt-dlp seçenekleri")
            captureUi("command-templates")
            clickLabel("Günlük");waitForLabel("Günlük kaynağı")
            captureUi("task-logs")
            app.store.themeMode="light"
            app.startActivity(android.content.Intent(app,MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
            waitForLabel("İndir")
            captureUi("light-home")
        } finally {
            app.store.showMediaPreview=previousPreview;app.store.configureBeforeDownload=previousConfigure
            app.store.themeMode=previousTheme;app.store.defaultOptions=previousOptions
        }
    }
}
