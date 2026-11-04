# Android 3.1.0 engineering audit

Base: `758a7c0` (3.0.0). Working branch: `feature/android-3.1.0`.

## Existing architecture

* `ZenithViewModel` owns asynchronous inspection, generation-based cancellation,
  immediately displayed Compose download configuration, playlist and batch drafts.
* `DownloadEngine` wraps the existing yt-dlp, FFmpeg and Aria2 Android packages.
  Metadata uses a persistent Python runtime and a bounded, session-aware cache.
* `DownloadWorker` publishes files through SAF, MediaStore or FileProvider;
  WorkManager persists scheduled/network-constrained work. The current in-process
  claim gate serializes all downloads and may register duplicate generations.
* `LocalStore` uses the existing `zenithw_v2` preferences JSON records. CookieVault
  is a separate AES-GCM Android Keystore store. Command templates are separate.
* The existing UI supports share-text, history, playlist selection, manual clip
  times, subtitles, SponsorBlock, naming templates, logs and encrypted sessions.
* Release CI builds signed ABI APKs, lint, JVM/Python checks and API 24/33 smoke
  tests. Stable, preview and F-Droid package identities must remain unchanged.

## Implementation boundaries

Extend these components, rather than introduce another downloader or database.
Add optional JSON fields with backward-compatible defaults. Preserve existing IDs,
URIs, cookies, output directory grants and all history. New queue fields must be
persisted immediately, not only at the next progress sample. Portable backups use
an independent, bounded and validated schema; never export credentials, network
proxy credentials, custom arbitrary commands or Android URI permission grants.

Local media tools must use SAF, cancellable bounded native processes, an isolated
temporary workspace and runtime capability detection. Never mutate originals.
Do not download remote streams to generate a visual timeline.

## Verification constraints

At audit time the previous Android SDK and Java 17 paths are absent. An installed
Java 21 executable is available directly (the Java PATH launcher is stale). Reuse
it if compatible. No physical Android device has been verified for this work.
Performance targets and runtime success require measured evidence.

The pinned native curl policy explicitly reports 4 KB ELF alignment. ZIP alignment
cannot fix this. A complete 16 KB claim requires rebuilding upstream native code,
checking every packaged ELF/dependency and execution on a 16 KB system. Keep the
existing transport protections; do not patch its bytes or relax hash verification.

## Release gate

Version 3.1.0 / 3001000 is preparation only. No stable tag, GitHub Release,
store submission or signing credential change is authorized by this request.
Track actual validation and unfinished requirements in the delivery report.
