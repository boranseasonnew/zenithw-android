package space.zenithw.app

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class MediaMode(val label: String) { VIDEO("Video"), AUDIO("Ses"), SUBTITLES("Altyazı") }
enum class JobState { QUEUED, RUNNING, SAVING, COMPLETED, FAILED, CANCELLED }
data class MediaPreview(val url: String, val title: String, val uploader: String = "",
    val thumbnail: String? = null, val duration: Int = 0, val heights: List<Int> = emptyList())
data class CookieProfile(val id: String, val host: String, val count: Int, val updatedAt: Long)
data class DownloadOptions(
    val mode: MediaMode = MediaMode.VIDEO,
    val height: Int = 0,
    val container: String = "mp4",
    val audioFormat: String = "m4a",
    val audioBitrate: Int = 0,
    val codec: String = "auto",
    val subtitles: Boolean = false,
    val autoSubtitles: Boolean = false,
    val embedSubtitles: Boolean = true,
    val subtitleLanguages: String = "tr.*,en.*",
    val subtitleFormat: String = "srt",
    val playlist: Boolean = false,
    val playlistItems: String = "",
    val cookieId: String? = null,
    val wifiOnly: Boolean = false,
    val scheduledAt: Long = 0,
    val embedMetadata: Boolean = true,
    val embedChapters: Boolean = true,
    val embedUploader: Boolean = true,
    val embedThumbnail: Boolean = false,
    val downloadThumbnail: Boolean = false,
    val thumbnailFormat: String = "jpg",
    val sponsorBlock: Boolean = false,
    val sponsorCategories: String = "sponsor,selfpromo",
    val useAria2: Boolean = false,
    val ariaConnections: Int = 4,
    val fragments: Int = 4,
    val retries: Int = 3,
    val fragmentRetries: Int = 3,
    val speedLimitKbps: Int = 0,
    val networkMode: String = "auto",
    val proxy: String = "",
    val userAgent: String = "",
    val filenameTemplate: String = "%(title).150B [%(id)s].%(ext)s",
    val playlistOrder: String = "normal",
    val playlistErrors: String = "continue",
    val sponsorAction: String = "remove",
    val skipUnavailable: Boolean = true,
    val writeInfoJson: Boolean = false,
    val writeDescription: Boolean = false,
    val downloadArchive: Boolean = false,
    val keepOriginal: Boolean = false,
    val sleepSeconds: Int = 0,
    val timeout: Int = 20,
    val selectedFormat: String = ""
) {
    fun json() = JSONObject().apply {
        put("mode",mode.name); put("height",height); put("container",container)
        put("audioFormat",audioFormat); put("audioBitrate",audioBitrate); put("codec",codec)
        put("subtitles",subtitles); put("autoSubtitles",autoSubtitles); put("embedSubtitles",embedSubtitles)
        put("subtitleLanguages",subtitleLanguages); put("subtitleFormat",subtitleFormat)
        put("playlist",playlist); put("playlistItems",playlistItems); put("cookieId",cookieId)
        put("wifiOnly",wifiOnly); put("scheduledAt",scheduledAt); put("embedMetadata",embedMetadata)
        put("embedChapters",embedChapters); put("embedUploader",embedUploader)
        put("embedThumbnail",embedThumbnail); put("downloadThumbnail",downloadThumbnail)
        put("thumbnailFormat",thumbnailFormat); put("sponsorBlock",sponsorBlock)
        put("sponsorCategories",sponsorCategories); put("useAria2",useAria2)
        put("ariaConnections",ariaConnections); put("fragments",fragments); put("retries",retries)
        put("fragmentRetries",fragmentRetries); put("speedLimitKbps",speedLimitKbps)
        put("networkMode",networkMode); put("proxy",proxy); put("userAgent",userAgent)
        put("playlistOrder",playlistOrder);
        put("playlistErrors",playlistErrors);
        put("sponsorAction",sponsorAction);
        put("skipUnavailable",skipUnavailable);
        put("writeInfoJson",writeInfoJson);
        put("writeDescription",writeDescription);
        put("downloadArchive",downloadArchive);
        put("keepOriginal",keepOriginal);
        put("sleepSeconds",sleepSeconds);
        put("timeout",timeout);
        put("filenameTemplate",filenameTemplate); put("selectedFormat",selectedFormat)
    }
    companion object {
        fun from(o: JSONObject) = DownloadOptions(
            mode=runCatching { MediaMode.valueOf(o.optString("mode")) }.getOrDefault(MediaMode.VIDEO),
            height=o.optInt("height"), container=o.optString("container","mp4"),
            audioFormat=o.optString("audioFormat","m4a"), audioBitrate=o.optInt("audioBitrate"),
            codec=o.optString("codec","auto"), subtitles=o.optBoolean("subtitles"),
            autoSubtitles=o.optBoolean("autoSubtitles"), embedSubtitles=o.optBoolean("embedSubtitles",true),
            subtitleLanguages=o.optString("subtitleLanguages","tr.*,en.*"),
            subtitleFormat=o.optString("subtitleFormat","srt"), playlist=o.optBoolean("playlist"),
            playlistItems=o.optString("playlistItems"), cookieId=o.optString("cookieId").takeIf { it.isNotBlank() && it != "null" },
            wifiOnly=o.optBoolean("wifiOnly"), scheduledAt=o.optLong("scheduledAt"),
            embedMetadata=o.optBoolean("embedMetadata",true), embedChapters=o.optBoolean("embedChapters",true),
            embedUploader=o.optBoolean("embedUploader",true), embedThumbnail=o.optBoolean("embedThumbnail"),
            downloadThumbnail=o.optBoolean("downloadThumbnail"), thumbnailFormat=o.optString("thumbnailFormat","jpg"),
            sponsorBlock=o.optBoolean("sponsorBlock"), sponsorCategories=o.optString("sponsorCategories","sponsor,selfpromo"),
            useAria2=o.optBoolean("useAria2"), ariaConnections=o.optInt("ariaConnections",4).coerceIn(1,16),
            fragments=o.optInt("fragments",4).coerceIn(1,16), retries=o.optInt("retries",3).coerceIn(0,10),
            fragmentRetries=o.optInt("fragmentRetries",3).coerceIn(0,10),
            speedLimitKbps=o.optInt("speedLimitKbps").coerceAtLeast(0),
            networkMode=o.optString("networkMode","auto"), proxy=o.optString("proxy"),
            userAgent=o.optString("userAgent"),
            filenameTemplate=o.optString("filenameTemplate","%(title).150B [%(id)s].%(ext)s"),
            playlistOrder=o.optString("playlistOrder","normal"),
            playlistErrors=o.optString("playlistErrors","continue"),
            sponsorAction=o.optString("sponsorAction","remove"),
            skipUnavailable=o.optBoolean("skipUnavailable",true),
            writeInfoJson=o.optBoolean("writeInfoJson",false),
            writeDescription=o.optBoolean("writeDescription",false),
            downloadArchive=o.optBoolean("downloadArchive",false),
            keepOriginal=o.optBoolean("keepOriginal",false),
            sleepSeconds=o.optInt("sleepSeconds",0).coerceIn(0,60),
            timeout=o.optInt("timeout",20).coerceIn(5,120),
            selectedFormat=o.optString("selectedFormat")
        )
    }
}
data class DownloadJob(
    val id: String = UUID.randomUUID().toString(), val url: String, val title: String,
    val thumbnail: String? = null, val options: DownloadOptions = DownloadOptions(),
    val state: JobState = JobState.QUEUED, val progress: Float = 0f,
    val detail: String = "Sırada", val createdAt: Long = System.currentTimeMillis(),
    val files: List<String> = emptyList(), val notes: List<String> = emptyList()
) {
    fun json() = JSONObject().apply {
        put("id",id); put("url",url); put("title",title); put("thumbnail",thumbnail)
        put("options",options.json()); put("state",state.name); put("progress",progress)
        put("detail",detail); put("createdAt",createdAt); put("files",JSONArray(files)); put("notes",JSONArray(notes))
    }
    companion object {
        fun from(o: JSONObject) = DownloadJob(
            id=o.getString("id"),url=o.getString("url"),title=o.getString("title"),
            thumbnail=o.optString("thumbnail").takeIf { it.startsWith("https://") },
            options=DownloadOptions.from(o.getJSONObject("options")),
            state=runCatching { JobState.valueOf(o.optString("state")) }.getOrDefault(JobState.FAILED),
            progress=o.optDouble("progress").toFloat().coerceIn(0f,100f),
            detail=o.optString("detail"),createdAt=o.optLong("createdAt"),
            files=o.optJSONArray("files")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
            notes=o.optJSONArray("notes")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        )
    }
}
fun normalizeUrl(input: String): String {
    val value=input.trim()
    val extracted=Regex("https?://[^\\s<>]+").find(value)?.value ?: value
    val normalized=if (!extracted.contains("://")) "https://$extracted" else extracted
    val uri=Uri.parse(normalized)
    require(uri.scheme == "https" || uri.scheme == "http") { "Geçerli bir paylaşım bağlantısı gir." }
    require(!uri.host.isNullOrBlank() && uri.host!!.contains('.') && uri.userInfo == null) { "Geçerli bir site adresi gir." }
    require(!normalized.any { it.isISOControl() }) { "Bağlantıda geçersiz karakter var." }
    return normalized
}
