@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package space.zenithw.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun WorkspaceHeader(page: Int,active: Int,onHome: ()->Unit,onLibrary: (Boolean)->Unit,
    onSettings: ()->Unit,onBulk: ()->Unit) {
    val texts=LocalAppContext.current
    Surface(color=Ink) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            if(page!=0) IconButton(onClick=onHome) { Icon(Icons.Outlined.ArrowBack,texts.getString(R.string.workspace_home)) }
            Column(Modifier.weight(1f).padding(start=if(page==0) 8.dp else 0.dp)) {
                Text("Zenith",style=MaterialTheme.typography.titleLarge)
                Text(BuildConfig.VERSION_NAME,color=Muted,style=MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick={ onLibrary(false) }) {
                BadgedBox(badge={ if(active>0) Badge(containerColor=Silver,contentColor=Ink) { Text(active.toString()) } }) {
                    Icon(Icons.Outlined.FolderOpen,texts.getString(R.string.workspace_library))
                }
            }
            IconButton(onClick=onBulk) {
                Icon(Icons.Outlined.Terminal,texts.getString(R.string.terminal_workspace),tint=if(page==4) Silver else Muted)
            }
            IconButton(onClick=onSettings) { Icon(Icons.Outlined.Settings,texts.getString(R.string.settings),tint=if(page==2) Silver else Muted) }
        }
    }
}

@Composable
fun BulkDownloadScreen(screen: ScreenState,onLink: (String,String)->Unit,onAdd: ()->Unit,onRemove: (String)->Unit,
    onMode: (MediaMode)->Unit,onPlaylistUrl: (String)->Unit,onPlaylist: ()->Unit,onSubmit: ()->Unit) {
    val texts=LocalAppContext.current
    val keyboard=LocalSoftwareKeyboardController.current
    val listState=rememberLazyListState()
    var previousCount by remember { mutableIntStateOf(screen.bulkLinks.size) }
    val active=screen.tab==4
    LaunchedEffect(active,screen.bulkLinks.size) {
        val added=screen.bulkLinks.size>previousCount
        previousCount=screen.bulkLinks.size
        if(active && added) {
            // Adding a row reveals it. Only an explicit tap starts text input;
            // delayed focus requests can reopen the IME after page navigation.
            listState.animateScrollToItem(screen.bulkLinks.size+1)
        }
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f),state=listState,contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item { Text(texts.getString(R.string.bulk_title),style=MaterialTheme.typography.headlineMedium) }
            item {
                Choices(listOf("VIDEO" to texts.getString(R.string.video),"AUDIO" to texts.getString(R.string.audio)),screen.bulkMode.name) { onMode(MediaMode.valueOf(it)) }
            }
            items(screen.bulkLinks,key={ it.id }) { row ->
                val index=screen.bulkLinks.indexOf(row)+1
                Row(verticalAlignment=Alignment.CenterVertically) {
                    OutlinedTextField(row.url,{ onLink(row.id,it) },label={ Text(texts.getString(R.string.bulk_link_number,index)) },
                        placeholder={ Text("https://…") },singleLine=true,modifier=Modifier.weight(1f),shape=RoundedCornerShape(16.dp),
                        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri,imeAction=ImeAction.Done),
                        keyboardActions=KeyboardActions(onDone={ keyboard?.hide() }))
                    IconButton(onClick={ onRemove(row.id) },enabled=screen.bulkLinks.size>1 || row.url.isNotEmpty()) {
                        Icon(Icons.Outlined.Close,texts.getString(R.string.bulk_remove_link,index),tint=Muted)
                    }
                }
            }
            item {
                OutlinedButton(onClick=onAdd,enabled=screen.bulkLinks.size<50,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp),shape=RoundedCornerShape(16.dp)) {
                    Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(8.dp));Text(texts.getString(R.string.bulk_add_row))
                }
            }
            item {
                ExpandableSection(texts.getString(R.string.playlist_title),texts.getString(R.string.playlist_open)) {
                    OutlinedTextField(screen.url,onPlaylistUrl,label={ Text(texts.getString(R.string.playlist_link)) },singleLine=true,
                        modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),
                        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri,imeAction=ImeAction.Go),
                        keyboardActions=KeyboardActions(onGo={ if(screen.url.isNotBlank() && !screen.playlistLoading) { keyboard?.hide();onPlaylist() } }))
                    OutlinedButton(onClick={ keyboard?.hide();onPlaylist() },enabled=screen.url.isNotBlank() && !screen.playlistLoading,modifier=Modifier.fillMaxWidth()) {
                        if(screen.playlistLoading) CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp)
                        else Icon(Icons.Outlined.PlaylistPlay,null)
                        Spacer(Modifier.width(8.dp));Text(texts.getString(if(screen.playlistLoading) R.string.playlist_loading else R.string.playlist_open))
                    }
                }
            }
        }
        HorizontalDivider(color=Hairline)
        Button(onClick={ keyboard?.hide();onSubmit() },enabled=screen.bulkLinks.any { it.url.isNotBlank() } && !screen.submitting,
            modifier=Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=14.dp).heightIn(min=54.dp),shape=RoundedCornerShape(17.dp)) {
            if(screen.submitting) CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp,color=Ink)
            else Icon(Icons.Outlined.FileDownload,null)
            Spacer(Modifier.width(9.dp));Text(texts.getString(R.string.bulk_submit))
        }
    }
}

@Composable
fun PlaylistSelectionSheet(entries: List<PlaylistEntry>,onDismiss: ()->Unit,submitting: Boolean=false,onSubmit: (Set<Int>)->Unit) {
    val texts=LocalAppContext.current
    var selected by rememberSaveable(entries) { mutableStateOf(entries.map { it.index }) }
    val height=(LocalConfiguration.current.screenHeightDp*.86f).dp
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        containerColor=Panel,contentColor=Silver) {
        Column(Modifier.fillMaxWidth().heightIn(max=height)) {
            Row(Modifier.padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(texts.getString(R.string.playlist_title),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                IconButton(onClick=onDismiss) { Icon(Icons.Outlined.Close,texts.getString(R.string.close_window)) }
            }
            Text(texts.getString(R.string.playlist_limit),Modifier.padding(horizontal=24.dp),color=Muted,style=MaterialTheme.typography.bodySmall)
            Row(Modifier.padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(onClick={ selected=entries.map { it.index } }) { Text(texts.getString(R.string.playlist_all)) }
                TextButton(onClick={ selected=emptyList() }) { Text(texts.getString(R.string.playlist_none)) }
            }
            LazyColumn(Modifier.weight(1f,fill=false),contentPadding=PaddingValues(horizontal=20.dp,vertical=6.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(entries) { entry ->
                    val checked=entry.index in selected
                    Surface(color=if(checked) PanelRaised else Panel,shape=RoundedCornerShape(15.dp),border=BorderStroke(1.dp,if(checked) Hairline else Panel)) {
                        Row(Modifier.fillMaxWidth().clickable { selected=if(checked) selected-entry.index else selected+entry.index }.padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
                            Checkbox(checked,onCheckedChange={ value -> selected=if(value) selected+entry.index else selected-entry.index })
                            Text("${entry.index}",color=Muted,style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(end=10.dp))
                            Text(entry.title,Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            HorizontalDivider(color=Hairline)
            Text(texts.getString(R.string.playlist_selected,selected.size),Modifier.padding(horizontal=24.dp,vertical=10.dp),color=Muted)
            Button(onClick={ onSubmit(selected.toSet()) },enabled=selected.isNotEmpty() && !submitting,modifier=Modifier.fillMaxWidth().padding(horizontal=24.dp).padding(bottom=20.dp).heightIn(min=54.dp),shape=RoundedCornerShape(17.dp)) {
                Icon(Icons.Outlined.FileDownload,null);Spacer(Modifier.width(9.dp));Text(texts.getString(R.string.playlist_download))
            }
        }
    }
}
