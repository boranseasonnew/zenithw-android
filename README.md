<div align="center">

# ⚡ Zenith Android 2.1

### **Fast. Native. Rebuilt.**

A native **Kotlin + Jetpack Compose** reconstruction of the original **ZenithW Android** application.

**Video • Audio • Subtitles • yt-dlp • Aria2c**

---

> 🧩 **The Android source code is back.**  
> The original project files were no longer accessible after the old account was banned and was not restored.  
> The application was reconstructed from the existing APK using **JADX GUI**, then cleaned up, rewritten and modernized into a maintainable native Android project.

</div>

---

## ✨ What's inside?

### Changes in 2.1

- A minimal download screen: logo, URL, Paste and Download.
- Stable/nightly update lookup uses official release redirects without the GitHub API quota.
- Complete nightly version numbers and checksum verification before installation.
- Download choices and selected encrypted sessions survive app restarts.
- Android 13 emulator checks cover channel switching, force-stop persistence and the home screen before publishing.

### Stable APK signing

Stable releases use `space.zenithw.app.stable` and install alongside the original
`space.zenithw.app` and preview. The original app's signing key is unavailable;
its settings are not migrated. Stable builds use the retained key at
`.signing/preview.keystore`, restored in Actions from `ANDROID_SIGNING_KEY_BASE64`.
Release builds fail if that key is missing; they never generate a replacement.
Keep this key for future updates. The public certificate fingerprint is recorded
in `release-certificate.sha256`; no private key is committed.

Actions verifies all three APK signatures, the stable certificate, package,
version and ABI before uploading. For a build without publishing, run the Android
release workflow with `publish_release` disabled and download `android-signed-apks`.
The original automated v2.0.0 signing issue has been fixed. Stable updates retain
the same package and signing key, so installing v2.1 over v2.0 preserves app data.

ZenithW 2.0 is not just a visual refresh.

The Android application has been reconstructed around a cleaner native architecture while preserving the core functionality of the original app.

### 🎨 Interface

- 🖤 Matte **black / dark gray** design
- 📱 Native **Jetpack Compose** interface
- 👆 Large touch targets for easier mobile usage
- 🧭 Bottom navigation dock
- 📑 Native bottom sheets
- ✨ Lightweight selection animations
- 📐 Layout designed with one-handed usage in mind

### 🔗 URL handling

- 📋 Paste links directly
- 🧹 Clear URL button
- 📤 Receive links through Android's **Share** menu
- 🔎 Cancellable media inspection
- 🌐 Cleaner URL input workflow

### 🎬 Download options

The important settings stay visible while less frequently used controls stay out of the way.

**Primary options:**

- 🎞️ Video quality
- 🎵 Audio format
- 💬 Subtitles

**Advanced options:**

- ⚡ Aria2c
- 🚦 Download speed limits
- ⏰ Scheduled downloads
- 📶 Unmetered-network-only downloads

### 📥 Download system

- 📚 Download queue
- 🔔 Android notifications
- 📂 Open downloaded files
- 📤 Share downloaded files
- ⏱️ Scheduled downloads
- 📡 Wi-Fi / unmetered network restrictions
- 🖼️ Thumbnail and cover handling improvements
- ⚙️ Native fallback when Aria2c cannot be used

See [`DOWNLOAD_FIXES.md`](DOWNLOAD_FIXES.md) for technical details about the Aria2c and thumbnail fixes.

---

## 🛠️ yt-dlp

ZenithW uses **yt-dlp** as its download engine.

The app includes:

- 🔄 Automatic update checking at startup
- ✅ Verified yt-dlp downloads
- 📦 Patched offline bundled binary
- 🔐 Version and checksum verification

Current bundled version:

```text
yt-dlp 2026.08.19
```

Bundle information, source and checksum are stored in:

```text
yt-dlp-bundle.json
```

---

## 🍪 Cookies

ZenithW 2.0 includes a redesigned cookie system.

- 🔒 Encrypted cookie profiles
- 🌐 Cookie browser with `https://` prefilled
- 💾 Saved session selection
- 📄 Netscape-format cookie file import
- ⚡ Previously saved profiles can be reused automatically

---

# 🔨 Building

## Requirements

You will need:

- **JDK 17 or newer**
- **Android SDK 36**
- **Android Build Tools 36.0.0**
- Accepted Android SDK licenses

Android Studio is recommended, but it is not strictly required.

Configure the SDK through Android Studio or create:

```text
local.properties
```

with:

```properties
sdk.dir=/path/to/your/android/sdk
```

---

## 🐧 Linux / WSL

Build using:

```bash
./gradlew :app:assembleDebug
```

---

## 🪟 Windows

Use:

```bat
gradlew.bat :app:assembleDebug
```

> ⚠️ **Important**
>
> Do not build the project directly from a WSL network path such as:
>
> ```text
> \\wsl$\Ubuntu\...
> ```
>
> Copy or extract the project into a normal Windows folder before running Gradle.

The Windows Gradle runtime may fail when the project is built directly through the WSL network filesystem.

---

## 🔐 Preview signing

The Gradle wrapper currently pins:

```text
Gradle 8.13
```

Its distribution is verified using **SHA-256**.

The first preview build automatically creates a local signing key inside:

```text
.signing/
```

### ⚠️ Keep this key private

The same key is required to install future preview builds as updates over previous preview versions.

The `.signing` directory is:

- 🚫 Excluded from Git
- 🚫 Excluded from distributed source archives
- 🔒 Intended to remain local

---

# 📦 APK builds

The earlier 2.0 preview application used:

```text
Package:
space.zenithw.app.preview

Application:
ZenithW 2.0 Preview

Version:
2.0.0-preview

Version Code:
20000
```

Minimum Android version:

```text
Android 7.0
API 24
```

---

## 🧱 Supported architectures

Preview APKs have been successfully built for:

| Architecture | Status |
|---|---|
| **ARM64 / arm64-v8a** | ✅ Built |
| **ARM32 / armeabi-v7a** | ✅ Built |
| **x86_64** | ✅ Built |

Build date:

**7 October 2026**

The APK signatures and bundled **yt-dlp 2026.08.19** binary were checked successfully.

> 🧪 These builds have not yet been fully tested on a physical Android device.

Generated APKs and build reports are stored locally under:

```text
ZenithW-Builds/Android-2.0
```

This directory is intentionally ignored by Git.

---

# 📐 Android 16 KB page-size compatibility

Modern Android devices are moving toward **16 KB memory page sizes**.

ZenithW's ARM64 executables are currently:

```text
✅ 16 KB aligned
```

However, five **WebP shared libraries** inside the bundled FFmpeg runtime still use:

```text
⚠️ 4 KB ELF alignment
```

To improve compatibility, ZenithW explicitly enables Android's **per-app page-size compatibility mode** on supported devices.

### Important

This compatibility mode is **not a replacement** for fully native 16 KB-compatible upstream binaries.

The following areas still require real-device verification on 16 KB devices:

- 📥 Downloads
- 🖼️ Thumbnail conversion
- 🎬 FFmpeg operations involving the affected libraries

Android documentation:

https://developer.android.com/guide/practices/page-sizes

---

# 🔄 Updating the original ZenithW app

The original Android application uses:

```text
space.zenithw.app
```

Android requires updates to an installed application to be signed with the **same signing certificate** as the original APK.

The original signing key has **not been recovered**.

Because of this, the earlier preview used a separate package:

```text
space.zenithw.app.preview
```

This allows **ZenithW 2.0 Preview** to be installed alongside the original ZenithW application.

### Original-package release builds

Builds targeting:

```text
space.zenithw.app
```

are signed with the retained stable key under `space.zenithw.app.stable`. They update the stable app and do not replace the original package.

---

# 🧩 Source recovery

The previous Android source repository became inaccessible after the old account was banned and the account was not restored.

Instead of abandoning the Android version, the existing APK was inspected using **JADX GUI**.

Recovered application logic was then used as a reference to reconstruct the project.

This repository is therefore **not simply a raw JADX export**.

Large portions have been:

- 🧹 Cleaned up
- 🧱 Reorganized
- ✍️ Rewritten
- 🎨 Redesigned
- ⚙️ Reimplemented using modern Android APIs
- 🧪 Prepared for continued development

The result is a maintainable **Kotlin + Jetpack Compose** project intended to continue ZenithW Android development properly.

---

# 📚 Third-party components

ZenithW Android uses several open-source components.

### yt-dlp

Official verified release.

Source, version and checksum information can be found in:

```text
yt-dlp-bundle.json
```

### Native download components

ZenithW currently uses:

```text
youtubedl-android 0.18.1
FFmpeg Android packages
Aria2c Android packages
```

### Android libraries

Including:

- Jetpack Compose
- AndroidX
- WorkManager
- Kotlin Coroutines
- Coil

---

# ⚖️ License

The original ZenithW Android project is licensed under:

**GNU Affero General Public License v3.0**

See:

[`LICENSE`](LICENSE)

Third-party libraries remain subject to their respective licenses and notices.

Licenses and notices inherited from the original Android project and dependency packages continue to apply.

---

# 🚧 Project status

> **Zenith Android 2.1 is the stable release.**

The project builds successfully, but additional device testing and compatibility work is still planned.

Current priorities include:

- 📱 Physical-device testing
- 🧪 Android version compatibility testing
- 📐 Full 16 KB native-library compatibility
- ⚡ Download reliability improvements
- 🐛 Bug fixes
- 🎨 UI polishing
- 🚀 Signed stable releases through GitHub Actions

---

<div align="center">

## ⚡ ZenithW

**Download without the clutter.**

`Android • Kotlin • Jetpack Compose • yt-dlp`

</div>
