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
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

class DownloadWorker(context: Context,params: WorkerParameters): CoroutineWorker(context,params) {
    private val app=applicationContext as ZenithApplication
    override suspend fun doWork(): Result=withContext(Dispatchers.IO) {
        val job=app.store.get(id.toString()) ?: return@withContext Result.failure()
        if(job.state in listOf(JobState.COMPLETED,JobState.CANCELLED)) return@withContext Result.success()
        val directory=File(applicationContext.noBackupFilesDir,"downloads/${job.id}").apply { mkdirs() }
        var lastProgress=0L
        try {
            setForeground(foreground(job.title,0))
            val outcome=app.engine.download(job,directory,started={
                app.store.change(job.id) { it.copy(state=JobState.RUNNING,detail="Bağlantı ve medya hazırlanıyor") }
            },progress={ percent,eta,line ->
                val now=System.currentTimeMillis()
                if(now-lastProgress>600 || line.contains("ZenithWNativeFallback")) {
                    lastProgress=now
                    app.store.change(job.id) { it.copy(progress=percent.coerceIn(0f,99f),
                        detail=DownloadPolicy.progressDetail(percent,eta,line)) }
                    setForegroundAsync(foreground(job.title,percent.toInt().coerceIn(0,99)))
                }
            })
            app.store.change(job.id) { it.copy(state=JobState.SAVING,detail="Cihaza kaydediliyor") }
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
            val uris=outputs.map { publish(it).toString() }
            app.store.change(job.id) { it.copy(state=JobState.COMPLETED,progress=100f,detail=if(outcome.notes.isEmpty()) "Kaydedildi" else "Kaydedildi · açıklamayı kontrol et",files=uris,notes=outcome.notes) }
            Result.success()
        } catch(cancelled: CancellationException) {
            app.store.change(job.id) { it.copy(state=JobState.CANCELLED,detail="İptal edildi") }
            throw cancelled
        } catch(e: Exception) {
            val cancelled=isStopped || app.store.get(job.id)?.state==JobState.CANCELLED
            app.store.change(job.id) { it.copy(state=if(cancelled) JobState.CANCELLED else JobState.FAILED,
                detail=if(cancelled) "İptal edildi" else DownloadPolicy.failureDetail(e.message.orEmpty())) }
            Result.success()
        } finally { app.engine.cancel(job.id); directory.deleteRecursively() }
    }
    private fun foreground(title: String,percent: Int): ForegroundInfo {
        val manager=applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if(Build.VERSION.SDK_INT>=26) manager.createNotificationChannel(NotificationChannel(
            "downloads","İndirmeler",NotificationManager.IMPORTANCE_LOW))
        val intent=Intent(applicationContext,MainActivity::class.java).putExtra("showQueue",true)
        val pending=PendingIntent.getActivity(applicationContext,0,intent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val cancel=WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val notification=NotificationCompat.Builder(applicationContext,"downloads")
            .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(title.take(80))
            .setContentText(if(percent>0) "%$percent" else "Sırada / hazırlanıyor")
            .setProgress(100,percent,percent==0).setOngoing(true).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(pending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,"İptal",cancel).build()
        val notificationId=(id.hashCode() and Int.MAX_VALUE).coerceAtLeast(1)
        return if(Build.VERSION.SDK_INT>=29) ForegroundInfo(notificationId,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else ForegroundInfo(notificationId,notification)
    }
    private fun publish(file: File): Uri {
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
                resolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } } ?: error("Cannot write file")
                resolver.update(uri,ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING,0) },null,null)
                return uri
            } catch(e: Exception) { resolver.delete(uri,null,null); throw e }
        }
        val directory=File(applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),"ZenithW").apply { mkdirs() }
        val target=File(directory,file.name)
        val safeTarget=if(target.exists()) File(directory,"${System.currentTimeMillis()}-${file.name}") else target
        file.copyTo(safeTarget)
        return FileProvider.getUriForFile(applicationContext,"${applicationContext.packageName}.fileprovider",safeTarget)
    }
    companion object {
        fun enqueue(app: ZenithApplication,job: DownloadJob) {
            app.store.put(job)
            val constraints=Constraints.Builder().setRequiredNetworkType(
                if(job.options.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build()
            val request=OneTimeWorkRequestBuilder<DownloadWorker>().setId(UUID.fromString(job.id))
                .setConstraints(constraints).addTag("zenithw-download")
                .setInitialDelay((job.options.scheduledAt-System.currentTimeMillis()).coerceAtLeast(0),TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(app).enqueueUniqueWork("download-${job.id}",ExistingWorkPolicy.KEEP,request)
        }
        fun cancel(app: ZenithApplication,id: String) {
            app.store.change(id) { it.copy(state=JobState.CANCELLED,detail="İptal edildi") }
            app.engine.cancel(id)
            WorkManager.getInstance(app).cancelWorkById(UUID.fromString(id))
        }
    }
}
