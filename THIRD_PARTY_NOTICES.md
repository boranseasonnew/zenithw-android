# Third-party acknowledgements

ZenithW Android's own source remains under [AGPL-3.0-only](LICENSE).
Third-party libraries and bundled tools retain their respective licenses.

## Native browser transport candidate

The candidate packages official Android curl-impersonate 2.3.2 executables from
[lexiforest/curl-impersonate](https://github.com/lexiforest/curl-impersonate/releases/tag/v2.3.2).
The yt-dlp adapter is independently implemented; it does not copy Seal code.
Exact source, archive and executable digests are recorded in
`native-curl-manifest.json`. All ten complete upstream component notices are
included in APK assets under `third_party/native-curl/`. See the
[provenance, notices and compatibility limits](docs/native-curl-NOTICES.md).

## Seal

- Project: [Seal by JunkFood02 and contributors](https://github.com/JunkFood02/Seal).
- Reference version: **v1.13.1**, commit
  [`a2d7e5d5819d0aa8b4f5dbb906731da283a28ed1`](https://github.com/JunkFood02/Seal/tree/a2d7e5d5819d0aa8b4f5dbb906731da283a28ed1).
- Source license: [GNU GPL version 3](https://github.com/JunkFood02/Seal/blob/a2d7e5d5819d0aa8b4f5dbb906731da283a28ed1/LICENSE).

Seal was studied as a behavioral and design reference for the 2.3.0 changes:

1. A minimal link-entry home screen and configuration that opens before metadata
   extraction completes.
2. Compact video/audio configuration, source-format selection and expandable controls.
3. Named yt-dlp command templates, task output, cancellation and restart.
4. Appearance, preview, remembered-choice, history and destination-folder preferences.
5. Discovery request timeouts, network preferences and unnecessary subtitle enumeration.

These behaviors were independently implemented in Zenith's existing Kotlin/Compose
architecture. **No Seal source files or source snippets, logo, icons, screenshots,
translations or APK binaries were copied into or bundled with this repository or its
APKs.** Zenith is not an official Seal build, and this acknowledgement does not imply
endorsement by Seal's maintainers. The application continues to be named Zenith.

The local APK was used only for static investigation; it is not a release asset or
runtime dependency. The pinned comparison, APK digest, source references and limits
of the performance conclusions are recorded in [the comparison report](docs/seal-comparison.md).

## Reuse policy

This acknowledgement is not a substitute for the license of any code actually reused.
If a later contribution incorporates Seal source, it must identify the exact origin
and modifications, retain the original copyright and license notices, include the
applicable license text, and meet the source-distribution obligations. GNU documents
specific GPLv3/AGPLv3 combination rules in
[License Compatibility and Relicensing](https://www.gnu.org/licenses/license-compatibility.en.html);
compatibility does not remove the obligations of the original license.

This document records the scope of the Seal comparison; it is not a legal opinion
or a claim that every dependency's distribution terms have been independently audited.
