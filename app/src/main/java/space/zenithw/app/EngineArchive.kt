package space.zenithw.app

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/** yt-dlp is a Python zipapp: its ZIP is preceded by an executable shebang. */
object EngineArchive {
    private val versionPattern=Regex("(?m)^__version__\\s*=\\s*['\"]([0-9]{4}\\.[0-9]{2}\\.[0-9]{2}(?:\\.[0-9]+)?)['\"]")
    fun version(file: File): String=file.inputStream().use(::version)
    fun version(input: InputStream): String {
        val source=BufferedInputStream(input)
        try {
            source.mark(4)
            val first=source.read(); val second=source.read()
            source.reset()
            if(first=='#'.code && second=='!'.code) {
                var length=0
                while(true) {
                    val next=source.read()
                    if(next<0 || ++length>1024) throw IOException("UPDATE_ARCHIVE: invalid zipapp header")
                    if(next=='\n'.code) break
                }
            }
            source.mark(4)
            val magic=IntArray(4) { source.read() }
            source.reset()
            if(!magic.contentEquals(intArrayOf(0x50,0x4b,0x03,0x04)))
                throw IOException("UPDATE_ARCHIVE: missing ZIP header")
            ZipInputStream(source).use { zip ->
                var entries=0
                var inflated=0
                val buffer=ByteArray(8192)
                while(true) {
                    val entry=zip.nextEntry ?: break
                    if(++entries>10000) throw IOException("UPDATE_ARCHIVE: too many entries")
                    val isVersion=entry.name=="yt_dlp/version.py"
                    val content=if(isVersion) ByteArrayOutputStream() else null
                    while(true) {
                        val count=zip.read(buffer)
                        if(count<0) break
                        inflated+=count
                        if(inflated>32*1024*1024) throw IOException("UPDATE_ARCHIVE: inflated data too large")
                        if(content!=null) {
                            if(content.size()+count>8192) throw IOException("UPDATE_ARCHIVE: version file too large")
                            content.write(buffer,0,count)
                        }
                    }
                    if(content!=null) return versionPattern.find(String(content.toByteArray(),Charsets.UTF_8))?.groupValues?.get(1)
                        ?: throw IOException("UPDATE_ARCHIVE: invalid version declaration")
                }
            }
            throw IOException("UPDATE_ARCHIVE: version file missing")
        } catch(error: IOException) {
            if(error.message?.startsWith("UPDATE_ARCHIVE:")==true) throw error
            throw IOException("UPDATE_ARCHIVE: ${error.javaClass.simpleName}: ${error.message.orEmpty().take(100)}",error)
        }
    }
}
