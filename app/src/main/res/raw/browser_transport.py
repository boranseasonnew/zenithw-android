"""App-private browser transport for yt-dlp's explicit Pornhub impersonation requests.

No extractor is copied. The normal CLI and metadata worker attach the same
independent request handler to their own YoutubeDL instance. Other sites and
ordinary media downloads retain yt-dlp's existing network handlers.
"""
import email.parser
import io
import ipaddress
import json
import os
from pathlib import Path
import re
import socket
import subprocess
import sys
import tempfile
import urllib.parse
import urllib.request

from yt_dlp.networking import Request, Response
from yt_dlp.networking.common import Features
from yt_dlp.networking.exceptions import HTTPError, TransportError, UnsupportedRequest
from yt_dlp.networking.impersonate import ImpersonateRequestHandler, ImpersonateTarget
from yt_dlp.utils.networking import select_proxy


MAX_BODY = 16 * 1024 * 1024
MAX_HEADERS = 256 * 1024
ROOTS = ('pornhub.com', 'pornhub.net', 'pornhub.org', 'pornhubpremium.com',
         'pornhubpremium.net', 'pornhubpremium.org', 'thumbzilla.com',
         'phncdn.com', 'phprcdn.com')
REDIRECTS = (301, 302, 303, 307, 308)


def source_url(url):
    """Exact domain boundaries; reject userinfo, custom ports and URL ambiguity."""
    try:
        parsed = urllib.parse.urlsplit(url)
        host = (parsed.hostname or '').lower()
        return (parsed.scheme == 'https' and parsed.port in (None, 443)
                and parsed.username is None and parsed.password is None
                and not any(ord(c) < 33 or ord(c) == 127 or c == '\\' for c in url)
                and any(host == root or host.endswith('.' + root) for root in ROOTS))
    except (ValueError, TypeError):
        return False


def quote(value):
    # curl --config has its own grammar; JSON and shell escaping are not valid here.
    value = str(value)
    if any(ord(c) < 32 or ord(c) == 127 for c in value):
        raise TransportError('BROWSER_INVALID_CONFIGURATION')
    return '"' + value.replace('\\', '\\\\').replace('"', '\\"') + '"'


def setting(name, value):
    return name + ' = ' + quote(value) + '\n'


def configuration():
    return json.loads(Path(__file__).with_suffix('.json').read_text(encoding='utf-8'))


def checked_addresses(host, source):
    family = socket.AF_INET if source == '0.0.0.0' else socket.AF_INET6 if source == '::' else socket.AF_UNSPEC
    answers = socket.getaddrinfo(host, 443, family, socket.SOCK_STREAM)
    result = []
    for answer in answers:
        address = ipaddress.ip_address(answer[4][0])
        if not address.is_global or address.is_multicast or address.is_reserved:
            raise TransportError('BROWSER_NONPUBLIC_ADDRESS')
        rendered = '[' + str(address) + ']' if address.version == 6 else str(address)
        if rendered not in result:
            result.append(rendered)
    if not result:
        raise TransportError('BROWSER_DNS_FAILED')
    return ','.join(result)


class NativeBrowserRH(ImpersonateRequestHandler):
    RH_NAME = 'zenith_browser'
    _SUPPORTED_URL_SCHEMES = ('https',)
    _SUPPORTED_PROXY_SCHEMES = ('http', 'https', 'socks4', 'socks4a', 'socks5', 'socks5h')
    _SUPPORTED_FEATURES = (Features.ALL_PROXY, Features.NO_PROXY)
    _SUPPORTED_IMPERSONATE_TARGET_MAP = {
        ImpersonateTarget('chrome', '136', 'macos', '15'): 'chrome136',
    }

    def __init__(self, *, configuration=None, **kwargs):
        super().__init__(**kwargs)
        self.config = configuration if configuration is not None else globals()['configuration']()

    def _check_extensions(self, extensions):
        super()._check_extensions(extensions)
        for name in ('impersonate', 'cookiejar', 'timeout', 'keep_header_casing'):
            extensions.pop(name, None)

    def _validate(self, request):
        super()._validate(request)
        if not source_url(request.url) or not self._get_request_target(request):
            raise UnsupportedRequest('Browser transport is limited to explicit Pornhub browser requests')
        if request.method not in ('GET', 'HEAD', 'POST') or (request.data is not None and not isinstance(request.data, bytes)):
            raise UnsupportedRequest('Unsupported browser request body')
        if not self.verify or self._client_cert or self.legacy_ssl_support:
            raise UnsupportedRequest('Browser transport requires normal TLS verification')

    def _run(self, request, url, headers, body):
        timeout = min(20.0, max(1.0, self._calculate_timeout(request)))
        proxy = select_proxy(url, self._get_proxies(request)) or ''
        host = urllib.parse.urlsplit(url).hostname
        # Resolve using the app's Android resolver (and VPN), then pin the result.
        # Proxy-side DNS stays with the configured proxy, never a separate resolver.
        addresses = checked_addresses(host, self.source_address) if not proxy else None
        with tempfile.TemporaryDirectory(prefix='browser-', dir=self.config['temp_dir']) as directory:
            root = Path(directory)
            output, head = root / 'response', root / 'headers'
            text = 'silent\ncompressed\n' + setting('url', url)
            text += setting('impersonate', 'chrome136')
            text += setting('proto', '=https') + setting('proto-redir', '=https')
            text += setting('max-time', timeout) + setting('connect-timeout', min(timeout, 10))
            text += setting('max-filesize', MAX_BODY) + setting('max-redirs', 0)
            text += setting('proxy', proxy) + setting('noproxy', '')
            text += setting('cacert', self.config['ca_file'])
            text += setting('proxy-cacert', self.config['ca_file'])
            text += setting('output', output) + setting('dump-header', head)
            text += setting('write-out', '%{response_code}')
            if addresses:
                text += setting('resolve', host + ':443:' + addresses)
            if self.source_address == '0.0.0.0':
                text += 'ipv4\n'
            elif self.source_address == '::':
                text += 'ipv6\n'
            elif self.source_address:
                text += setting('interface', self.source_address)
            if request.method == 'HEAD':
                text += 'head\n'
            else:
                text += setting('request', request.method)
            for key, value in headers.items():
                if not re.fullmatch(r"[!#$%&'*+.^_`|~0-9A-Za-z-]+", key):
                    raise TransportError('BROWSER_INVALID_HEADER')
                text += setting('header', key + ': ' + value)
            if body is not None:
                payload = root / 'request'
                payload.write_bytes(body)
                text += setting('data-binary', '@' + str(payload))
            command = [self.config['binary'], '--disable', '--config', '-']
            if sys.platform != 'win32' and os.environ.get('ANDROID_ROOT'):
                # Killing the owning Python worker must also stop curl. This tiny
                # launcher installs Linux's parent-death signal before exec; curl
                # keeps its PID, UID, VPN routing and read-only APK executable.
                launcher = ('import ctypes,os,signal,sys,resource; '
                            'parent=int(sys.argv[1]); '
                            'libc=ctypes.CDLL(None); '
                            'result=libc.prctl(1,signal.SIGKILL,0,0,0); '
                            'sys.exit(125) if result or os.getppid()!=parent else None; '
                            'resource.setrlimit(resource.RLIMIT_FSIZE,(16777216,16777216)); '
                            'os.execv(sys.argv[2],sys.argv[2:])')
                command = [sys.executable, '-c', launcher, str(os.getpid()), *command]
            try:
                # No shell or request URLs/cookies/proxy credentials in argv.
                # Raw stderr is never forwarded: it can contain a signed URL.
                completed = subprocess.run(command, input=text.encode(), stdout=subprocess.PIPE,
                                           stderr=subprocess.DEVNULL, timeout=timeout + 3, check=False)
            except subprocess.TimeoutExpired as error:
                raise TransportError('BROWSER_TIMEOUT') from error
            except OSError as error:
                raise TransportError('BROWSER_RUNTIME_UNAVAILABLE') from error
            if completed.returncode:
                code = 'TIMEOUT' if completed.returncode == 28 else 'TLS' if completed.returncode in (35, 51, 60, 77) else 'TRANSFER'
                raise TransportError('BROWSER_' + code + '_' + str(completed.returncode))
            if (not head.is_file() or head.stat().st_size > MAX_HEADERS
                    or (output.is_file() and output.stat().st_size > MAX_BODY)):
                raise TransportError('BROWSER_RESPONSE_TOO_LARGE')
            raw_headers = head.read_bytes()
            # CONNECT/informational responses can precede the final header block.
            blocks = re.split(br'\r?\n\r?\n', raw_headers)
            block = next((part for part in reversed(blocks) if part.startswith(b'HTTP/')), None)
            if block is None or not re.fullmatch(rb'\d{3}', completed.stdout):
                raise TransportError('BROWSER_INVALID_RESPONSE')
            status = int(completed.stdout)
            first, _, fields = block.partition(b'\n')
            match = re.match(br'HTTP/[^ ]+ +(\d{3})(?: +([^\r\n]*))?', first)
            if not match or int(match[1]) != status:
                raise TransportError('BROWSER_INVALID_RESPONSE')
            parsed = email.parser.BytesParser().parsebytes(fields)
            response = Response(io.BytesIO(output.read_bytes() if output.is_file() and request.method != 'HEAD' else b''), url,
                                {}, status, match[2].decode('latin-1') if match[2] else None)
            response.headers = parsed
            # libcurl --compressed has already decoded the body.
            del response.headers['Content-Encoding']
            del response.headers['Content-Length']
            return response

    def _send(self, request):
        self._validate(request)
        current = request.copy()
        headers = self._get_impersonate_headers(current)
        jar = self._get_cookiejar(current)
        for hop in range(6):
            url = current.url
            if not source_url(url):
                raise TransportError('BROWSER_UNSAFE_REDIRECT')
            cookie_request = urllib.request.Request(url, headers=dict(headers))
            jar.add_cookie_header(cookie_request)
            sent = dict(headers)
            if cookie_request.has_header('Cookie'):
                sent['Cookie'] = cookie_request.get_header('Cookie')
            response = self._run(current, url, sent, current.data)
            jar.extract_cookies(response, cookie_request)
            if response.status not in REDIRECTS or not response.headers.get('Location'):
                if response.status >= 400:
                    raise HTTPError(response)
                return response
            if hop == 5:
                raise HTTPError(response, redirect_loop=True)
            location = urllib.parse.urljoin(url, response.headers['Location'])
            if not source_url(location):
                response.close()
                raise TransportError('BROWSER_UNSAFE_REDIRECT')
            if urllib.parse.urlsplit(location).netloc != urllib.parse.urlsplit(url).netloc:
                if current.data is not None and response.status in (307, 308):
                    response.close()
                    raise TransportError('BROWSER_CROSS_ORIGIN_BODY')
                headers = {k: v for k, v in headers.items() if k.lower() not in
                           ('authorization', 'cookie', 'proxy-authorization', 'origin', 'referer', 'host')}
            if (response.status == 303 and current.method != 'HEAD') or (response.status in (301, 302) and current.method == 'POST'):
                current = Request(location, headers={}, method='GET', extensions=current.extensions, proxies=current.proxies)
                headers = {k: v for k, v in headers.items() if k.lower() not in ('content-type', 'content-length')}
            else:
                current.url = location
            response.close()


def install(ydl):
    if 'NativeBrowser' in ydl._request_director.handlers:
        return
    config = configuration()
    if not Path(config['binary']).is_file():
        raise TransportError('BROWSER_RUNTIME_UNAVAILABLE')
    director = ydl.build_request_director([NativeBrowserRH])
    ydl._request_director.add_handler(director.handlers['NativeBrowser'])
    ydl._request_director.preferences.update(director.preferences)


# Public plugin extension points; only install a transport, retain upstream
# extractor initialization, login, format parsing and playlist logic unchanged.
from yt_dlp.extractor.pornhub import (PornHubIE, PornHubUserIE, PornHubPagedVideoListIE,
                                    PornHubUserVideosUploadIE, PornHubPlaylistIE)


class ZenithPornHubIE(PornHubIE, plugin_name='zenith_browser'):
    def _real_initialize(self):
        install(self._downloader)
        return super()._real_initialize()


class ZenithPornHubUserIE(PornHubUserIE, plugin_name='zenith_browser'):
    def _real_initialize(self):
        install(self._downloader)
        return super()._real_initialize()


class ZenithPornHubPagedVideoListIE(PornHubPagedVideoListIE, plugin_name='zenith_browser'):
    def _real_initialize(self):
        install(self._downloader)
        return super()._real_initialize()


class ZenithPornHubUserVideosUploadIE(PornHubUserVideosUploadIE, plugin_name='zenith_browser'):
    def _real_initialize(self):
        install(self._downloader)
        return super()._real_initialize()


class ZenithPornHubPlaylistIE(PornHubPlaylistIE, plugin_name='zenith_browser'):
    def _real_initialize(self):
        install(self._downloader)
        return super()._real_initialize()
