package space.zenithw.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LivePornhubProbeTest {
    /** Actual site comparison, with no personal cookies, fake success or skipped
     * network failures. Its report contains only timing/status/count fields. */
    @Test(timeout=120000) fun livePornhubNetworkComparison()=runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val app=instrumentation.targetContext.applicationContext as ZenithApplication
        MetadataFixture().use { app.engine.inspect(it.origin+"/live-probe-init",null,DownloadOptions()) }
        val root=File(app.cacheDir,"live-browser-probe-${UUID.randomUUID()}").apply { mkdirs() }
        val reports=app.getExternalFilesDir("smoke-ui") ?: File(app.filesDir,"smoke-ui")
        reports.mkdirs()
        val report=File(reports,"native-pornhub-live.json")
        var process: Process?=null
        try {
            val script=File(root,"live_pornhub_probe.py").apply {
                instrumentation.context.assets.open("browser-transport/live_pornhub_probe.py").use { input -> outputStream().use { input.copyTo(it) } }
            }
            val worker=File(root,"metadata-worker.py").apply {
                app.resources.openRawResource(R.raw.metadata_worker).use { input -> outputStream().use { input.copyTo(it) } }
            }
            val plugin=File(BrowserTransport.prepare(app),"zenith/yt_dlp_plugins/extractor/zenith_browser.py")
            val native=File(app.applicationInfo.nativeLibraryDir)
            val packages=File(app.noBackupFilesDir,"youtubedl-android/packages")
            val engine=File(app.noBackupFilesDir,"youtubedl-android/yt-dlp/yt-dlp")
            val summary=File(root,"summary.txt")
            process=ProcessBuilder(File(native,"libpython.so").absolutePath,"-u",script.absolutePath,
                "--engine",engine.absolutePath,"--worker",worker.absolutePath,"--plugin",plugin.absolutePath,
                "--workspace",root.absolutePath,"--report",report.absolutePath).apply {
                environment().apply {
                    put("PYTHONHOME",File(packages,"python/usr").absolutePath)
                    put("HOME",File(packages,"python/usr").absolutePath)
                    put("SSL_CERT_FILE",File(packages,"python/usr/etc/tls/cert.pem").absolutePath)
                    put("LD_LIBRARY_PATH",listOf("python","ffmpeg","aria2c").joinToString(":") { File(packages,"$it/usr/lib").absolutePath })
                    put("PATH",System.getenv("PATH").orEmpty()+":"+native.absolutePath)
                    put("TMPDIR",root.absolutePath)
                    put("PYTHONUNBUFFERED","1")
                }
                redirectErrorStream(true);redirectOutput(summary)
            }.start()
            val deadline=android.os.SystemClock.elapsedRealtime()+100000
            var finished=false
            while(!finished && android.os.SystemClock.elapsedRealtime()<deadline) {
                finished=try { process.exitValue();true } catch(_: IllegalThreadStateException) { false }
                if(!finished) Thread.sleep(25)
            }
            val measured=report.takeIf { it.isFile }?.readText() ?: "{\"native\":{\"state\":\"report_missing\",\"success\":false}}"
            android.util.Log.i("ZenithLiveProbe",measured)
            assertTrue("Live comparison exceeded deadline: $measured",finished)
            assertEquals("Live native comparison failed: $measured",0,process.exitValue())
            val result=JSONObject(measured).getJSONObject("native")
            assertTrue("Actual source metadata did not succeed: $measured",result.getBoolean("success"))
            assertTrue("Native transport was not attached",result.getBoolean("handler_present"))
            assertTrue("Actual formats missing",result.getInt("format_count")>0)
        } finally {
            process?.destroy()
            root.deleteRecursively()
        }
    }
}
