@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package space.zenithw.app

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage

private val CardShape=RoundedCornerShape(26.dp)

@Composable fun ZenithApp(model: ZenithViewModel,onDownload: (DownloadOptions)->Unit,onQueuedDownload: ()->Unit={}) {
    val screen by model.screen.collectAsStateWithLifecycle()
    val base=LocalContext.current
    val configuration=LocalConfiguration.current
    val localized=remember(base,configuration,screen.language) { AppLanguage.context(base,screen.language) }
    CompositionLocalProvider(LocalAppContext provides localized,LocalConfiguration provides localized.resources.configuration) {
        ZenithContent(model,onDownload,onQueuedDownload)
    }
}

@Composable private fun ZenithContent(model: ZenithViewModel,onDownload: (DownloadOptions)->Unit,onQueuedDownload: ()->Unit) {
    val texts=LocalAppContext.current
    val screen by model.screen.collectAsStateWithLifecycle()
    val jobs by model.jobs.collectAsStateWithLifecycle()
    val engine by model.engine.collectAsStateWithLifecycle()
    var cookies by rememberSaveable { mutableStateOf(false) }
    var newCookie by rememberSaveable { mutableStateOf(false) }
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    fun finishInput() { focus.clearFocus(force=true);keyboard?.hide() }
    fun navigate(page: Int) { finishInput();model.tab(page) }
    BackHandler(screen.tab!=0 && screen.preview==null) { navigate(0) }
    val snackbar=remember { SnackbarHostState() }
    val context=LocalContext.current
    val cookieBrowser=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        model.refreshProfiles()
        if(result.resultCode==android.app.Activity.RESULT_OK) result.data?.getStringExtra("profileId")?.let(model::cookie)
    }
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(model::importCookies)
    }
    // Also cover navigation initiated by a share intent or a queued download.
    LaunchedEffect(screen.tab) { finishInput() }
    LaunchedEffect(screen.message) {
        screen.message?.let { snackbar.showSnackbar(AppLanguage.message(texts,it)); model.dismissMessage() }
    }
    Scaffold(containerColor=Ink,snackbarHost={ SnackbarHost(snackbar) },topBar={
        WorkspaceHeader(screen.tab,jobs.count { it.state in setOf(JobState.QUEUED,JobState.RUNNING,JobState.SAVING) },
            { navigate(0) },{ finishInput();model.library(it) },{ navigate(2) },{ navigate(4) })
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).navigationBarsPadding().imePadding()) {
            AnimatedContent(screen.tab,transitionSpec={
                (fadeIn(tween(170))+slideInHorizontally(tween(190)) { 24 }) togetherWith fadeOut(tween(100))
            },label="workspace-page") { page ->
                when(page) {
                    0 -> HomeScreen(screen,model::url,model::analyze,{ cookies=true })
                    1 -> DownloadsScreen(jobs,model::cancel,model::retry,screen.showHistory) { job,share ->
                        runCatching { openFiles(context,job.files,share) }.onFailure { model.showMessage(texts.getString(R.string.no_opener)) }
                    }
                    3 -> AboutScreen { navigate(2) }
                    4 -> TerminalWorkspace(model,screen,onQueuedDownload)
                    else -> SettingsHub(model,screen,engine,{ cookies=true })
                }
            }
        }
    }
    if(screen.playlistEntries.isNotEmpty()) PlaylistSelectionSheet(screen.playlistEntries,model::dismissPlaylist,submitting=screen.submitting) { indices -> onQueuedDownload();model.enqueuePlaylist(indices) }
    screen.preview?.let { DownloadSheet(it,screen.profiles.firstOrNull { p -> p.id==screen.cookieId }?.host,
        model.app.store.defaultOptions,model::downloadChoices,model::dismissPreview,onDownload,model::loadPlaylist,submitting=screen.submitting,
        analyzing=screen.analyzing,analysisError=screen.analysisError,onRefreshInfo=model::refreshInfo,onCancelAnalysis=model::cancelAnalysis,
        inspectSlow=screen.inspectSlow) }
    if(cookies) ProfileSheet(screen.profiles,screen.cookieId,{ cookies=false },
        { model.cookie(it); cookies=false },model::removeProfile,
        { cookies=false; newCookie=true },
        { cookies=false; importer.launch(arrayOf("text/plain","application/octet-stream")) })
    if(newCookie) CookieUrlDialog({ newCookie=false }) { url ->
        newCookie=false
        cookieBrowser.launch(Intent(context,CookieBrowserActivity::class.java).putExtra("url",url))
    }
}

@Composable private fun HomeScreen(screen: ScreenState,
    onUrl: (String)->Unit,onAnalyze: ()->Unit,onCookies: ()->Unit) {
    val texts=LocalAppContext.current
    val clipboard=LocalClipboardManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    fun analyze() { keyboard?.hide();onAnalyze() }
    Column(Modifier.fillMaxSize().padding(horizontal=24.dp,vertical=24.dp)) {
        Spacer(Modifier.weight(1f))
        OutlinedTextField(screen.url,onUrl,placeholder={ Text("https://…",color=Muted) },
            label={ Text(texts.getString(R.string.seal_video_link)) },
            modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(20.dp),
            leadingIcon={ Icon(Icons.Outlined.Link,null,tint=Muted) },
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri,imeAction=ImeAction.Go),
            keyboardActions=KeyboardActions(onGo={ if(screen.url.isNotBlank() && !screen.playlistLoading) analyze() }),
            trailingIcon={ if(screen.url.isNotEmpty()) IconButton(onClick={ onUrl("") }) { Icon(Icons.Outlined.Close,texts.getString(R.string.clear_link)) } })
        Spacer(Modifier.height(12.dp))
        Surface(onClick=onCookies,color=Panel,shape=RoundedCornerShape(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock,null,Modifier.size(18.dp),tint=Muted)
                Spacer(Modifier.width(12.dp))
                Text(screen.profiles.firstOrNull { it.id==screen.cookieId }?.host ?: texts.getString(R.string.connect_session),
                    Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium)
                Icon(Icons.Outlined.ChevronRight,null,Modifier.size(20.dp),tint=Muted)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(top=32.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            OutlinedButton(onClick={ clipboard.getText()?.text?.takeIf(String::isNotBlank)?.let(onUrl) },
                modifier=Modifier.heightIn(min=56.dp),shape=RoundedCornerShape(18.dp)) {
                Icon(Icons.Outlined.ContentPaste,null,Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp));Text(texts.getString(R.string.paste))
            }
            Button(onClick=::analyze,enabled=screen.url.isNotBlank() && !screen.playlistLoading,
                modifier=Modifier.weight(1f).heightIn(min=56.dp),shape=RoundedCornerShape(18.dp)) {
                Icon(Icons.Outlined.FileDownload,null,Modifier.size(21.dp))
                Spacer(Modifier.width(9.dp));Text(texts.getString(R.string.download))
            }
        }
    }
}

@Composable private fun DownloadsScreen(jobs: List<DownloadJob>,onCancel: (String)->Unit,
    onRetry: (DownloadJob)->Unit,initialHistory: Boolean=false,onOpen: (DownloadJob,Boolean)->Unit) {
    val texts=LocalAppContext.current
    var history by rememberSaveable(initialHistory) { mutableStateOf(initialHistory) }
    val visible=jobs.filter { (it.state in setOf(JobState.COMPLETED,JobState.FAILED,JobState.CANCELLED))==history }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Choices(listOf("queue" to texts.getString(R.string.queue_filter),"history" to texts.getString(R.string.history_filter)),if(history) "history" else "queue") { history=it=="history" } }
        item {
            Text(texts.getString(R.string.files),style=MaterialTheme.typography.headlineMedium,modifier=Modifier.padding(top=12.dp))
        }
        if(visible.isEmpty()) item {
            Surface(color=Panel,shape=CardShape,modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.FileDownload,null,tint=Muted,modifier=Modifier.size(34.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(texts.getString(if(history) R.string.empty_files else R.string.empty_queue),style=MaterialTheme.typography.titleMedium)
                }
            }
        }
        items(visible,key={ it.id }) { job ->
            Surface(color=Panel,shape=CardShape,modifier=Modifier.fillMaxWidth(),border=BorderStroke(1.dp,Hairline.copy(alpha=.7f))) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        AsyncImage(job.thumbnail,null,modifier=Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(PanelRaised))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(job.title,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleMedium)
                            val format=if(job.options.mode==MediaMode.AUDIO) job.options.audioFormat else job.options.container
                            Text(AppLanguage.message(texts,job.options.mode.label)+" · "+format.uppercase(),color=Muted,
                                style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(top=4.dp))
                        }
                    }
                    Spacer(Modifier.height(15.dp))
                    Text(AppLanguage.message(texts,job.detail),color=if(job.state==JobState.COMPLETED) Success else Muted,style=MaterialTheme.typography.bodyMedium)
                    if(job.state in listOf(JobState.QUEUED,JobState.RUNNING,JobState.SAVING)) {
                        Spacer(Modifier.height(12.dp))
                        if(!job.progressKnown || job.state!=JobState.RUNNING)
                            LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),color=Silver,trackColor=Hairline)
                        else LinearProgressIndicator(progress={ job.progress/100f },
                            modifier=Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),color=Silver,trackColor=Hairline)
                        if(job.progressKnown) Text("${job.progress.toInt()}%",color=Muted,style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(top=7.dp))
                    TextButton(onClick={ onCancel(job.id) }) { Text(texts.getString(R.string.cancel_download),color=Muted) }
                    }
                    job.notes.forEach { Text(AppLanguage.message(texts,it),color=Muted,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(top=10.dp)) }
                    if(job.files.isNotEmpty()) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick={ onOpen(job,false) }) { Icon(Icons.Outlined.OpenInNew,null,Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(texts.getString(R.string.open)) }
                        TextButton(onClick={ onOpen(job,true) }) { Icon(Icons.Outlined.Share,null,Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(texts.getString(R.string.share)) }
                    }
                    if(job.state in listOf(JobState.FAILED,JobState.CANCELLED)) TextButton(onClick={ onRetry(job) }) { Text(texts.getString(R.string.retry)) }
                }
            }
        }
    }
}

@Composable fun OptionSwitch(title: String,subtitle: String="",value: Boolean,onChange: (Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=58.dp).toggleable(value=value,role=Role.Switch,onValueChange=onChange).padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end=12.dp)) {
            Text(AppLanguage.message(LocalAppContext.current,title),style=MaterialTheme.typography.bodyLarge)
            if(subtitle.isNotBlank()) Text(AppLanguage.message(LocalAppContext.current,subtitle),color=Muted,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(top=3.dp))
        }
        Switch(value,onCheckedChange=null)
    }
}
@Composable fun Choices(items: List<Pair<String,String>>,selected: String,onSelect: (String)->Unit) {
    FlowRow(modifier=Modifier.selectableGroup(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        items.forEach { (value,label) ->
            val active=selected==value
            val color by animateColorAsState(if(active) Silver else PanelRaised,tween(160),label="choice")
            Surface(modifier=Modifier.clip(RoundedCornerShape(14.dp)).selectable(selected=active,role=Role.RadioButton,onClick={ onSelect(value) }),
                color=color,shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,if(active) Silver else Hairline)) {
                Row(Modifier.heightIn(min=48.dp).padding(horizontal=14.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    if(active) Icon(Icons.Outlined.Check,null,Modifier.size(16.dp),tint=Ink)
                    Text(AppLanguage.message(LocalAppContext.current,label),color=if(active) Ink else Silver,style=MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
@Composable fun OptionPicker(title: String,items: List<Pair<String,String>>,selected: String,onSelect: (String)->Unit) {
    val texts=LocalAppContext.current
    var expanded by remember { mutableStateOf(false) }
    val label=items.firstOrNull { it.first==selected }?.second ?: selected
    ExposedDropdownMenuBox(expanded=expanded,onExpandedChange={ expanded=it }) {
        OutlinedTextField(value=AppLanguage.message(texts,label),onValueChange={},readOnly=true,singleLine=true,
            label={ Text(AppLanguage.message(texts,title)) },
            trailingIcon={ ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier=Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            shape=RoundedCornerShape(16.dp))
        ExposedDropdownMenu(expanded=expanded,onDismissRequest={ expanded=false },containerColor=PanelRaised) {
            items.forEach { (value,name) ->
                DropdownMenuItem(text={ Text(AppLanguage.message(texts,name)) },
                    modifier=Modifier.semantics { this.selected=value==selected },
                    trailingIcon={ if(value==selected) Icon(Icons.Outlined.Check,null,tint=Silver) },
                    onClick={ expanded=false;onSelect(value) })
            }
        }
    }
}
@Composable fun ExpandableSection(title: String,summary: String,content: @Composable ColumnScope.()->Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().animateContentSize(tween(180)).clip(RoundedCornerShape(20.dp)).background(PanelRaised)) {
        Row(Modifier.fillMaxWidth().clickable { expanded=!expanded }.padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(AppLanguage.message(LocalAppContext.current,title),style=MaterialTheme.typography.titleMedium)
                Text(summary,color=Muted,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(top=3.dp))
            }
            Icon(if(expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,null,tint=Muted)
        }
        AnimatedVisibility(expanded,enter=fadeIn(tween(160))+expandVertically(tween(200)),exit=fadeOut(tween(120))+shrinkVertically(tween(180))) {
            Column(Modifier.padding(start=18.dp,end=18.dp,bottom=18.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
        }
    }
}
@Composable fun CompactField(label: String,value: String,onChange: (String)->Unit,numeric: Boolean=false) {
    OutlinedTextField(value,onChange,label={ Text(label) },modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),singleLine=true,
        keyboardOptions=KeyboardOptions(keyboardType=if(numeric) KeyboardType.Number else KeyboardType.Text))
}
private fun openFiles(context: android.content.Context,files: List<String>,share: Boolean) {
    require(files.isNotEmpty())
    val texts=AppLanguage.context(context)
    val uris=files.map(Uri::parse)
    val intent=if(share && uris.size>1)
        Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM,ArrayList(uris))
    else if(share) Intent(Intent.ACTION_SEND).setType(context.contentResolver.getType(uris.first()) ?: "*/*").putExtra(Intent.EXTRA_STREAM,uris.first())
    else Intent(Intent.ACTION_VIEW).setDataAndType(uris.first(),context.contentResolver.getType(uris.first()) ?: "*/*")
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    intent.clipData=ClipData.newUri(context.contentResolver,"ZenithW files",uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
    context.startActivity(if(share) Intent.createChooser(intent,texts.getString(R.string.share_files)) else intent)
}
