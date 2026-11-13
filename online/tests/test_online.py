"""Real HTTP clients exercise game rules, persistence, retries and binary framing."""
import concurrent.futures
import json
from pathlib import Path
import struct
import tempfile
import threading
import unittest
import urllib.error
import urllib.parse
import urllib.request

from server.main import Application, make_server
from server.game import GameError


class OnlineTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.app = Application(str(Path(self.tmp.name) / 'test.sqlite3'))
        self.http = make_server('127.0.0.1', 0, self.app)
        self.thread = threading.Thread(target=self.http.serve_forever, daemon=True)
        self.thread.start()
        self.url = 'http://127.0.0.1:' + str(self.http.server_port)
        self.a = self.call('register', name='Alice', password='Alice-pass-123')['token']
        self.b = self.call('register', name='Bob', password='Bob-password-456')['token']

    def tearDown(self):
        self.http.shutdown()
        self.http.server_close()
        self.thread.join()
        self.app.game.db.close()
        self.tmp.cleanup()

    def call(self, op, token='', **kw):
        request = urllib.request.Request(self.url + '/api', json.dumps(dict(op=op, **kw)).encode(),
                                         {'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token})
        try:
            with urllib.request.urlopen(request, timeout=10) as r:
                return json.load(r)
        except urllib.error.HTTPError as e:
            raise GameError(json.load(e)['error'])

    def battle(self, mode='pvp'):
        room = self.call('create', self.a, mode=mode)['room']['id']
        self.call('join', self.b, room=room)
        self.call('ready', self.a)
        self.call('ready', self.b)
        return self.call('start', self.a)['room']

    def boss_win(self):
        r = self.battle('boss')
        for i in range(12):
            actor = next(p for p in r['players'] if p['id'] == r['turn'])
            r = self.call('fire', self.a if actor['name'] == 'Alice' else self.b,
                          seq=r['seq'], angle=45, power=78 if actor['x'] == 70 else 71)['room']
            if r['state'] == 'finished':
                break
        self.assertEqual(r['state'], 'finished')
        self.assertEqual(r['boss_hp'], 0)
        return r

    def test_pvp_two_clients_same_hp_and_single_reward(self):
        r = self.battle()
        for i in range(3):
            r = self.call('fire', self.a, seq=r['seq'], angle=45, power=72, damage=999999)['room']
            self.assertEqual(r, self.call('poll', self.b)['room'])
            if r['state'] != 'finished':
                r = self.call('pass', self.b, seq=r['seq'])['room']
        self.assertEqual(r['state'], 'finished')
        self.assertEqual(self.call('poll', self.a)['user']['coins'], 50)
        with self.assertRaises(GameError):
            self.call('fire', self.a, seq=r['seq'], angle=45, power=72)
        self.assertEqual(self.call('poll', self.a)['user']['wins'], 1)

    def test_coop_event_claim_once_and_concurrent_claim(self):
        for i in range(2):
            self.boss_win()
            self.call('leave', self.a)
            self.call('leave', self.b)
        self.assertEqual(self.call('poll', self.b)['event']['progress'], 2)
        def claim():
            try:
                return self.call('claim', self.a)['user']['coins']
            except GameError:
                return None
        with concurrent.futures.ThreadPoolExecutor(2) as pool:
            results = list(pool.map(lambda _: claim(), range(2)))
        self.assertEqual(sum(x is not None for x in results), 1)
        self.assertEqual(self.call('poll', self.a)['user']['coins'], 400)
        self.assertEqual(self.call('claim', self.b)['user']['coins'], 400)

    def test_auth_rejects_bad_password_and_unknown_session(self):
        with self.assertRaises(GameError):
            self.call('login', name='Alice', password='incorrect-password')
        with self.assertRaises(GameError):
            self.call('poll', 'forged-token')
        with self.assertRaises(GameError):
            self.call('register', name='alice', password='Other-pass-123')

    def test_turn_stale_duplicate_and_out_of_range_rejected(self):
        r = self.battle()
        for token, seq, angle, power in [(self.b, 1, 45, 72), (self.a, 0, 45, 72),
                                         (self.a, 1, 200, 72), (self.a, 1, 45, 101)]:
            with self.assertRaises(GameError):
                self.call('fire', token, seq=seq, angle=angle, power=power)
        self.assertEqual(self.call('poll', self.a)['room']['seq'], r['seq'])
        self.call('move', self.a, seq=r['seq'], dx=30)
        with self.assertRaises(GameError):
            self.call('fire', self.a, seq=r['seq'], angle=45, power=72)

    def test_movement_budget_and_room_capacity(self):
        r = self.battle()
        self.call('move', self.a, seq=r['seq'], dx=-30)
        with self.assertRaises(GameError):
            self.call('move', self.a, seq=r['seq']+1, dx=30)
        c = self.call('register', name='Carol', password='Carol-pass-789')['token']
        with self.assertRaises(GameError):
            self.call('join', c, room=r['id'])

    def test_reconnect_returns_current_battle_and_persistent_account(self):
        self.boss_win()
        token = self.call('login', name='Alice', password='Alice-pass-123')['token']
        self.assertEqual(self.call('poll', token)['room'], self.call('poll', self.a)['room'])
        from server.game import Game
        second = Game(str(Path(self.tmp.name) / 'test.sqlite3'))
        try:
            uid = second.identify(token)
            self.assertEqual(second.snapshot(uid)['user']['coins'], 100)
            self.assertIsNone(second.snapshot(uid)['room'])  # Live rooms don't survive a restart.
        finally:
            second.db.close()

    def test_owner_and_readiness_are_required(self):
        room = self.call('create', self.a, mode='boss')['room']['id']
        self.call('join', self.b, room=room)
        with self.assertRaises(GameError):
            self.call('start', self.b)
        with self.assertRaises(GameError):
            self.call('start', self.a)

    def test_timeout_advances_and_disconnect_abandons(self):
        r = self.battle()
        with self.app.lock:
            self.app.game.clock = lambda: 2000000000
            self.app.game.rooms[r['id']]['deadline'] = 1999999999
            self.app.game.rooms[r['id']]['seen'] = {p['id']: 2000000000 for p in r['players']}
            self.app.game.tick()
            self.assertNotEqual(self.app.game.rooms[r['id']]['index'], 0)
            self.app.game.clock = lambda: 2000000100
            self.app.game.tick()
            self.assertEqual(self.app.game.rooms[r['id']]['state'], 'finished')
            self.assertEqual(self.app.game.player(r['owner'])['coins'], 0)

    def test_event_uses_server_bangkok_day(self):
        with self.app.lock:
            # 2026-01-01 16:59 UTC is 23:59 Bangkok; then a new event day.
            self.app.game.clock = lambda: 1767286740
            first = self.app.game.event_id()
            self.app.game.clock = lambda: 1767286860
            self.assertNotEqual(self.app.game.event_id(), first)

    def test_binary_endpoint_and_error_framing(self):
        def jme(values, token):
            req = urllib.request.Request(self.url + '/jme', urllib.parse.urlencode(values).encode(),
                                          {'Authorization': 'Bearer ' + token})
            return urllib.request.urlopen(req, timeout=10).read()
        good = jme({'op': 'poll'}, self.a)
        self.assertEqual(struct.unpack('>IBH', good[:7]), (0x41324f31, 0, 0))
        bad = jme({'op': 'poll'}, 'bad')
        self.assertEqual(bad[4], 1)
        length = struct.unpack('>H', bad[5:7])[0]
        self.assertEqual(len(bad), 7+length)

    def test_forfeit_cannot_mint_rewards(self):
        self.battle()
        self.call('leave', self.a)
        self.assertEqual(self.call('poll', self.b)['user']['coins'], 0)

    def test_logout_revokes_session(self):
        self.call('logout', self.a)
        with self.assertRaises(GameError):
            self.call('poll', self.a)
        token = self.call('login', name='Alice', password='Alice-pass-123')['token']
        self.assertEqual(self.call('poll', token)['user']['name'], 'Alice')

    def test_public_snapshot_cannot_mutate_server(self):
        self.battle()
        with self.app.lock:
            uid = self.app.game.identify(self.a)
            s = self.app.game.snapshot(uid)
            s['room']['players'][0]['hp'] = 999999
            self.assertEqual(self.app.game.room(uid)['players'][0]['hp'], 200)


if __name__ == '__main__':
    unittest.main()
