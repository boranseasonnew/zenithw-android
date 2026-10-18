package space.zenithw.app

import android.app.Application
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ScreenState(val tab: Int=0,val url: String="",val analyzing: Boolean=false,
    val preview: MediaPreview?=null,val message: String?=null,val profiles: List<CookieProfile> = emptyList(),
    val cookieId: String?=null,val autoUpdate: Boolean=true,val channel: String="stable",val language: String="tr",val defaults: DownloadOptions=DownloadOptions(),val showHistory: Boolean=false,
    val playlistEntries: List<PlaylistEntry> = emptyList(),val playlistLoading: Boolean=false,
    val bulkLinks: List<BulkLinkInput> = listOf(BulkLinkInput()),val bulkMode: MediaMode=MediaMode.VIDEO,val submitting: Boolean=false,
    val analysisError: String?=null,val inspectSlow: Boolean=false,
    val themeMode: String="system",val dynamicColors: Boolean=false,val configureBeforeDownload: Boolean=true,
    val rememberDownloadChoices: Boolean=true,val showMediaPreview: Boolean=true,val keepHistory: Boolean=true,
    val updateIntervalHours: Int=24,val outputDirectory: String="",val separateMediaFolders: Boolean=false)
class ZenithViewModel(application: Application): AndroidViewModel(application) {
    val app=application as ZenithApplication
    private val mutableScreen=MutableStateFlow(ScreenState(profiles=app.vault.profiles(),
        autoUpdate=app.store.autoUpdate,channel=app.store.channel,cookieId=app.store.selectedCookieId,language=app.store.language,defaults=app.store.defaultOptions,
        bulkMode=if(app.store.defaultOptions.mode==MediaMode.AUDIO) MediaMode.AUDIO else MediaMode.VIDEO,
        themeMode=app.store.themeMode,dynamicColors=app.store.dynamicColors,configureBeforeDownload=app.store.configureBeforeDownload,
        rememberDownloadChoices=app.store.rememberDownloadChoices,showMediaPreview=app.store.showMediaPreview,
        keepHistory=app.store.keepHistory,updateIntervalHours=app.store.updateIntervalHours,
        outputDirectory=app.store.outputDirectory,separateMediaFolders=app.store.separateMediaFolders))
    val screen=mutableScreen.asStateFlow()
    val jobs=app.store.jobs
    val logs=app.store.logs
    val engine=app.engine.status
    private var inspection: Job?=null
    private val enqueueGate=Mutex()
    private var generation=0L
    private var playlistOptions: DownloadOptions?=null
    private var inspectionOptions: DownloadOptions?=null
    private fun cancelInspection() { generation++;inspection?.cancel() }
    fun library(history: Boolean=false) {
        cancelInspection()
        edit { it.copy(tab=1,showHistory=history,message=null,analyzing=false,playlistLoading=false,preview=null,playlistEntries=emptyList()) }
    }
    private fun edit(update: (ScreenState)->ScreenState) { mutableScreen.value=update(mutableScreen.value) }
    fun tab(tab: Int) {
        cancelInspection()
        edit { it.copy(tab=tab,message=null,analyzing=false,playlistLoading=false,preview=null,playlistEntries=emptyList()) }
    }
    fun url(url: String) {
        if(url!=mutableScreen.value.url) cancelInspection()
        edit { it.copy(url=url,message=null,analyzing=false,preview=null,playlistEntries=emptyList(),playlistLoading=false) }
    }
    fun showMessage(value: String)=edit { it.copy(message=value) }
    fun importCookies(uri: Uri) {
        viewModelScope.launch {
            try {
                val profile=withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes=input.readNBytesCompat(1024*1024+1)
                        require(bytes.size<=1024*1024) { "Cookie dosyası çok büyük." }
                        app.vault.importNetscape(bytes.toString(Charsets.UTF_8))
                    } ?: error("Dosya açılamadı.")
                }
                refreshProfiles()
                cookie(profile.id)
                showMessage("Cookie dosyası şifreli olarak kaydedildi.")
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { showMessage("Cookie dosyası okunamadı. Netscape biçiminde bir dosya seç.") }
        }
    }
    fun dismissMessage()=edit { it.copy(message=null) }
    fun dismissPreview() { cancelInspection();edit { it.copy(preview=null,analyzing=false,analysisError=null) } }
    fun cancelAnalysis() { cancelInspection();edit { it.copy(analyzing=false) } }
    fun cookie(id: String?) {
        cancelInspection()
        app.store.selectedCookieId=id
        edit { it.copy(cookieId=id,analyzing=false,preview=null,playlistEntries=emptyList(),playlistLoading=false) }
    }
    fun refreshProfiles() {
        val profiles=app.vault.profiles()
        edit { it.copy(profiles=profiles,cookieId=it.cookieId?.takeIf { id -> profiles.any { profile -> profile.id==id } }) }
        app.store.selectedCookieId=mutableScreen.value.cookieId
    }
    fun removeProfile(id: String) { app.vault.remove(id); refreshProfiles() }
    fun shareIntent(text: String) {
        url(runCatching { normalizeUrl(text) }.getOrDefault(text))
        tab(0)
    }
    private fun text(id: Int,vararg args: Any)=AppLanguage.context(getApplication()).getString(id,*args)
    fun analyze() {
        val state=mutableScreen.value
        val url=try { normalizeUrl(state.url) } catch(e: Exception) {
            showMessage(e.message ?: text(R.string.check_link));return
        }
        cancelInspection()
        inspectionOptions=state.defaults
        val uri=Uri.parse(url)
        val listLink=uri.path.orEmpty().trimEnd('/').endsWith("/playlist")
        edit { it.copy(url=url,preview=MediaPreview(url,url,isPlaylist=listLink || uri.getQueryParameter("list")!=null,
            metadataReady=false),analyzing=false,analysisError=null,message=null,playlistLoading=false,playlistEntries=emptyList()) }
        app.store.log("INSPECT configuration opened before metadata")
        if(!state.configureBeforeDownload) { enqueue(state.defaults);return }
        if(state.showMediaPreview && !listLink) refreshInfo(false)
    }
    fun refreshInfo(slow: Boolean=false) {
        val state=mutableScreen.value
        val url=state.preview?.url ?: return
        cancelInspection();val request=generation
        edit { it.copy(analyzing=true,inspectSlow=slow,analysisError=null) }
        inspection=viewModelScope.launch {
            try {
                val preview=app.engine.inspect(url,state.cookieId,inspectionOptions ?: state.defaults,slow)
                coroutineContext.ensureActive()
                if(request==generation && mutableScreen.value.url==url && mutableScreen.value.cookieId==state.cookieId)
                    edit { it.copy(preview=preview) }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { if(request==generation) {
                app.store.log("INSPECT failed: ${EngineUpdateSource.diagnostic(e)}")
                edit { it.copy(analysisError=text(R.string.media_error)) }
            } }
            finally { if(request==generation) edit { it.copy(analyzing=false) } }
        }
    }
    fun enqueue(options: DownloadOptions) {
        val state=mutableScreen.value;val preview=state.preview ?: return
        if(state.submitting) return
        if(!validOptions(options,preview.duration)) return
        if(state.rememberDownloadChoices) defaults(options)
        if(options.playlist) { loadPlaylist(options);return }
        cancelInspection();edit { it.copy(analyzing=false) }
        enqueueBatch(listOf(DownloadJob(url=preview.url,title=if(preview.metadataReady) preview.title else Uri.parse(preview.url).host.orEmpty(),thumbnail=preview.thumbnail,
            options=options.copy(cookieId=state.cookieId))))
    }
    fun bulkMode(mode: MediaMode)=edit { it.copy(bulkMode=mode) }
    fun addBulkLink()=edit { if(it.bulkLinks.size>=50) it else it.copy(bulkLinks=it.bulkLinks+BulkLinkInput()) }
    fun removeBulkLink(id: String)=edit { state ->
        val rows=state.bulkLinks.filterNot { it.id==id }
        state.copy(bulkLinks=rows.ifEmpty { listOf(BulkLinkInput()) })
    }
    fun bulkLink(id: String,value: String)=edit { state ->
        runCatching { replaceBulkLink(state.bulkLinks,id,value) }
            .fold({ state.copy(bulkLinks=it) },{ state.copy(message=text(R.string.bulk_limit)) })
    }
    fun enqueueBulk(): Boolean {
        val state=mutableScreen.value
        if(state.submitting) return false
        return enqueueBulk(state.bulkLinks.joinToString("\n") { it.url },state.defaults.copy(mode=state.bulkMode))
    }
    fun enqueueBulk(raw: String,options: DownloadOptions): Boolean {
        if(mutableScreen.value.submitting) return false
        if(!validOptions(options)) return false
        val lines=raw.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        if(lines.size !in 1..50) { showMessage(text(R.string.bulk_limit));return false }
        val urls=runCatching { lines.map(::normalizeUrl).distinct() }.getOrElse { showMessage(text(R.string.bulk_limit));return false }
        val selected=options.copy(cookieId=mutableScreen.value.cookieId,playlist=false,playlistItems="",selectedFormat="")
        enqueueBatch(urls.map { DownloadJob(url=it,title=Uri.parse(it).host ?: it,options=selected) },clearBulk=true,stayTerminal=selected.customArguments.isNotBlank())
        return true
    }
    fun enqueueCommand(raw: String,arguments: String): Boolean {
        if(arguments.isBlank()) { showMessage(text(R.string.command_required));return false }
        val options=mutableScreen.value.defaults.copy(mode=MediaMode.VIDEO,customArguments=arguments,selectedFormat="",filenameOverride="",clipStart="",clipEnd="")
        return enqueueBulk(raw,options)
    }
    private fun validOptions(options: DownloadOptions,duration: Int=0): Boolean = try {
        InspectionPolicy.section(options.clipStart,options.clipEnd,duration)
        if(options.filenameOverride.isNotBlank()) InspectionPolicy.literalFilename(options.filenameOverride)
        if(options.customArguments.isNotBlank()) parseCustomArguments(options.customArguments)
        require(options.maxFilesizeMb in 0..102400)
        true
    } catch(e: IllegalArgumentException) { showMessage(text(R.string.options_invalid));false }
    fun loadPlaylist(options: DownloadOptions=mutableScreen.value.defaults) {
        val state=mutableScreen.value
        if(state.submitting || state.playlistLoading) return
        val url=runCatching { normalizeUrl(state.preview?.url ?: state.url) }.getOrElse { showMessage(text(R.string.check_link));return }
        cancelInspection();val request=generation;playlistOptions=options.copy(cookieId=state.cookieId)
        edit { it.copy(playlistLoading=true,analyzing=false,preview=null,playlistEntries=emptyList(),message=null) }
        inspection=viewModelScope.launch {
            try {
                val entries=app.engine.playlist(url,state.cookieId,options)
                coroutineContext.ensureActive()
                if(request==generation && mutableScreen.value.cookieId==state.cookieId) {
                    if(entries.isEmpty()) showMessage(text(R.string.playlist_empty))
                    else edit { it.copy(playlistEntries=entries) }
                }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { if(request==generation) showMessage(text(R.string.playlist_error)) }
            finally { if(request==generation) edit { it.copy(playlistLoading=false) } }
        }
    }
    fun dismissPlaylist() { cancelInspection();edit { it.copy(playlistEntries=emptyList(),playlistLoading=false) } }
    fun enqueuePlaylist(indices: Set<Int>) {
        val options=(playlistOptions ?: mutableScreen.value.defaults).copy(playlist=false,playlistItems="",selectedFormat="")
        val entries=mutableScreen.value.playlistEntries.filter { it.index in indices }
        if(entries.isEmpty()) return
        enqueueBatch(entries.map { DownloadJob(url=it.url,title=it.title,options=options) })
    }
    private fun enqueueBatch(candidates: List<DownloadJob>,clearBulk: Boolean=false,stayTerminal: Boolean=false) {
        if(mutableScreen.value.submitting) return
        val expectedGeneration=generation
        val expectedUrl=mutableScreen.value.url
        val expectedTab=mutableScreen.value.tab
        val submittedLinks=mutableScreen.value.bulkLinks
        val active=setOf(JobState.QUEUED,JobState.RUNNING,JobState.SAVING)
        edit { it.copy(submitting=true) }
        fun key(job: DownloadJob)=listOf(job.url,job.options.mode.name,job.options.height,job.options.container,
            job.options.audioFormat,job.options.selectedFormat,job.options.cookieId,job.options.playlist,
            job.options.customArguments,job.options.clipStart,job.options.clipEnd,job.options.filenameOverride).joinToString("|")
        viewModelScope.launch {
            try {
                val count=withContext(Dispatchers.IO) { enqueueGate.withLock {
                    val current=app.store.jobs.value.filter { it.state in active }
                    val seen=current.map(::key).toMutableSet()
                    val selected=candidates.filter { seen.add(key(it)) }
                    require(current.size+selected.size<=200) { "capacity" }
                    selected.forEachIndexed { index,job -> DownloadWorker.enqueue(app,job.copy(createdAt=System.currentTimeMillis()+index)) }
                    selected.size
                } }
                if(count==0) {
                    val sameInteraction=expectedGeneration==generation && mutableScreen.value.url==expectedUrl && mutableScreen.value.tab==expectedTab
                    if(sameInteraction) edit { it.copy(preview=null,tab=if(stayTerminal) 4 else 1,showHistory=false,playlistEntries=emptyList(),message=text(R.string.bulk_duplicates)) }
                    else showMessage(text(R.string.bulk_duplicates))
                }
                else {
                    val sameInteraction=expectedGeneration==generation && mutableScreen.value.url==expectedUrl && mutableScreen.value.tab==expectedTab
                    if(sameInteraction) edit { it.copy(preview=null,url="",tab=if(stayTerminal) 4 else 1,showHistory=false,playlistEntries=emptyList(),
                        bulkLinks=if(clearBulk && it.bulkLinks==submittedLinks) listOf(BulkLinkInput()) else it.bulkLinks,message=text(R.string.bulk_added,count)) }
                    else showMessage(text(R.string.bulk_added,count))
                }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { showMessage(text(if(e.message=="capacity") R.string.queue_capacity else R.string.bulk_error)) }
            finally { edit { it.copy(submitting=false) } }
        }
    }
    fun cancel(id: String)=DownloadWorker.cancel(app,id)
    fun retry(job: DownloadJob) {
        val replacement=job.copy(id=UUID.randomUUID().toString(),state=JobState.QUEUED,progress=0f,
            detail="Sırada",phase="queued",progressKnown=false,etaSeconds=-1,bytesDownloaded=0,totalBytes=0,createdAt=System.currentTimeMillis(),files=emptyList(),notes=emptyList())
        enqueueBatch(listOf(replacement),stayTerminal=job.options.customArguments.isNotBlank())
    }
    fun automatic(value: Boolean) { app.store.autoUpdate=value; edit { it.copy(autoUpdate=value) } }
    fun language(value: String) { app.store.language=value; edit { it.copy(language=app.store.language) } }
    fun defaults(value: DownloadOptions) { app.store.defaultOptions=value; edit { it.copy(defaults=app.store.defaultOptions) } }
    fun downloadChoices(value: DownloadOptions) { inspectionOptions=value;if(app.store.rememberDownloadChoices) defaults(value) }
    fun preference(key: String,value: Boolean) {
        when(key) {
            "dynamicColors" -> app.store.dynamicColors=value
            "configureBeforeDownload" -> app.store.configureBeforeDownload=value
            "rememberDownloadChoices" -> app.store.rememberDownloadChoices=value
            "showMediaPreview" -> app.store.showMediaPreview=value
            "keepHistory" -> app.store.keepHistory=value
            "separateMediaFolders" -> app.store.separateMediaFolders=value
            else -> return
        }
        edit { it.copy(dynamicColors=app.store.dynamicColors,configureBeforeDownload=app.store.configureBeforeDownload,
            rememberDownloadChoices=app.store.rememberDownloadChoices,showMediaPreview=app.store.showMediaPreview,
            keepHistory=app.store.keepHistory,separateMediaFolders=app.store.separateMediaFolders) }
    }
    fun theme(mode: String) { app.store.themeMode=mode;edit { it.copy(themeMode=app.store.themeMode) } }
    fun updateInterval(hours: Int) { app.store.updateIntervalHours=hours;edit { it.copy(updateIntervalHours=app.store.updateIntervalHours) } }
    fun outputDirectory(value: String) { app.store.outputDirectory=value;edit { it.copy(outputDirectory=app.store.outputDirectory) } }
    fun clearLogs()=app.store.clearLogs()
    fun channel(value: String) { app.store.channel=value; edit { it.copy(channel=value) }; updateEngine() }
    fun updateEngine() { app.applicationScope.launch { app.engine.initializeAndUpdate(force=true) } }
}


private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val output=java.io.ByteArrayOutputStream()
    val buffer=ByteArray(8192)
    while(output.size()<limit) {
        val count=read(buffer,0,minOf(buffer.size,limit-output.size()))
        if(count<0) break
        output.write(buffer,0,count)
    }
    return output.toByteArray()
}
