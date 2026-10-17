@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package space.zenithw.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun WorkspaceHeader(page: Int,active: Int,onHome: ()->Unit,onLibrary: (Boolean)->Unit,
    onSettings: ()->Unit,onAbout: ()->Unit) {
    val texts=LocalAppContext.current
    var menu by remember { mutableStateOf(false) }
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
            Box {
                IconButton(onClick={ menu=true }) { Icon(Icons.Outlined.Settings,texts.getString(R.string.workspace_menu)) }
                DropdownMenu(expanded=menu,onDismissRequest={ menu=false },containerColor=Panel) {
                    DropdownMenuItem(text={ Text(texts.getString(R.string.workspace_home)) },leadingIcon={ Icon(Icons.Outlined.Add,null) },onClick={ menu=false;onHome() })
                    DropdownMenuItem(text={ Text(texts.getString(R.string.queue_filter)) },leadingIcon={ Icon(Icons.Outlined.Queue,null) },onClick={ menu=false;onLibrary(false) })
                    DropdownMenuItem(text={ Text(texts.getString(R.string.history_filter)) },leadingIcon={ Icon(Icons.Outlined.History,null) },onClick={ menu=false;onLibrary(true) })
                    HorizontalDivider(color=Hairline)
                    DropdownMenuItem(text={ Text(texts.getString(R.string.settings)) },leadingIcon={ Icon(Icons.Outlined.Tune,null) },onClick={ menu=false;onSettings() })
                    DropdownMenuItem(text={ Text(texts.getString(R.string.workspace_about)) },leadingIcon={ Icon(Icons.Outlined.Info,null) },onClick={ menu=false;onAbout() })
                }
            }
        }
    }
}

@Composable
fun BulkLinkSheet(defaults: DownloadOptions,onDismiss: ()->Unit,onSubmit: (String,DownloadOptions)->Unit) {
    val texts=LocalAppContext.current
    var links by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(if(defaults.mode==MediaMode.AUDIO) "AUDIO" else "VIDEO") }
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        containerColor=Panel,contentColor=Silver) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal=24.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(texts.getString(R.string.bulk_title),Modifier.weight(1f),style=MaterialTheme.typography.headlineMedium)
                IconButton(onClick=onDismiss) { Icon(Icons.Outlined.Close,texts.getString(R.string.close_window)) }
            }
            Text(texts.getString(R.string.bulk_description),color=Muted,style=MaterialTheme.typography.bodyMedium)
            OutlinedTextField(links,{ links=it.take(120000) },label={ Text(texts.getString(R.string.bulk_links)) },
                minLines=4,maxLines=7,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp))
            Choices(listOf("VIDEO" to texts.getString(R.string.video),"AUDIO" to texts.getString(R.string.audio)),mode) { mode=it }
            Text(texts.getString(R.string.format_defaults),color=Muted,style=MaterialTheme.typography.bodySmall)
            Button(onClick={ onSubmit(links,defaults.copy(mode=MediaMode.valueOf(mode))) },enabled=links.isNotBlank(),
                modifier=Modifier.fillMaxWidth().heightIn(min=54.dp),shape=RoundedCornerShape(17.dp)) {
                Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(9.dp));Text(texts.getString(R.string.bulk_submit))
            }
        }
    }
}

@Composable
fun PlaylistSelectionSheet(entries: List<PlaylistEntry>,onDismiss: ()->Unit,onSubmit: (Set<Int>)->Unit) {
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
            Button(onClick={ onSubmit(selected.toSet()) },enabled=selected.isNotEmpty(),modifier=Modifier.fillMaxWidth().padding(horizontal=24.dp).padding(bottom=20.dp).heightIn(min=54.dp),shape=RoundedCornerShape(17.dp)) {
                Icon(Icons.Outlined.FileDownload,null);Spacer(Modifier.width(9.dp));Text(texts.getString(R.string.playlist_download))
            }
        }
    }
}
