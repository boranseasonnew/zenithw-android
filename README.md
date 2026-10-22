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

Get a signed APK from the [latest release](https://github.com/boranseasonnew/zenithw-android/releases/latest). Requires **Android 7.0+ (API 24)**, internet access and enough free storage.

| APK architecture | Choose this for |
| --- | --- |
| **arm64-v8a** | Most modern Android phones and tablets |
| **armeabi-v7a** | Older devices running 32-bit ARM Android |
| **x86_64** | Compatible Intel/AMD Android devices and emulators |

Open the APK and allow installation from your browser or file manager if Android asks. Existing stable installations can be updated with the same signed edition. Release files include `SHA256SUMS.txt`; the [signing-certificate fingerprint](release-certificate.sha256) is also public.

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

**New in 2.3.1:** single downloads avoid unnecessary playlist discovery, active downloads stay on the home screen, artwork is retained for history, and settings restore your scroll position. See the [release notes](releases/2.3.1.md). **Not tested:** automated tests and lint were skipped for this publication.

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
