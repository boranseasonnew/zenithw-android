package space.zenithw.app

import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import coil.ImageLoader
import coil.EventListener
import coil.request.ImageRequest
import coil.request.SuccessResult
import java.io.File
import java.net.ServerSocket
import kotlin.concurrent.thread

/** Same harness is copied onto the baseline tag and candidate by the benchmark workflow.
 * External sites may fail in CI. Record failures, never turn them into successful timings. */
class MetadataBenchmarkTest {
    @Test fun compareMetadataScenarios() {
        val instrument=InstrumentationRegistry.getInstrumentation()
        val app=instrument.targetContext.applicationContext as ZenithApplication
        app.store.autoUpdate=false; app.store.showMediaPreview=true
        app.store.configureBeforeDownload=true;app.store.selectedCookieId=null
        app.store.defaultOptions=DownloadOptions()
        // Join any startup updater, then pin each revision to its bundled engine.
        // Otherwise an automatic engine update makes before/after irreproducible.
        runBlocking { app.engine.initializeAndUpdate(false) }
        runCatching {
            val field=app.engine.javaClass.getDeclaredField("metadataRuntime").apply { isAccessible=true }
            val runtime=field.get(app.engine);runtime.javaClass.getMethod("close").invoke(runtime)
        }
        val executable=File(app.noBackupFilesDir,"youtubedl-android/yt-dlp/yt-dlp")
        app.resources.openRawResource(R.raw.ytdlp).use { input -> executable.outputStream().use { input.copyTo(it) } }
        app.store.lastEngineVersion="stable · ${BundledEngine.VERSION}"
        val covers=java.util.concurrent.ConcurrentHashMap<String,Long>()
        val loader=ImageLoader.Builder(app).eventListener(object: EventListener {
            override fun onSuccess(request: ImageRequest,result: SuccessResult) { covers[request.data.toString()]=SystemClock.elapsedRealtime() }
        }).build()
        instrument.runOnMainSync { coil.Coil.setImageLoader(loader) }
        val samples=JSONArray()
        val versionStart=SystemClock.elapsedRealtime()
        val runtime=runBlocking { app.engine.runtimeVersion() }
        val versionMillis=SystemClock.elapsedRealtime()-versionStart
        val startupTrace=JSONArray(app.store.logs.value.filter { "PERF " in it })
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: ZenithViewModel
            scenario.onActivity { model=ViewModelProvider(it)[ZenithViewModel::class.java] }
            fun inspect(name: String,url: String,options: DownloadOptions=DownloadOptions()) {
                app.store.clearLogs()
                covers.clear()
                val result=JSONObject().put("scenario",name)
                var start=0L
                scenario.onActivity {
                    model.defaults(options);model.cookie(null);model.url(url)
                    start=SystemClock.elapsedRealtime();model.analyze()
                    result.put("panel_state_ms",SystemClock.elapsedRealtime()-start)
                }
                fun screenshot(suffix: String) {
                    instrument.uiAutomation.takeScreenshot()?.let { bitmap ->
                        val file=File(app.getExternalFilesDir(null),"metadata-$suffix.png")
                        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                        bitmap.recycle()
                    }
                }
                if(name=="local_hls_delayed") { Thread.sleep(350);screenshot("skeleton") }
                val deadline=start+40000
                var title: Long?=null;var duration: Long?=null;var thumbnail: Long?=null;var formats: Long?=null
                while(SystemClock.elapsedRealtime()<deadline) {
                    val s=model.screen.value;val p=s.preview;val elapsed=SystemClock.elapsedRealtime()-start
                    if(p!=null) {
                        if(title==null && p.title!=p.url && p.title.isNotBlank()) title=elapsed
                        if(duration==null && p.duration>0) duration=elapsed
                        if(thumbnail==null && p.thumbnail!=null) thumbnail=elapsed
                        if(formats==null && p.formats.isNotEmpty()) formats=elapsed
                    }
                    if(s.analysisError!=null || !s.analyzing && (p?.metadataReady==true || p==null)) break
                    Thread.sleep(10)
                }
                val s=model.screen.value
                val metadataTotal=SystemClock.elapsedRealtime()-start
                val coverDeadline=SystemClock.elapsedRealtime()+2500
                val coverUrl=s.preview?.thumbnail
                while(coverUrl!=null && !covers.containsKey(coverUrl) && SystemClock.elapsedRealtime()<coverDeadline) Thread.sleep(10)
                val coverLoaded=coverUrl?.let { covers[it] }?.let { (it-start).coerceAtLeast(thumbnail ?: 0) }
                result.put("title_ms",title ?: JSONObject.NULL).put("duration_ms",duration ?: JSONObject.NULL)
                    .put("thumbnail_url_ms",thumbnail ?: JSONObject.NULL).put("formats_ms",formats ?: JSONObject.NULL)
                    .put("cover_loaded_ms",coverLoaded ?: JSONObject.NULL).put("total_ms",metadataTotal)
                    .put("success",s.preview?.metadataReady==true && s.analysisError==null)
                    .put("error",s.analysisError ?: JSONObject.NULL)
                    .put("trace",JSONArray(app.store.logs.value.filter { "PERF " in it || "INSPECT " in it }))
                samples.put(result)
                if(name=="local_hls_delayed") screenshot("ready")
                scenario.onActivity { model.dismissPreview() }
            }
            inspect("youtube_video","https://www.youtube.com/watch?v=aqz-KE-bpKQ")
            inspect("youtube_video_warm_cache","https://www.youtube.com/watch?v=aqz-KE-bpKQ")
            inspect("youtube_playlist","https://www.youtube.com/playlist?list=PL4lCao7KL_QFodcLWhDpGCYnngnHtQ-Xf")
            inspect("youtube_shorts","https://www.youtube.com/shorts/BGQWPY4IigY")
            inspect("vimeo","https://vimeo.com/22439234")
            inspect("invalid_url","not a valid address")
            MetadataFixture().use { fixture ->
                inspect("local_hls_cold",fixture.origin+"/first")
                inspect("local_hls_warm_runtime",fixture.origin+"/second")
                inspect("local_hls_cache",fixture.origin+"/second")
                inspect("local_hls_duration",fixture.origin+"/structured")
            }
            MetadataFixture(250).use { fixture -> inspect("local_hls_delayed",fixture.origin+"/delayed") }
            // Deterministic slow proxy: accept connections but never answer the CONNECT request.
            ServerSocket(0).use { server ->
                val sockets=java.util.Collections.synchronizedList(mutableListOf<java.net.Socket>())
                val accept=thread(isDaemon=true) {
                    runCatching { while(!server.isClosed) sockets.add(server.accept()) }
                }
                try { inspect("slow_network","https://www.youtube.com/watch?v=aqz-KE-bpKQ",DownloadOptions(proxy="http://127.0.0.1:${server.localPort}")) }
                finally { sockets.toList().forEach { runCatching { it.close() } }; server.close();accept.join(1000) }
            }
            val burstStart=SystemClock.elapsedRealtime()
            repeat(5) { i ->
                scenario.onActivity { model.url("https://www.youtube.com/watch?v=aqz-KE-bpKQ&start=$i");model.analyze() }
                Thread.sleep(40)
            }
            Thread.sleep(250)
            val current=model.screen.value
            samples.put(JSONObject().put("scenario","five_links").put("elapsed_ms",SystemClock.elapsedRealtime()-burstStart)
                .put("latest_input_retained",current.url.endsWith("start=4"))
                .put("stale_result",current.preview?.url?.let { it!=current.url } ?: false))
            scenario.onActivity { model.dismissPreview() }
        }
        val result=JSONObject().put("runtime",runtime).put("version_command_ms",versionMillis)
            .put("startup_trace",startupTrace)
            .put("sdk",android.os.Build.VERSION.SDK_INT).put("abi",android.os.Build.SUPPORTED_ABIS.first())
            .put("measurement_note","panel_state_ms measures state publication, not first displayed frame; thumbnail_url_ms is not image decode time")
            .put("samples",samples)
        File(app.getExternalFilesDir(null),"metadata-benchmark.json").writeText(result.toString(2))
        println("BENCHMARK_RESULT ${result}")
    }
}
