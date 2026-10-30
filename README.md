<p align="center">
  <img src="app/src/main/res/drawable/zenithw.png" width="88" height="88" alt="ZenithW logo">
</p>

<h1 align="center">ZenithW for Android</h1>

<p align="center">
  A private, open-source way to download and manage online media on Android.<br>
  No ads, subscriptions, or ZenithW account required.
</p>

<p align="center">
  <a href="https://github.com/boranseasonnew/zenithw-android/releases/latest"><img src="https://img.shields.io/github/v/release/boranseasonnew/zenithw-android?style=flat&color=8b5cf6" alt="Latest release"></a>
  <img src="https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 7.0 or later">
  <a href="https://github.com/boranseasonnew/zenithw-android/releases"><img src="https://img.shields.io/badge/release%20downloads-173-6366f1?logo=github" alt="173 GitHub release asset downloads as of 2026-10-30"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-AGPL--3.0--only-64748b" alt="AGPL-3.0-only license"></a>
</p>

<p align="center">
  <a href="https://github.com/boranseasonnew/zenithw-android/releases/latest">Get the latest APK</a> ·
  <a href="https://zenithw.space/downloads">Website & Windows</a> ·
  <a href="https://github.com/boranseasonnew/zenithw-android/issues">Report an issue</a> ·
  <a href="tr-readme.md">Türkçe</a>
</p>

## What is ZenithW?

ZenithW is a native Android app for saving online video and audio for personal, offline access. It combines **Kotlin**, **Jetpack Compose**, **yt-dlp**, **FFmpeg** and **Aria2c** in one interface.

Downloads and media processing run on your device using its network connection. Your media does not pass through the ZenithW web download service. ZenithW is open source and does not require an account, subscription or ad-supported service.

## What you can do

- **Choose what to save:** select video quality, available formats, audio, subtitles, metadata and cover art.
- **Track active downloads:** see cover art, duration, file size, speed and progress; open or share saved files from history.
- **Work with multiple links:** queue up to 50 URL rows or select individual entries from a playlist.
- **Manage tasks:** save yt-dlp option templates, inspect and copy task logs, and retry failed jobs.
- **Make it yours:** set the output folder, theme, download options, SponsorBlock, network settings and history behavior.
- **Use saved sessions:** capture cookies with the built-in browser or import a Netscape cookie file. Cookie profiles are encrypted at rest.

The interface is available in **English, Turkish, German, French and Russian**. The GitHub edition can also check for verified yt-dlp stable or nightly updates from Settings; engine updates are separate from app updates.

## Get the app

The latest stable release is **[2.4.1](https://github.com/boranseasonnew/zenithw-android/releases/tag/v2.4.1)** and requires **Android 7.0 or later (API 24+)**. Download the APK that matches your device:

| APK | Typical devices |
| --- | --- |
| [arm64-v8a](https://github.com/boranseasonnew/zenithw-android/releases/download/v2.4.1/Zenith-2.4.1-stable-arm64-v8a.apk) | Most modern Android phones and tablets |
| [armeabi-v7a](https://github.com/boranseasonnew/zenithw-android/releases/download/v2.4.1/Zenith-2.4.1-stable-armeabi-v7a.apk) | Older 32-bit ARM devices |
| [x86_64](https://github.com/boranseasonnew/zenithw-android/releases/download/v2.4.1/Zenith-2.4.1-stable-x86_64.apk) | Compatible Intel/AMD devices and emulators |

For file integrity, compare the downloaded APK with [SHA256SUMS.txt](https://github.com/boranseasonnew/zenithw-android/releases/download/v2.4.1/SHA256SUMS.txt). The public [signing-certificate fingerprint](release-certificate.sha256) helps verify update compatibility.

## Project status

Version **2.4.1** improves handling of previously archived items, completed-download history and download-panel behavior. See the [release notes](releases/2.4.1.md). Its [GitHub Actions validation](https://github.com/boranseasonnew/zenithw-android/actions/runs/40908115943) passed Android 13 and Android 7 instrumentation checks, lint, JVM and Python tests, and signed-package checks. This release has **not** been verified with a physical-device YouTube download.

Downloads depend on yt-dlp support and the source's access requirements. The bundled Android runtime currently lacks `curl_cffi` impersonation support, so cookies or a VPN cannot guarantee access. Full native 16 KB memory-page compatibility has not been established.

The [F-Droid submission](docs/FDROID.md) is pending.

## Build from source

To build a debug APK, use **JDK 17**, **Android SDK Platform 36** and **Build Tools 36.0.0**. Configure the SDK path in Android Studio or `local.properties`, then run:

```sh
./gradlew :app:assembleDebug
```

On Windows, run `.\\gradlew.bat :app:assembleDebug`. The APK is written to `app/build/outputs/apk/debug/`. Official signed releases are built and published by [GitHub Actions](.github/workflows/android-release.yml).

## Credits & license

Created by [boranseason](https://github.com/boranseasonnew). ZenithW is licensed under [AGPL-3.0-only](LICENSE); dependencies retain their own licenses.

[Seal](https://github.com/JunkFood02/Seal) v1.13.1 informed the download configuration, settings and terminal workflows. These were implemented independently; no Seal source code or visual assets are bundled. See the [comparison](docs/seal-comparison.md) and [third-party notices](THIRD_PARTY_NOTICES.md).

Found a problem? [Open an issue](https://github.com/boranseasonnew/zenithw-android/issues) with your app and Android versions, reproduction steps and a sanitized error message. Do not include cookies or private links in public reports. Use ZenithW only for media you own or have permission to download.
