package space.zenithw.app

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class MetadataOptimizationTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val app get()=instrumentation.targetContext.applicationContext as ZenithApplication

    @Test fun selectingSessionKeepsTheOpenPanelAndCurrentChoices() {
        val before=app.store.showMediaPreview;val configure=app.store.configureBeforeDownload
        val selected=app.store.selectedCookieId;val remember=app.store.rememberDownloadChoices
        val profile = app.vault.capture("fixture-${System.nanoTime()}.example", "fixture=session")
        try {
            app.store.showMediaPreview=false;app.store.configureBeforeDownload=true
            app.store.rememberDownloadChoices=false
            instrumentation.runOnMainSync {
                val model=ZenithViewModel(app)
                model.cookie(null);model.url("https://example.org/video");model.analyze()
                val panel=model.screen.value.analysisId
                model.downloadChoices(DownloadOptions(mode=MediaMode.AUDIO,audioFormat="m4a"))
                model.cookie(profile.id)
                assertEquals(profile.id,model.screen.value.cookieId)
                assertNotNull(model.screen.value.preview)
                assertEquals(panel,model.screen.value.analysisId)
                assertFalse(model.screen.value.preview!!.metadataReady)
                assertEquals(AnalysisState.Preparing,model.screen.value.analysisState)
                val options=ZenithViewModel::class.java.getDeclaredField("inspectionOptions").apply { isAccessible=true }
                assertEquals("m4a",(options.get(model) as DownloadOptions).audioFormat)
                // Explicit anonymous selection also invalidates source data and
                // keeps configuration open; no unrequested account is selected.
                model.cookie(null)
                assertNotNull(model.screen.value.preview)
                assertEquals(panel,model.screen.value.analysisId)
                model.dismissPreview()
            }
        } finally {
            app.vault.remove(profile.id)
            app.store.showMediaPreview=before;app.store.configureBeforeDownload=configure
            app.store.selectedCookieId=selected;app.store.rememberDownloadChoices=remember
        }
    }

    @Test fun deletedSelectedSessionBlocksQueueWithoutSwitchingToAnonymous() {
        val before = app.store.showMediaPreview
        val configure = app.store.configureBeforeDownload
        val selected = app.store.selectedCookieId
        val profile = app.vault.capture("fixture-${System.nanoTime()}.example", "fixture=session")
        try {
            app.store.showMediaPreview = false
            app.store.configureBeforeDownload = true
            app.store.selectedCookieId = profile.id
            instrumentation.runOnMainSync {
                val model = ZenithViewModel(app)
                model.url("https://example.org/video")
                model.analyze()
                model.removeProfile(profile.id)
                val jobs = app.store.jobs.value
                model.enqueue(DownloadOptions())
                assertEquals(jobs, app.store.jobs.value)
                assertEquals(profile.id, model.screen.value.cookieId)
                assertFalse(model.screen.value.submitting)
                assertEquals(AnalysisState.Error, model.screen.value.analysisState)
                assertEquals(AppLanguage.context(app).getString(R.string.missing_session), model.screen.value.message)
                assertTrue(runCatching { app.vault.requireAvailable(profile.id) }.exceptionOrNull() is CookieSessionException)
                model.cookie(null)
                assertNull(model.screen.value.cookieId)
            }
        } finally {
            app.vault.remove(profile.id)
            app.store.showMediaPreview = before
            app.store.configureBeforeDownload = configure
            app.store.selectedCookieId = selected
        }
    }

    @Test fun cachedTransferLinksExpireBeforeTheirDeadline() {
        val now = 1_780_000_000L
        assertTrue(MetadataSnapshotPolicy.fresh(89_999L, 0, now))
        assertFalse(MetadataSnapshotPolicy.fresh(90_000L, 0, now))
        assertTrue(MetadataSnapshotPolicy.fresh(240_000L, now + 31, now))
        assertFalse(MetadataSnapshotPolicy.fresh(240_000L, now + 30, now))
        assertFalse(MetadataSnapshotPolicy.fresh(300_000L, now + 300, now))
        assertFalse(MetadataSnapshotPolicy.fresh(-1, now + 300, now))
    }


    @Test fun repeatAnalyzeAndStaleDismissPreserveNewPanel() {
        val before=app.store.showMediaPreview;val configure=app.store.configureBeforeDownload
        try {
            app.store.showMediaPreview=false;app.store.configureBeforeDownload=true
            instrumentation.runOnMainSync {
                val model=ZenithViewModel(app)
                model.url("https://youtu.be/LAqBFGH1r_c");model.analyze()
                val old=model.screen.value.analysisId;val preview=model.screen.value.preview
                model.analyze()
                assertSame(preview,model.screen.value.preview)
                assertEquals(old,model.screen.value.analysisId)
                model.url("https://youtu.be/aqz-KE-bpKQ");model.analyze()
                model.dismissPreview(old)
                assertNotNull(model.screen.value.preview)
                assertNotEquals(old,model.screen.value.analysisId)
                model.dismissPreview()
                assertNull(model.screen.value.preview)
            }
        } finally { app.store.showMediaPreview=before;app.store.configureBeforeDownload=configure }
    }

    @Test fun completedCardsDisappearButFailuresAndActiveDownloadsRemain() {
        val job=DownloadJob(url="https://youtu.be/LAqBFGH1r_c",title="Example")
        for(state in listOf(JobState.COMPLETED,JobState.CANCELLED,JobState.SKIPPED))
            assertNull(homeDownloadJob(listOf(job.copy(state=state)),job.id))
        for(state in listOf(JobState.QUEUED,JobState.RUNNING,JobState.SAVING,JobState.FAILED))
            assertEquals(state,homeDownloadJob(listOf(job.copy(state=state)),job.id)!!.state)
        val next=job.copy(id="next",state=JobState.RUNNING)
        assertEquals("next",homeDownloadJob(listOf(job.copy(state=JobState.COMPLETED),next),job.id)!!.id)
    }

    @Test fun archivedVideoIsSkippedWithoutAttemptingPublication()=runBlocking {
        val archive=java.io.File(app.noBackupFilesDir,"download-archive.txt")
        val previous=archive.takeIf { it.exists() }?.readBytes()
        val output=java.io.File(app.cacheDir,"archive-regression").apply { mkdirs() }
        try {
            MetadataFixture().use { fixture ->
                val url=fixture.origin+"/archived"
                app.engine.inspect(url,null,DownloadOptions()) // Initialize bundled native engine.
                withContext(Dispatchers.IO) {
                    val request=com.yausername.youtubedl_android.YoutubeDLRequest(url)
                    request.addOption("--skip-download");request.addOption("--force-write-archive")
                    request.addOption("--download-archive",archive.absolutePath)
                    com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(request)
                }
                assertTrue(archive.length()>0)
                val outcome=app.engine.download(DownloadJob(url=url,title="Archived",options=DownloadOptions(downloadArchive=true)),
                    output,started={},progress={ _,_,_ -> })
                assertTrue(outcome.archiveSkipped)
                assertTrue(output.listFiles().orEmpty().none { DownloadPolicy.isPublicOutput(it.name) })
            }
        } finally {
            if(previous==null) archive.delete() else archive.writeBytes(previous)
            output.deleteRecursively()
        }
    }

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
            val cached=app.engine.inspect(fixture.origin+"/one?signature=first",null,DownloadOptions(subtitleLanguages="tr,en"))
            assertEquals(one,cached)
            assertEquals(count,fixture.requests.get())
            app.engine.inspect(fixture.origin+"/one?signature=first",null,DownloadOptions(autoSubtitles=true))
            assertTrue(fixture.requests.get()>count)
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

    @Test fun sourceFailureRetainsRuntimeAndPlaylistCacheAvoidsAnotherRequest()=runBlocking {
        MetadataFixture().use { fixture ->
            assertNotNull(runCatching { app.engine.inspect(fixture.origin+"/fail",null,DownloadOptions()) }.exceptionOrNull())
            app.store.clearLogs()
            val recovered=app.engine.inspect(fixture.origin+"/recovered",null,DownloadOptions())
            assertTrue(recovered.metadataReady)
            assertTrue(app.store.logs.value.any { "event=python_import ms=0" in it })
            val playlist=app.engine.playlist(fixture.origin+"/collection",null,DownloadOptions())
            assertTrue(playlist.isNotEmpty())
            val count=fixture.requests.get()
            assertEquals(playlist,app.engine.playlist(fixture.origin+"/collection",null,DownloadOptions()))
            assertEquals(count,fixture.requests.get())
            val duration=app.engine.inspect(fixture.origin+"/declared-duration",null,DownloadOptions())
            assertEquals(125,duration.duration)
        }
    }

    @Test fun cancellationAndInvalidInputHaveExplicitStates() {
        val before=app.store.showMediaPreview;val configure=app.store.configureBeforeDownload
        try {
            app.store.showMediaPreview=false;app.store.configureBeforeDownload=true
            instrumentation.runOnMainSync {
                val model=ZenithViewModel(app)
                model.url("https://example.org/video");model.analyze();model.cancelAnalysis()
                assertEquals(AnalysisState.Cancelled,model.screen.value.analysisState)
                assertFalse(model.screen.value.analyzing)
                model.url("not an address");model.analyze()
                assertEquals(AnalysisState.Error,model.screen.value.analysisState)
                assertNull(model.screen.value.preview)
                model.tab(0);assertEquals(AnalysisState.Idle,model.screen.value.analysisState)
            }
        } finally { app.store.showMediaPreview=before;app.store.configureBeforeDownload=configure }
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
