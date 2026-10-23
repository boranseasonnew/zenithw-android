package space.zenithw.app

import org.json.JSONObject
import java.util.Locale

internal fun mediaThumbnail(json: JSONObject): String? {
    fun safe(value: String): String? = value.takeIf { it.startsWith("https://") && it.none(Char::isISOControl) }
    safe(json.optString("thumbnail"))?.let { return it }
    val thumbnails=json.optJSONArray("thumbnails") ?: return null
    return (thumbnails.length()-1 downTo 0).firstNotNullOfOrNull { safe(thumbnails.optJSONObject(it)?.optString("url").orEmpty()) }
}

internal fun downloadMetadata(job: DownloadJob,json: JSONObject): DownloadJob = job.copy(
    title=json.optString("title").takeUnless { it.isBlank() || it=="null" }?.filterNot(Char::isISOControl)?.take(500) ?: job.title,
    thumbnail=mediaThumbnail(json) ?: job.thumbnail,
    durationSeconds=json.optDouble("duration",0.0).takeIf { it.isFinite() && it>0 }?.toInt() ?: job.durationSeconds)

internal fun estimatedDownloadBytes(preview: MediaPreview,options: DownloadOptions): Long {
    val formats=preview.formats
    if(options.selectedFormat.isNotBlank()) {
        val selected=options.selectedFormat.split('+').map { id -> formats.firstOrNull { it.id==id } ?: return 0 }
        return if(selected.all { it.sizeBytes>0 }) selected.sumOf { it.sizeBytes } else 0
    }
    val audio=formats.filter { !it.hasVideo && it.hasAudio }.maxByOrNull { it.bitrate }
    if(options.mode==MediaMode.AUDIO) return audio?.sizeBytes ?: 0
    if(options.mode!=MediaMode.VIDEO) return 0
    val video=formats.firstOrNull { it.hasVideo && (options.height<=0 || it.height<=options.height) } ?: return 0
    return if(video.sizeBytes<=0) 0 else if(video.hasAudio) video.sizeBytes
        else audio?.sizeBytes?.takeIf { it>0 }?.plus(video.sizeBytes) ?: 0
}

fun mediaPreview(json: JSONObject,url: String,isPlaylist: Boolean=false): MediaPreview {
    val values=json.optJSONArray("formats")
    val formats=if(values==null) emptyList() else (0 until values.length()).mapNotNull { index ->
        val item=values.optJSONObject(index) ?: return@mapNotNull null
        val id=item.optString("format_id")
        val v=item.optString("vcodec").takeUnless { it in listOf("none","null","") }.orEmpty()
        val a=item.optString("acodec").takeUnless { it in listOf("none","null","") }.orEmpty()
        if(id.isBlank() || v.isBlank() && a.isBlank() || item.optBoolean("has_drm")) return@mapNotNull null
        val height=item.optInt("height").coerceAtLeast(0)
        val fps=item.optDouble("fps",0.0).takeIf { it.isFinite() } ?: 0.0
        val rate=item.optDouble("tbr",0.0).takeIf { it.isFinite() } ?: 0.0
        val size=item.optLong("filesize",item.optLong("filesize_approx")).coerceAtLeast(0)
        val extension=item.optString("ext")
        val label=listOfNotNull(if(height>0) "${height}p" else null,extension.takeIf(String::isNotBlank),
            if(fps>0) "${fps.toInt()} fps" else null,v.takeIf(String::isNotBlank),a.takeIf(String::isNotBlank),
            if(rate>0) "${rate.toInt()} kbps" else null,
            if(size>0) String.format(Locale.ROOT,"%.1f MiB",size/1048576.0) else null).joinToString(" · ")
        MediaFormat(id,extension,height,item.optInt("width"),fps,v,a,rate,size,v.isNotBlank(),a.isNotBlank(),label)
    }.distinctBy { it.id }.sortedWith(compareByDescending<MediaFormat> { it.height }.thenByDescending { it.bitrate })
    val languages=(listOf("subtitles","automatic_captions").flatMap { key ->
        json.optJSONObject(key)?.keys()?.asSequence()?.filter { it!="live_chat" }?.toList().orEmpty()
    }).distinct().sorted()
    return MediaPreview(url,json.optString("title").takeUnless { it.isBlank() || it=="null" }?.filterNot(Char::isISOControl)?.take(500) ?: url,
        json.optString("uploader").takeUnless { it=="null" }.orEmpty(),
        mediaThumbnail(json),json.optDouble("duration",0.0).takeIf { it.isFinite() && it>0 }?.toInt() ?: 0,
        formats.map { it.height }.filter { it>0 }.distinct(),isPlaylist,true,formats,languages)
}
