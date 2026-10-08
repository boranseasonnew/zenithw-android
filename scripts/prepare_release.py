"""Verify signed stable APKs before staging any public release assets."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess


ABIS = ("arm64-v8a", "armeabi-v7a", "x86_64")
PACKAGE = "space.zenithw.app.stable"


def verify_apk(apk, abi, version, version_code, build_tools, certificate):
    suffix = ".bat" if os.name == "nt" else ""
    signature = subprocess.check_output(
        [str(build_tools / f"apksigner{suffix}"), "verify", "--verbose", "--print-certs", str(apk)],
        text=True,
    )
    signers = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)$", signature, re.M)
    if signers != [certificate]:
        raise ValueError(f"{apk}: signing certificate does not match the retained stable key")
    aapt = "aapt.exe" if os.name == "nt" else "aapt"
    badging = subprocess.check_output([str(build_tools / aapt), "dump", "badging", str(apk)], text=True)
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
    if not package or package.groups() != (PACKAGE, str(version_code), version):
        raise ValueError(f"{apk}: incorrect stable package or version")
    native_code = re.search(r"^native-code: (.+)$", badging, re.M)
    if not native_code or re.findall(r"'([^']+)'", native_code[1]) != [abi]:
        raise ValueError(f"{apk}: incorrect native ABI; expected {abi}")
    if "application-debuggable" in badging:
        raise ValueError(f"{apk}: debug APK cannot be published as stable")
    if not re.search(r"^sdkVersion:'24'$", badging, re.M):
        raise ValueError(f"{apk}: unexpected minimum Android version")
    if not re.search(r"^targetSdkVersion:'36'$", badging, re.M):
        raise ValueError(f"{apk}: unexpected target Android version")
    print(f"Verified {apk.name}: {PACKAGE}, {version}, {abi}, certificate {certificate}")


def prepare_release(root, out, version, build_tools, certificate):
    match = re.fullmatch(r"(\d+)\.(\d+)\.(\d+)", version)
    if not match:
        raise ValueError("Expected a release version such as 2.0.0")
    major, minor, patch = map(int, match.groups())
    version_code = major * 1_000_000 + minor * 1_000 + patch
    found = {}
    for metadata in root.rglob("output-metadata.json"):
        data = json.loads(metadata.read_text(encoding="utf-8"))
        if data.get("variantName") != "release" or data.get("applicationId") != PACKAGE:
            raise ValueError(f"{metadata}: expected the stable release variant")
        for element in data.get("elements", []):
            filters = element.get("filters", [])
            abi = next((item.get("value") for item in filters if item.get("filterType") == "ABI"), None)
            if abi not in ABIS or len(filters) != 1 or abi in found:
                raise ValueError(f"{metadata}: unexpected or duplicate ABI output {abi}")
            source = metadata.parent / element["outputFile"]
            if "unsigned" in source.name or not source.is_file():
                raise ValueError(f"{source}: missing or unsigned release APK")
            found[abi] = source
    if set(found) != set(ABIS):
        raise ValueError(f"Missing release ABI APKs: {sorted(set(ABIS) - set(found))}")

    # Verify every source before copying or calculating hashes. No filename fallback.
    for abi in ABIS:
        verify_apk(found[abi], abi, version, version_code, build_tools, certificate)
    out.mkdir(parents=True, exist_ok=True)
    if any(out.iterdir()):
        raise ValueError(f"{out}: release staging directory must be empty")
    hashes = []
    for abi in ABIS:
        dest = out / f"Zenith-{version}-stable-{abi}.apk"
        shutil.copy2(found[abi], dest)
        digest = hashlib.sha256(dest.read_bytes()).hexdigest()
        hashes.append(f"{digest}  {dest.name}\n")
    (out / "SHA256SUMS.txt").write_text("".join(hashes), encoding="ascii")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True)
    parser.add_argument("--build-tools", type=Path, required=True)
    args = parser.parse_args()
    certificate = Path("release-certificate.sha256").read_text(encoding="ascii").strip()
    if not re.fullmatch(r"[0-9a-f]{64}", certificate):
        raise ValueError("Invalid stable signing certificate fingerprint")
    prepare_release(Path("app/build/outputs/apk/release"), Path("release-assets"),
                    args.version, args.build_tools, certificate)
