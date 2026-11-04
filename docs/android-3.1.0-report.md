# Android 3.1.0 implementation and verification

Base: 3.0.0 (`758a7c0`). Development branch: `feature/android-3.1.0`.
Version: 3.1.0 / 3001000. This report distinguishes implementation from verification.

## Implementation

| Area | Status | Implementation | Verification scope |
| --- | --- | --- | --- |
| Sharing | PARTIAL | SEND/SEND_MULTIPLE text links, bounded extraction, duplicate delivery filtering, immediate configuration, quick modes, optional one-tap preference | Model and input tests passed on physical Android 13; source-app return UX remains partial |
| Queue | PARTIAL | Existing WorkManager extended with unique requests, priorities, order, concurrency 1–5/default 2, charging/Wi-Fi/unmetered conditions and bounded network retries | Unique scheduling, durable queued pause and constraints passed on phone; full concurrent/network-switch/reboot stress remains partial |
| Trimmer | PARTIAL | Source duration slider, multiple ranges, manual existing timestamp input, separate outputs, optional compatible merge and accurate cuts; local VideoView range preview | Clip validation and native copy/accurate cutting and compatible merging passed; remote multi-range UI coverage remains partial |
| Library | PARTIAL | Search, list/grid, platform/date/type/resolution/status filters, sorting, favorites/collections, multi-select, share/export, missing-file checks and distinct entry/file deletion | Data persistence and original-file preservation tested; large-library/provider interaction coverage remains partial |
| Converter | PARTIAL | Local SAF files, MP4, MP4/MKV remux, audio extraction, MP3/M4A/WAV/FLAC, muting, presets/bitrate/sample rate, bounded batches, progress and cancellation | Real audio conversion passed on phone; native video suite passed on physical Android 13; batch/provider interaction coverage remains partial |
| Smart Mode | PARTIAL | Optional local source profiles, conservative available-height selection and manual overrides | Source profile and authentication preservation tests passed; automatic storage-based format selection is not implemented |
| Backup | PARTIAL | Settings/profile schema 2, schema 1 migration, pre-parse depth/UTF-8/size validation, optional matching favorite/collection metadata | Valid/malicious schema tests passed; arbitrary queue/media migration and sensitive session export intentionally excluded |
| Naming | PARTIAL | Safe variable compilation, live preview, existing yt-dlp naming behavior and a selected output subfolder | Traversal/invalid-variable tests passed; automatic multi-level folder-variable expansion is not implemented |
| Recovery | PARTIAL | Failure categories, actionable localized explanation, bounded retry for transient errors and existing private logs | Classifier and private transport diagnostics passed; dedicated diagnostic-file export remains partial |
| Duplicates | PARTIAL | Source identity plus output settings/format/quality/clip identity, open/skip/another-copy choices | Format/quality/clip distinction tested on phone; missing files are surfaced and may need another copy |
| Chapters | PARTIAL | Bounded actual source chapter metadata and selectable ranges; no generated chapters | Projection/privacy and chapter validation tests passed |
| TXT import | PARTIAL | Streaming bounded reader, 50 accepted links, validation/duplicate counts and confirmation | Malformed/oversized/cancelled input tests passed; no removal of the 50-row constraint |
| GIF/WebP | PASS | Runtime-gated encoders, clip/rate/dimension/loop controls and bounded processing | Native suite passed after correcting the WebP demuxer allowlist; extreme inputs are bounded |
| Subtitles | PASS | SRT/VTT, local import, track attachment, rendering when available, offset and existing source language selection | Native conversion, offset, track attachment and burn-command/output verification passed; multilingual glyph appearance remains unverified |
| Storage Guard | PARTIAL | Available workspace storage, existing download-size estimate, reviewed aged private leftovers and owned conversion cache, active-work protection | Active/unknown download protection tested on phone; kill/reboot cleanup/provider stress remains partial |

PASS means a specific check passed, not that every possible device/source workflow
was exercised. The implementation is not a blanket production-readiness claim.

## Existing components reused

`ZenithViewModel`, `DownloadEngine`, persistent metadata runtime/cache, yt-dlp
section downloads, Android FFmpeg/Aria2 packages, WorkManager, LocalStore,
CookieVault, JobArtwork, SAF/MediaStore/FileProvider and existing themes/locales.
No Seal source was copied, and no additional cloud or account system was added.

## Architecture and storage

* No parallel download engine or second history database was introduced.
* Queue scheduling uses unique per-job work and a process-wide claim gate.
* New JSON fields have defaults for older records; IDs, URI lists and history are
  retained. Missing/non-finite legacy progress is now read as zero.
* Queue order/priority, paused state, favorites, collections, retry count and
  output conditions persist through the existing store.
* Native media work uses a separate, serial WorkManager lane and private temporary
  workspaces. Native arguments are argv lists; file/format protocols are limited.
* Backups do not contain cookie secrets, keys, proxy credentials, custom commands
  or device URI grants. Restoring never automatically starts another queue. The current device history-retention preference is preserved to avoid removing history as a side effect.

## Validation recorded during development

Physical device: TECNO Spark 20 Pro / KJ6, Android 13, arm64, 4096-byte pages.
Tests use `space.zenithw.app.preview`; the user's stable app and data are untouched.

* 27 physical-device tests passed in 79.198 seconds: FeatureReleaseTest (9),
  MetadataOptimizationTest (12), DownloadRegressionTest (5), BrowserTransportTest (1).
* 32 Python tests passed, including metadata-worker, browser transport, pinned ELF
  packaging, five-language feature-resource coverage and private chapter projection.
* 45 new independent Kotlin policy assertions passed, alongside the existing
  download, bulk-link, inspection, command, diagnostics and preview policy checks.
* Preview ABI APKs and instrumentation APK compiled. Lint passed on an earlier
  candidate and on the diagnostic update. The first lint error was fixed in code;
  no lint check was disabled. A transient lint/compiler error occurred when a new
  test source was added during analysis; subsequent complete compilation/lint passed.
* Real audio conversions preserved the input bytes. Additional direct native
  probes passed MP4→MKV, MKV→MP4, muting, copy trim, H.264 conversion, GIF/WebP,
  subtitle conversion/attachment/rendering using short synthetic media.
* The combined media test exposed missing `webp_pipe` in the output-probe allowlist.
  The narrow demuxer correction passed the complete physical media suite in 20.864 seconds, including remux, two trim modes, encoding, animation, subtitle conversion/attachment/burning, compatible merging and cancellation.

### Signed candidate and published release

[Signed candidate Actions run 41852742303](https://github.com/boranseasonnew/zenithw-android/actions/runs/41852742303) completed successfully on commit `db54abc`:

* Signed arm64-v8a, armeabi-v7a and x86_64 APKs built and passed certificate/version/ABI checks.
* Android lint, JVM policies and all 32 Python checks passed.
* 60 Android 13 smoke/regression tests and 23 Android 7 compatibility tests passed.
* The full native media workflow passed on both the physical phone and Android 13 CI.

The final safety follow-up preserves current history-retention preferences during restore, refreshes the Smart Mode control, and rejects imported filename references to private HTTP headers.

[Final release Actions run 41856386484](https://github.com/boranseasonnew/zenithw-android/actions/runs/41856386484) completed successfully on source commit `77f870e`:

* **61 Android 13 tests passed**, including the added history-preserving restore regression and real native media suite.
* **23 Android 7 engine/settings/compatibility checks passed**. This is not full UI/tool coverage on Android 7.
* **32 Python tests**, **45 new feature-policy assertions plus all existing JVM checks**, and **Android lint** passed.
* All three stable ABI APKs passed certificate/version/package/ABI verification and were uploaded by GitHub Actions.
* Release `v3.1.0` is public, not draft/prerelease, and verified as GitHub latest. Its tag points to `77f870ea177867b7368b636e01506cd2bd92b6ad`.
* The GitHub asset SHA-256 digests for all three APKs match the published SHA256SUMS manifest.

[Published release and APKs](https://github.com/boranseasonnew/zenithw-android/releases/tag/v3.1.0).

One performance bound is verified by the existing physical test: a pasted complete URL publishes configuration state in less than 300 ms. This is not a measurement of first visible frame.

The attached phone disconnected after the completed media suite. The additional full metadata benchmark could not run after the ADB disconnect; no new panel/title/format latency or before/after improvement is claimed.


## Compatibility and remaining limits

Android 7/API 24 remains configured. Stable, Preview and F-Droid package IDs and
release signing configuration are preserved. Local testing uses a local Preview
key only; stable signing remains in the existing GitHub Actions secret.

**BLOCKED: full native 16 KB compatibility.** Verified pinned curl artifacts have
4096-byte ELF load alignment. No binary/hash patch or ZIP-alignment workaround
was substituted. Rebuilding the dependency chain from reviewed upstream source,
checking every packaged ELF and running on a 16 KB device/emulator remain necessary.
The attached phone has 4 KB pages and cannot verify that requirement.

Reference: [Android native page-size requirements](https://developer.android.com/guide/practices/page-sizes).
Local FFmpeg inputs use explicit [format and protocol allowlists](https://ffmpeg.org/ffmpeg-all.html).

Remaining device-specific checks include low-storage volumes, third-party SAF
providers, 200-item queue load, five active transfers, process death/reboot during
publication, large libraries, metered/Wi-Fi transitions and real authenticated
sources. Source anti-bot restrictions are not claimed universally solved.

## Files and artifacts

Core policies/workers: `FeaturePolicy.kt`, `Models.kt`, `LocalStore.kt`,
`DownloadWorker.kt`, `LocalToolWorker.kt`, `LocalMediaTools.kt`, `SettingsBackup.kt`,
`StorageGuard.kt`, `LibraryFiles.kt`, `SmartProfiles.kt` and metadata projection.
UI: MainActivity, ZenithViewModel, DownloadSheet, LibraryScreen, ToolsScreen,
WorkspaceSheets, TerminalWorkspace and ZenithUi. New resources cover all five locales.

Local Preview artifacts are under `app/build/outputs/apk/debug/` (three ABIs),
instrumentation under `app/build/outputs/apk/androidTest/debug/`, and lint under
`app/build/reports/lint-results-debug.html`. Published stable assets must come
from the existing signing/ABI/certificate-verifying GitHub Actions workflow.
