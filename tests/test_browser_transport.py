"""Real yt-dlp integration with controlled native responses; no private cookies."""
import importlib.util
import http.cookiejar
import io
import json
from pathlib import Path
import shlex
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'app/src/main/res/raw/ytdlp'))
spec = importlib.util.spec_from_file_location('zenith_browser_test', ROOT / 'app/src/main/res/raw/browser_transport.py')
transport = importlib.util.module_from_spec(spec)
spec.loader.exec_module(transport)
from yt_dlp import YoutubeDL
from yt_dlp.cookies import YoutubeDLCookieJar
from yt_dlp.networking import Request, Response
from yt_dlp.networking.exceptions import HTTPError, TransportError, UnsupportedRequest
from yt_dlp.networking.impersonate import ImpersonateTarget


class Logger:
    def __getattr__(self, name):
        return lambda *args, **kwargs: None


def response(url, status=200, headers=None, body=b'ok'):
    return Response(io.BytesIO(body), url, headers or {}, status)


class BrowserTransportTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(dir=ROOT)
        self.addCleanup(self.directory.cleanup)
        self.config = dict(binary='/app/libzenithcurl.so', ca_file='/app/ca.pem', temp_dir=self.directory.name)
        self.jar = YoutubeDLCookieJar()
        self.rh = transport.NativeBrowserRH(logger=Logger(), cookiejar=self.jar, configuration=self.config)
        self.request = Request('https://www.pornhub.com/view_video.php?viewkey=fixture',
                               extensions={'impersonate': ImpersonateTarget()})

    def test_domains_and_extensions_are_strictly_scoped(self):
        self.rh.validate(self.request)
        for url in ('https://pornhub.com.evil.test/a', 'https://evilpornhub.com/a',
                    'http://www.pornhub.com/a', 'https://@www.pornhub.com/a',
                    'https://user:password@www.pornhub.com/a', 'https://www.pornhub.com:444/a',
                    'https://www.pornhub.com\\@other.test'):
            with self.subTest(url=url), self.assertRaises(UnsupportedRequest):
                self.rh.validate(Request(url, extensions=self.request.extensions))
        with self.assertRaises(UnsupportedRequest):
            self.rh.validate(Request(self.request.url))
        for host in ('www.pornhub.com', 'pornhub.net', 'cdn.phncdn.com'):
            self.assertTrue(transport.source_url('https://' + host + '/a'))
        self.assertFalse(transport.source_url('https://www.pornhub.com/a\r\nheader'))

    def test_temporary_cookie_jar_identity_and_redirect_scope(self):
        self.jar.set_cookie(http.cookiejar.Cookie(0, 'session', 'private', None, False,
            'www.pornhub.com', False, False, '/', True, True, None, True, None, None, {}))
        seen = []
        def run(req, url, headers, body):
            seen.append((url, headers, body))
            return response(url, 302, {'Location': 'https://cdn.phncdn.com/meta'}) if len(seen) == 1 else response(url)
        with patch.object(self.rh, '_run', side_effect=run):
            result = self.rh.send(self.request)
        self.assertEqual(result.status, 200)
        self.assertIn('session=private', seen[0][1]['Cookie'])
        self.assertNotIn('Cookie', seen[1][1])
        self.assertIs(self.rh.cookiejar, self.jar)

    def test_server_cookie_updates_apply_before_next_request(self):
        count = 0
        def run(req, url, headers, body):
            nonlocal count
            count += 1
            if count == 1:
                return response(url, 302, {'Location': '/second', 'Set-Cookie': 'token=value; Path=/; Secure'})
            self.assertIn('token=value', headers['Cookie'])
            return response(url)
        with patch.object(self.rh, '_run', side_effect=run):
            self.rh.send(self.request)
        self.assertEqual(next(iter(self.jar)).name, 'token')

    def test_redirect_rejects_downgrade_other_domains_and_post_replay(self):
        for location in ('http://www.pornhub.com/a', 'https://evil.test/a', 'https://localhost/a'):
            with patch.object(self.rh, '_run', return_value=response(self.request.url, 302, {'Location': location})), self.assertRaises(TransportError):
                self.rh.send(self.request)
        post = Request(self.request.url, data=b'secret', extensions=self.request.extensions)
        with patch.object(self.rh, '_run', return_value=response(self.request.url, 307, {'Location': 'https://cdn.phncdn.com/a'})), self.assertRaises(TransportError):
            self.rh.send(post)

    def test_redirect_and_error_responses_are_bounded(self):
        with patch.object(self.rh, '_run', side_effect=lambda req, url, headers, body: response(url, 302, {'Location': '/again'})) as run:
            with self.assertRaises(HTTPError) as error:
                self.rh.send(self.request)
            self.assertTrue(error.exception.redirect_loop)
            self.assertEqual(run.call_count, 6)
        with patch.object(self.rh, '_run', return_value=response(self.request.url, 403)), self.assertRaises(HTTPError) as error:
            self.rh.send(self.request)
        self.assertEqual(error.exception.status, 403)

    def test_head_redirect_remains_head_and_post_303_becomes_get(self):
        for method, body, expected in [('HEAD', None, 'HEAD'), ('POST', b'body', 'GET')]:
            methods = []
            def run(req, url, headers, data):
                methods.append(req.method)
                return response(url, 303, {'Location': '/next'}) if len(methods) == 1 else response(url)
            with patch.object(self.rh, '_run', side_effect=run):
                self.rh.send(Request(self.request.url, data=body, method=method, extensions=self.request.extensions))
            self.assertEqual(methods, [method, expected])

    def test_private_dns_is_rejected_and_ipv4_setting_is_honored(self):
        with patch.object(transport.socket, 'getaddrinfo', return_value=[(2, 1, 6, '', ('127.0.0.1', 443))]), self.assertRaises(TransportError):
            transport.checked_addresses('www.pornhub.com', None)
        with patch.object(transport.socket, 'getaddrinfo', return_value=[(2, 1, 6, '', ('1.1.1.1', 443))]) as lookup:
            self.assertEqual(transport.checked_addresses('www.pornhub.com', '0.0.0.0'), '1.1.1.1')
            self.assertEqual(lookup.call_args.args[2], transport.socket.AF_INET)

    def test_native_configuration_preserves_proxy_ca_and_private_headers_without_argv(self):
        self.rh.proxies = {'all': 'socks5h://user:password@proxy.example:1080'}
        def native(command, **kwargs):
            self.assertEqual(command, ['/app/libzenithcurl.so', '--disable', '--config', '-'])
            text = kwargs['input'].decode()
            parsed = {}
            for line in text.splitlines():
                if ' = ' in line:
                    key, value = line.split(' = ', 1)
                    parsed.setdefault(key, []).append(shlex.split(value)[0])
            self.assertEqual(parsed['proxy'], ['socks5h://user:password@proxy.example:1080'])
            self.assertEqual(parsed['cacert'], ['/app/ca.pem'])
            self.assertEqual(parsed['proxy-cacert'], ['/app/ca.pem'])
            self.assertEqual(parsed['max-redirs'], ['0'])
            self.assertNotIn('insecure', text)
            self.assertNotIn('location', parsed)
            self.assertEqual(parsed['header'], ['Cookie: private=value', 'User-Agent: captured browser'])
            Path(parsed['dump-header'][0]).write_bytes(b'HTTP/1.1 200 Connection established\r\n\r\nHTTP/2 200\r\nContent-Type: text/html\r\nContent-Encoding: gzip\r\nSet-Cookie: a=b\r\nSet-Cookie: c=d\r\n\r\n')
            Path(parsed['output'][0]).write_bytes(b'decoded response')
            return subprocess.CompletedProcess(command, 0, b'200')
        with patch.object(transport.subprocess, 'run', side_effect=native), patch.object(transport, 'checked_addresses') as lookup, patch.dict(transport.os.environ, {'ANDROID_ROOT': ''}):
            result = self.rh._run(self.request, self.request.url, {'Cookie': 'private=value', 'User-Agent': 'captured browser'}, None)
            lookup.assert_not_called()
            self.assertEqual(result.read(), b'decoded response')
            self.assertIsNone(result.headers.get('Content-Encoding'))
            self.assertEqual(result.headers.get_all('Set-Cookie'), ['a=b', 'c=d'])
        self.assertFalse(list(Path(self.directory.name).iterdir()))

    def test_failures_do_not_expose_third_party_diagnostics(self):
        for code in (28, 60, 23):
            with patch.object(transport, 'checked_addresses', return_value='1.1.1.1'), patch.object(transport.subprocess, 'run', return_value=subprocess.CompletedProcess([], code, b'')), self.assertRaises(TransportError) as error:
                self.rh._run(self.request, self.request.url, {}, None)
            self.assertNotIn('viewkey', str(error.exception))
            self.assertIn('BROWSER_', str(error.exception))
        with self.assertRaises(TransportError):
            transport.quote('injected\nheader')

    def test_install_is_per_instance_and_does_not_enable_other_sites_globally(self):
        with tempfile.NamedTemporaryFile(dir=self.directory.name) as binary, patch.object(transport, 'configuration', return_value=dict(self.config, binary=binary.name)):
            with YoutubeDL({'quiet': True}) as enabled, YoutubeDL({'quiet': True}) as untouched:
                transport.install(enabled)
                transport.install(enabled)
                self.assertIn('NativeBrowser', enabled._request_director.handlers)
                self.assertNotIn('NativeBrowser', untouched._request_director.handlers)
                self.assertEqual(len(enabled._get_available_impersonate_targets()), 1)
                self.assertEqual(untouched._get_available_impersonate_targets(), [])


if __name__ == '__main__':
    unittest.main()
