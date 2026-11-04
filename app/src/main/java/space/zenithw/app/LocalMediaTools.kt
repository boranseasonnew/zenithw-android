package space.zenithw.app

import android.content.Context
import android.net.Uri
import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext

/** Local SAF files only. Every native invocation uses argv (never a shell). */
class LocalMediaTools(private val context: Context) {
    private val inputPolicy=listOf("-protocol_whitelist","file,pipe","-format_whitelist","mov,matroska,webm,mp3,aac,wav,flac,ogg,srt,webvtt,gif,webp,webp_pipe,mpegts,avi,asf,flv")
    private val native=File(context.applicationInfo.nativeLibraryDir)
    data class Capabilities(val encoders: Set<String>,val filters: Set<String>) {
        fun supports(mode: String)=when(mode) {
            "mp4","trim_exact" -> "libx264" in encoders && "aac" in encoders
            "mp3" -> "libmp3lame" in encoders
            "m4a" -> "aac" in encoders
            "wav" -> "pcm_s16le" in encoders
            "flac" -> "flac" in encoders
            "gif" -> "gif" in encoders
            "webp" -> "libwebp_anim" in encoders
            "burn" -> "subtitles" in filters && "libx264" in encoders && "aac" in encoders
            "attach" -> "mov_text" in encoders
            "srt" -> "srt" in encoders || "subrip" in encoders
            "vtt" -> "webvtt" in encoders
            else -> mode in setOf("remux_mp4","remux_mkv","mute","trim")
        }
    }
    suspend fun capabilities(): Capabilities {
        FFmpeg.getInstance().init(context)
        fun names(output: String)=output.lineSequence().mapNotNull {
            Regex("^\\s*[A-Z.]{6}\\s+(\\S+)").find(it)?.groupValues?.get(1)
        }.toSet()
        val enc=command("libffmpeg.so",listOf("-hide_banner","-encoders"),15000)
        val filters=command("libffmpeg.so",listOf("-hide_banner","-filters"),15000).lineSequence().mapNotNull {
            Regex("^\\s*[TSC.]{3}\\s+(\\S+)").find(it)?.groupValues?.get(1)
        }.toSet()
        return Capabilities(names(enc),filters)
    }
    data class Request(val source: Uri,val destination: Uri,val mode: String,
        val start: Double=0.0,val end: Double=0.0,val preset: String="balanced",
        val resolution: Int=0,val bitrate: Int=192,val sampleRate: Int=44100,
        val fps: Int=10,val loop: Int=0,val videoBitrateKbps: Int=0,val subtitle: Uri?=null,val offset: Double=0.0)
    suspend fun execute(request: Request,progress: (Int)->Unit) = withContext(Dispatchers.IO) {
        require(request.source.scheme=="content" && request.destination.scheme=="content" && request.source!=request.destination)
        require(request.mode in setOf("mp4","mp3","m4a","wav","flac","gif","webp","srt","vtt","remux_mp4","remux_mkv","mute","trim","trim_exact","attach","burn"))
        require(request.preset in setOf("fast","balanced","quality") && request.bitrate in 32..320)
        require(request.sampleRate in setOf(22050,44100,48000) && request.resolution in setOf(0,240,360,480,720,1080))
        require(request.loop in 0..1 && request.videoBitrateKbps in 0..20000)
        require(request.offset.isFinite() && kotlin.math.abs(request.offset)<=86400)
        val caps=capabilities();require(caps.supports(request.mode)) { "encoder_unavailable" }
        val folder=File(context.cacheDir,"media-tools/${java.util.UUID.randomUUID()}").apply { check(mkdirs()) }
        val extension=when(request.mode) { "remux_mp4","mute","trim","trim_exact","attach","burn" -> "mp4";"remux_mkv" -> "mkv";else -> request.mode }
        File(folder,".zenith-owned").writeText("ZenithW.LocalMediaTools/1")
        val input=File(folder,if(request.mode in setOf("srt","vtt")) "input.srt" else "input.media")
        val output=File(folder,"output.$extension")
        var touchedOutput=false
        try {
            copyFrom(request.source,input,if(request.mode in setOf("srt","vtt")) 4L*1024*1024 else Long.MAX_VALUE)
            val probe=JSONObject(command("libffprobe.so",inputPolicy+listOf("-v","error","-show_format","-show_streams","-of","json",input.absolutePath),30000))
            val duration=probe.optJSONObject("format")?.optDouble("duration",0.0) ?: 0.0
            val args=mutableListOf("-hide_banner","-loglevel","error","-nostdin","-y")
            val clipped=request.end>0 || request.start>0 || request.mode in setOf("trim","trim_exact","gif","webp")
            if(clipped) {
                ClipRange(request.start,request.end).validate(duration)
                args.addAll(listOf("-ss",request.start.toString()))
            }
            if(request.mode in setOf("srt","vtt") && request.offset!=0.0) args.addAll(listOf("-itsoffset",request.offset.toString()))
            args.addAll(inputPolicy+listOf("-i",input.absolutePath))
            val subtitle=request.subtitle?.let {
                require(it.scheme=="content")
                File(folder,"subtitle.srt").also { f -> copyFrom(it,f,4L*1024*1024);validateSubtitle(f) }
            }
            if(request.mode in setOf("attach","burn")) require(subtitle!=null) { "subtitle_required" }
            if(request.mode=="attach") args.addAll(inputPolicy+listOf("-itsoffset",request.offset.toString(),"-i",subtitle!!.absolutePath))
            if(clipped) args.addAll(listOf("-t",(request.end-request.start).toString()))
            when(request.mode) {
                "remux_mp4","remux_mkv","trim" -> args.addAll(listOf("-map","0:v:0?","-map","0:a?","-c","copy"))
                "mute" -> args.addAll(listOf("-map","0:v:0","-c:v","copy","-an"))
                "mp4","burn","trim_exact" -> {
                    args.addAll(listOf("-map","0:v:0","-map","0:a:0?","-c:v","libx264","-preset",if(request.preset=="fast") "ultrafast" else "veryfast",
                        "-crf",when(request.preset) { "fast" -> "28";"quality" -> "18";else -> "23" },"-pix_fmt","yuv420p","-c:a","aac","-b:a","${request.bitrate}k"))
                    if(request.videoBitrateKbps>0) args.addAll(listOf("-b:v","${request.videoBitrateKbps}k"))
                    val filters=mutableListOf<String>()
                    if(request.resolution>0) filters.add("scale=-2:min(${request.resolution}\\,ih)")
                    if(request.mode=="burn") filters.add("subtitles=filename='${subtitle!!.absolutePath}':fontsdir=/system/fonts")
                    if(filters.isNotEmpty()) args.addAll(listOf("-vf",filters.joinToString(",")))
                }
                "mp3","m4a","wav","flac" -> args.addAll(listOf("-map","0:a:0","-vn","-c:a",when(request.mode) { "mp3" -> "libmp3lame";"m4a" -> "aac";"wav" -> "pcm_s16le";else -> "flac" },
                    "-ar",request.sampleRate.toString())+if(request.mode in setOf("mp3","m4a")) listOf("-b:a","${request.bitrate}k") else emptyList())
                "gif","webp" -> {
                    require(request.fps in 5..20 && request.resolution in setOf(240,360,480))
                    require(request.end-request.start<=30 && (request.end-request.start)*request.fps*request.resolution*request.resolution<=140_000_000)
                    args.addAll(listOf("-an","-vf","fps=${request.fps},scale=${request.resolution}:${request.resolution}:force_original_aspect_ratio=decrease:force_divisible_by=2:flags=bilinear",
                        "-c:v",if(request.mode=="gif") "gif" else "libwebp_anim","-loop",request.loop.toString()))
                    if(request.mode=="webp") args.addAll(listOf("-quality",if(request.preset=="fast") "50" else "75"))
                }
                "srt","vtt" -> { validateSubtitle(input);args.addAll(listOf("-map","0:s:0","-c:s",if(request.mode=="srt") "srt" else "webvtt")) }
                "attach" -> args.addAll(listOf("-map","0:v:0?","-map","0:a?","-map","1:0","-c","copy","-c:s","mov_text"))
            }
            if(extension=="mp4" || extension=="m4a") args.addAll(listOf("-movflags","+faststart"))
            args.addAll(listOf("-progress","pipe:1",output.absolutePath))
            val expected=if(clipped) request.end-request.start else duration
            command("libffmpeg.so",args,30*60*1000L) { line ->
                line.removePrefix("out_time_us=").takeIf { line.startsWith("out_time_us=") }?.toLongOrNull()?.let {
                    if(expected>0) progress((it/10000.0/expected).toInt().coerceIn(0,99))
                }
            }
            require(output.isFile && output.length()>0) { "empty_output" }
            val checked=JSONObject(command("libffprobe.so",inputPolicy+listOf("-v","error","-show_streams","-of","json",output.absolutePath),30000))
            require((checked.optJSONArray("streams")?.length() ?: 0)>0) { "invalid_output" }
            coroutineContext.ensureActive()
            touchedOutput=true
            context.contentResolver.openOutputStream(request.destination,"wt")?.use { out ->
                output.inputStream().use { from -> copy(from,out) }
            } ?: error("output_permission")
            context.contentResolver.openFileDescriptor(request.destination,"r")?.use {
                require(it.statSize<0 || it.statSize==output.length()) { "output_size" }
            } ?: error("output_verification")
            progress(100)
        } catch(e: Exception) {
            if(touchedOutput) runCatching { context.contentResolver.openOutputStream(request.destination,"wt")?.close() }
            throw e
        } finally {
            val root=File(context.cacheDir,"media-tools").canonicalFile
            if(folder.canonicalFile.parentFile==root) folder.deleteRecursively()
        }
    }
    /** Only generated files in one private download workspace; preserve separate clips. */
    suspend fun mergeClips(directory: File,mode: MediaMode): File=withContext(Dispatchers.IO) {
        val root=directory.canonicalFile
        val files=directory.listFiles().orEmpty().filter { it.isFile && it.extension.lowercase() in JobArtwork.MEDIA_EXTENSIONS }
            .sortedBy { it.name }
        require(files.size in 2..20)
        require(files.all { it.canonicalFile.parentFile==root && it.name.none(Char::isISOControl) })
        fun signatures(json: JSONObject): List<String> {
            val streams=json.getJSONArray("streams")
            return (0 until streams.length()).map { streams.getJSONObject(it) }
                .filter { it.optString("codec_type") in setOf("video","audio") && it.optJSONObject("disposition")?.optInt("attached_pic")!=1 }
                .map { stream -> listOf("codec_type","codec_name","profile","width","height","pix_fmt","time_base","sample_rate","channels","channel_layout").joinToString("|") { stream.optString(it) } }
        }
        var expected: List<String>?=null
        files.forEach { file ->
            val probe=JSONObject(command("libffprobe.so",inputPolicy+listOf("-v","error","-show_streams","-of","json",file.absolutePath),30000))
            val signature=signatures(probe);require(signature.isNotEmpty())
            if(expected==null) expected=signature else require(signature==expected) { "incompatible_clips" }
        }
        val list=File(directory,".merge-clips.txt")
        val output=File(directory,"Zenith-merged."+files.first().extension)
        require(!output.exists())
        try {
            list.writeText(files.joinToString("\n") { file -> "file '"+file.name.replace("'","'\\''")+"'" })
            command("libffmpeg.so",listOf("-hide_banner","-loglevel","error","-nostdin","-n","-protocol_whitelist","file,pipe","-f","concat","-safe","0","-i",list.absolutePath,
                *(if(mode==MediaMode.AUDIO) listOf("-map","0:a:0") else listOf("-map","0:v:0","-map","0:a?")).toTypedArray(),"-c","copy",output.absolutePath),30*60*1000L)
            require(output.length()>0)
            val checked=JSONObject(command("libffprobe.so",inputPolicy+listOf("-v","error","-show_streams","-of","json",output.absolutePath),30000))
            require(signatures(checked)==expected) { "merge_verification" }
            output
        } catch(e: Exception) { output.delete();throw e } finally { list.delete() }
    }
    private fun validateSubtitle(file: File) {
        val decoded=Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(file.readBytes())).toString()
        require(decoded.length<=4*1024*1024 && !decoded.contains('\u0000') && "-->" in decoded) { "subtitle_utf8" }
    }
    private suspend fun copyFrom(uri: Uri,file: File,limit: Long) {
        context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { out -> copy(input,out,limit) } } ?: error("input_permission")
    }
    private suspend fun copy(input: java.io.InputStream,out: java.io.OutputStream,limit: Long=Long.MAX_VALUE) {
        val buffer=ByteArray(65536);var bytes=0L
        while(true) { coroutineContext.ensureActive();val n=input.read(buffer);if(n<0) break
            bytes+=n;require(bytes<=limit && File(context.cacheDir.absolutePath).usableSpace>16L*1024*1024) { "storage_limit" }
            out.write(buffer,0,n)
        }
    }
    private suspend fun command(binary: String,args: List<String>,timeout: Long,onLine: (String)->Unit={}): String=runInterruptible(Dispatchers.IO) {
        val builder=ProcessBuilder(listOf(File(native,binary).absolutePath)+args).redirectErrorStream(true)
        val packages=File(context.noBackupFilesDir,"youtubedl-android/packages")
        builder.environment()["LD_LIBRARY_PATH"]=listOf("python","ffmpeg","aria2c").joinToString(":") { File(packages,"$it/usr/lib").absolutePath }
        builder.environment()["TMPDIR"]=context.cacheDir.absolutePath
        val process=builder.start();val content=StringBuffer();val failure=AtomicReference<Throwable?>()
        val reader=Thread {
            try { process.inputStream.bufferedReader().use { input ->
                val pending=StringBuilder();val buffer=CharArray(4096)
                while(true) {
                    val count=input.read(buffer);if(count<0) break
                    for(i in 0 until count) {
                        val char=buffer[i]
                        if(char=='\n') {
                            require(content.length+pending.length<2*1024*1024) { "native_output_limit" }
                            val line=pending.toString();pending.setLength(0);content.append(line).append('\n');onLine(line)
                        } else { require(pending.length<65536) { "native_line_limit" };pending.append(char) }
                    }
                }
                if(pending.isNotEmpty()) { content.append(pending);onLine(pending.toString()) }
            } } catch(e: Throwable) { failure.set(e);process.destroy() }
        }.apply { isDaemon=true;name="ZenithMediaTool";start() }
        try {
            val end=android.os.SystemClock.elapsedRealtime()+timeout
            var exit: Int?=null
            while(exit==null) {
                if(Thread.currentThread().isInterrupted) throw InterruptedException()
                check(android.os.SystemClock.elapsedRealtime()<end) { "media_tool_timeout" }
                exit=try { process.exitValue() } catch(_: IllegalThreadStateException) { Thread.sleep(50);null }
            }
            reader.join(3000);check(!reader.isAlive)
            failure.get()?.let { throw IllegalStateException("native_output",it) }
            check(exit==0) {
                "conversion_failed: "+content.toString().replace(Regex("(?i)(?:https?|content|file)://[^\\s]+"),"[link]")
                    .replace(context.cacheDir.absolutePath,"[temporary]")
                    .replace(Regex("(?i)(authorization|cookie|password|token)(\\s*[:=]\\s*)[^\\r\\n]+"),"$1=[hidden]")
                    .takeLast(800)
            }
            content.toString()
        } finally { process.destroy();process.inputStream.close();reader.interrupt() }
    }
}
