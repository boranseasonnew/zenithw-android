# Seal comparison and Android 2.3.0 development changes

## Evidence and scope

Compared official Seal **v1.13.1**, source commit
`a2d7e5d5819d0aa8b4f5dbb906731da283a28ed1`, with Zenith Android 2.2.1.
The local `Seal-1.13.1-arm64-v8a-release.apk` was statically inspected through REA/JADX:

- SHA-256: `23112395e489da4655fe3f6e5be2a47bca8ab3b406cc5ed7590a00b4548faa37`
- Package `com.junkfood.seal`, version 1.13.1 / code 11312; min SDK 21, target SDK 34.
- REA evidence: `ev_ab35326985fa3f9ebef4f853081ce728116922fcfc790c3669a23852dbb5726e`.
- The APK was not executed or signature-verified. Its size/version match the official
  asset, but that alone does not establish binary provenance.

The implementation adapts observed behavior independently. No Seal source, branding,
icons or screenshots were copied into Zenith. Seal uses yt-dlp through
youtubedl-android 0.17.1; Zenith uses the same underlying approach through 0.18.1.
This comparison does not establish a separate, faster extraction engine in Seal.

## What explains the perceived delay

[Seal DownloadPage](https://github.com/JunkFood02/Seal/blob/a2d7e5d5819d0aa8b4f5dbb906731da283a28ed1/app/src/main/java/com/junkfood/seal/ui/page/download/DownloadPage.kt)
opens its configuration dialog immediately in CONFIGURE mode. Metadata extraction
comes later. Zenith previously waited for full JSON metadata before opening its sheet.

[Seal DownloadUtil](https://github.com/JunkFood02/Seal/blob/a2d7e5d5819d0aa8b4f5dbb906731da283a28ed1/app/src/main/java/com/junkfood/seal/util/DownloadUtil.kt)
uses no-playlist JSON extraction, a 5-second socket timeout, and saved proxy/IP/user-agent
settings. Its `-R 1` is a download retry setting, not the extractor retry count.
It conditionally skips translated subtitle enumeration. Shorter limits bound stalls;
they do not prove that successful extraction becomes faster.

Zenith already had a 90-second metadata cache and reused JSON when downloading.
Those mechanisms remain. The actual defect found was that discovery/list requests
ignored saved network preferences even though downloads applied them.

## Implemented

- Open the configuration sheet immediately with an explicit pending state. Video
  metadata arrives asynchronously without resetting the user's choices. Starting
  an automatic download is possible without a preliminary metadata request.
- Fast discovery: 8-second socket timeout, 35-second overall timeout, one extractor
  retry; explicit extended retry: 20/90 seconds, three extractor retries. Generation
  guards prevent cancelled requests from updating a different URL's screen.
- Apply proxy, IP family and user agent consistently to discovery, playlists and
  downloads. Include those preferences and subtitle enumeration choices in cache keys.
- Skip YouTube translated subtitle enumeration when automatic subtitles are not
  requested. Do not skip video formats, player challenges, or required JavaScript.
- Respect a persisted update-check interval; a manual update still checks immediately.
- Record initialization, extraction and parsing durations in session logs. No URLs
  are included in those timing entries. Real measured device benchmarks remain required.
- Minimal branded home, compact mode/options, real source formats with codec/fps/size
  details, available subtitle languages, literal filename override and validated clips.
- Keep exact audio format IDs. Video-only IDs are combined with the audio fallback.
  Per-media format/name/clip/custom options do not leak into the next download.
- Resolve the job title from yt-dlp's `before_dl` event when the download starts before
  metadata is available; use a host label until then.
- Batch/Commands/Log workspace, + URL rows, persistent named templates, direct yt-dlp
  arguments, per-task logs, copy, cancel and restart. No shell execution. Reserved
  output/config/exec/cookie-path options remain managed by the app. Supported options
  are validated, including quoted values and attached short-option values.
- System/light/dark/dynamic appearance; configure/remember/preview preferences;
  no-history disk behavior preserving the active queue; format sorting, restricted
  filenames and maximum file size; SAF output tree and optional media subfolders.

Seal's terminal reference is
[TaskListPage](https://github.com/JunkFood02/Seal/blob/a2d7e5d5819d0aa8b4f5dbb906731da283a28ed1/app/src/main/java/com/junkfood/seal/ui/page/command/TaskListPage.kt)
and
[TaskLogPage](https://github.com/JunkFood02/Seal/blob/a2d7e5d5819d0aa8b4f5dbb906731da283a28ed1/app/src/main/java/com/junkfood/seal/ui/page/command/TaskLogPage.kt).
It runs yt-dlp tasks/templates, not arbitrary Android shell commands.

## Validation boundary

Platform-independent regression checks cover download policy, URL rows, discovery
limits/update intervals, clip/name validation, and command parsing. `SealFlowTest`
adds device tests for real metadata formats, no-history persistence, per-media defaults,
request options, and immediate configuration availability; `android_smoke.sh` runs them
in the existing emulator workflow.

No Android device was connected during local development. Compilation and lint are
separate from executed device tests. SAF picker/grants, rendering, real site extraction,
and end-to-end download behavior need Android execution. To measure speed, compare the
same URL on the same device/network in cold, warm and cached runs, and separate
time-to-configuration from time-to-metadata. No numeric extraction speedup is claimed.

The 2.3.0 release is built and published through GitHub Actions after its verification
steps, rather than uploading locally built APKs. See [release notes](../releases/2.3.0.md)
and [attribution/reuse policy](../THIRD_PARTY_NOTICES.md).

## October download-flow follow-up

The user's Seal screen references informed the home progress card, compact
configuration summary and settings spacing. These are independent Compose
implementations; no Seal screenshots, artwork or source files were bundled.
The optional session/User-Agent handling was also compared with the referenced
Seal source. See the [implementation and verification record](download-flow-20261023.md)
for the playlist, artwork, navigation and source-access changes and their limits.


## 2.4.0 metadata follow-up

ZenithW independently adds a persistent Python metadata worker, public preview hints, bounded caches and generation-safe cancellation. These are not copied Seal implementations. See [measured results and limitations](metadata-performance-2.4.0.md); no direct Seal speed comparison was measured.
