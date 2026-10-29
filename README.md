<p align="center">
  <img src="app/src/main/res/drawable/zenithw.png" width="88" height="88" alt="ZenithW logo">
</p>

<h1 align="center">ZenithW Android</h1>

<p align="center">
  Download video and audio directly on your Android device.<br>
  Open source. No ads, subscriptions or required ZenithW account.
</p>

<p align="center">
  <a href="https://github.com/boranseasonnew/zenithw-android/releases/latest"><img src="https://img.shields.io/github/v/release/boranseasonnew/zenithw-android?style=flat&color=8b5cf6" alt="Latest release"></a>
  <a href="#installation"><img src="https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 7.0 or later"></a>
  <a href="https://github.com/boranseasonnew/zenithw-android/releases"><img src="https://img.shields.io/github/downloads/boranseasonnew/zenithw-android/total?color=6366f1" alt="GitHub release downloads"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-AGPL--3.0--only-64748b" alt="AGPL-3.0-only license"></a>
</p>

<p align="center">
  <a href="https://github.com/boranseasonnew/zenithw-android/releases/latest">Download APK</a> ·
  <a href="https://zenithw.space/downloads">Website & Windows</a> ·
  <a href="https://github.com/boranseasonnew/zenithw-android/issues">Report an issue</a> ·
  <a href="tr-readme.md">Türkçe</a>
</p>

ZenithW is a native Kotlin and Jetpack Compose app powered by **yt-dlp**, **FFmpeg** and **Aria2c**. Downloads and media processing run on your device using its network connection. Your media does not pass through the ZenithW web download service.

## Installation

**Current stable: [2.4.1](https://github.com/boranseasonnew/zenithw-android/releases/tag/v2.4.1).** Requires **Android 7.0+ (API 24)**, internet access and enough free storage. Choose a signed APK below:

| APK architecture | Choose this for |
| --- | --- |
| [**arm64-v8a APK**](https://github.com/boranseasonnew/zenithw-android/releases/download/v2.4.1/Zenith-2.4.1-stable-arm64-v8a.apk) | Most modern Android phones and tablets |
| [**armeabi-v7a APK**](https://github.com/boranseasonnew/zenithw-android/releases/download/v2.4.1/Zenith-2.4.1-stable-armeabi-v7a.apk) | Older devices running 32-bit ARM Android |
| [**x86_64 APK**](https://github.com/boranseasonnew/zenithw-android/releases/download/v2.4.1/Zenith-2.4.1-stable-x86_64.apk) | Compatible Intel/AMD Android devices and emulators |

Open the APK and allow installation from your browser or file manager if Android asks. Existing stable installations can be updated with the same signed edition. Verify downloads with [SHA256SUMS.txt](https://github.com/boranseasonnew/zenithw-android/releases/download/v2.4.1/SHA256SUMS.txt); the [signing-certificate fingerprint](release-certificate.sha256) is also public.

## What you can do

- **Video & audio:** choose quality, source formats and audio options; download subtitles, metadata and cover art where supported.
- **Live progress:** view the cover, duration, file size, speed and progress on the home screen, then open or share saved files from history.
- **Batch & playlists:** add up to 50 URL rows with **+**, or select individual playlist entries.
- **Terminal workspace:** save yt-dlp option templates and inspect and copy task logs, and retry failed jobs.
- **Your preferences:** choose the output folder, themes, download options, SponsorBlock, network settings and history behavior.
- **Saved sessions:** capture cookies with the built-in browser or import a Netscape cookie file. Profiles are encrypted at rest.

The interface supports **English, Turkish, German, French and Russian**. The GitHub edition also supports verified yt-dlp stable/nightly updates from Settings, separately from app updates.

## Getting started

1. Paste a media link, or share one to ZenithW from another app.
2. Choose video, audio or subtitles in the download panel while media details load.
3. Start the download and follow its progress on the home screen.

## New in 2.4.1

- Previously archived videos show as skipped instead of failing with “No output files.” An explicit retry downloads again without changing your archive preference.
- Finished download cards leave the home screen; saved files remain accessible from history.
- Download-panel opening and dismissal are more reliable, repeated taps reuse the open panel, and early preview titles are retained when starting a download.

See the [release notes](releases/2.4.1.md). [GitHub Actions validation](https://github.com/boranseasonnew/zenithw-android/actions/runs/40908115943) passed Android 13/7 instrumentation checks, lint, JVM and Python tests, and signed-package checks. This patch has **not** been verified with a physical-device YouTube download.

The instant preview system introduced in 2.4.0 is documented in the [performance report](docs/metadata-performance-2.4.0.md), with measured results and testing limits. The bundled yt-dlp version is **2026.10.21**; an engine updated through Settings can have a different version from the app.

## Availability & limitations

Downloads depend on yt-dlp support and each source's access requirements. Cookies or a VPN do not guarantee access; the bundled Android runtime currently lacks `curl_cffi` impersonation support. Full native 16 KB memory-page compatibility is not established.

**F-Droid submission is pending**; a packaging variant is available in the source, with external engine updates disabled. See [F-Droid packaging](docs/FDROID.md).

## Build from source

Use **JDK 17**, **Android SDK Platform 36** and **Build Tools 36.0.0**. Configure the SDK path in Android Studio or `local.properties`, then run:

```sh
./gradlew :app:assembleDebug
```

On Windows: `.\gradlew.bat :app:assembleDebug`. APKs appear in `app/build/outputs/apk/debug/`. Keep the generated `.signing/preview.keystore` to update your debug installation. Official signed releases are built and uploaded by [GitHub Actions](.github/workflows/android-release.yml).

## Credits & license

Created by [boranseason](https://github.com/boranseasonnew). Licensed under [AGPL-3.0-only](LICENSE); dependencies retain their own licenses.

[Seal](https://github.com/JunkFood02/Seal) v1.13.1 informed the download configuration, settings and terminal workflows. These were implemented independently; no Seal source code or visual assets are bundled. Details: [comparison](docs/seal-comparison.md) · [third-party notices](THIRD_PARTY_NOTICES.md).

Found a problem? [Open an issue](https://github.com/boranseasonnew/zenithw-android/issues) with your app/Android version, reproduction steps and a sanitized error message. Keep cookies and private links out of public reports. Use ZenithW for media you own or have permission to download.
