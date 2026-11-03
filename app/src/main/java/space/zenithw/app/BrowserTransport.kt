package space.zenithw.app

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONObject
import java.io.File
import java.net.URI

/** One app-private adapter shared by metadata and the upstream download CLI.
 * Executable code comes only from the signed APK, never from a runtime download. */
internal object BrowserTransport {
    private val sites=setOf("pornhub.com","pornhub.net","pornhub.org",
        "pornhubpremium.com","pornhubpremium.net","pornhubpremium.org","thumbzilla.com")
    fun supports(url: String): Boolean=runCatching {
        val uri=URI(url)
        val host=uri.host?.lowercase() ?: return@runCatching false
        uri.scheme in setOf("http","https") && uri.rawUserInfo==null && uri.port in setOf(-1,443) &&
            sites.any { host==it || host.endsWith(".$it") }
    }.getOrDefault(false)

    @Synchronized fun prepare(context: Context): File {
        val root=File(context.noBackupFilesDir,"browser-transport")
        val plugin=File(root,"zenith/yt_dlp_plugins/extractor/zenith_browser.py")
        plugin.parentFile!!.mkdirs()
        val code=context.resources.openRawResource(R.raw.browser_transport).use { it.readBytes() }
        if(!plugin.isFile || !plugin.readBytes().contentEquals(code)) plugin.writeBytes(code)
        val native=File(context.applicationInfo.nativeLibraryDir,"libzenithcurl.so")
        val ca=File(context.noBackupFilesDir,"youtubedl-android/packages/python/usr/etc/tls/cert.pem")
        val temporary=File(context.cacheDir,"browser-transport").apply { mkdirs() }
        val config=JSONObject().put("binary",native.absolutePath).put("ca_file",ca.absolutePath)
            .put("temp_dir",temporary.absolutePath).toString()
        val destination=File(plugin.parentFile,"zenith_browser.json")
        if(!destination.isFile || destination.readText()!=config) destination.writeText(config)
        return root
    }

    fun apply(context: Context,request: YoutubeDLRequest,url: String) {
        if(supports(url)) request.addOption("--plugin-dirs",prepare(context).absolutePath)
    }
}
