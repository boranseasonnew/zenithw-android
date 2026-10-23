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
from pathlib import Path

wire = sys.stdout.buffer


def emit(event, **values):
    payload = json.dumps(dict(event=event, **values), ensure_ascii=False).encode('utf-8')
    if len(payload) > 16 * 1024 * 1024:
        raise ValueError('Metadata frame too large')
    wire.write(struct.pack('>I', len(payload)))
    wire.write(payload)
    wire.flush()


class QuietStream:
    # Third-party diagnostic strings may contain signed URLs. Do not forward them.
    def write(self, value):
        return len(value)

    def flush(self):
        pass


sys.stdout = sys.stderr = QuietStream()
started = time.monotonic()
sys.path.insert(0, sys.argv[1])
import yt_dlp
emit('runtime_ready', import_ms=round((time.monotonic() - started) * 1000))

FORMAT_FIELDS = ('format_id', 'ext', 'height', 'width', 'fps', 'vcodec', 'acodec',
                 'tbr', 'filesize', 'filesize_approx', 'has_drm')


def compact(info):
    result = {key: info.get(key) for key in ('id', 'title', 'uploader', 'duration',
               'thumbnail', 'thumbnails', '_type', 'webpage_url') if info.get(key) is not None}
    result['formats'] = [{k: f.get(k) for k in FORMAT_FIELDS if f.get(k) is not None}
                         for f in info.get('formats', [])]
    for key in ('subtitles', 'automatic_captions'):
        result[key] = {language: [] for language in info.get(key, {})}
    if 'entries' in info:
        result['entries'] = [compact(entry) for entry in info.get('entries', []) if entry is not None]
    # Flat playlist entries need their source URL, but format URLs never cross this protocol.
    if info.get('_type') in ('url', 'url_transparent'):
        result['url'] = info.get('url')
    return result


class RequestLogger:
    def debug(self, message):
        pass

    info = warning = error = debug


cached_ydl = None
cached_args = None
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
        # Cookie-bearing objects are deliberately not reused after their temporary
        # cookie file is removed. The interpreter/import cache is still shared.
        reusable = '--cookies' not in args
        construct = time.monotonic()
        reused = reusable and cached_ydl is not None and args == cached_args
        if reused:
            ydl = cached_ydl
        else:
            parsed = yt_dlp.parse_options(args)
            params = parsed.ydl_opts
            params.update(logger=RequestLogger(), quiet=True, noprogress=True, ignoreerrors=False,
                          simulate=True, skip_download=True, postprocessors=[])
            ydl = yt_dlp.YoutubeDL(params)
            if reusable:
                if cached_ydl is not None:
                    cached_ydl.close()
                cached_ydl, cached_args = ydl, args
        emit('extracting', construct_ms=round((time.monotonic()-construct)*1000), reused=reused)
        original_urlopen = ydl.urlopen

        def timed_urlopen(req):
            global network_count, network_headers_ms
            tick = time.monotonic()
            try:
                return original_urlopen(req)
            finally:
                elapsed = round((time.monotonic()-tick)*1000)
                network_count += 1
                network_headers_ms += elapsed
                # No URL, query, request headers, cookies or tokens in events.
                emit('network', request=network_count, headers_ms=elapsed)

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
        snapshot = json.dumps(sanitized, ensure_ascii=False)
        if len(snapshot.encode('utf-8')) <= 4*1024*1024:
            Path(request['snapshot']).write_text(snapshot, encoding='utf-8')
        if not reusable:
            # Finish cookie persistence before Kotlin removes its private directory.
            ydl.close()
            ydl = None
        emit('result', info=compact(sanitized), extract_ms=extract_ms,
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
        code = 'HTTP_' + status[1] if status else category
        emit('error', code=code, elapsed_ms=round((time.monotonic()-begin)*1000),
             network_count=network_count, network_headers_ms=network_headers_ms)
