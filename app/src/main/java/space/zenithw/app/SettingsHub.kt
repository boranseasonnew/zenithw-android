@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package space.zenithw.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private data class SettingsCategory(val key: String,val title: String,val detail: String,val icon: ImageVector)
private val categories=listOf(
    SettingsCategory("language","Dil","Uygulama dili",Icons.Outlined.Language),
    SettingsCategory("engine","İndirme motoru","Otomatik güncelleme ve sürüm kanalı",Icons.Outlined.Memory),
    SettingsCategory("cookies","Tarayıcı oturumları","Cihazda saklanan çerezler",Icons.Outlined.Lock),
    SettingsCategory("video","Video ve ses","Kalite, dosya biçimi, codec",Icons.Outlined.Tune),
    SettingsCategory("content","Dosya içeriği","Kapak, medya bilgisi ve bölümler",Icons.Outlined.Image),
    SettingsCategory("subtitles","Altyazılar","Dil, otomatik altyazı ve gömme",Icons.Outlined.Subtitles),
    SettingsCategory("sponsor","SponsorBlock","Hangi bölümlere ne yapılacağını seç",Icons.Outlined.AutoAwesome),
    SettingsCategory("aria","Aria2c","Harici indirici ve bağlantı sayısı",Icons.Outlined.Speed),
    SettingsCategory("network","Bağlantı ve hız","Hız sınırı, ağ, denemeler ve bekleme",Icons.Outlined.Wifi),
    SettingsCategory("playlist","Oynatma listeleri","Aralık, sıra ve hata davranışı",Icons.Outlined.PlaylistPlay),
    SettingsCategory("files","Dosyalar ve arşiv","Dosya adı, açıklama ve tekrarları atlama",Icons.Outlined.Folder),
    SettingsCategory("presets","Hazır profiller","Tek dokunuşla günlük kullanım veya müzik",Icons.Outlined.Bookmarks),
    SettingsCategory("terminal","İşlem günlüğü","Motor ve indirme akışını izle",Icons.Outlined.Terminal)
)

@Composable fun SettingsHub(model: ZenithViewModel,screen: ScreenState,engine: EngineStatus,onCookies: ()->Unit) {
    val texts=LocalAppContext.current
    var active by rememberSaveable { mutableStateOf<String?>(null) }
    val logs by model.logs.collectAsStateWithLifecycle()
    val o=screen.defaults
    fun update(next: DownloadOptions)=model.defaults(next)
    BackHandler(active!=null) { active=null }
    AnimatedContent(active,transitionSpec={ fadeIn(tween(160)) togetherWith fadeOut(tween(100)) },label="settings-page") { category ->
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    if(category!=null) {
                        IconButton(onClick={ active=null }) { Icon(Icons.Outlined.ArrowBack,AppLanguage.message(texts,"Ayarlara dön")) }
                        Spacer(Modifier.width(6.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(AppLanguage.message(texts,if(category==null) "Ayarlar" else categories.first { it.key==category }.title),style=MaterialTheme.typography.headlineMedium)
                        Text(if(category==null) "Zenith · ${BuildConfig.VERSION_NAME}" else AppLanguage.message(texts,"Otomatik kaydedilir"),color=Muted,style=MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if(category==null) {
                items(categories) { group ->
                    Surface(onClick={ if(group.key=="cookies")onCookies() else active=group.key },
                        shape=RoundedCornerShape(22.dp),color=Panel) {
                        Row(Modifier.fillMaxWidth().padding(19.dp),verticalAlignment=Alignment.CenterVertically) {
                            Surface(shape=RoundedCornerShape(14.dp),color=PanelRaised) {
                                Icon(group.icon,null,tint=Silver,modifier=Modifier.padding(12.dp).size(22.dp))
                            }
                            Spacer(Modifier.width(15.dp))
                            Column(Modifier.weight(1f)) {
                                Text(AppLanguage.message(texts,group.title),style=MaterialTheme.typography.titleMedium)
                                Text(AppLanguage.message(texts,group.detail),color=Muted,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(top=4.dp))
                            }
                            Icon(Icons.Outlined.ChevronRight,null,tint=Muted)
                        }
                    }
                }
                item { Text(AppLanguage.message(texts,"Seçimlerin sonraki indirmelerde hazır gelir. İndirme penceresinde yalnızca o dosya için değiştirebilirsin."),color=Muted,style=MaterialTheme.typography.bodyMedium) }
            } else if(category=="terminal") {
                item {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(AppLanguage.message(texts,"{count} kayıt · bu oturum").replace("{count}",logs.size.toString()),color=Muted,modifier=Modifier.weight(1f))
                        OutlinedButton(onClick=model::clearLogs) { Text(AppLanguage.message(texts,"Temizle")) }
                    }
                    Text(AppLanguage.message(texts,"İşlem günlüğü. Bağlantılar gizlenir; burada komut yazılmaz."),color=Muted,style=MaterialTheme.typography.bodyMedium)
                }
                if(logs.isEmpty()) item { Text(AppLanguage.message(texts,"Motor ve indirme hareketleri burada görünür."),color=Muted) }
                items(logs.asReversed()) { line ->
                    Surface(shape=RoundedCornerShape(12.dp),color=Panel) {
                        SelectionContainer { Text(line,Modifier.fillMaxWidth().padding(12.dp),fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall) }
                    }
                }
            } else item {
                Surface(shape=RoundedCornerShape(24.dp),color=Panel) {
                    Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        when(category) {
                            "language" -> Choices(AppLanguage.choices,screen.language,model::language)
                            "engine" -> {
                                Text(engine.version.ifBlank { "yt-dlp hazırlanıyor" },style=MaterialTheme.typography.titleMedium)
                                OptionSwitch("Açılışta otomatik güncelle","İndirmeyi bekletmeden arka planda kontrol eder.",screen.autoUpdate,model::automatic)
                                Pick("Sürüm kanalı",listOf("stable" to "Kararlı","nightly" to "Nightly"),screen.channel,model::channel)
                                engine.warning?.let {
                                    Text(AppLanguage.message(texts,it),color=MaterialTheme.colorScheme.error)
                                    TextButton(onClick={ active="terminal" }) { Text(AppLanguage.message(texts,"İşlem günlüğü")) }
                                }
                                Button(onClick=model::updateEngine,enabled=!engine.updating,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)) {
                                    Text(AppLanguage.message(texts,if(engine.updating) "Kontrol ediliyor…" else "Şimdi güncelle"))
                                }
                            }
                            "video" -> {
                                Pick("Varsayılan indirme",MediaMode.entries.map { it.name to it.label },o.mode.name) { update(o.copy(mode=MediaMode.valueOf(it))) }
                                Pick("Video kalitesi",listOf(0,480,720,1080,1440,2160).map { it.toString() to if(it==0) "En iyi" else "${it}p" },o.height.toString()) { update(o.copy(height=it.toInt())) }
                                Pick("Video dosyası",listOf("mp4" to "MP4","mkv" to "MKV","webm" to "WebM"),o.container) { update(o.copy(container=it)) }
                                Pick("Video codec",listOf("auto" to "Otomatik","h264" to "H.264","av1" to "AV1","vp9" to "VP9"),o.codec) { update(o.copy(codec=it)) }
                                Pick("Ses dosyası",listOf("m4a" to "M4A","mp3" to "MP3","opus" to "Opus","flac" to "FLAC","wav" to "WAV"),o.audioFormat) { update(o.copy(audioFormat=it)) }
                                Pick("Ses kalitesi",listOf(0,128,192,256,320).map { it.toString() to if(it==0) "En iyi" else "$it kbps" },o.audioBitrate.toString()) { update(o.copy(audioBitrate=it.toInt())) }
                            }
                            "content" -> {
                                OptionSwitch("Medya bilgilerini göm",value=o.embedMetadata) { update(o.copy(embedMetadata=it)) }
                                OptionSwitch("Bölümleri göm",value=o.embedChapters) { update(o.copy(embedChapters=it)) }
                                OptionSwitch("Yükleyici bilgisini ekle",value=o.embedUploader) { update(o.copy(embedUploader=it)) }
                                OptionSwitch("Kapak görselini göm","Desteklenmeyen biçimlerde ayrı kapak kaydedilir.",o.embedThumbnail) { update(o.copy(embedThumbnail=it)) }
                                OptionSwitch("Kapağı ayrıca kaydet",value=o.downloadThumbnail) { update(o.copy(downloadThumbnail=it)) }
                                Pick("Kapak dosyası",listOf("jpg" to "JPG","png" to "PNG","webp" to "WebP"),o.thumbnailFormat) { update(o.copy(thumbnailFormat=it)) }
                                OptionSwitch("Kaynak dosyaları sakla","Dönüştürmeden önceki dosyaları da tutar.",o.keepOriginal) { update(o.copy(keepOriginal=it)) }
                            }
                            "subtitles" -> {
                                OptionSwitch("Altyazıları indir",value=o.subtitles) { update(o.copy(subtitles=it)) }
                                OptionSwitch("Otomatik altyazıları ekle",value=o.autoSubtitles) { update(o.copy(autoSubtitles=it)) }
                                OptionSwitch("Videoya göm",value=o.embedSubtitles) { update(o.copy(embedSubtitles=it)) }
                                Pick("Dil grubu",listOf("tr.*,en.*" to "TR + EN","en.*" to "English","de.*,en.*" to "DE + EN","ru.*,en.*" to "RU + EN","all,-live_chat" to "Tüm diller"),o.subtitleLanguages) { update(o.copy(subtitleLanguages=it)) }
                                Pick("Dosya biçimi",listOf("srt" to "SRT","vtt" to "VTT","ass" to "ASS","lrc" to "LRC"),o.subtitleFormat) { update(o.copy(subtitleFormat=it)) }
                            }
                            "sponsor" -> {
                                OptionSwitch("SponsorBlock","Kaynakta bölüm verisi varsa uygulanır.",o.sponsorBlock) { update(o.copy(sponsorBlock=it)) }
                                Pick("Seçili bölümler",listOf("remove" to "Videodan çıkar","mark" to "Bölüm olarak işaretle"),o.sponsorAction) { update(o.copy(sponsorAction=it)) }
                                Text(AppLanguage.message(texts,"Kategoriler"),style=MaterialTheme.typography.titleMedium)
                                val labels=listOf("sponsor" to "Sponsor","selfpromo" to "Tanıtım","interaction" to "Abone / beğeni","intro" to "Giriş","outro" to "Kapanış","preview" to "Önizleme","filler" to "Boş bölümler","music_offtopic" to "Müzik dışı")
                                labels.forEach { (key,label) ->
                                    val selected=o.sponsorCategories.split(',').filter { it.isNotBlank() }.toSet()
                                    OptionSwitch(label,value=key in selected) { value ->
                                        val next=if(value)selected+key else selected-key
                                        if(next.isNotEmpty())update(o.copy(sponsorCategories=next.joinToString(",")))
                                    }
                                }
                            }
                            "aria" -> {
                                OptionSwitch("Aria2c kullan","HTTP indirmelerinde kullanılır; sorun olursa normal motor denenir.",o.useAria2) { update(o.copy(useAria2=it)) }
                                Pick("Bağlantı sayısı",listOf(1,2,4,8,16).map { it.toString() to it.toString() },o.ariaConnections.toString()) { update(o.copy(ariaConnections=it.toInt())) }
                                Pick("DASH / HLS paralel parçaları",listOf(1,2,4,8,16).map { it.toString() to it.toString() },o.fragments.toString()) { update(o.copy(fragments=it.toInt())) }
                                OptionSwitch("Eksik parçaları atla",value=o.skipUnavailable) { update(o.copy(skipUnavailable=it)) }
                            }
                            "network" -> {
                                OptionSwitch("Yalnızca ölçülmeyen ağ","Wi-Fi veya başka bir ölçülmeyen bağlantıyı bekler.",o.wifiOnly) { update(o.copy(wifiOnly=it)) }
                                Pick("Hız sınırı",listOf(0,500,1000,2000,5000,10000).map { it.toString() to if(it==0) "Sınırsız" else "$it KB/s" },o.speedLimitKbps.toString()) { update(o.copy(speedLimitKbps=it.toInt())) }
                                Pick("IP protokolü",listOf("auto" to "Otomatik","ipv4" to "IPv4","ipv6" to "IPv6"),o.networkMode) { update(o.copy(networkMode=it)) }
                                Pick("Tekrar denemeler",listOf(0,1,3,5,10).map { it.toString() to it.toString() },o.retries.toString()) { update(o.copy(retries=it.toInt())) }
                                Pick("Parça denemeleri",listOf(0,1,3,5,10).map { it.toString() to it.toString() },o.fragmentRetries.toString()) { update(o.copy(fragmentRetries=it.toInt())) }
                                Pick("Zaman aşımı",listOf(10,20,30,60,120).map { it.toString() to "$it ${AppLanguage.message(texts,"sn")}" },o.timeout.toString()) { update(o.copy(timeout=it.toInt())) }
                                Pick("İndirme öncesi bekleme",listOf(0,3,5,10,30,60).map { it.toString() to if(it==0) "Kapalı" else "$it ${AppLanguage.message(texts,"sn")}" },o.sleepSeconds.toString()) { update(o.copy(sleepSeconds=it.toInt())) }
                                CompactField(texts.getString(R.string.proxy),o.proxy,onChange={ update(o.copy(proxy=it.trim())) })
                                CompactField(texts.getString(R.string.user_agent),o.userAgent,onChange={ update(o.copy(userAgent=it)) })
                            }
                            "playlist" -> {
                                OptionSwitch("Liste indirmeye izin ver",value=o.playlist) { update(o.copy(playlist=it)) }
                                Pick("Liste aralığı",listOf("" to "Tümü","1:5" to "İlk 5","1:10" to "İlk 10","1:25" to "İlk 25","1:50" to "İlk 50","1:100" to "İlk 100"),o.playlistItems) { update(o.copy(playlistItems=it)) }
                                CompactField(texts.getString(R.string.playlist_items),o.playlistItems,onChange={ update(o.copy(playlistItems=it)) })
                                Pick("İndirme sırası",listOf("normal" to "Normal","reverse" to "Ters","random" to "Karışık"),o.playlistOrder) { update(o.copy(playlistOrder=it)) }
                                Pick("Bir video indirilemezse",listOf("continue" to "Sonrakine geç","stop" to "Durdur"),o.playlistErrors) { update(o.copy(playlistErrors=it)) }
                            }
                            "files" -> {
                                Text(AppLanguage.message(texts,"Download / Zenith"),style=MaterialTheme.typography.titleMedium)
                                Pick("Dosya adı",listOf("%(title).150B [%(id)s].%(ext)s" to "Başlık + ID","%(uploader).50B - %(title).150B.%(ext)s" to "Kanal + başlık","%(upload_date)s - %(title).150B.%(ext)s" to "Tarih + başlık"),o.filenameTemplate) { update(o.copy(filenameTemplate=it)) }
                                OptionSwitch("Tekrar indirilenleri atla","Tamamlanan indirmeleri yerel arşivden kontrol eder.",o.downloadArchive) { update(o.copy(downloadArchive=it)) }
                                OptionSwitch("Açıklamayı kaydet",value=o.writeDescription) { update(o.copy(writeDescription=it)) }
                                OptionSwitch("Bilgi dosyasını kaydet","Kaynak bağlantısını içerebilir.",o.writeInfoJson) { update(o.copy(writeInfoJson=it)) }
                            }
                            "presets" -> {
                                Text(AppLanguage.message(texts,"Profil seçimi mevcut varsayılanları değiştirir. Sonrasında tek tek düzenleyebilirsin."),color=Muted)
                                listOf("Günlük" to DownloadOptions(),"Müzik" to DownloadOptions(mode=MediaMode.AUDIO,audioFormat="m4a",embedThumbnail=true),"Arşiv" to DownloadOptions(container="mkv",height=0,downloadArchive=true,subtitles=true,embedThumbnail=true),"Az veri" to DownloadOptions(height=480,wifiOnly=true,speedLimitKbps=1000),"Yüksek kalite" to DownloadOptions(height=0,container="mkv",audioFormat="flac")).forEach { (name,value) ->
                                    OutlinedButton(onClick={ update(value);model.showMessage(AppLanguage.message(texts,"{name} profili uygulandı.").replace("{name}",AppLanguage.message(texts,name))) },modifier=Modifier.fillMaxWidth().heightIn(min=50.dp),shape=RoundedCornerShape(16.dp)) { Text(AppLanguage.message(texts,name)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Pick(title: String,items: List<Pair<String,String>>,selected: String,onSelect: (String)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(AppLanguage.message(LocalAppContext.current,title),style=MaterialTheme.typography.titleSmall)
        Choices(items,selected,onSelect)
    }
}
@Composable private fun Pick(title: String,vararg items: Pair<String,String>,selected: String,onSelect: (String)->Unit)=Pick(title,items.toList(),selected,onSelect)
