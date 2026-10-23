"""Read and write the gzipped NBT structure templates (DATA_VERSION 4903).

The reader returns plain Python values: compounds are dicts, lists are lists,
strings are str and numbers are int or float. Byte, int and long arrays come
back as lists of int. The writer takes the typed wrappers below so the tag
types round-trip exactly.
"""
import gzip
import io
import struct

DATA_VERSION = 4903


class Byte(int):
    pass


class Short(int):
    pass


class Long(int):
    pass


class Float(float):
    pass


class Double(float):
    pass


class IntArray(list):
    pass


class TagList(list):
    """A list whose element tag type is fixed; `kind` is one of the tag ids."""

    def __init__(self, kind, items=()):
        super().__init__(items)
        self.kind = kind


TAG_END, TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG, TAG_FLOAT, TAG_DOUBLE = 0, 1, 2, 3, 4, 5, 6
TAG_BYTE_ARRAY, TAG_STRING, TAG_LIST, TAG_COMPOUND, TAG_INT_ARRAY, TAG_LONG_ARRAY = 7, 8, 9, 10, 11, 12


def _read(f, t, typed=False):
    if t == TAG_BYTE:
        v = struct.unpack('>b', f.read(1))[0]
        return Byte(v) if typed else v
    if t == TAG_SHORT:
        v = struct.unpack('>h', f.read(2))[0]
        return Short(v) if typed else v
    if t == TAG_INT:
        return struct.unpack('>i', f.read(4))[0]
    if t == TAG_LONG:
        v = struct.unpack('>q', f.read(8))[0]
        return Long(v) if typed else v
    if t == TAG_FLOAT:
        v = struct.unpack('>f', f.read(4))[0]
        return Float(v) if typed else v
    if t == TAG_DOUBLE:
        v = struct.unpack('>d', f.read(8))[0]
        return Double(v) if typed else v
    if t == TAG_BYTE_ARRAY:
        n = struct.unpack('>i', f.read(4))[0]
        return list(f.read(n))
    if t == TAG_STRING:
        n = struct.unpack('>H', f.read(2))[0]
        return f.read(n).decode('utf-8')
    if t == TAG_LIST:
        et = f.read(1)[0]
        n = struct.unpack('>i', f.read(4))[0]
        items = [_read(f, et, typed) for _ in range(n)]
        return TagList(et, items) if typed else items
    if t == TAG_COMPOUND:
        d = {}
        while True:
            tt = f.read(1)[0]
            if tt == TAG_END:
                return d
            nl = struct.unpack('>H', f.read(2))[0]
            name = f.read(nl).decode('utf-8')
            d[name] = _read(f, tt, typed)
    if t == TAG_INT_ARRAY:
        n = struct.unpack('>i', f.read(4))[0]
        v = list(struct.unpack('>%di' % n, f.read(4 * n)))
        return IntArray(v) if typed else v
    if t == TAG_LONG_ARRAY:
        n = struct.unpack('>i', f.read(4))[0]
        return list(struct.unpack('>%dq' % n, f.read(8 * n)))
    raise ValueError('unknown tag %d' % t)


def load(path, typed=False):
    """Load a gzipped NBT file and return its root compound as a dict. typed keeps the tag types
    (Byte, Short, Long, Float, Double, IntArray, TagList) so the result writes back unchanged."""
    with gzip.open(path, 'rb') as g:
        f = io.BytesIO(g.read())
    t = f.read(1)[0]
    nl = struct.unpack('>H', f.read(2))[0]
    f.read(nl)
    return _read(f, t, typed)


def _tag_of(v):
    if isinstance(v, bool):
        return TAG_BYTE
    if isinstance(v, Byte):
        return TAG_BYTE
    if isinstance(v, Short):
        return TAG_SHORT
    if isinstance(v, Long):
        return TAG_LONG
    if isinstance(v, Float):
        return TAG_FLOAT
    if isinstance(v, (Double, float)):
        return TAG_DOUBLE
    if isinstance(v, int):
        return TAG_INT
    if isinstance(v, str):
        return TAG_STRING
    if isinstance(v, IntArray):
        return TAG_INT_ARRAY
    if isinstance(v, (TagList, list, tuple)):
        return TAG_LIST
    if isinstance(v, dict):
        return TAG_COMPOUND
    raise TypeError('no NBT tag for %r' % (v,))


def _write(f, t, v):
    if t == TAG_BYTE:
        f.write(struct.pack('>b', int(v)))
    elif t == TAG_SHORT:
        f.write(struct.pack('>h', v))
    elif t == TAG_INT:
        f.write(struct.pack('>i', v))
    elif t == TAG_LONG:
        f.write(struct.pack('>q', v))
    elif t == TAG_FLOAT:
        f.write(struct.pack('>f', v))
    elif t == TAG_DOUBLE:
        f.write(struct.pack('>d', v))
    elif t == TAG_STRING:
        b = v.encode('utf-8')
        f.write(struct.pack('>H', len(b)))
        f.write(b)
    elif t == TAG_INT_ARRAY:
        f.write(struct.pack('>i', len(v)))
        f.write(struct.pack('>%di' % len(v), *v))
    elif t == TAG_LIST:
        if isinstance(v, TagList):
            et = v.kind
        elif len(v) == 0:
            et = TAG_END
        else:
            et = _tag_of(v[0])
        f.write(bytes([et]))
        f.write(struct.pack('>i', len(v)))
        for item in v:
            _write(f, et, item)
    elif t == TAG_COMPOUND:
        for k, item in v.items():
            it = _tag_of(item)
            f.write(bytes([it]))
            kb = k.encode('utf-8')
            f.write(struct.pack('>H', len(kb)))
            f.write(kb)
            _write(f, it, item)
        f.write(bytes([TAG_END]))
    else:
        raise ValueError('cannot write tag %d' % t)


def dump(root, path):
    """Write `root` (a dict) as a gzipped NBT file. mtime is fixed so output is deterministic."""
    f = io.BytesIO()
    f.write(bytes([TAG_COMPOUND]))
    f.write(struct.pack('>H', 0))
    _write(f, TAG_COMPOUND, root)
    with open(path, 'wb') as raw:
        with gzip.GzipFile(filename='', mode='wb', fileobj=raw, mtime=0) as g:
            g.write(f.getvalue())
