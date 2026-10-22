package space.zenithw.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.io.File
import java.security.MessageDigest

/** App-private durable artwork; never fetch a remote media URL through the retriever. */
internal class JobArtwork(private val context: Context) {
    private val directory get()=File(context.noBackupFilesDir,"artwork").apply { mkdirs() }
    fun cached(id: String): File?=target(id).takeIf { it.isFile && it.length()>0 }
    private fun target(id: String): File {
        val key=MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(directory,"$key.jpg")
    }
    private fun save(id: String,bytes: ByteArray): File? {
        if(bytes.isEmpty() || bytes.size>16*1024*1024) return null
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        if(bounds.outWidth<=0 || bounds.outHeight<=0) return null
        val options=BitmapFactory.Options().apply {
            inSampleSize=1
            while(bounds.outWidth/inSampleSize>768 || bounds.outHeight/inSampleSize>768) inSampleSize*=2
        }
        val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size,options) ?: return null
        val destination=target(id)
        val staged=File.createTempFile("cover-",".tmp",directory)
        return try {
            staged.outputStream().use { if(!bitmap.compress(Bitmap.CompressFormat.JPEG,88,it)) return null }
            if(staged.renameTo(destination)) destination else null
        } finally { bitmap.recycle();staged.delete() }
    }
    private fun embedded(source: (MediaMetadataRetriever)->Unit): ByteArray? {
        val retriever=MediaMetadataRetriever()
        return try { source(retriever);retriever.embeddedPicture }
        catch(_: Exception) { null }
        finally { runCatching { retriever.release() } }
    }
    fun retain(id: String,files: List<File>): File? {
        cached(id)?.let { return it }
        for(file in files.filter { it.extension.lowercase() in setOf("jpg","jpeg","png","webp") }) {
            val saved=runCatching { if(file.length()<=16*1024*1024) save(id,file.readBytes()) else null }.getOrNull()
            if(saved!=null) return saved
        }
        for(file in files.filter { it.extension.lowercase() in MEDIA_EXTENSIONS }) {
            val bytes=embedded { it.setDataSource(file.absolutePath) } ?: continue
            runCatching { save(id,bytes) }.getOrNull()?.let { return it }
        }
        return null
    }
    suspend fun resolve(job: DownloadJob): File?=withContext(Dispatchers.IO) {
        cached(job.id)?.let { return@withContext it }
        // Also repair old history entries with an embedded cover, offline.
        for(value in job.files.take(8)) {
            coroutineContext.ensureActive()
            val uri=Uri.parse(value)
            if(uri.scheme!="content") continue
            val mime=runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
            val bytes=if(mime.startsWith("image/")) runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readNBytesCompat(16*1024*1024+1) }
            }.getOrNull() else embedded { it.setDataSource(context,uri) }
            if(bytes!=null) runCatching { save(job.id,bytes) }.getOrNull()?.let { return@withContext it }
        }
        null
    }
    companion object {
        val MEDIA_EXTENSIONS=setOf("mp4","mkv","webm","mov","m4v","mp3","m4a","opus","wav","flac","ogg")
    }
}
