"""Local-only real Android native/yt-dlp integration; no remote media or accounts.

The adjacent certificate/private key are public test fixtures, never production
credentials. Their CA is trusted only by this isolated test process and copy of
the app's plugin configuration. All source requests terminate at our local proxy.
"""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import select
import signal
import shutil
import socket
import socketserver
import ssl
import struct
import subprocess
import sys
import threading
import time


MEDIA = b'\x00\x00\x00\x18ftypmp42\x00\x00\x00\x00mp42isom' + bytes(range(256)) * 8
SOURCE = 'https://www.pornhub.com/view_video.php?viewkey=fixture'
CAPTURED_AGENT = 'ZenithW controlled browser fixture/1.0'


def require(condition, code):
    if not condition:
        raise AssertionError(code)


def frame(process, deadline):
    def exact(count):
        data = b''
        while len(data) < count:
            remaining = deadline - time.monotonic()
            require(remaining > 0, 'WORKER_DEADLINE')
            require(select.select([process.stdout], [], [], remaining)[0], 'WORKER_DEADLINE')
            part = os.read(process.stdout.fileno(), count - len(data))
            require(part, 'WORKER_EARLY_EXIT')
            data += part
        return data
    size, = struct.unpack('>I', exact(4))
    require(0 < size <= 16 * 1024 * 1024, 'WORKER_FRAME_LIMIT')
    return json.loads(exact(size))


class Proxy(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True

    def __init__(self, certificate, key):
        super().__init__(('127.0.0.1', 0), ProxyRequest)
        self.context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        self.context.load_cert_chain(certificate, key)
        self.context.set_alpn_protocols(['http/1.1'])
        self.requests = []
        self.lock = threading.Lock()
        self.holding = threading.Event()
        self.hold_closed = threading.Event()
        self.thread = threading.Thread(target=self.serve_forever, daemon=True)
        self.thread.start()

    @property
    def url(self):
        return 'http://127.0.0.1:' + str(self.server_address[1])

    def close(self):
        self.shutdown()
        self.server_close()
        self.thread.join(2)


class ProxyRequest(socketserver.BaseRequestHandler):
    def handle(self):
        self.request.settimeout(12)
        try:
            reader = self.request.makefile('rb')
            request = reader.readline(8193)
            if not request.startswith(b'CONNECT '):
                return
            while reader.readline(8193) not in (b'\r\n', b'\n', b''):
                pass
            self.request.sendall(b'HTTP/1.1 200 Connection established\r\n\r\n')
            reader.close()
            with self.server.context.wrap_socket(self.request, server_side=True) as secured:
                secured.settimeout(12)
                with secured.makefile('rb') as source:
                    line = source.readline(8193).decode('latin-1')
                    fields = line.split(' ')
                    if len(fields) < 3:
                        return
                    method, target = fields[:2]
                    headers = {}
                    while True:
                        line = source.readline(8193)
                        if line in (b'\r\n', b'\n', b''):
                            break
                        name, value = line.decode('latin-1').split(':', 1)
                        headers[name.lower()] = value.strip()
                    with self.server.lock:
                        self.server.requests.append((target, headers))
                    if 'viewkey=hold' in target:
                        self.server.holding.set()
                        # curl waits for headers; parent cancellation must close
                        # this connection promptly, rather than its 20s deadline.
                        try:
                            while secured.recv(1):
                                pass
                        except (OSError, ssl.SSLError):
                            pass
                        finally:
                            self.server.hold_closed.set()
                        return
                    status, kind = 200, 'text/html'
                    if '/video/get_media' in target:
                        body = json.dumps([{'quality': '360', 'videoUrl': 'https://cdn.phncdn.com/360p_100k.mp4'}]).encode()
                        kind = 'application/json'
                    elif '/360p_100k.mp4' in target:
                        body, kind = MEDIA, 'video/mp4'
                    elif 'viewkey=deny' in target:
                        status, body = 403, b'Controlled source rejection'
                    else:
                        data = {'video_duration': 125, 'image_url': 'https://cdn.phncdn.com/cover.jpg',
                                'mediaDefinitions': [{'format': 'mp4', 'quality': '360',
                                    'videoUrl': 'https://www.pornhub.com/video/get_media?fixture=1'}]}
                        body = ('<html><head><meta name="twitter:title" content="Controlled fixture">'
                                '</head><body><script>var flashvars_123 = ' + json.dumps(data) +
                                ';</script></body></html>').encode()
                    result = (f'HTTP/1.1 {status} Fixture\r\nContent-Type: {kind}\r\n'
                              f'Content-Length: {len(body)}\r\nConnection: close\r\n\r\n').encode()
                    secured.sendall(result + (b'' if method == 'HEAD' else body))
        except (OSError, ValueError, ssl.SSLError):
            # An untrusted-CA negative test deliberately aborts the handshake.
            pass


def descendants(pid):
    found = set()
    # Linux reports children for each native thread, not just the leader.
    for task in Path('/proc', str(pid), 'task').glob('*'):
        try:
            found.update(int(value) for value in (task / 'children').read_text().split())
        except (OSError, ValueError):
            pass
    return found


def alive(pid):
    try:
        fields = Path('/proc', str(pid), 'stat').read_text().split(') ', 1)[1].split()
        return fields[0] != 'Z'
    except (OSError, IndexError):
        return False


def run(args):
    work = Path(args.workspace)
    native = subprocess.run([args.binary, '--disable', '--version'], capture_output=True, timeout=6)
    require(native.returncode == 0 and b'curl ' in native.stdout, 'NATIVE_EXECUTION_FAILED')
    plugin_root = work / 'plugins'
    plugin = plugin_root / 'zenith/yt_dlp_plugins/extractor/zenith_browser.py'
    plugin.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(args.plugin, plugin)
    config = dict(binary=args.binary, ca_file=args.certificate, temp_dir=str(work))
    plugin.with_suffix('.json').write_text(json.dumps(config))
    cookies = work / 'fixture-cookies.txt'
    cookies.write_text('# Netscape HTTP Cookie File\nwww.pornhub.com\tFALSE\t/\tTRUE\t0\tsession\tfixture-only\n')
    env = dict(os.environ, SSL_CERT_FILE=args.certificate)
    proxy = Proxy(args.certificate, args.key)
    processes = []
    try:
        options = ['--ignore-config', '--no-playlist', '--socket-timeout', '4', '--retries', '0',
                   '--extractor-retries', '0', '--proxy', proxy.url, '--cookies', str(cookies),
                   '--user-agent', CAPTURED_AGENT]
        def start_worker(url):
            process = subprocess.Popen([sys.executable, '-u', args.worker, args.engine, str(plugin)],
                stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, env=env, bufsize=0)
            processes.append(process)
            require(frame(process, time.monotonic() + 12)['event'] == 'runtime_ready', 'WORKER_NOT_READY')
            body = dict(operation='inspect', url=url, snapshot=str(work / 'source.json'), args=options + ['--skip-download'])
            process.stdin.write((json.dumps(body) + '\n').encode())
            process.stdin.flush()
            return process

        worker = start_worker(SOURCE)
        events = []
        deadline = time.monotonic() + 15
        while True:
            event = frame(worker, deadline)
            events.append(event)
            if event['event'] in ('result', 'error'):
                break
        require(events[-1]['event'] == 'result', 'METADATA_' + events[-1].get('code', 'FAILED'))
        info = events[-1]['info']
        require(info.get('title') == 'Controlled fixture' and info.get('duration') == 125, 'METADATA_VALUES')
        require(info.get('formats'), 'METADATA_FORMATS_MISSING')
        require(any('NativeBrowser' in event.get('handlers', []) for event in events), 'METADATA_HANDLER_MISSING')
        worker.kill(); worker.wait(timeout=3)

        output = work / 'fixture-output.mp4'
        cli = subprocess.run([sys.executable, args.engine, *options, '--plugin-dirs', str(plugin_root),
                              '--no-progress', '--no-part', '--no-mtime', '--no-simulate', '-f', 'best',
                              '-o', str(output), SOURCE], capture_output=True, env=env, timeout=18)
        require(cli.returncode == 0, 'CLI_EXTRACTION_OR_DOWNLOAD_FAILED')
        require(output.is_file() and output.read_bytes() == MEDIA, 'CLI_MEDIA_BYTES_MISMATCH')
        with proxy.lock:
            requests = list(proxy.requests)
        webpages = [headers for target, headers in requests if 'view_video.php' in target]
        media = [headers for target, headers in requests if '/360p_100k.mp4' in target]
        require(len(webpages) >= 2 and media, 'FIXTURE_REQUESTS_MISSING')
        require(all(headers.get('user-agent') == CAPTURED_AGENT for headers in webpages), 'CAPTURED_AGENT_NOT_PRESERVED')
        require(all('session=fixture-only' in headers.get('cookie', '') for headers in webpages), 'SELECTED_COOKIE_NOT_PRESERVED')
        require(all('session=fixture-only' not in headers.get('cookie', '') for headers in media), 'COOKIE_LEAKED_TO_CDN')

        # A proper source403 stays an error, rather than silent fallback/success.
        rejected = start_worker(SOURCE.replace('fixture', 'deny'))
        deadline = time.monotonic() + 12
        while True:
            event = frame(rejected, deadline)
            if event['event'] in ('result', 'error'):
                break
        require(event.get('code') == 'HTTP_403', 'SOURCE_REJECTION_NOT_RETAINED')
        rejected.kill(); rejected.wait(timeout=3)

        # Trust production's normal roots for the negative probe. This fixture's
        # private CA must fail; no insecure option is used anywhere in the probe.
        config['ca_file'] = args.system_ca
        plugin.with_suffix('.json').write_text(json.dumps(config))
        rejected = start_worker(SOURCE)
        deadline = time.monotonic() + 12
        while True:
            event = frame(rejected, deadline)
            if event['event'] in ('result', 'error'):
                break
        require(event['event'] == 'error' and 'TLS' in event.get('code', ''), 'UNTRUSTED_CA_NOT_REJECTED')
        rejected.kill(); rejected.wait(timeout=3)

        config['ca_file'] = args.certificate
        plugin.with_suffix('.json').write_text(json.dumps(config))
        held = start_worker(SOURCE.replace('fixture', 'hold'))
        require(proxy.holding.wait(8), 'CANCELLATION_REQUEST_NOT_STARTED')
        child_pids = descendants(held.pid)
        require(child_pids, 'NATIVE_CHILD_NOT_OBSERVED')
        held.kill(); held.wait(timeout=3)
        require(proxy.hold_closed.wait(3), 'NATIVE_CONNECTION_SURVIVED_PARENT')
        deadline = time.monotonic() + 3
        while any(alive(pid) for pid in child_pids) and time.monotonic() < deadline:
            time.sleep(.05)
        require(not any(alive(pid) for pid in child_pids), 'NATIVE_PROCESS_SURVIVED_PARENT')
        return dict(native_execution=True, metadata=True, cli_download=True, tls_verified=True,
                    source_403_retained=True, cookie_scope=True, captured_agent=True, parent_cancellation=True)
    finally:
        for process in processes:
            if process.poll() is None:
                process.kill()
            process.communicate(timeout=3)
        proxy.close()


if __name__ == '__main__':
    def terminate(signum, frame):
        # Java Process.destroy() must run the probe's child cleanup too.
        raise SystemExit(1)
    signal.signal(signal.SIGTERM, terminate)
    parser = argparse.ArgumentParser()
    for name in ('engine', 'worker', 'plugin', 'binary', 'system-ca', 'certificate', 'key', 'workspace'):
        parser.add_argument('--' + name, required=True)
    try:
        print(json.dumps(run(parser.parse_args()), sort_keys=True))
    except BaseException as error:
        # Never print third-party diagnostics, proxy headers or private URLs.
        print('NATIVE_BROWSER_PROBE_FAILED ' + (str(error) if isinstance(error, AssertionError) else type(error).__name__))
        raise SystemExit(1)
