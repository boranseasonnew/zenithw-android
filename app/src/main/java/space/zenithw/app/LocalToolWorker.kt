package space.zenithw.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalToolWorker(context: Context,params: WorkerParameters): CoroutineWorker(context,params) {
    override suspend fun doWork(): Result=withContext(Dispatchers.IO) {
        val app=applicationContext as ZenithApplication
        try {
            val texts=AppLanguage.context(applicationContext)
            val manager=applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if(Build.VERSION.SDK_INT>=26) manager.createNotificationChannel(NotificationChannel("media-tools",texts.getString(R.string.feature_tools),NotificationManager.IMPORTANCE_LOW))
            val notification=NotificationCompat.Builder(applicationContext,"media-tools").setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(texts.getString(R.string.feature_tools)).setProgress(100,0,true).setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .addAction(0,texts.getString(R.string.cancel_download),WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)).build()
            setForeground(if(Build.VERSION.SDK_INT>=29) ForegroundInfo(71931,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(71931,notification))
            app.engine.runtimeVersion()
            fun request(source: Uri,destination: Uri)=LocalMediaTools.Request(
                source,destination,inputData.getString("mode") ?: error("mode"),inputData.getDouble("start",0.0),inputData.getDouble("end",0.0),
                inputData.getString("preset") ?: "balanced",inputData.getInt("resolution",0),inputData.getInt("bitrate",192),
                inputData.getInt("sampleRate",44100),inputData.getInt("fps",10),inputData.getInt("loop",0),inputData.getInt("videoBitrate",0),inputData.getString("subtitle")?.let(Uri::parse),inputData.getDouble("offset",0.0))
            val destination=Uri.parse(inputData.getString("destination") ?: error("destination"))
            val sources=inputData.getString("batchSources")?.let { raw ->
                require(raw.length<=6000)
                org.json.JSONArray(raw).let { a -> require(a.length() in 1..10);(0 until a.length()).map { Uri.parse(a.getString(it)) } }
            }
            if(sources==null) {
                val request=request(Uri.parse(inputData.getString("source") ?: error("source")),destination)
                LocalMediaTools(applicationContext).execute(request) { percent -> setProgressAsync(workDataOf("percent" to percent)) }
                Result.success(workDataOf("uri" to destination.toString()))
            } else {
                require(destination.scheme=="content" && sources.all { it.scheme=="content" })
                val parent=android.provider.DocumentsContract.buildDocumentUriUsingTree(destination,android.provider.DocumentsContract.getTreeDocumentId(destination))
                var completed=0;var failed=0
                for((index,source) in sources.withIndex()) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    var output: Uri?=null
                    try {
                        val mode=inputData.getString("mode") ?: error("mode")
                        val extension=when(mode) { "remux_mp4","trim","trim_exact","mute","attach","burn" -> "mp4";"remux_mkv" -> "mkv";else -> mode }
                        require(extension.matches(Regex("[a-z0-9]{2,5}")))
                        output=android.provider.DocumentsContract.createDocument(applicationContext.contentResolver,parent,"application/octet-stream","Zenith-${java.util.UUID.randomUUID()}.$extension") ?: error("output")
                        LocalMediaTools(applicationContext).execute(request(source,output)) { percent ->
                            setProgressAsync(workDataOf("percent" to ((index*100+percent)/sources.size),"completed" to completed,"failed" to failed))
                        }
                        completed++
                    } catch(e: Exception) {
                        output?.let { runCatching { android.provider.DocumentsContract.deleteDocument(applicationContext.contentResolver,it) } }
                        if(e is CancellationException) throw e
                        failed++;app.store.log("MEDIA  Batch item ${index+1} failed: ${e.javaClass.simpleName}")
                    }
                }
                val result=workDataOf("completed" to completed,"failed" to failed)
                if(failed==0) Result.success(result) else Result.failure(result)
            }
        } catch(e: CancellationException) { throw e }
        catch(e: Exception) {
            app.store.log("MEDIA  ${e.javaClass.simpleName}: ${e.message?.take(80)}")
            Result.failure(workDataOf("error" to RecoveryPolicy.classify(e.message.orEmpty()).name))
        }
    }
    companion object {
        const val NAME="zenith-local-media-tool"
        suspend fun enqueue(context: Context,r: LocalMediaTools.Request,sources: List<Uri> = emptyList()): Boolean=withContext(Dispatchers.IO) {
            require(sources.size<=10)
            val batch=if(sources.isEmpty()) null else org.json.JSONArray(sources.map { it.toString() }).toString().also { require(it.length<=6000) }
            val manager=WorkManager.getInstance(context)
            if(manager.getWorkInfosForUniqueWork(NAME).get().any { !it.state.isFinished }) return@withContext false
            val work=OneTimeWorkRequestBuilder<LocalToolWorker>().setInputData(workDataOf(
                "batchSources" to batch,"source" to r.source.toString(),"destination" to r.destination.toString(),"mode" to r.mode,
                "start" to r.start,"end" to r.end,"preset" to r.preset,"resolution" to r.resolution,
                "bitrate" to r.bitrate,"sampleRate" to r.sampleRate,"fps" to r.fps,"loop" to r.loop,"videoBitrate" to r.videoBitrateKbps,"subtitle" to r.subtitle?.toString(),"offset" to r.offset)).build()
            manager.enqueueUniqueWork(NAME,ExistingWorkPolicy.KEEP,work).result.get()
            true
        }
    }
}
