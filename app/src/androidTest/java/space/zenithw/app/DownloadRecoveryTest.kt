package space.zenithw.app

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class DownloadRecoveryTest {
    companion object {
        private var previousAutoUpdate: Boolean? = null

        @BeforeClass @JvmStatic fun settleStartupUpdate() {
            val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ZenithApplication
            previousAutoUpdate = app.store.autoUpdate
            app.store.autoUpdate = false
            // A startup replacement legitimately invalidates snapshots. Join that
            // update before counting source requests in this suite.
            runBlocking { app.engine.initializeAndUpdate(false) }
        }

        @AfterClass @JvmStatic fun restoreAutoUpdate() {
            val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ZenithApplication
            previousAutoUpdate?.let { app.store.autoUpdate = it }
        }
    }

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as ZenithApplication
    private val options = DownloadOptions(
        subtitles = true, subtitleLanguages = "en", subtitleFormat = "vtt", embedSubtitles = false,
        embedMetadata = false, embedChapters = false, embedUploader = false, retries = 0,
        fragmentRetries = 0, timeout = 5,
    )

    @Test fun cachedPreviewAndSubtitlesUseRealLocalTransfer() = runBlocking {
        withTimeout(60000) {
            mediaFixture().use { source ->
                val url = source.origin + "/captioned"
                val started = SystemClock.elapsedRealtime()
                val preview = app.engine.inspect(url, null, options)
                val cold = SystemClock.elapsedRealtime() - started
                val requests = source.requestsFor("/captioned")
                val warmStarted = SystemClock.elapsedRealtime()
                assertEquals(preview.title, app.engine.inspect(url, null, options).title)
                val warm = SystemClock.elapsedRealtime() - warmStarted
                assertEquals(requests, source.requestsFor("/captioned"))
                withOutput { output ->
                    val transferStarted = SystemClock.elapsedRealtime()
                    val outcome = download(url, options, output)
                    val transfer = SystemClock.elapsedRealtime() - transferStarted
                    assertTrue(outcome.notes.toString(), outcome.notes.isEmpty())
                    assertTrue(output.listFiles().orEmpty().any { it.extension == "mp4" && it.length() > 0 })
                    assertTrue(output.listFiles().orEmpty().any { it.extension == "vtt" && "Fixture caption" in it.readText() })
                    assertEquals("Download extracted the same page again", requests, source.requestsFor("/captioned"))
                    android.util.Log.i("ZenithDeviceCheck", "fixture_cold_ms=$cold fixture_warm_ms=$warm fixture_transfer_ms=$transfer")
                }
            }
        }
    }

    @Test fun downloadJoinsAnExtractionAlreadyInProgress() = runBlocking {
        withTimeout(60000) {
            mediaFixture(delayMillis = 300).use { source ->
                app.engine.inspect(source.origin + "/captioned-reference", null, options)
                val oneExtraction = source.requestsFor("/captioned-reference")
                assertTrue(oneExtraction > 0)
                val url = source.origin + "/captioned"
                val preview = async { app.engine.inspect(url, null, options) }
                while (source.requestsFor("/captioned") == 0 && !preview.isCompleted) delay(15)
                withOutput { output -> download(url, options, output) }
                assertTrue(preview.await().metadataReady)
                assertEquals("An in-flight preview and download each requested the page", oneExtraction, source.requestsFor("/captioned"))
            }
        }
    }

    @Test fun optionalSubtitle429WarnsAndSubtitleOnlyFails() = runBlocking {
        withTimeout(60000) {
            mediaFixture().use { source ->
                val url = source.origin + "/captioned-broken"
                app.engine.inspect(url, null, options)
                val requests = source.requestsFor("/captioned-broken")
                withOutput { output ->
                    val outcome = download(url, options, output)
                    assertTrue(outcome.notes.toString(), outcome.notes.any { "altyazı" in it.lowercase() && "429" in it })
                    assertTrue(output.listFiles().orEmpty().any { it.extension == "mp4" && it.length() > 0 })
                    assertEquals(requests, source.requestsFor("/captioned-broken"))
                }
                withOutput { output ->
                    val error = runCatching { download(url, options.copy(mode = MediaMode.SUBTITLES), output) }.exceptionOrNull()
                    assertTrue(error.toString(), error is DownloadFailureException)
                    val failure = (error as DownloadFailureException).failure
                    assertEquals(RecoveryKind.SUBTITLE, failure.kind)
                    assertEquals(429, failure.httpStatus)
                    assertFalse(output.listFiles().orEmpty().any { it.extension == "mp4" })
                }
            }
        }
    }

    @Test fun removedSelectedSessionStopsBeforeSourceRequest() = runBlocking {
        withTimeout(30000) {
            mediaFixture().use { source ->
                val profile = app.vault.capture("fixture-${UUID.randomUUID()}.example.org", "session=fixture")
                app.vault.remove(profile.id)
                withOutput { output ->
                    val error = runCatching {
                        download(source.origin + "/captioned", options.copy(cookieId = profile.id), output)
                    }.exceptionOrNull()
                    assertTrue(error.toString(), error is CookieSessionException)
                    assertEquals(0, source.requests.get())
                }
            }
        }
    }

    @Test fun cachedMedia403RefreshesTheSourceOnlyOnce() = runBlocking {
        withTimeout(60000) {
            val transfers = AtomicInteger()
            mediaFixture(mediaStatus = { if (transfers.getAndIncrement() == 0) "403 Forbidden" else "200 OK" }).use { source ->
                val url = source.origin + "/captioned"
                val video = options.copy(subtitles = false)
                app.engine.inspect(url, null, video)
                val previewRequests = source.requestsFor("/captioned")
                withOutput { output ->
                    download(url, video, output)
                    assertTrue(output.listFiles().orEmpty().any { it.extension == "mp4" && it.length() > 0 })
                    assertEquals(2, transfers.get())
                    assertEquals(2 * previewRequests, source.requestsFor("/captioned"))
                }
            }
        }
    }

    @Test fun networkBackoffDoesNotBlockNextJobOrRetryMissingSession() = runBlocking {
        mediaFixture(mediaStatus = { "503 Service Unavailable" }).use { source ->
            val video = options.copy(subtitles = false)
            val transient = DownloadJob(url = source.origin + "/captioned", title = "Temporary failure fixture", options = video)
            val missing = DownloadJob(url = source.origin + "/captioned-missing", title = "Missing session fixture",
                options = video.copy(cookieId = UUID.randomUUID().toString()))
            try {
                DownloadWorker.enqueue(app, transient)
                withTimeout(25000) {
                    while (app.store.get(transient.id)?.let { it.phase != "retry_wait" && it.state !in setOf(JobState.FAILED, JobState.COMPLETED) } != false) delay(100)
                }
                assertEquals(app.store.taskLogs.value[transient.id].orEmpty().joinToString("\n"), "retry_wait", app.store.get(transient.id)?.phase)
                assertEquals(1, app.store.get(transient.id)?.retryCount)
                DownloadWorker.enqueue(app, missing)
                withTimeout(12000) {
                    while (app.store.get(missing.id)?.state != JobState.FAILED) delay(100)
                }
                assertEquals("SESSION", app.store.get(missing.id)?.failureKind)
                assertEquals(0, app.store.get(missing.id)?.retryCount)
                assertEquals(0, source.requestsFor("/captioned-missing"))
            } finally {
                DownloadWorker.cancel(app, transient.id)
                DownloadWorker.cancel(app, missing.id)
            }
        }
    }

    @Test fun cancelledJobCannotEnterPublication() {
        val job = DownloadJob(url = "https://example.org/cancelled-fixture", title = "Cancelled fixture", state = JobState.RUNNING)
        try {
            app.store.put(job)
            app.store.change(job.id) { it.copy(state = JobState.CANCELLED, detail = "İptal edildi") }
            assertFalse(DownloadWorker.enterSaving(app.store, job.id, listOf("late engine result")))
            val stored = requireNotNull(app.store.get(job.id))
            assertEquals(JobState.CANCELLED, stored.state)
            assertEquals("İptal edildi", stored.detail)
            assertTrue(stored.notes.isEmpty())
        } finally {
            app.store.removeEntry(job.id)
        }
    }

    @Test fun aFreeSlotWakesCapacityWaitWithoutWaitingForFailureBackoff() = runBlocking {
        val oldConcurrency = app.store.concurrentDownloads
        val releaseTransfer = CountDownLatch(1)
        val mediaRequests = AtomicInteger()
        app.store.concurrentDownloads = 1
        try {
            mediaFixture(mediaStatus = {
                mediaRequests.incrementAndGet()
                releaseTransfer.await(60, TimeUnit.SECONDS)
                "200 OK"
            }).use { source ->
                val video = options.copy(subtitles = false)
                val active = DownloadJob(url = source.origin + "/captioned", title = "Active fixture", options = video.copy(timeout = 45))
                val queued = DownloadJob(url = source.origin + "/captioned-queued", title = "Waiting fixture",
                    options = video.copy(cookieId = UUID.randomUUID().toString()))
                try {
                    DownloadWorker.enqueue(app, active)
                    withTimeout(25000) { while (mediaRequests.get() == 0) delay(50) }
                    DownloadWorker.enqueue(app, queued)
                    withTimeout(15000) { while (app.store.get(queued.id)?.phase != "queue_wait") delay(50) }
                    assertEquals(0, app.store.get(queued.id)?.retryCount)
                    DownloadWorker.cancel(app, active.id)
                    releaseTransfer.countDown()
                    // The original WorkManager request was backed off for 30s.
                    withTimeout(15000) { while (app.store.get(queued.id)?.state != JobState.FAILED) delay(50) }
                    assertEquals("SESSION", app.store.get(queued.id)?.failureKind)
                    assertEquals(0, app.store.get(queued.id)?.retryCount)
                    assertEquals(0, source.requestsFor("/captioned-queued"))
                    assertEquals(JobState.CANCELLED, app.store.get(active.id)?.state)
                    assertTrue(app.store.get(active.id)?.files.orEmpty().isEmpty())
                } finally {
                    DownloadWorker.cancel(app, active.id)
                    DownloadWorker.cancel(app, queued.id)
                    releaseTransfer.countDown()
                }
            }
        } finally {
            app.store.concurrentDownloads = oldConcurrency
        }
    }

    @Test fun publicYouTubeProbe() = runBlocking {
        val requestedMode = InstrumentationRegistry.getArguments().getString("zenithPublicYouTubeMode")
        assumeTrue(requestedMode == "video" || requestedMode == "captions")
        withTimeout(90000) {
            val captions = requestedMode == "captions"
            val network = options.copy(height = 144, subtitles = captions, autoSubtitles = captions, maxFilesizeMb = 8)
            val url = "https://www.youtube.com/watch?v=jNQXAC9IVRw"
            val started = SystemClock.elapsedRealtime()
            val preview = app.engine.inspect(url, null, network)
            assertTrue(preview.metadataReady)
            val extracted = SystemClock.elapsedRealtime() - started
            withOutput { output ->
                val transferStarted = SystemClock.elapsedRealtime()
                val outcome = download(url, network, output)
                assertTrue(output.listFiles().orEmpty().any { it.extension == "mp4" && it.length() > 0 })
                if (captions) assertTrue(outcome.notes.toString(), output.listFiles().orEmpty().any { it.extension == "vtt" && it.length() > 0 })
                android.util.Log.i("ZenithDeviceCheck", "youtube_mode=$requestedMode metadata_ms=$extracted transfer_ms=${SystemClock.elapsedRealtime() - transferStarted}")
            }
        }
    }

    private fun mediaFixture(delayMillis: Long = 0, mediaStatus: () -> String = { "200 OK" }) = MetadataFixture(
        delayMillis = delayMillis,
        media = instrumentation.context.assets.open("download-fixture.mp4").use { it.readBytes() },
        mediaStatus = mediaStatus,
    )

    private suspend fun download(url: String, options: DownloadOptions, output: File): DownloadOutcome =
        app.engine.download(DownloadJob(url = url, title = "Fixture", options = options), output, {}, { _, _, _ -> })

    private suspend fun withOutput(block: suspend (File) -> Unit) {
        val output = File(app.cacheDir, "download-recovery-${UUID.randomUUID()}").apply { mkdirs() }
        try { block(output) } finally { output.deleteRecursively() }
    }
}
