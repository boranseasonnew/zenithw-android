package space.zenithw.app

import android.content.Context
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

data class EngineStatus(val ready: Boolean=false, val updating: Boolean=false,
    val label: String="Motor hazırlanıyor", val version: String="", val warning: String?=null)

class DownloadEngine(private val context: Context, private val store: LocalStore, private val vault: CookieVault, private val updateHttp: EngineHttp=EngineHttp()) {
    private val gate=Mutex()
    private val updateGate=Mutex()
    private val inspectionGate=Mutex()
    private val metadataRuntime=MetadataRuntime(context)
    private val basicPreview=BasicPreviewFetcher()
    private var activeProcesses=0
    private var idle=CompletableDeferred<Unit>().apply { complete(Unit) }
    private data class PreviewRequest(
        val url: String,
        val cookieId: String?,
        val options: MetadataSnapshotPolicy.ExtractionOptions,
    )
    private data class PreviewCacheKey(
        val request: PreviewRequest,
        val sessionRevision: Long,
        val sessionUserAgent: String,
        val engineVersion: String,
    )
    private data class CachedPreview(
        val created: Long,
        val preview: MediaPreview,
        val json: String?,
        val linkExpirySeconds: Long,
    )
    private data class CachedCollection(val created: Long,val entries: List<PlaylistEntry>)
    private val previews=linkedMapOf<PreviewCacheKey,CachedPreview>()
    private val collections=linkedMapOf<PreviewCacheKey,CachedCollection>()
    private val activeInspections = ConcurrentHashMap<PreviewRequest, CompletableDeferred<Unit>>()
    private var initialized=false
    private val mutableStatus=MutableStateFlow(EngineStatus())
    val status=mutableStatus.asStateFlow()
    private suspend fun <T> withEngine(block: suspend ()->T): T=withContext(Dispatchers.IO) {
        gate.withLock {
            init()
            if(activeProcesses++==0) idle=CompletableDeferred()
        }
        try { block() }
        finally { withContext(NonCancellable) { gate.withLock { if(--activeProcesses==0) idle.complete(Unit) } } }
    }
    private suspend fun installWhenIdle(block: ()->Unit) {
        while(true) {
            val wait=gate.withLock {
                if(activeProcesses==0) { block(); null } else idle
            }
            if(wait==null) return
            wait.await()
        }
    }
    private fun previewRequest(url: String, cookieId: String?, options: DownloadOptions) =
        PreviewRequest(normalizeUrl(url), cookieId, MetadataSnapshotPolicy.extractionOptions(options))

    private fun cacheKey(request: PreviewRequest): PreviewCacheKey {
        val session = vault.identity(request.cookieId)
        return PreviewCacheKey(request, session?.revision ?: 0L, session?.userAgent.orEmpty(), store.lastEngineVersion)
    }

    private fun cached(key: PreviewCacheKey): CachedPreview? = synchronized(previews) {
        previews[key]?.takeIf {
            android.os.SystemClock.elapsedRealtime() - it.created < MetadataSnapshotPolicy.PREVIEW_TTL_MS
        }
    }

    private fun reusableSnapshot(request: PreviewRequest): String? = cached(cacheKey(request))?.let { entry ->
        val age = android.os.SystemClock.elapsedRealtime() - entry.created
        entry.json?.takeIf {
            MetadataSnapshotPolicy.fresh(age, entry.linkExpirySeconds, System.currentTimeMillis() / 1000)
        }
    }

    private suspend fun cachedDownloadSnapshot(job: DownloadJob): String? {
        if (job.options.playlist || job.options.customArguments.isNotBlank()) return null
        val request = previewRequest(job.url, job.options.cookieId, job.options)
        val trace = AnalysisTrace(store, jobId = job.id)
        var snapshot = reusableSnapshot(request)
        if (snapshot == null) {
            activeInspections[request]?.let { pending ->
                trace.mark("metadata_wait")
                withTimeoutOrNull(InspectionPolicy.totalTimeoutMillis(slow = true)) { pending.await() }
                snapshot = reusableSnapshot(request)
            }
        }
        trace.mark(if (snapshot == null) "cache_miss" else "cache_hit")
        return snapshot
    }

    private fun forgetDownloadSnapshot(job: DownloadJob) {
        val request = previewRequest(job.url, job.options.cookieId, job.options)
        synchronized(previews) {
            // Keep the harmless display data; only the rejected transfer URLs are stale.
            previews.entries.filter { it.key.request == request }.forEach { entry ->
                entry.setValue(entry.value.copy(json = null))
            }
        }
    }

    internal fun invalidateSessionSnapshots(cookieId: String) {
        synchronized(previews) {
            previews.keys.removeAll { it.request.cookieId == cookieId }
            collections.keys.removeAll { it.request.cookieId == cookieId }
        }
    }
    private fun init() {
        if(initialized) return
        YoutubeDL.getInstance().init(context)
        installBundledFloor()
        BrowserTransport.prepare(context)
        initialized=true
    }
    private fun binaryVersion(file: File): String? = runCatching { EngineArchive.version(file) }.getOrNull()
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
                val shouldUpdate = !BuildConfig.F_DROID && InspectionPolicy.shouldCheckUpdate(force,store.autoUpdate,
                    System.currentTimeMillis(),store.lastEngineCheck,store.updateIntervalHours)
                mutableStatus.value=EngineStatus(true,shouldUpdate,
                    if(shouldUpdate) "Güncellemeler kontrol ediliyor" else "İndirmeye hazır",store.lastEngineVersion)
                if(shouldUpdate) { store.log("ENGINE  ${store.channel} kontrol ediliyor");updateVerifiedBinary() }
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
        installWhenIdle {
            if(store.channel!=channel) return@installWhenIdle
            val directory=File(context.noBackupFilesDir,"youtubedl-android/yt-dlp").apply { mkdirs() }
            val staged=File(directory,"yt-dlp.download")
            val destination=File(directory,"yt-dlp")
            try {
                java.io.FileOutputStream(staged).use { it.write(binary); it.fd.sync() }
                val downloadedVersion=EngineArchive.version(staged)
                require(downloadedVersion==tag) { "UPDATE_VERSION: expected=$tag actual=$downloadedVersion" }
                require(tag >= BundledEngine.VERSION) { "UPDATE_VERSION: $tag is older than bundled ${BundledEngine.VERSION}" }
                metadataRuntime.close()
                synchronized(previews) { previews.clear();collections.clear() }
                require(staged.renameTo(destination)) { "UPDATE_INSTALL: cannot replace engine file" }
                store.lastEngineVersion=version
                store.lastEngineCheck=System.currentTimeMillis()
            } finally { if(staged.exists()) staged.delete() }
        }
    }
    suspend fun runtimeVersion(): String=withContext(Dispatchers.IO) {
        withEngine {
            val request=YoutubeDLRequest(listOf<String>()).apply { addOption("--version") }
            runInterruptible { YoutubeDL.getInstance().execute(request).out.trim() }
        }
    }
    private fun baseRequest(url: String?)=YoutubeDLRequest(url?.let { listOf(normalizeUrl(it)) } ?: emptyList<String>()).apply {
        addOption("--ignore-config")
        addOption("--socket-timeout","20")
        addOption("--cache-dir",File(context.cacheDir,"yt-dlp-cache").apply { mkdirs() }.absolutePath)
        if(!BuildConfig.F_DROID) addOption("--remote-components","ejs:github")
        url?.let { BrowserTransport.apply(context,this,it) }
    }
    private fun applyNetwork(request: YoutubeDLRequest,o: DownloadOptions,cookieId: String?=o.cookieId) {
        when(o.networkMode) { "ipv4" -> request.addOption("--force-ipv4"); "ipv6" -> request.addOption("--force-ipv6") }
        if(o.proxy.isNotBlank()) {
            require(o.proxy.matches(Regex("^(https?|socks5h?)://[^\\s]+$")));request.addOption("--proxy",o.proxy)
        }
        val userAgent=o.userAgent.ifBlank { vault.userAgent(cookieId) }
        if(userAgent.isNotBlank()) { require(!userAgent.any { it.isISOControl() });request.addOption("--user-agent",userAgent) }
    }
    private fun metadataRequest(cookieId: String?,options: DownloadOptions,directory: File,slow: Boolean,collection: Boolean,limit: Int)=baseRequest(null).apply {
        addOption("--skip-download");addOption("--playlist-end",limit.toString())
        if(collection) { addOption("--flat-playlist");addOption("--yes-playlist") } else addOption("--no-playlist")
        addOption("--socket-timeout",InspectionPolicy.socketTimeout(slow).toString())
        addOption("--extractor-retries",InspectionPolicy.extractorRetries(slow).toString());addOption("--retries","1")
        if(!options.autoSubtitles) addOption("--extractor-args","youtube:skip=translated_subs")
        addOption("--js-runtimes","quickjs:${File(context.applicationInfo.nativeLibraryDir,"libqjs.so").absolutePath}")
        addOption("--ffmpeg-location",File(context.applicationInfo.nativeLibraryDir,"libffmpeg.so").absolutePath)
        applyNetwork(this,options,cookieId)
        vault.temporaryFile(cookieId,directory)?.let { addOption("--cookies",it.absolutePath) }
    }
    suspend fun prepareMetadataRuntime() {
        try { withEngine { withTimeout(10000) {
            val trace=AnalysisTrace(store)
            metadataRuntime.execute(null,onEvent={ event ->
                if(event.optString("event")=="runtime_ready") metadataRuntimeReady(event,trace)
            })
        } } } catch(cancelled: CancellationException) { throw cancelled }
        catch(_: Exception) { metadataRuntime.close() /* Retried on demand, no download impact. */ }
    }
    private fun metadataRuntimeReady(event: JSONObject,trace: AnalysisTrace) {
        trace.sample("python_import",event.optLong("import_ms"))
        store.log("ENGINE metadata Python=${event.optString("python_version","unknown")} TLS=${event.optString("openssl_version","unknown")}")
    }
    suspend fun inspect(url: String,cookieId: String?,options: DownloadOptions=store.defaultOptions,slow: Boolean=false,
        onUpdate: (AnalysisState,MediaPreview?)->Unit={ _,_ -> },trace: AnalysisTrace?=null): MediaPreview {
        val request = previewRequest(url, cookieId, options)
        val pending = CompletableDeferred<Unit>()
        val registered = activeInspections.putIfAbsent(request, pending) == null
        return try {
            withContext(Dispatchers.IO) { inspectPreview(request, options, slow, onUpdate, trace) }
        } finally {
            if (registered) {
                pending.complete(Unit)
                activeInspections.remove(request, pending)
            }
        }
    }

    private suspend fun inspectPreview(request: PreviewRequest, options: DownloadOptions, slow: Boolean,
        onUpdate: (AnalysisState, MediaPreview?) -> Unit, trace: AnalysisTrace?): MediaPreview {
        val normalized = request.url
        val cookieId = request.cookieId
        val timing=trace ?: AnalysisTrace(store)
        timing.mark("metadata_start")
        // Serialize access and recheck the cache after waiting: duplicate callers
        // cannot start a second extractor for a completed/in-flight request.
        return inspectionGate.withLock {
            cached(cacheKey(request))?.let {
                timing.mark("cache_hit");timing.preview(it.preview);timing.mark("metadata_ready");timing.mark("metadata_total");onUpdate(AnalysisState.Ready,it.preview)
                return@withLock it.preview
            }
            timing.mark("cache_miss")
            coroutineScope {
                val basicReady=AtomicBoolean(false)
                val hint=launch {
                    withTimeoutOrNull(2500) { basicPreview.fetch(normalized,cookieId,options) }?.let {
                        basicReady.set(true)
                        timing.preview(it);onUpdate(AnalysisState.BasicInfoReady,it)
                        onUpdate(AnalysisState.FetchingFormats,null)
                    }
                }
                try { withEngine {
                    timing.mark("engine_ready");onUpdate(if(basicReady.get()) AnalysisState.FetchingFormats else AnalysisState.FetchingMetadata,null)
                    val identity=cacheKey(request)
                    val directory=File(context.cacheDir,"inspect-${java.util.UUID.randomUUID()}").apply { mkdirs() }
                    try {
                        val listLink=PreviewPolicy.isPlaylistOnly(normalized)
                        val command=metadataRequest(cookieId,options,directory,slow,listLink,1)
                        store.log("INSPECT session=${if(cookieId==null) "none" else "selected"}")
                        val snapshot=File(directory,"source-info.json")
                        val result=try { withTimeout(InspectionPolicy.totalTimeoutMillis(slow)) {
                            metadataRuntime.execute(command,normalized,snapshot) { event ->
                                when(event.optString("event")) {
                                    "runtime_process" -> { timing.sample("python_spawn",event.optLong("spawn_ms"));if(event.optBoolean("reused")) timing.sample("python_import",0) }
                                    "runtime_ready" -> metadataRuntimeReady(event,timing)
                                    "extracting" -> {
                                        timing.sample("ydl_object",event.optLong("construct_ms"));timing.mark("extractor_started")
                                        store.log("INSPECT handlers=${event.optJSONArray("handlers") ?: "[]"}")
                                    }
                                    "network" -> timing.sample("http_headers",event.optLong("headers_ms"))
                                    "js_challenge" -> timing.mark("js_challenge")
                                    "error" -> timing.sample("extraction_error",event.optLong("elapsed_ms"))
                                }
                            }
                        } } catch(timeout: TimeoutCancellationException) { throw java.io.IOException("Metadata request timed out",timeout) }
                        coroutineContext.ensureActive()
                        hint.cancel()
                        val json=result.getJSONObject("info")
                        val preview=mediaPreview(json,normalized,listLink || json.has("entries") || PreviewPolicy.isCollection(normalized))
                        timing.sample("extraction",result.optLong("extract_ms"))
                        timing.sample("serialization",result.optLong("serialize_ms"))
                        timing.preview(preview);timing.mark("metadata_ready");timing.mark("metadata_total")
                        store.log("INSPECT requests=${result.optInt("network_count")} headers_ms=${result.optLong("network_headers_ms")} metadata_worker=true")
                        synchronized(previews) {
                            while(previews.size>=8) previews.remove(previews.keys.first())
                            if(identity==cacheKey(request)) {
                                val serialized=snapshot.takeIf { !json.has("entries") && it.isFile && it.length()<=4*1024*1024 }?.readText()
                                while(previews.isNotEmpty() && previews.values.sumOf { it.json?.length ?: 0 }+(serialized?.length ?: 0)>8*1024*1024)
                                    previews.remove(previews.keys.first())
                                previews[identity]=CachedPreview(android.os.SystemClock.elapsedRealtime(),preview,serialized,result.optLong("link_expiry_seconds"))
                            }
                        }
                        onUpdate(AnalysisState.Ready,preview)
                        preview
                    } finally { directory.deleteRecursively() }
                } } finally { hint.cancel() }
            }
        }
    }
    suspend fun playlist(url: String,cookieId: String?,options: DownloadOptions=store.defaultOptions): List<PlaylistEntry> = withContext(Dispatchers.IO) {
        inspectionGate.withLock {
            val selection = previewRequest(url, cookieId, options)
            val key=cacheKey(selection)
            synchronized(previews) { collections[key]?.takeIf { android.os.SystemClock.elapsedRealtime()-it.created<90000 } }?.let {
                return@withContext it.entries
            }
            withEngine {
                val identity=cacheKey(selection)
                val directory=File(context.cacheDir,"playlist-${java.util.UUID.randomUUID()}").apply { mkdirs() }
                try {
                    val request=metadataRequest(cookieId,options,directory,true,true,200)
                    val response=try { withTimeout(90000) {
                        metadataRuntime.execute(request,normalizeUrl(url),File(directory,"source-info.json"))
                    } } catch(timeout: TimeoutCancellationException) { throw java.io.IOException("Playlist request timed out after 90 seconds",timeout) }
                    coroutineContext.ensureActive()
                    val entries=playlistEntries(response.getJSONObject("info"),url)
                    synchronized(previews) {
                        if(identity==cacheKey(selection)) {
                            while(collections.size>=8) collections.remove(collections.keys.first())
                            collections[identity]=CachedCollection(android.os.SystemClock.elapsedRealtime(),entries)
                        }
                    }
                    entries
                } finally { directory.deleteRecursively() }
            }
        }
    }
    suspend fun download(job: DownloadJob, directory: File, started: ()->Unit,
                         progress: (Float,Long,String)->Unit): DownloadOutcome = withEngine {
        started()
        progress(0f,-1,"ZenithWPreparing")
        withContext(Dispatchers.IO) { FFmpeg.getInstance().init(context) }
        val notes=mutableListOf<String>()
        val plan=DownloadPolicy.thumbnailPlan(job.options.mode.name,job.options.container,
            job.options.audioFormat,job.options.embedThumbnail,job.options.downloadThumbnail,job.options.thumbnailFormat)
        if(job.options.customArguments.isBlank()) plan.note?.let { notes.add(it) }
        val options = job.options
        val session = vault.identity(options.cookieId)
        val cookie = vault.temporaryFile(options.cookieId, directory)
        val managed = options.customArguments.isBlank()
        val optionalSubtitles = managed && options.mode != MediaMode.SUBTITLES && (options.subtitles || options.autoSubtitles)
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
            var metadata = if (managed && !options.playlist) cachedDownloadSnapshot(job) else null
            val fallbackMarker = File(directory, ".subtitle-fallback")
            val priorSubtitleStatus = fallbackMarker.takeIf { optionalSubtitles && it.isFile && it.length() in 1L..16L }
                ?.readText()?.toIntOrNull()
            var withSubtitles = priorSubtitleStatus == null
            priorSubtitleStatus?.let { status ->
                notes.add(0, DownloadPolicy.subtitleWarning(RecoveryFailure(RecoveryKind.SUBTITLE, status.takeIf { it in 100..599 })))
            }
            suspend fun execute(aria: Boolean): com.yausername.youtubedl_android.YoutubeDLResponse {
                if (vault.identity(options.cookieId)?.revision != session?.revision) throw CookieSessionException()
                val tracker = DownloadProgress()
                val attemptStarted = android.os.SystemClock.elapsedRealtime()
                var subtitleStarted: Long? = null
                var subtitleProcessing = false
                var transferReady = false
                var mediaStarted = false
                var jsChallenge = false
                fun stage(name: String, detail: String = "") {
                    val elapsed = android.os.SystemClock.elapsedRealtime() - attemptStarted
                    store.log("$name elapsed_ms=$elapsed$detail", job.id)
                }
                val snapshot = metadata?.let { File(directory, ".source-info.json") }
                val captured = if (snapshot == null && optionalSubtitles && withSubtitles && !options.playlist)
                    File(directory, ".source-capture.json") else null
                try {
                    captured?.delete()
                    if (snapshot != null) {
                        // yt-dlp otherwise re-extracts automatically on every DownloadError.
                        // Restore the page URL on each per-format copy in the video stage.
                        val info = JSONObject(requireNotNull(metadata))
                        val page = info.optString("webpage_url")
                        if (page.isNotBlank()) info.put("zenith_page", page)
                        info.remove("webpage_url")
                        snapshot.writeText(info.toString())
                    }
                    stage("metadata_start", if (snapshot == null) " source=extract" else " source=cache")
                    return runInterruptible(Dispatchers.IO) {
                        YoutubeDL.getInstance().execute(request(job, directory, cookie, aria, snapshot, withSubtitles, captured),
                            processId = job.id, redirectErrorStream = false, callback = { _, _, line ->
                                if (!jsChallenge && (line.contains("[jsc:") || line.contains("[jsc]"))) {
                                    jsChallenge = true
                                    stage("js_challenge")
                                }
                                if (line == "ZenithWMetadataReady") stage("metadata_ready")
                                if (line.startsWith("[info] Writing video subtitles to:")) {
                                    if (subtitleStarted == null) subtitleStarted = android.os.SystemClock.elapsedRealtime()
                                    stage("subtitle_fetch", " state=start")
                                }
                                if (line.startsWith("ZENITHW_PROCESS\t")) {
                                    runCatching {
                                        val postprocessor = JSONObject(line.substringAfter('\t'))
                                        subtitleProcessing = postprocessor.optString("status") != "finished" &&
                                            postprocessor.optString("postprocessor") in setOf("EmbedSubtitle", "SubtitlesConvertor")
                                    }
                                }
                                if (line.startsWith("ZenithWMetadata:")) {
                                    transferReady = true
                                    subtitleStarted?.let { startedAt ->
                                        stage("subtitle_fetch", " state=ready duration_ms=${android.os.SystemClock.elapsedRealtime() - startedAt}")
                                    }
                                    subtitleStarted = null
                                    runCatching {
                                        val data = JSONObject(line.removePrefix("ZenithWMetadata:"))
                                        store.change(job.id) { downloadMetadata(it, data) }
                                    }
                                } else {
                                    if (!mediaStarted && transferReady &&
                                        (line.startsWith("[download] Destination:") || line.startsWith("ZENITHW_PROGRESS\t"))) {
                                        mediaStarted = true
                                        stage("media_transfer_start")
                                    }
                                    if (line != "ZenithWMetadataReady") store.log(line, job.id)
                                }
                                tracker.accept(line)?.let { progress(it.percent, it.eta, it.line) }
                            })
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    coroutineContext.ensureActive()
                    val message = error.message.orEmpty()
                    val failure = RecoveryPolicy.failure(message)
                    // A downloader may report only a raw HTTP or network error
                    // while the callback has already identified the subtitle stage.
                    val subtitleTransportError = failure.httpStatus == 401 || failure.kind in setOf(RecoveryKind.FORBIDDEN, RecoveryKind.RATE_LIMIT, RecoveryKind.NETWORK, RecoveryKind.UNKNOWN)
                    val subtitleFailure = subtitleStarted != null && subtitleTransportError ||
                        subtitleProcessing && failure.kind in setOf(RecoveryKind.CONVERSION, RecoveryKind.UNKNOWN)
                    val classified = if (subtitleFailure)
                        failure.copy(kind = RecoveryKind.SUBTITLE) else failure
                    subtitleStarted?.let { startedAt ->
                        stage("subtitle_fetch", " state=failed duration_ms=${android.os.SystemClock.elapsedRealtime() - startedAt} code=${classified.code}")
                    }
                    if (classified.kind == RecoveryKind.SUBTITLE && captured != null) {
                        // Cold requests capture the resolved source before writing
                        // subtitles, so fallback can keep the same extraction.
                        metadata = runCatching {
                            if (captured.length() !in 1L..(4L * 1024 * 1024)) return@runCatching null
                            val source = JSONObject(captured.readText())
                            val hasMedia = source.optString("url").startsWith("http") || (source.optJSONArray("formats")?.length() ?: 0) > 0
                            source.takeIf { hasMedia && it.optString("_type", "video") == "video" && !it.has("entries") }
                                ?.toString()
                        }.getOrNull()
                        if (metadata != null) stage("cache_hit", " source=current_attempt")
                    }
                    throw DownloadFailureException(classified, message, error)
                } finally {
                    snapshot?.delete()
                    captured?.delete()
                }
            }
            var refreshed=false
            var response: com.yausername.youtubedl_android.YoutubeDLResponse
            while(true) {
                try { response=execute(useAria);break }
                catch(cancelled: CancellationException) { throw cancelled }
                catch(e: Exception) {
                    coroutineContext.ensureActive()
                    cancel(job.id)
                    val failure = (e as? DownloadFailureException)?.failure ?: RecoveryPolicy.failure(e.message.orEmpty())
                    if (optionalSubtitles && withSubtitles && failure.kind == RecoveryKind.SUBTITLE) {
                        withSubtitles = false
                        fallbackMarker.writeText((failure.httpStatus ?: 0).toString())
                        notes.add(0, DownloadPolicy.subtitleWarning(failure))
                        store.log("retry_reason kind=SUBTITLE code=${failure.code} strategy=media_only", job.id)
                        progress(-1f, -1, "ZenithWSubtitleFallback")
                        continue
                    }
                    if (metadata != null && !refreshed && failure.kind == RecoveryKind.FORBIDDEN && failure.httpStatus in setOf(403, 410)) {
                        metadata = null
                        forgetDownloadSnapshot(job)
                        refreshed = true
                        store.log("retry_reason kind=FORBIDDEN code=${failure.code} strategy=refresh_snapshot", job.id)
                        progress(0f,-1,"ZenithWRefreshing");continue
                    }
                    if (useAria && failure.kind in setOf(RecoveryKind.UNKNOWN, RecoveryKind.ENGINE, RecoveryKind.NETWORK) && DownloadPolicy.isAriaDownloadFailure(e.message.orEmpty())) {
                        useAria=false;notes.add("Aria2c indirmesi tamamlanamadı; normal motorla yeniden denendi.")
                        store.log("retry_reason kind=${failure.kind.name} code=${failure.code} strategy=native_downloader", job.id)
                        progress(-1f,-1,"ZenithWNativeFallback");continue
                    }
                    throw e
                }
            }
            val output = response.out + "\n" + response.err
            response.err.lineSequence().filter(String::isNotBlank).take(100).forEach { store.log(it,job.id) }
            val hasExpectedOutput = directory.listFiles().orEmpty().any {
                it.isFile && it.length() > 0 && DownloadPolicy.isExpectedOutput(it.name, options.mode.name, !managed)
            }
            if (DownloadPolicy.archiveSkipped(output) && !hasExpectedOutput)
                return@withEngine DownloadOutcome(notes.distinct(),archiveSkipped=true)
            if (!hasExpectedOutput && options.mode == MediaMode.SUBTITLES && managed) {
                throw DownloadFailureException(RecoveryFailure(RecoveryKind.SUBTITLE), "NO_SUBTITLE_OUTPUT")
            }
            check(hasExpectedOutput) { "No output files" }
            if (optionalSubtitles) DownloadPolicy.subtitleWarning(output)?.let { notes.add(it) }
            if(job.options.mergeClips && job.options.clipRanges.size>1 && job.options.customArguments.isBlank()) {
                try { LocalMediaTools(context).mergeClips(directory,job.options.mode) }
                catch(cancelled: CancellationException) { throw cancelled }
                catch(e: Exception) { notes.add(AppLanguage.context(context).getString(R.string.feature_merge_failed)) }
            }
            // EmbedThumbnail can finish without embedding when the source has no usable image.
            DownloadPolicy.thumbnailWarning(response.out+"\n"+response.err)?.let { notes.add(it) }
            if(job.options.customArguments.isBlank() && job.options.embedThumbnail && job.options.mode!=MediaMode.SUBTITLES) {
                progress(-1f,-1,"ZenithWSquareCover")
                notes.addAll(SquareCover(context).applyTo(directory,job.id))
            }
            DownloadOutcome(notes.distinct())
        } finally { cookie?.delete(); cancel(job.id) }
    }
    private fun ariaCertificate()=File(context.noBackupFilesDir,"youtubedl-android/packages/python/usr/etc/tls/cert.pem")
    fun cancel(id: String) {
        // The adapter can remove its process entry while cancellation is in flight.
        runCatching { YoutubeDL.getInstance().destroyProcessById(id) }
    }
    private fun request(job: DownloadJob, directory: File, cookie: File?, useAria: Boolean, snapshot: File?, withSubtitles: Boolean, capture: File?): YoutubeDLRequest {
        val o=job.options
        require(!o.filenameTemplate.contains('/') && !o.filenameTemplate.contains('\\') && !o.filenameTemplate.contains(".."))
        val r=baseRequest(if(snapshot==null) job.url else null)
        snapshot?.let {
            r.addOption("--load-info-json",it.absolutePath)
            r.addOption("--parse-metadata", "video:zenith_page:%(webpage_url)s")
            BrowserTransport.apply(context, r, job.url)
        }
        capture?.let { r.addCommands(listOf("--print-to-file", "pre_process:%()j", it.absolutePath.replace("%", "%%"))) }
        val nameBase=if(o.filenameOverride.isNotBlank()) InspectionPolicy.literalFilename(o.filenameOverride)
            else o.filenameTemplate.ifBlank { "%(title).150B [%(id)s].%(ext)s" }
        val name=if(o.clipRanges.isNotEmpty()) nameBase.replace(".%(ext)s"," - %(section_number)03d.%(ext)s") else nameBase
        r.addOption("-o",File(directory,name).absolutePath)
        if(o.restrictFilenames) r.addOption("--restrict-filenames")
        if(o.formatSort.isNotBlank()) { require(o.formatSort.matches(Regex("[a-zA-Z0-9_:+.,~/-]+")));r.addOption("--format-sort",o.formatSort) }
        if(o.maxFilesizeMb>0) r.addOption("--max-filesize","${o.maxFilesizeMb}M")
        if(o.clipRanges.isNotEmpty()) {
            require(o.clipRanges.size<=20 && o.mode!=MediaMode.SUBTITLES && !o.playlist)
            o.clipRanges.forEach { it.validate();r.addOption("--download-sections","*${it.start}-${it.end}") }
        } else InspectionPolicy.section(o.clipStart,o.clipEnd)?.let { r.addOption("--download-sections",it) }
        if(o.exactCuts) r.addOption("--force-keyframes-at-cuts")
        r.addOption("--newline"); r.addOption("--no-mtime")
        r.addOption("--progress");r.addOption("--progress-delta","0.2");r.addOption("--no-simulate")
        r.addOption("--output-na-placeholder","null")
        r.addOption("--progress-template",DownloadProgress.TEMPLATE)
        r.addOption("--progress-template",DownloadProgress.POST)
        r.addOption("--print",DownloadProgress.PLAN)
        if (o.customArguments.isBlank()) r.addOption("--print", "pre_process:ZenithWMetadataReady")
        r.addOption("--print","before_dl:ZenithWMetadata:%(.{title,thumbnail,thumbnails,duration})j")
        // --print otherwise implies quiet mode, whose progress screen is stderr.
        // The 0.18.1 wrapper forwards every stdout line to our callback; keep
        // stderr separate so its original HTTP errors remain available for retry.
        r.addOption("--no-quiet");r.addOption("--color","never")
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
        applyNetwork(r,o)
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
        // A command task supplies its own media pipeline. In particular, -x must
        // not inherit the normal video's --remux-video or default format selector.
        if(o.customArguments.isBlank()) {
        // Retain previously downloaded bytes and completed sidecars on retries.
        r.addOption("--continue")
        r.addOption("--no-overwrites")
        if (!o.playlist || o.playlistErrors != "continue") r.addOption("--abort-on-error")
        if (!o.autoSubtitles || !withSubtitles) r.addOption("--extractor-args", "youtube:skip=translated_subs")
        when(o.mode) {
            MediaMode.AUDIO -> {
                r.addOption("-f",o.selectedFormat.ifBlank { "bestaudio/best" }); r.addOption("-x")
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
        if(withSubtitles && (o.subtitles || o.mode==MediaMode.SUBTITLES)) r.addOption("--write-subs")
        if(withSubtitles && o.autoSubtitles) r.addOption("--write-auto-subs")
        if(withSubtitles && (o.subtitles || o.autoSubtitles || o.mode==MediaMode.SUBTITLES)) {
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
        }
        if(o.customArguments.isNotBlank()) r.addCommands(parseCustomArguments(o.customArguments))
        return r
    }
}
