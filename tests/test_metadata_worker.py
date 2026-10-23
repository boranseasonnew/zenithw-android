"""Exercise the real bundled yt-dlp and persistent worker on a local HLS source.
No account, cookies, external site or media download is required.
"""
import http.server
import json
import os
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import threading
import time
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / 'app/src/main/res/raw/metadata_worker.py'
ENGINE = ROOT / 'app/src/main/res/raw/ytdlp'


class Source(http.server.BaseHTTPRequestHandler):
    count = 0

    def log_message(self, *args):
        pass

    def do_HEAD(self):
        self.respond(False)

    def do_GET(self):
        self.respond(True)

    def respond(self, body):
        Source.count += 1
        origin = f'http://127.0.0.1:{self.server.server_port}'
        if self.path.startswith('/fail'):
            self.send_error(503)
            return
        if self.path.startswith('/declared-duration'):
            schema = {'@context': 'https://schema.org', '@type': 'VideoObject', 'name': 'Declared duration',
                      'description': 'Controlled source metadata', 'duration': 'PT2M5S',
                      'contentUrl': origin+'/video.mp4'}
            text = '<html><head><title>Declared duration</title><script type="application/ld+json">'+json.dumps(schema)+'</script></head><body></body></html>'
            kind = 'text/html'
        elif self.path.startswith('/duration.mpd'):
            text = f'<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" mediaPresentationDuration="PT125S" type="static" minBufferTime="PT1S"><Period duration="PT125S"><AdaptationSet mimeType="video/mp4" contentType="video" codecs="avc1.42e01e"><Representation id="video" bandwidth="500000" width="640" height="360"><BaseURL>{origin}/video.mp4</BaseURL></Representation></AdaptationSet></Period></MPD>'
            kind = 'application/dash+xml'
        elif self.path.startswith('/master'):
            text = f'#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=500000,RESOLUTION=640x360,CODECS="avc1.42e01e,mp4a.40.2"\n{origin}/media.m3u8\n'
            kind = 'application/vnd.apple.mpegurl'
        elif self.path.startswith('/media'):
            text = f'#EXTM3U\n#EXT-X-TARGETDURATION:125\n#EXTINF:125,\n{origin}/segment.ts\n#EXT-X-ENDLIST\n'
            kind = 'application/vnd.apple.mpegurl'
        else:
            text = f'<html><head><title>Fixture {self.path}</title></head><body><video controls><source src="{origin}/master.m3u8" type="application/x-mpegURL"></video></body></html>'
            kind = 'text/html'
        data = text.encode()
        self.send_response(200)
        self.send_header('Content-Type', kind)
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        if body:
            self.wfile.write(data)


class Worker:
    def __init__(self):
        self.process = subprocess.Popen([sys.executable, '-u', str(SCRIPT), str(ENGINE)],
                                        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self.ready = self.read()

    def read(self):
        prefix = self.process.stdout.read(4)
        if len(prefix) != 4:
            raise RuntimeError('Worker exited: ' + self.process.stderr.read().decode())
        size, = struct.unpack('>I', prefix)
        assert 0 < size <= 16*1024*1024
        return json.loads(self.process.stdout.read(size))

    def inspect(self, url, snapshot, extra=None):
        args = ['--ignore-config', '--no-playlist', '--skip-download', '--socket-timeout', '2', '--retries', '0']
        args += extra or []
        self.process.stdin.write((json.dumps(dict(url=url, snapshot=str(snapshot), args=args))+'\n').encode())
        self.process.stdin.flush()
        events = []
        while True:
            event = self.read()
            events.append(event)
            if event['event'] in ('result', 'error'):
                return events

    def close(self):
        self.process.kill()
        self.process.communicate(timeout=5)


class MetadataWorkerTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Source)
        threading.Thread(target=cls.server.serve_forever, daemon=True).start()
        cls.origin = f'http://127.0.0.1:{cls.server.server_port}'

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()

    def setUp(self):
        cache = ROOT / '.cache/metadata-tests'
        cache.mkdir(parents=True, exist_ok=True)
        self.directory = tempfile.TemporaryDirectory(dir=cache)
        self.snapshot = Path(self.directory.name) / 'info.json'
        self.worker = Worker()

    def tearDown(self):
        self.worker.close()
        self.directory.cleanup()

    def test_real_formats_and_snapshot_without_duplicate_extraction(self):
        events = self.worker.inspect(self.origin+'/one', self.snapshot)
        result = events[-1]
        self.assertEqual('result', result['event'], events)
        self.assertTrue(result['info']['formats'])
        self.assertEqual(360, result['info']['formats'][0]['height'])
        self.assertNotIn('url', result['info']['formats'][0])
        snapshot = json.loads(self.snapshot.read_text(encoding='utf-8'))
        self.assertIn('url', snapshot['formats'][0])
        self.assertTrue(all('/segment.ts' not in f.get('url', '') for f in snapshot['formats']))
        # This exact snapshot must remain usable by the ordinary CLI downloader.
        check = subprocess.run([sys.executable, str(ENGINE), '--ignore-config', '--load-info-json',
                                str(self.snapshot), '--skip-download', '--dump-single-json'],
                               capture_output=True, text=True, timeout=20)
        self.assertEqual(0, check.returncode, check.stderr)
        self.assertIn('formats', json.loads(check.stdout))

    def test_interpreter_and_safe_anonymous_object_are_reused(self):
        first = self.worker.inspect(self.origin+'/one', self.snapshot)
        second = self.worker.inspect(self.origin+'/two', self.snapshot)
        self.assertFalse(next(e for e in first if e['event']=='extracting')['reused'])
        self.assertTrue(next(e for e in second if e['event']=='extracting')['reused'])
        self.assertNotEqual(first[-1]['info']['title'], second[-1]['info']['title'])

    def test_cookie_objects_are_not_reused(self):
        cookie = Path(self.directory.name) / 'cookies.txt'
        cookie.write_text('# Netscape HTTP Cookie File\n')
        for _ in range(2):
            events = self.worker.inspect(self.origin+'/one', self.snapshot, ['--cookies', str(cookie)])
            self.assertFalse(next(e for e in events if e['event']=='extracting')['reused'])

    def test_diagnostics_never_expose_source_query_or_signed_media_urls(self):
        events = self.worker.inspect(self.origin+'/one?token=private-test-secret', self.snapshot)
        diagnostic = json.dumps([e for e in events if e['event']!='result'])
        self.assertNotIn('private-test-secret', diagnostic)
        self.assertNotIn('http://', diagnostic)
        self.assertTrue(any(e['event']=='network' for e in events))

    def test_failure_is_bounded_and_worker_recovers(self):
        events = self.worker.inspect(self.origin+'/fail?token=hidden', self.snapshot)
        self.assertEqual('error', events[-1]['event'])
        self.assertEqual('HTTP_503', events[-1]['code'])
        self.assertNotIn('hidden', json.dumps(events))
        self.assertEqual('result', self.worker.inspect(self.origin+'/recovery', self.snapshot)[-1]['event'])

    def test_duration_is_read_from_source_metadata(self):
        result = self.worker.inspect(self.origin+'/declared-duration', self.snapshot)[-1]
        self.assertEqual('result', result['event'], result)
        self.assertEqual(125, result['info']['duration'])
        self.assertTrue(result['info']['formats'])

    def test_unknown_duration_is_not_invented(self):
        result = self.worker.inspect(self.origin+'/duration.mpd', self.snapshot)[-1]
        self.assertEqual('result', result['event'], result)
        self.assertNotIn('duration', result['info'])


if __name__ == '__main__':
    unittest.main()
