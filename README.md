# ZenithW Android 2.0

Native Kotlin and Jetpack Compose reconstruction of the original ZenithW Android app.
The original Android project uses AGPL-3.0; see LICENSE.

## Included

- Matte black and gray interface with large touch targets, a bottom navigation dock
  and native bottom sheets.
- Video/audio/subtitle choices first; advanced settings in collapsible sections.
- URL paste/clear controls, system share-link reception and cancellable inspection.
- yt-dlp update checking on launch, verified downloads, a patched offline bundle.
- HTTPS-prefilled cookie browser, encrypted cookie profiles and Netscape file import.
- Download queue, notifications, scheduled/unmetered-network downloads, file opening
  and sharing.
- Aria2c option fix/native fallback and thumbnail format handling documented in
  DOWNLOAD_FIXES.md.

## Build

Use JDK 17 or newer and Android SDK 36/build tools 36.0.0.
Accept Google's SDK license and configure your local SDK using Android Studio or a
local.properties file containing sdk.dir.

On Windows, extract/copy the source into a normal local folder before building;
the Windows Gradle runtime cannot build directly from the WSL network path used here.

Build the signed preview with:

    ./gradlew :app:assembleDebug

On Windows use gradlew.bat instead. The wrapper pins Gradle 8.13 and verifies its
distribution SHA-256. The first preview build creates a local signing key under
.signing automatically. Keep this private key to update subsequent preview installs.
It is excluded from Git and from delivered source archives.

## APKs

The preview package is space.zenithw.app.preview and installs alongside the old app.
The label is ZenithW 2.0 Preview, version 2.0.0-preview, versionCode 20000.
Android 7.0/API 24 or newer is required.

ARM64, ARM32 and x86_64 previews were built successfully on 7 October 2026.
Their signatures and the packaged yt-dlp 2026.08.19 binary were checked.
The actual APKs and build report are kept in the ignored ZenithW-Builds/Android-2.0
directory in the parent workspace. They have not been run on a physical device.

### Native page-size compatibility

The ARM64 executables are 16 KB aligned. Five WebP shared libraries inside the
FFmpeg runtime archive still have 4 KB ELF alignment. The app explicitly enables
Android's per-app page-size compatibility mode for these libraries on supporting
devices. This is not a replacement for fully 16 KB compatible upstream binaries;
16 KB device downloads and thumbnail conversion remain unverified.
See https://developer.android.com/guide/practices/page-sizes .

## Updating the original installed app

A release uses the original package space.zenithw.app and requires the original
app's signing key to update the old installation. That key has not been recovered.
Release builds are deliberately unsigned until that key is supplied.

## Third-party components

- yt-dlp: official verified release; source/version/checksum in yt-dlp-bundle.json.
- youtubedl-android, FFmpeg and Aria2c: 0.18.1 native Android packages.
- AndroidX/Jetpack Compose, WorkManager, Kotlin coroutines and Coil.

Licenses/notices from the original Android project and dependency packages apply.
