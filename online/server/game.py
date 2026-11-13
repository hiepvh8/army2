"""Authoritative turn-based online arena. No dependency on client-supplied stats."""
import hashlib
import copy
import hmac
import math
import re
import secrets
import sqlite3
import time
from datetime import datetime, timedelta, timezone


class GameError(Exception):
    pass


def require(condition, message):
    if not condition:
        raise GameError(message)


def trajectory(x, angle, power, targets):
    """Online v1 physics; deliberately separate from unported offline weapons."""
    a = math.radians(angle)
    vx, vy = math.cos(a) * power * .19, -math.sin(a) * power * .19
    y = 232.0
    path = [[round(x), round(y)]]
    hit = None
    for i in range(240):
        x, y, vy = x + vx, y + vy, vy + .4
        path.append([round(x), round(y)])
        if i > 2:
            hit = next((t for t in targets if t['hp'] > 0 and
                        abs(x - t['x']) <= t.get('radius', 15) and
                        256 - t.get('height', 30) <= y <= 258), None)
        if hit or y >= 256 or x < -30 or x > 750:
            break
    return path, hit


class Game:
    def __init__(self, db_path, clock=time.time, event_goal=2):
        self.clock = clock
        self.event_goal = event_goal
        self.db = sqlite3.connect(db_path, check_same_thread=False)
        self.db.row_factory = sqlite3.Row
        self.db.executescript('''
        PRAGMA journal_mode=WAL;
        PRAGMA foreign_keys=ON;
        CREATE TABLE IF NOT EXISTS users (
          id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE COLLATE NOCASE,
          salt TEXT NOT NULL, password TEXT NOT NULL,
          coins INTEGER NOT NULL DEFAULT 0, wins INTEGER NOT NULL DEFAULT 0);
        CREATE TABLE IF NOT EXISTS sessions (
          token TEXT PRIMARY KEY, uid INTEGER NOT NULL REFERENCES users(id), expires REAL NOT NULL);
        CREATE TABLE IF NOT EXISTS matches (
          id TEXT PRIMARY KEY, mode TEXT NOT NULL, outcome TEXT NOT NULL, ended REAL NOT NULL);
        CREATE TABLE IF NOT EXISTS rewards (
          match_id TEXT NOT NULL REFERENCES matches(id), uid INTEGER NOT NULL REFERENCES users(id),
          coins INTEGER NOT NULL, PRIMARY KEY(match_id,uid));
        CREATE TABLE IF NOT EXISTS event_progress (
          event TEXT NOT NULL, uid INTEGER NOT NULL REFERENCES users(id),
          progress INTEGER NOT NULL DEFAULT 0, claimed INTEGER NOT NULL DEFAULT 0,
          PRIMARY KEY(event,uid));
        ''')
        self.rooms = {}
        self.membership = {}
        self.next_room = 1

    def auth(self, op, name, password):
        require(isinstance(name, str) and re.fullmatch(r'[A-Za-z][A-Za-z0-9_]{2,15}', name),
                'Ten 3-16 ky tu: chu, so, _.')
        require(isinstance(password, str) and 10 <= len(password) <= 128,
                'Mat khau can 10-128 ky tu.')
        row = self.db.execute('SELECT * FROM users WHERE name=?', (name,)).fetchone()
        if op == 'register':
            require(row is None, 'Ten da duoc su dung.')
            salt = secrets.token_hex(16)
            digest = hashlib.pbkdf2_hmac('sha256', password.encode(), bytes.fromhex(salt), 200000).hex()
            with self.db:
                self.db.execute('INSERT INTO users(name,salt,password) VALUES(?,?,?)', (name, salt, digest))
            row = self.db.execute('SELECT * FROM users WHERE name=?', (name,)).fetchone()
        else:
            salt = row['salt'] if row else '00' * 16
            digest = hashlib.pbkdf2_hmac('sha256', password.encode(), bytes.fromhex(salt), 200000).hex()
            require(row is not None and hmac.compare_digest(digest, row['password']), 'Sai tai khoan/mat khau.')
        token = secrets.token_urlsafe(32)
        with self.db:
            self.db.execute('DELETE FROM sessions WHERE expires<?', (self.clock(),))
            self.db.execute('INSERT INTO sessions VALUES(?,?,?)',
                            (hashlib.sha256(token.encode()).hexdigest(), row['id'], self.clock() + 7 * 86400))
        return row['id'], token

    def identify(self, token):
        require(isinstance(token, str) and len(token) <= 128, 'Can dang nhap.')
        row = self.db.execute('SELECT uid FROM sessions WHERE token=? AND expires>?',
                              (hashlib.sha256(token.encode()).hexdigest(), self.clock())).fetchone()
        require(row is not None, 'Phien het han. Hay dang nhap lai.')
        return row['uid']

    def event_id(self):
        # One shared calendar day in the user's timezone, never the device clock.
        return 'boss-' + datetime.fromtimestamp(self.clock(), timezone(timedelta(hours=7))).strftime('%Y-%m-%d')

    def player(self, uid):
        row = self.db.execute('SELECT id,name,coins,wins FROM users WHERE id=?', (uid,)).fetchone()
        return dict(row)

    def event(self, uid):
        row = self.db.execute('SELECT progress,claimed FROM event_progress WHERE event=? AND uid=?',
                              (self.event_id(), uid)).fetchone()
        return dict(id=self.event_id(), title='Ha boss cung dong doi', goal=self.event_goal,
                    progress=row['progress'] if row else 0, claimed=bool(row and row['claimed']), active=True)

    def snapshot(self, uid):
        room = self.rooms.get(self.membership.get(uid))
        if room:
            room['seen'][uid] = self.clock()
        public = None
        if room:
            public = {k: room[k] for k in ('id', 'mode', 'state', 'owner', 'seq', 'players',
                                          'boss_hp', 'boss_max', 'result', 'path', 'shot_id', 'log')}
            public['turn'] = self.turn(room) if room['state'] == 'battle' else 0
            public['seconds'] = max(0, int(room['deadline'] - self.clock())) if room['state'] == 'battle' else 0
        return dict(user=self.player(uid), event=self.event(uid), room=copy.deepcopy(public),
                    rooms=[dict(id=r['id'], name=r['name'], mode=r['mode'], count=len(r['players']),
                                capacity=2 if r['mode'] == 'pvp' else 4, state=r['state'])
                           for r in self.rooms.values() if r['state'] == 'lobby'])

    @staticmethod
    def turn(room):
        return room['players'][room['index']]['id']

    def room(self, uid):
        r = self.rooms.get(self.membership.get(uid))
        require(r is not None, 'Ban chua o trong phong.')
        return r

    def action(self, uid, op, data):
        if op == 'poll':
            return
        if op == 'claim':
            e = self.event(uid)
            require(e['progress'] >= e['goal'] and not e['claimed'], 'Chua du dieu kien hoac da nhan thuong.')
            with self.db:
                cur = self.db.execute('UPDATE event_progress SET claimed=1 WHERE event=? AND uid=? AND claimed=0',
                                      (e['id'], uid))
                if cur.rowcount:
                    self.db.execute('UPDATE users SET coins=coins+200 WHERE id=?', (uid,))
            return
        if op == 'create':
            require(uid not in self.membership, 'Hay roi phong cu truoc.')
            require(len(self.rooms) < 64, 'Server da day phong.')
            mode = data.get('mode')
            require(mode in ('pvp', 'boss'), 'Che do khong hop le.')
            rid = self.next_room
            self.next_room += 1
            r = dict(id=rid, match=secrets.token_hex(16), mode=mode, state='lobby', name=self.player(uid)['name'],
                     owner=uid, seq=0, index=0, players=[], boss_hp=0, boss_max=0, result='',
                     path=[], shot_id=0, log='Cho dong doi vao phong.', deadline=0, moved=False,
                     seen={}, created=self.clock(), ended=0)
            self.rooms[rid] = r
            self.join(uid, r)
            return
        if op == 'join':
            rid = self.integer(data, 'room', 1, 2147483647)
            require(rid in self.rooms, 'Phong khong ton tai.')
            self.join(uid, self.rooms[rid])
            return
        r = self.room(uid)
        r['seen'][uid] = self.clock()
        p = next(p for p in r['players'] if p['id'] == uid)
        if op == 'leave':
            self.leave(uid)
        elif op == 'ready':
            require(r['state'] == 'lobby', 'Tran da bat dau.')
            p['ready'] = not p['ready']
        elif op == 'start':
            require(uid == r['owner'] and r['state'] == 'lobby', 'Chi chu phong duoc bat dau.')
            require(len(r['players']) >= 2 and all(p['ready'] for p in r['players']),
                    'Can it nhat 2 nguoi; tat ca phai san sang.')
            r.update(state='battle', seq=1, deadline=self.clock() + 30, log='Tran dau bat dau!')
            if r['mode'] == 'boss':
                r['boss_hp'] = r['boss_max'] = 280 + 100 * len(r['players'])
            for i, member in enumerate(r['players']):
                member.update(x=(120 if i == 0 else 600) if r['mode'] == 'pvp' else 70 + i * 70,
                              hp=200, max_hp=200)
        elif op in ('move', 'fire', 'pass'):
            require(r['state'] == 'battle' and self.turn(r) == uid, 'Chua den luot cua ban.')
            require(self.integer(data, 'seq', 1, 100000) == r['seq'], 'Lenh cu/trung. Dang tai lai trang thai.')
            if op == 'move':
                require(not r['moved'], 'Moi luot chi di chuyen mot lan.')
                dx = self.integer(data, 'dx', -30, 30)
                x = p['x'] + dx
                require(25 <= x <= (400 if r['mode'] == 'boss' else 695), 'Ngoai ban do.')
                require(all(q['id'] == uid or q['hp'] <= 0 or abs(x-q['x']) >= 35 for q in r['players']),
                        'Vi tri dang co nguoi.')
                p['x'] = x
                r['moved'] = True
                r['seq'] += 1
            elif op == 'pass':
                self.advance(r)
            else:
                angle = self.integer(data, 'angle', 5, 175)
                power = self.integer(data, 'power', 10, 100)
                self.fire(r, p, angle, power)
        else:
            raise GameError('Lenh khong duoc ho tro.')

    @staticmethod
    def integer(data, key, minimum, maximum):
        try:
            value = data[key]
            require(not isinstance(value, bool) and str(value).lstrip('-').isdigit(), 'Tham so khong hop le: ' + key)
            n = int(value)
        except (KeyError, ValueError, TypeError):
            raise GameError('Thieu/sai tham so: ' + key)
        require(minimum <= n <= maximum, 'Tham so ngoai gioi han: ' + key)
        return n

    def join(self, uid, r):
        require(uid not in self.membership, 'Ban da o trong phong.')
        require(r['state'] == 'lobby' and len(r['players']) < (2 if r['mode'] == 'pvp' else 4), 'Phong da day/dang dau.')
        r['players'].append(dict(id=uid, name=self.player(uid)['name'], ready=False, x=0, hp=200, max_hp=200))
        r['seen'][uid] = self.clock()
        self.membership[uid] = r['id']

    def fire(self, r, p, angle, power):
        boss = dict(id=0, x=600, hp=r['boss_hp'], radius=32, height=64)
        targets = [q for q in r['players'] if q['id'] != p['id']] if r['mode'] == 'pvp' else [boss]
        path, direct = trajectory(p['x'], angle, power, targets)
        r['path'], r['shot_id'] = path, r['shot_id'] + 1
        damage = 0
        for target in targets:
            distance = abs(target['x'] - path[-1][0])
            hit = target is direct
            if target['hp'] > 0 and (hit or (distance < 60 and path[-1][1] >= 205)):
                amount = 85 if hit else max(10, int(85 * (1 - distance/60)))
                target['hp'] = max(0, target['hp'] - amount)
                damage += amount
        if r['mode'] == 'boss':
            r['boss_hp'] = boss['hp']
        r['log'] = p['name'] + ' ban: ' + str(damage) + ' sat thuong.'
        if r['mode'] == 'boss' and r['boss_hp'] <= 0:
            self.finish(r, 'win', [q['id'] for q in r['players'] if self.membership.get(q['id']) == r['id']])
        elif r['mode'] == 'pvp' and len([q for q in r['players'] if q['hp'] > 0]) <= 1:
            self.finish(r, 'win', [q['id'] for q in r['players'] if q['hp'] > 0])
        else:
            self.advance(r)

    def advance(self, r):
        if r['state'] != 'battle':
            return
        old = r['index']
        for step in range(1, len(r['players']) + 1):
            idx = (old + step) % len(r['players'])
            if r['players'][idx]['hp'] > 0:
                if r['mode'] == 'boss' and old + step >= len(r['players']):
                    victim = next(q for q in r['players'][idx:] + r['players'][:idx] if q['hp'] > 0)
                    victim['hp'] = max(0, victim['hp'] - 45)
                    r['log'] += ' Boss danh ' + victim['name'] + ': 45 HP.'
                    if not any(q['hp'] > 0 for q in r['players']):
                        self.finish(r, 'defeat', [])
                        return
                    if r['players'][idx]['hp'] <= 0:
                        idx = next(i for i, q in enumerate(r['players']) if q['hp'] > 0)
                r.update(index=idx, seq=r['seq'] + 1, deadline=self.clock() + 30, moved=False)
                return
        self.finish(r, 'defeat', [])

    def finish(self, r, outcome, winners):
        if r['state'] != 'battle':
            return
        with self.db:
            self.db.execute('INSERT INTO matches VALUES(?,?,?,?)', (r['match'], r['mode'], outcome, self.clock()))
            for uid in winners:
                coins = 100 if r['mode'] == 'boss' else 50
                self.db.execute('INSERT INTO rewards VALUES(?,?,?)', (r['match'], uid, coins))
                self.db.execute('UPDATE users SET coins=coins+?,wins=wins+1 WHERE id=?', (coins, uid))
                if r['mode'] == 'boss':
                    self.db.execute('INSERT INTO event_progress(event,uid,progress) VALUES(?,?,1) '
                                    'ON CONFLICT(event,uid) DO UPDATE SET progress=progress+1', (self.event_id(), uid))
        names = [q['name'] for q in r['players'] if q['id'] in winners]
        r.update(state='finished', result=('Chien thang: ' + ', '.join(names)) if names else 'Tran dau ket thuc: ' + outcome,
                 ended=self.clock(), seq=r['seq'] + 1)

    def leave(self, uid):
        r = self.room(uid)
        if r['state'] == 'battle':
            if r['mode'] == 'pvp':
                # Forfeit gives no currency; prevents trivial leave/rejoin farming.
                self.finish(r, 'forfeit', [])
            else:
                next(p for p in r['players'] if p['id'] == uid)['hp'] = 0
                if not any(p['hp'] > 0 for p in r['players']):
                    self.finish(r, 'abandoned', [])
                elif self.turn(r) == uid:
                    self.advance(r)
                # Keep combat roster stable until the match ends.
                del self.membership[uid]
                return
        del self.membership[uid]
        r['players'] = [p for p in r['players'] if p['id'] != uid]
        r['seen'].pop(uid, None)
        if not r['players']:
            self.rooms.pop(r['id'], None)
        elif r['owner'] == uid:
            r['owner'] = r['players'][0]['id']

    def tick(self):
        now = self.clock()
        for r in list(self.rooms.values()):
            if r['state'] == 'battle':
                if all(now - r['seen'].get(p['id'], 0) > 90 for p in r['players'] if p['hp'] > 0):
                    self.finish(r, 'abandoned', [])
                elif now >= r['deadline']:
                    r['log'] = 'Het gio. Chuyen luot.'
                    self.advance(r)
            expiry = r['ended'] + 300 if r['state'] == 'finished' else r['created'] + 3600
            if r['state'] != 'battle' and now > expiry:
                self.rooms.pop(r['id'], None)
                for p in r['players']:
                    if self.membership.get(p['id']) == r['id']:
                        del self.membership[p['id']]
