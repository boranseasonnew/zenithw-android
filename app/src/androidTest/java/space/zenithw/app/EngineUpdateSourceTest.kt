package space.zenithw.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EngineUpdateSourceTest {
    private val repo="yt-dlp/yt-dlp"
    private val tag="2026.08.19"
    private class Reply(url: URL, private val code: Int, private val body: String="", private val location: String?=null, private val bytes: ByteArray?=null): HttpsURLConnection(url) {
        override fun getResponseCode()=code
        override fun getHeaderField(name: String?): String?=if(name.equals("Location",true)) location else null
        override fun getInputStream()=bytes?.inputStream() ?: body.byteInputStream()
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy()=false
        override fun getCipherSuite()="TEST"
        override fun getLocalCertificates(): Array<Certificate>?=null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }
    private fun failure(block: ()->Unit): String {
        try { block() } catch(error: Exception) { return EngineUpdateSource.diagnostic(error) }
        throw AssertionError("Request unexpectedly succeeded")
    }

    @Test fun releasePageFailureUsesAssetRoute() {
        val calls=mutableListOf<String>()
        val http=EngineHttp { url ->
            calls.add(url.toString())
            when(url.path) {
                "/$repo/releases/latest" -> Reply(url,200,"<html>Unexpected page</html>")
                "/$repo/releases/latest/download/SHA2-256SUMS" -> Reply(url,302,location="/$repo/releases/download/$tag/SHA2-256SUMS")
                else -> throw AssertionError("The API must not be needed: $url")
            }
        }
        assertEquals(tag,EngineUpdateSource.latestTag("stable",http))
        assertEquals(2,calls.size)
    }
    @Test fun blockedRedirectsUseOfficialApi() {
        val http=EngineHttp { url -> if(url.host=="github.com") Reply(url,403) else Reply(url,200,"""{"tag_name":"$tag"}""") }
        assertEquals(tag,EngineUpdateSource.latestTag("stable",http))
    }
    @Test fun blockedAssetUsesApiDownloadAndKeepsVersion() {
        val http=EngineHttp { url -> when {
            url.host=="github.com" -> Reply(url,403)
            url.path.endsWith("/tags/$tag") -> Reply(url,200,"""{"tag_name":"$tag","assets":[{"name":"yt-dlp","url":"https://api.github.com/repos/$repo/releases/assets/123"}]}""")
            url.path.endsWith("/assets/123") -> Reply(url,302,location="https://release-assets.githubusercontent.com/file")
            url.host=="release-assets.githubusercontent.com" -> Reply(url,200,"verified in the installer")
            else -> throw AssertionError("Unexpected URL")
        } }
        assertEquals("verified in the installer",String(EngineUpdateSource.asset("stable",tag,"yt-dlp",100,http)))
    }
    @Test fun untrustedRedirectIsNeverRequested() {
        var calls=0
        val http=EngineHttp { url -> calls++; Reply(url,302,location="https://untrusted.example/payload") }
        assertTrue(failure { http.read("https://github.com/test",100) }.contains("UPDATE_HOST"))
        assertEquals(1,calls)
    }
    @Test fun oversizedResponseIsRejectedAndCacheIsDisabled() {
        lateinit var reply: Reply
        val http=EngineHttp { url -> Reply(url,200,"0123456789").also { reply=it } }
        assertTrue(failure { http.read("https://github.com/test",5) }.contains("UPDATE_SIZE"))
        assertFalse(reply.useCaches)
        assertEquals("no-cache",reply.getRequestProperty("Cache-Control"))
    }
    @Test fun failureNamesLookupStageAndHttpStatus() {
        val http=EngineHttp { url -> Reply(url,403) }
        val detail=failure { EngineUpdateSource.latestTag("nightly",http) }
        assertTrue(detail.contains("UPDATE_LOOKUP"))
        assertTrue(detail.contains("HTTP 403"))
        assertTrue(detail.contains("api.github.com"))
        assertFalse(EngineUpdateSource.diagnostic(IOException("bad https://host/path?secret=123")).contains("secret"))
    }
    @Test fun corruptUpdatePreservesWorkingEngine()=runBlocking {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ZenithApplication
        app.engine.initializeAndUpdate(force=false)
        val oldChannel=app.store.channel
        val before=app.engine.runtimeVersion()
        val oldVersion=app.store.lastEngineVersion
        val nightly="2026.09.27.232945"
        val http=EngineHttp { url -> when {
            url.path.endsWith("/latest") -> Reply(url,302,location="/yt-dlp/yt-dlp-nightly-builds/releases/tag/$nightly")
            url.path.endsWith("SHA2-256SUMS") -> Reply(url,200,"${"0".repeat(64)}  yt-dlp\n")
            else -> Reply(url,200,"damaged executable")
        } }
        try {
            app.store.channel="nightly"
            val engine=DownloadEngine(app,app.store,app.vault,http)
            engine.initializeAndUpdate(force=true)
            assertTrue(engine.status.value.ready)
            assertNotNull(engine.status.value.warning)
            assertEquals(before,engine.runtimeVersion())
            assertEquals(oldVersion,app.store.lastEngineVersion)
            assertTrue(app.store.logs.value.last().contains("UPDATE_CHECKSUM"))
        } finally { app.store.channel=oldChannel }
    }
    @Test fun mismatchedVersionKeepsWorkingEngineAndNamesBothVersions()=runBlocking {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ZenithApplication
        app.engine.initializeAndUpdate(force=false)
        val oldChannel=app.store.channel
        val before=app.engine.runtimeVersion()
        val oldVersion=app.store.lastEngineVersion
        val nightly="2026.09.27.232945"
        val stable=app.resources.openRawResource(R.raw.ytdlp).use { it.readBytes() }
        val http=EngineHttp { url -> when {
            url.path.endsWith("/latest") -> Reply(url,302,location="/yt-dlp/yt-dlp-nightly-builds/releases/tag/$nightly")
            url.path.endsWith("SHA2-256SUMS") -> Reply(url,200,"${BundledEngine.SHA256}  yt-dlp\n")
            else -> Reply(url,200,bytes=stable)
        } }
        try {
            app.store.channel="nightly"
            val engine=DownloadEngine(app,app.store,app.vault,http)
            engine.initializeAndUpdate(force=true)
            assertTrue(engine.status.value.ready)
            assertNotNull(engine.status.value.warning)
            assertEquals(before,engine.runtimeVersion())
            assertEquals(oldVersion,app.store.lastEngineVersion)
            val detail=app.store.logs.value.last()
            assertTrue(detail.contains("expected=$nightly"))
            assertTrue(detail.contains("actual=${BundledEngine.VERSION}"))
        } finally { app.store.channel=oldChannel }
    }
}
