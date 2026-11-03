"""Offline verification of the native curl artifact supply chain and ELF policy."""
import copy
import importlib.util
import io
import json
from pathlib import Path
import struct
import tarfile
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('package_native_curl', ROOT/'scripts/package_native_curl.py')
pack = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(pack)


def executable():
    data = bytearray(8192)
    data[:7] = b'\x7fELF\x02\x01\x01'
    struct.pack_into('<HH', data, 16, 3, 183)
    struct.pack_into('<Q', data, 32, 64)
    struct.pack_into('<HH', data, 54, 56, 4)
    headers = [
        (1, 5, 0, 0, 0, len(data), len(data), 4096),
        (3, 4, 512, 512, 0, 21, 21, 1),
        (2, 6, 1024, 1024, 0, 48, 48, 8),
        (4, 4, 2048, 2048, 0, 24, 24, 4),
    ]
    for index, header in enumerate(headers):
        struct.pack_into('<IIQQQQQQ', data, 64+index*56, *header)
    data[512:533] = b'/system/bin/linker64\x00\x00'
    struct.pack_into('<QQQQQQ', data, 1024, 5, 3072, 1, 1, 0, 0)
    data[3072:3081] = b'\x00libc.so\x00'
    struct.pack_into('<III', data, 2048, 8, 4, 1)
    data[2060:2068] = b'Android\x00'
    struct.pack_into('<I', data, 2068, 21)
    return bytes(data)


def archive_bytes(binary, license_content=b'Full upstream license\n', extras=(), link=False, duplicate=False):
    stream = io.BytesIO()
    with tarfile.open(fileobj=stream, mode='w:gz') as archive:
        member = tarfile.TarInfo('curl-impersonate')
        if link:
            member.type = tarfile.SYMTYPE
            member.linkname = '../outside'
            archive.addfile(member)
        else:
            member.size = len(binary)
            archive.addfile(member, io.BytesIO(binary))
            if duplicate:
                archive.addfile(member, io.BytesIO(binary))
        for name, content in [('LICENSE', license_content), *extras]:
            member = tarfile.TarInfo(name)
            member.size = len(content)
            archive.addfile(member, io.BytesIO(content))
    return stream.getvalue()


class NativeCurlPackagingTest(unittest.TestCase):
    def setUp(self):
        self.binary = executable()
        self.archive = archive_bytes(self.binary)
        self.record = dict(pack.elf_info(self.binary), binary_bytes=len(self.binary),
                           binary_sha256=pack.sha256(self.binary), archive='fixture.tar.gz',
                           archive_sha256=pack.sha256(self.archive),
                           url='https://github.com/lexiforest/curl-impersonate/releases/download/v2.3.2/fixture.tar.gz')
        self.manifest = dict(schema=1, version='2.3.2', source_commit='a'*40, profile='chrome136',
                             member='curl-impersonate', output_name='libzenithcurl.so',
                             minimum_android_api=21, archive_limit_bytes=16777216,
                             binary_limit_bytes=33554432,
                             licenses={'LICENSE': pack.sha256(b'Full upstream license\n')},
                             abis={'arm64-v8a': self.record})

    def test_android_elf_and_explicit_16k_limit(self):
        info = pack.validate_binary(self.binary, self.record)
        self.assertEqual(info['minimum_android_api'], 21)
        self.assertFalse(info['supports_16k'])
        with self.assertRaisesRegex(ValueError, '16KB'):
            pack.validate_binary(self.binary, self.record, require_16k=True)

    def test_wrong_hash_machine_and_dependency_rejected(self):
        for key, value in [('binary_sha256', '0'*64), ('elf_machine', 62), ('needed', ['libunknown.so'])]:
            record = dict(self.record, **{key: value})
            with self.subTest(key=key), self.assertRaises(ValueError):
                pack.validate_binary(self.binary, record)

    def test_corrupt_program_headers_rejected(self):
        binary = bytearray(self.binary)
        struct.pack_into('<Q', binary, 32, len(binary)-8)
        with self.assertRaisesRegex(ValueError, 'program headers'):
            pack.elf_info(bytes(binary))

    def test_archive_hash_and_license_hash_rejected(self):
        with self.assertRaisesRegex(ValueError, 'archive hash'):
            pack.unpack_verified(self.archive+b'altered', self.manifest, self.record)
        manifest = copy.deepcopy(self.manifest)
        manifest['licenses']['LICENSE'] = '0'*64
        with self.assertRaisesRegex(ValueError, 'License hash'):
            pack.unpack_verified(self.archive, manifest, self.record)

    def test_link_and_duplicate_members_rejected(self):
        for options in [dict(link=True), dict(duplicate=True)]:
            data = archive_bytes(self.binary, **options)
            record = dict(self.record, archive_sha256=pack.sha256(data))
            with self.subTest(options=options), self.assertRaisesRegex(ValueError, 'linked or oversized'):
                pack.unpack_verified(data, self.manifest, record)

    def test_oversized_member_and_wrong_android_api_rejected(self):
        manifest = dict(self.manifest, binary_limit_bytes=len(self.binary)-1)
        with self.assertRaisesRegex(ValueError, 'oversized'):
            pack.unpack_verified(self.archive, manifest, self.record)
        with self.assertRaisesRegex(ValueError, 'Android API'):
            pack.validate_binary(self.binary, self.record, minimum_api=25)

    def test_unrelated_traversal_member_never_extracted(self):
        data = archive_bytes(self.binary, extras=[('../outside', b'ignored')])
        record = dict(self.record, archive_sha256=pack.sha256(data))
        binary, licenses, _ = pack.unpack_verified(data, self.manifest, record)
        self.assertEqual(binary, self.binary)
        self.assertEqual(set(licenses), {'LICENSE'})

    def test_wrong_release_and_cached_tamper_fail_without_download(self):
        with tempfile.TemporaryDirectory() as directory:
            cache = Path(directory)
            (cache/self.record['archive']).write_bytes(b'bad')
            with mock.patch.object(pack.urllib.request, 'build_opener', side_effect=AssertionError('network not allowed')):
                with self.assertRaisesRegex(ValueError, 'Cached.*hash'):
                    pack.obtain_archive(self.record, cache, 16777216)
                record = dict(self.record, url='https://example.com/fixture.tar.gz')
                with self.assertRaisesRegex(ValueError, 'pinned official'):
                    pack.obtain_archive(record, cache, 16777216)
                for suffix in ('?download=1', '/different.tar.gz', '#fragment'):
                    record = dict(self.record, url=self.record['url']+suffix)
                    with self.subTest(suffix=suffix), self.assertRaisesRegex(ValueError, 'pinned official'):
                        pack.obtain_archive(record, cache, 16777216)

    def test_tls_downgrade_redirect_rejected(self):
        request = pack.urllib.request.Request(self.record['url'])
        with self.assertRaisesRegex(ValueError, 'HTTPS'):
            pack.HttpsRedirects().redirect_request(request, None, 302, '', {}, 'http://example.com/file')

    def test_package_exact_executable_notices_and_provenance(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root/self.record['archive']).write_bytes(self.archive)
            result = pack.package(self.manifest, ['arm64-v8a'], root, root/'jni', root/'assets')
            self.assertEqual((root/'jni/arm64-v8a/libzenithcurl.so').read_bytes(), self.binary)
            self.assertEqual((root/'assets/LICENSE').read_bytes(), b'Full upstream license\n')
            self.assertEqual(json.loads((root/'assets/provenance.json').read_text()), result)
            self.assertEqual(result['abis']['arm64-v8a']['sha256'], pack.sha256(self.binary))

    def test_failed_verification_writes_no_output(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root/self.record['archive']).write_bytes(b'wrong')
            with self.assertRaises(ValueError):
                pack.package(self.manifest, ['arm64-v8a'], root, root/'jni', root/'assets')
            self.assertFalse((root/'jni').exists())
            self.assertFalse((root/'assets').exists())


if __name__ == '__main__':
    unittest.main()
