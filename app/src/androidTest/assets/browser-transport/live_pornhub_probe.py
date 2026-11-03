"""Bounded actual-device comparison. Only sanitized measurements leave private storage."""
import argparse
import json
import os
from pathlib import Path
import re
import select
import signal
import struct
import subprocess
import sys
import time


SOURCE = 'https://www.pornhub.com/view_video.php?viewkey=6ae70643a2298'


def status(error):
    return sorted(set(int(value) for value in re.findall(r'(?:HTTP Error|HTTP status|HTTP_)[ :=]*(\d{3})', str(error))))


def read_frame(process, deadline):
    def exact(count):
        data = b''
        while len(data) < count:
            remaining = deadline - time.monotonic()
            if remaining <= 0 or not select.select([process.stdout], [], [], remaining)[0]:
                raise TimeoutError()
            chunk = os.read(process.stdout.fileno(), count - len(data))
            if not chunk:
                raise EOFError()
            data += chunk
        return data
    size, = struct.unpack('>I', exact(4))
    if not 0 < size <= 16 * 1024 * 1024:
        raise ValueError()
    return json.loads(exact(size))


def run(args):
    result = {'schema': 1, 'cookies': False, 'routing': 'device_default',
              'baseline': {'state': 'pending'}, 'native': {'state': 'pending'},
              'media_probe': {'state': 'pending'}}
    report = Path(args.report)
    def save():
        report.write_text(json.dumps(result, sort_keys=True), encoding='utf-8')
    save()
    options = ['--ignore-config', '--no-playlist', '--socket-timeout', '6', '--retries', '0',
               '--extractor-retries', '0', '--skip-download']
    began = time.monotonic()
    try:
        baseline = subprocess.run([sys.executable, args.engine, *options, '--dump-single-json', SOURCE],
                                  capture_output=True, timeout=22)
        info = json.loads(baseline.stdout) if baseline.returncode == 0 else {}
        success = bool(info.get('title') and info.get('formats'))
        result['baseline'] = dict(state='complete', success=success, elapsed_ms=round((time.monotonic()-began)*1000),
            returncode=baseline.returncode, http_statuses=status(baseline.stderr.decode('utf-8', errors='replace')),
            format_count=len(info.get('formats') or []), title_present=bool(info.get('title')),
            duration=info.get('duration'))
    except Exception as error:
        result['baseline'] = dict(state='error', success=False, elapsed_ms=round((time.monotonic()-began)*1000),
            error_code=type(error).__name__, http_statuses=status(error))
    save()
    worker = None
    began = time.monotonic()
    try:
        worker = subprocess.Popen([sys.executable, '-u', args.worker, args.engine, args.plugin],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, bufsize=0)
        ready = read_frame(worker, time.monotonic()+12)
        if ready.get('event') != 'runtime_ready':
            raise ValueError()
        snapshot = Path(args.workspace) / 'live-source.json'
        body = dict(operation='inspect', url=SOURCE, snapshot=str(snapshot), args=options)
        worker.stdin.write((json.dumps(body)+'\n').encode())
        worker.stdin.flush()
        deadline = time.monotonic()+25
        handlers = []
        while True:
            event = read_frame(worker, deadline)
            if event.get('event') == 'extracting':
                handlers = event.get('handlers', [])
            if event.get('event') in ('result', 'error'):
                break
        info = event.get('info') or {}
        success = bool(event.get('event') == 'result' and info.get('title') and info.get('formats')
                       and 'NativeBrowser' in handlers)
        result['native'] = dict(state='complete', success=success, elapsed_ms=round((time.monotonic()-began)*1000),
            handler_present='NativeBrowser' in handlers, format_count=len(info.get('formats') or []),
            title_present=bool(info.get('title')), duration=info.get('duration'),
            error_code=event.get('code'), http_statuses=status(event.get('code', '')),
            extract_ms=event.get('extract_ms'), network_count=event.get('network_count'))
        save()
        if success and snapshot.is_file():
            # A small real media read verifies the existing downloader transport;
            # keep its URL/headers only in this app-private process and snapshot.
            began_media = time.monotonic()
            try:
                sys.path.insert(0, args.engine)
                import yt_dlp
                from yt_dlp.networking import Request
                data = json.loads(snapshot.read_text(encoding='utf-8'))
                formats = [item for item in data.get('formats', []) if item.get('url', '').startswith('https://')
                           and item.get('ext') == 'mp4' and item.get('protocol') in (None, 'https', 'http')]
                formats.sort(key=lambda item: (item.get('height') or 100000, item.get('tbr') or 100000))
                if not formats:
                    result['media_probe'] = dict(state='not_available', success=False, reason='NO_DIRECT_HTTPS_MP4')
                else:
                    selected = formats[0]
                    headers = dict(data.get('http_headers') or {})
                    headers.update(selected.get('http_headers') or {})
                    headers['Range'] = 'bytes=0-65535'
                    with yt_dlp.YoutubeDL({'quiet': True, 'no_warnings': True, 'socket_timeout': 6}) as ydl:
                        with ydl.urlopen(Request(selected['url'], headers=headers, extensions={'timeout': 6})) as response:
                            payload = response.read(65536)
                            result['media_probe'] = dict(state='complete', success=b'ftyp' in payload[:32],
                                bytes_read=len(payload), http_status=response.status,
                                elapsed_ms=round((time.monotonic()-began_media)*1000))
            except Exception as error:
                result['media_probe'] = dict(state='error', success=False, error_code=type(error).__name__,
                    http_statuses=status(error), elapsed_ms=round((time.monotonic()-began_media)*1000))
        else:
            result['media_probe'] = dict(state='not_attempted', success=False, reason='NATIVE_METADATA_FAILED')
    except Exception as error:
        result['native'] = dict(state='error', success=False, elapsed_ms=round((time.monotonic()-began)*1000),
            error_code=type(error).__name__, http_statuses=status(error))
        result['media_probe'] = dict(state='not_attempted', success=False, reason='NATIVE_METADATA_FAILED')
    finally:
        if worker is not None:
            worker.kill()
            worker.communicate(timeout=3)
        save()
    print(json.dumps(result, sort_keys=True))
    return 0 if result['native'].get('success') else 1


if __name__ == '__main__':
    def terminate(signum, frame):
        raise SystemExit(1)
    signal.signal(signal.SIGTERM, terminate)
    parser = argparse.ArgumentParser()
    for name in ('engine', 'worker', 'plugin', 'workspace', 'report'):
        parser.add_argument('--'+name, required=True)
    raise SystemExit(run(parser.parse_args()))
