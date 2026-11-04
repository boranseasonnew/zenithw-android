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
import android.provider.DocumentsContract
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
        if(job.state in listOf(JobState.COMPLETED,JobState.CANCELLED,JobState.SKIPPED,JobState.PAUSED)) return@withContext Result.success()
        // Independent WorkRequests keep cancelling one item from cancelling the
        // rest of a dependency chain. Claim the oldest currently eligible item.
        if(!claimTurn(job)) return@withContext Result.retry()
        val directory=File(applicationContext.noBackupFilesDir,"downloads/${job.id}").apply { mkdirs() }
        var lastProgress=0L
        var lastPhase=""
        var publishedAll=false
        var retryPending=false
        val saved=app.store.get(job.id)?.files.orEmpty().toMutableList()
        try {
            app.store.log("TASK  Started",job.id)
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
                        bytesDownloaded=bytes.getOrNull(1)?.toLongOrNull() ?: it.bytesDownloaded,
                        totalBytes=bytes.getOrNull(2)?.toLongOrNull() ?: it.totalBytes,
                        speedBytes=bytes.getOrNull(3)?.toLongOrNull() ?: 0,
                        detail=DownloadPolicy.progressDetail(percent,eta,line)) }
                    setForegroundAsync(foreground(app.store.get(job.id)?.title ?: job.title,percent.toInt().coerceIn(0,99),percent<0 || phase!="downloading"))
                }
            })
            currentCoroutineContext().ensureActive()
            if(outcome.archiveSkipped) {
                app.store.change(job.id) { it.copy(state=JobState.SKIPPED,phase="archive_skipped",progress=0f,
                    progressKnown=false,etaSeconds=-1,detail="İndirme arşivinde kayıtlı; atlandı. Yeniden indirmek için tekrar dene.") }
                app.store.log("TASK  Skipped: already recorded in download archive",job.id)
                return@withContext Result.success()
            }
            app.store.change(job.id) { it.copy(state=JobState.SAVING,phase="saving",progressKnown=false,etaSeconds=-1,detail="Cihaza kaydediliyor",notes=outcome.notes) }
            app.store.log("TASK  Saving output files",job.id)
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
            // Read the embedded/sidecar cover before the temporary media is moved.
            // This is local file work; no second extraction or network request.
            JobArtwork(applicationContext).retain(job.id,outputs)
            val mediaBytes=outputs.filter { it.extension.lowercase() in JobArtwork.MEDIA_EXTENSIONS }.sumOf { it.length() }
            for(output in outputs) {
                currentCoroutineContext().ensureActive()
                require(app.store.get(job.id)?.state!=JobState.CANCELLED) { "Cancelled" }
                saved.add(publish(output,job).toString())
                // Preserve each successful URI even if a later copy fails.
                app.store.change(job.id) { it.copy(files=saved.toList()) }
                output.delete()
            }
            publishedAll=true
            app.store.change(job.id) { it.copy(state=JobState.COMPLETED,phase="complete",progressKnown=true,etaSeconds=-1,progress=100f,
                bytesDownloaded=mediaBytes.takeIf { size -> size>0 } ?: it.bytesDownloaded,
                totalBytes=mediaBytes.takeIf { size -> size>0 } ?: it.totalBytes,
                detail=if(outcome.notes.isEmpty()) "Kaydedildi" else "Kaydedildi · açıklamayı kontrol et",files=saved.toList(),notes=outcome.notes) }
            app.store.log("TASK  Completed · ${saved.size} file(s)",job.id)
            Result.success()
        } catch(cancelled: CancellationException) {
            app.store.log("TASK  Cancelled",job.id)
            app.store.change(job.id) { it.copy(state=JobState.CANCELLED,phase="cancelled",progressKnown=false,etaSeconds=-1,files=saved.toList(),detail="İptal edildi",
                notes=it.notes+if(directory.listFiles().orEmpty().any { file -> DownloadPolicy.isPublicOutput(file.name) }) listOf("Kaydedilemeyen dosyalar cihazdaki özel kurtarma klasöründe korundu.") else emptyList()) }
            throw cancelled
        } catch(e: Exception) {
            val cancelled=app.store.get(job.id)?.state==JobState.CANCELLED
            if(!cancelled && saved.isEmpty() && job.retryCount<3 && RecoveryPolicy.retryable(RecoveryPolicy.classify(e.message.orEmpty()))) {
                retryPending=true
                app.store.change(job.id) { it.copy(state=JobState.QUEUED,phase="retry_wait",retryCount=it.retryCount+1,progressKnown=false,etaSeconds=-1,detail="Sırada") }
                app.store.log("TASK  Temporary failure; bounded retry ${job.retryCount+1}/3",job.id)
                return@withContext Result.retry()
            }
            if(cancelled) app.store.log("TASK  Cancelled",job.id)
            else (e.message ?: e.javaClass.simpleName).lineSequence().filter { it.isNotBlank() }.toList().takeLast(20)
                .forEach { app.store.log("ERROR  $it",job.id) }
            app.store.change(job.id) { it.copy(state=if(cancelled) JobState.CANCELLED else JobState.FAILED,phase=if(cancelled) "cancelled" else "failed",etaSeconds=-1,
                failureKind=RecoveryPolicy.classify(e.message.orEmpty()).name,files=saved.toList(),notes=it.notes+if(directory.listFiles().orEmpty().any { file -> DownloadPolicy.isPublicOutput(file.name) }) listOf("Kaydedilemeyen dosyalar cihazdaki özel kurtarma klasöründe korundu.") else emptyList(),
                detail=if(cancelled) "İptal edildi" else DownloadPolicy.failureDetail(e.message.orEmpty())) }
            Result.success()
        } finally {
            app.engine.cancel(job.id)
            val root=File(applicationContext.noBackupFilesDir,"downloads").canonicalFile
            if(!retryPending && directory.canonicalFile.parentFile==root) {
                // Private metadata/cookies are never retained with recoverable media.
                directory.listFiles().orEmpty().filter { it.name=="session.txt" || it.name==".source-info.json" }.forEach { it.delete() }
                if(publishedAll || directory.listFiles().orEmpty().none { it.isFile && DownloadPolicy.isPublicOutput(it.name) }) directory.deleteRecursively()
            }
            withContext(NonCancellable) { turn.withLock { activeWorkers.remove(job.id) };resumeQueue(app) }
        }
    }
    private suspend fun claimTurn(job: DownloadJob): Boolean=turn.withLock {
        if(job.id in activeWorkers || activeWorkers.size>=app.store.concurrentDownloads) return@withLock false
        val next=eligible(app).filterNot { it.id in activeWorkers }
            .take(app.store.concurrentDownloads-activeWorkers.size)
        if(next.none { it.id==job.id }) return@withLock false
        activeWorkers.add(job.id)
        true
    }
    private fun jobIdHash()=(inputData.getString("jobId") ?: id.toString()).hashCode()
    private fun foreground(title: String,percent: Int,indeterminate: Boolean=true): ForegroundInfo {
        val texts=AppLanguage.context(applicationContext)
        val manager=applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if(Build.VERSION.SDK_INT>=26) manager.createNotificationChannel(NotificationChannel(
            "downloads",texts.getString(R.string.downloads),NotificationManager.IMPORTANCE_LOW))
        val intent=Intent(applicationContext,MainActivity::class.java).putExtra("showQueue",true)
        val pending=PendingIntent.getActivity(applicationContext,0,intent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val cancel=PendingIntent.getBroadcast(applicationContext,jobIdHash(),Intent(applicationContext,DownloadActionReceiver::class.java).putExtra("jobId",inputData.getString("jobId")),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
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
    private suspend fun publish(file: File,job: DownloadJob): Uri {
        val mime=android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
        val subfolder=FilenamePolicy.folder(job.options.outputFolder).ifBlank { if(app.store.separateMediaFolders) when {
            mime.startsWith("video/") -> "Video"
            mime.startsWith("audio/") -> "Audio"
            else -> "Other"
        } else "" }
        if(app.store.outputDirectory.isNotBlank()) {
            val resolver=applicationContext.contentResolver
            val tree=Uri.parse(app.store.outputDirectory)
            var parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree))
            if(subfolder.isNotBlank()) {
                val children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getDocumentId(parent))
                val existing=resolver.query(children,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE),null,null,null)?.use { cursor ->
                    var document: Uri?=null
                    while(cursor.moveToNext()) {
                        if(cursor.getString(1)==subfolder && cursor.getString(2)==DocumentsContract.Document.MIME_TYPE_DIR) {
                            document=DocumentsContract.buildDocumentUriUsingTree(tree,cursor.getString(0));break
                        }
                    }
                    document
                }
                parent=existing ?: DocumentsContract.createDocument(resolver,parent,DocumentsContract.Document.MIME_TYPE_DIR,subfolder)
                    ?: error("Cannot create output folder")
            }
            val uri=DocumentsContract.createDocument(resolver,parent,mime,file.name) ?: error("Cannot create output file")
            try {
                resolver.openOutputStream(uri,"w")?.use { copy(file,it) } ?: error("Cannot write output file")
                currentCoroutineContext().ensureActive()
                return uri
            } catch(e: Exception) {
                runCatching { DocumentsContract.deleteDocument(resolver,uri) }
                throw e
            }
        }
        if(Build.VERSION.SDK_INT>=29) {
            val values=ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME,file.name)
                put(MediaStore.MediaColumns.MIME_TYPE,mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH,"Download/ZenithW"+if(subfolder.isNotBlank()) "/$subfolder" else "")
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
        val directory=File(applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),"ZenithW"+if(subfolder.isNotBlank()) "/$subfolder" else "").apply { mkdirs() }
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
        private val activeWorkers=java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        private val queueStates=setOf(JobState.QUEUED,JobState.RUNNING,JobState.SAVING,JobState.PAUSED)
        fun enqueue(app: ZenithApplication,job: DownloadJob) {
            require(app.store.jobs.value.count { it.state in queueStates }<200) { "queue_capacity" }
            app.store.put(job)
            schedule(app,job)
        }
        private fun schedule(app: ZenithApplication,job: DownloadJob,replaceWaiting: Boolean=false) {
            val constraints=Constraints.Builder().setRequiredNetworkType(
                if(job.options.unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresCharging(job.options.chargingOnly).build()
            val request=OneTimeWorkRequestBuilder<DownloadWorker>().setInputData(workDataOf("jobId" to job.id))
                .setConstraints(constraints).addTag("zenithw-download").addTag("zenithw-job-${job.id}")
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,10,TimeUnit.SECONDS)
                .setInitialDelay((job.options.scheduledAt-System.currentTimeMillis()).coerceAtLeast(0),TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(app).enqueueUniqueWork("download-3-${job.id}",if(replaceWaiting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,request)
        }
        internal fun eligible(app: ZenithApplication): List<DownloadJob> {
            val manager=app.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val unmetered=!manager.isActiveNetworkMetered
            val wifi=manager.getNetworkCapabilities(manager.activeNetwork)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)==true
            val battery=app.registerReceiver(null,android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status=battery?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS,-1)
            val charging=status==android.os.BatteryManager.BATTERY_STATUS_CHARGING || status==android.os.BatteryManager.BATTERY_STATUS_FULL
            val now=System.currentTimeMillis()
            return app.store.jobs.value.filter { it.state in setOf(JobState.QUEUED,JobState.RUNNING,JobState.SAVING) &&
                it.options.scheduledAt<=now && (!it.options.wifiOnly || wifi) && (!it.options.unmeteredOnly || unmetered) && (!it.options.chargingOnly || charging) }
                .sortedWith(compareByDescending<DownloadJob> { it.priority }.thenBy { it.queueOrder }.thenBy { it.createdAt })
        }
        fun resumeQueue(app: ZenithApplication,reconsiderNetwork: Boolean=false) {
            val ready=if(reconsiderNetwork) eligible(app).map { it.id }.toSet() else emptySet()
            app.store.jobs.value.filter { it.state in setOf(JobState.QUEUED,JobState.RUNNING,JobState.SAVING) }
                .forEach { schedule(app,it,reconsiderNetwork && it.state==JobState.QUEUED && it.id !in activeWorkers && it.id in ready) }
        }
        suspend fun pauseQueued(app: ZenithApplication,id: String) {
            val paused=turn.withLock {
                app.store.change(id) { if(it.state==JobState.QUEUED && id !in activeWorkers) it.copy(state=JobState.PAUSED) else it }
                app.store.get(id)?.state==JobState.PAUSED
            }
            if(paused) WorkManager.getInstance(app).cancelUniqueWork("download-3-$id").result.get()
        }
        fun resumeQueued(app: ZenithApplication,id: String) {
            app.store.change(id) { if(it.state==JobState.PAUSED) it.copy(state=JobState.QUEUED) else it }
            app.store.get(id)?.takeIf { it.state==JobState.QUEUED }?.let { schedule(app,it) }
        }
        fun cancel(app: ZenithApplication,id: String) {
            app.store.change(id) { if(it.state in queueStates) it.copy(state=JobState.CANCELLED,detail="İptal edildi") else it }
            if(app.store.get(id)?.state!=JobState.CANCELLED) return
            app.engine.cancel(id)
            WorkManager.getInstance(app).cancelAllWorkByTag("zenithw-job-$id")
            // Also cancel requests persisted by versions using the old unique name.
            WorkManager.getInstance(app).cancelUniqueWork("download-$id")
            WorkManager.getInstance(app).cancelUniqueWork("download-3-$id")
            resumeQueue(app)
        }
    }
}
