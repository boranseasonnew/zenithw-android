# Metadata performance: 2.3.1 → 2.4.0

Measured on Android 13 x86_64 GitHub-hosted emulators, one paired pass on separate ephemeral runners of the same class. Baseline bundled yt-dlp 2026.08.19; candidate 2026.10.21. These results include the engine update and are not an isolated algorithm comparison. No physical device was attached; the reported 24-second phone result was not reproduced on that device. No timed Seal comparison was performed.

[Paired benchmark and raw JSON/screenshots](https://github.com/boranseasonnew/zenithw-android/actions/runs/39882006415) · [Signed validation](https://github.com/boranseasonnew/zenithw-android/actions/runs/39882007211)

## Results

| Scenario | Before total ms | After total ms | After title / cover / formats ms | Outcome |
| --- | ---: | ---: | --- | --- |
| youtube_video | 4476 | 3470 | 617 / 1454 / unavailable | error / unavailable |
| youtube_video_warm_cache | 3740 | 2692 | 11 / unavailable / unavailable | error / unavailable |
| youtube_playlist | 40002 | 1479 | 1469 / unavailable / unavailable | success |
| youtube_shorts | 3450 | 2223 | 84 / 362 / unavailable | error / unavailable |
| vimeo | 2725 | 1548 | unavailable / unavailable / unavailable | error / unavailable |
| invalid_url | 1 | 0 | unavailable / unavailable / unavailable | error / unavailable |
| local_hls_uncached | 5547 | 3640 | 3629 / 3658 / 3629 | success |
| local_hls_warm_runtime | 5551 | 364 | 89 / 296 / 89 | success |
| local_hls_cache | 11 | 12 | 12 / unavailable / 12 | success |
| local_declared_duration | 5569 | 330 | 315 / 353 / 315 | success |
| local_hls_delayed | 6140 | 910 | 910 / 910 / 910 | success |
| slow_network | 33761 | 32216 | unavailable / unavailable / unavailable | error / unavailable |

The supplied YouTube video was `https://www.youtube.com/watch?v=aqz-KE-bpKQ`. Its panel became visibly laid out in **255 ms**, real public title arrived in **617 ms**, artwork decoded in **1454 ms**. YouTube and Shorts full extraction encountered authentication/bot checks on the CI network in both versions. Their error completion times are NOT successful format extraction times. Vimeo also required authentication; external-site success is therefore not established. Playlist data loaded in 1479 ms; the old 40002 ms is a harness deadline because automatic playlist analysis did not start, not a measured extraction duration.

Local HLS uncached total fell from 5547 to 3640 ms; a different uncached URL with the same warm runtime fell from 5551 to 364 ms. Existing completed-cache performance was already fast (11 versus 12 ms). The duration fixture declares 125 seconds in source JSON-LD; HLS duration remains unknown rather than invented. Five rapid links retained the latest URL with no stale result in both revisions. A stalled proxy terminated after 32216 ms rather than waiting forever.

The first-panel 300 ms target was met for the supplied video in this pass. Full YouTube metadata and format targets of 3/5 seconds remain unverified because access was blocked. The repeat-URL benchmark reopens programmatically without a UI settling interval: its later layout event (2715 ms) must not be treated as an independent panel latency success. Some cached images have no new decode event; unavailable is not zero.

## Why it was slow and what changed

Previously, complete JSON extraction gated the UI, each uncached inspection launched Python again, and normal processing selected formats and could probe FFmpeg before the user downloaded anything. The baseline local warm extraction still spent about 5534 ms extracting. The new runtime imports once, reuses safe anonymous YoutubeDL objects, avoids processing resolved video formats, and postpones FFmpeg initialization until downloading. Source errors retain the healthy interpreter. Cold import measured 1600 ms; subsequent worker traces report zero import/spawn. The separate CLI version command was 1848 / 1835 ms; it is not a pure interpreter initialization measurement.

A lightweight public YouTube oEmbed hint runs beside one full extraction, supplying real title/artwork only. It is disabled for cookie sessions, explicit proxies and explicit IP-family selection. It does not invent duration, resolution or bytes. Other sites receive full extractor data directly. Playlist extraction uses flat results only for collection selection; individual videos still extract complete formats. Concurrent identical inspections share extraction, completed results have a 90-second bounded memory cache, and private metadata snapshots can avoid extracting again on download. Cache identity includes the full URL and session/network/engine preferences.

Cancellation destroys the owned worker and releases blocked reads. Generation IDs reject old callbacks. Main UI work remains asynchronous; remembered download choices survive incremental data. States are Idle, Preparing, FetchingMetadata, BasicInfoReady, FetchingFormats, Ready, Error and Cancelled.

## Timing scope and privacy

PERF logs contain random attempt IDs and numeric milliseconds, never source titles, URLs, cookies or signed format URLs. Separate events measure panel state, first layout, visibility, title availability/draw, cover decode, duration, formats, total, spawn/import/object construction, extraction and serialization. Network timings measure response headers, not complete body transfer or a separately isolated JavaScript cost. Baseline did not expose all these stages; missing measurements are unavailable. Thumbnail decoding is asynchronous.

## Verification and risks

The validation workflow runs Python worker tests, JVM policy checks, lint, signed ABI/package/certificate checks and Android 13/7 instrumentation. All these validation stages passed in run 39882007211. Seven Python worker tests passed; the paired benchmark also passed. Tests cover real local HLS/JSON-LD extraction, snapshot reuse, cookies, sanitized errors, timeout/recovery, cache identity, concurrent requests, cancellation, five-link replacement and explicit analysis states. Benchmark JSON and screenshots are retained as workflow artifacts.

No claim is made that all source authentication issues, impersonation, or physical-device performance are fixed. Native 16 KB page compatibility remains unestablished. Snapshot size is capped at 4 MB with the normal extraction path as fallback. The worker exits after 120 seconds idle, so a later cold analysis pays startup cost again. Routing/session hints deliberately favor compatibility over speculative speed. Existing URL parameters are preserved.

## Main changed files

`DownloadEngine.kt`, `MetadataRuntime.kt`, `BasicPreviewFetcher.kt`, `PreviewPolicy.kt`, `AnalysisTrace.kt`, `ZenithViewModel.kt`, `DownloadSheet.kt`, `ZenithUi.kt`, `metadata_worker.py`, bundled engine/version records, instrumentation fixtures/tests, Python/JVM checks and Actions workflows. Build metadata is now version 2.4.0 / 2004000.

## References and independent implementation

[Seal source](https://github.com/JunkFood02/Seal/tree/v1.13.1) was studied for download configuration and asynchronous inspection behavior. No Seal implementation or visual assets were copied. [yt-dlp embedding documentation](https://github.com/yt-dlp/yt-dlp#embedding-yt-dlp) supplies the public extraction API; [youtubedl-android](https://github.com/yausername/youtubedl-android/tree/0.18.1) supplies the existing Android native packaging. The persistent worker and preview/cache orchestration are independent ZenithW code.
