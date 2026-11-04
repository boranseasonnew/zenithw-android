@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package space.zenithw.app

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun LibraryScreen(model: ZenithViewModel,jobs: List<DownloadJob>,initialHistory: Boolean,onOpen: (DownloadJob,Boolean)->Unit) {
    val texts=LocalAppContext.current
    val context=LocalContext.current
    var concurrency by rememberSaveable { mutableIntStateOf(model.app.store.concurrentDownloads) }
    var history by rememberSaveable(initialHistory) { mutableStateOf(initialHistory) }
    var search by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("date") }
    var type by rememberSaveable { mutableStateOf("all") }
    var platform by rememberSaveable { mutableStateOf("all") }
    var state by rememberSaveable { mutableStateOf("all") }
    var highResolution by rememberSaveable { mutableStateOf(false) }
    var week by rememberSaveable { mutableStateOf(false) }
    var favorites by rememberSaveable { mutableStateOf(false) }
    var grid by rememberSaveable { mutableStateOf(false) }
    var filters by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    var delete by remember { mutableStateOf(false) }
    var collection by rememberSaveable { mutableStateOf("") }
    var count by rememberSaveable { mutableIntStateOf(50) }
    val scope=rememberCoroutineScope()
    var exporting by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var exportProgress by remember { mutableStateOf(0 to 0) }
    val exportPicker=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { tree ->
        if(tree!=null) {
            val chosen=jobs.filter { it.id in selected }
            exporting=scope.launch {
                try {
                    exportLibraryFiles(context,chosen,tree) { done,total -> exportProgress=done to total }
                    model.showMessage(texts.getString(R.string.feature_done))
                } catch(e: CancellationException) { throw e }
                catch(e: Exception) { model.showMessage(texts.getString(R.string.feature_file_error)) }
                finally { exporting=null }
            }
        }
    }
    val active=setOf(JobState.QUEUED,JobState.RUNNING,JobState.SAVING,JobState.PAUSED)
    val queue=jobs.filter { it.state in active }.sortedWith(compareByDescending<DownloadJob> { it.priority }.thenBy { it.queueOrder })
    val visible=remember(jobs,history,search,sort,type,platform,state,highResolution,week,favorites,collection) {
        val found=jobs.filter { job ->
            (job.state !in active)==history && (type=="all" || job.options.mode.name==type) &&
                (platform=="all" || SourceIdentity.platform(job.url)==platform) && (state=="all" || job.state.name==state) &&
                (!highResolution || job.options.height>=720) && (!week || System.currentTimeMillis()-job.createdAt<7*86400000L) &&
                (!favorites || job.favorite) && (collection.isBlank() || job.collection.contains(collection,true)) &&
                (search.isBlank() || job.title.contains(search,true) || job.files.any { Uri.parse(it).lastPathSegment.orEmpty().contains(search,true) })
        }
        if(!history) found.sortedWith(compareByDescending<DownloadJob> { it.priority }.thenBy { it.queueOrder })
        else when(sort) { "name" -> found.sortedBy { it.title.lowercase() };"size" -> found.sortedByDescending { it.totalBytes };"duration" -> found.sortedByDescending { it.durationSeconds };else -> found.sortedByDescending { it.createdAt } }
    }
    val missing by produceState<Set<String>>(emptySet(),jobs.filter { it.state==JobState.COMPLETED }.map { it.id to it.files }) {
        value=withContext(Dispatchers.IO) { jobs.filter { it.state==JobState.COMPLETED && it.files.isNotEmpty() }.filter { job ->
            job.files.none { file -> runCatching { context.contentResolver.openFileDescriptor(Uri.parse(file),"r")?.use { true } ?: false }.getOrDefault(false) }
        }.map { it.id }.toSet() }
    }
    val density=LocalDensity.current
    fun move(id: String,to: Int) {
        val ids=queue.map { it.id }.toMutableList();ids.remove(id);ids.add(to.coerceIn(0,ids.size),id);model.reorderQueue(ids)
    }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Choices(listOf("queue" to texts.getString(R.string.queue_filter),"history" to texts.getString(R.string.history_filter)),if(history) "history" else "queue") { history=it=="history";selected=emptyList() } }
        item {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(texts.getString(R.string.files),Modifier.weight(1f),style=MaterialTheme.typography.headlineMedium)
                IconButton(onClick={ filters=!filters }) { Icon(Icons.Outlined.FilterList,texts.getString(R.string.feature_filters)) }
                if(history) IconButton(onClick={ grid=!grid }) { Icon(Icons.Outlined.GridView,texts.getString(R.string.feature_grid)) }
            }
            OutlinedTextField(search,{ search=it;count=50 },label={ Text(texts.getString(R.string.feature_search)) },modifier=Modifier.fillMaxWidth(),singleLine=true)
        }
        if(!history) item {
            OptionPicker(texts.getString(R.string.feature_concurrency),(1..5).map { it.toString() to it.toString() },concurrency.toString()) { concurrency=it.toInt();model.queueConcurrency(concurrency) }
        }
        if(filters) item {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Choices(listOf("all" to texts.getString(R.string.feature_all),"VIDEO" to texts.getString(R.string.video),"AUDIO" to texts.getString(R.string.audio)),type) { type=it }
                OptionPicker(texts.getString(R.string.feature_platform),listOf("all" to texts.getString(R.string.feature_all))+jobs.map { SourceIdentity.platform(it.url) }.distinct().map { it to it },platform) { platform=it }
                OptionPicker(texts.getString(R.string.feature_status),listOf("all" to texts.getString(R.string.feature_all))+jobs.map { it.state }.distinct().map { it.name to texts.getString(jobStateResource(it)) },state) { state=it }
                OptionPicker(texts.getString(R.string.feature_sort),listOf("date" to texts.getString(R.string.feature_date),"name" to texts.getString(R.string.feature_name),"size" to texts.getString(R.string.feature_size),"duration" to texts.getString(R.string.feature_duration)),sort) { sort=it }
                OptionSwitch(texts.getString(R.string.feature_favorites),value=favorites) { favorites=it }
                OptionSwitch(texts.getString(R.string.feature_week),value=week) { week=it }
                OptionSwitch("720p+",value=highResolution) { highResolution=it }
                OutlinedTextField(collection,{ collection=it.take(80) },label={ Text(texts.getString(R.string.feature_collection)) },modifier=Modifier.fillMaxWidth())
            }
        }
        if(selected.isNotEmpty()) item {
            Text("${selected.size} / ${visible.size}")
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(onClick={ selected=visible.map { it.id } }) { Text(texts.getString(R.string.feature_select_all)) }
                if(history) {
                    TextButton(onClick={ exportPicker.launch(null) },enabled=exporting==null) { Text(texts.getString(R.string.feature_export)) }
                    TextButton(onClick={ runCatching { openFiles(context,jobs.filter { it.id in selected }.flatMap { it.files },true) }.onFailure { model.showMessage(texts.getString(R.string.no_opener)) } }) { Text(texts.getString(R.string.share)) }
                    TextButton(onClick={ delete=true }) { Text(texts.getString(R.string.feature_delete)) }
                    TextButton(onClick={ selected.forEach { model.collection(it,collection) } }) { Text(texts.getString(R.string.feature_collection)) }
                } else {
                    TextButton(onClick={ selected.forEach(model::cancel);selected=emptyList() }) { Text(texts.getString(R.string.cancel_download)) }
                    TextButton(onClick={ selected.forEach(model::pauseQueued) }) { Text(texts.getString(R.string.feature_pause)) }
                }
            }
        }
        if(exporting!=null) item {
            Text("${exportProgress.first} / ${exportProgress.second}")
            LinearProgressIndicator(Modifier.fillMaxWidth())
            TextButton(onClick={ exporting?.cancel() }) { Text(texts.getString(R.string.cancel_download)) }
        }
        if(visible.isEmpty()) item { Text(texts.getString(if(history) R.string.empty_files else R.string.empty_queue),Modifier.padding(24.dp),color=Muted) }
        val rows=visible.take(count).chunked(if(grid && history) 2 else 1)
        items(rows,key={ it.first().id }) { row ->
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                row.forEach { job ->
                    val index=queue.indexOfFirst { it.id==job.id }
                    val onDragMove by rememberUpdatedState<(Int)->Unit> { direction -> move(job.id,index+direction) }
                    var drag by remember { mutableFloatStateOf(0f) }
                    Surface(Modifier.weight(1f),color=Panel,shape=RoundedCornerShape(22.dp),border=BorderStroke(1.dp,Hairline)) {
                        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            JobCover(job,Modifier.fillMaxWidth().height(if(grid) 96.dp else 120.dp))
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Checkbox(job.id in selected,{ selected=if(it) selected+job.id else selected-job.id })
                                Text(job.title,Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleSmall)
                                if(!history) Icon(Icons.Outlined.DragHandle,texts.getString(R.string.feature_reorder),Modifier.size(48.dp).pointerInput(job.id) {
                                    detectDragGesturesAfterLongPress(onDragStart={ drag=0f },onDrag={ change,delta ->
                                        change.consume();drag+=delta.y
                                        if(kotlin.math.abs(drag)>with(density) { 90.dp.toPx() }) { onDragMove(if(drag>0) 1 else -1);drag=0f }
                                    })
                                })
                            }
                            Text(texts.getString(jobStateResource(job.state)),color=Muted)
                            if(job.id in missing) Text(texts.getString(R.string.feature_missing),color=MaterialTheme.colorScheme.error)
                            Text(listOfNotNull(job.totalBytes.takeIf { it>0 }?.let(::mediaSize),job.durationSeconds.takeIf { it>0 }?.let { mediaDuration(it.toLong()) },job.speedBytes.takeIf { it>0 }?.let { "${mediaSize(it)}/s" },job.etaSeconds.takeIf { it>0 }?.let { "~${mediaDuration(it)}" }).joinToString(" · "),style=MaterialTheme.typography.labelSmall)
                            if(job.state in setOf(JobState.RUNNING,JobState.SAVING)) {
                                if(job.progressKnown) LinearProgressIndicator(progress={ job.progress/100f },modifier=Modifier.fillMaxWidth())
                                else LinearProgressIndicator(Modifier.fillMaxWidth())
                            }
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                                if(history) {
                                    IconButton(onClick={ model.favorite(job.id) }) { Icon(if(job.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,texts.getString(R.string.feature_favorites)) }
                                    if(job.files.isNotEmpty()) {
                                        TextButton(onClick={ onOpen(job,false) }) { Text(texts.getString(R.string.open)) }
                                        TextButton(onClick={ model.toolSource=job.files.first();model.tab(5) }) { Text(texts.getString(R.string.feature_tools)) }
                                    }
                                    if(job.state in setOf(JobState.FAILED,JobState.CANCELLED,JobState.SKIPPED)) TextButton(onClick={ model.retry(job) }) { Text(texts.getString(R.string.retry)) }
                                } else {
                                    if(job.state==JobState.QUEUED) {
                                        TextButton(onClick={ move(job.id,0);model.priority(job.id,1) }) { Text(texts.getString(R.string.feature_top)) }
                                        TextButton(onClick={ model.pauseQueued(job.id) }) { Text(texts.getString(R.string.feature_pause)) }
                                        OptionPicker(texts.getString(R.string.feature_priority),listOf("-1" to "−","0" to "=","1" to "+"),job.priority.toString()) { model.priority(job.id,it.toInt()) }
                                    }
                                    if(job.state==JobState.PAUSED) TextButton(onClick={ model.resumeQueued(job.id) }) { Text(texts.getString(R.string.feature_resume)) }
                                    TextButton(onClick={ model.cancel(job.id) }) { Text(texts.getString(R.string.cancel_download)) }
                                }
                                if(job.state==JobState.FAILED) Text(recoveryMessage(texts,runCatching { RecoveryKind.valueOf(job.failureKind) }.getOrDefault(RecoveryPolicy.classify(job.detail))),style=MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        if(visible.size>count) item { TextButton(onClick={ count+=50 }) { Text(texts.getString(R.string.feature_more)) } }
        item { Text("Σ ${mediaSize(jobs.filter { it.state==JobState.COMPLETED }.sumOf { it.totalBytes })}",color=Muted) }
    }
    if(delete) AlertDialog(onDismissRequest={ delete=false },title={ Text(texts.getString(R.string.feature_delete)) },text={ Text(texts.getString(R.string.feature_delete_warning)) },
        confirmButton={ Column {
            TextButton(onClick={ model.deleteEntries(selected.toSet(),false);selected=emptyList();delete=false }) { Text(texts.getString(R.string.feature_entry_only)) }
            TextButton(onClick={ model.deleteEntries(selected.toSet(),true);selected=emptyList();delete=false }) { Text(texts.getString(R.string.feature_physical)) }
        } },dismissButton={ TextButton(onClick={ delete=false }) { Text(texts.getString(R.string.cancel_download)) } })
}

internal fun jobStateResource(state: JobState)=when(state) {
    JobState.PAUSED -> R.string.feature_paused
    JobState.QUEUED -> R.string.queue_filter
    JobState.RUNNING -> R.string.feature_downloading
    JobState.SAVING -> R.string.feature_processing
    JobState.COMPLETED -> R.string.feature_completed
    JobState.FAILED -> R.string.feature_failed
    JobState.CANCELLED -> R.string.cancel_download
    JobState.SKIPPED -> R.string.feature_skipped
}
internal fun recoveryMessage(context: android.content.Context,kind: RecoveryKind)=context.getString(when(kind) {
    RecoveryKind.FORBIDDEN -> R.string.feature_error_403
    RecoveryKind.RATE_LIMIT -> R.string.feature_error_429
    RecoveryKind.SESSION -> R.string.feature_error_session
    RecoveryKind.FORMAT -> R.string.feature_error_format
    RecoveryKind.NETWORK -> R.string.feature_error_network
    RecoveryKind.STORAGE -> R.string.feature_error_storage
    RecoveryKind.PERMISSION -> R.string.feature_error_permission
    RecoveryKind.UNSUPPORTED -> R.string.feature_error_unsupported
    RecoveryKind.CONVERSION -> R.string.feature_error_conversion
    else -> R.string.feature_error_engine
})
