package space.zenithw.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import java.util.Locale

internal fun mediaDuration(seconds: Long): String {
    val value=seconds.coerceAtLeast(0)
    return if(value>=3600) String.format(Locale.ROOT,"%d:%02d:%02d",value/3600,value/60%60,value%60)
        else String.format(Locale.ROOT,"%d:%02d",value/60,value%60)
}
internal fun mediaSize(bytes: Long): String {
    if(bytes<=0) return "—"
    return if(bytes>=1000_000_000) String.format(Locale.getDefault(),"%.2f GB",bytes/1000_000_000.0)
        else String.format(Locale.getDefault(),"%.1f MB",bytes/1000_000.0)
}

@Composable internal fun JobCover(job: DownloadJob,modifier: Modifier=Modifier) {
    val context=LocalContext.current
    val artwork=remember(context) { JobArtwork(context) }
    val local by produceState<File?>(null,job.id,job.files) { value=artwork.resolve(job) }
    val request=remember(local,job.thumbnail,job.url,job.options.userAgent,job.options.cookieId) {
        ImageRequest.Builder(context).data(local ?: job.thumbnail).apply {
            if(local==null && job.thumbnail!=null) {
                val source=android.net.Uri.parse(job.url)
                if(source.scheme=="https" && !source.host.isNullOrBlank()) setHeader("Referer","https://${source.host}/")
                val agent=job.options.userAgent.ifBlank { (context.applicationContext as? ZenithApplication)?.vault?.userAgent(job.options.cookieId).orEmpty() }
                if(agent.isNotBlank() && agent.none(Char::isISOControl)) setHeader("User-Agent",agent)
            }
        }.crossfade(true).build()
    }
    Box(modifier.background(PanelRaised),contentAlignment=Alignment.Center) {
        Icon(if(job.options.mode==MediaMode.AUDIO) Icons.Outlined.MusicNote else Icons.Outlined.PlayCircleOutline,
            null,tint=Muted,modifier=Modifier.size(30.dp))
        AsyncImage(request,null,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
    }
}

@Composable internal fun HomeDownloadCard(job: DownloadJob,onCancel: ()->Unit,onRetry: ()->Unit,onOpen: ()->Unit) {
    val texts=LocalAppContext.current
    val active=job.state in setOf(JobState.QUEUED,JobState.RUNNING,JobState.SAVING)
    Surface(shape=RoundedCornerShape(24.dp),color=Panel,border=BorderStroke(1.dp,Hairline),modifier=Modifier.fillMaxWidth()) {
        Column {
            Box {
                JobCover(job,Modifier.fillMaxWidth().height(132.dp))
                if(job.durationSeconds>0) Surface(color=Ink.copy(alpha=.86f),shape=RoundedCornerShape(8.dp),
                    modifier=Modifier.align(Alignment.BottomEnd).padding(12.dp)) {
                    Text(mediaDuration(job.durationSeconds.toLong()),Modifier.padding(horizontal=8.dp,vertical=4.dp),
                        style=MaterialTheme.typography.labelMedium,color=Silver)
                }
            }
            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment=Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(job.title,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text(job.url,color=Muted,style=MaterialTheme.typography.bodySmall,maxLines=1,
                            overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=4.dp))
                    }
                    if(active) IconButton(onClick=onCancel,modifier=Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.Close,texts.getString(R.string.cancel_download),tint=Muted)
                    }
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(AppLanguage.message(texts,job.detail),Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,
                        color=if(job.state==JobState.FAILED) MaterialTheme.colorScheme.error else Muted)
                    if(job.progressKnown) Text("${job.progress.toInt()}%",style=MaterialTheme.typography.labelMedium)
                }
                if(active && (!job.progressKnown || job.state!=JobState.RUNNING))
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)),color=Silver,trackColor=Hairline)
                else LinearProgressIndicator(progress={ job.progress/100f },modifier=Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)),
                    color=if(job.state==JobState.COMPLETED) Success else Silver,trackColor=Hairline)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(if(job.bytesDownloaded>0 && job.totalBytes>0 && active) "${mediaSize(job.bytesDownloaded)} / ${mediaSize(job.totalBytes)}"
                        else mediaSize(job.totalBytes.takeIf { it>0 } ?: job.bytesDownloaded),color=Muted,style=MaterialTheme.typography.labelMedium)
                    if(job.etaSeconds>0 && job.phase=="downloading") Text("~${mediaDuration(job.etaSeconds)}",color=Muted,style=MaterialTheme.typography.labelMedium)
                }
                if(job.state==JobState.FAILED || job.state==JobState.CANCELLED)
                    TextButton(onClick=onRetry) { Text(texts.getString(R.string.retry)) }
                if(job.state==JobState.COMPLETED && job.files.isNotEmpty())
                    TextButton(onClick=onOpen) { Icon(Icons.Outlined.OpenInNew,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text(texts.getString(R.string.open)) }
            }
        }
    }
}
