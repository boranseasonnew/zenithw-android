# F-Droid packaging

Build from the public source with JDK 17, Android SDK 36 and build-tools 36.0.0:

```sh
./gradlew --no-daemon assembleRelease -Pfdroid=true
```

This produces one unsigned universal APK at `app/build/outputs/apk/release/app-release-unsigned.apk`, with application ID `space.zenithw.app.fdroid`. F-Droid supplies its own signature. No developer keystore is needed. The GitHub stable build continues using its existing package, signing key and ABI splits when the property is omitted.

In this edition, external engine updates are disabled in the engine itself, including forced updates, and their controls are replaced with an explanation in all five interface languages. Old stored update preferences cannot enable downloads. The bundled engine is updated only through new app versions. A successful compile is not F-Droid inclusion or publication approval.

Dependencies are AndroidX, Kotlin coroutines, Coil and `io.github.junkfood02.youtubedl-android` 0.18.1 (library, FFmpeg and aria2 modules), fetched from Google Maven and Maven Central. Native-tool dependencies and the bundled yt-dlp archive need F-Droid's provenance/license review. The bundled engine's upstream release and checksum are recorded in `yt-dlp-bundle.json`; source is https://github.com/yt-dlp/yt-dlp/tree/2026.08.19 . Bundled Python code comes from that project's official zipapp release, not proprietary code.

Release version: 2.1.3; versionCode: 2001003; minimum Android version: 7.0 (API 24). Public source license: AGPL-3.0-only. Store descriptions and the icon are in `fastlane/metadata/android/`.
