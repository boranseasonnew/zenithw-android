package space.zenithw.app

import android.content.Context
import android.system.Os
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext

/** Uses the FFmpeg/FFprobe executables shipped by youtubedl-android. */
class SquareCover(private val context: Context) {
    private val native=File(context.applicationInfo.nativeLibraryDir)
    private val ffmpeg=File(native,"libffmpeg.so")
    private val ffprobe=File(native,"libffprobe.so")
    private val supported=setOf("mp3","m4a","mp4","flac","mkv","mov")
    private val media=supported+setOf("opus","ogg","webm","wav")
    private val base=listOf("-hide_banner","-loglevel","error","-nostdin","-y")
    private fun cover(stream: JSONObject): Boolean = stream.optJSONObject("disposition")?.optInt("attached_pic")==1 ||
        (stream.optString("codec_type")=="attachment" && stream.optJSONObject("tags")?.let {
            it.optString("mimetype").startsWith("image/") &&
                it.optString("filename").matches(Regex("(?i)(?:small_)?cover(?:_land)?\\.(?:jpe?g|png|webp)"))
        }==true)
    private fun streams(probe: JSONObject): List<JSONObject> {
        val array=probe.optJSONArray("streams") ?: error("Missing stream information")
        require(array.length()<=256)
        return (0 until array.length()).map { array.getJSONObject(it) }
    }
    private fun signature(stream: JSONObject)=listOf("codec_type","codec_name","width","height","sample_rate","channels")
        .joinToString("|") { stream.optString(it) }
    private suspend fun probe(file: File)=JSONObject(command(ffprobe,listOf("-v","error","-show_streams","-show_chapters","-show_format","-of","json",file.absolutePath),15000))

    suspend fun applyTo(directory: File,jobId: String): List<String> {
        val notes=mutableListOf<String>()
        val root=directory.canonicalFile
        if(!ffmpeg.canExecute() || !ffprobe.canExecute()) return listOf("Kare kapak doğrulama aracı bulunamadı; asıl medya dosyası korundu.")
        val files=root.listFiles().orEmpty().filter { it.isFile && it.extension.lowercase() in media && !it.name.startsWith('.') }
        for(file in files) {
            coroutineContext.ensureActive()
            if(file.extension.lowercase() !in supported) {
                notes.add("Bu biçimde kare kapak gömme güvenilir biçimde desteklenmiyor; medya dosyası korundu.");continue
            }
            var temporary: File?=null
            try {
                require(file.canonicalFile.parentFile==root)
                val length=file.length();val modified=file.lastModified()
                val before=probe(file);val original=streams(before)
                val covers=original.filter(::cover);val retained=original.filterNot(::cover)
                val firstMedia=retained.firstOrNull { it.optString("codec_type") in setOf("audio","video") } ?: error("Missing media")
                val embedded=covers.firstOrNull()
                if(covers.size==1 && embedded?.optInt("width")==3000 && embedded.optInt("height")==3000) continue
                var image=if(embedded==null) listOf("jpg","jpeg","png","webp").map { File(root,file.nameWithoutExtension+"."+it) }.firstOrNull { it.isFile } else null
                if(embedded==null && image==null) { notes.add("Kaynakta kullanılabilir kapak bulunamadı; medya dosyası kaydedildi.");continue }
                temporary=File(root,".square-${UUID.randomUUID()}").apply { require(mkdir()) }
                val jpg=File(temporary,"cover.jpg")
                if(embedded?.optString("codec_type")=="attachment") {
                    image=File(temporary,"source-image.bin")
                    command(ffmpeg,base+listOf("-dump_attachment:${embedded.getInt("index")}",image.absolutePath,"-i",file.absolutePath,
                        "-map","0:${firstMedia.getInt("index")}","-t","0","-c","copy","-f","null","-"))
                }
                val input=if(embedded!=null && embedded.optString("codec_type")!="attachment")
                    listOf("-i",file.absolutePath,"-map","0:${embedded.getInt("index")}") else listOf("-i",requireNotNull(image).absolutePath,"-map","0:v:0")
                command(ffmpeg,base+input+listOf("-frames:v","1","-vf","crop=min(iw\\,ih):min(iw\\,ih),scale=3000:3000:flags=lanczos,setsar=1",
                    "-c:v","mjpeg","-q:v","2","-pix_fmt","yuvj420p","-update","1",jpg.absolutePath))
                require(streams(probe(jpg)).any { it.optInt("width")==3000 && it.optInt("height")==3000 })
                val output=File(temporary,"finished.${file.extension}")
                val mkv=file.extension.equals("mkv",true)
                val args=(base+listOf("-i",file.absolutePath)).toMutableList()
                if(!mkv) args.addAll(listOf("-i",jpg.absolutePath))
                retained.forEach { args.addAll(listOf("-map","0:${it.getInt("index")}")) }
                if(!mkv) args.addAll(listOf("-map","1:v:0"))
                args.addAll(listOf("-map_metadata","0","-map_chapters","0","-copy_unknown","-c","copy"))
                retained.forEachIndexed { index,stream -> args.addAll(listOf("-map_metadata:s:$index","0:s:${stream.getInt("index")}")) }
                if(mkv) args.addAll(listOf("-attach",jpg.absolutePath,"-metadata:s:${retained.size}","filename=cover.jpg",
                    "-metadata:s:${retained.size}","mimetype=image/jpeg","-metadata:s:${retained.size}","title=Album cover"))
                else {
                    val index=retained.count { it.optString("codec_type")=="video" }
                    args.addAll(listOf("-disposition:v:$index","attached_pic","-metadata:s:v:$index","title=Album cover","-metadata:s:v:$index","comment=Cover (front)"))
                    if(file.extension.equals("mp3",true)) args.addAll(listOf("-id3v2_version","3"))
                }
                args.add(output.absolutePath);command(ffmpeg,args,180000)
                val after=probe(output);val result=streams(after);val normal=result.filterNot(::cover)
                require(normal.map(::signature).sorted()==retained.map(::signature).sorted()) { "A media stream changed" }
                require((after.optJSONArray("chapters")?.length() ?: 0)==(before.optJSONArray("chapters")?.length() ?: 0)) { "Chapters changed" }
                var validated=result.any { cover(it) && it.optInt("width")==3000 && it.optInt("height")==3000 }
                if(!validated && mkv) {
                    val attachment=result.firstOrNull { cover(it) && it.optString("codec_type")=="attachment" }
                    if(attachment!=null) {
                        val extracted=File(temporary,"verified.jpg")
                        command(ffmpeg,base+listOf("-dump_attachment:${attachment.getInt("index")}",extracted.absolutePath,"-i",output.absolutePath,
                            "-map","0:${normal.first { it.optString("codec_type") in setOf("audio","video") }.getInt("index")}","-t","0","-c","copy","-f","null","-"))
                        validated=streams(probe(extracted)).any { it.optInt("width")==3000 && it.optInt("height")==3000 }
                    }
                }
                require(validated) { "Embedded cover was not verified" }
                val oldDuration=before.optJSONObject("format")?.optDouble("duration",Double.NaN) ?: Double.NaN
                val newDuration=after.optJSONObject("format")?.optDouble("duration",Double.NaN) ?: Double.NaN
                require(!oldDuration.isFinite() || !newDuration.isFinite() || kotlin.math.abs(oldDuration-newDuration)<=maxOf(1.0,oldDuration*0.005)) { "Duration changed" }
                coroutineContext.ensureActive()
                require(file.length()==length && file.lastModified()==modified) { "Source changed" }
                // POSIX rename is atomic on this same private filesystem. Never delete the original first.
                Os.rename(output.absolutePath,file.absolutePath)
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { notes.add("Kare kapak doğrulanamadı; asıl medya dosyası korundu.") }
            finally {
                temporary?.canonicalFile?.takeIf { it.parentFile==root && it.name.startsWith(".square-") }?.deleteRecursively()
            }
        }
        return notes.distinct()
    }

    private suspend fun command(binary: File,args: List<String>,timeout: Long=30000): String=runInterruptible(Dispatchers.IO) {
        val builder=ProcessBuilder(listOf(binary.absolutePath)+args).redirectErrorStream(true)
        val packages=File(context.noBackupFilesDir,"youtubedl-android/packages")
        builder.environment()["LD_LIBRARY_PATH"]=listOf("python","ffmpeg","aria2c").joinToString(":") { File(packages,"$it/usr/lib").absolutePath }
        builder.environment()["TMPDIR"]=context.cacheDir.absolutePath
        val process=builder.start();val content=StringBuffer();val failure=AtomicReference<Throwable?>()
        val reader=Thread {
            try { process.inputStream.reader(Charsets.UTF_8).use { input ->
                val buffer=CharArray(4096)
                while(true) { val count=input.read(buffer);if(count<0) break
                    if(content.length+count>1024*1024) { failure.set(IllegalStateException("Media information exceeds safety limit"));process.destroy();break }
                    content.append(buffer,0,count)
                }
            } } catch(e: Exception) { failure.compareAndSet(null,e) }
        }.apply { name="ZenithCoverOutput";isDaemon=true;start() }
        try {
            val deadline=android.os.SystemClock.elapsedRealtime()+timeout
            var exit: Int?=null
            while(exit==null) {
                if(Thread.currentThread().isInterrupted) throw InterruptedException()
                if(android.os.SystemClock.elapsedRealtime()>deadline) error("Cover processing timed out")
                exit=try { process.exitValue() } catch(_: IllegalThreadStateException) { Thread.sleep(25);null }
            }
            reader.join(3000);require(!reader.isAlive) { "Media information did not finish" }
            failure.get()?.let { throw IllegalStateException("Could not read media information",it) }
            require(exit==0) { "FFmpeg could not safely prepare cover art" }
            content.toString()
        } finally { process.destroy();process.inputStream.close();reader.interrupt() }
    }
}
