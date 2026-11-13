"""Single-process EC2 test deployment: Python stdlib HTTP + SQLite, behind TLS proxy."""
import argparse
import collections
import json
import logging
import os
from pathlib import Path
import signal
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs

from .game import Game, GameError, require
from .wire import encode

LOG = logging.getLogger('army2')


class Application:
    def __init__(self, db):
        self.game = Game(db)
        self.lock = threading.RLock()
        self.buckets = {}

    def rate_limit(self, identity, limit):
        now = time.monotonic()
        bucket = self.buckets.setdefault(identity, collections.deque())
        while bucket and bucket[0] < now - 60:
            bucket.popleft()
        require(len(bucket) < limit, 'Qua nhieu yeu cau. Hay cho mot phut.')
        bucket.append(now)
        if len(self.buckets) > 4096:
            self.buckets = {k: v for k, v in self.buckets.items() if v and v[-1] > now - 60}

    def handle(self, data, token, address):
        with self.lock:
            self.game.tick()
            self.rate_limit(('request', address), 1200)
            op = data.get('op')
            issued = ''
            if op in ('login', 'register'):
                self.rate_limit(('auth', address), 20)
                uid, issued = self.game.auth(op, data.get('name'), data.get('password'))
            else:
                uid = self.game.identify(token)
                self.rate_limit(('user', uid), 180)
                if op == 'logout':
                    if uid in self.game.membership:
                        self.game.leave(uid)
                    import hashlib
                    with self.game.db:
                        self.game.db.execute('DELETE FROM sessions WHERE token=?',
                                             (hashlib.sha256(token.encode()).hexdigest(),))
                else:
                    self.game.action(uid, op, data)
            return self.game.snapshot(uid), issued


def make_server(host, port, app, require_tls=False):
    class Handler(BaseHTTPRequestHandler):
        server_version = 'Army2Online/1'

        def setup(self):
            super().setup()
            self.connection.settimeout(10)

        def log_message(self, fmt, *args):
            # Do not log request bodies, URLs, passwords or authorization headers.
            pass

        def reply(self, status, body, kind):
            self.send_response(status)
            self.send_header('Content-Type', kind)
            self.send_header('Content-Length', str(len(body)))
            self.send_header('Cache-Control', 'no-store')
            self.send_header('X-Content-Type-Options', 'nosniff')
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):
            if self.path == '/health':
                self.reply(200, b'{"status":"ok","protocol":1}', 'application/json')
            else:
                self.reply(404, b'Not found', 'text/plain')

        def do_POST(self):
            binary = self.path == '/jme'
            if self.path not in ('/jme', '/api'):
                self.reply(404, b'Not found', 'text/plain')
                return
            try:
                require(not require_tls or (self.client_address[0] in ('127.0.0.1', '::1') and
                        self.headers.get('X-Forwarded-Proto') == 'https'), 'HTTPS bat buoc.')
                length = int(self.headers.get('Content-Length', '0'))
                require(0 < length <= 4096, 'Yeu cau qua lon/rong.')
                raw = self.rfile.read(length)
                require(len(raw) == length, 'Yeu cau khong day du.')
                if binary:
                    values = parse_qs(raw.decode('utf-8'), max_num_fields=16, keep_blank_values=True)
                    require(all(len(v) == 1 for v in values.values()), 'Tham so trung.')
                    data = {k: v[0] for k, v in values.items()}
                else:
                    data = json.loads(raw)
                require(isinstance(data, dict), 'Yeu cau khong hop le.')
                auth = self.headers.get('Authorization', '')
                token = auth[7:] if auth.startswith('Bearer ') else ''
                snapshot, issued = app.handle(data, token, self.client_address[0])
                body = encode(snapshot, issued) if binary else json.dumps(dict(**snapshot, token=issued)).encode()
                self.reply(200, body, 'application/octet-stream' if binary else 'application/json')
            except (GameError, ValueError, TypeError, UnicodeError) as exc:
                message = str(exc) if isinstance(exc, GameError) else 'Yeu cau khong hop le.'
                body = encode(error=message) if binary else json.dumps({'error': message}).encode()
                self.reply(200 if binary else 400, body, 'application/octet-stream' if binary else 'application/json')
            except Exception:
                LOG.exception('Request failed')
                body = encode(error='Loi server. Vui long thu lai.') if binary else b'{"error":"server_error"}'
                self.reply(500, body, 'application/octet-stream' if binary else 'application/json')

    class Server(ThreadingHTTPServer):
        daemon_threads = True
        request_queue_size = 32

    return Server((host, port), Handler)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--host', default=os.getenv('ARMY_HOST', '127.0.0.1'))
    parser.add_argument('--port', type=int, default=int(os.getenv('ARMY_PORT', '8080')))
    parser.add_argument('--db', default=os.getenv('ARMY_DB', 'data/army2.sqlite3'))
    args = parser.parse_args()
    Path(args.db).parent.mkdir(parents=True, exist_ok=True)
    logging.basicConfig(level=logging.INFO)
    app = Application(args.db)
    server = make_server(args.host, args.port, app, os.getenv('ARMY_REQUIRE_TLS', '0') == '1')
    stopped = threading.Event()
    def clock_loop():
        while not stopped.wait(1):
            try:
                with app.lock:
                    app.game.tick()
            except Exception:
                LOG.exception('Game tick failed')
    threading.Thread(target=clock_loop, daemon=True).start()
    def stop(signum, frame):
        stopped.set()
        threading.Thread(target=server.shutdown, daemon=True).start()
    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    LOG.info('Listening on %s:%s', args.host, server.server_port)
    try:
        server.serve_forever()
    finally:
        stopped.set()
        server.server_close()
        app.game.db.close()


if __name__ == '__main__':
    main()
