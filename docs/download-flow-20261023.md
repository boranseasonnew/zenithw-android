# Download flow maintenance — 23 October 2026

These changes build on the published 2.3.0 source. They do not establish a new
published application release or verified support for a particular source site.

## Single downloads and live progress

The saved playlist preference permits collection selection when the input or
resolved preview identifies a collection. A single media item no longer makes
an additional flat-playlist request merely because that preference is enabled.
Its queued options disable playlist expansion while retaining the user's global
preference. This also permits reuse of a fresh inspected metadata snapshot.

Downloads submitted from the home configuration sheet stay on home. A compact
media card above the link field displays the cover, title, source URL, duration,
actual transfer bytes, transfer ETA and progress. Unknown sizes stay unknown;
processing and saving are indeterminate. The library remains accessible from
the header. Batch and command workflows retain their dedicated pages.

The configuration header displays a wider cover with a duration badge. Available
format sizes are explicitly approximate estimates; transfer progress uses the
selected stream plan and actual byte reports. No additional marketing copy was
added to home.

## Artwork and history

A structured `before_dl` print projection updates title, thumbnail and duration
together, including jobs queued before the optional preview finishes. Thumbnail
arrays are a fallback when the primary thumbnail field is absent. Artwork and
duration changes are persisted even when the progress save throttle is active.

Before moving downloaded files, the app retains a bounded, downsampled JPEG from
a sidecar or embedded cover in app-private storage. Library and home cards prefer
this local image. Older history can recover embedded artwork from its saved
content URIs without repeating source extraction. Remote thumbnail requests use
the source origin as Referer and the configured/session User-Agent; cookies are
not forwarded to arbitrary thumbnail hosts. No signed media URLs or account
cookies are added to test artifacts.

## Settings navigation

Each settings category and the root list has a separate saved lazy-list state,
including transitions into and back from About. Stable category keys prevent
scroll positions from being associated with a different row. The category list
uses larger headings, icon tiles, consistent spacing and subtle borders.

## Session and source diagnostics

New in-app browser captures retain the browser's User-Agent alongside the
encrypted cookie payload. Inspect, playlist and download requests use it when
no explicit User-Agent override is configured. Existing captures without this
field need to be saved again; an imported Netscape file does not contain the
browser agent. The preview cache key includes the cookie revision and captured
agent. A 403/410 retry refreshes a cached snapshot only when one was actually
used; a fresh source rejection is not immediately repeated without new inputs.

Inspection errors log the actual ERROR lines separately from long optional
dependency warnings. Previously, the 175-character update diagnostic formatter
could hide the source HTTP error behind an impersonation warning.

The official youtubedl-android 0.18.1 AAR was inspected: its runtime contains
Python 3.12 and does not bundle cffi or curl_cffi. Updating the yt-dlp zipapp alone
does not install these native dependencies. Upstream
[runtime support PR #361](https://github.com/yausername/youtubedl-android/pull/361)
is still open. No unmerged native runtime or third-party binary was substituted.

The supplied Seal screenshot shows yt-dlp nightly `2026.10.21.233146`; this is an
engine version rather than the Seal APK version. The official latest Seal release
is [v1.13.1](https://github.com/JunkFood02/Seal/releases/tag/v1.13.1). Its
[optional User-Agent/session request behavior](https://github.com/JunkFood02/Seal/blob/a2d7e5d5819d0aa8b4f5dbb906731da283a28ed1/app/src/main/java/com/junkfood/seal/util/DownloadUtil.kt)
was compared with this implementation. Matching those request inputs is useful,
but does not prove successful Pornhub extraction on the reporting device.

## Validation

Local Android debug/release Kotlin and instrumentation sources compile; a debug
APK is built, and Android lint passes with warnings. Platform-independent checks pass for the existing download, input,
inspection and command policies plus preserving the real extraction failure.
The JSON print projection was checked against yt-dlp 2026.10.21 with populated
and absent thumbnail fields.

`DownloadRegressionTest` covers metadata persistence, captured-agent request
options, single-media submission with playlist defaults, and retained artwork.
`ReleaseSmokeTest` adds settings-scroll and synthetic home-card screenshot checks.
The existing GitHub Actions smoke script runs these on its Android 13 emulator.
Device test execution and rendering must be reported from the actual Actions
result, separately from compilation. No physical phone is connected locally.
Real Pornhub extraction with the user's VPN/session remains unverified.
