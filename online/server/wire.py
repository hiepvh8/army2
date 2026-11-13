"""Binary response v1, read by Java ME DataInputStream. Strings are ASCII UTF."""
import io
import struct


def encode(snapshot=None, token='', error=''):
    out = io.BytesIO()
    def number(fmt, value):
        out.write(struct.pack('>' + fmt, value))
    def text(value):
        # All protocol text is server-owned ASCII in v1 (Java modified UTF compatible).
        b = str(value).encode('ascii', errors='replace')
        number('H', len(b))
        out.write(b)
    number('I', 0x41324f31)
    number('B', 1 if error else 0)
    text(error)
    if error:
        return out.getvalue()
    text(token)
    u, e = snapshot['user'], snapshot['event']
    number('i', u['id']); text(u['name']); number('i', u['coins']); number('i', u['wins'])
    text(e['id']); text(e['title']); number('i', e['goal']); number('i', e['progress'])
    number('?', e['claimed']); number('?', e['active'])
    number('H', len(snapshot['rooms']))
    for r in snapshot['rooms']:
        number('i', r['id']); text(r['name']); text(r['mode'])
        number('B', r['count']); number('B', r['capacity']); text(r['state'])
    r = snapshot['room']
    number('?', bool(r))
    if r:
        number('i', r['id']); text(r['mode']); text(r['state'])
        for key in ('owner', 'turn', 'seq', 'seconds', 'boss_hp', 'boss_max'):
            number('i', r[key])
        text(r['result']); number('B', len(r['players']))
        for p in r['players']:
            number('i', p['id']); text(p['name'])
            for key in ('x', 'hp', 'max_hp'):
                number('i', p[key])
            number('?', p['ready'])
        number('H', len(r['path']))
        for x, y in r['path']:
            number('h', x); number('h', y)
        number('i', r['shot_id']); text(r['log'])
    return out.getvalue()
