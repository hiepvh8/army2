"""Authentication boundaries and persistence against a fresh, isolated database."""
import hashlib
import tempfile
import unittest
from pathlib import Path

from server.game import Game, GameError
from tools.build_client import valid_server


class AuthAudit(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.now = 1800000000
        self.path = str(Path(self.tmp.name) / 'audit.sqlite3')
        self.game = Game(self.path, clock=lambda: self.now)

    def tearDown(self):
        self.game.db.close()
        self.tmp.cleanup()

    def test_registration_boundaries_and_case_insensitive_duplicates(self):
        for name in ('', 'ab', 'a'*17, '1abc', 'abc def', 'nguyễn', 'a-b'):
            with self.subTest(name=name), self.assertRaises(GameError):
                self.game.auth('register', name, 'testpassword')
        for name in ('Abc', 'a'*16, 'A_1'):
            self.game.auth('register', name, 'testpassword')
        with self.assertRaises(GameError):
            self.game.auth('register', 'ABC', 'testpassword')
        self.assertEqual(self.game.db.execute('SELECT COUNT(*) FROM users').fetchone()[0], 3)

    def test_password_lengths_and_non_ascii(self):
        for password in ('', 'x'*9, 'x'*129):
            with self.subTest(length=len(password)), self.assertRaises(GameError):
                self.game.auth('register', 'TestUser', password)
        uid, _ = self.game.auth('register', 'TestUser', 'mật-khẩu-thử')
        self.assertEqual(self.game.auth('login', 'testuser', 'mật-khẩu-thử')[0], uid)

    def test_wrong_password_and_unknown_user_do_not_create_sessions(self):
        self.game.auth('register', 'Person', 'safe-password')
        before = self.game.db.execute('SELECT COUNT(*) FROM sessions').fetchone()[0]
        for name, password in [('Person','wrong-password'), ('Nobody','safe-password')]:
            with self.assertRaises(GameError):
                self.game.auth('login', name, password)
        self.assertEqual(self.game.db.execute('SELECT COUNT(*) FROM sessions').fetchone()[0], before)

    def test_passwords_salted_hashed_and_token_hashed(self):
        _, token = self.game.auth('register', 'Alice', 'same-password')
        self.game.auth('register', 'Bobby', 'same-password')
        rows = self.game.db.execute('SELECT salt,password FROM users ORDER BY id').fetchall()
        self.assertNotEqual(rows[0]['salt'], rows[1]['salt'])
        self.assertNotEqual(rows[0]['password'], rows[1]['password'])
        self.assertNotEqual(rows[0]['password'], 'same-password')
        self.assertEqual(rows[0]['password'], hashlib.pbkdf2_hmac('sha256', b'same-password', bytes.fromhex(rows[0]['salt']), 200000).hex())
        stored = self.game.db.execute('SELECT token FROM sessions ORDER BY rowid LIMIT 1').fetchone()[0]
        self.assertNotEqual(stored, token)
        self.assertEqual(stored, hashlib.sha256(token.encode()).hexdigest())

    def test_expiry_and_forged_tokens(self):
        uid, token = self.game.auth('register', 'Alice', 'good-password')
        self.now += 7*86400-1
        self.assertEqual(self.game.identify(token), uid)
        self.now += 1
        for candidate in (token, '', 'forged', 'x'*129):
            with self.subTest(candidate_length=len(candidate)), self.assertRaises(GameError):
                self.game.identify(candidate)

    def test_login_after_server_restart_persists_account(self):
        uid, token = self.game.auth('register', 'Alice', 'good-password')
        self.game.db.close()
        self.game = Game(self.path, clock=lambda: self.now)
        self.assertEqual(self.game.identify(token), uid)
        self.assertEqual(self.game.auth('login', 'ALICE', 'good-password')[0], uid)

    def test_build_url_cannot_disguise_remote_http_as_loopback(self):
        for endpoint in ('https://game.example.com', 'https://game.example.com/api', 'http://127.0.0.1:8080', 'http://localhost:8080'):
            with self.subTest(endpoint=endpoint):
                self.assertTrue(valid_server(endpoint))
        for endpoint in ('http://127.0.0.1:8080@evil.example', 'http://localhost:8080@evil.example', 'http://evil.example', 'https://a@evil.example', 'https://example.com:99999', 'https://'):
            with self.subTest(endpoint=endpoint):
                self.assertFalse(valid_server(endpoint))


if __name__ == '__main__':
    unittest.main()
