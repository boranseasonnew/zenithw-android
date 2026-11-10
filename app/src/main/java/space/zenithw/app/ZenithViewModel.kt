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
import kotlinx.coroutines.flow.update
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
    val updateIntervalHours: Int=24,val outputDirectory: String="",val separateMediaFolders: Boolean=false,
    val fromShare: Boolean=false,val duplicate: DownloadJob?=null, val importPreview: IncomingLinks.Import?=null,
    val homeJobId: String?=null,val analysisState: AnalysisState=AnalysisState.Idle,val analysisId: String="")
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
    var toolSource: String?=null
    private var lastShare=""
    private var lastShareAt=0L
    private var duplicateCandidates: List<DownloadJob> = emptyList()
    private var inspection: Job?=null
    private val enqueueGate=Mutex()
    @Volatile private var generation=0L
    @Volatile private var analysisTrace: AnalysisTrace?=null
    private var playlistOptions: DownloadOptions?=null
    private var inspectionOptions: DownloadOptions?=null
    private var runningInspectionOptions: MetadataSnapshotPolicy.ExtractionOptions? = null
    private fun cancelInspection() {
        generation++
        if(inspection?.isActive==true) analysisTrace?.mark("cancelled")
        inspection?.cancel()
        runningInspectionOptions = null
    }
    fun library(history: Boolean=false) {
        cancelInspection()
        edit { it.copy(tab=1,showHistory=history,message=null,analyzing=false,playlistLoading=false,preview=null,playlistEntries=emptyList(),analysisState=AnalysisState.Idle) }
    }
    private fun edit(update: (ScreenState)->ScreenState) { mutableScreen.update(update) }
    fun tab(tab: Int) {
        cancelInspection()
        edit { it.copy(tab=tab,message=null,analyzing=false,playlistLoading=false,preview=null,playlistEntries=emptyList(),analysisState=AnalysisState.Idle) }
    }
    fun url(url: String) {
        if(url==mutableScreen.value.url) return
        if(url!=mutableScreen.value.url) cancelInspection()
        edit { it.copy(url=url,fromShare=false,homeJobId=null,message=null,analyzing=false,preview=null,playlistEntries=emptyList(),playlistLoading=false,analysisState=AnalysisState.Idle) }
    }
    /** A complete pasted URL opens immediately; ordinary character edits stay editable. */
    fun inputUrl(value: String) {
        val pasted=value.length-mutableScreen.value.url.length>4
        url(value)
        if(pasted && value.trim().startsWith("http") && runCatching { normalizeUrl(value) }.isSuccess) analyze()
    }
    fun analysisRendered(id: String,event: String) {
        analysisTrace?.takeIf { it.id==id && id==mutableScreen.value.analysisId }?.mark(event)
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
    fun dismissPreview()=dismissPreview(mutableScreen.value.analysisId)
    fun dismissPreview(id: String) {
        if(id!=mutableScreen.value.analysisId || mutableScreen.value.submitting) return
        cancelInspection();edit { it.copy(preview=null,analyzing=false,analysisError=null,analysisState=AnalysisState.Idle) }
    }
    fun cancelAnalysis() { cancelInspection();edit { it.copy(analyzing=false,analysisState=AnalysisState.Cancelled) } }
    fun cookie(id: String?) = selectCookie(id, forceRefresh = false)

    private fun selectCookie(id: String?, forceRefresh: Boolean) {
        val state=mutableScreen.value
        if (!checkSession(id) || !forceRefresh && id == state.cookieId) return
        cancelInspection()
        app.store.selectedCookieId=id
        // Changing a session must invalidate source data, not dismiss the open
        // configuration or silently keep metadata fetched anonymously. Keep the
        // panel identity so its current format choices survive the refresh.
        val pending=state.preview?.let { MediaPreview(it.url,it.url,
            isPlaylist=PreviewPolicy.isCollection(it.url),metadataReady=false) }
        edit { it.copy(cookieId=id,analyzing=false,preview=pending,analysisError=null,
            playlistEntries=emptyList(),playlistLoading=false,
            analysisState=if(pending==null) AnalysisState.Idle else AnalysisState.Preparing) }
        if(pending!=null && state.showMediaPreview) startInspection(false,true)
    }
    fun refreshProfiles() {
        val before = mutableScreen.value
        val profiles = app.vault.profiles()
        edit { it.copy(profiles = profiles) }
        val selected = before.cookieId ?: return
        val previous = before.profiles.find { it.id == selected }
        val current = profiles.find { it.id == selected }
        if (current == null && previous != null) {
            // Keep the missing selection. Anonymous mode requires an explicit choice.
            app.engine.invalidateSessionSnapshots(selected)
            cancelInspection()
            val message = text(R.string.missing_session)
            edit { state ->
                val preview = state.preview?.let { MediaPreview(it.url, it.url,
                    isPlaylist = PreviewPolicy.isCollection(it.url), metadataReady = false) }
                state.copy(preview = preview, analyzing = false, playlistLoading = false,
                    playlistEntries = emptyList(), message = message, analysisError = message,
                    analysisState = if (preview == null) AnalysisState.Idle else AnalysisState.Error)
            }
        } else if (current != null && current.updatedAt != previous?.updatedAt) {
            app.engine.invalidateSessionSnapshots(selected)
            selectCookie(selected, forceRefresh = true)
        }
    }
    fun removeProfile(id: String) {
        val removed = app.vault.remove(id)
        app.engine.invalidateSessionSnapshots(id)
        refreshProfiles()
        if (!removed) showMessage(text(R.string.session_save_error))
    }
    fun shareIntent(text: String) {
        val urls=runCatching { IncomingLinks.extract(text) }.getOrElse { showMessage(text(R.string.options_invalid));return }
        if(urls.isEmpty()) { showMessage(text(R.string.check_link));return }
        val fingerprint=SourceIdentity.fingerprint(urls)
        val now=android.os.SystemClock.elapsedRealtime()
        if(fingerprint==lastShare && now-lastShareAt<3000) return
        lastShare=fingerprint;lastShareAt=now
        if(urls.size>1) {
            cancelInspection()
            edit { it.copy(tab=4,preview=null,bulkLinks=urls.map { link -> BulkLinkInput(url=link) },fromShare=true) }
        } else {
            url(urls.first());tab(0);edit { it.copy(fromShare=true) };analyze()
        }
    }
    fun importLinks(uri: Uri) {
        inspection?.cancel()
        inspection=viewModelScope.launch {
            try {
                val imported=withContext(Dispatchers.IO) {
                    val task=coroutineContext[Job]
                    app.contentResolver.openInputStream(uri)?.use { IncomingLinks.read(it) { task?.isActive==false } } ?: error("input")
                }
                edit { it.copy(importPreview=imported) }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { showMessage(text(R.string.bulk_limit)) }
        }
    }
    fun acceptImport(accept: Boolean) {
        val imported=mutableScreen.value.importPreview ?: return
        edit { it.copy(importPreview=null,bulkLinks=if(accept) imported.links.map { link -> BulkLinkInput(url=link) }.ifEmpty { listOf(BulkLinkInput()) } else it.bulkLinks) }
    }
    fun resolveDuplicate(anotherCopy: Boolean) {
        val candidates=duplicateCandidates;duplicateCandidates=emptyList()
        edit { it.copy(duplicate=null) }
        if(anotherCopy) enqueueBatch(candidates,allowDuplicate=true)
    }
    fun queueConcurrency(value: Int) { app.store.concurrentDownloads=value;viewModelScope.launch(Dispatchers.IO) { DownloadWorker.resumeQueue(app) } }
    fun pauseQueued(id: String) { viewModelScope.launch(Dispatchers.IO) { DownloadWorker.pauseQueued(app,id) } }
    fun resumeQueued(id: String) { viewModelScope.launch(Dispatchers.IO) { DownloadWorker.resumeQueued(app,id) } }
    fun priority(id: String,value: Int) { viewModelScope.launch(Dispatchers.IO) { app.store.change(id) { it.copy(priority=value.coerceIn(-1,1)) } } }
    fun reorderQueue(ids: List<String>) { viewModelScope.launch(Dispatchers.IO) { app.store.reorder(ids) } }
    fun favorite(id: String) { viewModelScope.launch(Dispatchers.IO) { app.store.change(id) { it.copy(favorite=!it.favorite) } } }
    fun collection(id: String,value: String) { viewModelScope.launch(Dispatchers.IO) { app.store.change(id) { it.copy(collection=value.take(80)) } } }
    fun deleteEntries(ids: Set<String>,physical: Boolean) {
        viewModelScope.launch {
            val failed=withContext(Dispatchers.IO) {
                var failures=0
                app.store.jobs.value.filter { it.id in ids && it.state !in setOf(JobState.QUEUED,JobState.RUNNING,JobState.SAVING,JobState.PAUSED) }.forEach { job ->
                    var removed=true
                    if(physical) job.files.forEach { value ->
                        val uri=Uri.parse(value)
                        try {
                            if(android.provider.DocumentsContract.isDocumentUri(app,uri)) removed=android.provider.DocumentsContract.deleteDocument(app.contentResolver,uri) && removed
                            else removed=(app.contentResolver.delete(uri,null,null)>0) && removed
                        } catch(e: Exception) { removed=false }
                    }
                    if(removed) app.store.removeEntry(job.id) else failures++
                }
                failures
            }
            if(failed>0) showMessage(text(R.string.feature_file_error))
        }
    }
    private fun text(id: Int,vararg args: Any)=AppLanguage.context(getApplication()).getString(id,*args)
    private fun checkSession(id: String?): Boolean {
        if (app.vault.exists(id)) return true
        showMessage(text(R.string.missing_session))
        return false
    }
    fun analyze() {
        val state=mutableScreen.value
        val url=try { normalizeUrl(state.url) } catch(e: Exception) {
            edit { it.copy(analysisState=AnalysisState.Error) };showMessage(e.message ?: text(R.string.check_link));return
        }
        if(state.preview?.url==url) return
        cancelInspection()
        inspectionOptions=state.defaults
        val trace=AnalysisTrace(app.store);analysisTrace=trace
        edit { it.copy(url=url,preview=MediaPreview(url,url,isPlaylist=PreviewPolicy.isCollection(url),
            metadataReady=false),analyzing=false,analysisError=null,message=null,playlistLoading=false,playlistEntries=emptyList(),
            analysisState=AnalysisState.Preparing,analysisId=trace.id) }
        trace.mark("panel_state")
        app.store.log("INSPECT configuration opened before metadata")
        if(if(state.fromShare) app.store.oneTapShare else !state.configureBeforeDownload) { enqueue(state.defaults);return }
        if(state.showMediaPreview) startInspection(false,true)
    }
    fun refreshInfo(slow: Boolean=false)=startInspection(slow,false)
    private fun startInspection(slow: Boolean,reuseTrace: Boolean) {
        val state=mutableScreen.value
        val url=state.preview?.url ?: return
        cancelInspection();val request=generation
        val trace=(analysisTrace.takeIf { reuseTrace } ?: AnalysisTrace(app.store)).also { analysisTrace=it }
        val options = inspectionOptions ?: state.defaults
        runningInspectionOptions = MetadataSnapshotPolicy.extractionOptions(options)
        edit { it.copy(analyzing=true,inspectSlow=slow,analysisError=null,analysisState=AnalysisState.Preparing,analysisId=trace.id) }
        inspection=viewModelScope.launch {
            try {
                val preview=app.engine.inspect(url,state.cookieId,options,slow,onUpdate={ phase,partial ->
                    if(request==generation) edit { current ->
                        if(request!=generation || current.url!=url || current.cookieId!=state.cookieId ||
                            current.preview?.metadataReady==true && phase!=AnalysisState.Ready) current
                        else current.copy(analysisState=phase,preview=partial ?: current.preview)
                    }
                },trace=trace)
                coroutineContext.ensureActive()
                if(request==generation && mutableScreen.value.url==url && mutableScreen.value.cookieId==state.cookieId)
                    edit { it.copy(preview=preview,analysisState=AnalysisState.Ready) }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { if(request==generation) {
                extractionFailureLines(e.message ?: e.javaClass.simpleName).forEach { app.store.log("INSPECT failed: $it") }
                trace.mark("error")
                val message = if (e is CookieSessionException) text(R.string.missing_session)
                    else recoveryMessage(AppLanguage.context(app), RecoveryPolicy.classify(e.message.orEmpty()))
                edit { it.copy(analysisError=message,analysisState=AnalysisState.Error) }
            } }
            finally { if(request==generation) {
                runningInspectionOptions = null
                edit { it.copy(analyzing=false) }
            } }
        }
    }
    fun enqueue(options: DownloadOptions) {
        val state=mutableScreen.value;val preview=state.preview ?: return
        if(state.submitting) return
        if (!checkSession(state.cookieId) || !validOptions(options,preview.duration)) return
        if(state.rememberDownloadChoices) defaults(options)
        if(options.playlist && preview.isPlaylist) { loadPlaylist(options);return }
        if (inspection?.isActive == true && options.customArguments.isBlank() &&
            MetadataSnapshotPolicy.extractionOptions(options) == runningInspectionOptions) {
            // The worker can reuse this in-flight extraction. Stop delivering its
            // UI callbacks but let the already-started network request complete.
            generation++
            inspection = null
            runningInspectionOptions = null
        } else cancelInspection()
        edit { it.copy(analyzing=false) }
        enqueueBatch(listOf(DownloadJob(url=preview.url,title=preview.title.takeIf { it.isNotBlank() && it!=preview.url } ?: Uri.parse(preview.url).host.orEmpty(),thumbnail=preview.thumbnail,
            durationSeconds=preview.duration,options=options.copy(cookieId=state.cookieId,playlist=false,playlistItems=""))))
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
        if (!checkSession(mutableScreen.value.cookieId) || !validOptions(options)) return false
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
        require(options.clipRanges.size<=20)
        options.clipRanges.forEach { it.validate(duration.toDouble()) }
        InspectionPolicy.section(options.clipStart,options.clipEnd,duration)
        if(options.filenameOverride.isNotBlank()) InspectionPolicy.literalFilename(options.filenameOverride)
        if(options.customArguments.isNotBlank()) parseCustomArguments(options.customArguments)
        require(options.maxFilesizeMb in 0..102400)
        true
    } catch(e: IllegalArgumentException) { showMessage(text(R.string.options_invalid));false }
    fun loadPlaylist(options: DownloadOptions=mutableScreen.value.defaults) {
        val state=mutableScreen.value
        if(state.submitting || state.playlistLoading) return
        if (!checkSession(state.cookieId)) return
        val url=runCatching { normalizeUrl(state.preview?.url ?: state.url) }.getOrElse { showMessage(text(R.string.check_link));return }
        cancelInspection();val request=generation;playlistOptions=options.copy(cookieId=state.cookieId)
        edit { it.copy(playlistLoading=true,analyzing=false,preview=null,playlistEntries=emptyList(),message=null,analysisState=AnalysisState.FetchingMetadata) }
        inspection=viewModelScope.launch {
            try {
                val entries=app.engine.playlist(url,state.cookieId,options)
                coroutineContext.ensureActive()
                if(request==generation && mutableScreen.value.cookieId==state.cookieId) {
                    if(entries.isEmpty()) showMessage(text(R.string.playlist_empty))
                    edit { it.copy(playlistEntries=entries,analysisState=AnalysisState.Ready) }
                }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { if(request==generation) {
                extractionFailureLines(e.message ?: e.javaClass.simpleName).forEach { app.store.log("PLAYLIST failed: $it") }
                edit { it.copy(analysisState=AnalysisState.Error) }
                showMessage(text(if (e is CookieSessionException) R.string.missing_session else R.string.playlist_error))
            } }
            finally { if(request==generation) edit { it.copy(playlistLoading=false) } }
        }
    }
    fun dismissPlaylist() { cancelInspection();edit { it.copy(playlistEntries=emptyList(),playlistLoading=false,analysisState=AnalysisState.Idle) } }
    fun enqueuePlaylist(indices: Set<Int>) {
        val options=(playlistOptions ?: mutableScreen.value.defaults).copy(playlist=false,playlistItems="",selectedFormat="")
        val entries=mutableScreen.value.playlistEntries.filter { it.index in indices }
        if(entries.isEmpty()) return
        enqueueBatch(entries.map { DownloadJob(url=it.url,title=it.title,options=options) })
    }
    private fun enqueueBatch(candidates: List<DownloadJob>,clearBulk: Boolean=false,stayTerminal: Boolean=false,allowDuplicate: Boolean=false) {
        if(mutableScreen.value.submitting) return
        if (candidates.any { !checkSession(it.options.cookieId) }) return
        val expectedGeneration=generation
        val expectedUrl=mutableScreen.value.url
        val expectedTab=mutableScreen.value.tab
        val submittedLinks=mutableScreen.value.bulkLinks
        val active=setOf(JobState.QUEUED,JobState.RUNNING,JobState.SAVING)
        if(!allowDuplicate) {
            val match=candidates.firstNotNullOfOrNull { candidate -> app.store.jobs.value.firstOrNull {
                it.state==JobState.COMPLETED && it.files.isNotEmpty() && duplicateKey(it)==duplicateKey(candidate)
            } }
            if(match!=null) { duplicateCandidates=candidates;edit { it.copy(duplicate=match) };return }
        }
        edit { it.copy(submitting=true) }
        fun key(job: DownloadJob)=duplicateKey(job)+"|"+job.options.cookieId.orEmpty()
        viewModelScope.launch {
            try {
                val added=withContext(Dispatchers.IO) {
                    enqueueGate.withLock {
                        val current=app.store.jobs.value.filter { it.state in active }
                        val seen=current.map(::key).toMutableSet()
                        val selected=candidates.filter { seen.add(key(it)) }
                        require(current.size+selected.size<=200) { "capacity" }
                        selected.mapNotNull { it.options.cookieId }.distinct().forEach(app.vault::requireAvailable)
                        selected.forEachIndexed { index,job ->
                            DownloadWorker.enqueue(app,job.copy(createdAt=System.currentTimeMillis()+index))
                        }
                        selected
                    }
                }
                val count=added.size
                if(count==0) {
                    val sameInteraction=expectedGeneration==generation && mutableScreen.value.url==expectedUrl && mutableScreen.value.tab==expectedTab
                    if(sameInteraction) edit { it.copy(preview=null,tab=if(stayTerminal) 4 else 1,showHistory=false,playlistEntries=emptyList(),message=text(R.string.bulk_duplicates)) }
                    else showMessage(text(R.string.bulk_duplicates))
                }
                else {
                    val sameInteraction=expectedGeneration==generation && mutableScreen.value.url==expectedUrl && mutableScreen.value.tab==expectedTab
                    if(sameInteraction) edit { it.copy(preview=null,url="",tab=if(stayTerminal) 4 else if(expectedTab==0) 0 else 1,
                        homeJobId=if(expectedTab==0) added.first().id else it.homeJobId,showHistory=false,playlistEntries=emptyList(),
                        bulkLinks=if(clearBulk && it.bulkLinks==submittedLinks) listOf(BulkLinkInput()) else it.bulkLinks,message=text(R.string.bulk_added,count)) }
                    else showMessage(text(R.string.bulk_added,count))
                }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) {
                val message = when {
                    e is CookieSessionException -> R.string.missing_session
                    e.message == "capacity" -> R.string.queue_capacity
                    else -> R.string.bulk_error
                }
                showMessage(text(message))
            }
            finally { edit { it.copy(submitting=false) } }
        }
    }
    fun cancel(id: String) { viewModelScope.launch(Dispatchers.IO) { DownloadWorker.cancel(app,id) } }
    fun retry(job: DownloadJob) {
        val replacement=job.copy(id=UUID.randomUUID().toString(),
            options=if(job.options.customArguments.isBlank()) job.options.copy(downloadArchive=false) else job.options,
            state=JobState.QUEUED,progress=0f,retryCount=0,failureKind="",
            detail="Sırada",phase="queued",progressKnown=false,etaSeconds=-1,bytesDownloaded=0,totalBytes=0,createdAt=System.currentTimeMillis(),files=emptyList(),notes=emptyList())
        enqueueBatch(listOf(replacement),stayTerminal=job.options.customArguments.isNotBlank())
    }
    fun reloadPreferences() {
        edit { it.copy(defaults=app.store.defaultOptions,themeMode=app.store.themeMode,language=app.store.language,
            dynamicColors=app.store.dynamicColors,configureBeforeDownload=app.store.configureBeforeDownload,
            rememberDownloadChoices=app.store.rememberDownloadChoices,showMediaPreview=app.store.showMediaPreview,keepHistory=app.store.keepHistory) }
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


internal fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val output=java.io.ByteArrayOutputStream()
    val buffer=ByteArray(8192)
    while(output.size()<limit) {
        val count=read(buffer,0,minOf(buffer.size,limit-output.size()))
        if(count<0) break
        output.write(buffer,0,count)
    }
    return output.toByteArray()
}
