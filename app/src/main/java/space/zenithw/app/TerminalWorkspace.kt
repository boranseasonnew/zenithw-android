@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package space.zenithw.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun TerminalWorkspace(model: ZenithViewModel, screen: ScreenState, onQueuedDownload: () -> Unit) {
    val texts = LocalAppContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = tab, containerColor = Ink, contentColor = Silver) {
            listOf(R.string.terminal_batch, R.string.terminal_commands, R.string.terminal_logs).forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(texts.getString(title)) })
            }
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> BulkDownloadScreen(screen, model::bulkLink, model::addBulkLink, model::removeBulkLink,
                    model::bulkMode, model::url, { model.loadPlaylist() }, { if (model.enqueueBulk()) onQueuedDownload() })
                1 -> CommandComposer(model, screen, onStarted = { onQueuedDownload(); tab = 2 })
                else -> CommandLogs(model)
            }
        }
    }
}

@Composable
private fun CommandComposer(model: ZenithViewModel, screen: ScreenState, onStarted: () -> Unit) {
    val texts = LocalAppContext.current
    val templateStore = remember { CommandTemplateStore(texts) }
    var templates by remember { mutableStateOf(templateStore.load()) }
    var selected by rememberSaveable { mutableStateOf("") }
    var arguments by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf(false) }
    var templateName by rememberSaveable { mutableStateOf("") }
    var templateArguments by rememberSaveable { mutableStateOf("") }
    var validationError by rememberSaveable { mutableStateOf(false) }
    val valid = remember(arguments) { runCatching { parseCustomArguments(arguments) }.isSuccess }
    val current = templates.firstOrNull { it.id == selected }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text(texts.getString(R.string.terminal_commands), style = MaterialTheme.typography.headlineSmall) }
            item { Text(texts.getString(R.string.terminal_arguments_hint), style = MaterialTheme.typography.bodyMedium, color = Muted) }
            item {
                OptionPicker(texts.getString(R.string.terminal_template), listOf("" to texts.getString(R.string.terminal_custom)) + templates.map { it.id to it.name }, selected) { id ->
                    selected = id
                    templates.firstOrNull { it.id == id }?.let { arguments = it.arguments }
                }
            }
            item {
                OutlinedTextField(arguments, { arguments = it.take(8192) }, label = { Text(texts.getString(R.string.terminal_arguments)) },
                    placeholder = { Text("-f bestaudio -x --audio-format mp3") }, textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                    minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    isError = arguments.isNotBlank() && !valid,
                    supportingText = { if (arguments.isNotBlank() && !valid) Text(texts.getString(R.string.terminal_arguments_error)) })
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        templateName = current?.name.orEmpty(); templateArguments = arguments; validationError = false; editing = true
                    }, enabled = valid && (current != null || templates.size < 100)) {
                        Icon(Icons.Outlined.Save, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(texts.getString(if (current == null) R.string.terminal_save_template else R.string.terminal_edit_template))
                    }
                    if (current != null) IconButton(onClick = {
                        templates = templates.filterNot { it.id == current.id }; templateStore.save(templates); selected = ""
                    }) { Icon(Icons.Outlined.DeleteOutline, texts.getString(R.string.terminal_delete_template)) }
                }
            }
            item {
                ExpandableSection(texts.getString(R.string.terminal_supported), texts.getString(R.string.terminal_supported_summary)) {
                    Text(texts.getString(R.string.terminal_supported_flags), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    Text(texts.getString(R.string.terminal_managed_options), color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
            items(screen.bulkLinks, key = { it.id }) { link ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(link.url, { model.bulkLink(link.id, it) }, label = { Text(texts.getString(R.string.bulk_link_number, screen.bulkLinks.indexOf(link) + 1)) },
                        placeholder = { Text("https://…") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp))
                    IconButton(onClick = { model.removeBulkLink(link.id) }, enabled = screen.bulkLinks.size > 1 || link.url.isNotEmpty()) {
                        Icon(Icons.Outlined.Close, texts.getString(R.string.bulk_remove_link, screen.bulkLinks.indexOf(link) + 1))
                    }
                }
            }
            item {
                OutlinedButton(onClick = model::addBulkLink, enabled = screen.bulkLinks.size < 50, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text(texts.getString(R.string.bulk_add_row))
                }
            }
        }
        HorizontalDivider(color = Hairline)
        Button(onClick = {
            if (model.enqueueCommand(screen.bulkLinks.joinToString("\n") { it.url }, arguments)) onStarted()
        }, enabled = valid && screen.bulkLinks.any { it.url.isNotBlank() } && !screen.submitting,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp).heightIn(min = 52.dp)) {
            Icon(Icons.Outlined.Terminal, null); Spacer(Modifier.width(8.dp)); Text(texts.getString(R.string.terminal_start))
        }
    }
    if (editing) AlertDialog(onDismissRequest = { editing = false }, title = { Text(texts.getString(R.string.terminal_save_template)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(templateName, { templateName = it.take(80) }, label = { Text(texts.getString(R.string.terminal_template_name)) }, singleLine = true)
            OutlinedTextField(templateArguments, { templateArguments = it.take(8192); validationError = false },
                label = { Text(texts.getString(R.string.terminal_arguments)) }, minLines = 3, maxLines = 6, isError = validationError,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace))
            if (validationError) Text(texts.getString(R.string.terminal_arguments_error), color = MaterialTheme.colorScheme.error)
        } }, confirmButton = { TextButton(onClick = {
            if (runCatching { validateCustomArguments(templateArguments) }.isFailure) validationError = true
            else {
                val replacement = CommandTemplate(id = current?.id ?: java.util.UUID.randomUUID().toString(), name = templateName.trim(), arguments = templateArguments.trim())
                templates = templates.filterNot { it.id == replacement.id } + replacement
                templateStore.save(templates); selected = replacement.id; arguments = replacement.arguments; editing = false
            }
        }, enabled = templateName.isNotBlank()) { Text(texts.getString(R.string.terminal_save)) } },
        dismissButton = { TextButton(onClick = { editing = false }) { Text(texts.getString(R.string.cancel_download)) } })
}

@Composable
private fun CommandLogs(model: ZenithViewModel) {
    val texts = LocalAppContext.current
    val jobs by model.jobs.collectAsState()
    val sessionLogs by model.logs.collectAsState()
    val taskLogs by model.app.store.taskLogs.collectAsState()
    var selectedId by rememberSaveable { mutableStateOf("") }
    var follow by rememberSaveable { mutableStateOf(true) }
    val commands = jobs.filter { it.options.customArguments.isNotBlank() }.sortedByDescending { it.createdAt }
    val selected = jobs.firstOrNull { it.id == selectedId }
    val lines = if (selectedId.isEmpty()) sessionLogs else taskLogs[selectedId].orEmpty()
    val list = rememberLazyListState()
    LaunchedEffect(selectedId, lines.size, lines.lastOrNull(), follow) {
        if (follow && lines.isNotEmpty()) list.scrollToItem(lines.lastIndex)
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionPicker(texts.getString(R.string.terminal_log_source), listOf("" to texts.getString(R.string.terminal_session)) + commands.map { it.id to it.title }, selectedId) { selectedId = it }
            if (selected != null) {
                Text(AppLanguage.message(texts, selected.detail), color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (selected.state == JobState.RUNNING || selected.state == JobState.SAVING) {
                    if (selected.progressKnown) LinearProgressIndicator(progress = { (selected.progress / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(texts.getString(R.string.terminal_follow), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Switch(checked = follow, onCheckedChange = { follow = it })
                IconButton(onClick = {
                    (texts.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("ZenithW", lines.joinToString("\n")))
                }, enabled = lines.isNotEmpty()) { Icon(Icons.Outlined.ContentCopy, texts.getString(R.string.terminal_copy)) }
                if (selected != null) {
                    if (selected.state in setOf(JobState.QUEUED, JobState.RUNNING, JobState.SAVING)) IconButton(onClick = { model.cancel(selected.id) }) {
                        Icon(Icons.Outlined.Stop, texts.getString(R.string.cancel_download))
                    } else IconButton(onClick = { model.retry(selected) }) { Icon(Icons.Outlined.Refresh, texts.getString(R.string.retry)) }
                }
                if (selectedId.isEmpty()) IconButton(onClick = model::clearLogs, enabled = lines.isNotEmpty()) {
                    Icon(Icons.Outlined.DeleteOutline, texts.getString(R.string.terminal_clear))
                }
            }
        }
        HorizontalDivider(color = Hairline)
        if (lines.isEmpty()) Box(Modifier.fillMaxWidth().weight(1f).padding(24.dp), contentAlignment = Alignment.Center) {
            Text(texts.getString(R.string.terminal_empty_log), color = Muted)
        } else SelectionContainer(Modifier.weight(1f)) {
            LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(lines.size) { index -> Text(lines[index], fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
            }
        }
        if (commands.isNotEmpty()) Text(texts.getString(R.string.terminal_task_count, commands.size), Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = Muted, style = MaterialTheme.typography.labelSmall)
    }
}
