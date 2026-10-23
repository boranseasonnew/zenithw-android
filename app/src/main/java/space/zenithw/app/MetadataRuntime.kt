package space.zenithw.app

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Own metadata-only process. Downloads continue to use the upstream CLI adapter.
 * One executor serializes worker access; cancellation destroys the owning process
 * immediately so a blocked pipe cannot delay a replacement URL. */
internal class MetadataRuntime(private val context: Context) {
    private data class Worker(val process: Process,val input: DataInputStream,val revision: String)
    private val executor=Executors.newSingleThreadExecutor { task -> Thread(task,"ZenithMetadata").apply { isDaemon=true } }
    @Volatile private var worker: Worker?=null
    private val guard=Any()
    private fun revision(): String {
        val engine=File(context.noBackupFilesDir,"youtubedl-android/yt-dlp/yt-dlp")
        return "${engine.lastModified()}:${engine.length()}"
    }
    private fun start(): Worker=synchronized(guard) {
        val version=revision()
        worker?.takeIf { it.revision==version && alive(it.process) }?.let { return@synchronized it }
        worker?.process?.destroy()
        val native=File(context.applicationInfo.nativeLibraryDir)
        val packages=File(context.noBackupFilesDir,"youtubedl-android/packages")
        val script=File(context.cacheDir,"metadata-worker.py")
        context.resources.openRawResource(R.raw.metadata_worker).use { input -> script.outputStream().use { input.copyTo(it) } }
        val command=listOf(File(native,"libpython.so").absolutePath,"-u",script.absolutePath,
            File(context.noBackupFilesDir,"youtubedl-android/yt-dlp/yt-dlp").absolutePath)
        val process=ProcessBuilder(command).apply {
            environment().apply {
                put("PYTHONHOME",File(packages,"python/usr").absolutePath)
                put("HOME",File(packages,"python/usr").absolutePath)
                put("SSL_CERT_FILE",File(packages,"python/usr/etc/tls/cert.pem").absolutePath)
                put("LD_LIBRARY_PATH",listOf("python","ffmpeg","aria2c").joinToString(":") { File(packages,"$it/usr/lib").absolutePath })
                put("PATH",System.getenv("PATH").orEmpty()+":"+native.absolutePath)
                put("TMPDIR",context.cacheDir.absolutePath)
                put("PYTHONUNBUFFERED","1")
            }
            // Python's private worker suppresses raw diagnostics. Drain remaining
            // native stderr without retaining potentially sensitive strings.
        }.start()
        Thread({ runCatching { process.errorStream.use { stream -> val bytes=ByteArray(4096);while(stream.read(bytes)>=0) Unit } } },"ZenithMetadataStderr").apply { isDaemon=true;start() }
        Worker(process,DataInputStream(BufferedInputStream(process.inputStream,32768)),version).also { worker=it }
    }
    private fun alive(process: Process)=try { process.exitValue();false } catch(_: IllegalThreadStateException) { true }
    private fun stop(value: Worker) { synchronized(guard) { if(worker===value) worker=null;value.process.destroy() } }
    fun close() { synchronized(guard) { worker?.process?.destroy();worker=null } }
    private fun read(value: Worker): JSONObject {
        val size=value.input.readInt()
        require(size in 1..16*1024*1024) { "Metadata protocol frame is too large" }
        return JSONObject(String(ByteArray(size).also(value.input::readFully),Charsets.UTF_8))
    }
    suspend fun execute(request: YoutubeDLRequest?,url: String="",snapshot: File?=null,onEvent: (JSONObject)->Unit={}): JSONObject =
        suspendCancellableCoroutine { continuation ->
            val owned=AtomicReference<Worker?>()
            val future=executor.submit {
                try {
                    if(!continuation.isActive) return@submit
                    val previous=worker
                    val started=android.os.SystemClock.elapsedRealtime()
                    val current=start();owned.set(current)
                    onEvent(JSONObject().put("event","runtime_process").put("reused",current===previous)
                        .put("spawn_ms",android.os.SystemClock.elapsedRealtime()-started))
                    if(!continuation.isActive) { stop(current);return@submit }
                    val body=if(request==null) JSONObject().put("operation","ping") else
                        JSONObject().put("operation","inspect").put("url",url)
                            .put("snapshot",requireNotNull(snapshot).absolutePath).put("args",JSONArray(request.buildCommand()))
                    current.process.outputStream.apply { write((body.toString()+"\n").toByteArray(Charsets.UTF_8));flush() }
                    while(continuation.isActive) {
                        val event=read(current)
                        onEvent(event)
                        when(event.getString("event")) {
                            "pong","result" -> { continuation.resume(event);return@submit }
                            "error" -> throw IOException("ERROR: metadata ${event.optString("code","failed")}")
                        }
                    }
                    stop(current)
                } catch(error: Throwable) {
                    owned.get()?.let(::stop)
                    if(continuation.isActive) continuation.resumeWithException(error)
                }
            }
            continuation.invokeOnCancellation { owned.get()?.let(::stop);future.cancel(true) }
        }
}
