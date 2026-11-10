@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package space.zenithw.app

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable fun ToolsScreen(model: ZenithViewModel) {
    val texts=LocalAppContext.current
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var source by rememberSaveable { mutableStateOf(model.toolSource.orEmpty()) }
    var batchSources by rememberSaveable { mutableStateOf(listOf<String>()) }
    var subtitle by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf("remux_mp4") }
    var preset by rememberSaveable { mutableStateOf("balanced") }
    var resolution by rememberSaveable { mutableIntStateOf(480) }
    var bitrate by rememberSaveable { mutableIntStateOf(192) }
    var start by rememberSaveable { mutableStateOf("0") }
    var end by rememberSaveable { mutableStateOf("10") }
    var offset by rememberSaveable { mutableStateOf("0") }
    var range by remember { mutableStateOf(0f..10f) }
    var player by remember { mutableStateOf<android.widget.VideoView?>(null) }
    var duration by remember { mutableFloatStateOf(0f) }
    var caps by remember { mutableStateOf<LocalMediaTools.Capabilities?>(null) }
    var capabilityError by remember { mutableStateOf(false) }
    var fps by rememberSaveable { mutableIntStateOf(10) }
    var loops by rememberSaveable { mutableIntStateOf(0) }
    var sampleRate by rememberSaveable { mutableIntStateOf(44100) }
    var videoBitrate by rememberSaveable { mutableIntStateOf(0) }
    var submitting by remember { mutableStateOf(false) }
    val manager=remember { WorkManager.getInstance(context) }
    val work by manager.getWorkInfosForUniqueWorkFlow(LocalToolWorker.NAME).collectAsState(initial=emptyList())
    val task=work.firstOrNull { !it.state.isFinished } ?: work.maxByOrNull { it.runAttemptCount }
    val busy=submitting || work.any { !it.state.isFinished }
    var backupLibrary by rememberSaveable { mutableStateOf(false) }
    var restore by remember { mutableStateOf<SettingsBackup.Preview?>(null) }
    var candidates by remember { mutableStateOf<List<StorageGuard.Candidate>?>(null) }
    var available by remember { mutableLongStateOf(0) }
    var template by rememberSaveable { mutableStateOf(FilenamePolicy.DEFAULT) }
    var folder by rememberSaveable { mutableStateOf(model.app.store.defaultOptions.outputFolder) }
    var smart by rememberSaveable { mutableStateOf(model.app.store.smartEnabled) }
    var oneTap by rememberSaveable { mutableStateOf(model.app.store.oneTapShare) }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
            source=uri.toString();batchSources=emptyList();duration=0f;model.toolSource=source
        }.onFailure { model.showMessage(texts.getString(R.string.feature_file_error)) }
    }
    val batchPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        runCatching {
            require(uris.size<=10 && uris.sumOf { it.toString().length }<=6000)
            uris.forEach { context.contentResolver.takePersistableUriPermission(it,Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            batchSources=uris.map { it.toString() }
            if(uris.isNotEmpty()) source=uris.first().toString()
        }.onFailure { model.showMessage(texts.getString(R.string.options_invalid)) }
    }
    val subtitlePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) runCatching { context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);subtitle=uri.toString() }
    }
    fun request(destination: Uri)=LocalMediaTools.Request(Uri.parse(source),destination,mode,
        if(mode in setOf("trim","trim_exact","gif","webp")) InspectionPolicy.seconds(start) ?: 0.0 else 0.0,
        if(mode in setOf("trim","trim_exact","gif","webp")) InspectionPolicy.seconds(end) ?: 0.0 else 0.0,preset,
        if(mode in setOf("gif","webp","mp4","burn")) resolution else 0,bitrate,
        sampleRate=sampleRate,fps=fps,loop=loops,videoBitrateKbps=videoBitrate,subtitle=subtitle.takeIf { it.isNotBlank() }?.let(Uri::parse),offset=offset.toDoubleOrNull() ?: 0.0).also {
        if(mode in setOf("trim","trim_exact","gif","webp")) ClipRange(it.start,it.end).validate(duration.toDouble())
        if(mode in setOf("gif","webp")) require(it.end-it.start<=30)
    }
    val outputPicker=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if(uri!=null) {
            submitting=true
            scope.launch {
                try {
                    context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if(!LocalToolWorker.enqueue(context,request(uri))) model.showMessage(texts.getString(R.string.feature_busy))
                } catch(e: Exception) { model.showMessage(texts.getString(R.string.options_invalid)) }
                finally { submitting=false }
            }
        }
    }
    val batchOutput=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if(uri!=null) {
            submitting=true
            scope.launch {
                try {
                    context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if(!LocalToolWorker.enqueue(context,request(uri),batchSources.map(Uri::parse))) model.showMessage(texts.getString(R.string.feature_busy))
                } catch(e: Exception) { model.showMessage(texts.getString(R.string.options_invalid)) }
                finally { submitting=false }
            }
        }
    }
    val backupOutput=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri!=null) scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val data=SettingsBackup(context,model.app.store).export(backupLibrary)
                context.contentResolver.openOutputStream(uri)?.use { it.write(data.toByteArray()) } ?: error("output")
            } }.onSuccess { model.showMessage(texts.getString(R.string.feature_done)) }.onFailure { model.showMessage(texts.getString(R.string.feature_file_error)) }
        }
    }
    val backupInput=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val bytes=context.contentResolver.openInputStream(uri)?.use { it.readNBytesCompat(512*1024+1) } ?: error("input")
                SettingsBackup(context,model.app.store).validate(bytes)
            } }.onSuccess { restore=it }.onFailure { model.showMessage(texts.getString(R.string.feature_backup_invalid)) }
        }
    }
    LaunchedEffect(player,range) {
        while(kotlinx.coroutines.currentCoroutineContext().isActive) {
            kotlinx.coroutines.delay(100)
            player?.takeIf { it.isPlaying && it.currentPosition>=range.endInclusive*1000 }?.pause()
        }
    }
    LaunchedEffect(Unit) {
        available=withContext(Dispatchers.IO) { context.cacheDir.usableSpace }
        runCatching { withContext(Dispatchers.IO) { model.app.engine.runtimeVersion();LocalMediaTools(context).capabilities() } }
            .onSuccess { caps=it }.onFailure { capabilityError=true }
    }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Text(texts.getString(R.string.feature_tools),style=MaterialTheme.typography.headlineLarge) }
        item {
            ExpandableSection(texts.getString(R.string.feature_converter),texts.getString(R.string.feature_local_only)) {
                OutlinedButton(onClick={ picker.launch(arrayOf("video/*","audio/*","text/*","application/octet-stream")) },enabled=!busy) { Text(texts.getString(R.string.feature_import_file)) }
                OutlinedButton(onClick={ batchPicker.launch(arrayOf("video/*","audio/*")) },enabled=!busy) { Text(texts.getString(R.string.feature_batch_files)) }
                if(batchSources.isNotEmpty()) Text(batchSources.size.toString())
                if(source.isNotBlank()) Text(Uri.parse(source).lastPathSegment.orEmpty().takeLast(100),style=MaterialTheme.typography.bodySmall)
                if(caps==null) Text(texts.getString(if(capabilityError) R.string.feature_error_engine else R.string.queued_preparing),color=Muted)
                val modes=listOf("remux_mp4" to "MP4 · "+texts.getString(R.string.feature_copy),"remux_mkv" to "MKV · "+texts.getString(R.string.feature_copy),"mp4" to "MP4 · H.264",
                    "mp3" to "MP3","m4a" to "M4A","wav" to "WAV","flac" to "FLAC",
                    "mute" to texts.getString(R.string.feature_mute),"trim" to texts.getString(R.string.feature_trim),
                    "trim_exact" to texts.getString(R.string.feature_exact_cuts),"gif" to "GIF","webp" to "WebP","srt" to "SRT","vtt" to "VTT",
                    "attach" to texts.getString(R.string.feature_attach),"burn" to texts.getString(R.string.feature_burn))
                    .filter { caps?.supports(it.first)==true }
                if(modes.isNotEmpty()) OptionPicker(texts.getString(R.string.feature_output),modes,mode) { mode=it }
                Choices(listOf("fast" to texts.getString(R.string.feature_fast),"balanced" to texts.getString(R.string.feature_balanced),"quality" to texts.getString(R.string.feature_quality)),preset) { preset=it }
                if(mode in setOf("mp4","burn","gif","webp")) OptionPicker(texts.getString(R.string.feature_resolution),listOf(240,360,480).map { it.toString() to "${it}p" },resolution.toString()) { resolution=it.toInt() }
                if(mode in setOf("mp3","m4a","mp4","burn")) OptionPicker(texts.getString(R.string.feature_bitrate),listOf(96,128,192,256,320).map { it.toString() to "${it} kbps" },bitrate.toString()) { bitrate=it.toInt() }
                if(mode in setOf("mp4","burn")) OptionPicker(texts.getString(R.string.feature_video_bitrate),listOf(0,1000,2500,5000,8000).map { it.toString() to if(it==0) texts.getString(R.string.feature_auto) else "${it} kbps" },videoBitrate.toString()) { videoBitrate=it.toInt() }
                if(mode in setOf("mp3","m4a","wav","flac")) OptionPicker(texts.getString(R.string.feature_sample_rate),listOf(22050,44100,48000).map { it.toString() to "$it Hz" },sampleRate.toString()) { sampleRate=it.toInt() }
                if(mode in setOf("gif","webp")) {
                    OptionPicker("FPS",listOf(5,10,15,20).map { it.toString() to it.toString() },fps.toString()) { fps=it.toInt() }
                    OptionPicker(texts.getString(R.string.feature_loop),listOf("0" to texts.getString(R.string.feature_forever),"1" to texts.getString(R.string.feature_once)),loops.toString()) { loops=it.toInt() }
                }
                if(mode in setOf("trim","trim_exact","gif","webp")) {
                    Text(texts.getString(R.string.feature_keyframes),style=MaterialTheme.typography.bodySmall,color=Muted)
                    if(source.isNotBlank()) AndroidView(factory={ android.widget.VideoView(it).also { view -> player=view } },update={ view ->
                        if(view.tag!=source) {
                            view.tag=source;view.setVideoURI(Uri.parse(source))
                            view.setOnPreparedListener { duration=it.duration/1000f;view.seekTo((range.start*1000).toInt()) }
                            view.setOnErrorListener { _,_,_ -> true }
                            view.setMediaController(android.widget.MediaController(view.context).apply { setAnchorView(view) })
                        }
                    },modifier=Modifier.fillMaxWidth().height(180.dp),onRelease={ it.stopPlayback();player=null })
                    if(duration>0) RangeSlider(value=range.start.coerceIn(0f,duration)..range.endInclusive.coerceIn(0f,duration),onValueChange={
                        range=it;start="%.3f".format(java.util.Locale.ROOT,it.start);end="%.3f".format(java.util.Locale.ROOT,it.endInclusive)
                    },valueRange=0f..duration,onValueChangeFinished={ player?.seekTo((range.start*1000).toInt()) })
                    TextButton(onClick={ player?.seekTo((range.start*1000).toInt());player?.start() }) { Text(texts.getString(R.string.feature_preview_range)) }
                    OutlinedTextField(start,{ start=it },label={ Text(texts.getString(R.string.feature_start)) },singleLine=true,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(end,{ end=it },label={ Text(texts.getString(R.string.feature_end)) },singleLine=true,modifier=Modifier.fillMaxWidth())
                }
                if(mode in setOf("srt","vtt","attach","burn")) {
                    Text(texts.getString(R.string.feature_utf8),style=MaterialTheme.typography.bodySmall)
                    OutlinedTextField(offset,{ offset=it },label={ Text(texts.getString(R.string.feature_offset)) },singleLine=true,modifier=Modifier.fillMaxWidth())
                }
                if(mode in setOf("attach","burn")) OutlinedButton(onClick={ subtitlePicker.launch(arrayOf("text/*","application/octet-stream")) }) { Text(texts.getString(R.string.feature_subtitle)) }
                Button(onClick={
                    val extension=when(mode) { "remux_mp4","trim","trim_exact","mute","attach","burn" -> "mp4";"remux_mkv" -> "mkv";else -> mode }
                    if(runCatching { request(Uri.parse("content://pending/output")) }.isFailure) model.showMessage(texts.getString(R.string.options_invalid))
                    else if(batchSources.isNotEmpty()) batchOutput.launch(null) else outputPicker.launch("Zenith-${System.currentTimeMillis()}.$extension")
                },enabled=source.isNotBlank() && !busy && caps?.supports(mode)==true) { Text(texts.getString(R.string.feature_export)) }
                task?.let { current ->
                    val percent=current.progress.getInt("percent",0)
                    if(!current.state.isFinished) {
                        LinearProgressIndicator(progress={ percent/100f },modifier=Modifier.fillMaxWidth())
                        TextButton(onClick={ manager.cancelUniqueWork(LocalToolWorker.NAME) }) { Text(texts.getString(R.string.cancel_download)) }
                    } else { Text(texts.getString(if(current.state==WorkInfo.State.SUCCEEDED) R.string.feature_done else R.string.feature_failed))
                        if(current.state==WorkInfo.State.SUCCEEDED) current.outputData.getString("uri")?.let { uri -> TextButton(onClick={ runCatching { openFiles(context,listOf(uri),false) }.onFailure { model.showMessage(texts.getString(R.string.no_opener)) } }) { Text(texts.getString(R.string.open)) } }
                        if(current.outputData.keyValueMap.containsKey("completed")) Text("✓ ${current.outputData.getInt("completed",0)} · × ${current.outputData.getInt("failed",0)}") }
                }
            }
        }
        item {
            ExpandableSection(texts.getString(R.string.feature_profiles),texts.getString(R.string.feature_profiles_hint)) {
                OptionSwitch(texts.getString(R.string.feature_smart),value=smart) { smart=it;model.app.store.smartEnabled=it }
                OptionSwitch(texts.getString(R.string.feature_one_tap),texts.getString(R.string.feature_one_tap_hint),oneTap) { oneTap=it;model.app.store.oneTapShare=it }
                OutlinedTextField(template,{ template=it.take(250) },label={ Text(texts.getString(R.string.feature_filename)) },modifier=Modifier.fillMaxWidth())
                Text(runCatching { FilenamePolicy.preview(template) }.getOrDefault(texts.getString(R.string.options_invalid)),style=MaterialTheme.typography.bodySmall)
                Text(FilenamePolicy.variables.keys.joinToString(" · ") { "{$it}" },style=MaterialTheme.typography.bodySmall,color=Muted)
                OutlinedTextField(folder,{ folder=it.take(80) },label={ Text(texts.getString(R.string.feature_folder)) },modifier=Modifier.fillMaxWidth())
                Button(onClick={ runCatching {
                    val o=model.app.store.defaultOptions.copy(filenameTemplate=FilenamePolicy.compile(template),outputFolder=FilenamePolicy.folder(folder))
                    model.defaults(o)
                }.onFailure { model.showMessage(texts.getString(R.string.options_invalid)) } }) { Text(texts.getString(R.string.feature_save)) }
            }
        }
        item {
            ExpandableSection(texts.getString(R.string.feature_backup),texts.getString(R.string.feature_backup_hint)) {
                OptionSwitch(texts.getString(R.string.feature_backup_library),value=backupLibrary) { backupLibrary=it }
                TextButton(onClick={ backupOutput.launch("zenith-settings-${BuildConfig.VERSION_NAME.substringBefore('-')}.json") }) { Text(texts.getString(R.string.feature_export)) }
                TextButton(onClick={ backupInput.launch(arrayOf("application/json","text/plain","application/octet-stream")) }) { Text(texts.getString(R.string.feature_restore)) }
            }
        }
        item {
            ExpandableSection(texts.getString(R.string.feature_storage),mediaSize(available)) {
                Text(texts.getString(R.string.feature_storage_hint),style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={ scope.launch { candidates=withContext(Dispatchers.IO) { StorageGuard(context,model.app.store).abandoned() } } }) { Text(texts.getString(R.string.feature_review_cleanup)) }
            }
        }
    }
    restore?.let { preview -> AlertDialog(onDismissRequest={ restore=null },title={ Text(texts.getString(R.string.feature_restore)) },
        text={ Text(texts.getString(R.string.feature_restore_hint)+"\n${preview.profiles}") },confirmButton={ TextButton(onClick={
            scope.launch { runCatching { withContext(Dispatchers.IO) { SettingsBackup(context,model.app.store).restore(preview) } }
                .onSuccess { model.reloadPreferences();smart=model.app.store.smartEnabled }.onFailure { model.showMessage(texts.getString(R.string.feature_backup_invalid)) };restore=null }
        }) { Text(texts.getString(R.string.feature_restore)) } },dismissButton={ TextButton(onClick={ restore=null }) { Text(texts.getString(R.string.cancel_download)) } }) }
    candidates?.let { reviewed -> AlertDialog(onDismissRequest={ candidates=null },title={ Text(texts.getString(R.string.feature_storage)) },
        text={ Text(texts.getString(R.string.feature_cleanup_warning)+"\n${reviewed.size} · ${mediaSize(reviewed.sumOf { it.bytes })}") },
        confirmButton={ TextButton(onClick={ scope.launch { withContext(Dispatchers.IO) { StorageGuard(context,model.app.store).cleanup(reviewed) };candidates=null } }) { Text(texts.getString(R.string.feature_delete)) } },
        dismissButton={ TextButton(onClick={ candidates=null }) { Text(texts.getString(R.string.cancel_download)) } }) }
}
