package space.zenithw.app

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Copies only explicitly selected library files to a user-granted SAF directory. */
internal suspend fun exportLibraryFiles(context: Context,jobs: List<DownloadJob>,tree: Uri,onProgress: (Int,Int)->Unit)=withContext(Dispatchers.IO) {
    val sources=jobs.flatMap { it.files }.distinct()
    require(sources.size in 1..200 && tree.scheme=="content")
    val resolver=context.contentResolver
    val parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree))
    val created=mutableListOf<Uri>()
    try {
        sources.forEachIndexed { index,source ->
            coroutineContext.ensureActive()
            val uri=Uri.parse(source);require(uri.scheme=="content")
            val display=resolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { c -> if(c.moveToFirst()) c.getString(0) else null }.orEmpty()
            val name=display.filterNot { it.isISOControl() || it in "/\\:*?\"<>|" }.take(150).ifBlank { "media.bin" }
            // A unique prefix avoids replacing an existing file on providers with overwrite behavior.
            val output=DocumentsContract.createDocument(resolver,parent,resolver.getType(uri) ?: "application/octet-stream","${java.util.UUID.randomUUID().toString().take(8)}-$name") ?: error("output")
            created.add(output)
            var bytes=0L
            resolver.openInputStream(uri)?.use { input -> resolver.openOutputStream(output,"w")?.use { out ->
                val buffer=ByteArray(65536)
                while(true) { coroutineContext.ensureActive();val n=input.read(buffer);if(n<0) break;out.write(buffer,0,n);bytes+=n }
            } ?: error("output") } ?: error("input")
            require(bytes>0)
            resolver.openFileDescriptor(output,"r")?.use { require(it.statSize<0 || it.statSize==bytes) } ?: error("verification")
            onProgress(index+1,sources.size)
        }
    } catch(e: Exception) { created.forEach { runCatching { DocumentsContract.deleteDocument(resolver,it) } };throw e }
}
