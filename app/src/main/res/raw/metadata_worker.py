"""ZenithW metadata-only worker. Persistent Python imports, one request at a time.

Uses yt-dlp's public embedding API; does not patch any extractor. stdout is a
bounded, length-prefixed private protocol, never a public log stream.
"""
import sys
import json
import struct
import time
import select
import re
import threading
import ssl
import importlib.util
from pathlib import Path
from urllib.parse import parse_qsl, urlsplit

wire = sys.stdout.buffer
wire_lock = threading.Lock()
stats_lock = threading.Lock()


def emit(event, **values):
    payload = json.dumps(dict(event=event, **values), ensure_ascii=False).encode('utf-8')
    if len(payload) > 16 * 1024 * 1024:
        raise ValueError('Metadata frame too large')
    with wire_lock:
        wire.write(struct.pack('>I', len(payload)))
        wire.write(payload)
        wire.flush()


class QuietStream:
    # Third-party diagnostic strings may contain signed URLs. Do not forward them.
    def write(self, value):
        return len(value)

    def flush(self):
        pass

    encoding = 'utf-8'

    def isatty(self):
        return False


sys.stdout = sys.stderr = QuietStream()
started = time.monotonic()
sys.path.insert(0, sys.argv[1])
import yt_dlp
browser_transport = None
if len(sys.argv) > 2:
    spec = importlib.util.spec_from_file_location('zenith_browser_transport', sys.argv[2])
    browser_transport = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(browser_transport)
emit('runtime_ready', import_ms=round((time.monotonic() - started) * 1000),
     python_version=sys.version.split()[0], openssl_version=ssl.OPENSSL_VERSION)

FORMAT_FIELDS = ('format_id', 'ext', 'height', 'width', 'fps', 'vcodec', 'acodec',
                 'tbr', 'filesize', 'filesize_approx', 'has_drm')


def compact(info):
    result = {key: info.get(key) for key in ('id', 'title', 'uploader', 'duration',
               'thumbnail', 'thumbnails', '_type', 'webpage_url') if info.get(key) is not None}
    result['formats'] = [{k: f.get(k) for k in FORMAT_FIELDS if f.get(k) is not None}
                         for f in info.get('formats', [])]
    for key in ('subtitles', 'automatic_captions'):
        result[key] = {language: [] for language in info.get(key, {})}
    result['chapters'] = [{k: chapter.get(k) for k in ('title', 'start_time', 'end_time')}
                          for chapter in info.get('chapters', [])[:500] if isinstance(chapter, dict)]
    if 'entries' in info:
        result['entries'] = [compact(entry) for entry in info.get('entries', []) if entry is not None]
    # Flat playlist entries need their source URL, but format URLs never cross this protocol.
    if info.get('_type') in ('url', 'url_transparent'):
        result['url'] = info.get('url')
    return result


def snapshot_expiry(info):
    """Return the earliest explicit expiry on a cached media or caption link."""
    candidates = [info]
    formats = info.get('formats')
    if isinstance(formats, (list, tuple)):
        candidates.extend(formats)
    for key in ('subtitles', 'automatic_captions'):
        captions = info.get(key)
        if isinstance(captions, dict):
            for tracks in captions.values():
                if isinstance(tracks, (list, tuple)):
                    candidates.extend(tracks)
    expiries = []
    for candidate in candidates:
        if not isinstance(candidate, dict):
            continue
        for field in ('url', 'manifest_url', 'fragment_base_url'):
            value = candidate.get(field)
            if not isinstance(value, str):
                continue
            try:
                pairs = parse_qsl(urlsplit(value).query, max_num_fields=256)
            except ValueError:
                continue
            for key, raw in pairs:
                if key.lower() in ('expire', 'expires', 'exp', 'expiration') and re.fullmatch(r'[0-9]{10}|[0-9]{13}', raw):
                    seconds = int(raw) // (1000 if len(raw) == 13 else 1)
                    expiries.append(seconds)
    return min(expiries, default=0)


class RequestLogger:
    def __init__(self):
        self.impersonation_unavailable = False

    def debug(self, message):
        # Observe yt-dlp's phase marker without emitting the raw text or modifying
        # the extractor/solver. Kotlin records the time from metadata_start.
        if re.fullmatch(r'\[youtube\] \[jsc:[\w-]+\] Solving JS challenges using (?:quickjs|node|deno|bun)', str(message), re.I):
            emit('js_challenge')

    info = error = debug

    def warning(self, message):
        # Retain only a capability bit; raw warnings can contain private URLs.
        if 'no impersonate target is available' in str(message).lower():
            self.impersonation_unavailable = True


cached_ydl = None
cached_args = None
cached_browser = None
while True:
    # Release the imported interpreter after two idle minutes. No busy polling.
    if sys.platform != 'win32' and not select.select([sys.stdin], [], [], 120)[0]:
        break
    line = sys.stdin.readline(65537)
    if not line:
        break
    if len(line) > 65536:
        emit('error', code='REQUEST_TOO_LARGE')
        break
    request = json.loads(line)
    if request.get('operation') == 'ping':
        emit('pong')
        continue
    begin = time.monotonic()
    network_count = 0
    network_headers_ms = 0
    ydl = None
    reusable = True
    try:
        args = request['args']
        use_browser = browser_transport is not None and browser_transport.source_url(request['url'])
        # Cookie-bearing objects are deliberately not reused after their temporary
        # cookie file is removed. The interpreter/import cache is still shared.
        reusable = '--cookies' not in args
        construct = time.monotonic()
        reused = reusable and cached_ydl is not None and args == cached_args and use_browser == cached_browser
        if reused:
            ydl = cached_ydl
        else:
            parsed = yt_dlp.parse_options(args)
            params = parsed.ydl_opts
            params.update(logger=RequestLogger(), quiet=True, noprogress=True, ignoreerrors=False,
                          simulate=True, skip_download=True, postprocessors=[])
            ydl = yt_dlp.YoutubeDL(params)
            if use_browser:
                browser_transport.install(ydl)
            if reusable:
                if cached_ydl is not None:
                    cached_ydl.close()
                cached_ydl, cached_args, cached_browser = ydl, args, use_browser
        ydl.params['logger'].impersonation_unavailable = False
        handlers = sorted(name for name in ydl._request_director.handlers
                          if re.fullmatch(r'[A-Za-z0-9_]+', name))
        emit('extracting', construct_ms=round((time.monotonic()-construct)*1000), reused=reused,
             handlers=handlers)
        original_urlopen = ydl.urlopen

        def timed_urlopen(req):
            global network_count, network_headers_ms
            tick = time.monotonic()
            try:
                return original_urlopen(req)
            finally:
                elapsed = round((time.monotonic()-tick)*1000)
                with stats_lock:
                    network_count += 1
                    network_headers_ms += elapsed
                    number = network_count
                # No URL, query, request headers, cookies or tokens in events.
                emit('network', request=number, headers_ms=elapsed)

        ydl.urlopen = timed_urlopen
        try:
            info = ydl.extract_info(request['url'], download=False, process=False)
            if info is None:
                raise ValueError('Source returned no metadata')
            # A resolved video's extractor already supplies the complete format
            # list. Skip format selection, FFmpeg capability probes and download
            # preparation here. Preserve yt-dlp's normal resolver for transparent
            # URLs, direct URLs and flat playlists rather than guessing site rules.
            if info.get('_type', 'video') != 'video' or not info.get('formats'):
                info = ydl.process_ie_result(info, download=False)
        finally:
            ydl.urlopen = original_urlopen
        extract_ms = round((time.monotonic()-begin)*1000)
        serialization = time.monotonic()
        sanitized = ydl.sanitize_info(info)
        # Retain the full info dictionary only in the app-private snapshot used by
        # --load-info-json. The small UI message avoids per-character huge JSON reads.
        link_expiry = 0
        if ('entries' not in sanitized and not sanitized.get('is_live')
                and sanitized.get('live_status') not in ('is_live', 'is_upcoming', 'post_live')):
            destination = Path(request['snapshot'])
            destination.unlink(missing_ok=True)
            staged = destination.with_suffix('.partial')
            size = 0
            complete = True
            try:
                with staged.open('w', encoding='utf-8') as output:
                    for chunk in json.JSONEncoder(ensure_ascii=False).iterencode(sanitized):
                        size += len(chunk.encode('utf-8'))
                        if size > 4*1024*1024:
                            complete = False
                            break
                        output.write(chunk)
                if complete:
                    staged.replace(destination)
                    link_expiry = snapshot_expiry(sanitized)
            finally:
                staged.unlink(missing_ok=True)
        if not reusable:
            # Finish cookie persistence before Kotlin removes its private directory.
            ydl.close()
            ydl = None
        emit('result', info=compact(sanitized), extract_ms=extract_ms,
             link_expiry_seconds=link_expiry,
             serialize_ms=round((time.monotonic()-serialization)*1000),
             network_count=network_count, network_headers_ms=network_headers_ms)
    except Exception as error:
        if not reusable and ydl is not None:
            try:
                ydl.close()
            except Exception:
                pass
        # Report actionable categories, never raw third-party error text.
        status = re.search(r'(?:HTTP Error|HTTP status|status[=:])\s*(\d{3})', str(error))
        message = str(error).lower()
        category = next((code for needle, code in (
            ('sign in', 'AUTH_REQUIRED'), ('logged-in', 'AUTH_REQUIRED'),
            ('private video', 'AUTH_REQUIRED'), ('timed out', 'NETWORK_TIMEOUT'),
            ('unsupported url', 'UNSUPPORTED_URL'), ('no video formats', 'NO_FORMATS'),
            ('video unavailable', 'UNAVAILABLE')) if needle in message), type(error).__name__)
        browser_code = re.search(r'\bBROWSER_[A-Z_]+(?:_\d+)?\b', str(error))
        code = 'HTTP_' + status[1] if status else browser_code[0] if browser_code else category
        emit('error', code=code, elapsed_ms=round((time.monotonic()-begin)*1000),
             network_count=network_count, network_headers_ms=network_headers_ms,
             impersonation_unavailable=bool(ydl is not None and
                 getattr(ydl.params.get('logger'), 'impersonation_unavailable', False)))
