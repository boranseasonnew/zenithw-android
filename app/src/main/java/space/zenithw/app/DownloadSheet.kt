@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package space.zenithw.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.json.JSONObject
import java.text.DateFormat
import java.util.Calendar

@Composable fun DownloadSheet(preview: MediaPreview,cookieHost: String?,defaults: DownloadOptions,
    onDefaults: (DownloadOptions)->Unit,onDismiss: ()->Unit,onDownload: (DownloadOptions)->Unit) {
    var raw by rememberSaveable(preview.url) { mutableStateOf(defaults.json().toString()) }
    val o=remember(raw) { DownloadOptions.from(JSONObject(raw)) }
    fun update(value: DownloadOptions) { raw=value.json().toString(); onDefaults(value) }
    val texts=LocalAppContext.current
    val context=texts
    val plan=DownloadPolicy.thumbnailPlan(o.mode.name,o.container,o.audioFormat,o.embedThumbnail,o.downloadThumbnail,o.thumbnailFormat)
    val maxHeight=(LocalConfiguration.current.screenHeightDp*.90f).dp
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        containerColor=Panel,contentColor=Silver,shape=RoundedCornerShape(topStart=30.dp,topEnd=30.dp)) {
        Column(Modifier.fillMaxWidth().heightIn(max=maxHeight).imePadding()) {
            LazyColumn(Modifier.weight(1f,fill=false),contentPadding=PaddingValues(horizontal=24.dp),
                verticalArrangement=Arrangement.spacedBy(20.dp)) {
                item {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        AsyncImage(preview.thumbnail,null,Modifier.size(68.dp).clip(RoundedCornerShape(16.dp)))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(preview.title,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                            Text(preview.uploader.ifBlank { texts.getString(R.string.media_ready) },color=Muted,
                                style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(top=5.dp))
                        }
                        IconButton(onClick=onDismiss) { Icon(Icons.Outlined.Close,texts.getString(R.string.close_window)) }
                    }
                }
                item {
                    Choices(MediaMode.entries.map { it.name to AppLanguage.message(texts,it.label) },o.mode.name) { update(o.copy(mode=MediaMode.valueOf(it))) }
                }
                if(o.mode==MediaMode.VIDEO) item {
                    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text(texts.getString(R.string.quality),style=MaterialTheme.typography.titleMedium)
                        val heights=preview.heights.take(4)
                        Choices(listOf("0" to texts.getString(R.string.best))+heights.map { it.toString() to "${it}p" },o.height.toString()) {
                            update(o.copy(height=it.toInt()))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(texts.getString(R.string.file_format),style=MaterialTheme.typography.titleMedium)
                        Choices(listOf("mp4" to "MP4","mkv" to "MKV","webm" to "WebM"),o.container) { update(o.copy(container=it)) }
                    }
                }
                if(o.mode==MediaMode.AUDIO) item {
                    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text(texts.getString(R.string.audio_format),style=MaterialTheme.typography.titleMedium)
                        Choices(listOf("m4a" to "M4A","mp3" to "MP3","opus" to "Opus","flac" to "FLAC","wav" to "WAV"),o.audioFormat) {
                            update(o.copy(audioFormat=it))
                        }
                        Choices(listOf("0" to texts.getString(R.string.best),"320" to "320 kbps","192" to "192 kbps","128" to "128 kbps"),
                            o.audioBitrate.toString()) { update(o.copy(audioBitrate=it.toInt())) }
                    }
                }
                if(o.mode!=MediaMode.SUBTITLES) item {
                    Column {
                        OptionSwitch(texts.getString(R.string.thumbnail),texts.getString(R.string.thumbnail_hint),o.embedThumbnail) { update(o.copy(embedThumbnail=it)) }
                        plan.note?.let { Text(AppLanguage.message(texts,it),color=Muted,style=MaterialTheme.typography.bodyMedium) }
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
            Button(onClick={ onDownload(o) },modifier=Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=14.dp).heightIn(min=58.dp),
                shape=RoundedCornerShape(18.dp)) {
                Icon(Icons.Outlined.FileDownload,null,Modifier.size(20.dp)); Spacer(Modifier.width(10.dp))
                Text(if(o.scheduledAt>System.currentTimeMillis()) texts.getString(R.string.schedule) else texts.getString(R.string.start_download))
            }
        }
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

