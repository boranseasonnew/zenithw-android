<p align="center">
  <img src="app/src/main/res/drawable/zenithw.png" width="96" height="96" alt="ZenithW logo">
</p>

<h1 align="center">ZenithW Android</h1>

<p align="center">
  An open-source video and audio downloader with on-device media processing.<br>
  No ads, subscription or mandatory ZenithW account.
</p>

<p align="center">
  <a href="https://github.com/boranseasonnew/zenithw-android/releases/latest">Download APK</a> ·
  <a href="https://zenithw.space">Website</a> ·
  <a href="https://github.com/boranseasonnew/zenithw">Web & Windows</a> ·
  <a href="tr-readme.md">Türkçe</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/version-2.2.0-222222" alt="Version 2.2.0">
  <img src="https://img.shields.io/badge/Android-7.0%2B-444444" alt="Android 7.0 and later">
  <img src="https://img.shields.io/badge/license-AGPL--3.0--only-555555" alt="AGPL-3.0-only license">
</p>

ZenithW Android is a native Kotlin and Jetpack Compose application for saving supported online media, extracting audio and managing downloads. It uses yt-dlp to inspect and retrieve media, with FFmpeg and Aria2c for processing and transfer options. Downloads and media processing run on your device, using its network connection rather than the public ZenithW web download service.

## Install

Download a signed APK from the [latest GitHub release](https://github.com/boranseasonnew/zenithw-android/releases/latest). **Android 7.0 (API 24) or later is required.** Choose the package that matches your device:

| Architecture | Intended devices | APK for 2.2.0 |
| --- | --- | --- |
| `arm64-v8a` | Most modern Android phones and tablets | `Zenith-2.2.0-stable-arm64-v8a.apk` |
| `armeabi-v7a` | Devices running 32-bit ARM Android | `Zenith-2.2.0-stable-armeabi-v7a.apk` |
| `x86_64` | Compatible Intel/AMD Android devices and emulators | `Zenith-2.2.0-stable-x86_64.apk` |

Open the downloaded APK and, if Android requests it, allow installation from the browser or file manager you used. The release includes `SHA256SUMS.txt` for checking file integrity and a [public signing-certificate fingerprint](release-certificate.sha256) is available in this repository.

The 2.2.0 stable APKs retain the package ID and signing certificate used by 2.1.3 stable, allowing an update over that installation. Preview, F-Droid and legacy installations use different package identities; their settings and encrypted sessions do not transfer automatically.

## First download

1. Paste a supported media URL, or share a link to ZenithW through Android's Share menu.
2. Inspect the media and choose video, audio or subtitles. Review the available quality, format and processing options.
3. Add the job to the queue. You can prepare another link while the current download runs.
4. Follow transfer, post-processing and saving progress, then open or share the saved files from the download history.

Available formats, subtitles and metadata depend on the source. A quality preference cannot create a stream that the source does not provide.

## Features

| Area | Capabilities |
| --- | --- |
| Video and audio | Quality and container selection, codec preferences, audio extraction and bitrate settings. |
| Subtitles and metadata | Manual or automatic subtitles where available; metadata, chapters, artwork and separate thumbnail files. |
| Batch downloads | Add up to **50 URLs** at once, one per line, with duplicate filtering and video/audio options. |
| Playlists | List up to **200 entries** and select individual items before adding them to the queue. |
| Queue and history | Up to **200 active or pending jobs**, cancellation per job, scheduled starts, Wi-Fi-only settings, notifications and access to saved files. Eligible jobs run in queue order, one at a time. |
| Advanced settings | SponsorBlock, optional Aria2c transfers, speed limits, retries, proxy/network options, filename templates, download archive and reusable defaults. |
| Sessions | Cookie profiles from the built-in HTTPS browser or an imported Netscape cookie file, encrypted at rest. |
| Interface | Native Compose interface, a top-right menu for settings/history, and Turkish, English, German, French and Russian translations. |

Pending work is rescheduled when the app opens. Scheduled and background execution remain subject to Android's network, battery and background-work restrictions.

### Improvements in 2.2.0

- **Independent link preparation:** inspect another URL during a download; a short-lived metadata cache reduces repeated inspection.
- **Clearer progress:** percentages use transferred bytes when totals are known. Preparation, transfer, post-processing and saving are separate stages; completion is reported after files are saved. Remaining time estimates describe transfer, not the entire processing pipeline.
- **Batch and playlist selection:** add multiple links or selected playlist entries without cancelling an existing job.
- **Square embedded artwork:** supported MP3, M4A, MP4, FLAC, MKV and MOV files can receive center-cropped **3000 × 3000** covers without re-encoding their audio/video streams. The output is checked; if artwork processing fails, the original media is retained with a warning.
- **Updated workspace:** revised home and selection panels, queue counter, About section and a manual application-update check.

See the [2.2.0 release notes](releases/2.2.0.md) for details and the scope of release verification.

## Application and engine updates

Application updates and yt-dlp updates are separate:

- **Application:** the stable edition's Settings screen can check the official Android GitHub release. This check does not silently download or install an APK.
- **Download engine:** the GitHub edition supports official yt-dlp stable/nightly updates with version and checksum checks. Invalid updates are rejected while the working engine is retained.
- **Bundled engine:** source builds include yt-dlp **2026.08.19**, recorded with its upstream URL and SHA-256 in [yt-dlp-bundle.json](yt-dlp-bundle.json). An engine installed through an in-app update may be newer.

| Edition | Application ID | Distribution and updates |
| --- | --- | --- |
| GitHub stable | `space.zenithw.app.stable` | Signed APKs from GitHub Releases; supports in-app yt-dlp updates. |
| Preview / debug | `space.zenithw.app.preview` | Separate local test installation; retain its local signing key to install subsequent preview updates. |
| F-Droid build variant | `space.zenithw.app.fdroid` | Separate unsigned build for packaging. External engine updates are disabled; the bundled engine changes with app releases. |
| Legacy application | `space.zenithw.app` | Uses the original package identity and cannot be updated by these APKs because its signing key is unavailable. |

The repository provides F-Droid metadata and a dedicated build variant. This does not establish F-Droid approval or availability; see the [packaging notes](docs/FDROID.md) for the build process and review requirements.

## Privacy and sessions

Media is downloaded and processed on the device without uploading it to the ZenithW web backend. Settings and history are stored locally. Source sites, GitHub update endpoints and any optional services you enable still receive the requests needed for those features; local processing does not make those requests anonymous.

Cookie profiles are encrypted at rest with AES-GCM using an Android Keystore key. For a job that needs a session, cookies are decrypted into a temporary app-private file and removed during job cleanup. Cookies can grant access to an account: use them only when needed and never attach them, private URLs or tokens to public issue reports.

## Compatibility and limitations

- Source support follows yt-dlp and the source platform. Private or deleted media, login requirements, regional or age restrictions, rate limits and upstream changes can prevent downloads. A cookie profile does not guarantee access.
- Quality, codecs, subtitles and processing options depend on the media and container. Reliable square artwork embedding is not supported for Opus, Ogg, WebM or WAV. Enlarging a small cover does not add image detail.
- Full native **16 KB memory-page compatibility is not established**. The bundled native runtime still has known alignment limitations; affected FFmpeg operations need validation on real 16 KB-page devices.
- The 2.2.0 release notes record build, critical lint, package, version, ABI and signature checks. They do not claim runtime verification of real downloads, native FFmpeg operations or every device configuration.

Use ZenithW only for content you own or have permission to download and use. Respect copyright and platform terms. ZenithW is not affiliated with the supported platforms.

## Build from source

Requirements: **JDK 17**, **Android SDK Platform 36**, **Build Tools 36.0.0** and accepted Android SDK licenses. Android Studio is optional. The repository includes the Gradle wrapper; a separate Gradle installation is unnecessary.

Clone the repository, open its root directory, and configure the SDK through Android Studio or a local `local.properties` file:

```properties
sdk.dir=/path/to/android/sdk
```

Build the debug/preview edition:

```sh
# Linux / macOS / WSL
./gradlew :app:assembleDebug
```

```powershell
# Windows PowerShell
.\gradlew.bat :app:assembleDebug
```

Architecture-specific APKs are written under `app/build/outputs/apk/debug/`. The first debug build generates `.signing/preview.keystore`; keep that local key to update existing preview installations. When using Windows Gradle, build from a local Windows directory rather than a `\\wsl$` network path.

To build the unsigned universal F-Droid variant:

```sh
./gradlew --no-daemon assembleRelease -Pfdroid=true
```

Output: `app/build/outputs/apk/release/app-release-unsigned.apk`. It requires signing before installation. Official stable releases use the retained project signing key; the [release workflow](.github/workflows/android-release.yml) restores it from the `ANDROID_SIGNING_KEY_BASE64` secret and verifies release packages. Private signing keys are not included in the repository.

### Code organization

The application source is in [`app/src/main/java/space/zenithw/app/`](app/src/main/java/space/zenithw/app/).

| Components | Responsibility |
| --- | --- |
| `ZenithUi`, `WorkspaceSheets`, `DownloadSheet`, `SettingsHub` | Compose screens and download controls. |
| `ZenithViewModel`, `Models` | Screen state, media inspection and queue input. |
| `DownloadEngine`, `DownloadWorker`, `DownloadProgress`, `SquareCover` | yt-dlp integration, background work, progress and artwork processing. |
| `LocalStore`, `CookieBrowserActivity`, `CookieSheets` | Local preferences/history and session management. |
| `BundledEngine`, `EngineUpdateSource`, `EngineArchive`, `AppReleaseChecker` | Bundled engine, verified engine updates and application release checks. |

The project uses AndroidX WorkManager, Kotlin Coroutines, Coil and youtubedl-android's library, FFmpeg and Aria2c components. Dependency versions are defined in [app/build.gradle.kts](app/build.gradle.kts) and [build.gradle.kts](build.gradle.kts).

## Contributing and support

Report bugs or suggest improvements through [GitHub Issues](https://github.com/boranseasonnew/zenithw-android/issues). Include the app version and edition, Android version, device architecture, reproduction steps and a sanitized error message. Translations, focused fixes and physical-device compatibility reports are welcome.

For implementation history and maintenance context, see [download fixes](DOWNLOAD_FIXES.md), [F-Droid packaging](docs/FDROID.md) and [update certificate notes](docs/update-ca.md). After access to the original source repository was lost, the existing APK served as a behavioral reference and the project was rebuilt into this maintained native codebase.

## License and acknowledgements

ZenithW Android is licensed under [AGPL-3.0-only](LICENSE). Third-party components retain their own licenses. Thanks to the maintainers of yt-dlp, FFmpeg, Aria2c, youtubedl-android and the other open-source projects used by ZenithW.
