package space.zenithw.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.work.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

class DownloadWorker(context: Context,params: WorkerParameters): CoroutineWorker(context,params) {
    private val app=applicationContext as ZenithApplication
    override suspend fun doWork(): Result=withContext(Dispatchers.IO) {
        val job=app.store.get(inputData.getString("jobId") ?: id.toString()) ?: return@withContext Result.failure()
        if(job.state in listOf(JobState.COMPLETED,JobState.CANCELLED)) return@withContext Result.success()
        // Independent WorkRequests keep cancelling one item from cancelling the
        // rest of a dependency chain. Claim the oldest currently eligible item.
        if(!claimTurn(job)) return@withContext Result.success()
        val directory=File(applicationContext.noBackupFilesDir,"downloads/${job.id}").apply { mkdirs() }
        var lastProgress=0L
        var lastPhase=""
        var publishedAll=false
        val saved=app.store.get(job.id)?.files.orEmpty().toMutableList()
        try {
            setForeground(foreground(job.title,0))
            val outcome=app.engine.download(job,directory,started={
                app.store.change(job.id) { it.copy(state=JobState.RUNNING,phase="preparing",progress=0f,progressKnown=false,etaSeconds=-1,detail="Bağlantı ve medya hazırlanıyor") }
            },progress={ percent,eta,line ->
                if(app.store.get(job.id)?.state==JobState.CANCELLED) app.engine.cancel(job.id)
                val now=android.os.SystemClock.elapsedRealtime()
                val phase=if(line.startsWith("ZenithWTransfer")) "downloading" else if(line.startsWith("[" ) || line in setOf("ZenithWSquareCover","ZenithWProcessing")) "processing" else "preparing"
                if(now-lastProgress>200 || phase!=lastPhase || percent==0f) {
                    lastProgress=now
                    lastPhase=phase
                    val bytes=if(line.startsWith("ZenithWTransfer:")) line.split(':') else emptyList()
                    app.store.change(job.id) { it.copy(progress=percent.coerceIn(0f,99f),progressKnown=percent>=0,phase=phase,
                        etaSeconds=if(phase=="downloading") eta else -1,
                        bytesDownloaded=bytes.getOrNull(1)?.toLongOrNull() ?: 0,totalBytes=bytes.getOrNull(2)?.toLongOrNull() ?: 0,
                        detail=DownloadPolicy.progressDetail(percent,eta,line)) }
                    setForegroundAsync(foreground(job.title,percent.toInt().coerceIn(0,99),percent<0 || phase!="downloading"))
                }
            })
            currentCoroutineContext().ensureActive()
            app.store.change(job.id) { it.copy(state=JobState.SAVING,phase="saving",progressKnown=false,etaSeconds=-1,detail="Cihaza kaydediliyor",notes=outcome.notes) }
            val outputs=directory.listFiles()?.filter {
                it.isFile && DownloadPolicy.isPublicOutput(it.name)
            }.orEmpty().sortedWith(compareBy<File> {
                when(it.extension.lowercase()) {
                    "mp4","mkv","webm","mov","m4v","mp3","m4a","opus","wav","flac","ogg" -> 0
                    "srt","vtt","ass","lrc" -> 1
                    else -> 2
                }
            }.thenBy { it.name })
            require(outputs.isNotEmpty()) { "No output files" }
            for(output in outputs) {
                currentCoroutineContext().ensureActive()
                require(app.store.get(job.id)?.state!=JobState.CANCELLED) { "Cancelled" }
                saved.add(publish(output).toString())
                // Preserve each successful URI even if a later copy fails.
                app.store.change(job.id) { it.copy(files=saved.toList()) }
                output.delete()
            }
            publishedAll=true
            app.store.change(job.id) { it.copy(state=JobState.COMPLETED,phase="complete",progressKnown=true,etaSeconds=-1,progress=100f,detail=if(outcome.notes.isEmpty()) "Kaydedildi" else "Kaydedildi · açıklamayı kontrol et",files=saved.toList(),notes=outcome.notes) }
            Result.success()
        } catch(cancelled: CancellationException) {
            app.store.change(job.id) { it.copy(state=JobState.CANCELLED,phase="cancelled",progressKnown=false,etaSeconds=-1,files=saved.toList(),detail="İptal edildi",
                notes=it.notes+if(directory.listFiles().orEmpty().any { file -> DownloadPolicy.isPublicOutput(file.name) }) listOf("Kaydedilemeyen dosyalar cihazdaki özel kurtarma klasöründe korundu.") else emptyList()) }
            throw cancelled
        } catch(e: Exception) {
            val cancelled=isStopped || app.store.get(job.id)?.state==JobState.CANCELLED
            app.store.change(job.id) { it.copy(state=if(cancelled) JobState.CANCELLED else JobState.FAILED,phase=if(cancelled) "cancelled" else "failed",etaSeconds=-1,
                files=saved.toList(),notes=it.notes+if(directory.listFiles().orEmpty().any { file -> DownloadPolicy.isPublicOutput(file.name) }) listOf("Kaydedilemeyen dosyalar cihazdaki özel kurtarma klasöründe korundu.") else emptyList(),
                detail=if(cancelled) "İptal edildi" else DownloadPolicy.failureDetail(e.message.orEmpty())) }
            Result.success()
        } finally {
            app.engine.cancel(job.id)
            val root=File(applicationContext.noBackupFilesDir,"downloads").canonicalFile
            if(directory.canonicalFile.parentFile==root) {
                // Private metadata/cookies are never retained with recoverable media.
                directory.listFiles().orEmpty().filter { it.name=="session.txt" || it.name==".source-info.json" }.forEach { it.delete() }
                if(publishedAll || directory.listFiles().orEmpty().none { it.isFile && DownloadPolicy.isPublicOutput(it.name) }) directory.deleteRecursively()
            }
            withContext(NonCancellable) { turn.withLock { if(activeWorker==job.id) activeWorker=null };resumeQueue(app) }
        }
    }
    private suspend fun claimTurn(job: DownloadJob): Boolean=turn.withLock {
        if(activeWorker!=null) return@withLock false
        val manager=applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val unmetered=!manager.isActiveNetworkMetered
        val now=System.currentTimeMillis()
        val next=app.store.jobs.value.filter { it.state in setOf(JobState.QUEUED,JobState.RUNNING,JobState.SAVING)
            && it.options.scheduledAt<=now && (!it.options.wifiOnly || unmetered) }
            .sortedBy { it.createdAt }.firstOrNull()
        if(next?.id!=job.id) return@withLock false
        activeWorker=job.id
        true
    }
    private fun foreground(title: String,percent: Int,indeterminate: Boolean=true): ForegroundInfo {
        val texts=AppLanguage.context(applicationContext)
        val manager=applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if(Build.VERSION.SDK_INT>=26) manager.createNotificationChannel(NotificationChannel(
            "downloads",texts.getString(R.string.downloads),NotificationManager.IMPORTANCE_LOW))
        val intent=Intent(applicationContext,MainActivity::class.java).putExtra("showQueue",true)
        val pending=PendingIntent.getActivity(applicationContext,0,intent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val cancel=WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val notification=NotificationCompat.Builder(applicationContext,"downloads")
            .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(title.take(80))
            .setContentText(if(percent>0) "%$percent" else texts.getString(R.string.queued_preparing))
            .setProgress(100,percent,indeterminate).setOngoing(true).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(pending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,texts.getString(R.string.cancel_notification),cancel).build()
        val notificationId=(id.hashCode() and Int.MAX_VALUE).coerceAtLeast(1)
        return if(Build.VERSION.SDK_INT>=29) ForegroundInfo(notificationId,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else ForegroundInfo(notificationId,notification)
    }
    private suspend fun publish(file: File): Uri {
        val mime=android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
        if(Build.VERSION.SDK_INT>=29) {
            val values=ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME,file.name)
                put(MediaStore.MediaColumns.MIME_TYPE,mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH,"Download/ZenithW")
                put(MediaStore.MediaColumns.IS_PENDING,1)
            }
            val resolver=applicationContext.contentResolver
            val uri=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values) ?: error("Cannot save file")
            try {
                resolver.openOutputStream(uri)?.use { output -> copy(file,output) } ?: error("Cannot write file")
                currentCoroutineContext().ensureActive()
                resolver.update(uri,ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING,0) },null,null)
                return uri
            } catch(e: Exception) { resolver.delete(uri,null,null); throw e }
        }
        val directory=File(applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),"ZenithW").apply { mkdirs() }
        val target=File(directory,file.name)
        val safeTarget=if(target.exists()) File(directory,"${System.currentTimeMillis()}-${file.name}") else target
        try {
            safeTarget.outputStream().use { copy(file,it) }
            currentCoroutineContext().ensureActive()
            return FileProvider.getUriForFile(applicationContext,"${applicationContext.packageName}.fileprovider",safeTarget)
        } catch(e: Exception) { safeTarget.delete();throw e }
    }
    private suspend fun copy(file: File,output: java.io.OutputStream) {
        file.inputStream().use { input ->
            val buffer=ByteArray(65536)
            while(true) { currentCoroutineContext().ensureActive();val count=input.read(buffer);if(count<0) break;output.write(buffer,0,count) }
        }
    }
    companion object {
        private val turn=Mutex()
        @Volatile private var activeWorker: String?=null
        fun enqueue(app: ZenithApplication,job: DownloadJob) {
            require(app.store.jobs.value.count { it.state in setOf(JobState.QUEUED,JobState.RUNNING,JobState.SAVING) }<200) { "İndirme kuyruğu en fazla 200 dosya alabilir." }
            app.store.put(job)
            schedule(app,job)
        }
        private fun schedule(app: ZenithApplication,job: DownloadJob) {
            val constraints=Constraints.Builder().setRequiredNetworkType(
                if(job.options.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build()
            val request=OneTimeWorkRequestBuilder<DownloadWorker>().setInputData(workDataOf("jobId" to job.id))
                .setConstraints(constraints).addTag("zenithw-download").addTag("zenithw-job-${job.id}")
                .setInitialDelay((job.options.scheduledAt-System.currentTimeMillis()).coerceAtLeast(0),TimeUnit.MILLISECONDS)
                .build()
            // A fresh generation cannot be swallowed by KEEP while a previous
            // non-head worker is still committing SUCCEEDED to WorkManager's DB.
            // claimTurn prevents duplicate generations from downloading together.
            WorkManager.getInstance(app).enqueue(request)
        }
        fun resumeQueue(app: ZenithApplication) {
            if(activeWorker!=null) return
            val manager=app.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val unmetered=!manager.isActiveNetworkMetered
            val now=System.currentTimeMillis()
            val pending=app.store.jobs.value.filter { it.state in setOf(JobState.QUEUED,JobState.RUNNING,JobState.SAVING) }
                .sortedBy { it.createdAt }
            val ready=pending.firstOrNull { it.options.scheduledAt<=now && (!it.options.wifiOnly || unmetered) }
            // Delayed/network-constrained work remains registered with WorkManager.
            (ready ?: pending.minByOrNull { it.options.scheduledAt })?.let { schedule(app,it) }
        }
        fun cancel(app: ZenithApplication,id: String) {
            app.store.change(id) { it.copy(state=JobState.CANCELLED,detail="İptal edildi") }
            app.engine.cancel(id)
            WorkManager.getInstance(app).cancelAllWorkByTag("zenithw-job-$id")
            // Also cancel requests persisted by versions using the old unique name.
            WorkManager.getInstance(app).cancelUniqueWork("download-$id")
            resumeQueue(app)
        }
    }
}
