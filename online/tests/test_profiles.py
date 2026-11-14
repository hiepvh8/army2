"""Account isolation, optimistic concurrency and portable-save HTTP storage."""
import concurrent.futures
import http.client
import json
from pathlib import Path
import struct
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
import zlib

from server.main import Application, make_server
from server.profiles import MAX_PROFILE_BYTES, validate_profile
from server.game import GameError


def archive(seed=b'12345678'):
    data = (b'AVP1' + b'\x02' + struct.pack('>ii', 1, len(seed)) + seed + b'\x02'
            + struct.pack('>i', -1) + struct.pack('>i', 0))
    return data + struct.pack('>I', zlib.adler32(data) & 0xffffffff)


class ProfilesTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.db = str(Path(self.tmp.name) / 'accounts.db')
        self.app = Application(self.db)
        self.http = make_server('127.0.0.1', 0, self.app)
        self.worker = threading.Thread(target=self.http.serve_forever, daemon=True)
        self.worker.start()
        self.url = 'http://127.0.0.1:' + str(self.http.server_port)
        self.a = self.register('Alice')
        self.b = self.register('Bobby')

    def tearDown(self):
        self.http.shutdown()
        self.http.server_close()
        self.worker.join()
        self.app.game.db.close()
        self.tmp.cleanup()

    def register(self, name):
        req = urllib.request.Request(self.url + '/api', json.dumps({
            'op': 'register', 'name': name, 'password': 'a-long-password-123'}).encode())
        with urllib.request.urlopen(req, timeout=10) as res:
            return json.load(res)['token']

    def request(self, op, token, data=b'', revision=None):
        headers = {'Authorization': 'Bearer ' + token}
        if revision is not None:
            headers['X-Profile-Revision'] = str(revision)
        req = urllib.request.Request(self.url + '/profile/' + op, data, headers)
        try:
            with urllib.request.urlopen(req, timeout=10) as res:
                return res.status, res.headers, res.read()
        except urllib.error.HTTPError as res:
            return res.code, res.headers, res.read()

    def test_account_isolation_and_restart_persistence(self):
        self.assertEqual(self.request('load', self.a)[0], 404)
        status, headers, _ = self.request('save', self.a, archive(), 0)
        self.assertEqual((status, headers['X-Profile-Revision']), (200, '1'))
        self.assertEqual(self.request('load', self.b)[0], 404)
        self.assertEqual(self.request('load', 'bad-token')[0], 400)
        self.assertEqual(self.request('load', self.a)[2], archive())
        second = Application(self.db)
        try:
            uid = second.game.identify(self.a)
            self.assertEqual(second.profiles.load(uid), (1, archive()))
        finally:
            second.game.db.close()

    def test_stale_and_concurrent_uploads_do_not_overwrite(self):
        self.assertEqual(self.request('save', self.a, archive(), 0)[0], 200)
        def attempt(payload):
            return self.request('save', self.a, archive(payload), 1)[0]
        with concurrent.futures.ThreadPoolExecutor(2) as pool:
            result = list(pool.map(attempt, (b'abcdefgh', b'ABCDEFGH')))
        self.assertEqual(sorted(result), [200, 409])
        status, headers, data = self.request('load', self.a)
        self.assertEqual((status, headers['X-Profile-Revision']), (200, '2'))
        self.assertIn(data, (archive(b'abcdefgh'), archive(b'ABCDEFGH')))
        status, headers, _ = self.request('save', self.a, data, 1)
        self.assertEqual((status, headers['X-Profile-Revision']), (200, '2'))
        self.assertEqual(self.request('save', self.a, archive(b'otherone'), 0)[0], 409)
        self.assertEqual(self.request('load', self.a)[2], data)

    def test_invalid_envelopes_are_rejected_without_losing_existing_save(self):
        good = archive()
        self.assertEqual(self.request('save', self.a, good, 0)[0], 200)
        invalid = [good[:-1] + b'\0', b'bad' + good[3:], good[:-5], b'x' * 31,
                   good + b'x']
        for data in invalid:
            self.assertEqual(self.request('save', self.a, data, 1)[0], 400)
        # The size is rejected from headers before allocating or reading the body.
        conn = http.client.HTTPConnection('127.0.0.1', self.http.server_port, timeout=10)
        conn.putrequest('POST', '/profile/save')
        conn.putheader('Authorization', 'Bearer ' + self.a)
        conn.putheader('X-Profile-Revision', '1')
        conn.putheader('Content-Length', str(MAX_PROFILE_BYTES + 1))
        conn.endheaders()
        self.assertEqual(conn.getresponse().status, 400)
        conn.close()
        self.assertEqual(self.request('load', self.a)[2], good)
        for offset, value in [(21, 1), (22, 0x7f)]:
            broken = bytearray(good)
            broken[offset] = value
            broken[-4:] = struct.pack('>I', zlib.adler32(broken[:-4]) & 0xffffffff)
            with self.assertRaises(GameError):
                validate_profile(bytes(broken))

    def test_session_revocation_and_tls_guard(self):
        self.assertEqual(self.request('save', self.a, archive(), 0)[0], 200)
        uid = self.app.game.identify(self.a)
        with self.app.lock, self.app.game.db:
            self.app.game.db.execute('DELETE FROM sessions WHERE uid=?', (uid,))
        self.assertEqual(self.request('load', self.a)[0], 400)
        guarded = make_server('127.0.0.1', 0, self.app, require_tls=True)
        worker = threading.Thread(target=guarded.serve_forever, daemon=True)
        worker.start()
        old_url = self.url
        try:
            self.url = 'http://127.0.0.1:' + str(guarded.server_port)
            self.assertEqual(self.request('load', self.b)[0], 400)
        finally:
            self.url = old_url
            guarded.shutdown(); guarded.server_close(); worker.join()


if __name__ == '__main__':
    unittest.main()
