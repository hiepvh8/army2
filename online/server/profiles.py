"""Validation and durable, revision-checked storage of the original portable save.

The archive remains client-authored data; validating its container does not make
its character stats or economy authoritative for online competition.
"""
import hashlib
import struct
import zlib

from .game import GameError, require

MAX_PROFILE_BYTES = 0x300000
MAX_MAIN_BYTES = 0x200000
MAX_EXTRA_BYTES = 65536
MAX_REVISION = 0x7fffffff


class ProfileConflict(GameError):
    pass


def validate_profile(data):
    """Check the AVP1 envelope against PortableSave2321.importBytes limits."""
    require(isinstance(data, bytes) and 31 <= len(data) <= MAX_PROFILE_BYTES,
            'Ban luu khong hop le hoac qua lon.')
    require(data[:4] == b'AVP1' and data[4] in (1, 2), 'Sai dinh dang ban luu goc.')
    checksum = struct.unpack_from('>I', data, len(data) - 4)[0]
    require(zlib.adler32(data[:-4]) & 0xffffffff == checksum, 'Ban luu bi hong.')
    # ID and main archive length follow the version byte. The game validates
    # the internal save and player ID itself when importing it.
    size = struct.unpack_from('>i', data, 9)[0]
    end = len(data) - 4
    require(8 <= size <= MAX_MAIN_BYTES and 13 + size < end,
            'Du lieu nhan vat khong hop le.')
    pos = 13 + size
    count = data[pos]
    pos += 1
    require(2 <= count <= 16, 'Thieu du lieu mo rong.')
    for _ in range(count):
        require(pos + 4 <= end, 'Ban luu bi thieu du lieu.')
        length = struct.unpack_from('>i', data, pos)[0]
        pos += 4
        require(-1 <= length <= MAX_EXTRA_BYTES, 'Du lieu mo rong khong hop le.')
        if length >= 0:
            require(pos + length <= end, 'Ban luu bi thieu du lieu.')
            pos += length
    require(pos == end, 'Ban luu co du lieu thua.')


class Profiles:
    def __init__(self, game):
        self.game = game
        self.db = game.db
        with self.db:
            self.db.execute('''CREATE TABLE IF NOT EXISTS original_profiles (
                uid INTEGER PRIMARY KEY REFERENCES users(id),
                revision INTEGER NOT NULL CHECK(revision > 0),
                data BLOB NOT NULL, sha256 TEXT NOT NULL,
                updated REAL NOT NULL)''')

    def load(self, uid):
        row = self.db.execute('SELECT revision,data,sha256 FROM original_profiles WHERE uid=?',
                              (uid,)).fetchone()
        if row is None:
            return 0, None
        data = bytes(row['data'])
        require(hashlib.sha256(data).hexdigest() == row['sha256'], 'Ban luu tren may chu bi hong.')
        return row['revision'], data

    def save(self, uid, expected, data):
        require(isinstance(expected, int) and 0 <= expected <= MAX_REVISION,
                'Phien ban ban luu khong hop le.')
        validate_profile(data)
        digest = hashlib.sha256(data).hexdigest()
        with self.db:
            row = self.db.execute('SELECT revision,sha256 FROM original_profiles WHERE uid=?',
                                  (uid,)).fetchone()
            revision = row['revision'] if row else 0
            # A retried upload after losing the success response is harmless.
            if row and row['sha256'] == digest:
                return revision
            if expected != revision:
                raise ProfileConflict('Ban luu da thay doi tren thiet bi khac.')
            require(revision < MAX_REVISION, 'Khong the cap nhat phien ban ban luu.')
            revision += 1
            self.db.execute('''INSERT INTO original_profiles(uid,revision,data,sha256,updated)
                VALUES(?,?,?,?,?) ON CONFLICT(uid) DO UPDATE SET
                revision=excluded.revision,data=excluded.data,sha256=excluded.sha256,
                updated=excluded.updated''', (uid, revision, data, digest, self.game.clock()))
        return revision
