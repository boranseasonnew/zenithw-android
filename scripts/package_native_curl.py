"""Package pinned upstream Android curl executables; never execute downloaded code.

The CLI is stored in nativeLibraryDir as libzenithcurl.so, independently of the
Python runtime. Only exact regular-file archive members are read, never extractall.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import os
import struct
import tarfile
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
SYSTEM_LIBRARIES = {"libc++_shared.so", "libdl.so", "libm.so", "libc.so"}


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def elf_info(data):
    if len(data) < 64 or data[:4] != b"\x7fELF" or data[4] not in (1, 2) or data[5] != 1:
        raise ValueError("Expected a little-endian Android ELF")
    wide = data[4] == 2
    elf_type, machine = struct.unpack_from("<HH", data, 16)
    if elf_type != 3:
        raise ValueError("Android executable must be PIE/ET_DYN")
    phoff, = struct.unpack_from("<Q" if wide else "<I", data, 32 if wide else 28)
    entsize, count = struct.unpack_from("<HH", data, 54 if wide else 42)
    fmt = "<IIQQQQQQ" if wide else "<IIIIIIII"
    if entsize != struct.calcsize(fmt) or not 1 <= count <= 128 or phoff+count*entsize > len(data):
        raise ValueError("Invalid ELF program headers")
    headers = []
    for index in range(count):
        fields = struct.unpack_from(fmt, data, phoff+index*entsize)
        if wide:
            kind, flags, offset, address, _, size, memory, alignment = fields
        else:
            kind, offset, address, _, size, memory, flags, alignment = fields
        if offset+size > len(data):
            raise ValueError("ELF segment exceeds binary bounds")
        headers.append(dict(kind=kind, offset=offset, address=address, size=size,
                            memory=memory, alignment=alignment, flags=flags))

    def text(offset, limit):
        end = data.find(b"\0", offset, min(limit, len(data)))
        if end < 0:
            raise ValueError("Unterminated ELF string")
        return data[offset:end].decode("ascii")

    interpreters, dynamic, android_api = [], [], None
    for header in headers:
        offset, end = header["offset"], header["offset"]+header["size"]
        if header["kind"] == 3:
            interpreters.append(text(offset, end))
        elif header["kind"] == 2:
            step = 16 if wide else 8
            for position in range(offset, end-step+1, step):
                tag, value = struct.unpack_from("<QQ" if wide else "<II", data, position)
                if tag == 0:
                    break
                dynamic.append((tag, value))
        elif header["kind"] == 4:
            while offset+12 <= end:
                namesize, descsize, kind = struct.unpack_from("<III", data, offset)
                offset += 12
                name = data[offset:offset+namesize].rstrip(b"\0")
                offset += (namesize+3)//4*4
                desc = data[offset:offset+descsize]
                offset += (descsize+3)//4*4
                if offset > end:
                    raise ValueError("Invalid ELF note")
                if name == b"Android" and kind == 1 and len(desc) >= 4:
                    android_api, = struct.unpack_from("<I", desc)
    if len(interpreters) != 1 or android_api is None:
        raise ValueError("Missing Android interpreter or API note")
    loads = [header for header in headers if header["kind"] == 1]
    if not loads or not any(header["flags"] & 1 for header in loads):
        raise ValueError("Missing executable load segment")
    string_addresses = [value for tag, value in dynamic if tag == 5]
    if len(string_addresses) != 1:
        raise ValueError("Missing ELF dynamic string table")
    string_address = string_addresses[0]
    segment = next((header for header in loads
                    if header["address"] <= string_address < header["address"]+header["size"]), None)
    if segment is None:
        raise ValueError("ELF string table is not file backed")
    string_offset = segment["offset"]+string_address-segment["address"]
    needed = [text(string_offset+value, segment["offset"]+segment["size"])
              for tag, value in dynamic if tag == 1]
    return dict(elf_class=64 if wide else 32, elf_machine=machine,
                interpreter=interpreters[0], needed=needed, minimum_android_api=android_api,
                load_alignment=min(header["alignment"] for header in loads),
                supports_16k=all(header["alignment"] >= 16384 and
                    header["offset"] % 16384 == header["address"] % 16384 for header in loads))


def validate_binary(data, record, minimum_api=21, require_16k=False):
    if len(data) != record["binary_bytes"] or sha256(data) != record["binary_sha256"]:
        raise ValueError("Native curl binary hash/size mismatch")
    info = elf_info(data)
    for key in ("elf_class", "elf_machine", "interpreter", "load_alignment", "supports_16k"):
        if info[key] != record[key]:
            raise ValueError("Native curl ELF mismatch: "+key)
    if set(info["needed"]) != set(record["needed"]) or not set(info["needed"]) <= SYSTEM_LIBRARIES:
        raise ValueError("Unexpected native curl runtime dependencies")
    if info["minimum_android_api"] != minimum_api or minimum_api > 24:
        raise ValueError("Native curl requires an unsupported Android API")
    if require_16k and not info["supports_16k"]:
        raise ValueError("Upstream executable is not 16KB-aligned; a source rebuild is required")
    return info


def read_member(archive, name, maximum):
    matches = [member for member in archive.getmembers() if member.name == name]
    if len(matches) != 1 or not matches[0].isfile() or not 0 < matches[0].size <= maximum:
        raise ValueError("Missing, duplicate, linked or oversized archive member: "+name)
    with archive.extractfile(matches[0]) as stream:
        result = stream.read(maximum+1)
    if len(result) != matches[0].size:
        raise ValueError("Truncated archive member: "+name)
    return result


def unpack_verified(data, manifest, record, require_16k=False):
    if len(data) > manifest["archive_limit_bytes"] or sha256(data) != record["archive_sha256"]:
        raise ValueError("Native curl archive hash/size mismatch")
    with tarfile.open(fileobj=io.BytesIO(data), mode="r:gz") as archive:
        binary = read_member(archive, manifest["member"], manifest["binary_limit_bytes"])
        info = validate_binary(binary, record, manifest["minimum_android_api"], require_16k)
        licenses = {}
        for name, expected in manifest["licenses"].items():
            if name != "LICENSE" and not name.startswith("LICENSE_"):
                raise ValueError("Invalid license member name")
            if "/" in name or "\\" in name:
                raise ValueError("Invalid license member path")
            content = read_member(archive, name, 256*1024)
            if sha256(content) != expected:
                raise ValueError("License hash mismatch: "+name)
            licenses[name] = content
    return binary, licenses, info


class HttpsRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        parsed = urllib.parse.urlsplit(newurl)
        if parsed.scheme != "https" or parsed.username or parsed.password:
            raise ValueError("Native curl download may only redirect over HTTPS")
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def obtain_archive(record, cache, limit):
    parsed = urllib.parse.urlsplit(record["url"])
    if (parsed.scheme != "https" or parsed.netloc != "github.com" or parsed.query or parsed.fragment or
            parsed.path != "/lexiforest/curl-impersonate/releases/download/v2.3.2/"+record["archive"]):
        raise ValueError("Native curl must come from its pinned official release")
    if (Path(record["archive"]).name != record["archive"] or "/" in record["archive"] or
            "\\" in record["archive"] or record["archive"] in ("", ".", "..")):
        raise ValueError("Invalid archive cache filename")
    path = cache/record["archive"]
    if path.exists():
        if path.stat().st_size > limit:
            raise ValueError("Cached native curl archive is oversized")
        data = path.read_bytes()
    else:
        cache.mkdir(parents=True, exist_ok=True)
        opener = urllib.request.build_opener(HttpsRedirects())
        with opener.open(record["url"], timeout=60) as response:
            data = response.read(limit+1)
        if len(data) > limit:
            raise ValueError("Native curl download exceeded the size limit")
        if sha256(data) != record["archive_sha256"]:
            raise ValueError("Downloaded native curl archive hash mismatch")
        path.write_bytes(data)
    if sha256(data) != record["archive_sha256"]:
        raise ValueError("Cached native curl archive hash mismatch")
    return data


def atomic_write(path, content, executable=False):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name+".partial")
    try:
        temporary.write_bytes(content)
        if executable:
            temporary.chmod(0o755)
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def package(manifest, abis, cache, output, assets, require_16k=False):
    if manifest.get("schema") != 1 or manifest.get("output_name") != "libzenithcurl.so":
        raise ValueError("Unsupported native curl manifest")
    provenance = dict(version=manifest["version"], source_commit=manifest["source_commit"],
                      profile=manifest["profile"], abis={})
    for abi in abis:
        if abi not in {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"} or abi not in manifest["abis"]:
            raise ValueError("Unsupported Android ABI: "+abi)
        record = manifest["abis"][abi]
        data = obtain_archive(record, cache, manifest["archive_limit_bytes"])
        binary, licenses, info = unpack_verified(data, manifest, record, require_16k)
        atomic_write(output/abi/manifest["output_name"], binary, executable=True)
        for name, content in licenses.items():
            atomic_write(assets/name, content)
        provenance["abis"][abi] = dict(info, sha256=record["binary_sha256"], bytes=len(binary))
    atomic_write(assets/"provenance.json", (json.dumps(provenance, indent=2)+"\n").encode())
    return provenance


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, default=ROOT/"native-curl-manifest.json")
    parser.add_argument("--abi", action="append", dest="abis")
    parser.add_argument("--cache-dir", type=Path, default=ROOT/".cache/native-curl")
    parser.add_argument("--output", type=Path, default=ROOT/"app/src/main/jniLibs")
    parser.add_argument("--assets", type=Path, default=ROOT/"app/src/main/assets/third_party/native-curl")
    parser.add_argument("--require-16k", action="store_true")
    args = parser.parse_args()
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    result = package(manifest, args.abis or list(manifest["abis"]), args.cache_dir,
                     args.output, args.assets, args.require_16k)
    print(json.dumps(result, indent=2))
