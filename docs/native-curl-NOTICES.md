# Android native HTTP runtime

Zenith packages the official Android executable from
[lexiforest/curl-impersonate v2.3.2](https://github.com/lexiforest/curl-impersonate/releases/tag/v2.3.2)
to provide a supported browser TLS profile independently of Python's OpenSSL.
This integration is independently implemented; no Seal application code is copied.

`native-curl-manifest.json` pins upstream commit
`5b1c3542fc7fc0e9fe623a6c9a848d16f92fe99a`, the four release archive SHA-256
digests, each executable digest, architecture, interpreter and dependencies,
and the complete upstream license file digests. The selected profile is
`chrome136`, which is included in this release. Changing the pin requires
reviewing and verifying all of these records again.

Run `python scripts/package_native_curl.py` before the Android build. This
stdlib-only script downloads from the pinned official HTTPS release, verifies
archive and member hashes, reads only named regular files without extracting
archive paths, and checks each Android PIE ELF before writing
`app/src/main/jniLibs/<abi>/libzenithcurl.so`. Downloaded executables are not run
by the packaging script. CI must additionally execute `--version` and a local
HTTPS fixture on Android to verify the actual runtime integration.

The full, unmodified upstream `LICENSE`, `LICENSE_BORINGSSL`, `LICENSE_BROTLI`,
`LICENSE_CARES`, `LICENSE_CURL`, `LICENSE_NGHTTP2`, `LICENSE_NGHTTP3`,
`LICENSE_NGTCP2`, `LICENSE_ZLIB`, and `LICENSE_ZSTD` files are packaged at
`assets/third_party/native-curl/`, alongside generated `provenance.json`.
These notices cover curl-impersonate and its bundled components; consult the
complete individual notices for terms, copyrights, and acknowledgments.
Some release notices cover components not linked into the Android variant.
The files are preserved rather than replacing their terms with this summary.

The official Android binaries target API 21 and depend on Android's `libc`,
`libdl`, `libm`, and `libc++_shared.so`. Zenith already ships the latter with
its Python/aria2 runtime; subprocesses must retain its existing
`LD_LIBRARY_PATH`. This dependency must be tested for all supported ABIs.
Upstream's Android build uses the system threaded DNS resolver, so the
integration does not substitute public DNS servers or bypass the device VPN.
TLS certificate checks remain enabled; the HTTP adapter supplies the existing
Python CA bundle explicitly rather than relying on upstream build-machine paths.

## Current compatibility limit

These v2.3.2 release executables have **4 KiB ELF load alignment**. They are
not certified for native execution on 16 KiB page-size devices. Android ZIP
alignment alone cannot repair this ELF limitation. `--require-16k` deliberately
rejects these artifacts; supporting those devices requires rebuilding the
upstream source and its dependencies with the appropriate linker settings,
then replacing the verified pins. Do not describe this build as universally
16 KiB compatible or as a fully source-built distribution.

Static artifact checks and local adapter tests do not prove that a particular
site accepts a particular real phone's network path, VPN or account cookies.
Keep real-device download verification separate from packaging checks.
