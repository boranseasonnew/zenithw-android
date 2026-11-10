<p align="center">
  <img src="app/src/main/res/drawable/zenithw.png" width="96" height="96" alt="ZenithW logo">
</p>

<h1 align="center">ZenithW Android</h1>

<p align="center">
  Save media. Keep it simple.<br>
  Open-source Android media downloader. No ads, subscriptions or mandatory account.
</p>

<p align="center">
  <a href="https://github.com/boranseasonnew/zenithw-android/releases/latest"><img src="https://img.shields.io/github/v/release/boranseasonnew/zenithw-android?style=for-the-badge&amp;label=Android&amp;labelColor=151922&amp;logo=android&amp;logoColor=white&amp;color=2E7D32" alt="Latest Android release"></a>
  <img src="https://img.shields.io/badge/Android-7.0%2B-3DDC84?style=for-the-badge&amp;labelColor=151922&amp;logo=android&amp;logoColor=white" alt="Android 7.0 or later">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-AGPL--3.0--only-64748B?style=for-the-badge&amp;labelColor=151922&amp;logoColor=white" alt="AGPL-3.0-only license"></a>
</p>

<p align="center">
  <a href="https://github.com/boranseasonnew/zenithw-android/releases"><img src="https://img.shields.io/github/downloads/boranseasonnew/zenithw-android/total?style=for-the-badge&amp;labelColor=151922&amp;label=Android%20downloads&amp;logo=github&amp;logoColor=white&amp;color=2E7D32" alt="Android GitHub release downloads across all releases"></a>
</p>

<p align="center"><sub>The download badge counts GitHub release file downloads, not unique users or installs from other stores.</sub></p>

<p align="center">
  <a href="https://github.com/boranseasonnew/zenithw-android/releases/latest">Download APK</a> ·
  <a href="https://zenithw.space">Website</a> ·
  <a href="https://github.com/boranseasonnew/zenithw-android/issues">Issues</a> ·
  <a href="tr-readme.md">Türkçe</a>
</p>

<p align="center"><strong><a href="https://github.com/boranseasonnew/zenithw/blob/main/why_zenithw.md">✦ Why ZenithW? Read our story</a></strong></p>

ZenithW is a native Android app built with Kotlin, Jetpack Compose, yt-dlp, FFmpeg and Aria2c. Downloads and media processing run on your device using its own network connection, not through ZenithW's web download service.

## Features

- **Download control:** choose available video and audio formats, quality, subtitles, metadata and cover art.
- **Queue and history:** handle multiple links and playlists, track progress, retry tasks and review logs.
- **Media tools:** convert, trim and manage supported files locally, including GIF/WebP and subtitle workflows.
- **Personalization:** download profiles, themes, SponsorBlock and configurable storage and network options.

Available in **English, Turkish, German, French and Russian**.

## Download

Get the latest APK from [GitHub Releases](https://github.com/boranseasonnew/zenithw-android/releases/latest). Requires **Android 7.0 or later (API 24+)**.

Choose `arm64-v8a` for most modern phones, `armeabi-v7a` for older 32-bit ARM devices, or `x86_64` for compatible devices and emulators. Verify downloads using the checksums included with each release. The [signing certificate fingerprint](release-certificate.sha256) is also public.

## Documentation

- [Releases and changes](https://github.com/boranseasonnew/zenithw-android/releases) · [Implementation and test coverage](docs/android-3.1.0-report.md)
- [F-Droid packaging status](docs/FDROID.md) · [Native dependencies and limitations](docs/native-curl-NOTICES.md)

Full compatibility with devices requiring **16 KB memory pages** has not yet been verified. Available formats and downloads depend on the source and its access requirements.

## Build from source

Requires JDK 17, Android SDK Platform 36 and Build Tools 36.0.0.

```sh
python3 scripts/package_native_curl.py
./gradlew :app:assembleDebug
```

On Windows, use `python scripts/package_native_curl.py` and `.\gradlew.bat :app:assembleDebug`. Signed releases are built through [GitHub Actions](.github/workflows/android-release.yml).

## Contributing and license

Bug reports, translations and pull requests are welcome. [Report an issue](https://github.com/boranseasonnew/zenithw-android/issues) without sharing cookies or private links.

ZenithW is licensed under [AGPL-3.0-only](LICENSE). Built with open-source software; see [third-party notices](THIRD_PARTY_NOTICES.md) and the [Seal comparison](docs/seal-comparison.md). Download only content you own or have permission to use.
