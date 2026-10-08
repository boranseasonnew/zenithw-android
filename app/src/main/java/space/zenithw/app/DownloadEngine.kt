package space.zenithw.app

import android.content.Context
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class EngineStatus(val ready: Boolean=false, val updating: Boolean=false,
    val label: String="Motor hazırlanıyor", val version: String="", val warning: String?=null)

class DownloadEngine(private val context: Context, private val store: LocalStore, private val vault: CookieVault, private val updateHttp: EngineHttp=EngineHttp()) {
    private val gate=Mutex()
    private val updateGate=Mutex()
    private var initialized=false
    private val mutableStatus=MutableStateFlow(EngineStatus())
    val status=mutableStatus.asStateFlow()
    private fun init() {
        if(initialized) return
        YoutubeDL.getInstance().init(context)
        FFmpeg.getInstance().init(context)
        installBundledFloor()
        initialized=true
    }
    private fun binaryVersion(file: File): String? = runCatching {
        java.util.zip.ZipFile(file).use { zip ->
            zip.getEntry("yt_dlp/version.py")?.let { entry ->
                zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    .let { Regex("__version__\\s*=\\s*['\"]([0-9]{4}\\.[0-9]{2}\\.[0-9]{2}(?:\\.[0-9]+)?)['\"]").find(it)?.groupValues?.get(1) }
            }
        }
    }.getOrNull()
    private fun installBundledFloor() {
        val directory=File(context.noBackupFilesDir,"youtubedl-android/yt-dlp").apply { mkdirs() }
        val destination=File(directory,"yt-dlp")
        var version=binaryVersion(destination)
        if(version==null || version < BundledEngine.VERSION) {
            val staged=File(directory,"yt-dlp.bundle")
            try {
                context.resources.openRawResource(R.raw.ytdlp).use { input ->
                    java.io.FileOutputStream(staged).use { output -> input.copyTo(output); output.fd.sync() }
                }
                val digest=MessageDigest.getInstance("SHA-256")
                staged.inputStream().use { input ->
                    val buffer=ByteArray(32768)
                    while(true) {
                        val count=input.read(buffer); if(count<0) break
                        digest.update(buffer,0,count)
                    }
                }
                require(digest.digest().joinToString("") { "%02x".format(it) } == BundledEngine.SHA256) { "BUNDLE_CHECKSUM: bundled engine is damaged" }
                require(staged.renameTo(destination)) { "BUNDLE_INSTALL: cannot replace engine file" }
                version=BundledEngine.VERSION
                store.lastEngineVersion="stable · $version"
            } finally { staged.delete() }
        }
        if(store.lastEngineVersion=="Birlikte gelen sürüm" || store.lastEngineVersion.isBlank())
            store.lastEngineVersion="stable · $version"
    }
    suspend fun initializeAndUpdate(force: Boolean=false)=withContext(Dispatchers.IO) {
        updateGate.withLock {
            try {
                gate.withLock { init() }
                mutableStatus.value=EngineStatus(true,store.autoUpdate || force,
                    if(store.autoUpdate || force) "Güncellemeler kontrol ediliyor" else "İndirmeye hazır",store.lastEngineVersion)
                if(store.autoUpdate || force) { store.log("ENGINE  ${store.channel} kontrol ediliyor");updateVerifiedBinary() }
                store.log("ENGINE  Hazır · ${store.lastEngineVersion}")
                mutableStatus.value=EngineStatus(true,false,"İndirmeye hazır",store.lastEngineVersion)
            } catch(cancelled: CancellationException) {
                throw cancelled
            } catch(e: Exception) {
                store.log("ENGINE  ${store.channel} güncellemesi başarısız: ${EngineUpdateSource.diagnostic(e)}")
                android.util.Log.w("ZenithEngine", "Engine update failed for ${store.channel}",e)
                mutableStatus.value=EngineStatus(initialized,false,
                    if(initialized) "İndirmeye hazır" else "Motor başlatılamadı",store.lastEngineVersion,
                    if(initialized) "${if(store.channel=="nightly") "Nightly" else "Kararlı sürüm"} güncellenemedi. Tekrar dene." else "Motoru hazırlamak için tekrar dene.")
            }
        }
    }
    private suspend fun updateVerifiedBinary() {
        val channel=store.channel
        val tag=EngineUpdateSource.latestTag(channel,updateHttp)
        val version="$channel · $tag"
        val installed=File(context.noBackupFilesDir,"youtubedl-android/yt-dlp/yt-dlp")
        if(store.lastEngineVersion==version && binaryVersion(installed)==tag) {
            store.lastEngineCheck=System.currentTimeMillis(); return
        }
        val sums=String(EngineUpdateSource.asset(channel,tag,"SHA2-256SUMS",1024*1024,updateHttp),Charsets.UTF_8)
        val checksum=sums.lineSequence().map { it.trim().split(Regex("\\s+"),limit=2) }
            .firstOrNull { it.size==2 && it[1].removePrefix("*")=="yt-dlp" }?.first()
            ?: error("UPDATE_CHECKSUM: missing executable checksum")
        require(checksum.matches(Regex("[a-fA-F0-9]{64}"))) { "UPDATE_CHECKSUM: invalid checksum format" }
        val binary=EngineUpdateSource.asset(channel,tag,"yt-dlp",32*1024*1024,updateHttp)
        val actual=MessageDigest.getInstance("SHA-256").digest(binary).joinToString("") { "%02x".format(it) }
        require(actual.equals(checksum,ignoreCase=true)) { "UPDATE_CHECKSUM: downloaded file does not match SHA-256" }
        // Fetch and verify outside the download lock. Startup update requests must
        // not prevent a download from starting on an already usable engine.
        gate.withLock {
            if(store.channel!=channel) return@withLock
            val directory=File(context.noBackupFilesDir,"youtubedl-android/yt-dlp").apply { mkdirs() }
            val staged=File(directory,"yt-dlp.download")
            val destination=File(directory,"yt-dlp")
            try {
                java.io.FileOutputStream(staged).use { it.write(binary); it.fd.sync() }
                require(binaryVersion(staged)==tag && tag >= BundledEngine.VERSION) { "UPDATE_VERSION: downloaded engine version differs from release" }
                require(staged.renameTo(destination)) { "UPDATE_INSTALL: cannot replace engine file" }
                store.lastEngineVersion=version
                store.lastEngineCheck=System.currentTimeMillis()
            } finally { if(staged.exists()) staged.delete() }
        }
    }
    suspend fun runtimeVersion(): String=withContext(Dispatchers.IO) {
        gate.withLock {
            init()
            val request=YoutubeDLRequest(listOf<String>()).apply { addOption("--version") }
            runInterruptible { YoutubeDL.getInstance().execute(request).out.trim() }
        }
    }
    private fun baseRequest(url: String)=YoutubeDLRequest(normalizeUrl(url)).apply {
        addOption("--ignore-config")
        addOption("--socket-timeout","20")
        addOption("--remote-components","ejs:github")
    }
    suspend fun inspect(url: String, cookieId: String?): MediaPreview=withContext(Dispatchers.IO) {
        gate.withLock {
            init()
            val directory=File(context.cacheDir,"inspect-${java.util.UUID.randomUUID()}").apply { mkdirs() }
            try {
                val request=baseRequest(url).apply {
                    addOption("--dump-single-json"); addOption("--no-playlist")
                    vault.temporaryFile(cookieId,directory)?.let { addOption("--cookies",it.absolutePath) }
                }
                val response=runInterruptible { YoutubeDL.getInstance().execute(request) }
                val json=JSONObject(response.out)
                val formats=json.optJSONArray("formats")
                val heights=if(formats==null) emptyList() else (0 until formats.length())
                    .map { formats.getJSONObject(it).optInt("height") }.filter { it>0 }.distinct().sortedDescending()
                MediaPreview(normalizeUrl(url),json.optString("title","Medya"),
                    json.optString("uploader"),json.optString("thumbnail").takeIf { it.startsWith("https://") },
                    json.optInt("duration"),heights)
            } finally { directory.deleteRecursively() }
        }
    }
    suspend fun download(job: DownloadJob, directory: File, started: ()->Unit,
                         progress: (Float,Long,String)->Unit): DownloadOutcome = gate.withLock {
        init()
        started()
        val notes=mutableListOf<String>()
        val plan=DownloadPolicy.thumbnailPlan(job.options.mode.name,job.options.container,
            job.options.audioFormat,job.options.embedThumbnail,job.options.downloadThumbnail,job.options.thumbnailFormat)
        plan.note?.let { notes.add(it) }
        val cookie=vault.temporaryFile(job.options.cookieId,directory)
        try {
            var useAria=job.options.useAria2 && job.options.mode!=MediaMode.SUBTITLES
            if(useAria) {
                try {
                    Aria2c.getInstance().init(context)
                    require(File(context.applicationInfo.nativeLibraryDir,"libaria2c.so").canExecute())
                    require(ariaCertificate().isFile)
                } catch(cancelled: CancellationException) { throw cancelled }
                catch(e: Exception) {
                    useAria=false
                    notes.add("Aria2c hazırlanamadı; normal indirme motoru kullanıldı.")
                }
            }
            suspend fun execute(aria: Boolean) = runInterruptible(Dispatchers.IO) {
                YoutubeDL.getInstance().execute(request(job,directory,cookie,aria),
                    processId=job.id,redirectErrorStream=false,callback=progress)
            }
            val response=try { execute(useAria) }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) {
                coroutineContext.ensureActive()
                if(!useAria || !DownloadPolicy.isAriaDownloadFailure(e.message.orEmpty())) throw e
                YoutubeDL.getInstance().destroyProcessById(job.id)
                notes.add("Aria2c indirmesi tamamlanamadı; normal motorla yeniden denendi.")
                progress(-1f,-1,"ZenithWNativeFallback")
                execute(false)
            }
            // EmbedThumbnail can finish without embedding when the source has no usable image.
            DownloadPolicy.thumbnailWarning(response.out+"\n"+response.err)?.let { notes.add(it) }
            DownloadOutcome(notes.distinct())
        } finally { cookie?.delete(); YoutubeDL.getInstance().destroyProcessById(job.id) }
    }
    private fun ariaCertificate()=File(context.noBackupFilesDir,"youtubedl-android/packages/python/usr/etc/tls/cert.pem")
    fun cancel(id: String) { YoutubeDL.getInstance().destroyProcessById(id) }
    private fun request(job: DownloadJob,directory: File,cookie: File?,useAria: Boolean): YoutubeDLRequest {
        val o=job.options
        require(!o.filenameTemplate.contains('/') && !o.filenameTemplate.contains('\\') && !o.filenameTemplate.contains(".."))
        val r=baseRequest(job.url)
        r.addOption("-o",File(directory,o.filenameTemplate.ifBlank { "%(title).150B [%(id)s].%(ext)s" }).absolutePath)
        r.addOption("--newline"); r.addOption("--no-mtime")
        r.addOption("--retries",o.retries.toString()); r.addOption("--fragment-retries",o.fragmentRetries.toString())
        r.addOption("--concurrent-fragments",o.fragments.toString())
        r.addOption(if(o.playlist) "--yes-playlist" else "--no-playlist")
        if(o.playlist && o.playlistItems.isNotBlank()) {
            require(o.playlistItems.matches(Regex("[0-9,: -]+"))); r.addOption("--playlist-items",o.playlistItems)
        }
        r.addOption("--socket-timeout",o.timeout.toString())
        r.addOption(if(o.skipUnavailable) "--skip-unavailable-fragments" else "--abort-on-unavailable-fragments")
        if(o.playlist) {
            when(o.playlistOrder) { "reverse" -> r.addOption("--playlist-reverse"); "random" -> r.addOption("--playlist-random") }
            if(o.playlistErrors=="continue") r.addOption("--ignore-errors")
        }
        if(o.writeInfoJson) r.addOption("--write-info-json")
        if(o.writeDescription) r.addOption("--write-description")
        if(o.downloadArchive) r.addOption("--download-archive",File(context.noBackupFilesDir,"download-archive.txt").absolutePath)
        if(o.keepOriginal) r.addOption("--keep-video")
        if(o.sleepSeconds>0) r.addOption("--sleep-interval",o.sleepSeconds.toString())
        cookie?.let { r.addOption("--cookies",it.absolutePath) }
        when(o.networkMode) { "ipv4" -> r.addOption("--force-ipv4"); "ipv6" -> r.addOption("--force-ipv6") }
        if(o.proxy.isNotBlank()) {
            require(o.proxy.matches(Regex("^(https?|socks5h?)://[^\\s]+$"))); r.addOption("--proxy",o.proxy)
        }
        if(o.userAgent.isNotBlank()) { require(!o.userAgent.any { it.isISOControl() }); r.addOption("--user-agent",o.userAgent) }
        if(useAria) {
            // The protocol-qualified absolute path also avoids the library's three
            // repeated aria2c args: yt-dlp replaces earlier values for the same downloader.
            val binary=File(context.applicationInfo.nativeLibraryDir,"libaria2c.so")
            r.addOption("--downloader","http,ftp:${binary.absolutePath}")
            r.addOption("--downloader","dash,m3u8:native")
            r.addOption("--downloader-args",DownloadPolicy.ariaArguments(
                o.ariaConnections,o.retries,o.speedLimitKbps,ariaCertificate().absolutePath,o.timeout))
        } else if(o.speedLimitKbps>0) r.addOption("--limit-rate","${o.speedLimitKbps}K")
        if(o.sponsorBlock && o.mode!=MediaMode.AUDIO) {
            require(o.sponsorCategories.matches(Regex("[a-z_,]+"))); r.addOption(if(o.sponsorAction=="mark") "--sponsorblock-mark" else "--sponsorblock-remove",o.sponsorCategories)
        }
        when(o.mode) {
            MediaMode.AUDIO -> {
                r.addOption("-f","bestaudio/best"); r.addOption("-x")
                require(o.audioFormat in listOf("m4a","mp3","opus","wav","flac"))
                r.addOption("--audio-format",o.audioFormat)
                r.addOption("--audio-quality",if(o.audioBitrate>0) "${o.audioBitrate}K" else "0")
            }
            MediaMode.VIDEO -> {
                val height=if(o.height>0) "[height<=${o.height}]" else ""
                val codec=when(o.codec) { "h264"->"[vcodec^=avc]";"av1"->"[vcodec^=av01]";"vp9"->"[vcodec^=vp9]"; else->"" }
                r.addOption("-f",o.selectedFormat.ifBlank { "bv*$height$codec+ba/b$height" })
                require(o.container in listOf("mp4","mkv","webm"))
                r.addOption("--merge-output-format",if(o.container=="webm") "mkv" else o.container)
                // Merge format alone is ignored for a single combined video/audio stream.
                r.addOption(if(o.container=="webm") "--recode-video" else "--remux-video",o.container)
            }
            MediaMode.SUBTITLES -> r.addOption("--skip-download")
        }
        if(o.subtitles || o.mode==MediaMode.SUBTITLES) r.addOption("--write-subs")
        if(o.autoSubtitles) r.addOption("--write-auto-subs")
        if(o.subtitles || o.autoSubtitles || o.mode==MediaMode.SUBTITLES) {
            r.addOption("--sub-langs",o.subtitleLanguages.ifBlank { "all,-live_chat" })
            r.addOption("--sub-format","best")
            require(o.subtitleFormat in listOf("srt","vtt","ass","lrc"))
            r.addOption("--convert-subs",o.subtitleFormat)
            if(o.embedSubtitles && o.mode==MediaMode.VIDEO) r.addOption("--embed-subs")
        }
        if(o.embedMetadata) r.addOption("--embed-metadata")
        if(o.embedChapters) r.addOption("--embed-chapters")
        if(o.embedUploader) r.addOption("--parse-metadata","uploader:%(meta_artist)s")
        val thumbnail=DownloadPolicy.thumbnailPlan(o.mode.name,o.container,o.audioFormat,
            o.embedThumbnail,o.downloadThumbnail,o.thumbnailFormat)
        if(thumbnail.embed) r.addOption("--embed-thumbnail")
        if(thumbnail.write) r.addOption("--write-thumbnail")
        thumbnail.convert?.let { r.addOption("--convert-thumbnails",it) }
        return r
    }
}
