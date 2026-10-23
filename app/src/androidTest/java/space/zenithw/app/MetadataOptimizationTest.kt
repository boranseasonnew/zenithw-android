package space.zenithw.app

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class MetadataOptimizationTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val app get()=instrumentation.targetContext.applicationContext as ZenithApplication

    @Test fun completePasteOpensPanelWithoutNetworkOrLosingOptions() {
        val before=app.store.showMediaPreview;val configure=app.store.configureBeforeDownload
        val defaults=app.store.defaultOptions
        try {
            app.store.showMediaPreview=false;app.store.configureBeforeDownload=true
            app.store.defaultOptions=DownloadOptions(mode=MediaMode.AUDIO,audioFormat="mp3",height=720)
            instrumentation.runOnMainSync {
                val model=ZenithViewModel(app);val start=SystemClock.elapsedRealtime()
                model.inputUrl("https://www.youtube.com/watch?v=aqz-KE-bpKQ")
                assertNotNull(model.screen.value.preview)
                assertTrue(SystemClock.elapsedRealtime()-start<300)
                assertEquals(AnalysisState.Preparing,model.screen.value.analysisState)
                assertEquals("mp3",model.screen.value.defaults.audioFormat)
                assertTrue(model.screen.value.preview!!.formats.isEmpty())
                assertEquals(0,estimatedDownloadBytes(model.screen.value.preview!!,model.screen.value.defaults))
                model.dismissPreview()
                assertEquals(AnalysisState.Idle,model.screen.value.analysisState)
            }
        } finally { app.store.showMediaPreview=before;app.store.configureBeforeDownload=configure;app.store.defaultOptions=defaults }
    }

    @Test fun realFormatsCacheAvoidsNetworkAndPreservesQueryIdentity()=runBlocking {
        MetadataFixture().use { fixture ->
            val events=mutableListOf<AnalysisState>()
            val one=app.engine.inspect(fixture.origin+"/one?signature=first",null,DownloadOptions(),onUpdate={ state,_ -> events.add(state) })
            assertTrue(one.metadataReady)
            assertTrue(one.formats.any { it.height==360 })
            assertEquals(AnalysisState.Ready,events.last())
            val count=fixture.requests.get()
            val cached=app.engine.inspect(fixture.origin+"/one?signature=first",null,DownloadOptions())
            assertEquals(one,cached)
            assertEquals(count,fixture.requests.get())
            val two=app.engine.inspect(fixture.origin+"/two?signature=second",null,DownloadOptions())
            assertNotEquals(one.title,two.title)
            assertTrue(fixture.requests.get()>count)
            assertEquals(fixture.origin+"/two?signature=second",two.url)
        }
    }

    @Test fun cancellationUnblocksReplacementAndDoesNotLeaveWorkerBusy()=runBlocking {
        MetadataFixture(5000).use { slow ->
            val running=launch(Dispatchers.IO) { app.engine.inspect(slow.origin+"/slow",null,DownloadOptions()) }
            withTimeout(10000) { while(slow.requests.get()==0) delay(20) }
            val cancel=SystemClock.elapsedRealtime();running.cancelAndJoin()
            assertTrue("Cancellation must release the caller promptly",SystemClock.elapsedRealtime()-cancel<1500)
            MetadataFixture().use { fast ->
                val value=withTimeout(15000) { app.engine.inspect(fast.origin+"/replacement",null,DownloadOptions()) }
                assertTrue(value.title.contains("replacement"));assertTrue(value.formats.isNotEmpty())
            }
        }
    }

    @Test fun concurrentIdenticalRequestsShareOneExtraction()=runBlocking {
        MetadataFixture().use { fixture ->
            app.engine.inspect(fixture.origin+"/reference",null,DownloadOptions())
            val perExtraction=fixture.requests.get();fixture.requests.set(0)
            val one=async(Dispatchers.IO) { app.engine.inspect(fixture.origin+"/shared",null,DownloadOptions()) }
            val two=async(Dispatchers.IO) { app.engine.inspect(fixture.origin+"/shared",null,DownloadOptions()) }
            assertEquals(one.await(),two.await())
            assertEquals(perExtraction,fixture.requests.get())
        }
    }

    @Test fun latestOfFiveLinksWinsAndUserChoicesSurvive()=runBlocking {
        val before=app.store.showMediaPreview;val configure=app.store.configureBeforeDownload;val defaults=app.store.defaultOptions
        val remember=app.store.rememberDownloadChoices
        try {
            app.store.showMediaPreview=true;app.store.configureBeforeDownload=true;app.store.rememberDownloadChoices=true
            lateinit var model: ZenithViewModel
            MetadataFixture(150).use { fixture ->
                instrumentation.runOnMainSync { model=ZenithViewModel(app) }
                repeat(5) { i ->
                    instrumentation.runOnMainSync { model.url(fixture.origin+"/video-$i");model.analyze() }
                    delay(40)
                }
                instrumentation.runOnMainSync { model.downloadChoices(DownloadOptions(mode=MediaMode.AUDIO,audioFormat="flac",height=720)) }
                withTimeout(15000) { while(model.screen.value.analyzing) delay(20) }
                val state=model.screen.value
                assertEquals(AnalysisState.Ready,state.analysisState)
                assertTrue(state.preview!!.url.endsWith("video-4"))
                assertTrue(state.preview!!.title.contains("video-4"))
                assertEquals("flac",state.defaults.audioFormat)
                assertEquals(720,state.defaults.height)
                instrumentation.runOnMainSync { model.dismissPreview() }
            }
        } finally { app.store.showMediaPreview=before;app.store.configureBeforeDownload=configure;app.store.defaultOptions=defaults;app.store.rememberDownloadChoices=remember }
    }

    @Test fun timingLogsContainNoMetadataOrCredentials() {
        app.store.clearLogs()
        val trace=AnalysisTrace(app.store)
        trace.preview(MediaPreview("https://example.org/video?token=secret-value","Private title",thumbnail="https://example.org/cover?signature=private",duration=90))
        trace.mark("panel_first_layout");trace.mark("panel_first_layout")
        val lines=app.store.logs.value.joinToString("\n")
        assertFalse(lines.contains("secret-value"));assertFalse(lines.contains("Private title"));assertFalse(lines.contains("example.org"))
        assertEquals(1,app.store.logs.value.count { "event=panel_first_layout" in it })
        assertTrue(lines.contains("event=first_duration"))
    }
}
