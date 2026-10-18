package space.zenithw.app

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SealFlowTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val app get()=instrumentation.targetContext.applicationContext as ZenithApplication

    @Test fun metadataOffersRealFormatsAndSubtitleLanguages() {
        val json=JSONObject("""{"title":"Example","duration":120,"formats":[
            {"format_id":"video","ext":"mp4","height":1080,"vcodec":"avc1","acodec":"none","fps":30},
            {"format_id":"audio","ext":"m4a","vcodec":"none","acodec":"mp4a","tbr":128},
            {"format_id":"storyboard","ext":"mhtml","vcodec":"none","acodec":"none"},
            {"format_id":"drm","ext":"mp4","vcodec":"avc1","acodec":"mp4a","has_drm":true}],
            "subtitles":{"tr":[],"live_chat":[]},"automatic_captions":{"en":[]}}""")
        val preview=mediaPreview(json,"https://example.org/video")
        assertTrue(preview.metadataReady)
        assertEquals(listOf("video","audio"),preview.formats.map { it.id })
        assertTrue(preview.formats.first().hasVideo)
        assertFalse(preview.formats.first().hasAudio)
        assertEquals(listOf("en","tr"),preview.subtitleLanguages)
        assertEquals(listOf(1080),preview.heights)
    }

    @Test fun privacyRetainsActiveQueueAndClearsPerMediaDefaults() {
        val name="seal-flow-${UUID.randomUUID()}"
        val context=object: ContextWrapper(app) {
            override fun getSharedPreferences(ignored: String,mode: Int)=super.getSharedPreferences(name,mode)
        }
        try {
            val store=LocalStore(context)
            val queued=DownloadJob(url="https://example.org/a",title="Queued")
            val completed=DownloadJob(url="https://example.org/b",title="Saved",state=JobState.COMPLETED,files=listOf("content://example/media/1"))
            store.put(queued);store.put(completed);store.keepHistory=false
            assertEquals(listOf(queued.id),LocalStore(context).jobs.value.map { it.id })
            assertTrue(store.jobs.value.any { it.id==completed.id })
            assertEquals(completed.files,store.get(completed.id)?.files)
            store.defaultOptions=DownloadOptions(selectedFormat="137",filenameOverride="One",clipStart="5",clipEnd="10",customArguments="-x",restrictFilenames=true)
            assertEquals(DownloadOptions(restrictFilenames=true),store.defaultOptions)
            store.log("https://example.org/private token=secret",queued.id)
            assertFalse(store.taskLogs.value[queued.id].orEmpty().joinToString().contains("secret"))
            assertFalse(store.logs.value.joinToString().contains("example.org"))
        } finally { context.getSharedPreferences(name,Context.MODE_PRIVATE).edit().clear().commit() }
    }

    @Test fun configurationIsAvailableWithoutWaitingForExtraction() {
        val before=app.store.showMediaPreview
        val configure=app.store.configureBeforeDownload
        try {
            app.store.showMediaPreview=false;app.store.configureBeforeDownload=true
            instrumentation.runOnMainSync {
                val model=ZenithViewModel(app)
                model.url("https://example.org/video");model.analyze()
                assertNotNull(model.screen.value.preview)
                assertFalse(model.screen.value.preview!!.metadataReady)
                assertFalse(model.screen.value.analyzing)
                model.dismissPreview()
                assertNull(model.screen.value.preview)
            }
        } finally { app.store.showMediaPreview=before;app.store.configureBeforeDownload=configure }
    }

    @Test fun requestHonorsAudioFormatClipAndNetworkOptions() {
        val directory=java.io.File(app.cacheDir,"request-check-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val method=DownloadEngine::class.java.getDeclaredMethod("request",DownloadJob::class.java,
                java.io.File::class.java,java.io.File::class.java,java.lang.Boolean.TYPE,java.io.File::class.java).apply { isAccessible=true }
            val options=DownloadOptions(mode=MediaMode.AUDIO,selectedFormat="140",networkMode="ipv4",
                proxy="socks5://127.0.0.1:1080",userAgent="Zenith test",filenameOverride="100% music",
                clipStart="10",clipEnd="20",restrictFilenames=true,formatSort="res,fps",maxFilesizeMb=50)
            val request=method.invoke(app.engine,DownloadJob(url="https://example.org/video",title="Test",options=options),directory,null,false,null)
                as com.yausername.youtubedl_android.YoutubeDLRequest
            val args=request.buildCommand()
            assertEquals("140",request.getOption("-f"))
            assertTrue(args.contains("--force-ipv4"))
            assertEquals(options.proxy,request.getOption("--proxy"))
            assertEquals(options.userAgent,request.getOption("--user-agent"))
            assertEquals("*10.0-20.0",request.getOption("--download-sections"))
            assertEquals("50M",request.getOption("--max-filesize"))
            assertTrue(request.getOption("-o").orEmpty().endsWith("100%% music.%(ext)s"))
            val command=method.invoke(app.engine,DownloadJob(url="https://example.org/video",title="Test",
                options=options.copy(mode=MediaMode.VIDEO,customArguments="-f bestaudio -x --audio-format mp3")),directory,null,false,null)
                as com.yausername.youtubedl_android.YoutubeDLRequest
            assertFalse(command.hasOption("--remux-video"))
            assertFalse(command.hasOption("-f")) // custom tokens, not inherited automatic media options
            assertTrue(command.buildCommand().containsAll(listOf("-f","bestaudio","-x","--audio-format","mp3")))
        } finally { directory.delete() }
    }
}
