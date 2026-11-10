@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package space.zenithw.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.json.JSONObject
import java.text.DateFormat
import java.util.Calendar

@Composable
fun DownloadSheet(
    preview: MediaPreview,
    cookieHost: String?,
    defaults: DownloadOptions,
    onDefaults: (DownloadOptions) -> Unit,
    onDismiss: () -> Unit,
    onDownload: (DownloadOptions) -> Unit,
    onPlaylistSelect: (DownloadOptions) -> Unit,
    submitting: Boolean = false,
    analyzing: Boolean = false,
    analysisError: String? = null,
    onRefreshInfo: (Boolean) -> Unit = {},
    onCancelAnalysis: () -> Unit = {},
    inspectSlow: Boolean = false,
    onRendered: (String) -> Unit = {},
    onSaveProfile: (DownloadOptions) -> Unit = {},
    smartSuggested: Boolean = false,
    lastUsed: DownloadOptions = defaults,
) {
    var raw by rememberSaveable(preview.url) { mutableStateOf(defaults.json().toString()) }
    var showFormats by rememberSaveable(preview.url) { mutableStateOf(false) }
    var advancedExpanded by rememberSaveable(preview.url) { mutableStateOf(false) }
    var suggestionActive by rememberSaveable(preview.url) { mutableStateOf(smartSuggested) }
    val o = remember(raw) { DownloadOptions.from(JSONObject(raw)) }
    fun update(value: DownloadOptions) {
        suggestionActive = false
        raw = value.json().toString()
        onDefaults(value)
    }
    fun changeMode(mode: MediaMode) {
        if (mode != o.mode) update(o.copy(mode = mode, selectedFormat = ""))
    }
    val submissionOptions = if (o.mode == MediaMode.SUBTITLES) {
        o.copy(clipStart = "", clipEnd = "", clipRanges = emptyList(), exactCuts = false, mergeClips = false)
    } else o

    val texts = LocalAppContext.current
    val context = texts
    val plan = DownloadPolicy.thumbnailPlan(
        o.mode.name, o.container, o.audioFormat, o.embedThumbnail, o.downloadThumbnail, o.thumbnailFormat,
    )
    val maxHeight = (LocalConfiguration.current.screenHeightDp * .92f).dp
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = null,
        containerColor = Panel,
        contentColor = Silver,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        contentWindowInsets = { WindowInsets.safeDrawing },
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = maxHeight).onGloballyPositioned {
                onRendered("panel_first_layout")
                val bounds = it.boundsInWindow()
                if (bounds.height > 0 && bounds.top < it.findRootCoordinates().size.height) {
                    onRendered("panel_visible")
                }
            }
        ) {
            LazyColumn(
                Modifier.weight(1f, fill = false),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item("header") { DownloadPreviewHeader(preview, o, analyzing, analysisError, onDismiss, onRendered) }
                item("type") {
                    DownloadSegments(
                        MediaMode.entries.map { it to AppLanguage.message(texts, it.label) },
                        o.mode,
                        onSelect = ::changeMode,
                    )
                }
                item("format") {
                    DownloadFormatOptions(preview, o, analyzing, { showFormats = true }, ::update)
                }
                if (o.mode != MediaMode.SUBTITLES) item("quick-options") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = o.embedThumbnail,
                            onClick = { update(o.copy(embedThumbnail = !o.embedThumbnail)) },
                            label = { Text(texts.getString(R.string.download_sheet_cover)) },
                        )
                        FilterChip(
                            selected = o.subtitles,
                            onClick = { update(o.copy(subtitles = !o.subtitles)) },
                            label = { Text(texts.getString(R.string.subtitle)) },
                        )
                        FilterChip(
                            selected = o.embedMetadata,
                            onClick = { update(o.copy(embedMetadata = !o.embedMetadata)) },
                            label = { Text(texts.getString(R.string.download_sheet_metadata)) },
                        )
                    }
                    plan.note?.let {
                        Text(AppLanguage.message(texts, it), color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (preview.isPlaylist) item("playlist") {
                    OutlinedButton(
                        onClick = { onPlaylistSelect(submissionOptions) },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.Outlined.PlaylistPlay, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(texts.getString(R.string.playlist_open))
                    }
                }
                if (analyzing || analysisError != null || !preview.metadataReady) item("analysis") {
                    if (analyzing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                texts.getString(if (inspectSlow) R.string.seal_finding_media_slow else R.string.seal_finding_media),
                                Modifier.weight(1f),
                                color = Muted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            TextButton(onClick = onCancelAnalysis) { Text(texts.getString(R.string.download_sheet_stop_analysis)) }
                        }
                    } else {
                        if (analysisError != null) {
                            Text(analysisError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { onRefreshInfo(false) }) { Text(texts.getString(R.string.retry)) }
                            TextButton(onClick = { onRefreshInfo(true) }) { Text(texts.getString(R.string.seal_slow_retry)) }
                        }
                    }
                }
                item("advanced") {
                    DownloadAdvancedToggle(advancedExpanded) { advancedExpanded = it }
                }
                if (advancedExpanded) {
                    item("presets") {
                        if (suggestionActive) {
                            Text(texts.getString(R.string.feature_suggested), style = MaterialTheme.typography.bodySmall, color = Muted)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { changeMode(MediaMode.VIDEO) }) {
                                Text(texts.getString(R.string.feature_quick_video))
                            }
                            TextButton(onClick = { changeMode(MediaMode.AUDIO) }) {
                                Text(texts.getString(R.string.feature_quick_audio))
                            }
                            TextButton(onClick = { update(lastUsed) }) {
                                Text(texts.getString(R.string.feature_last_preset))
                            }
                            TextButton(onClick = { onSaveProfile(o) }) {
                                Text(texts.getString(R.string.feature_save_profile))
                            }
                        }
                    }
                    if(preview.duration>0 && !preview.isPlaylist && o.mode!=MediaMode.SUBTITLES) item("trim") {
                        DownloadAdvancedGroup(texts.getString(R.string.feature_trim),texts.getString(R.string.feature_keyframes)) {
                            var range by remember(preview.url,preview.duration) { mutableStateOf(0f..preview.duration.toFloat()) }
                            RangeSlider(value=range,onValueChange={ range=it },valueRange=0f..preview.duration.toFloat())
                            Text("${mediaDuration(range.start.toLong())} – ${mediaDuration(range.endInclusive.toLong())}")
                            TextButton(onClick={ if(o.clipRanges.size<20 && range.endInclusive>range.start) update(o.copy(clipRanges=o.clipRanges+ClipRange(range.start.toDouble(),range.endInclusive.toDouble()),clipStart="",clipEnd="")) }) { Text(texts.getString(R.string.bulk_add_row)) }
                            o.clipRanges.forEachIndexed { index,clip ->
                                TextButton(onClick={ update(o.copy(clipRanges=o.clipRanges.filterIndexed { i,_ -> i!=index })) }) { Text("${index+1} · ${mediaDuration(clip.start.toLong())} – ${mediaDuration(clip.end.toLong())} ×") }
                            }
                            OptionSwitch(texts.getString(R.string.feature_exact_cuts),value=o.exactCuts) { update(o.copy(exactCuts=it)) }
                            OptionSwitch(texts.getString(R.string.feature_merge_clips),value=o.mergeClips) { update(o.copy(mergeClips=it)) }
                        }
                    }
                    if (preview.chapters.isNotEmpty() && !preview.isPlaylist && o.mode != MediaMode.SUBTITLES) {
                        item("chapter-heading") {
                            DownloadAdvancedHeading(texts.getString(R.string.feature_chapters), preview.chapters.size.toString())
                        }
                        items(preview.chapters.size, key = { "chapter-$it" }) { index ->
                            val chapter = preview.chapters[index]
                            val clip = ClipRange(chapter.start, chapter.end)
                            OptionSwitch(
                                chapter.title,
                                "${mediaDuration(chapter.start.toLong())} – ${mediaDuration(chapter.end.toLong())}",
                                clip in o.clipRanges,
                            ) { selected ->
                                if (!selected || o.clipRanges.size < 20) {
                                    val ranges = if (selected) (o.clipRanges + clip).distinct() else o.clipRanges - clip
                                    update(o.copy(clipStart = "", clipEnd = "", clipRanges = ranges))
                                }
                            }
                        }
                    }
                    item("seal_filename_clip") {
                        val heading = if (o.mode == MediaMode.SUBTITLES) R.string.filename else R.string.seal_filename_clip
                        DownloadAdvancedGroup(texts.getString(heading), texts.getString(R.string.seal_filename_clip_hint)) {
                            CompactField(texts.getString(R.string.seal_filename_override),o.filenameOverride,{ update(o.copy(filenameOverride=it)) })
                            if (o.mode != MediaMode.SUBTITLES) {
                                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                    Box(Modifier.weight(1f)) { CompactField(texts.getString(R.string.seal_clip_start),o.clipStart,{ update(o.copy(clipStart=it)) }) }
                                    Box(Modifier.weight(1f)) { CompactField(texts.getString(R.string.seal_clip_end),o.clipEnd,{ update(o.copy(clipEnd=it)) }) }
                                }
                                Text(texts.getString(R.string.seal_clip_hint),color=Muted,style=MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    item("file_content") {
                        DownloadAdvancedGroup(texts.getString(R.string.file_content),texts.getString(R.string.file_content_hint)) {
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
                    item("subtitles") {
                        DownloadAdvancedGroup(texts.getString(R.string.subtitles),if(o.mode==MediaMode.SUBTITLES) texts.getString(R.string.subtitle_settings) else texts.getString(R.string.optional_add)) {
                            if(o.mode!=MediaMode.SUBTITLES) OptionSwitch(texts.getString(R.string.download_subtitles),value=o.subtitles) { update(o.copy(subtitles=it)) }
                            OptionSwitch(texts.getString(R.string.auto_subtitles),value=o.autoSubtitles) { update(o.copy(autoSubtitles=it)) }
                            if(preview.subtitleLanguages.isNotEmpty()) {
                                OptionPicker(texts.getString(R.string.seal_available_subtitles),
                                    listOf(o.subtitleLanguages to o.subtitleLanguages)+preview.subtitleLanguages.filter { it!=o.subtitleLanguages }.map { it to it },o.subtitleLanguages) {
                                    update(o.copy(subtitleLanguages=it))
                                }
                            }
                            CompactField(texts.getString(R.string.subtitle_languages),o.subtitleLanguages,{ update(o.copy(subtitleLanguages=it)) })
                            Choices(subtitleFormats,o.subtitleFormat) { update(o.copy(subtitleFormat=it)) }
                            if(o.mode==MediaMode.VIDEO) OptionSwitch(texts.getString(R.string.embed_subtitles),value=o.embedSubtitles) { update(o.copy(embedSubtitles=it)) }
                        }
                    }
                    item("connection_speed") {
                        DownloadAdvancedGroup(texts.getString(R.string.connection_speed),if(o.useAria2) texts.getString(R.string.aria_enabled) else texts.getString(R.string.standard_download)) {
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
                            OptionSwitch(texts.getString(R.string.feature_unmetered),value=o.unmeteredOnly) { update(o.copy(unmeteredOnly=it)) }
                            OptionSwitch(texts.getString(R.string.feature_charging),value=o.chargingOnly) { update(o.copy(chargingOnly=it)) }
                            OptionSwitch(texts.getString(R.string.unmetered),texts.getString(R.string.unmetered_hint),o.wifiOnly) { update(o.copy(wifiOnly=it)) }
                        }
                    }
                    item("other_options") {
                        DownloadAdvancedGroup(texts.getString(R.string.other_options),texts.getString(R.string.other_options_hint)) {
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

                    cookieHost?.let { host ->
                        item("session") {
                            Text(texts.getString(R.string.session_host, host), color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            HorizontalDivider(color = Hairline)
            DownloadSheetActions(o.scheduledAt > System.currentTimeMillis(), submitting, onDismiss) {
                onDownload(submissionOptions)
            }
        }
    }
    if (showFormats) {
        MediaFormatDialog(preview, o.mode, o.selectedFormat, { showFormats = false }) { selected ->
            update(o.copy(selectedFormat = selected))
            showFormats = false
        }
    }
}

@Composable
private fun DownloadPreviewHeader(
    preview: MediaPreview,
    options: DownloadOptions,
    analyzing: Boolean,
    analysisError: String?,
    onDismiss: () -> Unit,
    onRendered: (String) -> Unit,
) {
    val texts = LocalAppContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(width = 64.dp, height = 44.dp).clip(RoundedCornerShape(10.dp)).background(PanelRaised),
            contentAlignment = Alignment.Center,
        ) {
            if (analyzing) PreviewSkeleton(Modifier.fillMaxSize())
            if (preview.thumbnail != null) {
                AsyncImage(
                    preview.thumbnail, null, Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onSuccess = { onRendered("cover_loaded") },
                )
            } else if (!analyzing) {
                Icon(if (analysisError != null) Icons.Outlined.ErrorOutline else Icons.Outlined.Link, null, tint = Muted)
            }
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            if (preview.title != preview.url && preview.title.isNotBlank()) {
                Text(
                    preview.title,
                    modifier = Modifier.onGloballyPositioned { onRendered("title_drawn") },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (analyzing) {
                PreviewSkeleton(Modifier.fillMaxWidth(.9f).height(14.dp))
                Spacer(Modifier.height(5.dp))
                PreviewSkeleton(Modifier.fillMaxWidth(.55f).height(12.dp))
            } else {
                Text(
                    texts.getString(if (analysisError != null) R.string.seal_info_unavailable else R.string.seal_configure_download),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val duration = preview.duration.takeIf { it > 0 }?.let { mediaDuration(it.toLong()) }
            val size = estimatedDownloadBytes(preview, options).takeIf { it > 0 }?.let { "≈ ${mediaSize(it)}" }
            val detail = listOfNotNull(duration, size, preview.uploader.takeIf(String::isNotBlank)).joinToString(" · ")
            if (detail.isNotEmpty()) {
                Text(
                    detail,
                    modifier = Modifier.onGloballyPositioned { if (duration != null) onRendered("duration_drawn") },
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (analyzing) {
                Spacer(Modifier.height(4.dp))
                PreviewSkeleton(Modifier.width(40.dp).height(10.dp))
            } else {
                Text(preview.url, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onDismiss, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
            Icon(Icons.Outlined.Close, texts.getString(R.string.close_window), Modifier.size(20.dp))
        }
    }
}

@Composable
private fun DownloadFormatOptions(
    preview: MediaPreview,
    options: DownloadOptions,
    analyzing: Boolean,
    onShowFormats: () -> Unit,
    onChange: (DownloadOptions) -> Unit,
) {
    val texts = LocalAppContext.current
    val best = "0" to texts.getString(R.string.best)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (options.mode) {
            MediaMode.VIDEO -> {
                val available = preview.heights.filter { it > 0 }.distinct().sortedDescending()
                val heights = if (!preview.metadataReady && available.isEmpty()) listOf(2160, 1440, 1080, 720, 480, 360) else available
                val preferred = listOf(options.height).filter { it > 0 && it !in heights }
                DownloadPickerPair(
                    first = { modifier ->
                        DownloadPicker(
                            texts.getString(R.string.quality), listOf(best) + (preferred + heights).map { it.toString() to "${it}p" },
                            options.height.toString(), modifier,
                        ) { onChange(options.copy(height = it.toInt(), selectedFormat = "")) }
                    },
                    second = { modifier ->
                        DownloadPicker(
                            texts.getString(R.string.file_format), listOf("mp4" to "MP4", "mkv" to "MKV", "webm" to "WebM"),
                            options.container, modifier,
                        ) { onChange(options.copy(container = it)) }
                    },
                )
            }
            MediaMode.AUDIO -> DownloadPickerPair(
                first = { modifier ->
                    DownloadPicker(
                        texts.getString(R.string.audio_format),
                        listOf("m4a" to "M4A", "mp3" to "MP3", "opus" to "Opus", "flac" to "FLAC", "wav" to "WAV"),
                        options.audioFormat, modifier,
                    ) { onChange(options.copy(audioFormat = it)) }
                },
                second = { modifier ->
                    DownloadPicker(
                        texts.getString(R.string.download_sheet_audio_quality),
                        listOf(best) + listOf(320, 256, 192, 128).map { it.toString() to "$it kbps" },
                        options.audioBitrate.toString(), modifier,
                    ) { onChange(options.copy(audioBitrate = it.toInt())) }
                },
            )
            MediaMode.SUBTITLES -> DownloadPicker(
                texts.getString(R.string.download_sheet_subtitle_format),
                subtitleFormats,
                options.subtitleFormat,
                Modifier.fillMaxWidth(),
            ) { onChange(options.copy(subtitleFormat = it)) }
        }
        if (options.mode != MediaMode.SUBTITLES) {
            val custom = options.selectedFormat.isNotBlank()
            val canChoose = preview.formats.any { it.matchesMode(options.mode) }
            DownloadSegments(
                listOf(
                    false to texts.getString(R.string.automatic),
                    true to texts.getString(R.string.download_sheet_custom),
                ),
                selectedValue = custom,
                isEnabled = { !it || canChoose || custom },
            ) { useCustom ->
                if (useCustom) onShowFormats()
                else if (custom) onChange(options.copy(selectedFormat = ""))
            }
            if (custom) {
                Text(
                    texts.getString(R.string.seal_selected_format, options.selectedFormat),
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (analyzing && !canChoose) {
                Text(texts.getString(R.string.preview_formats_loading), color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private val subtitleFormats = listOf("srt" to "SRT", "vtt" to "VTT", "ass" to "ASS", "lrc" to "LRC")

private fun MediaFormat.matchesMode(mode: MediaMode): Boolean = when (mode) {
    MediaMode.VIDEO -> hasVideo
    MediaMode.AUDIO -> hasAudio && !hasVideo
    MediaMode.SUBTITLES -> false
}

@Composable
private fun DownloadPickerPair(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 270.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                first(Modifier.fillMaxWidth())
                second(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                first(Modifier.weight(1f))
                second(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DownloadPicker(
    title: String,
    choices: List<Pair<String, String>>,
    value: String,
    modifier: Modifier,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).focusable(),
            shape = shape,
            color = PanelRaised,
            border = BorderStroke(1.dp, Hairline),
        ) {
            Row(
                Modifier.heightIn(min = 48.dp).padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = Muted, style = MaterialTheme.typography.labelSmall)
                    Text(choices.firstOrNull { it.first == value }?.second ?: value, style = MaterialTheme.typography.labelLarge)
                }
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Muted,
                )
            }
        }
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = PanelRaised) {
            choices.forEach { (id, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    modifier = Modifier.semantics { selected = id == value },
                    onClick = { expanded = false; onSelect(id) },
                    trailingIcon = { if (id == value) Icon(Icons.Outlined.Check, null) },
                )
            }
        }
    }
}

@Composable
private fun <T> DownloadSegments(
    values: List<Pair<T, String>>,
    selectedValue: T,
    isEnabled: (T) -> Boolean = { true },
    onSelect: (T) -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().selectableGroup().clip(shape).background(PanelRaised),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        values.forEach { (value, label) ->
            val selected = value == selectedValue
            val enabled = isEnabled(value)
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp).selectable(
                    selected = selected,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = { onSelect(value) },
                ).padding(3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 36.dp).clip(RoundedCornerShape(9.dp))
                        .background(if (selected) Silver else PanelRaised)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = when {
                            selected -> Ink
                            enabled -> Silver
                            else -> Muted
                        },
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadAdvancedToggle(expanded: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button) { onChange(!expanded) }
            .semantics {
                if (expanded) collapse { onChange(false); true }
                else expand { onChange(true); true }
            }.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            LocalAppContext.current.getString(R.string.download_sheet_advanced),
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
        )
        Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = Muted)
    }
}

@Composable
private fun DownloadAdvancedGroup(title: String, summary: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DownloadAdvancedHeading(title, summary)
        content()
    }
}

@Composable
private fun DownloadAdvancedHeading(title: String, summary: String) {
    Column {
        HorizontalDivider(color = Hairline)
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(summary, color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DownloadSheetActions(
    scheduled: Boolean,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
) {
    val texts = LocalAppContext.current
    val shape = RoundedCornerShape(12.dp)
    val padding = PaddingValues(horizontal = 8.dp, vertical = 5.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            shape = shape,
            contentPadding = padding,
        ) {
            Text(texts.getString(R.string.cancel_notification), textAlign = TextAlign.Center)
        }
        Button(
            onClick = onDownload,
            enabled = !submitting,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            shape = shape,
            contentPadding = padding,
        ) {
            if (submitting) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Muted)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                texts.getString(if (scheduled) R.string.schedule else R.string.download),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable private fun MediaFormatDialog(preview: MediaPreview,mode: MediaMode,selected: String,onDismiss: ()->Unit,onSelect: (String)->Unit) {
    val texts=LocalAppContext.current
    val formats = preview.formats.filter { it.matchesMode(mode) }
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
