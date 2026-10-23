package space.zenithw.app

import android.os.SystemClock
import java.util.UUID

enum class AnalysisState { Idle,Preparing,FetchingMetadata,BasicInfoReady,FetchingFormats,Ready,Error,Cancelled }

/** Deliberately accepts event names and numeric samples only, never URL or metadata. */
class AnalysisTrace(private val store: LocalStore,private val start: Long=SystemClock.elapsedRealtime()) {
    val id=UUID.randomUUID().toString().take(8)
    private val seen=mutableSetOf<String>()
    @Synchronized fun mark(event: String) {
        require(event.matches(Regex("[a-z_]+")))
        if(seen.add(event)) sample(event,SystemClock.elapsedRealtime()-start)
    }
    fun sample(event: String,milliseconds: Long) {
        require(event.matches(Regex("[a-z_]+")))
        val line="PERF id=$id event=$event ms=${milliseconds.coerceAtLeast(0)}"
        store.log(line)
        if(BuildConfig.DEBUG) android.util.Log.d("ZenithPerf",line)
    }
    fun preview(value: MediaPreview) {
        if(value.title.isNotBlank() && value.title!=value.url) mark("first_title")
        if(value.duration>0) mark("first_duration")
        if(value.thumbnail!=null) mark("thumbnail_url")
        if(value.formats.isNotEmpty()) mark("formats_ready")
    }
}
