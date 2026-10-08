@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package space.zenithw.app

import android.content.ClipData
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage

private val CardShape=RoundedCornerShape(26.dp)

@Composable fun ZenithApp(model: ZenithViewModel,onDownload: (DownloadOptions)->Unit) {
    val screen by model.screen.collectAsStateWithLifecycle()
    val jobs by model.jobs.collectAsStateWithLifecycle()
    val engine by model.engine.collectAsStateWithLifecycle()
    var cookies by rememberSaveable { mutableStateOf(false) }
    var newCookie by rememberSaveable { mutableStateOf(false) }
    val snackbar=remember { SnackbarHostState() }
    val context=LocalContext.current
    val cookieBrowser=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        model.refreshProfiles()
        if(result.resultCode==android.app.Activity.RESULT_OK) result.data?.getStringExtra("profileId")?.let(model::cookie)
    }
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(model::importCookies)
    }
    LaunchedEffect(screen.message) {
        screen.message?.let { snackbar.showSnackbar(it); model.dismissMessage() }
    }
    val keyboardOpen=WindowInsets.ime.getBottom(LocalDensity.current)>0
    Scaffold(containerColor=Ink,snackbarHost={ SnackbarHost(snackbar) },
        bottomBar={ if(!keyboardOpen) Dock(screen.tab,model::tab) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
            when(screen.tab) {
                0 -> HomeScreen(screen,model::url,model::analyze)
                1 -> DownloadsScreen(jobs,model::cancel,model::retry) { job,share ->
                    runCatching { openFiles(context,job.files,share) }.onFailure {
                        model.showMessage("Dosyayı açabilecek bir uygulama bulunamadı.")
                    }
                }
                else -> SettingsScreen(screen,engine,model::automatic,model::channel,
                    model::updateEngine,{ cookies=true })
            }
        }
    }
    screen.preview?.let { DownloadSheet(it,screen.profiles.firstOrNull { p -> p.id==screen.cookieId }?.host,
        model.app.store.defaultOptions,model::defaults,model::dismissPreview,onDownload) }
    if(cookies) ProfileSheet(screen.profiles,screen.cookieId,{ cookies=false },
        { model.cookie(it); cookies=false },model::removeProfile,
        { cookies=false; newCookie=true },
        { cookies=false; importer.launch(arrayOf("text/plain","application/octet-stream")) })
    if(newCookie) CookieUrlDialog({ newCookie=false }) { url ->
        newCookie=false
        cookieBrowser.launch(Intent(context,CookieBrowserActivity::class.java).putExtra("url",url))
    }
}

@Composable private fun BrandRow() {
    Image(painterResource(R.drawable.zenithw),"Zenith",Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)))
}

@Composable private fun HomeScreen(screen: ScreenState,onUrl: (String)->Unit,onAnalyze: ()->Unit) {
    val clipboard=LocalClipboardManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    fun analyze() { keyboard?.hide(); onAnalyze() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        BrandRow()
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(screen.url,onUrl,placeholder={ Text("URL",color=Muted) },
            modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(16.dp),
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri,imeAction=ImeAction.Go),
            keyboardActions=KeyboardActions(onGo={ if(screen.url.isNotBlank() && !screen.analyzing) analyze() }),
            trailingIcon={ if(screen.url.isNotEmpty()) IconButton(onClick={ onUrl("") }) {
                Icon(Icons.Outlined.Close,"Bağlantıyı temizle")
            } })
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick={ clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let(onUrl) },
                modifier=Modifier.weight(1f).heightIn(min=52.dp),shape=RoundedCornerShape(16.dp)) {
                Icon(Icons.Outlined.ContentPaste,null,Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp)); Text("Yapıştır")
            }
            Button(onClick=::analyze,enabled=screen.url.isNotBlank() && !screen.analyzing,
                modifier=Modifier.weight(1f).heightIn(min=52.dp),shape=RoundedCornerShape(16.dp)) {
                if(screen.analyzing) CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp,color=Ink)
                else Icon(Icons.Outlined.FileDownload,null,Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp)); Text("İndir")
            }
        }
    }
}

@Composable private fun Dock(selected: Int,onSelect: (Int)->Unit) {
    val names=listOf("İndir","Dosyalar","Ayarlar")
    val icons=listOf(Icons.Outlined.Link,Icons.Outlined.FolderOpen,Icons.Outlined.Tune)
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=22.dp,vertical=12.dp)) {
        Surface(color=Panel,shape=RoundedCornerShape(25.dp),border=BorderStroke(1.dp,Hairline)) {
            Row(Modifier.fillMaxWidth().padding(7.dp),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                names.forEachIndexed { index,label ->
                    val active=selected==index
                    val color by animateColorAsState(if(active) Silver else Color.Transparent,tween(180),label="dock")
                    Surface(onClick={ onSelect(index) },modifier=Modifier.weight(1f).semantics { this.selected=active;role=Role.Tab },
                        color=color,shape=RoundedCornerShape(19.dp)) {
                        Column(Modifier.heightIn(min=58.dp).padding(7.dp),
                            horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                            Icon(icons[index],null,Modifier.size(20.dp),tint=if(active) Ink else Muted)
                            Spacer(Modifier.height(3.dp))
                            Text(label,color=if(active) Ink else Muted,style=MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun DownloadsScreen(jobs: List<DownloadJob>,onCancel: (String)->Unit,
    onRetry: (DownloadJob)->Unit,onOpen: (DownloadJob,Boolean)->Unit) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(24.dp),
        verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { BrandRow() }
        item {
            Text("Dosyalar",style=MaterialTheme.typography.headlineMedium,modifier=Modifier.padding(top=12.dp))
        }
        if(jobs.isEmpty()) item {
            Surface(color=Panel,shape=CardShape,modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.FileDownload,null,tint=Muted,modifier=Modifier.size(34.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Henüz dosya yok",style=MaterialTheme.typography.titleMedium)
                }
            }
        }
        items(jobs,key={ it.id }) { job ->
            Surface(color=Panel,shape=CardShape,modifier=Modifier.fillMaxWidth(),border=BorderStroke(1.dp,Hairline.copy(alpha=.7f))) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        AsyncImage(job.thumbnail,null,modifier=Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(PanelRaised))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(job.title,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleMedium)
                            val format=if(job.options.mode==MediaMode.AUDIO) job.options.audioFormat else job.options.container
                            Text(job.options.mode.label+" · "+format.uppercase(),color=Muted,
                                style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(top=4.dp))
                        }
                    }
                    Spacer(Modifier.height(15.dp))
                    Text(job.detail,color=if(job.state==JobState.COMPLETED) Success else Muted,style=MaterialTheme.typography.bodyMedium)
                    if(job.state in listOf(JobState.QUEUED,JobState.RUNNING,JobState.SAVING)) {
                        Spacer(Modifier.height(12.dp))
                        if(job.progress<=0 || job.state!=JobState.RUNNING)
                            LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),color=Silver,trackColor=Hairline)
                        else LinearProgressIndicator(progress={ job.progress/100f },
                            modifier=Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),color=Silver,trackColor=Hairline)
                        TextButton(onClick={ onCancel(job.id) }) { Text("İptal et",color=Muted) }
                    }
                    job.notes.forEach { Text(it,color=Muted,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(top=10.dp)) }
                    if(job.state==JobState.COMPLETED) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick={ onOpen(job,false) }) { Icon(Icons.Outlined.OpenInNew,null,Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Aç") }
                        TextButton(onClick={ onOpen(job,true) }) { Icon(Icons.Outlined.Share,null,Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Paylaş") }
                    }
                    if(job.state in listOf(JobState.FAILED,JobState.CANCELLED)) TextButton(onClick={ onRetry(job) }) { Text("Tekrar dene") }
                }
            }
        }
    }
}

@Composable private fun SettingsScreen(screen: ScreenState,engine: EngineStatus,onAuto: (Boolean)->Unit,
    onChannel: (String)->Unit,onUpdate: ()->Unit,onCookies: ()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        BrandRow()
        Text("Ayarlar",style=MaterialTheme.typography.headlineMedium,modifier=Modifier.padding(top=12.dp))
        Surface(color=Panel,shape=CardShape) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("İndirme motoru",style=MaterialTheme.typography.titleLarge)
                Text(engine.version.ifBlank { "yt-dlp hazırlanıyor" },color=Muted,style=MaterialTheme.typography.bodyMedium)
                OptionSwitch("Otomatik güncelle",value=screen.autoUpdate,onChange=onAuto)
                Choices(listOf("stable" to "Kararlı","nightly" to "Nightly"),screen.channel,onChannel)
                engine.warning?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium) }
                OutlinedButton(onClick=onUpdate,enabled=!engine.updating,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)) {
                    if(engine.updating) { CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp); Spacer(Modifier.width(8.dp)) }
                    Text(if(engine.updating) "Kontrol ediliyor" else "Şimdi kontrol et")
                }
            }
        }
        Surface(onClick=onCookies,color=Panel,shape=CardShape) {
            Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock,null,tint=Muted); Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Bağlı oturumlar",style=MaterialTheme.typography.titleMedium)
                    Text("${screen.profiles.size} oturum · cihazda şifreli saklanır",color=Muted,style=MaterialTheme.typography.bodyMedium)
                }
                Icon(Icons.Outlined.ChevronRight,null,tint=Muted)
            }
        }
        Text("${BuildConfig.VERSION_NAME}",style=MaterialTheme.typography.titleMedium)
    }
}
@Composable fun OptionSwitch(title: String,subtitle: String="",value: Boolean,onChange: (Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=58.dp).clickable { onChange(!value) }.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end=12.dp)) {
            Text(title,style=MaterialTheme.typography.bodyLarge)
            if(subtitle.isNotBlank()) Text(subtitle,color=Muted,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(top=3.dp))
        }
        Switch(value,onChange)
    }
}
@Composable fun Choices(items: List<Pair<String,String>>,selected: String,onSelect: (String)->Unit) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        items.forEach { (value,label) ->
            val active=selected==value
            val color by animateColorAsState(if(active) Silver else PanelRaised,tween(160),label="choice")
            Surface(onClick={ onSelect(value) },modifier=Modifier.semantics { this.selected=active;role=Role.RadioButton },
                color=color,shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,if(active) Silver else Hairline)) {
                Text(label,Modifier.heightIn(min=46.dp).padding(horizontal=15.dp,vertical=13.dp),
                    color=if(active) Ink else Muted,style=MaterialTheme.typography.labelLarge)
            }
        }
    }
}
@Composable fun ExpandableSection(title: String,summary: String,content: @Composable ColumnScope.()->Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(PanelRaised)) {
        Row(Modifier.fillMaxWidth().clickable { expanded=!expanded }.padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title,style=MaterialTheme.typography.titleMedium)
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
    val uris=files.map(Uri::parse)
    val intent=if(share && uris.size>1)
        Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM,ArrayList(uris))
    else if(share) Intent(Intent.ACTION_SEND).setType(context.contentResolver.getType(uris.first()) ?: "*/*").putExtra(Intent.EXTRA_STREAM,uris.first())
    else Intent(Intent.ACTION_VIEW).setDataAndType(uris.first(),context.contentResolver.getType(uris.first()) ?: "*/*")
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    intent.clipData=ClipData.newUri(context.contentResolver,"ZenithW files",uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
    context.startActivity(if(share) Intent.createChooser(intent,"Dosyaları paylaş") else intent)
}
