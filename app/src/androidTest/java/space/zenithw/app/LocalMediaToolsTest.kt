package space.zenithw.app

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Real native codecs and SAF publication, using short synthetic media only. */
class LocalMediaToolsTest {
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ZenithApplication
    private fun native(binary: String,args: List<String>): String {
        val builder=ProcessBuilder(listOf(File(app.applicationInfo.nativeLibraryDir,binary).absolutePath)+args).redirectErrorStream(true)
        val packages=File(app.noBackupFilesDir,"youtubedl-android/packages")
        builder.environment()["LD_LIBRARY_PATH"]=listOf("python","ffmpeg").joinToString(":") { File(packages,"$it/usr/lib").absolutePath }
        builder.environment()["TMPDIR"]=app.cacheDir.absolutePath
        val process=builder.start();val text=StringBuffer()
        val reader=Thread { process.inputStream.bufferedReader().useLines { lines -> lines.forEach { text.append(it).append('\n');check(text.length<1024*1024) } } }.apply { isDaemon=true;start() }
        try {
            val deadline=android.os.SystemClock.elapsedRealtime()+30000
            var exit: Int?=null
            while(exit==null) { check(android.os.SystemClock.elapsedRealtime()<deadline);exit=try { process.exitValue() } catch(_: IllegalThreadStateException) { Thread.sleep(20);null } }
            reader.join(2000);assertFalse(reader.isAlive);assertEquals(text.toString(),0,exit)
            return text.toString()
        } finally { process.destroy();reader.interrupt() }
    }
    private fun probe(uri: Uri): JSONObject {
        val local=File(app.cacheDir,"probe-${UUID.randomUUID()}")
        try {
            app.contentResolver.openInputStream(uri)!!.use { input -> local.outputStream().use { input.copyTo(it) } }
            return JSONObject(native("libffprobe.so",listOf("-v","error","-show_streams","-show_format","-of","json",local.absolutePath)))
        } finally { local.delete() }
    }
    @Test fun videoRemuxTrimAnimationSubtitlesAndCancellation()=runBlocking {
        if(android.os.Build.VERSION.SDK_INT<29) return@runBlocking
        app.engine.runtimeVersion();val tools=LocalMediaTools(app);val caps=tools.capabilities()
        println("MEDIA_CAPABILITIES "+listOf("mp4","mp3","m4a","wav","flac","gif","webp","attach","burn","srt","vtt").associateWith { caps.supports(it) })
        val folder=File(app.cacheDir,"media-fixture-${UUID.randomUUID()}").apply { mkdirs() }
        val created=mutableListOf<Uri>()
        fun document(extension: String): Uri=app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,"zenith-test-${UUID.randomUUID()}.$extension")
            put(MediaStore.MediaColumns.MIME_TYPE,"application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH,"Download/ZenithW-Test")
        })!!.also(created::add)
        try {
            val original=File(folder,"fixture.mp4")
            native("libffmpeg.so",listOf("-hide_banner","-loglevel","error","-nostdin","-y","-f","lavfi","-i","color=c=blue:s=160x90:r=10:d=3",
                "-f","lavfi","-i","sine=frequency=440:duration=3","-c:v",if("libx264" in caps.encoders) "libx264" else "mpeg4","-c:a","aac","-shortest",original.absolutePath))
            val input=document("mp4");val originalBytes=original.readBytes()
            app.contentResolver.openOutputStream(input)!!.use { it.write(originalBytes) }
            android.util.Log.i("ZenithMediaTest","CASE remux_mkv");val mkv=document("mkv");tools.execute(LocalMediaTools.Request(input,mkv,"remux_mkv")) {}
            android.util.Log.i("ZenithMediaTest","CASE remux_mp4");val remuxed=document("mp4");tools.execute(LocalMediaTools.Request(mkv,remuxed,"remux_mp4")) {}
            assertEquals(2,probe(remuxed).getJSONArray("streams").length())
            android.util.Log.i("ZenithMediaTest","CASE mute");val muted=document("mp4");tools.execute(LocalMediaTools.Request(input,muted,"mute")) {}
            val streams=probe(muted).getJSONArray("streams")
            assertTrue((0 until streams.length()).none { streams.getJSONObject(it).getString("codec_type")=="audio" })
            for(mode in listOf("trim","trim_exact","mp4","gif","webp").filter { caps.supports(it) }) {
                android.util.Log.i("ZenithMediaTest","CASE $mode")
                val output=document(if(mode in setOf("trim","trim_exact")) "mp4" else mode)
                val clip=mode in setOf("trim","trim_exact","gif","webp")
                tools.execute(LocalMediaTools.Request(input,output,mode,start=if(clip) .5 else 0.0,end=if(clip) 1.5 else 0.0,resolution=240)) {}
                assertTrue(app.contentResolver.openFileDescriptor(output,"r")!!.use { it.statSize>0 })
                if(mode=="trim_exact") assertEquals(1.0,probe(output).getJSONObject("format").getDouble("duration"),.2)
            }
            val subtitle=document("srt")
            app.contentResolver.openOutputStream(subtitle)!!.use { it.write("1\n00:00:00,100 --> 00:00:01,000\nZenith fixture\n".toByteArray()) }
            if(caps.supports("vtt")) {
                android.util.Log.i("ZenithMediaTest","CASE vtt");val vtt=document("vtt");tools.execute(LocalMediaTools.Request(subtitle,vtt,"vtt",offset=.2)) {}
                val text=app.contentResolver.openInputStream(vtt)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                assertTrue(text.contains("WEBVTT"));assertTrue(text.contains("00:00.300"))
            }
            if(caps.supports("attach")) {
                android.util.Log.i("ZenithMediaTest","CASE attach")
                val output=document("mp4");tools.execute(LocalMediaTools.Request(input,output,"attach",subtitle=subtitle)) {}
                val subs=probe(output).getJSONArray("streams")
                assertTrue((0 until subs.length()).any { subs.getJSONObject(it).getString("codec_type")=="subtitle" })
            }
            if(caps.supports("burn")) {
                android.util.Log.i("ZenithMediaTest","CASE burn")
                val output=document("mp4");tools.execute(LocalMediaTools.Request(input,output,"burn",subtitle=subtitle,resolution=240)) {}
                assertTrue(app.contentResolver.openFileDescriptor(output,"r")!!.use { it.statSize>0 })
            }
            val clips=File(folder,"clips").apply { mkdirs() }
            original.copyTo(File(clips,"clip-001.mp4"));original.copyTo(File(clips,"clip-002.mp4"))
            assertTrue(tools.mergeClips(clips,MediaMode.VIDEO).length()>0)
            val cancelled=document("mp4")
            val before=File(app.cacheDir,"media-tools").list().orEmpty().toSet()
            val job=launch(Dispatchers.IO) { tools.execute(LocalMediaTools.Request(input,cancelled,"mp4")) {} }
            delay(20);job.cancelAndJoin()
            assertTrue(File(app.cacheDir,"media-tools").list().orEmpty().toSet().subtract(before).isEmpty())
            assertArrayEquals(originalBytes,app.contentResolver.openInputStream(input)!!.use { it.readBytes() })
        } finally {
            created.forEach { app.contentResolver.delete(it,null,null) }
            require(folder.canonicalFile.parentFile==app.cacheDir.canonicalFile);folder.deleteRecursively()
        }
    }
}
