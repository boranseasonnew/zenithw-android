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
    val context=LocalContext.current
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
                            Text(preview.uploader.ifBlank { "Medya hazır" },color=Muted,
                                style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(top=5.dp))
                        }
                        IconButton(onClick=onDismiss) { Icon(Icons.Outlined.Close,"Pencereyi kapat") }
                    }
                }
                item {
                    Choices(MediaMode.entries.map { it.name to it.label },o.mode.name) { update(o.copy(mode=MediaMode.valueOf(it))) }
                }
                if(o.mode==MediaMode.VIDEO) item {
                    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text("Kalite",style=MaterialTheme.typography.titleMedium)
                        val heights=preview.heights.take(4)
                        Choices(listOf("0" to "En iyi")+heights.map { it.toString() to "${it}p" },o.height.toString()) {
                            update(o.copy(height=it.toInt()))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Dosya biçimi",style=MaterialTheme.typography.titleMedium)
                        Choices(listOf("mp4" to "MP4","mkv" to "MKV","webm" to "WebM"),o.container) { update(o.copy(container=it)) }
                    }
                }
                if(o.mode==MediaMode.AUDIO) item {
                    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text("Ses biçimi",style=MaterialTheme.typography.titleMedium)
                        Choices(listOf("m4a" to "M4A","mp3" to "MP3","opus" to "Opus","flac" to "FLAC","wav" to "WAV"),o.audioFormat) {
                            update(o.copy(audioFormat=it))
                        }
                        Choices(listOf("0" to "En iyi","320" to "320 kbps","192" to "192 kbps","128" to "128 kbps"),
                            o.audioBitrate.toString()) { update(o.copy(audioBitrate=it.toInt())) }
                    }
                }
                if(o.mode!=MediaMode.SUBTITLES) item {
                    Column {
                        OptionSwitch("Kapak görseli","Desteklenen biçimlerde dosyaya gömülür.",o.embedThumbnail) { update(o.copy(embedThumbnail=it)) }
                        plan.note?.let { Text(it,color=Muted,style=MaterialTheme.typography.bodyMedium) }
                    }
                }
                item {
                    ExpandableSection("Dosya içeriği","Medya bilgileri, kapak ve codec") {
                        OptionSwitch("Medya bilgilerini göm",value=o.embedMetadata) { update(o.copy(embedMetadata=it)) }
                        OptionSwitch("Bölümleri göm",value=o.embedChapters) { update(o.copy(embedChapters=it)) }
                        OptionSwitch("Yükleyici bilgisini ekle",value=o.embedUploader) { update(o.copy(embedUploader=it)) }
                        OptionSwitch("Kapağı ayrıca kaydet",value=o.downloadThumbnail) { update(o.copy(downloadThumbnail=it)) }
                        if(o.downloadThumbnail || plan.write) Choices(listOf("jpg" to "JPG","png" to "PNG","webp" to "WebP"),o.thumbnailFormat) {
                            update(o.copy(thumbnailFormat=it))
                        }
                        if(o.mode==MediaMode.VIDEO) {
                            Text("Video codec",color=Muted)
                            Choices(listOf("auto" to "Otomatik","h264" to "H.264","av1" to "AV1","vp9" to "VP9"),o.codec) { update(o.copy(codec=it)) }
                            Text("Bazı codec ve dosya biçimleri birlikte kullanılamayabilir.",color=Muted,style=MaterialTheme.typography.bodyMedium)
                        }
                        OptionSwitch("Sponsor bölümlerini kaldır","SponsorBlock verisi varsa uygulanır.",o.sponsorBlock) { update(o.copy(sponsorBlock=it)) }
                        if(o.sponsorBlock) CompactField("Kategoriler",o.sponsorCategories,{ update(o.copy(sponsorCategories=it)) })
                    }
                }
                item {
                    ExpandableSection("Altyazılar",if(o.mode==MediaMode.SUBTITLES) "Dil ve dosya biçimi" else "İstersen ekle") {
                        if(o.mode!=MediaMode.SUBTITLES) OptionSwitch("Altyazıları indir",value=o.subtitles) { update(o.copy(subtitles=it)) }
                        OptionSwitch("Otomatik altyazıları da al",value=o.autoSubtitles) { update(o.copy(autoSubtitles=it)) }
                        CompactField("Diller · tr.*,en.*",o.subtitleLanguages,{ update(o.copy(subtitleLanguages=it)) })
                        Choices(listOf("srt" to "SRT","vtt" to "VTT","ass" to "ASS","lrc" to "LRC"),o.subtitleFormat) { update(o.copy(subtitleFormat=it)) }
                        if(o.mode==MediaMode.VIDEO) OptionSwitch("Altyazıyı videoya göm",value=o.embedSubtitles) { update(o.copy(embedSubtitles=it)) }
                    }
                }
                item {
                    ExpandableSection("Bağlantı ve hız",if(o.useAria2) "Aria2c açık" else "Standart indirme") {
                        OptionSwitch("Aria2c kullan","Desteklenen bağlantılarda; hata olursa normal motor denenir.",o.useAria2) { update(o.copy(useAria2=it)) }
                        if(o.useAria2) {
                            Text("Sunucu başına bağlantı",color=Muted)
                            Choices(listOf("1" to "1","2" to "2","4" to "4","8" to "8","16" to "16"),o.ariaConnections.toString()) {
                                update(o.copy(ariaConnections=it.toInt()))
                            }
                        }
                        NumberField("Hız sınırı · KB/sn, 0 = sınırsız",o.speedLimitKbps,0,1000000) { update(o.copy(speedLimitKbps=it)) }
                        NumberField("Paralel parçalar",o.fragments,1,16) { update(o.copy(fragments=it)) }
                        NumberField("Tekrar deneme sayısı",o.retries,0,10) { update(o.copy(retries=it)) }
                        NumberField("Parça tekrar denemeleri",o.fragmentRetries,0,10) { update(o.copy(fragmentRetries=it)) }
                        Choices(listOf("auto" to "Otomatik","ipv4" to "IPv4","ipv6" to "IPv6"),o.networkMode) { update(o.copy(networkMode=it)) }
                        CompactField("Proxy · isteğe bağlı",o.proxy,{ update(o.copy(proxy=it)) })
                        CompactField("User-Agent · isteğe bağlı",o.userAgent,{ update(o.copy(userAgent=it)) })
                        OptionSwitch("Yalnızca ölçülmeyen ağ","Wi-Fi veya başka bir ölçülmeyen bağlantıyı bekler.",o.wifiOnly) { update(o.copy(wifiOnly=it)) }
                    }
                }
                item {
                    ExpandableSection("Diğer seçenekler","Oynatma listesi, zamanlama ve dosya adı") {
                        OptionSwitch("Oynatma listesini indir",value=o.playlist) { update(o.copy(playlist=it)) }
                        if(o.playlist) CompactField("Liste öğeleri · 1:5,8",o.playlistItems,{ update(o.copy(playlistItems=it)) })
                        CompactField("Dosya adı şablonu",o.filenameTemplate,{ update(o.copy(filenameTemplate=it)) })
                        if(o.mode==MediaMode.VIDEO) {
                            NumberField("Özel azami kalite · 0 = en iyi",o.height,0,16384) { update(o.copy(height=it)) }
                            CompactField("Format kimliği · isteğe bağlı",o.selectedFormat,{ update(o.copy(selectedFormat=it)) })
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
                            Text(if(o.scheduledAt>0) DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(o.scheduledAt) else "İndirmeyi zamanla")
                        }
                        if(o.scheduledAt>0) TextButton(onClick={ update(o.copy(scheduledAt=0)) }) { Text("Zamanlamayı kaldır") }
                    }
                }
                cookieHost?.let { item { Text("Oturum: $it",color=Muted,style=MaterialTheme.typography.bodyMedium) } }
                item { Spacer(Modifier.height(6.dp)) }
            }
            HorizontalDivider(color=Hairline,modifier=Modifier.padding(top=12.dp))
            Button(onClick={ onDownload(o) },modifier=Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=14.dp).heightIn(min=58.dp),
                shape=RoundedCornerShape(18.dp)) {
                Icon(Icons.Outlined.FileDownload,null,Modifier.size(20.dp)); Spacer(Modifier.width(10.dp))
                Text(if(o.scheduledAt>System.currentTimeMillis()) "Zamanla" else "İndirmeyi başlat")
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

