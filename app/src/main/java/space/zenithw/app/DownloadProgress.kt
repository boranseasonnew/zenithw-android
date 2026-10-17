package space.zenithw.app

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min

/** Uses actual bytes from yt-dlp; unknown sizes stay indeterminate. */
class DownloadProgress {
    data class Sample(val percent: Float, val eta: Long, val line: String)
    private data class Stream(var total: Long=0,var downloaded: Long=0,var complete: Boolean=false)
    private val streams=linkedMapOf<String,Stream>()
    private var item=""
    private var planned=false
    fun accept(line: String): Sample? {
        if(line.startsWith("ZENITHW_PROCESS\t")) return Sample(-1f,-1,"ZenithWProcessing")
        if(line.startsWith("ZENITHW_PLAN\t")) {
            val parts=line.split('\t',limit=6)
            if(parts.size!=6) return null
            streams.clear(); planned=true
            val formats=runCatching { JSONArray(parts[2]) }.getOrNull() ?: JSONArray()
            if(formats.length()>0) for(i in 0 until min(formats.length(),16)) {
                val format=formats.optJSONObject(i) ?: continue
                streams[format.optString("format_id")]=Stream(size(format,"filesize","filesize_approx"))
            } else streams[scalar(parts[3]).ifBlank { "media" }]=Stream(parts[4].toLongOrNull()?.takeIf { it>0 } ?: parts[5].toLongOrNull()?.coerceAtLeast(0) ?: 0)
            item=scalar(parts[1])
            return Sample(0f,-1,"ZenithWTransfer:0:${knownTotal()}")
        }
        if(line.startsWith("ZENITHW_PROGRESS\t")) {
            val parts=line.split('\t',limit=4)
            if(parts.size!=4) return null
            val data=runCatching { JSONObject(parts[3]) }.getOrNull() ?: return null
            val nextItem=scalar(parts[1])
            if(item.isNotBlank() && nextItem!=item && !planned) streams.clear()
            item=nextItem; planned=false
            val key=scalar(parts[2]).ifBlank { "media" }
            val stream=streams.getOrPut(key) { Stream() }
            val total=size(data,"total_bytes","total_bytes_estimate")
            if(total>0) stream.total=total
            stream.downloaded=data.optLong("downloaded_bytes",stream.downloaded).coerceAtLeast(0)
            stream.complete=data.optString("status")=="finished"
            if(stream.complete && stream.total<=0) stream.total=stream.downloaded
            val totalBytes=knownTotal()
            val downloaded=streams.values.sumOf { if(it.total>0) min(it.downloaded,it.total) else it.downloaded }
            val percent=if(totalBytes>0) (100.0*downloaded/totalBytes).toFloat().coerceIn(0f,99f) else -1f
            val speed=data.optDouble("speed",0.0)
            // This estimates transfer only. Merging, cover work and saving have no ETA.
            val eta=if(totalBytes>downloaded && speed.isFinite() && speed>0)
                kotlin.math.ceil((totalBytes-downloaded)/speed).toLong().coerceAtMost(604800) else -1L
            return Sample(percent,eta,"ZenithWTransfer:$downloaded:$totalBytes")
        }
        if(Regex("^\\[(?:Merger|VideoRemuxer|VideoConvertor|ExtractAudio|Metadata|ModifyChapters|EmbedThumbnail|ThumbnailsConvertor|EmbedSubtitle|Fixup[^]]*)]").containsMatchIn(line))
            return Sample(-1f,-1,line)
        return null
    }
    private fun knownTotal(): Long = if(streams.isEmpty() || streams.values.any { it.total<=0 }) 0 else streams.values.sumOf { it.total }
    private fun size(json: JSONObject,first: String,second: String)=json.optLong(first,0).takeIf { it>0 } ?: json.optLong(second,0).coerceAtLeast(0)
    private fun scalar(value: String): String=runCatching { JSONArray("[$value]").optString(0).takeUnless { it=="null" } ?: "" }.getOrDefault("")
    companion object {
        // Whole-object JSON projections omit missing fields. Optional scalar
        // values are isolated TAB fields; NA/null can never invalidate a JSON object.
        const val TEMPLATE="download:ZENITHW_PROGRESS\t%(info.id)j\t%(info.format_id)j\t%(progress.{downloaded_bytes,total_bytes,total_bytes_estimate,speed,status})j"
        const val PLAN="before_dl:ZENITHW_PLAN\t%(id)j\t%(requested_formats.:.{format_id,filesize,filesize_approx})j\t%(format_id)j\t%(filesize)s\t%(filesize_approx)s"
        const val POST="postprocess:ZENITHW_PROCESS\t%(progress.{status,postprocessor})j"
    }
}
