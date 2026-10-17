package space.zenithw.app

import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class AppReleaseStatus { AVAILABLE, CURRENT, AHEAD, STORE_MANAGED, UNSUPPORTED_VARIANT }

data class AppReleaseResult(
    val status: AppReleaseStatus,
    val currentVersion: String,
    val latestVersion: String?,
    val url: String
)

/** A manual version check, never an APK downloader or installer. */
object AppReleaseChecker {
    const val RELEASES_PAGE = "https://github.com/boranseasonnew/zenithw-android/releases"
    const val FDROID_PAGE = "https://f-droid.org/packages/space.zenithw.app.fdroid/"
    private const val API = "https://api.github.com/repos/boranseasonnew/zenithw-android/releases/latest"
    private const val LIMIT = 256 * 1024
    private const val TIMEOUT = 20_000L
    private const val CACHE_TIME = 5 * 60_000L
    private val mutex = Mutex()
    private var cached: AppReleaseResult? = null
    private var cacheKey = ""
    private var cachedAt = 0L

    suspend fun check(
        currentVersion: String = BuildConfig.VERSION_NAME,
        packageName: String = BuildConfig.APPLICATION_ID,
        fdroid: Boolean = BuildConfig.F_DROID
    ): AppReleaseResult {
        if (fdroid || packageName == "space.zenithw.app.fdroid") {
            return AppReleaseResult(AppReleaseStatus.STORE_MANAGED, currentVersion, null, FDROID_PAGE)
        }
        if (packageName != "space.zenithw.app.stable") {
            return AppReleaseResult(AppReleaseStatus.UNSUPPORTED_VARIANT, currentVersion, null, RELEASES_PAGE)
        }
        val current = version(currentVersion) ?: throw IOException("Invalid installed application version.")
        val abis = Build.SUPPORTED_ABIS.toList()
        val key = "$packageName|$currentVersion|${abis.joinToString(",")}"
        return mutex.withLock {
            cached?.takeIf { key == cacheKey && SystemClock.elapsedRealtime() - cachedAt < CACHE_TIME }?.let { return@withLock it }
            val release = JSONObject(readLatest())
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) throw IOException("No stable Android release is available.")
            val tag = release.optString("tag_name")
            val latest = version(tag) ?: throw IOException("The latest release has no stable version.")
            val latestName = latest.joinToString(".")
            val assets = release.optJSONArray("assets") ?: throw IOException("The latest release has no Android packages.")
            // Stable assets are named and package/signature-verified by the
            // repository's prepare_release.py before publication. Never offer
            // a debug, F-Droid, unsigned, or incompatible ABI build here.
            val compatibleNames = abis.map { "Zenith-$latestName-stable-$it.apk" }.toSet()
            val hasCompatibleApk = (0 until assets.length()).any { index ->
                val asset = assets.optJSONObject(index) ?: return@any false
                asset.optString("name") in compatibleNames && asset.optLong("size", 0) > 0
            }
            if (!hasCompatibleApk) throw IOException("No compatible stable Android package was found.")
            val order = current.zip(latest).firstOrNull { (a, b) -> a != b }?.let { (a, b) -> a.compareTo(b) } ?: 0
            val result = AppReleaseResult(
                when { order < 0 -> AppReleaseStatus.AVAILABLE; order > 0 -> AppReleaseStatus.AHEAD; else -> AppReleaseStatus.CURRENT },
                currentVersion, latestName, "$RELEASES_PAGE/tag/$tag"
            )
            cached = result; cacheKey = key; cachedAt = SystemClock.elapsedRealtime()
            result
        }
    }

    private fun version(raw: String): List<Long>? {
        val match = Regex("^v?(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})$").matchEntire(raw) ?: return null
        return match.groupValues.drop(1).map(String::toLong)
    }

    private suspend fun readLatest(): String {
      try {
       return withTimeout(TIMEOUT) {
        suspendCancellableCoroutine { continuation ->
            val connectionRef = AtomicReference<HttpsURLConnection?>()
            val worker = CoroutineScope(Dispatchers.IO).launch {
                var connection: HttpsURLConnection? = null
                try {
                    ensureActive()
                    val deadline = SystemClock.elapsedRealtime() + TIMEOUT
                    connection = (URL(API).openConnection() as HttpsURLConnection).apply {
                        instanceFollowRedirects = false
                        connectTimeout = 8_000; readTimeout = 8_000; useCaches = false
                        setRequestProperty("Accept", "application/vnd.github+json")
                        setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                        setRequestProperty("User-Agent", "ZenithW-Android/${BuildConfig.VERSION_NAME}")
                    }
                    connectionRef.set(connection)
                    ensureActive()
                    if (connection.responseCode != 200) throw IOException("GitHub release request failed.")
                    if (connection.contentLengthLong > LIMIT) throw IOException("Release response is too large.")
                    val body = connection.inputStream.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            ensureActive()
                            val remaining = deadline - SystemClock.elapsedRealtime()
                            if (remaining <= 0) throw IOException("Release request timed out.")
                            connection.readTimeout = remaining.coerceAtMost(8_000).toInt().coerceAtLeast(1)
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (output.size() + count > LIMIT) throw IOException("Release response is too large.")
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray().toString(Charsets.UTF_8)
                    }
                    if (continuation.isActive) continuation.resume(body)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                } finally {
                    connectionRef.compareAndSet(connection, null)
                    connection?.disconnect()
                }
            }
            continuation.invokeOnCancellation {
                worker.cancel()
                connectionRef.getAndSet(null)?.disconnect()
            }
        }
       }
      } catch (error: TimeoutCancellationException) {
        // A request timeout is a visible failure; leaving the screen is normal
        // cancellation and must still propagate to the owning Compose scope.
        currentCoroutineContext().ensureActive()
        throw IOException("Release request timed out.", error)
      }
    }
}
