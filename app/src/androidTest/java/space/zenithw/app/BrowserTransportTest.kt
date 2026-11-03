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
class BrowserTransportTest {
    /** Actual APK executable, Python worker and ordinary CLI through a local TLS
     * fixture. No external account, website, certificate bypass or media is used. */
    @Test(timeout=90000) fun nativeBrowserTransportMetadataDownloadTlsAndCancellation()=runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val app=instrumentation.targetContext.applicationContext as ZenithApplication
        // init() unpacks the signed APK's Python/certificate runtime without an
        // engine update or dependency on an external metadata request.
        val fixture=MetadataFixture()
        try { app.engine.inspect(fixture.origin+"/browser-runtime-init",null,DownloadOptions()) }
        finally { fixture.close() }
        val root=File(app.cacheDir,"native-browser-probe-${UUID.randomUUID()}").apply { mkdirs() }
        var process: Process?=null
        try {
            fun asset(name: String): File=File(root,name).apply {
                instrumentation.context.assets.open("browser-transport/$name").use { input -> outputStream().use { input.copyTo(it) } }
            }
            val probe=asset("native_browser_probe.py")
            val certificate=asset("fixture-cert.pem")
            val key=asset("fixture-key.pem")
            val worker=File(root,"metadata-worker.py").apply {
                app.resources.openRawResource(R.raw.metadata_worker).use { input -> outputStream().use { input.copyTo(it) } }
            }
            val plugin=File(BrowserTransport.prepare(app),"zenith/yt_dlp_plugins/extractor/zenith_browser.py")
            val native=File(app.applicationInfo.nativeLibraryDir)
            val packages=File(app.noBackupFilesDir,"youtubedl-android/packages")
            val ca=File(packages,"python/usr/etc/tls/cert.pem")
            val engine=File(app.noBackupFilesDir,"youtubedl-android/yt-dlp/yt-dlp")
            val output=File(root,"summary.txt")
            process=ProcessBuilder(File(native,"libpython.so").absolutePath,"-u",probe.absolutePath,
                "--engine",engine.absolutePath,"--worker",worker.absolutePath,
                "--plugin",plugin.absolutePath,"--binary",File(native,"libzenithcurl.so").absolutePath,
                "--system-ca",ca.absolutePath,"--certificate",certificate.absolutePath,
                "--key",key.absolutePath,"--workspace",root.absolutePath).apply {
                environment().apply {
                    put("PYTHONHOME",File(packages,"python/usr").absolutePath)
                    put("HOME",File(packages,"python/usr").absolutePath)
                    put("SSL_CERT_FILE",ca.absolutePath)
                    put("LD_LIBRARY_PATH",listOf("python","ffmpeg","aria2c").joinToString(":") { File(packages,"$it/usr/lib").absolutePath })
                    put("PATH",System.getenv("PATH").orEmpty()+":"+native.absolutePath)
                    put("TMPDIR",root.absolutePath)
                    put("PYTHONUNBUFFERED","1")
                }
                redirectErrorStream(true)
                redirectOutput(output)
            }.start()
            val deadline=android.os.SystemClock.elapsedRealtime()+80000
            var finished=false
            while(!finished && android.os.SystemClock.elapsedRealtime()<deadline) {
                finished=try { process.exitValue();true } catch(_: IllegalThreadStateException) { false }
                if(!finished) Thread.sleep(25)
            }
            assertTrue("Native browser probe exceeded deadline",finished)
            val summary=output.readText()
            assertEquals("Native browser probe failed: $summary",0,process.exitValue())
            val result=JSONObject(summary.lineSequence().last { it.startsWith("{") })
            for(name in listOf("native_execution","metadata","cli_download","tls_verified",
                "source_403_retained","cookie_scope","captured_agent","parent_cancellation"))
                assertTrue("Native browser check missing: $name",result.getBoolean(name))
        } finally {
            process?.destroy()
            root.deleteRecursively()
        }
    }
}
