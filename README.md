<div align="center">

# ⚡ ZenithW Android

**A native, open-source video and audio downloader for Android.**

Built with **Kotlin + Jetpack Compose**, powered by **yt-dlp**, **FFmpeg** and **Aria2c**.

![Version](https://img.shields.io/badge/version-2.1.3-222222?style=for-the-badge)
![Android](https://img.shields.io/badge/Android-7.0%2B-444444?style=for-the-badge&logo=android)
![License](https://img.shields.io/badge/license-AGPL--3.0-555555?style=for-the-badge)

**[🌐 Website](https://zenithw.space)** · **[📥 Downloads](https://zenithw.space/downloads)** · **[🚀 GitHub Releases](https://github.com/boranseasonnew/zenithw-android/releases/latest)** · **[🇹🇷 Türkçe README](tr-readme.md)**

**Download without the clutter.**

</div>

---

> [!IMPORTANT]
> **ZenithW Android is back with its source code.** The original Android project became inaccessible after the previous GitHub account was banned. Its existing APK was inspected using **JADX GUI** to recover a reference for the behavior, then the app was rebuilt, rewritten and modernized as a maintainable native Android project. This repository is **not** a raw decompilation export.

## ✨ Features

| | |
| --- | --- |
| 🎬 **Video & audio** | Select video quality, audio format and subtitles. |
| 🔗 **Easy link handling** | Paste a URL, clear it or send it through Android's Share menu. Cancel media inspection when needed. |
| 📚 **Download management** | Queue, notifications, scheduled tasks, file opening and sharing. |
| 🧩 **Advanced controls** | Playlists, SponsorBlock, Aria2c, speed limits, presets, proxy/network settings and logs. |
| 🍪 **Private sessions** | Encrypted cookie profiles, custom HTTPS cookie browser and Netscape cookie import. |
| ⚙️ **Engine management** | Bundled yt-dlp, verified stable/nightly updates on the GitHub edition, and recovery from invalid updates. |
| 🎨 **Native interface** | Matte black/gray theme, Jetpack Compose, bottom navigation, native sheets and touch-friendly controls. |
| 🌍 **Five languages** | Turkish, English, German, French and Russian; the selected language is preserved after restarting. |

Settings include separate categories for engine, cookies/browser, video, audio, subtitles, SponsorBlock, Aria2c, proxy/network, playlists, files/archive, presets and logs.

## 🚀 What's new in 2.1.3?

- **More reliable yt-dlp updates.** Version detection supports executable Python ZIP archives on older Android versions, without relying on a platform ZIP parser that may reject the header.
- **Nightly persistence.** The selected engine and its verified version survive app restarts; failed updates do not replace a working engine.
- **Better diagnostics.** Version mismatches, checksum failures and update network errors are recorded more precisely.
- **Android 7 compatibility work.** Official update-certificate support and focused tests for engine integrity and persistence.
- **F-Droid-ready build variant.** A separate, unsigned, universal APK can be built from source with external engine updates disabled. **This does not imply F-Droid has approved or published the app.**

Earlier **2.1.1** improvements restored the original URL card, complete settings categories, custom HTTPS cookie addresses, five-language UI and official stable/nightly update handling.

## 📥 Get ZenithW

**Download signed stable APKs from [GitHub Releases](https://github.com/boranseasonnew/zenithw-android/releases/latest)** or visit the [official download page](https://zenithw.space/downloads).

The **2.1.3** GitHub release provides APKs for:

| APK architecture | Typical devices |
| --- | --- |
| **arm64-v8a** | Most modern Android phones |
| **armeabi-v7a** | Older 32-bit ARM devices |
| **x86_64** | Compatible emulators and x86_64 devices |

Minimum supported OS: **Android 7.0 (API 24)**. The project targets **Android API 36**.

Check the release's **SHA256SUMS.txt** when verifying downloaded APKs. Only install packages from sources you trust.

### 📦 Editions and updates

| Edition | Application ID | Update behavior |
| --- | --- | --- |
| **GitHub stable** | `space.zenithw.app.stable` | Signed stable APKs can update previous stable installs when the signing certificate matches. Supports verified in-app yt-dlp stable/nightly updates. |
| **Preview / debug** | `space.zenithw.app.preview` | Separate install for testing; updates require the same locally generated signing key. |
| **F-Droid build variant** | `space.zenithw.app.fdroid` | Separate package; bundled engine is updated **only with app releases**, not through external in-app downloads. |
| **Original legacy app** | `space.zenithw.app` | Cannot be directly replaced by these builds because the original signing key is unavailable. |

These package IDs allow the editions to coexist. They **do not automatically transfer** settings or encrypted sessions between editions.

**F-Droid status:** packaging, metadata and a dedicated build workflow have been added, but inclusion and publication depend on F-Droid's separate review. See **[docs/FDROID.md](docs/FDROID.md)**.

## 🛠️ Engine and security

ZenithW uses the **yt-dlp** download engine and the **youtubedl-android** integration with FFmpeg and Aria2c.

- **Bundled yt-dlp:** `2026.08.19`; upstream URL and SHA-256 are documented in **[yt-dlp-bundle.json](yt-dlp-bundle.json)**. This is the version **included in source builds**, not necessarily the latest version available through an in-app update.
- **GitHub stable edition:** supports official **stable** and **nightly** yt-dlp channels. Update downloads are version- and checksum-verified, with fallback to official GitHub endpoints when appropriate.
- **Safety checks:** invalid, corrupted or version-mismatched updates are rejected without discarding the working installed engine.
- **F-Droid edition:** no external engine updater, including forced/manual updates. The bundled engine changes only when the application is rebuilt and released.

Cookie sessions are stored as encrypted profiles. A saved session can be selected again, and a cookie file in Netscape format can be imported. Treat exported cookies as sensitive account data.

> [!NOTE]
> Source support and media availability depend on upstream yt-dlp, the service and the content. Use ZenithW only for media you have the right to download and use.

## 🔨 Build from source

**Requirements:** JDK **17**, Android SDK **36**, Android Build Tools **36.0.0**, and accepted Android SDK licenses. Android Studio is recommended but optional.

Set your SDK path in **local.properties** if Android Studio has not configured it:

~~~properties
sdk.dir=/path/to/android/sdk
~~~

**Linux / macOS / WSL:**

~~~bash
./gradlew :app:assembleDebug
~~~

**Windows:**

~~~bat
gradlew.bat :app:assembleDebug
~~~

Build from a local Windows folder rather than a **\\wsl$** network path when using Windows Gradle.

**Unsigned universal F-Droid variant:**

~~~bash
./gradlew --no-daemon assembleRelease -Pfdroid=true
~~~

Output: **app/build/outputs/apk/release/app-release-unsigned.apk**. This APK is **unsigned** and intended for F-Droid packaging, not direct installation as a trusted signed release. See [F-Droid packaging notes](docs/FDROID.md).

### 🔐 Signing and CI

- The first debug/preview build generates a **local** key at **.signing/preview.keystore**; keep it private and retain it if you want preview updates to install over previous previews.
- Official stable builds require the **existing** retained signing key, restored in GitHub Actions from **ANDROID_SIGNING_KEY_BASE64**. A missing key fails release signing rather than silently generating another.
- **[release-certificate.sha256](release-certificate.sha256)** records the public certificate fingerprint. No private key is committed to the repository.
- The [Android release workflow](.github/workflows/android-release.yml) checks release APK signatures, package IDs, versions and ABIs, and runs Android **13** and **7** emulator checks before publication.
- The [F-Droid build workflow](.github/workflows/fdroid-build.yml) separately checks its unsigned APK and build flags.

## 📐 Compatibility and known limitations

- **Android 16 KB memory pages:** ARM64 executables have been checked for 16 KB alignment, but five WebP shared libraries in the bundled FFmpeg runtime still use **4 KB ELF alignment**. Android's per-app compatibility mode helps on supported devices, but this is **not full native 16 KB support**.
- Downloads, thumbnail conversion and affected FFmpeg operations still need verification on real 16 KB-page devices.
- Automated emulator checks are not a substitute for comprehensive testing across physical Android devices.
- Upstream websites and engine releases can change independently of ZenithW.

Technical notes: **[DOWNLOAD_FIXES.md](DOWNLOAD_FIXES.md)** · **[Android page-size guidance](https://developer.android.com/guide/practices/page-sizes)** · **[update certificate notes](docs/update-ca.md)**.

## 🧱 Technology

- **Kotlin**, **Jetpack Compose** and AndroidX
- **WorkManager**, Kotlin Coroutines and Coil
- **youtubedl-android 0.18.1** (library, FFmpeg and Aria2c components)
- **yt-dlp** (bundled upstream engine)

Third-party components are governed by their respective licenses and notices.

## 🧩 Source recovery and development

The previous Android source repository was lost with access to the old account. The existing APK provided a reference via JADX GUI. The codebase was then reorganized, rewritten where necessary and adapted to modern Android APIs, while preserving the app's core download workflows.

**Current development priorities:** physical-device testing, download reliability, native 16 KB compatibility, F-Droid source/license review, bug fixes and further interface polish.

Found a bug or want to suggest something? **[Open a GitHub issue](https://github.com/boranseasonnew/zenithw-android/issues)** with your Android version, APK edition and reproduction steps. Please **do not** share cookie files or private tokens in reports.

## ⚖️ License

ZenithW Android source is released under **GNU Affero General Public License v3.0 (AGPL-3.0)**. See **[LICENSE](LICENSE)**. Third-party dependencies remain under their own applicable licenses.

---

<div align="center">

### ⚡ ZenithW

**Android · Open source · Kotlin · Jetpack Compose · yt-dlp**

[Website](https://zenithw.space) · [Downloads](https://zenithw.space/downloads) · [Releases](https://github.com/boranseasonnew/zenithw-android/releases)

</div>
