# Android Pornhub comparison — 3 November 2026

## Verified outcome

The independently implemented browser transport resolves the reproduced HTTP 403 on
the Android emulators. Android 13 engine checks passed in
[run 41720064696](https://github.com/boranseasonnew/zenithw-android/actions/runs/41720064696),
and Android 7 checks plus signed APK integrity verification passed in
[run 41723372834](https://github.com/boranseasonnew/zenithw-android/actions/runs/41723372834).
The earlier full Android 13 run passed the UI checks on identical application
code; only its old concurrent-log assertion needed the test-only correction.

Latest Android 13 actual-source comparison, no personal cookies and normal device routing:

| Measurement | Ordinary CLI | Native browser worker |
| --- | --- | --- |
| Metadata result | HTTP 403, no formats | Success, 8 formats |
| Total wall time | 1774 ms until failure | 6877 ms |
| Extractor time | No metadata | 6170 ms |
| Real title / duration | Absent | Present / 1358 seconds |
| Actual media sample | Not attempted | 10,241 bytes in 2275 ms |

Android 7 independently reproduced the same baseline HTTP 403 (1992 ms), then
returned 4 real formats and the same title/duration in 3980 ms (3279 ms
extraction). Its real 10,241-byte media sample completed in 3976 ms. Available
format counts vary with the upstream response; no synthetic formats are added.

The sample uses the existing downloader and a cached info snapshot in yt-dlp's
test-download mode. It is not a complete video download. Controlled Android
tests additionally verify CLI extraction/download, selected-cookie and captured
User-Agent propagation, CDN cookie isolation, normal certificate checks,
source HTTP 403 preservation and native-process cancellation. All 10 adapter unit
tests also pass with the exact official nightly 2026.10.29.232701 in the phone log.

Candidate APKs are built by GitHub Actions with the retained stable certificate;
the stable release and main remain unchanged. Physical-phone VPN/account
validation is explicitly pending. All three downloaded APK checksums, packaged
native executable hashes and complete license notices were verified again locally.
The known 16 KiB page-size limitation is documented in
[native runtime provenance](native-curl-NOTICES.md).

## Targets and evidence

- ZenithW Android source: `boranseasonnew/zenithw-android`, main `94538f4`, application version 2.4.1. Restored to an ignored checkout solely for this task.
- Seal source: `JunkFood02/Seal`, main `7677f61`. Source HEAD is not assumed to be the installed APK.
- Official latest Seal release discovered from GitHub: v1.13.1. arm64 APK SHA-256: `23112395e489da4655fe3f6e5be2a47bca8ab3b406cc5ed7590a00b4548faa37`.
- REA package evidence `ev_469dd21975c1e2603fadf78c82cc118ec501f91cb1ef43c07581e859cfb358c4`: package com.junkfood.seal, version1.13.1, class count4319. Static analysis only, signatures not verified, APK not executed.
- REA execute-method evidence `ev_ffdd9e7e81c1c5b96aa999248bb84ac49bb0eb1e9b4c1a1bd1c60a442c007f6b`: common YoutubeDL CLI execution and Android Python environment.
- REA metadata-method evidence `ev_00bd806604eb9a6cbcc010c3fd1b57d4db1c2615d6bf9902a6423ddae03c55d0`: Seal passes enabled cookies and optional matching User-Agent to its metadata request, uses dump-single-json/no-playlist and ordinary CLI execution.

## Observations

The supplied Seal log reaches m3u8/JSON metadata and starts HLS fragment downloads. It does not include a complete terminal success. ZenithW's screenshot reports metadataHTTP403 in about3.8seconds, rather than a timeout. The session screenshot has 'continue without a session' selected; the saved profile exists but is not selected in that image.

ZenithW passes the selected cookieId to both metadata and download. Its private Python worker environment matches the common adapter in PYTHONHOME, library directories, certificate path, PATH and TMPDIR. Metadata extraction passes its generated temporary Netscape cookie file and captured browser User-Agent when a session is selected. There is no supported evidence that these selected cookies are globally ignored.

Archive inventory of the downloaded Seal APK's arm64 Python runtime finds Cryptodome and mutagen in site-packages, but no curl_cffi module or libcurl member. Therefore do not attribute Android Seal success to the same native curl impersonation path established separately on EC2. The two applications may also be using different updated yt-dlp versions; the supplied Zenith log says nightly2026.10.29.232701, and the APK alone cannot establish the phone's current Seal engine.

## Substantiated bug and independent patch

`ZenithViewModel.cookie(id)` cancelled inspection, cleared preview, and did not re-analyze. Selecting or importing a session during an open download configuration therefore dismissed configuration and left the user to start again. The patch:

- Keeps the same configuration/panel identity and existing format choices.
- Invalidates previously anonymous/other-session metadata, preserves the URL placeholder and clears stale errors/playlist entries.
- Re-analyzes with the explicitly chosen session when media previews are enabled.
- Preserves explicit anonymous selection. Does not auto-select saved accounts.
- Emits only `session=none` or `session=selected`, without cookie IDs/values/headers.
- Adds Android instrumentation regressions for session/panel identity/options and cookie inclusion during metadata extraction.

No Seal code was copied.

## Validation and limits

- Existing real Python metadata-worker regression suite: **9 tests passed** (8-test run plus the separately added warning-category test), using a controlled local HLS/HTML/DASH fixture and the bundled yt-dlp archive.
- git diff --check passed.
- Initial local validation had no Android SDK/emulator/device. Subsequent GitHub Actions instrumentation and real-source results are recorded above and below; local Python checks alone were not treated as Android proof.
- APKs were subsequently built and signed by the artifact-only GitHub Actions workflow. No phone cookies were read, and no public version tag or stable release was published.
- The reproduced emulator HTTP403 is resolved; the physical phone/VPN/account combination remains **unverified**. Normal TLS verification is retained, and successful metadata is distinguished from full-video download verification.

## Extended shipped-runtime comparison

A second official artifact was inspected without executing Android binaries:
Zenith2.4.1 arm64 APK SHA256 `1b0b06d4c772b6f511fd09d29b762cfed9a778a8588eb4e511763ad9b7ec46a2`.

| Shipped component | Seal1.13.1 | Zenith2.4.1 |
| --- | --- | --- |
| Python standard library | 3.11 | 3.12 |
| OpenSSL library version string | 3.3.2 (3Sep2024) | 3.5.2 (5Aug2025) |
| Native curl/cffi packages in arm64 Python archive | absent | absent |
| APK bundled engine floor | 2024.09.27 | 2026.10.21 |

The phone can update its engine independently, so bundled engine floors do not establish its current engine. Python/OpenSSL libraries differ by SHA256 as well as version strings; a different TLS ClientHello is plausible but was not captured/proved. Full version/hash inventory is retained in `../android-runtime-comparison.json`.

REA's ZenithAPK class/manifest load hit its512MiB Java heap limit, so do not claim successful Zenith decompilation. ZIP/native-library inventory was performed with Python's format-aware parser, distinct from REA evidence. Packaged worker resource `res/nK.py` has exactly the same normalized Python source as this checkout before changes; newline bytes differ on Windows.

Using the exact public URL from the user's Seal log and identical bundled yt-dlp2026.10.21 on the Windows host, with no phone cookies: the persistent worker returned8formats in5.726seconds (6network requests), and the ordinary CLI also returned8formats. This rules out a universal worker-vsCLI incompatibility for that source; it does not reproduce Android TLS, VPN, IP or credentials. Results: `../worker-cli-comparison.json`.

Current upstream support was rechecked from official APIs: MavenCentral latest `io.github.junkfood02.youtubedl-android:library` is0.18.1; curl_cffi runtime PR361 is closed with `merged_at=null`, not a published supported upgrade. No upstream PR binary was substituted.

The user confirms explicitly selecting the saved session still produces403. Therefore the session-panel patch is a separate UX fix, not a proven remedy for the reported network rejection. Do not characterize saved-cookie selection alone as the root cause.

## Additional safe diagnostics

The candidate patch additionally reports Python/OpenSSL versions at metadata runtime initialization, available request-handler names, and a boolean for yt-dlp's missing-impersonation-target warning. Raw warnings, URLs and cookie values remain suppressed. This will allow an actual device log to distinguish missing native capability, runtime revision, and session-use status. No TLS certificate checks were disabled or protocol/library versions downgraded.

## Artifact-only CI verification

Test branch: `codex/android-session-diagnostics`. Existing `android-release.yml` inputs: `release_tag=v2.4.1`, `publish_release=false`, `run_tests=true`, `test_release_tag=`. This leaves the public2.4.1release and main unchanged. New instrumentation methods are included explicitly in `scripts/android_smoke.sh`. Android13and7execution results must be reported from the workflow, independently of local Python tests.

### First CI attempt and test-finder correction

Run41708771077 compiled all signed ABI APKs and passed lint, JVM policies and Python worker checks. Android13 completed46instrumentation tests successfully, including both new session tests. Its only failed test was the existing `ReleaseSmokeTest#bulkWorkspace`: the screenshot showed the correct blank link field, while the platform accessibility representation merged its empty editable value and label (`", Bağlantı 1"`). The old finder accepted only plain text/contentDescription equality. Android7was not reached after this step failed.

The correction is limited to test code: EditText hints and the exact `", Label"` empty-field form are accepted. Other classes retain exact matching; partial-label matches remain rejected by a new explicit regression. No test is skipped, assertion removed, timeout enlarged or product behavior changed. The full artifact-only workflow is rerun for this test-only correction.

### Native browser transport candidate

The diagnostic/session workflow rerun41711473944 completed successfully, including
the configured Android13andAndroid7 checks. This predates the native transport.

The next candidate adds an independent, per-YoutubeDL request handler backed by
the pinned official curl-impersonate2.3.2 Android executable. It handles only
explicit HTTPS browser-impersonation requests to Pornhub and its recognized CDN
domains. No Seal application code or older Python/OpenSSL runtime is copied.
The same adapter is attached by the metadata worker and a minimal extractor
initialization plugin in the normal download CLI. Upstream extractor parsing,
ordinary media transfers, selected sessions and user download settings remain.

The adapter retains selected cookie and User-Agent settings, configured proxies,
IP-family selection, normal CA verification and the device's VPN routing. It
checks redirects individually, recalculates scoped cookies per hop and rejects
foreign domains, HTTPS downgrades and cross-origin POST replay. Transfers are
bounded to16MiB/20seconds; on Android a parent-death signal closes native curl
when its owning analysis process is cancelled. No URLs, cookies, tokens, proxy
credentials or raw native diagnostics are written to logs or process arguments.

Archives, ELF executables and complete dependency notices have pinned hashes;
APK staging independently verifies the packaged executable and notices again.
See `native-curl-NOTICES.md` for upstream source/license records and the explicit
4KiB/16KiB page-size limitation. The APK size increases by the native executable
(approximately15–19MiB uncompressed per ABI).

Local verification:9existing metadata tests,10new transport tests and11packaging
tests passed. A new Android instrumentation scenario exercises actual native
execution, metadata/CLI extraction and fixture media download through a local
TLS proxy, source403 handling, selected-cookie/User-Agent propagation, CDN
cookie isolation, rejection of an untrusted CA and native-process cancellation.
Its emulator result and a real-phone retry remain pending; local tests alone
do not establish that the reported phone403 is resolved.

### First native Android result

Run41714479872 built all signed ABI APKs and passed lint, JVM and Python checks.
On Android13, the same reported source failed through the normal CLI with
HTTP403 in2654ms. The native metadata worker succeeded without personal cookies:
8formats, a real title and1358seconds duration, in8712ms including interpreter
startup (7767ms extraction;6network requests). This reproduces the missing
browser-transport failure and demonstrates a successful alternative on Android.
It does not establish a physical-phone/VPN/account result.

The controlled native test completed execution, metadata, CLI fixture download,
cookie/User-Agent checks, source403 and untrusted-CA checks. Its final process
observer failed because the Android kernel did not expose task/children files.
The observer now also examines same-UID processes with the exact parent PID;
child identity, connection closure and process-exit assertions remain required.
The actual source exposed no direct HTTPS MP4 for the small range probe, so a
separate one-fragment HLS test download is added using the existing snapshot.
No full real-source download or Android7 native verification is claimed yet.

### Second native Android result

Run41717184327 passed the complete controlled browser-transport test, including
native cancellation. The actual source again returned8formats, a title and
1358seconds duration:8859ms total,7938ms extraction. Normal Python CLI again
failed with403 in2619ms. The existing downloader then successfully saved a
10,241-byte real HLS test sample in3923ms. This is a media sample, not a complete
video download. All native checks and the Android13 UI checks passed.

The run's only failed assertion was an existing update test that expected its
checksum error to remain the last global log entry. Logcat confirms the expected
UPDATE_CHECKSUM rejection; runtime startup logging could append another entry.
The tests now require a new operation-specific error while preserving the
unchanged-engine/version checks. A focused engine rerun is selected explicitly
for the same application source. It retains native/live/update/archive tests
on Android13 and Android7; public release validation still defaults to full.

### Android13 verification completed; API24 runner disk issue

Run41720064696 passed all selected Android13 engine tests, including native
execution/cancellation, TLS/cookie boundaries, live metadata, test media download
and both corrected update checks. Actual metadata took6877ms total /6170ms
extraction (8formats,1358seconds); the real10,241-byte HLS sample took2275ms.
The ordinary baseline again failed with403 (1774ms).

The Android7 emulator did not boot and no application test started: its fatal
message required7372.80MB for userdata while only6750.77MB was available on the
runner. The follow-up uses explicit2GiB userdata partitions and separate AVD
names, with an Android7-only artifact verification mode because application
source and the successful Android13 tests are unchanged. Stable publication
always selects the full Android13 suite when tests are enabled. Signed
candidate artifacts are integrity-checked before emulator startup so an
infrastructure failure no longer hides the build; their test outcome must
still be assessed separately.

The10transport unit tests also passed with the exact officially verified
nightly2026.10.29.232701 seen in the user's phone log. Static checks against the
exact Maven0.18.1AAR found all required C++ imports resolved for all4ABIs; this
is distinct from native execution on the x86_64 emulators.
