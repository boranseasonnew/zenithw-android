package space.zenithw.app

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DownloadRegressionTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val app get()=instrumentation.targetContext.applicationContext as ZenithApplication

    @Test fun extractedArtworkAndDurationSurviveHistoryReload() {
        val name="metadata-regression-${UUID.randomUUID()}"
        val context=object: ContextWrapper(app) {
            override fun getSharedPreferences(ignored: String,mode: Int)=super.getSharedPreferences(name,mode)
        }
        try {
            val store=LocalStore(context)
            val initial=DownloadJob(url="https://example.org/video",title="example.org")
            store.put(initial)
            val metadata=JSONObject("""{"title":"Resolved title","duration":125.5,"thumbnails":[{"url":"https://example.org/cover.jpg"}]}""")
            store.change(initial.id) { downloadMetadata(it,metadata) }
            val reloaded=LocalStore(context).get(initial.id)!!
            assertEquals("Resolved title",reloaded.title)
            assertEquals("https://example.org/cover.jpg",reloaded.thumbnail)
            assertEquals(125,reloaded.durationSeconds)
            assertEquals(reloaded.thumbnail,downloadMetadata(reloaded,JSONObject("""{"thumbnail":null}""")).thumbnail)
            assertNull(mediaThumbnail(JSONObject("""{"thumbnail":"file:///private/secret"}""")))
        } finally { context.getSharedPreferences(name,0).edit().clear().commit() }
    }

    @Test fun cookieRequestUsesCapturedBrowserAgentAndAllowsExplicitOverride() {
        val profile=app.vault.capture("fixture-${UUID.randomUUID()}.example.org","session=fixture","Fixture browser/1.0")
        val directory=File(app.cacheDir,"session-regression-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val method=DownloadEngine::class.java.getDeclaredMethod("request",DownloadJob::class.java,
                File::class.java,File::class.java,java.lang.Boolean.TYPE,File::class.java).apply { isAccessible=true }
            fun request(agent: String="")=method.invoke(app.engine,DownloadJob(url="https://example.org/video",title="Fixture",
                options=DownloadOptions(cookieId=profile.id,userAgent=agent)),directory,null,false,null) as YoutubeDLRequest
            assertEquals("Fixture browser/1.0",request().getOption("--user-agent"))
            assertEquals("Explicit agent",request("Explicit agent").getOption("--user-agent"))
            val args=request().buildCommand()
            assertTrue(args.contains("before_dl:ZenithWMetadata:%(.{title,thumbnail,thumbnails,duration})j"))
        } finally { app.vault.remove(profile.id);directory.delete() }
    }

    @Test fun singleVideoWithPlaylistDefaultStaysOnHomeAndQueuesOneItem() {
        val previous=app.store.defaultOptions
        val show=app.store.showMediaPreview
        val configure=app.store.configureBeforeDownload
        val remember=app.store.rememberDownloadChoices
        val before=app.store.jobs.value.map { it.id }.toSet()
        try {
            app.store.defaultOptions=DownloadOptions(playlist=true)
            app.store.showMediaPreview=false;app.store.configureBeforeDownload=true
            app.store.rememberDownloadChoices=false
            lateinit var model: ZenithViewModel
            instrumentation.runOnMainSync {
                model=ZenithViewModel(app)
                model.url("https://example.org/single-video");model.analyze()
                assertFalse(model.screen.value.preview!!.isPlaylist)
                model.enqueue(DownloadOptions(playlist=true,scheduledAt=System.currentTimeMillis()+86400000))
                assertFalse(model.screen.value.playlistLoading)
            }
            val deadline=System.currentTimeMillis()+5000
            while(model.screen.value.submitting && System.currentTimeMillis()<deadline) Thread.sleep(20)
            assertFalse("Queue submission timed out",model.screen.value.submitting)
            val added=app.store.jobs.value.filter { it.id !in before }
            assertEquals(1,added.size)
            assertFalse(added.single().options.playlist)
            assertEquals(0,model.screen.value.tab)
            assertEquals(added.single().id,model.screen.value.homeJobId)
            assertTrue(app.store.defaultOptions.playlist)
        } finally {
            app.store.jobs.value.filter { it.id !in before }.forEach { DownloadWorker.cancel(app,it.id) }
            app.store.defaultOptions=previous;app.store.showMediaPreview=show
            app.store.configureBeforeDownload=configure;app.store.rememberDownloadChoices=remember
        }
    }

    @Test fun privateArtworkIsResizedAndCanBeReadAfterRecreatingTheStore() {
        val directory=File(app.cacheDir,"cover-regression-${UUID.randomUUID()}").apply { mkdirs() }
        val id=UUID.randomUUID().toString()
        val bitmap=Bitmap.createBitmap(1200,800,Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        try {
            val input=File(directory,"cover.png")
            input.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
            val saved=JobArtwork(app).retain(id,listOf(input))
            assertNotNull(saved)
            assertEquals(saved,JobArtwork(app).cached(id))
            val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
            android.graphics.BitmapFactory.decodeFile(saved!!.absolutePath,bounds)
            assertTrue(bounds.outWidth in 1..768 && bounds.outHeight in 1..768)
        } finally { bitmap.recycle();JobArtwork(app).cached(id)?.delete();directory.listFiles()?.forEach { it.delete() };directory.delete() }
    }
}
