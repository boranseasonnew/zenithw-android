package space.zenithw.app

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class FeatureReleaseTest {
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ZenithApplication
    @Test fun backwardsCompatibleRecordsAndNewQueueFields() {
        val old=JSONObject().put("id","migration-fixture").put("url","https://example.org/fixture").put("title","Old title")
            .put("options",JSONObject()).put("state","COMPLETED").put("createdAt",123L)
        val loaded=DownloadJob.from(old)
        assertEquals(123L,loaded.queueOrder);assertEquals(0,loaded.priority);assertFalse(loaded.favorite)
        val upgraded=loaded.copy(priority=1,queueOrder=7,favorite=true,collection="Test",options=loaded.options.copy(chargingOnly=true,clipRanges=listOf(ClipRange(1.0,2.0))))
        assertEquals(upgraded,DownloadJob.from(upgraded.json()))
    }
    @Test fun duplicatesRespectQualityAndClipSettings() {
        val a=DownloadJob(url="https://youtu.be/aqz-KE-bpKQ",title="A")
        val b=a.copy(url="https://www.youtube.com/watch?v=aqz-KE-bpKQ")
        assertEquals(duplicateKey(a),duplicateKey(b))
        assertNotEquals(duplicateKey(a),duplicateKey(a.copy(options=a.options.copy(height=720))))
        assertNotEquals(duplicateKey(a),duplicateKey(a.copy(options=a.options.copy(audioFormat="mp3",mode=MediaMode.AUDIO))))
        assertNotEquals(duplicateKey(a),duplicateKey(a.copy(options=a.options.copy(clipRanges=listOf(ClipRange(1.0,2.0))))))
    }
    @Test fun backupExcludesSecretsAndRejectsMaliciousSchema() {
        val backup=SettingsBackup(app,app.store)
        val saved=backup.export();val root=JSONObject(saved)
        assertFalse(root.has("cookies"));assertFalse(root.has("keys"));assertFalse(root.has("outputDirectory"))
        assertEquals(2,backup.validate(saved.toByteArray()).root.getInt("schema"))
        fun rejected(o: JSONObject)=assertTrue(runCatching { backup.validate(o.toString().toByteArray()) }.isFailure)
        rejected(JSONObject(saved).put("schema",999))
        rejected(JSONObject(saved).put("concurrency",20))
        rejected(JSONObject(saved).apply { getJSONObject("defaults").put("cookieId","secret") })
        rejected(JSONObject(saved).apply { getJSONObject("defaults").put("filenameTemplate","../outside.%(ext)s") })
        rejected(JSONObject(saved).put("profiles",JSONArray().put(JSONObject().put("host","../escape").put("options",DownloadOptions().json()))))
        assertTrue(runCatching { backup.validate(ByteArray(512*1024+1)) }.isFailure)
    }
    @Test fun chapterMetadataComesFromSource() {
        val json=JSONObject().put("title","Fixture").put("chapters",JSONArray()
            .put(JSONObject().put("title","Intro").put("start_time",0).put("end_time",10))
            .put(JSONObject().put("title","Invalid").put("start_time",20).put("end_time",10)))
        val preview=mediaPreview(json,"https://example.org/test")
        assertEquals(listOf(MediaChapter("Intro",0.0,10.0)),preview.chapters)
        assertTrue(mediaPreview(JSONObject(),"https://example.org/test").chapters.isEmpty())
    }
    @Test fun sourceProfilesNeverCaptureSessions() {
        val old=app.store.smartEnabled
        try {
            val url="https://feature-fixture.example.org/video"
            app.store.saveSourceProfile(url,DownloadOptions(height=720,cookieId="secret",proxy="http://user:secret@example.org",customArguments="--quiet"))
            val saved=app.store.sourceProfile(url)!!
            assertNull(saved.cookieId);assertEquals("",saved.proxy);assertEquals("",saved.customArguments)
            app.store.smartEnabled=true
            val suggestion=smartSuggestion(app.store,MediaPreview(url,"Fixture",heights=listOf(360,1080)),DownloadOptions(cookieId="keep"))
            assertEquals(360,suggestion.height);assertEquals("keep",suggestion.cookieId)
        } finally {
            app.store.smartEnabled=old
            app.getSharedPreferences("zenithw_v2",0).edit().remove("profile:feature-fixture.example.org").commit()
        }
    }
    @Test fun storageGuardProtectsActiveAndUnknownFiles() {
        val root=java.io.File(app.noBackupFilesDir,"downloads").apply { mkdirs() }
        val id="feature-storage-guard-test"
        val folder=java.io.File(root,id).apply { mkdirs() }
        val file=java.io.File(folder,"fixture.part").apply { writeText("fixture") }
        folder.setLastModified(System.currentTimeMillis()-2*86400000L)
        try {
            assertFalse(StorageGuard(app,app.store).abandoned().any { it.jobId==id })
            app.store.put(DownloadJob(id=id,url="https://example.org/fixture",title="Fixture",state=JobState.PAUSED))
            assertFalse(StorageGuard(app,app.store).abandoned().any { it.jobId==id })
            app.store.change(id) { it.copy(state=JobState.FAILED) }
            val reviewed=StorageGuard(app,app.store).abandoned().filter { it.jobId==id }
            assertEquals(1,reviewed.size)
            app.store.change(id) { it.copy(state=JobState.RUNNING) }
            assertEquals(0L,StorageGuard(app,app.store).cleanup(reviewed));assertTrue(file.exists())
        } finally { app.store.removeEntry(id);file.delete();folder.delete() }
    }
    @Test fun queueSchedulingIsUniqueAndPausePersists()=runBlocking {
        val job=DownloadJob(url="https://example.org/scheduled-fixture",title="Queue fixture",
            options=DownloadOptions(scheduledAt=System.currentTimeMillis()+86400000,unmeteredOnly=true,chargingOnly=true))
        try {
            DownloadWorker.enqueue(app,job)
            DownloadWorker.resumeQueue(app);DownloadWorker.resumeQueue(app)
            val manager=androidx.work.WorkManager.getInstance(app)
            val infos=manager.getWorkInfosForUniqueWork("download-3-${job.id}").get()
            assertEquals(1,infos.size)
            assertTrue(infos.single().constraints.requiresCharging())
            assertEquals(androidx.work.NetworkType.UNMETERED,infos.single().constraints.requiredNetworkType)
            assertTrue(DownloadWorker.eligible(app).none { it.id==job.id })
            DownloadWorker.pauseQueued(app,job.id)
            assertEquals(JobState.PAUSED,LocalStore(app).get(job.id)!!.state)
            DownloadWorker.resumeQueued(app,job.id)
            assertEquals(JobState.QUEUED,app.store.get(job.id)!!.state)
        } finally { DownloadWorker.cancel(app,job.id);app.store.removeEntry(job.id) }
    }
    @Test fun shareIntentsDeduplicateWithoutAutomaticallyDownloading() {
        val show=app.store.showMediaPreview;val oneTap=app.store.oneTapShare
        val before=app.store.jobs.value.size
        try {
            app.store.showMediaPreview=false;app.store.oneTapShare=false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val model=ZenithViewModel(app)
                model.shareIntent("Watch https://example.org/intent-fixture")
                val panel=model.screen.value.analysisId
                model.shareIntent("Watch https://example.org/intent-fixture")
                assertEquals(panel,model.screen.value.analysisId)
                assertNotNull(model.screen.value.preview)
                assertEquals(before,app.store.jobs.value.size)
                model.shareIntent("https://example.org/first\nhttps://example.org/second")
                assertEquals(4,model.screen.value.tab)
                assertEquals(2,model.screen.value.bulkLinks.size)
                model.dismissPreview()
            }
        } finally { app.store.showMediaPreview=show;app.store.oneTapShare=oneTap }
    }
    @Test fun realLocalAudioConversionAndOriginalPreservation()=runBlocking {
        assumeTrue(android.os.Build.VERSION.SDK_INT>=29)
        app.engine.runtimeVersion()
        val tools=LocalMediaTools(app)
        val caps=tools.capabilities()
        assertTrue("PCM output encoder must be present",caps.supports("wav"))
        val created=mutableListOf<Uri>()
        fun document(name: String,mime: String): Uri=app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,"zenith-test-${System.nanoTime()}-$name")
            put(MediaStore.MediaColumns.MIME_TYPE,mime);put(MediaStore.MediaColumns.RELATIVE_PATH,"Download/ZenithW-Test")
        })!!.also(created::add)
        val samples=22050;val header=ByteBuffer.allocate(44+samples*2).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36+samples*2).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(22050).putInt(44100).putShort(2).putShort(16).put("data".toByteArray()).putInt(samples*2)
        repeat(samples) { header.putShort((kotlin.math.sin(it*2*Math.PI*440/22050)*4000).toInt().toShort()) }
        val original=header.array()
        try {
            val input=document("input.wav","audio/wav")
            app.contentResolver.openOutputStream(input)!!.use { it.write(original) }
            for(mode in listOf("wav","flac","mp3","m4a").filter { caps.supports(it) }) {
                val output=document("output.$mode","application/octet-stream")
                var percent=0
                tools.execute(LocalMediaTools.Request(input,output,mode)) { percent=it }
                assertEquals(100,percent)
                assertTrue(app.contentResolver.openInputStream(output)!!.use { it.readBytes().isNotEmpty() })
            }
            assertArrayEquals(original,app.contentResolver.openInputStream(input)!!.use { it.readBytes() })
            val invalid=document("invalid.mp4","application/octet-stream")
            assertTrue(runCatching { tools.execute(LocalMediaTools.Request(input,invalid,"invalid")) {} }.isFailure)
        } finally { created.forEach { app.contentResolver.delete(it,null,null) } }
    }
}
