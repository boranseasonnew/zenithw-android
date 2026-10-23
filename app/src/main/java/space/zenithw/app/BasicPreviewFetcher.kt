package space.zenithw.app

import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/** One bounded public oEmbed request in parallel with full extraction, never a
 * second yt-dlp call. Disabled for sessions and non-default network routing so it
 * cannot bypass the user's proxy, IP-family or login requirements. */
internal class BasicPreviewFetcher {
    private val cache=linkedMapOf<String,Pair<Long,MediaPreview>>()
    private val executor=Executors.newFixedThreadPool(2) { Thread(it,"ZenithBasicPreview").apply { isDaemon=true } }
    private val cleanup=Executors.newSingleThreadExecutor { Thread(it,"ZenithPreviewCleanup").apply { isDaemon=true } }
    suspend fun fetch(url: String,cookieId: String?,options: DownloadOptions): MediaPreview? {
        if(cookieId!=null || options.proxy.isNotBlank() || options.networkMode!="auto") return null
        val videoId=PreviewPolicy.youtubeVideoId(url) ?: return null
        val key=url+"\u0000"+options.userAgent
        synchronized(cache) { cache[key]?.takeIf { android.os.SystemClock.elapsedRealtime()-it.first<90000 }?.let { return it.second } }
        return suspendCancellableCoroutine { continuation ->
            val connection=AtomicReference<HttpURLConnection?>()
            val future=executor.submit {
                var result: MediaPreview?=null
                try {
                    if(!continuation.isActive) return@submit
                    val encoded=URLEncoder.encode("https://www.youtube.com/watch?v=$videoId","UTF-8")
                    val current=URL("https://www.youtube.com/oembed?format=json&url=$encoded").openConnection() as HttpURLConnection
                    connection.set(current)
                    current.connectTimeout=2000;current.readTimeout=2000;current.instanceFollowRedirects=false
                    if(options.userAgent.isNotBlank() && options.userAgent.none(Char::isISOControl)) current.setRequestProperty("User-Agent",options.userAgent)
                    if(!continuation.isActive) return@submit
                    if(current.responseCode==200) {
                        val bytes=current.inputStream.use { it.readNBytesCompat(65537) }
                        if(bytes.size<=65536) {
                            val json=JSONObject(String(bytes,Charsets.UTF_8))
                            val title=json.optString("title").filterNot(Char::isISOControl).take(500)
                            if(title.isNotBlank()) result=MediaPreview(url,title,json.optString("author_name").take(200),
                                mediaThumbnail(JSONObject().put("thumbnail",json.optString("thumbnail_url"))),
                                isPlaylist=PreviewPolicy.isCollection(url),metadataReady=false)
                        }
                    }
                } catch(_: Exception) { /* A missing public hint never fails authenticated/full extraction. */ }
                finally { connection.get()?.disconnect() }
                if(continuation.isActive) {
                    result?.let { value -> synchronized(cache) {
                        while(cache.size>=8) cache.remove(cache.keys.first())
                        cache[key]=android.os.SystemClock.elapsedRealtime() to value
                    } }
                    continuation.resume(result)
                }
            }
            continuation.invokeOnCancellation {
                future.cancel(true)
                cleanup.submit { connection.get()?.disconnect() }
            }
        }
    }
}
