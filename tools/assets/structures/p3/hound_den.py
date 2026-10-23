"""varkhast/hound_den: an ash hound lair in a cave mouth (worldgen; MILESTONE_6 5.5).

A knuckle of black basalt and tuff stands out of the ash plain, its columns cracked into spires. A pack of
ash hounds has taken the cave in its foot (site_spawner `ash_hounds`): the floor inside is trodden into
hollows of ash, bones lie everywhere, and a seam of embers in the back of the cave keeps it warm.

Variants (start pool, equal weights):
  mouth   a low wide mouth in the south face of the rock; a supply runner's speeder lies wrecked in front
          of it, its crates burst open on the ash, the runner a little way off.
  cleft   the rock is split by a narrow cleft that runs in from the east to the den; a fallen spire bridges
          the cleft, and the den has a hole in its roof where the light falls on the bones.

Run:  python3 tools/assets/structures/p3/hound_den.py [--preview DIR] [--only NAME]
"""
import math

import vk
from vk import S, AIR, Template, mod, Mix, Field, stair, slab, STEP, OPP

NAME = 'hound_den'
VARIANTS = ('mouth', 'cleft')
SIZE = (29, 22, 29)
BURIED = 3
G = 2           # the ground layer
F = 3           # feet on the ground and on the den floor
SEED = 960254
SALT = 960254

DEN = (14, 10)          # middle of the den chamber (x, z)
ROCK_TOP = F + 13


def build(preview=None, only=None):
    out = []
    for v in VARIANTS:
        if only and v != only:
            continue
        out.append(build_variant(v, preview))
    vk.worldgen(NAME, [(v, 1, SIZE[1]) for v in VARIANTS], ['ashfall'], spacing=40, separation=14, salt=SALT,
                buried=BURIED, support=10, ramp=2, no_monsters=False)
    vk.loot_table('chests/varkhast/hound_den', [
        vk.item('oldrepublic:credit_chit', 2, 5),
        vk.item('oldrepublic:medpac', 1, 2),
        vk.item('minecraft:bread', 1, 3),
        vk.item('oldrepublic:durasteel_plate', 1, 2, chance=0.5),
        vk.item('minecraft:bone', 2, 5),
        vk.item('oldrepublic:hyperfuel_cell', 1, 1, chance=0.3),
    ])
    return out


def build_variant(v, preview=None):
    tid = 'varkhast/%s/%s' % (NAME, v)
    t = Template(*SIZE, seed=SEED + VARIANTS.index(v) * 41, name=tid)
    rng = t.rng
    cave = rock(t, rng, v)
    den(t, rng, v, cave)
    if v == 'mouth':
        runner(t, rng)
    else:
        cleft_props(t, rng)
    weather(t, rng, v, cave)
    cx, cz = DEN
    mod.light_fill(t, (cx - 8, F, cz - 6, cx + 8, F + 6, cz + 5), level=8, strength=11)
    if v == 'mouth':
        mod.light_fill(t, (cx - 5, F, cz + 6, cx + 5, F + 4, cz + 9), level=8, strength=11)
    t.finish()
    goals = [(DEN[0], F, DEN[1]), (DEN[0] - 4, F, DEN[1]), (DEN[0] + 4, F, DEN[1] - 1)]
    goals += [(14, F, DEN[1] + 8)] if v == 'mouth' else [(22, F, DEN[1] + 1), (19, F, DEN[1] + 1)]
    vk.report(t, tid, ground_y=G, walk=((14, F, 27), goals, True))
    t.save(tid)
    if preview:
        vk.preview(t, '%s/%s_%s.png' % (preview, NAME, v), tid, plans=[F, F + 3])
        vk.preview(t, '%s/%s_%s_cut.png' % (preview, NAME, v), tid + ' (cut)', views=('se', 'sw'),
                   plans=[], cut=[F + 4])
    return t


# ---------------------------------------------------------------- the rock and the cave

def cave_ceiling(x, z, v, n):
    """The cave's ceiling (last air y) at (x, z), or None outside the cave."""
    cx, cz = DEN
    if v == 'cleft' and x >= cx + 4 and abs(z - cz - (x - cx - 4) * 0.12) <= 1.3 + (n(x, 5, z) - 0.5) * 0.6:
        # the cleft comes in from the east: open to the sky, three wide
        return SIZE[1] - 1
    # the den: a low dome
    d = math.hypot((x - cx) / 7.0, (z - cz) / 4.8) + (n(x, 0, z) - 0.5) * 0.25
    if d < 1.0:
        return F + 2 + int(round(3.2 * (1.0 - d * d)))
    if v == 'mouth':
        # the mouth runs south out of the rock, widest and lowest at the face
        if cz + 4 <= z <= cz + 12:
            half = 2.5 + (z - cz - 4) * 0.3 + (n(x, 3, z) - 0.5) * 0.8
            if abs(x - cx) <= half:
                return F + 3 + (1 if abs(x - cx) < half - 1.5 else 0)
    return None


def rock_height(x, z, v, n, sp):
    """The top of the rock column at (x, z) (inclusive), or None for open ground."""
    cx, cz = DEN[0], DEN[1] + 1
    r = math.hypot((x - cx) / 12.5, (z - cz) / 9.5) + (n(x, 0, z) - 0.5) * 0.35
    if r >= 1.0:
        return None
    h = G + (ROCK_TOP - G) * min(1.0, (1.0 - r) * 1.9)
    # columns stand proud of the rock in places: spires
    h += (sp(x, 0, z) - 0.8) * 25.0 if sp(x, 0, z) > 0.8 else 0.0
    if v == 'mouth' and z > DEN[1] + 6:
        # the south face is a cliff over the mouth rather than a slope
        h = min(h, G + (ROCK_TOP - G) * (1.0 - (z - DEN[1] - 6) / 7.0) + 4)
    return int(round(h))


def rock(t, rng, v):
    sx, sy, sz = SIZE
    n = Field(SEED + 1, 4.0)
    sp = Field(SEED + 2, 1.6)
    cn = Field(SEED + 3, 3.0)
    mass = Mix([('minecraft:basalt[axis=y]', 5), ('minecraft:smooth_basalt', 2), ('minecraft:blackstone', 1),
                ('minecraft:tuff', 1)], SEED + 4, 5.0)
    capstone = Mix([('minecraft:tuff', 3), ('minecraft:smooth_basalt', 2), ('minecraft:light_gray_concrete_powder', 1)],
                   SEED + 5, 4.0)
    ground = Mix([('minecraft:coarse_dirt', 4), ('minecraft:tuff', 2), ('minecraft:gravel', 2),
                  ('minecraft:light_gray_concrete_powder', 1)], SEED + 6, 4.0)
    under = vk.under_mix('ash', SEED + 7)
    cave = {}
    for x in range(sx):
        for z in range(sz):
            for y in range(0, G):
                t.set(x, y, z, under(x, y, z))
            t.set(x, G, z, ground(x, G, z))
            top = rock_height(x, z, v, n, sp)
            if top is not None and top > G:
                for y in range(F, top + 1):
                    t.set(x, y, z, mass(x, y, z))
                if top >= F + 2 and sp(x, 0, z) <= 0.8:
                    t.set(x, top, z, capstone(x, top, z))
                lo = top + 1
            else:
                lo = F
            for y in range(lo, min(sy, max(lo, F + 4))):
                t.set(x, y, z, AIR)
            c = cave_ceiling(x, z, v, cn)
            if c is not None and top is not None:
                cave[(x, z)] = c
                for y in range(F, min(c, sy - 1) + 1):
                    t.set(x, y, z, AIR)
    # loose ash left over the cave's roof would fall in: it is packed to tuff there
    for (x, y, z), st in list(t.cells.items()):
        if vk.B.name_of(st) in vk.GRAVITY and y > 0 and (t.get(x, y - 1, z) is None or vk.is_air_state(t.get(x, y - 1, z))):
            t.set(x, y, z, 'minecraft:tuff')
    if v == 'cleft':
        # a hole in the den's roof
        for x in range(DEN[0] - 1, DEN[0] + 2):
            for z in range(DEN[1] - 2, DEN[1]):
                for y in range(F, sy):
                    if t.get(x, y, z) is not None:
                        t.set(x, y, z, AIR)
    return cave


# ---------------------------------------------------------------- the den

def den(t, rng, v, cave):
    cx, cz = DEN
    floor = Mix([('minecraft:light_gray_concrete_powder', 3), ('minecraft:gravel', 2), ('minecraft:tuff', 1),
                 ('minecraft:coarse_dirt', 1)], SEED + 20, 2.0)
    for (x, z) in cave:
        t.set(x, G, z, floor(x, G, z))
    # sleeping hollows: rings of ash scraped up round bare patches
    for (hx, hz) in ((cx - 4, cz - 1), (cx + 3, cz - 2), (cx - 1, cz + 2)):
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                x, z = hx + dx, hz + dz
                if (x, z) not in cave or not t.is_air(x, F, z):
                    continue
                r = abs(dx) + abs(dz)
                if r >= 3 and rng.random() < 0.7:
                    t.set(x, F, z, 'minecraft:light_gray_carpet' if rng.random() < 0.6 else
                          'minecraft:light_gray_concrete_powder')
                elif r <= 1:
                    t.set(x, G, z, 'minecraft:coarse_dirt')
    # the seam of embers along the back wall: red light under red glass
    path = vk.fissure_path(rng, (cx - 5, cz - 3), 11, 0.4, [(1, 0)])
    vk.ember_fissure(t, [(x, G, z) for (x, z) in path if (x, z) in cave and t.is_air(x, F, z)], SEED + 21, glass=True)
    # bones: what the pack brought home
    cells = sorted(cave)
    rng.shuffle(cells)
    k = 0
    for (x, z) in cells:
        if k >= 16:
            break
        if not t.is_air(x, F, z) or not t.is_solid(x, G, z):
            continue
        r = rng.random()
        if r < 0.35:
            t.set(x, F, z, vk.bone(rng.choice(('x', 'z'))))
        elif r < 0.6:
            t.set(x, F, z, vk.skull(rng.randrange(16)))
        elif r < 0.8:
            t.set(x, F, z, 'or:debris')
        else:
            t.set(x, F, z, 'minecraft:light_gray_concrete_powder')
        k += 1
    # a heap of bones in the far corner of the den
    for (x, z, h) in ((cx + 5, cz - 2, 2), (cx + 6, cz - 1, 1), (cx + 4, cz - 3, 1), (cx + 5, cz - 1, 1)):
        if (x, z) in cave:
            for y in range(F, F + h):
                if t.is_air(x, y, z):
                    t.set(x, y, z, vk.bone('y' if y > F else 'x'))
            if t.is_air(x, F + h, z):
                t.set(x, F + h, z, vk.skull(rng.randrange(16)))
    mod.spawner(t, cx, F, cz, 'ash_hounds')


def runner(t, rng):
    """A supply runner's speeder, wrecked in front of the den, and its cargo burst over the ash."""
    # the speeder: a scorched hull on its side, its vanes torn off
    for (x, z, s) in ((9, 23, 'or:wreck_plating_scorched'), (10, 23, 'or:wreck_plating_scorched'),
                      (11, 23, 'or:durasteel_plating'), (10, 24, 'or:wreck_salvage'), (8, 23, 'or:wreck_salvage'),
                      (12, 22, 'or:scorch_mark'), (9, 22, 'or:scorch_mark'), (11, 24, 'or:debris')):
        t.set(x, F, z, s)
    t.set(10, F + 1, 23, slab('or:durasteel_plating_slab'))
    t.set(9, F + 1, 23, 'minecraft:iron_bars')
    # the cargo: crates broken open, one still shut
    for (x, z, s) in ((16, 22, 'or:cargo_crate'), (17, 23, 'or:cargo_crate'), (16, 23, vk.barrel('east')),
                      (19, 21, 'or:debris'), (18, 24, 'minecraft:decorated_pot'), (20, 23, 'or:debris')):
        t.set(x, F, z, s)
    t.set(17, F + 1, 23, vk.trapdoor('minecraft:spruce_trapdoor', 'north', 'top', open_=True))
    t.chest(18, F, 22, facing='south', loot='chests/varkhast/hound_den')
    # the runner, who got as far as the rocks
    t.set(5, F, 19, vk.skull(5))
    t.set(5, F, 20, vk.bone('z'))
    t.set(6, F, 21, vk.lantern(False))
    t.set(4, F, 21, 'or:debris')


def place(t, x, z, s):
    if t.inside(x, F, z) and t.is_air(x, F, z) and t.is_solid(x, G, z):
        t.set(x, F, z, s)
        return True
    return False


def cleft_props(t, rng):
    """A spire has fallen across the cleft; a hunter's last camp on the ash south-east of the rock."""
    cx, cz = DEN
    # the fallen spire: a basalt beam across the cleft at head height and a half
    for z in range(cz - 2, cz + 5):
        x = cx + 8
        if t.is_air(x, F + 3, z):
            t.set(x, F + 3, z, 'minecraft:basalt[axis=z]')
            if t.is_air(x, F + 4, z) and z % 2:
                t.set(x, F + 4, z, 'minecraft:smooth_basalt')
    # the hunter's camp: a cold fire, a bedroll, the strongbox, and the hunter, who did not leave
    place(t, 22, 24, vk.campfire(lit=False))
    place(t, 24, 25, 'minecraft:brown_carpet')
    place(t, 25, 25, 'minecraft:brown_carpet')
    if place(t, 21, 25, 'minecraft:air'):
        t.chest(21, F, 25, facing='north', loot='chests/varkhast/hound_den')
    place(t, 19, 21, vk.skull(12))
    place(t, 20, 21, vk.bone('x'))
    place(t, 24, 22, 'or:debris')
    place(t, 23, 26, vk.barrel('up'))


def weather(t, rng, v, cave):
    ash = Mix([('minecraft:light_gray_concrete_powder', 3), ('minecraft:gravel', 1)], SEED + 40, 2.0)
    vk.drift(t, (0, G, 0, SIZE[0] - 1, G, SIZE[2] - 1), ash, wind='west', depth=2, reach=3, seed=SEED + 41,
             cap='minecraft:light_gray_carpet', cap_chance=0.5,
             floor={'minecraft:coarse_dirt', 'minecraft:tuff', 'minecraft:gravel', 'minecraft:light_gray_concrete_powder'})
    # dead bushes on the plain
    for _ in range(14):
        x, z = rng.randrange(SIZE[0]), rng.randrange(SIZE[2])
        if (x, z) not in cave and t.is_air(x, F, z) and vk.B.name_of(t.get(x, G, z)) in (
                'minecraft:coarse_dirt', 'minecraft:gravel'):
            t.set(x, F, z, 'minecraft:dead_bush')


if __name__ == '__main__':
    a = vk.args()
    build(a.preview, a.only)
