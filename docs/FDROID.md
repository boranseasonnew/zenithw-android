# F-Droid packaging

Build from the public source with JDK 17, Android SDK 36 and build-tools 36.0.0:

```sh
python3 scripts/package_native_curl.py
./gradlew --no-daemon assembleRelease -Pfdroid=true
```

This produces one unsigned universal APK at `app/build/outputs/apk/release/app-release-unsigned.apk`, with application ID `space.zenithw.app.fdroid`. F-Droid supplies its own signature. No developer keystore is needed. The GitHub stable build continues using its existing package, signing key and ABI splits when the property is omitted.

In this edition, external engine updates are disabled in the engine itself, including forced updates, and their controls are replaced with an explanation in all five interface languages. Old stored update preferences cannot enable downloads. The bundled engine is updated only through new app versions. A successful compile is not F-Droid inclusion or publication approval.

Dependencies are AndroidX, Kotlin coroutines, Coil and `io.github.junkfood02.youtubedl-android` 0.18.1 (library, FFmpeg and aria2 modules), fetched from Google Maven and Maven Central. Native-tool dependencies and the bundled yt-dlp archive need F-Droid's provenance/license review. The bundled engine's upstream release and checksum are recorded in `yt-dlp-bundle.json`; source is https://github.com/yt-dlp/yt-dlp/tree/2026.10.21 . Bundled Python code comes from that project's official zipapp release, not proprietary code.

The browser-transport candidate additionally packages pinned upstream Android
curl-impersonate executables and complete license notices. This is a verified
prebuilt dependency, not a source rebuild. F-Droid inclusion still requires
review and, where required, a recipe rebuilding the native components from
source. See [native runtime provenance and limits](native-curl-NOTICES.md).

Release version: 3.0.0; versionCode: 3000000; minimum Android version: 7.0 (API 24). Public source license: AGPL-3.0-only. Store descriptions, the icon and the English 3.0.0 changelog are in `fastlane/metadata/android/`.

The [inclusion request](https://gitlab.com/fdroid/rfp/-/work_items/4542) remains
open as checked on 4 November 2026. Its submitted description still references
2.1.3; no `space.zenithw.app.fdroid` recipe was found in the official fdroiddata
master branch. Publishing a new GitHub release does not itself publish an
F-Droid build. The request and native-component build recipe need review for
the current version before store availability can be claimed.
