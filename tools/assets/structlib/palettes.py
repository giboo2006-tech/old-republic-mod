"""Material palettes: named roles with weighted block choices.

    from structlib.palettes import Palette
    jedi = Palette(
        wall=[('or:ruin_stone', 6), ('or:ruin_stone_cracked', 2), ('or:ruin_stone_mossy', 2)],
        trim='or:ruin_stone_carved',
        floor=[('smooth_stone', 3), ('polished_andesite', 1)],
    )
    t.fill(0, 1, 0, 10, 5, 0, jedi.picker('wall', t.rng))      # mixed per cell, repeatable
    t.fill(0, 0, 0, 10, 0, 10, jedi.patches('floor', seed=4))    # mixed in noise patches
    jedi['trim']                                                  # the role's main block

A role's value is one state or a list of (state, weight). Each family's builder module keeps its own
palettes next to its code; the shared ones below cover the materials several families use.
"""
from . import blocks as B
from .nature import Noise


class Palette:
    def __init__(self, **roles):
        self.roles = {}
        for k, v in roles.items():
            self.roles[k] = _norm(v)

    def __getitem__(self, role):
        return self.roles[role][0][0]

    def __contains__(self, role):
        return role in self.roles

    def options(self, role):
        return list(self.roles[role])

    def pick(self, role, rng):
        opts = self.roles[role]
        if len(opts) == 1:
            return opts[0][0]
        total = sum(w for _, w in opts)
        r = rng.random() * total
        for s, w in opts:
            r -= w
            if r <= 0:
                return s
        return opts[-1][0]

    def picker(self, role, rng):
        """A callable f(x, y, z) -> state for fill(), choosing per cell."""
        return lambda x, y, z: self.pick(role, rng)

    def patches(self, role, seed=0, scale=4.0):
        """A callable that chooses by coherent noise, so variants clump into patches."""
        opts = self.roles[role]
        total = sum(w for _, w in opts)
        noise = Noise(seed, scale)
        jitter = Noise(seed + 977, 1.3)

        def f(x, y, z):
            v = 0.75 * noise.at(x, y, z) + 0.25 * jitter.at(x, y, z)
            r = v * total
            for s, w in opts:
                r -= w
                if r <= 0:
                    return s
            return opts[-1][0]
        return f

    def with_roles(self, **roles):
        """A copy with some roles replaced or added."""
        p = Palette()
        p.roles = dict(self.roles)
        for k, v in roles.items():
            p.roles[k] = _norm(v)
        return p


def _canon(s):
    return B.build(*B.parse(s))


def _norm(v):
    if isinstance(v, str):
        return [(_canon(v), 1)]
    out = []
    for item in v:
        if isinstance(item, str):
            out.append((_canon(item), 1))
        else:
            s, w = item
            out.append((_canon(s), w))
    return out
