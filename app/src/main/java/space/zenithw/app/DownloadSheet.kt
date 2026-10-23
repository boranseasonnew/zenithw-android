@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package space.zenithw.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.json.JSONObject
import java.text.DateFormat
import java.util.Calendar

@Composable fun DownloadSheet(preview: MediaPreview,cookieHost: String?,defaults: DownloadOptions,
    onDefaults: (DownloadOptions)->Unit,onDismiss: ()->Unit,onDownload: (DownloadOptions)->Unit,
    onPlaylistSelect: (DownloadOptions)->Unit,submitting: Boolean=false,
    analyzing: Boolean=false,analysisError: String?=null,onRefreshInfo: (Boolean)->Unit={},onCancelAnalysis: ()->Unit={},inspectSlow: Boolean=false,
    onRendered: (String)->Unit={}) {
    var raw by rememberSaveable(preview.url) { mutableStateOf(defaults.json().toString()) }
    var showFormats by rememberSaveable(preview.url) { mutableStateOf(false) }
    val o=remember(raw) { DownloadOptions.from(JSONObject(raw)) }
    fun update(value: DownloadOptions) { raw=value.json().toString(); onDefaults(value) }
    val texts=LocalAppContext.current
    val context=texts
    val plan=DownloadPolicy.thumbnailPlan(o.mode.name,o.container,o.audioFormat,o.embedThumbnail,o.downloadThumbnail,o.thumbnailFormat)
    val maxHeight=(LocalConfiguration.current.screenHeightDp*.90f).dp
    val density=LocalDensity.current
    // Begin at the visible anchor. Opening the configuration must not wait for
    // Material's hidden-to-expanded entrance animation before showing placeholders.
    val instantSheet=remember(preview.url,density) {
        SheetState(skipPartiallyExpanded=true,initialValue=SheetValue.Expanded,
            positionalThreshold={ with(density) { 56.dp.toPx() } },velocityThreshold={ with(density) { 125.dp.toPx() } })
    }
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=instantSheet,
        sheetGesturesEnabled=false,dragHandle=null,
        containerColor=Panel,contentColor=Silver,shape=RoundedCornerShape(topStart=30.dp,topEnd=30.dp)) {
        Column(Modifier.fillMaxWidth().heightIn(max=maxHeight).fillMaxHeight().onGloballyPositioned {
            onRendered("panel_first_layout")
            val bounds=it.boundsInWindow()
            if(bounds.height>0 && bounds.top<it.findRootCoordinates().size.height) onRendered("panel_visible")
        }) {
            Row(Modifier.padding(start=24.dp,end=12.dp,top=18.dp,bottom=14.dp),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.size(width=120.dp,height=78.dp).clip(RoundedCornerShape(16.dp)).background(PanelRaised),contentAlignment=Alignment.Center) {
                    if(preview.thumbnail!=null)
                        AsyncImage(preview.thumbnail,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop,
                            onSuccess={ onRendered("cover_loaded") })
                    else if(analyzing) PreviewSkeleton(Modifier.fillMaxSize())
                    else Icon(if(analysisError!=null) Icons.Outlined.ErrorOutline else Icons.Outlined.Link,null,tint=Muted)
                    if(preview.duration>0) Surface(color=Ink.copy(alpha=.85f),shape=RoundedCornerShape(6.dp),
                        modifier=Modifier.align(Alignment.BottomEnd).padding(6.dp).onGloballyPositioned { onRendered("duration_drawn") }) {
                        Text(mediaDuration(preview.duration.toLong()),Modifier.padding(horizontal=6.dp,vertical=3.dp),
                            style=MaterialTheme.typography.labelSmall,color=Silver)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    if(preview.title!=preview.url && preview.title.isNotBlank())
                        Text(preview.title,modifier=Modifier.onGloballyPositioned { onRendered("title_drawn") },
                            style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                    else if(analyzing) {
                        PreviewSkeleton(Modifier.fillMaxWidth(.92f).height(18.dp))
                        Spacer(Modifier.height(7.dp));PreviewSkeleton(Modifier.fillMaxWidth(.6f).height(14.dp))
                    } else Text(texts.getString(if(analysisError!=null) R.string.seal_info_unavailable else R.string.seal_configure_download),
                        style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                    if(analyzing && preview.duration<=0) PreviewSkeleton(Modifier.padding(top=8.dp).width(44.dp).height(12.dp))
                    Text(if(preview.metadataReady) preview.uploader.ifBlank { preview.url } else preview.url,
                        color=Muted,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=4.dp))
                    val size=estimatedDownloadBytes(preview,o)
                    if(size>0) Text("≈ ${mediaSize(size)}",color=Muted,style=MaterialTheme.typography.labelMedium,modifier=Modifier.padding(top=6.dp))
                }
                IconButton(onClick=onDismiss) { Icon(Icons.Outlined.Close,texts.getString(R.string.close_window)) }
            }
            if(analyzing || analysisError!=null || !preview.metadataReady) {
                Row(Modifier.fillMaxWidth().padding(start=18.dp,end=18.dp,bottom=8.dp),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically) {
                    if(analyzing) TextButton(onClick=onCancelAnalysis) { Text(texts.getString(R.string.cancel_download)) }
                    else {
                        TextButton(onClick={ onRefreshInfo(false) }) { Text(texts.getString(R.string.retry)) }
                        TextButton(onClick={ onRefreshInfo(true) }) { Text(texts.getString(R.string.seal_slow_retry)) }
                    }
                }
                if(analysisError!=null) Text(analysisError,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall,
                    modifier=Modifier.padding(start=24.dp,end=24.dp,bottom=12.dp))
            }
            HorizontalDivider(color=Hairline)
            LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(horizontal=24.dp,vertical=16.dp),
                verticalArrangement=Arrangement.spacedBy(16.dp)) {
                if(preview.isPlaylist) item {
                    OutlinedButton(onClick={ onPlaylistSelect(o) },modifier=Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.PlaylistPlay,null);Spacer(Modifier.width(10.dp));Text(texts.getString(R.string.playlist_open))
                    }
                }
                item {
                    Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
                        Text(texts.getString(R.string.seal_download_type),style=MaterialTheme.typography.labelLarge,color=Muted)
                        Choices(MediaMode.entries.map { it.name to AppLanguage.message(texts,it.label) },o.mode.name) {
                            update(o.copy(mode=MediaMode.valueOf(it),selectedFormat=""));showFormats=false
                        }
                        if(o.mode!=MediaMode.SUBTITLES) FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected=o.embedThumbnail,onClick={ update(o.copy(embedThumbnail=!o.embedThumbnail)) },
                                label={ Text(texts.getString(R.string.thumbnail)) },leadingIcon={ Icon(Icons.Outlined.Image,null,Modifier.size(17.dp)) })
                            if(o.mode==MediaMode.VIDEO) FilterChip(selected=o.subtitles,onClick={ update(o.copy(subtitles=!o.subtitles)) },
                                label={ Text(texts.getString(R.string.subtitles)) },leadingIcon={ Icon(Icons.Outlined.Subtitles,null,Modifier.size(17.dp)) })
                            FilterChip(selected=o.embedMetadata,onClick={ update(o.copy(embedMetadata=!o.embedMetadata)) },
                                label={ Text(texts.getString(R.string.embed_metadata)) },leadingIcon={ Icon(Icons.Outlined.Info,null,Modifier.size(17.dp)) })
                        }
                    }
                }
                if(o.mode==MediaMode.VIDEO) item {
                    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        val heights=preview.heights.filter { it>0 }.distinct().sortedDescending()
                        val preferred=if(o.height>0 && o.height !in heights) listOf(o.height.toString() to "${o.height}p · ${texts.getString(R.string.preview_preference)}") else emptyList()
                        OptionPicker(texts.getString(R.string.quality),listOf("0" to texts.getString(R.string.best))+preferred+heights.map { it.toString() to "${it}p" },o.height.toString()) {
                            update(o.copy(height=it.toInt(),selectedFormat=""))
                        }
                        Spacer(Modifier.height(4.dp))
                        OptionPicker(texts.getString(R.string.file_format),listOf("mp4" to "MP4","mkv" to "MKV","webm" to "WebM"),o.container) { update(o.copy(container=it)) }
                    }
                }
                if(o.mode==MediaMode.AUDIO) item {
                    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        OptionPicker(texts.getString(R.string.audio_format),listOf("m4a" to "M4A","mp3" to "MP3","opus" to "Opus","flac" to "FLAC","wav" to "WAV"),o.audioFormat) {
                            update(o.copy(audioFormat=it))
                        }
                        OptionPicker(AppLanguage.message(texts,"Ses kalitesi"),listOf("0" to texts.getString(R.string.best),"320" to "320 kbps","256" to "256 kbps","192" to "192 kbps","128" to "128 kbps"),
                            o.audioBitrate.toString()) { update(o.copy(audioBitrate=it.toInt())) }
                    }
                }
                if(o.mode!=MediaMode.SUBTITLES) item {
                    val eligible=preview.formats.filter { if(o.mode==MediaMode.VIDEO) it.hasVideo else it.hasAudio && !it.hasVideo }
                    OutlinedButton(onClick={ showFormats=true },enabled=eligible.isNotEmpty(),modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(16.dp)) {
                        Icon(Icons.Outlined.Tune,null,Modifier.size(19.dp));Spacer(Modifier.width(10.dp))
                        Text(if(o.selectedFormat.isBlank()) texts.getString(R.string.seal_choose_format) else texts.getString(R.string.seal_selected_format,o.selectedFormat),maxLines=1,overflow=TextOverflow.Ellipsis)
                        Spacer(Modifier.weight(1f));Icon(Icons.Outlined.ChevronRight,null,Modifier.size(19.dp))
                    }
                    if(analyzing && eligible.isEmpty()) Text(texts.getString(R.string.preview_formats_loading),color=Muted,
                        style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=6.dp))
                    plan.note?.let { Text(AppLanguage.message(texts,it),color=Muted,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=6.dp)) }
                }
                item {
                    ExpandableSection(texts.getString(R.string.seal_filename_clip),texts.getString(R.string.seal_filename_clip_hint)) {
                        CompactField(texts.getString(R.string.seal_filename_override),o.filenameOverride,{ update(o.copy(filenameOverride=it)) })
                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.weight(1f)) { CompactField(texts.getString(R.string.seal_clip_start),o.clipStart,{ update(o.copy(clipStart=it)) }) }
                            Box(Modifier.weight(1f)) { CompactField(texts.getString(R.string.seal_clip_end),o.clipEnd,{ update(o.copy(clipEnd=it)) }) }
                        }
                        Text(texts.getString(R.string.seal_clip_hint),color=Muted,style=MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    ExpandableSection(texts.getString(R.string.file_content),texts.getString(R.string.file_content_hint)) {
                        OptionSwitch(texts.getString(R.string.embed_metadata),value=o.embedMetadata) { update(o.copy(embedMetadata=it)) }
                        OptionSwitch(texts.getString(R.string.embed_chapters),value=o.embedChapters) { update(o.copy(embedChapters=it)) }
                        OptionSwitch(texts.getString(R.string.embed_uploader),value=o.embedUploader) { update(o.copy(embedUploader=it)) }
                        OptionSwitch(texts.getString(R.string.save_thumbnail),value=o.downloadThumbnail) { update(o.copy(downloadThumbnail=it)) }
                        if(o.downloadThumbnail || plan.write) Choices(listOf("jpg" to "JPG","png" to "PNG","webp" to "WebP"),o.thumbnailFormat) {
                            update(o.copy(thumbnailFormat=it))
                        }
                        if(o.mode==MediaMode.VIDEO) {
                            Text(texts.getString(R.string.video_codec),color=Muted)
                            Choices(listOf("auto" to texts.getString(R.string.automatic),"h264" to "H.264","av1" to "AV1","vp9" to "VP9"),o.codec) { update(o.copy(codec=it)) }
                            Text(texts.getString(R.string.codec_hint),color=Muted,style=MaterialTheme.typography.bodyMedium)
                        }
                        OptionSwitch(texts.getString(R.string.remove_sponsors),texts.getString(R.string.sponsor_hint),o.sponsorBlock) { update(o.copy(sponsorBlock=it)) }
                        if(o.sponsorBlock) CompactField(texts.getString(R.string.categories),o.sponsorCategories,{ update(o.copy(sponsorCategories=it)) })
                    }
                }
                item {
                    ExpandableSection(texts.getString(R.string.subtitles),if(o.mode==MediaMode.SUBTITLES) texts.getString(R.string.subtitle_settings) else texts.getString(R.string.optional_add)) {
                        if(o.mode!=MediaMode.SUBTITLES) OptionSwitch(texts.getString(R.string.download_subtitles),value=o.subtitles) { update(o.copy(subtitles=it)) }
                        OptionSwitch(texts.getString(R.string.auto_subtitles),value=o.autoSubtitles) { update(o.copy(autoSubtitles=it)) }
                        if(preview.subtitleLanguages.isNotEmpty()) {
                            OptionPicker(texts.getString(R.string.seal_available_subtitles),
                                listOf(o.subtitleLanguages to o.subtitleLanguages)+preview.subtitleLanguages.filter { it!=o.subtitleLanguages }.map { it to it },o.subtitleLanguages) {
                                update(o.copy(subtitleLanguages=it))
                            }
                        }
                        CompactField(texts.getString(R.string.subtitle_languages),o.subtitleLanguages,{ update(o.copy(subtitleLanguages=it)) })
                        Choices(listOf("srt" to "SRT","vtt" to "VTT","ass" to "ASS","lrc" to "LRC"),o.subtitleFormat) { update(o.copy(subtitleFormat=it)) }
                        if(o.mode==MediaMode.VIDEO) OptionSwitch(texts.getString(R.string.embed_subtitles),value=o.embedSubtitles) { update(o.copy(embedSubtitles=it)) }
                    }
                }
                item {
                    ExpandableSection(texts.getString(R.string.connection_speed),if(o.useAria2) texts.getString(R.string.aria_enabled) else texts.getString(R.string.standard_download)) {
                        OptionSwitch(texts.getString(R.string.use_aria),texts.getString(R.string.aria_hint),o.useAria2) { update(o.copy(useAria2=it)) }
                        if(o.useAria2) {
                            Text(texts.getString(R.string.server_connections),color=Muted)
                            Choices(listOf("1" to "1","2" to "2","4" to "4","8" to "8","16" to "16"),o.ariaConnections.toString()) {
                                update(o.copy(ariaConnections=it.toInt()))
                            }
                        }
                        NumberField(texts.getString(R.string.speed_limit),o.speedLimitKbps,0,1000000) { update(o.copy(speedLimitKbps=it)) }
                        NumberField(texts.getString(R.string.fragments),o.fragments,1,16) { update(o.copy(fragments=it)) }
                        NumberField(texts.getString(R.string.retries),o.retries,0,10) { update(o.copy(retries=it)) }
                        NumberField(texts.getString(R.string.fragment_retries),o.fragmentRetries,0,10) { update(o.copy(fragmentRetries=it)) }
                        Choices(listOf("auto" to texts.getString(R.string.automatic),"ipv4" to "IPv4","ipv6" to "IPv6"),o.networkMode) { update(o.copy(networkMode=it)) }
                        CompactField(texts.getString(R.string.proxy),o.proxy,{ update(o.copy(proxy=it)) })
                        CompactField(texts.getString(R.string.user_agent),o.userAgent,{ update(o.copy(userAgent=it)) })
                        OptionSwitch(texts.getString(R.string.unmetered),texts.getString(R.string.unmetered_hint),o.wifiOnly) { update(o.copy(wifiOnly=it)) }
                    }
                }
                item {
                    ExpandableSection(texts.getString(R.string.other_options),texts.getString(R.string.other_options_hint)) {
                        OptionSwitch(texts.getString(R.string.playlist),value=o.playlist) { update(o.copy(playlist=it)) }
                        if(o.playlist) CompactField(texts.getString(R.string.playlist_items),o.playlistItems,{ update(o.copy(playlistItems=it)) })
                        CompactField(texts.getString(R.string.filename),o.filenameTemplate,{ update(o.copy(filenameTemplate=it)) })
                        OptionSwitch(texts.getString(R.string.seal_restrict_names),value=o.restrictFilenames) { update(o.copy(restrictFilenames=it)) }
                        NumberField(texts.getString(R.string.seal_max_filesize),o.maxFilesizeMb,0,100000) { update(o.copy(maxFilesizeMb=it)) }
                        CompactField(texts.getString(R.string.seal_format_sort),o.formatSort,{ update(o.copy(formatSort=it)) })
                        CompactField(texts.getString(R.string.seal_custom_arguments),o.customArguments,{ update(o.copy(customArguments=it)) })
                        if(o.mode==MediaMode.VIDEO) {
                            NumberField(texts.getString(R.string.max_quality),o.height,0,16384) { update(o.copy(height=it)) }
                            CompactField(texts.getString(R.string.format_id),o.selectedFormat,{ update(o.copy(selectedFormat=it)) })
                        }
                        OutlinedButton(onClick={
                            val cal=Calendar.getInstance()
                            DatePickerDialog(context,{ _,year,month,day ->
                                cal.set(year,month,day)
                                TimePickerDialog(context,{ _,hour,minute ->
                                    cal.set(Calendar.HOUR_OF_DAY,hour);cal.set(Calendar.MINUTE,minute);cal.set(Calendar.SECOND,0)
                                    update(o.copy(scheduledAt=cal.timeInMillis))
                                },cal.get(Calendar.HOUR_OF_DAY),cal.get(Calendar.MINUTE),true).show()
                            },cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH)).apply {
                                datePicker.minDate=System.currentTimeMillis()
                            }.show()
                        },modifier=Modifier.fillMaxWidth()) {
                            Text(if(o.scheduledAt>0) DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT,texts.resources.configuration.locales[0]).format(o.scheduledAt) else texts.getString(R.string.schedule_download))
                        }
                        if(o.scheduledAt>0) TextButton(onClick={ update(o.copy(scheduledAt=0)) }) { Text(texts.getString(R.string.remove_schedule)) }
                    }
                }
                cookieHost?.let { item { Text(texts.getString(R.string.session_host,it),color=Muted,style=MaterialTheme.typography.bodyMedium) } }
                item { Spacer(Modifier.height(6.dp)) }
            }
            HorizontalDivider(color=Hairline,modifier=Modifier.padding(top=12.dp))
            Button(onClick={ onDownload(o) },enabled=!submitting,modifier=Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=14.dp).heightIn(min=58.dp),
                shape=RoundedCornerShape(18.dp)) {
                if(submitting) CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp,color=Ink)
                else Icon(Icons.Outlined.FileDownload,null,Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(if(o.scheduledAt>System.currentTimeMillis()) texts.getString(R.string.schedule) else texts.getString(R.string.start_download))
            }
        }
    }
    if(showFormats) MediaFormatDialog(preview,o.mode,o.selectedFormat,{ showFormats=false }) { selected ->
        update(o.copy(selectedFormat=selected));showFormats=false
    }
}

@Composable private fun MediaFormatDialog(preview: MediaPreview,mode: MediaMode,selected: String,onDismiss: ()->Unit,onSelect: (String)->Unit) {
    val texts=LocalAppContext.current
    val formats=preview.formats.filter { if(mode==MediaMode.VIDEO) it.hasVideo else it.hasAudio && !it.hasVideo }
    AlertDialog(onDismissRequest=onDismiss,containerColor=Panel,title={ Text(texts.getString(R.string.seal_choose_format)) },
        text={
            LazyColumn(Modifier.fillMaxWidth().heightIn(max=420.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                item { FormatChoice(texts.getString(R.string.automatic),selected.isBlank(),{ onSelect("") }) }
                items(formats.size,key={ formats[it].id }) { index ->
                    val format=formats[index]
                    val value=if(mode==MediaMode.VIDEO && !format.hasAudio) format.id+"+bestaudio/best" else format.id
                    FormatChoice(format.label,selected==value,{ onSelect(value) })
                }
            }
        },confirmButton={ TextButton(onClick=onDismiss) { Text(texts.getString(R.string.close_window)) } })
}

@Composable private fun FormatChoice(label: String,selected: Boolean,onClick: ()->Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
        .background(if(selected) PanelRaised else Panel).selectable(selected=selected,role=Role.RadioButton,onClick=onClick)
        .padding(horizontal=10.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
        RadioButton(selected=selected,onClick=null)
        Spacer(Modifier.width(10.dp))
        Text(label,style=MaterialTheme.typography.bodyMedium)
    }
}

@Composable private fun NumberField(label: String,value: Int,min: Int,max: Int,onChange: (Int)->Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    CompactField(label,text,{ input ->
        if(input.all(Char::isDigit) && input.length<=8) {
            text=input
            input.toIntOrNull()?.let { onChange(it.coerceIn(min,max)) }
        }
    },numeric=true)
}


@Composable private fun PreviewSkeleton(modifier: Modifier=Modifier) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(Silver.copy(alpha=.10f)))
}
