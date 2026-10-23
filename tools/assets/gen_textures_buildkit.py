#!/usr/bin/env python3
"""Generates the build kit of the structures overhaul (docs/MILESTONE_6.md section 4): 64 plain
building blocks registered by com.oldrepublic.world.build.BuildBlocks.

Textures are the final art (Milestone 6 part B): original, deterministic (each function seeded on its own),
16x16, lit from the top left and worn like the mod's ruin, tomb and wreck textures, each one's average colour
close to the section 4 table (the structure previews colour every block from it). Every texture is opaque
except the grate's holes and the debris (alpha 0: cutout) and the scorch mark's soft edge, the only partial
alpha in the kit (translucent). No circles, rings, sigils or glyphs (docs/STRUCTURES.md section 3): pillar
ends are cut stone faces, screens show bars and readouts.

Models: the light panels are cubes whose faces glow (light_emission); the console and the holo screen face
the player, their screen set one pixel back in a bezel and glowing, the side texture's right-hand column at
the front on both flanks; Republic and Imperial plating have trimmed sides and plain tops (their stairs and
slabs too); crates show ribbed sides and a lid; pillars stand on their axis; walls are vanilla's parts plus
an empty part that names the states vanilla leaves implicit (no post, an open side).

Writes (paths under src/main/resources unless noted):
  assets/oldrepublic/textures/block/build/*.png     16x16 textures (cutout or translucent where the alpha says so)
  assets/oldrepublic/blockstates/<id>.json          one per block: cube, stairs, slab, wall, pillar axis, facing, carpet
  assets/oldrepublic/models/block/build/*.json      block models (stairs inner/outer, slab top, wall parts, pillar
                                                    horizontal, glowing lights and screens)
  assets/oldrepublic/items/<id>.json                item definitions (the block model; a wall its inventory model)
  data/oldrepublic/loot_table/blocks/<id>.json      drops itself (a slab two when double)
  data/minecraft/tags/{block,item}/{stairs,slabs,walls}.json, data/minecraft/tags/block/mineable/pickaxe.json (merged)
  data/oldrepublic/tags/block/build_kit.json        every kit block (included by #oldrepublic:built_blocks)
  src/main/lang-parts/build.json                    English names

Run from the repository root: python3 tools/assets/gen_textures_buildkit.py [--textures-only]
"""
import argparse
import json
import math
import os
import random
import re

from PIL import Image

REPO = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
RES = os.path.join(REPO, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "oldrepublic")
TEXTURES = os.path.join(ASSETS, "textures", "block", "build")
DATA = os.path.join(RES, "data")
LANG_PART = os.path.join(REPO, "src", "main", "lang-parts", "build.json")
JAVA = os.path.join(REPO, "src", "main", "java", "com", "oldrepublic", "world", "build", "BuildBlocks.java")

NS = "oldrepublic"

# (id, shape, base texture for stairs, slabs and walls, English name), in BuildBlocks order.
# Model shapes: cube (cube_all), trimmed (cube_column: <id> on the sides, <id>_top on top and bottom),
# light (a glowing cube), column (cube_column: <id>_side, <id>_top), pillar (axis: <id>_side, <id>_end),
# facing (a screen cabinet: <id> the front, <id>_side, <id>_top), stairs, slab, wall, carpet.
KIT = [
    ("durasteel_plating", "cube", None, "Durasteel Plating"),
    ("durasteel_plating_stairs", "stairs", "durasteel_plating", "Durasteel Plating Stairs"),
    ("durasteel_plating_slab", "slab", "durasteel_plating", "Durasteel Plating Slab"),
    ("durasteel_deck", "cube", None, "Durasteel Deck"),
    ("durasteel_deck_slab", "slab", "durasteel_deck", "Durasteel Deck Slab"),
    ("durasteel_grate", "cube", None, "Durasteel Grate"),
    ("durasteel_beam", "pillar", None, "Durasteel Beam"),
    ("durasteel_pipes", "pillar", None, "Durasteel Pipes"),
    ("durasteel_vent", "cube", None, "Durasteel Vent"),
    ("rusted_plating", "cube", None, "Rusted Plating"),
    ("republic_plating", "trimmed", None, "Republic Plating"),
    ("republic_plating_stairs", "stairs", "republic_plating", "Republic Plating Stairs"),
    ("republic_plating_slab", "slab", "republic_plating", "Republic Plating Slab"),
    ("imperial_plating", "trimmed", None, "Imperial Plating"),
    ("imperial_plating_stairs", "stairs", "imperial_plating", "Imperial Plating Stairs"),
    ("imperial_plating_slab", "slab", "imperial_plating", "Imperial Plating Slab"),
    ("light_panel", "light", None, "Light Panel"),
    ("light_panel_amber", "light", None, "Amber Light Panel"),
    ("light_panel_red", "light", None, "Red Light Panel"),
    ("console", "facing", None, "Console"),
    ("holo_screen", "facing", None, "Holo Screen"),
    ("cargo_crate", "column", None, "Cargo Crate"),
    ("cargo_crate_republic", "column", None, "Republic Cargo Crate"),
    ("cargo_crate_imperial", "column", None, "Imperial Cargo Crate"),
    ("ruin_bricks", "cube", None, "Ruin Bricks"),
    ("ruin_brick_stairs", "stairs", "ruin_bricks", "Ruin Brick Stairs"),
    ("ruin_brick_slab", "slab", "ruin_bricks", "Ruin Brick Slab"),
    ("ruin_brick_wall", "wall", "ruin_bricks", "Ruin Brick Wall"),
    ("ruin_pillar", "pillar", None, "Ruin Pillar"),
    ("tomb_bricks", "cube", None, "Tomb Bricks"),
    ("tomb_brick_stairs", "stairs", "tomb_bricks", "Tomb Brick Stairs"),
    ("tomb_brick_slab", "slab", "tomb_bricks", "Tomb Brick Slab"),
    ("tomb_brick_wall", "wall", "tomb_bricks", "Tomb Brick Wall"),
    ("tomb_pillar", "pillar", None, "Tomb Pillar"),
    ("watch_stone", "cube", None, "Watch Stone"),
    ("watch_stone_stairs", "stairs", "watch_stone", "Watch Stone Stairs"),
    ("watch_stone_slab", "slab", "watch_stone", "Watch Stone Slab"),
    ("watch_stone_wall", "wall", "watch_stone", "Watch Stone Wall"),
    ("ash_bricks", "cube", None, "Ash Bricks"),
    ("ash_brick_stairs", "stairs", "ash_bricks", "Ash Brick Stairs"),
    ("ash_brick_slab", "slab", "ash_bricks", "Ash Brick Slab"),
    ("ash_brick_wall", "wall", "ash_bricks", "Ash Brick Wall"),
    ("adobe", "cube", None, "Adobe"),
    ("adobe_stairs", "stairs", "adobe", "Adobe Stairs"),
    ("adobe_slab", "slab", "adobe", "Adobe Slab"),
    ("adobe_wall", "wall", "adobe", "Adobe Wall"),
    ("adobe_bricks", "cube", None, "Adobe Bricks"),
    ("salt_bricks", "cube", None, "Salt Bricks"),
    ("salt_brick_slab", "slab", "salt_bricks", "Salt Brick Slab"),
    ("stormstone", "cube", None, "Stormstone"),
    ("stormstone_bricks", "cube", None, "Stormstone Bricks"),
    ("stormstone_brick_stairs", "stairs", "stormstone_bricks", "Stormstone Brick Stairs"),
    ("stormstone_brick_slab", "slab", "stormstone_bricks", "Stormstone Brick Slab"),
    ("stormstone_brick_wall", "wall", "stormstone_bricks", "Stormstone Brick Wall"),
    ("pale_tiles", "cube", None, "Pale Tiles"),
    ("pale_tile_stairs", "stairs", "pale_tiles", "Pale Tile Stairs"),
    ("pale_tile_slab", "slab", "pale_tiles", "Pale Tile Slab"),
    ("canvas", "cube", None, "Canvas"),
    ("canvas_stairs", "stairs", "canvas", "Canvas Stairs"),
    ("canvas_slab", "slab", "canvas", "Canvas Slab"),
    ("canvas_navy", "cube", None, "Navy Canvas"),
    ("canvas_crimson", "cube", None, "Crimson Canvas"),
    ("scorch_mark", "carpet", None, "Scorch Mark"),
    ("debris", "carpet", None, "Debris"),
]

# Not mineable with a pickaxe: cloth needs no tool, decals break by hand.
NO_PICKAXE = {"canvas", "canvas_stairs", "canvas_slab", "canvas_navy", "canvas_crimson", "scorch_mark", "debris"}


# ---------------------------------------------------------------- pixel helpers

def clamp(v):
    return max(0, min(255, int(round(v))))


def mix(a, b, t):
    return tuple(clamp(x + (y - x) * t) for x, y in zip(a, b))


def noisy(rgb, rnd, amount):
    return tuple(clamp(c + rnd.uniform(-amount, amount)) for c in rgb)


def put(px, x, y, rgb, a=255):
    px[x % 16, y % 16] = tuple(rgb[:3]) + (a,)


def shade(px, x, y, t, toward=(0, 0, 0)):
    """Mixes the pixel toward a colour (dark by default); wraps around the tile edges."""
    x %= 16
    y %= 16
    px[x, y] = mix(px[x, y][:3], toward, t) + (px[x, y][3],)


def light(px, x, y, t):
    shade(px, x, y, t, (255, 255, 255))


def offset(px, x, y, d):
    """Shifts the pixel's value by d on every channel, keeping its hue."""
    x %= 16
    y %= 16
    r, g, b, a = px[x, y]
    px[x, y] = (clamp(r + d), clamp(g + d), clamp(b + d), a)


def base(seed, rgb, amount, pits=10, flecks=8):
    """Noisy fill with a few darker pits and lighter flecks, like vanilla stone (and gen_textures_world's stone_base)."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = noisy(rgb, rnd, amount) + (255,)
    for _ in range(pits):
        shade(px, rnd.randrange(16), rnd.randrange(16), 0.22)
    for _ in range(flecks):
        light(px, rnd.randrange(16), rnd.randrange(16), 0.14)
    return img, px, rnd


def metal(seed, rgb, amount=4, pits=3, flecks=3, brush=0.35, along="x"):
    """Smooth metal: faint brushing (along the rows, or the columns) and very few pits."""
    img, px, rnd = base(seed, rgb, amount, pits, flecks)
    for i in range(16):
        if rnd.random() < brush:
            t = rnd.uniform(0.03, 0.07)
            j0 = rnd.randrange(16)
            for j in range(j0, j0 + rnd.randrange(4, 12)):
                if along == "x":
                    light(px, j, i, t)
                else:
                    light(px, i, j, t)
    return img, px, rnd


def field(seed, cells=4, octaves=2, persistence=0.55):
    """Tileable value noise over the 16x16 tile, 0..1: a random lattice that wraps, smoothly interpolated.
    cells (2, 4, 8 or 16) sets the size of the first octave's features."""
    rnd = random.Random(seed)
    out = [[0.0] * 16 for _ in range(16)]
    amp, total, c = 1.0, 0.0, cells
    for _ in range(octaves):
        lat = [[rnd.random() for _ in range(c)] for _ in range(c)]
        for y in range(16):
            fy = (y + 0.5) * c / 16.0 - 0.5
            y0 = math.floor(fy)
            ty = fy - y0
            sy = ty * ty * (3 - 2 * ty)
            for x in range(16):
                fx = (x + 0.5) * c / 16.0 - 0.5
                x0 = math.floor(fx)
                tx = fx - x0
                sx = tx * tx * (3 - 2 * tx)
                a = lat[y0 % c][x0 % c]
                b = lat[y0 % c][(x0 + 1) % c]
                d = lat[(y0 + 1) % c][x0 % c]
                e = lat[(y0 + 1) % c][(x0 + 1) % c]
                out[y][x] += ((a * (1 - sx) + b * sx) * (1 - sy) + (d * (1 - sx) + e * sx) * sy) * amp
        total += amp
        amp *= persistence
        c *= 2
        if c > 16:
            break
    lo = min(min(r) for r in out)
    hi = max(max(r) for r in out)
    return [[(v - lo) / (hi - lo) for v in row] for row in out]


def masonry(seed, rgb, mortar, amount, rows, tone=6, hue=2, bevel=0.16, pits=8, flecks=6, chips=4):
    """Courses of dressed stone. rows: (top y, height with the mortar row under the course, joint columns).
    Each stone (the span between two joints, wrapping round the tile) gets its own tone and a slight hue;
    its top and left arrises catch the light, its bottom and right ones fall in shadow. Tiles both ways.
    Returns the image, its pixels, the random source and the stones as (top y, height, first x, end x)."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    px = img.load()
    stones = []
    for (y0, h, joints) in rows:
        js = sorted(j % 16 for j in joints)
        for i, j in enumerate(js):
            nxt = js[(i + 1) % len(js)]
            if nxt <= j:
                nxt += 16
            stones.append((y0, h, j + 1, nxt))
    for (y0, h, x0, x1) in stones:
        t = rnd.uniform(-tone, tone)
        hj = [rnd.uniform(-hue, hue) for _ in range(3)]
        c = tuple(rgb[k] + t + hj[k] for k in range(3))
        for y in range(y0, y0 + h - 1):
            for x in range(x0, x1):
                put(px, x, y, noisy(c, rnd, amount))
    for (y0, h, joints) in rows:
        for x in range(16):
            put(px, x, y0 + h - 1, noisy(mortar, rnd, 4))
        for j in joints:
            for y in range(y0, y0 + h - 1):
                put(px, j, y, noisy(mortar, rnd, 4))
    for (y0, h, x0, x1) in stones:
        for x in range(x0, x1):
            light(px, x, y0, bevel)
            shade(px, x, y0 + h - 2, bevel * 0.8)
        for y in range(y0, y0 + h - 1):
            light(px, x0, y, bevel * 0.6)
            shade(px, x1 - 1, y, bevel * 0.55)
    for _ in range(chips):
        (y0, h, x0, x1) = stones[rnd.randrange(len(stones))]
        shade(px, rnd.choice((x0, x1 - 1)), rnd.choice((y0, y0 + h - 2)), 0.3)
    for _ in range(pits):
        shade(px, rnd.randrange(16), rnd.randrange(16), 0.2)
    for _ in range(flecks):
        light(px, rnd.randrange(16), rnd.randrange(16), 0.12)
    return img, px, rnd, stones


def crack(px, rnd, start, steps, dark=0.5, edge=0.18):
    """A meandering hairline crack with a pale lip; wraps so it tiles."""
    x, y = start
    for _ in range(steps):
        shade(px, x, y, dark)
        light(px, x + 1, y, edge)
        dx, dy = rnd.choice(((1, 1), (0, 1), (1, 0), (-1, 1), (1, 1)))
        x += dx
        y += dy


def cylinder(px, t=0.28, x0=0, x1=16):
    """Rounds a column face: darker toward its left and right edges (the right a little more)."""
    mid = (x0 + x1 - 1) / 2.0
    half = (x1 - x0) / 2.0
    for x in range(x0, x1):
        k = ((x - mid) / half) ** 2
        for y in range(16):
            shade(px, x, y, t * k * (1.15 if x > mid else 0.85))


def rivet(px, x, y, rgb, drop=0.32):
    """A domed rivet head: a bright pixel, its shadow below."""
    put(px, x, y, rgb)
    shade(px, x, y + 1, drop)


# ---------------------------------------------------------------- durasteel

STEEL = (130, 135, 142)
STEEL_SEAM = (70, 73, 80)
STEEL_RIVET = (190, 194, 200)
DECK = (90, 94, 101)
DECK_SEAM = (50, 52, 57)
RUST = (138, 76, 42)
RUST_DEEP = (96, 50, 28)


def hull(seed, rgb, seam, rivet_rgb, amount=4, grime=0.1, rivets=(1, 5, 9, 13)):
    """One hull plate per block, laid as horizontal strakes: a strong seam under each plate with a row of rivets
    above it, a fainter upright seam between plates, the top edge catching the light, grime gathered toward the
    foot. Never staggered, so a wall reads as plating, not brickwork."""
    img, px, rnd = metal(seed, rgb, amount, brush=0.4)
    for y in range(16):
        g = max(0.0, (y - 7) / 7.0) * grime
        for x in range(16):
            shade(px, x, y, g)
    for i in range(16):
        put(px, 15, i, noisy(mix(rgb, seam, 0.7), rnd, 3))
        light(px, 0, i, 0.07)
        shade(px, 14, i, 0.05)
    for x in range(16):
        put(px, x, 15, noisy(seam, rnd, 3))
        light(px, x, 0, 0.18)
        shade(px, x, 14, 0.1)
    for x in rivets:
        rivet(px, x, 13, rivet_rgb, 0.3)
    return img, px, rnd


def durasteel_plating():
    """Mid-grey hull plates, clean and cared for: crisp seams, a rivet row along each strake, light scuffs."""
    img, px, rnd = hull(601, STEEL, STEEL_SEAM, STEEL_RIVET)
    for (x, y) in ((2, 3), (9, 8)):
        for k in range(3):
            light(px, x + k, y + (k + 1) // 2, 0.08)
    for (x, y) in ((12, 4), (5, 10)):
        shade(px, x, y, 0.1)
    return img


def durasteel_deck():
    """Darker deck plate with a raised diamond tread (short lugs turned alternately), a plate seam on two edges."""
    img, px, rnd = metal(602, DECK, 3, pits=4, flecks=2, brush=0.0)
    lugs = set()
    for j in range(4):
        for i in range(4):
            x0, y0 = 4 * i, 4 * j
            if (i + j) % 2 == 0:
                lugs.update(((x0 + 1, y0 + 2), (x0 + 2, y0 + 1)))
            else:
                lugs.update(((x0 + 1, y0 + 1), (x0 + 2, y0 + 2)))
    for (x, y) in lugs:
        light(px, x, y, 0.26)
    for (x, y) in lugs:
        if (x + 1, y + 1) not in lugs:
            shade(px, x + 1, y + 1, 0.24)
        if (x, y + 1) not in lugs:
            shade(px, x, y + 1, 0.1)
    for (x, y) in sorted(lugs):
        if rnd.random() < 0.18:
            light(px, x, y, 0.12)
    for i in range(16):
        put(px, i, 15, noisy(DECK_SEAM, rnd, 3))
        put(px, 15, i, noisy(DECK_SEAM, rnd, 3))
        light(px, i, 0, 0.1)
        light(px, 0, i, 0.1)
    for (x, y) in ((3, 6), (10, 13), (13, 4)):
        shade(px, x, y, 0.16, RUST_DEEP)
    return img


def durasteel_grate():
    """Bar grating: heavy bearing bars with a lit top edge, tied by two cross bars; open between them (cutout)."""
    rnd = random.Random(603)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    bar = (76, 79, 85)
    for y in range(16):
        for x in range(16):
            bearing = x % 4 in (0, 1)
            cross = y in (0, 8)
            if bearing or cross:
                c = noisy(bar, rnd, 4)
                if bearing:
                    c = mix(c, (255, 255, 255), 0.12) if x % 4 == 0 else mix(c, (0, 0, 0), 0.18)
                if cross and not bearing:
                    c = mix(c, (255, 255, 255), 0.06)
                put(px, x, y, c)
    for y in (0, 8):
        for x in range(16):
            if x % 4 in (0, 1):
                put(px, x, y, mix(px[x, y][:3], (255, 255, 255), 0.1))
            shade(px, x, y + 1, 0.0)
    for (x, y) in ((4, 5), (9, 12), (12, 3), (1, 10)):
        put(px, x, y, mix(px[x, y][:3], RUST, 0.35))
    return img


BEAM = (116, 120, 128)


def durasteel_beam_side():
    """An I-beam along its axis: the flanges proud at either side, the web recessed between them in shadow,
    a welded stiffener across the web at the foot, bolts in the flanges."""
    img, px, rnd = metal(604, BEAM, 4, brush=0.4, along="y")
    for y in range(16):
        for x in range(3, 13):
            shade(px, x, y, 0.34)
        shade(px, 3, y, 0.3)
        shade(px, 4, y, 0.12)
        light(px, 12, y, 0.05)
        light(px, 0, y, 0.16)
        shade(px, 2, y, 0.16)
        light(px, 13, y, 0.1)
        shade(px, 15, y, 0.24)
    for x in range(3, 13):
        put(px, x, 13, noisy(mix(BEAM, (0, 0, 0), 0.08), rnd, 3))
        put(px, x, 14, noisy(mix(BEAM, (0, 0, 0), 0.2), rnd, 3))
        light(px, x, 13, 0.1)
        shade(px, x, 15, 0.42)
        shade(px, x, 12, 0.18)
    for (x, y) in ((1, 2), (1, 9), (14, 5), (14, 12)):
        rivet(px, x, y, STEEL_RIVET, 0.3)
    return img


def durasteel_beam_end():
    """The beam's cut end: the I of the section in ground metal, the hollows either side of the web in shadow."""
    img, px, rnd = base(605, (56, 58, 64), 3, pits=0, flecks=0)
    cut = (150, 154, 160)
    for y in range(16):
        for x in range(16):
            if y <= 2 or y >= 13 or 6 <= x <= 9:
                c = noisy(cut, rnd, 5)
                if (x + 2 * y) % 5 == 0:
                    c = mix(c, (0, 0, 0), 0.08)
                put(px, x, y, c)
    for x in range(16):
        light(px, x, 0, 0.14)
        shade(px, x, 2, 0.16)
        light(px, x, 13, 0.1)
        shade(px, x, 15, 0.22)
    for y in range(3, 13):
        light(px, 6, y, 0.1)
        shade(px, 9, y, 0.16)
        shade(px, 5, y, 0.3)
        shade(px, 10, y, 0.12)
        for x in (0, 1, 2, 3, 4, 11, 12, 13, 14, 15):
            shade(px, x, y, 0.1 if y in (3, 12) else 0.0)
    for x in list(range(0, 6)) + list(range(10, 16)):
        shade(px, x, 3, 0.25)
    for (x, y) in ((5, 3), (10, 3), (5, 12), (10, 12)):
        put(px, x, y, noisy(mix(cut, (0, 0, 0), 0.22), rnd, 3))
    return img


PIPE_STEEL = (150, 154, 160)
PIPE_COPPER = (178, 112, 66)
PIPE_BLUE = (100, 118, 142)
PIPES = ((1, 5, PIPE_STEEL), (7, 4, PIPE_COPPER), (12, 3, PIPE_BLUE))
PIPE_PROFILES = {5: (0.64, 0.96, 1.12, 1.0, 0.72), 4: (0.68, 1.1, 0.96, 0.7), 3: (0.74, 1.1, 0.74)}


def durasteel_pipes_side():
    """Three conduits along the axis (steel, copper, blued steel), a flange joint, a clamp strap across the bundle."""
    img, px, rnd = base(606, (34, 36, 40), 3, pits=0, flecks=0)
    for (x0, w, rgb) in PIPES:
        for i, k in enumerate(PIPE_PROFILES[w]):
            for y in range(16):
                put(px, x0 + i, y, noisy(tuple(c * k for c in rgb), rnd, 3))
    for y in (4, 5):
        for x in range(0, 7):
            k = (0.7, 0.8, 1.05, 1.18, 1.05, 0.86, 0.66)[x]
            put(px, x, y, noisy(tuple(c * k for c in mix(PIPE_STEEL, (0, 0, 0), 0.08)), rnd, 3))
        light(px, 2, 4, 0.12)
    for x in range(0, 7):
        shade(px, x, 6, 0.25)
    for x in range(16):
        put(px, x, 11, noisy((84, 86, 92), rnd, 3))
        put(px, x, 12, noisy((64, 66, 72), rnd, 3))
        light(px, x, 11, 0.08)
        shade(px, x, 13, 0.28)
    for bx in (0, 6, 11, 15):
        put(px, bx, 11, (156, 158, 164))
        put(px, bx, 12, (96, 98, 104))
    for y in (6, 7, 8):
        for x in range(12, 15):
            put(px, x, y, mix((214, 176, 52), (0, 0, 0), (0.25, 0.0, 0.3)[x - 12]))
    for (x, y) in ((8, 2), (9, 14), (7, 9)):
        shade(px, x, y, 0.4, (82, 148, 120))
    for (x, y) in ((3, 1), (2, 9), (4, 14)):
        light(px, x, y, 0.12)
    return img


def durasteel_pipes_end():
    """The bundle's end: each conduit closed by a square cap plate (no hollow mouths), held in a bracket plate."""
    img, px, rnd = base(607, (54, 57, 63), 3, pits=2, flecks=0)
    for i in range(16):
        light(px, i, 0, 0.14)
        light(px, 0, i, 0.1)
        shade(px, i, 15, 0.28)
        shade(px, 15, i, 0.22)
    for (x0, w, rgb), y0 in zip(PIPES, (5, 6, 7)):
        for y in range(y0, y0 + w):
            for x in range(x0, x0 + w):
                put(px, x, y, noisy(mix(rgb, (0, 0, 0), 0.06), rnd, 3))
        for i in range(w):
            light(px, x0 + i, y0, 0.2)
            light(px, x0, y0 + i, 0.12)
            shade(px, x0 + i, y0 + w - 1, 0.24)
            shade(px, x0 + w - 1, y0 + i, 0.18)
            shade(px, x0 + i, y0 + w, 0.3)
        if w >= 4:
            for (x, y) in ((x0, y0), (x0 + w - 1, y0), (x0, y0 + w - 1), (x0 + w - 1, y0 + w - 1)):
                put(px, x, y, mix(rgb, (255, 255, 255), 0.2))
    for x in range(1, 15):
        put(px, x, 2, (40, 42, 47))
        light(px, x, 3, 0.08)
        put(px, x, 13, (40, 42, 47))
        light(px, x, 14, 0.06)
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        rivet(px, x, y, (150, 154, 160), 0.2)
    return img


def durasteel_vent():
    """A plating panel with a recessed vent: angled slats, the lip's shadow along the top, dust on the slats."""
    img, px, rnd = metal(608, STEEL, 4)
    for i in range(16):
        put(px, i, 15, noisy(STEEL_SEAM, rnd, 3))
        put(px, 15, i, noisy(STEEL_SEAM, rnd, 3))
        light(px, i, 0, 0.16)
        light(px, 0, i, 0.14)
    for y in range(3, 13):
        for x in range(3, 13):
            if (y - 3) % 2 == 0:
                c = mix(STEEL, (255, 255, 255), 0.1) if y > 3 else mix(STEEL, (0, 0, 0), 0.35)
                put(px, x, y, noisy(c, rnd, 3))
            else:
                put(px, x, y, noisy((28, 30, 34), rnd, 2))
    for i in range(2, 14):
        shade(px, i, 2, 0.32)
        shade(px, 2, i, 0.26)
        light(px, i, 13, 0.14)
        light(px, 13, i, 0.1)
    for x in range(3, 13):
        if rnd.random() < 0.3:
            shade(px, x, 5 + 2 * rnd.randrange(4), 0.15, (96, 86, 72))
    for (x, y) in ((1, 1), (13, 1), (1, 13), (13, 13)):
        rivet(px, x, y, STEEL_RIVET, 0.25)
    for x in (4, 9):
        shade(px, x, 14, 0.12)
    return img


def rusted_plating():
    """Scavenged plating gone to rust: rust eating the plate in patches, streaks bleeding down from the rivets and
    the seam, a patch plate riveted over a hole."""
    img, px, rnd = hull(609, (116, 110, 104), (68, 62, 58), (156, 146, 136), amount=6, grime=0.14)
    f = field(6091, cells=4, octaves=3)
    for y in range(16):
        for x in range(16):
            v = f[y][x]
            if v > 0.42:
                shade(px, x, y, min(0.7, (v - 0.42) * 2.0), RUST)
            if v > 0.74:
                shade(px, x, y, 0.4, RUST_DEEP)
    for x in (1, 5, 9, 13):
        for k in range(rnd.randrange(1, 3)):
            shade(px, x, 14 + k, 0.35, (124, 64, 34))
    for x in (3, 7, 11):
        for k in range(rnd.randrange(2, 5)):
            shade(px, x, 0 + k, 0.3 - k * 0.06, (118, 62, 34))
    return img


REPUBLIC = (216, 212, 202)
REPUBLIC_SEAM = (160, 158, 152)
NAVY = (40, 62, 112)


def republic_panel_base(seed):
    """An off-white hull panel with its seams on the bottom row and the right column, lit along the top and left."""
    img, px, rnd = metal(seed, REPUBLIC, 4, pits=2, flecks=0, brush=0.25)
    for i in range(16):
        put(px, i, 15, noisy(REPUBLIC_SEAM, rnd, 3))
        put(px, 15, i, noisy(REPUBLIC_SEAM, rnd, 3))
        light(px, i, 0, 0.14)
        light(px, 0, i, 0.1)
        shade(px, i, 14, 0.05)
        shade(px, 14, i, 0.04)
    return img, px, rnd


def republic_plating():
    """Old Republic hull: off-white panels with one navy trim line, in line with republic_panel's band."""
    img, px, rnd = republic_panel_base(610)
    for x in range(16):
        put(px, x, 11, noisy(NAVY, rnd, 4))
        put(px, x, 12, noisy(mix(NAVY, (0, 0, 0), 0.14), rnd, 4))
        light(px, x, 10, 0.06)
        shade(px, x, 13, 0.1)
    put(px, 15, 11, mix(NAVY, (0, 0, 0), 0.3))
    put(px, 15, 12, mix(NAVY, (0, 0, 0), 0.4))
    for (x, y) in ((2, 2), (13, 2)):
        put(px, x, y, (180, 178, 170))
        shade(px, x, y + 1, 0.06)
    for x in range(16):
        shade(px, x, 14, 0.04, (120, 110, 90))
    return img


def republic_plating_top():
    """The same panel seen from above or below: no trim, a pair of fixings at each end."""
    img, px, rnd = republic_panel_base(617)
    for (x, y) in ((2, 2), (12, 2), (2, 12), (12, 12)):
        put(px, x, y, (180, 178, 170))
        shade(px, x, y + 1, 0.06)
    for i in range(3, 12):
        shade(px, i, 7, 0.04)
    return img


IMPERIAL = (52, 53, 58)
IMPERIAL_SEAM = (28, 28, 32)
CRIMSON = (152, 30, 32)


def imperial_panel_base(seed):
    """A gunmetal panel: a dark seam on the bottom row, an upright seam splitting it, cold faint highlights."""
    img, px, rnd = metal(seed, IMPERIAL, 3, pits=2, flecks=2, brush=0.3)
    for i in range(16):
        put(px, i, 15, noisy(IMPERIAL_SEAM, rnd, 2))
        light(px, i, 0, 0.07)
    for y in range(15):
        put(px, 7, y, noisy(IMPERIAL_SEAM, rnd, 2))
        light(px, 8, y, 0.06)
    return img, px, rnd


def imperial_plating():
    """Imperial gunmetal: dark panels, slatted with faint ribs, one thin red line in line with imperial_panel's."""
    img, px, rnd = imperial_panel_base(611)
    for y in (3, 6, 9):
        for x in range(16):
            if x != 7:
                light(px, x, y, 0.05)
                shade(px, x, y + 1, 0.08)
    for x in range(16):
        put(px, x, 13, noisy(CRIMSON, rnd, 5))
        shade(px, x, 14, 0.2)
        light(px, x, 12, 0.03)
    for (x, y) in ((2, 1), (12, 1)):
        put(px, x, y, (96, 98, 104))
    return img


def imperial_plating_top():
    img, px, rnd = imperial_panel_base(618)
    for (x, y) in ((2, 2), (12, 2), (2, 12), (12, 12)):
        put(px, x, y, (96, 98, 104))
        shade(px, x, y + 1, 0.2)
    return img


# ---------------------------------------------------------------- lights and machines

def light_panel(seed, glow, hot):
    """A recessed light strip: a dark housing lip above and below, the diffuser running the full width (so panels
    in a row read as one strip), brightest along its middle, faint lens ribs."""
    rnd = random.Random(seed)
    housing = (104, 108, 116)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    px = img.load()
    for y in range(16):
        for x in range(16):
            put(px, x, y, noisy(housing, rnd, 3))
    for x in range(16):
        light(px, x, 0, 0.16)
        shade(px, x, 2, 0.24)
        light(px, x, 13, 0.1)
        shade(px, x, 15, 0.3)
    profile = {3: 0.62, 4: 0.88, 5: 0.97, 6: 1.0, 7: 1.0 + hot, 8: 1.0 + hot, 9: 1.0, 10: 0.97, 11: 0.88, 12: 0.62}
    for y, k in profile.items():
        for x in range(16):
            c = mix(housing, glow, k) if k <= 1 else mix(glow, (255, 255, 255), k - 1)
            if x % 2 == 1:
                c = mix(c, (0, 0, 0), 0.05)
            put(px, x, y, noisy(c, rnd, 2))
    for (x, y) in ((3, 1), (12, 1), (3, 14), (12, 14)):
        put(px, x, y, (150, 154, 160))
    return img


CASING = (66, 70, 78)
GLASS = (10, 26, 32)
TEAL = (84, 214, 190)
AMBER = (240, 182, 82)

# The emissive screens: (u0, v0, u1, v1) of each front texture, drawn by its own element in the model.
CONSOLE_SCREEN = (2, 2, 14, 9)
HOLO_SCREEN = (1, 1, 15, 15)


def casing(seed, rgb=CASING):
    img, px, rnd = metal(seed, rgb, 3, pits=2, flecks=2, brush=0.25)
    for i in range(16):
        light(px, i, 0, 0.16)
        light(px, 0, i, 0.1)
        shade(px, i, 15, 0.3)
        shade(px, 15, i, 0.2)
    return img, px, rnd


def console_front():
    """A control console: a lit screen of bar graphs and readouts in a dark bezel, a sloped key panel below."""
    img, px, rnd = casing(612)
    u0, v0, u1, v1 = CONSOLE_SCREEN
    for y in range(v0, v1):
        for x in range(u0, u1):
            put(px, x, y, noisy(GLASS, rnd, 2))
            if y % 2 == 1:
                light(px, x, y, 0.035)
    for x in range(u0 - 1, u1 + 1):
        put(px, x, v0 - 1, (30, 32, 37))
        put(px, x, v1, (30, 32, 37))
    for y in range(v0 - 1, v1 + 1):
        put(px, u0 - 1, y, (30, 32, 37))
        put(px, u1, y, (30, 32, 37))
    for x in range(3, 7):
        put(px, x, 3, AMBER)
    for x in range(8, 11):
        put(px, x, 3, TEAL)
    for x in (11, 12):
        put(px, x, 3, mix(TEAL, GLASS, 0.5))
    for (x, h) in ((3, 2), (5, 3), (7, 1)):
        for k in range(h):
            put(px, x, 7 - k, TEAL if k < h - 1 else mix(TEAL, (255, 255, 255), 0.35))
    for x in range(9, 13):
        put(px, x, 5, mix(TEAL, GLASS, 0.55))
    for x in range(9, 12):
        put(px, x, 7, (98, 210, 122))
    put(px, 12, 7, mix((98, 210, 122), GLASS, 0.6))
    light(px, 2, 2, 0.18)
    for x in range(1, 15):
        light(px, x, 10, 0.12)
        shade(px, x, 15, 0.1)
    for (x0, rgb) in ((2, (212, 62, 52)), (5, AMBER), (8, (84, 204, 112)), (11, (84, 152, 242))):
        for x in (x0, x0 + 1):
            put(px, x, 11, rgb)
            put(px, x, 12, mix(rgb, (0, 0, 0), 0.45))
    for x in range(2, 14, 2):
        put(px, x, 13, (150, 154, 160))
        put(px, x, 14, (40, 42, 48))
    return img


def console_side():
    """The cabinet's flank: an access panel with a vent and four fixings."""
    img, px, rnd = casing(613)
    for i in range(3, 13):
        put(px, i, 3, (46, 48, 54))
        put(px, i, 12, (46, 48, 54))
        put(px, 3, i, (46, 48, 54))
        put(px, 12, i, (46, 48, 54))
        light(px, i, 4, 0.08)
        light(px, 4, i, 0.06)
    for y in (8, 10):
        for x in range(5, 11):
            put(px, x, y, (26, 28, 32))
            light(px, x, y + 1, 0.08)
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        put(px, x, y, (132, 136, 144))
    return img


def console_top():
    """The desk: a bed of small keys behind the front lip, two lit, and a vent grille behind them."""
    img, px, rnd = casing(614)
    for y in range(2, 9):
        for x in range(1, 15):
            put(px, x, y, noisy((40, 42, 48), rnd, 2))
    keys = ([(2, 2), (5, 2), (8, 2), (11, 2)], [(3, 2), (6, 2), (9, 2), (12, 2)], [(2, 2), (5, 5), (11, 2)])
    for row, ks in zip((3, 5, 7), keys):
        for (x0, w) in ks:
            for x in range(x0, x0 + w):
                put(px, x, row, noisy((122, 126, 134), rnd, 3))
            light(px, x0, row, 0.12)
    for (x, y, rgb) in ((5, 3, AMBER), (6, 3, AMBER), (12, 5, TEAL), (13, 5, TEAL)):
        put(px, x, y, rgb)
    for x in range(1, 15):
        shade(px, x, 9, 0.12)
    for y in (11, 13):
        for x in range(3, 13):
            put(px, x, y, (28, 30, 34))
            light(px, x, y + 1, 0.06)
    return img


HOLO_HI = (160, 226, 255)


def holo_screen_front():
    """A wall display: a thin dark bezel round a blue holo field with scan lines, a terrain trace, readouts and
    level bars."""
    img, px, rnd = casing(615, (40, 44, 54))
    u0, v0, u1, v1 = HOLO_SCREEN
    for y in range(v0, v1):
        for x in range(u0, u1):
            t = (y - v0) / float(v1 - v0 - 1)
            c = mix((56, 138, 214), (26, 74, 140), t)
            if y % 2 == 0:
                c = mix(c, (0, 0, 0), 0.14)
            put(px, x, y, noisy(c, rnd, 3))
    for x in (5, 10):
        for y in range(v0, v1):
            light(px, x, y, 0.05)
    trace = [11, 11, 10, 10, 9, 9, 8, 8, 9, 10, 10, 9, 9, 10]
    for i, h in enumerate(trace):
        x = u0 + i
        put(px, x, h, HOLO_HI)
        for y in range(h + 1, v1):
            light(px, x, y, 0.07)
    for x in range(2, 7):
        put(px, x, 2, HOLO_HI)
    for x in range(2, 5):
        put(px, x, 4, mix(HOLO_HI, (40, 110, 190), 0.4))
    for (x, h) in ((11, 3), (13, 2)):
        for k in range(h):
            put(px, x, 5 - k, mix(HOLO_HI, (40, 110, 190), 0.25 if k < h - 1 else 0.0))
    for y in range(v0, v1):
        light(px, u0, y, 0.1)
        shade(px, u1 - 1, y, 0.06)
    for x in range(u0, u1):
        light(px, x, v0, 0.1)
    return img


def holo_screen_casing(seed):
    img, px, rnd = casing(seed, (44, 50, 62))
    for y in range(4, 12, 2):
        for x in range(3, 10):
            put(px, x, y, (28, 32, 40))
    return img, px, rnd


def holo_screen_side():
    """The display's housing; its right edge (turned to the front on both flanks) carries the screen's glow."""
    img, px, rnd = holo_screen_casing(616)
    for y in range(16):
        put(px, 15, y, noisy((70, 150, 222), rnd, 4))
        put(px, 14, y, noisy((34, 40, 50), rnd, 2))
    return img


def holo_screen_back():
    """The housing's back and underside: vents, no glow."""
    img, px, rnd = holo_screen_casing(617)
    return img


def holo_screen_top():
    """The housing from above: vent slots, the glowing front edge along the top row."""
    img, px, rnd = casing(619, (44, 50, 62))
    for x in range(16):
        put(px, x, 0, noisy((70, 150, 222), rnd, 4))
        put(px, x, 1, noisy((34, 40, 50), rnd, 2))
    for x in range(3, 13):
        if x % 2 == 1:
            for y in (7, 8, 9):
                put(px, x, y, (26, 30, 38))
    return img


# ---------------------------------------------------------------- cargo

def crate_frame(px, rnd, frame):
    for i in range(16):
        for (x, y) in ((i, 0), (i, 1), (i, 14), (i, 15), (0, i), (1, i), (14, i), (15, i)):
            put(px, x, y, noisy(frame, rnd, 3))
    for i in range(16):
        light(px, i, 0, 0.12)
        light(px, 0, i, 0.08)
        shade(px, i, 15, 0.25)
        shade(px, 15, i, 0.2)
    for i in range(2, 14):
        shade(px, i, 2, 0.2)
        shade(px, 2, i, 0.12)
        light(px, i, 13, 0.06)
    for (cx, cy) in ((0, 0), (14, 0), (0, 14), (14, 14)):
        for (x, y) in ((cx, cy), (cx + 1, cy), (cx, cy + 1), (cx + 1, cy + 1)):
            light(px, x, y, 0.1)
        shade(px, cx + 1, cy + 1, 0.35)


def crate_side(seed, body, frame, accent, mode):
    """A ribbed cargo container's flank: corrugated ribs inside a heavy frame with corner castings, and its livery:
    hazard-striped corners, a painted band, or a thin line."""
    img, px, rnd = metal(seed, body, 4, brush=0.0)
    for y in range(2, 14):
        for x in range(2, 14):
            k = (x - 2) % 3
            if k == 0:
                light(px, x, y, 0.12)
            elif k == 2:
                shade(px, x, y, 0.24)
    crate_frame(px, rnd, frame)
    if mode == "hazard":
        for y in range(16):
            for x in range(16):
                for (cx, cy) in ((0, 0), (15, 0), (0, 15), (15, 15)):
                    d = abs(x - cx) + abs(y - cy)
                    if d <= 4 and (x in (0, 1, 14, 15) or y in (0, 1, 14, 15)):
                        c = accent if ((x + y) // 2) % 2 == 0 else (34, 34, 36)
                        put(px, x, y, noisy(c, rnd, 4))
        for y in range(5, 8):
            for x in range(6, 10):
                put(px, x, y, noisy((206, 206, 198), rnd, 3))
        for x in range(6, 10):
            shade(px, x, 8, 0.3)
        put(px, 7, 6, (90, 90, 88))
        put(px, 8, 6, (90, 90, 88))
    else:
        rows = (6, 7, 8, 9) if mode == "band" else (7, 8)
        for y in rows:
            for x in range(2, 14):
                k = (x - 2) % 3
                c = noisy(accent, rnd, 4)
                if k == 0:
                    c = mix(c, (255, 255, 255), 0.1)
                elif k == 2:
                    c = mix(c, (0, 0, 0), 0.24)
                put(px, x, y, c)
        for x in range(2, 14):
            shade(px, x, rows[-1] + 1, 0.15)
            light(px, x, rows[0], 0.06)
    for (x, y) in ((4, 11), (10, 4), (12, 9)):
        light(px, x, y, 0.16)
    return img


def crate_top(seed, body, frame, accent, mode):
    """The lid: a raised panel with two handle slots, the frame and corner castings, the livery's mark."""
    img, px, rnd = metal(seed, body, 4, brush=0.0)
    crate_frame(px, rnd, frame)
    for i in range(3, 13):
        light(px, i, 3, 0.12)
        light(px, 3, i, 0.08)
        shade(px, i, 12, 0.18)
        shade(px, 12, i, 0.14)
    for x in range(6, 10):
        for y in (4, 11):
            put(px, x, y, (30, 30, 34))
            light(px, x, y + 1, 0.1)
    if mode == "hazard":
        for y in range(16):
            for x in range(16):
                for (cx, cy) in ((0, 0), (15, 0), (0, 15), (15, 15)):
                    if abs(x - cx) + abs(y - cy) <= 4 and (x in (0, 1, 14, 15) or y in (0, 1, 14, 15)):
                        c = accent if ((x + y) // 2) % 2 == 0 else (34, 34, 36)
                        put(px, x, y, noisy(c, rnd, 4))
    elif mode == "band":
        for y in (7, 8):
            for x in range(3, 13):
                put(px, x, y, noisy(accent, rnd, 4))
        for x in range(3, 13):
            shade(px, x, 9, 0.1)
    else:
        for x in range(3, 13):
            put(px, x, 8, noisy(accent, rnd, 5))
    return img


CRATES = {
    "cargo_crate": ((120, 124, 130), (76, 78, 84), (222, 182, 44), "hazard"),
    "cargo_crate_republic": ((214, 212, 204), (152, 152, 148), (44, 70, 130), "band"),
    "cargo_crate_imperial": ((50, 50, 54), (28, 28, 32), (156, 32, 34), "line"),
}


# ---------------------------------------------------------------- masonry

RUIN = (192, 178, 151)
RUIN_MORTAR = (132, 118, 96)


def ruin_bricks():
    """Sand-coloured dressed stone of the ruin_stone family, in smaller courses: each stone its own tone."""
    rows = [(0, 4, (3, 11)), (4, 4, (7, 15)), (8, 4, (1, 10)), (12, 4, (5, 13))]
    img, px, rnd, stones = masonry(620, RUIN, RUIN_MORTAR, 8, rows, tone=7, hue=2, bevel=0.16)
    crack(px, rnd, (12, 9), 3, dark=0.28, edge=0.08)
    return img


def ruin_pillar_side():
    """A fluted drum: shallow flutes, a bedding joint at the foot, rounded flanks."""
    img, px, rnd = base(621, (198, 185, 157), 7, pits=6, flecks=5)
    for x in range(1, 16, 3):
        for y in range(16):
            shade(px, x, y, 0.22)
            light(px, x + 1, y, 0.1)
    cylinder(px, 0.24)
    for x in range(16):
        put(px, x, 15, noisy(RUIN_MORTAR, rnd, 4))
        shade(px, x, 14, 0.12)
        light(px, x, 0, 0.12)
    for (x, y0, n) in ((5, 2, 4), (11, 8, 5)):
        for k in range(n):
            shade(px, x, y0 + k, 0.1)
    return img


def ruin_pillar_end():
    """The drum's cut face: a smooth drafted margin round a stippled, pick-dressed field."""
    img, px, rnd = base(622, (188, 174, 148), 5, pits=0, flecks=3)
    for y in range(3, 13):
        for x in range(3, 13):
            r = rnd.random()
            if r < 0.2:
                shade(px, x, y, 0.13)
            elif r < 0.3:
                light(px, x, y, 0.06)
    for i in range(1, 15):
        shade(px, i, 2, 0.08)
        shade(px, 2, i, 0.06)
        light(px, i, 13, 0.05)
        light(px, 13, i, 0.04)
    for i in range(16):
        light(px, i, 0, 0.16)
        light(px, 0, i, 0.12)
        shade(px, i, 15, 0.26)
        shade(px, 15, i, 0.22)
    return img


TOMB = (56, 52, 56)
TOMB_SEAM = (50, 36, 35)


def tomb_bricks():
    """Black basalt blocks, long and short, with faint red-brown seams and a polished sheen."""
    rows = [(0, 4, (2, 12)), (4, 4, (7,)), (8, 4, (4, 14)), (12, 4, (9,))]
    img, px, rnd, stones = masonry(623, TOMB, TOMB_SEAM, 4, rows, tone=4, hue=1.5, bevel=0.12, pits=6, flecks=9)
    for (y0, h, x0, x1) in stones:
        sx = x0 + rnd.randrange(max(1, x1 - x0 - 2))
        light(px, sx, y0 + 1, 0.07)
        light(px, sx + 1, y0, 0.05)
    for _ in range(4):
        y0, h, _x0, _x1 = stones[rnd.randrange(len(stones))]
        shade(px, rnd.randrange(16), y0 + h - 1, 0.25, (112, 56, 44))
    return img


def tomb_pillar_side():
    """A carved black column: two deep arrises framing a polished face, a cut double-groove collar with
    old red pigment left in the grooves."""
    img, px, rnd = base(624, (48, 44, 48), 4, pits=4, flecks=8)
    for y in range(16):
        for x in (0, 1, 2):
            light(px, x, y, 0.04)
        for x in (13, 14, 15):
            shade(px, x, y, 0.22)
        for gx in (3, 12):
            shade(px, gx, y, 0.45)
            light(px, gx + 1, y, 0.1)
    for x in range(16):
        light(px, x, 0, 0.08)
        for gy in (1, 3):
            put(px, x, gy, noisy(mix((48, 44, 48), (0, 0, 0), 0.45), rnd, 2))
            if 3 < x < 13 and rnd.random() < 0.4:
                put(px, x, gy, noisy((96, 36, 32), rnd, 5))
        light(px, x, 2, 0.1)
        shade(px, x, 4, 0.3)
        shade(px, x, 15, 0.25)
    for y in range(5, 15):
        if y % 4 == 1:
            light(px, 6, y, 0.05)
    return img


def tomb_pillar_end():
    """The column's cut face: polished basalt in a drafted margin, a faint sheen."""
    img, px, rnd = base(625, (42, 38, 42), 3, pits=0, flecks=6)
    for i in range(1, 15):
        shade(px, i, 2, 0.2)
        shade(px, 2, i, 0.16)
        light(px, i, 13, 0.06)
        light(px, 13, i, 0.05)
    for i in range(16):
        light(px, i, 0, 0.1)
        light(px, 0, i, 0.08)
        shade(px, i, 15, 0.3)
        shade(px, 15, i, 0.26)
    for (x, y) in ((4, 4), (5, 4), (4, 5), (6, 4), (5, 5)):
        light(px, x, y, 0.05)
    return img


WATCH = (160, 170, 156)
WATCH_MORTAR = (116, 126, 114)


def watch_stone():
    """Selvaren's Watch stone: pale grey-green ashlar in courses of uneven height, lichen on a few stones."""
    rows = [(0, 6, (3, 10)), (6, 5, (6, 14)), (11, 5, (1, 9))]
    img, px, rnd, stones = masonry(626, WATCH, WATCH_MORTAR, 6, rows, tone=7, hue=3, bevel=0.15)
    for (x, y, n) in ((5, 2, 2), (12, 8, 2), (3, 13, 2), (9, 12, 1)):
        for k in range(n):
            shade(px, x + k, y, 0.5, (172, 182, 138))
    for (x, y) in ((6, 9), (14, 7), (9, 14), (1, 12)):
        put(px, x, y, noisy((92, 112, 80), rnd, 4))
    return img


ASH = (132, 129, 126)
ASH_MORTAR = (112, 56, 40)


def ash_bricks():
    """Varkhast's ash brick: small grey bricks in rust-red mortar, sooted in patches, ash on the ledges."""
    rows = [(0, 4, (3, 11)), (4, 4, (7, 15)), (8, 4, (3, 11)), (12, 4, (7, 15))]
    img, px, rnd, stones = masonry(627, ASH, ASH_MORTAR, 6, rows, tone=9, hue=2, bevel=0.12)
    f = field(6271, cells=4, octaves=2)
    for y in range(16):
        for x in range(16):
            if f[y][x] > 0.62:
                shade(px, x, y, (f[y][x] - 0.62) * 0.9, (38, 36, 36))
    for (y0, h, x0, x1) in stones:
        for x in range(x0, x1):
            if rnd.random() < 0.25:
                light(px, x, y0, 0.12)
    return img


ADOBE = (206, 176, 128)


def adobe():
    """Ghessar's sand-plaster render: smooth, broad trowel strokes, faint tonal drift, a fine hairline."""
    img, px, rnd = base(628, ADOBE, 4, pits=3, flecks=3)
    f = field(6281, cells=2, octaves=3)
    for y in range(16):
        for x in range(16):
            offset(px, x, y, (f[y][x] - 0.5) * 12)
    g = field(6282, cells=4, octaves=2)
    for y in range(16):
        for x in range(16):
            offset(px, x, y, (g[y][(x + y // 3) % 16] - 0.5) * 6)
    for _ in range(5):
        y0 = rnd.randrange(16)
        x0 = rnd.randrange(16)
        n = rnd.randrange(6, 12)
        t = rnd.uniform(0.025, 0.045)
        for k in range(n):
            y = y0 + (k * 2) // n
            light(px, x0 + k, y, t)
            shade(px, x0 + k, y + 1, t * 0.5)
    x, y = 3, 5
    for _ in range(4):
        shade(px, x, y, 0.08)
        x += rnd.choice((0, 1, 1))
        y += 1
    return img


ADOBE_BRICK = (154, 116, 80)
ADOBE_MORTAR = (118, 90, 62)


def adobe_bricks():
    """Rough mud brick: uneven courses, deep joints, crumbled corners and straw flecks."""
    rows = [(0, 5, (4, 12)), (5, 6, (0, 8)), (11, 5, (5, 13))]
    img, px, rnd, stones = masonry(629, ADOBE_BRICK, ADOBE_MORTAR, 8, rows, tone=10, hue=3, bevel=0.1, chips=8)
    for (y0, h, x0, x1) in stones:
        for (x, y) in ((x0, y0), (x1 - 1, y0 + h - 2)):
            if rnd.random() < 0.5:
                put(px, x, y, noisy(mix(ADOBE_MORTAR, (0, 0, 0), 0.1), rnd, 4))
    for _ in range(9):
        x, y = rnd.randrange(16), rnd.randrange(16)
        dx = rnd.choice((1, -1))
        put(px, x, y, noisy((206, 180, 120), rnd, 6))
        put(px, x + dx, y + rnd.choice((0, 1)), noisy((190, 164, 108), rnd, 6))
    return img


SALT = (234, 234, 230)
SALT_SEAM = (190, 198, 210)


def salt_bricks():
    """White crystalline blocks: blue-grey seams, angular facets catching the light, sparkles."""
    rows = [(0, 8, (6, 14)), (8, 8, (2, 10))]
    img, px, rnd, stones = masonry(630, SALT, SALT_SEAM, 4, rows, tone=4, hue=1, bevel=0.08, pits=3, flecks=0)
    for (y0, h, x0, x1) in stones:
        sx = x0 + rnd.randrange(max(1, x1 - x0 - 3))
        sy = y0 + rnd.randrange(1, 3)
        for k in range(3):
            light(px, sx + k, sy + k, 0.3)
            shade(px, sx + k, sy + k + 1, 0.1, (150, 172, 204))
    for _ in range(7):
        put(px, rnd.randrange(16), rnd.randrange(16), (255, 255, 255))
    return img


STORM = (64, 70, 84)


def stormstone():
    """Tyrvann basalt: dark blue-grey, mottled, faint columnar joints, pale wet glints."""
    img, px, rnd = base(631, STORM, 7, pits=14, flecks=4)
    f = field(6311, cells=4, octaves=3)
    for y in range(16):
        for x in range(16):
            offset(px, x, y, (f[y][x] - 0.5) * 18)
    for (x, y0, n) in ((5, 1, 5), (12, 8, 6)):
        for k in range(n):
            shade(px, x + (1 if k > n // 2 else 0), y0 + k, 0.2)
            light(px, x + 1 + (1 if k > n // 2 else 0), y0 + k, 0.04)
    for (x, y) in ((9, 6), (3, 12)):
        put(px, x, y, (120, 134, 156))
    return img


STORM_BRICK = (70, 76, 90)
STORM_MORTAR = (38, 42, 52)


def stormstone_bricks():
    """Stormstone dressed into big blocks: rain-streaked faces, a little moss in the joints."""
    rows = [(0, 8, (2, 10)), (8, 8, (6, 14))]
    img, px, rnd, stones = masonry(632, STORM_BRICK, STORM_MORTAR, 6, rows, tone=5, hue=2, bevel=0.14)
    for (y0, h, x0, x1) in stones:
        for _ in range(2):
            x = x0 + rnd.randrange(max(1, x1 - x0))
            for k in range(rnd.randrange(3, 6)):
                light(px, x, y0 + 1 + k, 0.05)
    for (x, y) in ((10, 3), (6, 12), (14, 10), (0, 7)):
        put(px, x, y, noisy((56, 74, 60), rnd, 4))
    for (x, y) in ((4, 2), (12, 11)):
        put(px, x, y, (112, 124, 146))
    return img


TILE = (218, 218, 214)
GROUT = (172, 174, 172)


def pale_tiles():
    """Clean white-grey floor tiles of Republic interiors: four tiles, each its own tone, grout and a few scuffs."""
    img, px, rnd = base(633, TILE, 3, pits=2, flecks=2)
    for (tx, ty) in ((0, 0), (8, 0), (0, 8), (8, 8)):
        tone = rnd.uniform(-5, 5)
        for y in range(ty, ty + 7):
            for x in range(tx, tx + 7):
                offset(px, x, y, tone)
        for i in range(7):
            light(px, tx + i, ty, 0.12)
            light(px, tx, ty + i, 0.1)
            shade(px, tx + i, ty + 6, 0.05)
            shade(px, tx + 6, ty + i, 0.05)
    for i in range(16):
        put(px, i, 7, noisy(GROUT, rnd, 3))
        put(px, i, 15, noisy(GROUT, rnd, 3))
        put(px, 7, i, noisy(GROUT, rnd, 3))
        put(px, 15, i, noisy(GROUT, rnd, 3))
    for (x, y, n) in ((2, 4, 3), (10, 11, 2)):
        for k in range(n):
            shade(px, x + k, y, 0.09)
    return img


def canvas(seed, rgb):
    """Tent cloth: a fine diagonal twill, soft sun-fade and sag, a stitched seam down one edge."""
    img, px, rnd = base(seed, rgb, 3, pits=0, flecks=0)
    f = field(seed * 7 + 1, cells=2, octaves=2)
    for y in range(16):
        for x in range(16):
            offset(px, x, y, (f[y][x] - 0.5) * 12)
            k = (x + y) % 4
            if k == 0:
                shade(px, x, y, 0.05)
            elif k == 2:
                light(px, x, y, 0.03)
    for y in range(16):
        shade(px, 15, y, 0.14)
        if y % 3 == 0:
            put(px, 14, y, mix(rgb, (255, 255, 255), 0.25))
    return img


# ---------------------------------------------------------------- decals

def scorch_mark():
    """A blast scorch on the ground: opaque black at the heart, an irregular fringe of brown soot that fades to
    nothing (the only partial alpha in the kit), a few short soot rays thrown out by the blast."""
    rnd = random.Random(640)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    cx, cy = 7.3, 8.2
    f = field(6401, cells=4, octaves=3)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, (y - cy) * 1.12)
            v = 1.0 - d / 7.4 + (f[y][x] - 0.5) * 0.6
            if v <= 0.0:
                continue
            core = min(1.0, v / 0.5)
            a = 255 if core >= 1.0 else clamp(250 * core ** 1.3)
            if a < 16:
                continue
            c = mix((74, 60, 48), (22, 20, 18), core ** 0.7)
            px[x, y] = noisy(c, rnd, 4) + (a,)
    for k in range(6):
        ang = k * 1.05 + rnd.uniform(-0.25, 0.25)
        for s in range(3):
            r = 5.6 + s
            x, y = int(round(cx + r * math.cos(ang))), int(round(cy + r * math.sin(ang)))
            if 0 <= x < 16 and 0 <= y < 16:
                alpha = clamp(150 - s * 45)
                if px[x, y][3] < alpha:
                    px[x, y] = noisy((44, 38, 32), rnd, 4) + (alpha,)
    for _ in range(6):
        x, y = rnd.randrange(16), rnd.randrange(16)
        if px[x, y][3] == 255:
            px[x, y] = noisy((58, 54, 50), rnd, 4) + (255,)
    return img


def debris():
    """Scattered scrap and grit on bare ground (cutout): torn plate shards, a bent rod, rust, a cut wire, grit."""
    rnd = random.Random(641)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    shards = [
        ((2, 2), ((0, 0), (1, 0), (2, 0), (0, 1), (1, 1)), (128, 132, 138)),
        ((10, 1), ((0, 0), (1, 0), (1, 1), (2, 1)), (104, 108, 114)),
        ((4, 9), ((0, 0), (0, 1), (1, 1), (0, 2)), (142, 84, 48)),
        ((11, 10), ((0, 0), (1, 0), (2, 0), (1, 1), (2, 1), (3, 1)), (118, 122, 128)),
        ((7, 5), ((0, 0), (1, 0), (1, 1)), (92, 96, 102)),
    ]
    for ((x0, y0), cells, rgb) in shards:
        for (dx, dy) in cells:
            put(px, x0 + dx, y0 + dy, noisy(rgb, rnd, 6))
        top = min(dy for _, dy in cells)
        for (dx, dy) in cells:
            if dy == top:
                light(px, x0 + dx, y0 + dy, 0.18)
        for (dx, dy) in cells:
            if (dx, dy + 1) not in cells:
                put(px, x0 + dx, y0 + dy + 1, mix(rgb, (0, 0, 0), 0.6))
    for k in range(5):
        put(px, 1 + k, 14 - (k * 3) // 4, (84, 88, 94) if k % 2 else (110, 114, 120))
    for (x, y) in ((13, 5), (14, 6)):
        put(px, x, y, (184, 116, 64))
    for _ in range(22):
        x, y = rnd.randrange(16), rnd.randrange(16)
        if px[x, y][3] == 0:
            put(px, x, y, rnd.choice(((96, 90, 84), (150, 144, 136), (72, 68, 64), (122, 96, 70))))
    return img


def textures():
    tex = {
        "durasteel_plating": durasteel_plating(),
        "durasteel_deck": durasteel_deck(),
        "durasteel_grate": durasteel_grate(),
        "durasteel_beam_side": durasteel_beam_side(),
        "durasteel_beam_end": durasteel_beam_end(),
        "durasteel_pipes_side": durasteel_pipes_side(),
        "durasteel_pipes_end": durasteel_pipes_end(),
        "durasteel_vent": durasteel_vent(),
        "rusted_plating": rusted_plating(),
        "republic_plating": republic_plating(),
        "republic_plating_top": republic_plating_top(),
        "imperial_plating": imperial_plating(),
        "imperial_plating_top": imperial_plating_top(),
        "light_panel": light_panel(618, (214, 232, 248), 0.5),
        "light_panel_amber": light_panel(619, (255, 178, 82), 0.4),
        "light_panel_red": light_panel(650, (232, 54, 44), 0.3),
        "console": console_front(),
        "console_side": console_side(),
        "console_top": console_top(),
        "holo_screen": holo_screen_front(),
        "holo_screen_side": holo_screen_side(),
        "holo_screen_back": holo_screen_back(),
        "holo_screen_top": holo_screen_top(),
        "ruin_bricks": ruin_bricks(),
        "ruin_pillar_side": ruin_pillar_side(),
        "ruin_pillar_end": ruin_pillar_end(),
        "tomb_bricks": tomb_bricks(),
        "tomb_pillar_side": tomb_pillar_side(),
        "tomb_pillar_end": tomb_pillar_end(),
        "watch_stone": watch_stone(),
        "ash_bricks": ash_bricks(),
        "adobe": adobe(),
        "adobe_bricks": adobe_bricks(),
        "salt_bricks": salt_bricks(),
        "stormstone": stormstone(),
        "stormstone_bricks": stormstone_bricks(),
        "pale_tiles": pale_tiles(),
        "canvas": canvas(634, (196, 174, 130)),
        "canvas_navy": canvas(635, (48, 64, 108)),
        "canvas_crimson": canvas(636, (132, 38, 40)),
        "scorch_mark": scorch_mark(),
        "debris": debris(),
    }
    for i, (name, spec) in enumerate(sorted(CRATES.items())):
        tex[name + "_side"] = crate_side(660 + 2 * i, *spec)
        tex[name + "_top"] = crate_top(661 + 2 * i, *spec)
    return tex


# ---------------------------------------------------------------- JSON

# Model faces that glow (the block model's light_emission): the light panels and the two screens.
# API-CHECK: element "light_emission" (0 to 15) in 26.2 block models; the mod's engineering bench and holocron
# pedestal models already use it.
LIGHT_EMISSION = 15
SCREENS = {"console": CONSOLE_SCREEN, "holo_screen": HOLO_SCREEN}
SCREEN_BACKS = {"holo_screen"}  # a back texture (<id>_back) for the back and underside, which <id>_side's glow must not reach

def tex_ref(name):
    return f"{NS}:block/build/{name}"


def model_ref(name):
    return f"{NS}:block/build/{name}"


def face_uv(direction, frm, to):
    """The UV vanilla gives a face of an element by default, except the west face, which is mirrored so that
    a side texture's right-hand column meets the front on both flanks of a block that faces the player."""
    # API-CHECK: a face uv with u1 > u2 draws the texture mirrored (flipped UVs are allowed in block models).
    x1, y1, z1 = frm
    x2, y2, z2 = to
    return {
        "north": [16 - x2, 16 - y2, 16 - x1, 16 - y1],
        "south": [x1, 16 - y2, x2, 16 - y1],
        "east": [16 - z2, 16 - y2, 16 - z1, 16 - y1],
        "west": [16 - z1, 16 - y2, 16 - z2, 16 - y1],
        "up": [x1, z1, x2, z2],
        "down": [x1, 16 - z2, x2, 16 - z1],
    }[direction]


def element(frm, to, faces, emission=0):
    """faces: {direction: (texture variable, uv or None for the default, cullface or None)}."""
    out = {"from": list(frm), "to": list(to)}
    if emission:
        out["light_emission"] = emission
    out["faces"] = {}
    for direction, (texture, uv, cull) in faces.items():
        f = {"uv": uv if uv is not None else face_uv(direction, frm, to), "texture": texture}
        if cull:
            f["cullface"] = cull
        out["faces"][direction] = f
    return out


def light_model(block_id):
    """A light panel: one cube whose every face glows, so the strip reads lit whatever the light around it."""
    faces = {d: ("#all", [0, 0, 16, 16], d) for d in ("down", "up", "north", "south", "west", "east")}
    return {"parent": "minecraft:block/block",
            "textures": {"all": tex_ref(block_id), "particle": tex_ref(block_id)},
            "elements": [element((0, 0, 0), (16, 16, 16), faces, LIGHT_EMISSION)]}


def screen_model(block_id, rect):
    """A cabinet facing north (the blockstate turns it): its screen, rect (u0, v0, u1, v1) of the front
    texture, sits one pixel back in its bezel and glows; the rest of the front is a one-pixel frame."""
    u0, v0, u1, v1 = rect
    x0, x1 = 16 - u1, 16 - u0
    y0, y1 = 16 - v1, 16 - v0
    F, S, T = "#front", "#side", "#top"
    B = "#back" if block_id in SCREEN_BACKS else S
    els = [element((0, 0, 1), (16, 16, 16), {
        "down": (B, None, "down"), "up": (T, None, "up"), "south": (B, None, "south"),
        "west": (S, None, "west"), "east": (S, None, "east")})]
    frame = [
        ((0, y1, 0), (16, 16, 1), "down", [0, v0 - 1, 16, v0]),    # above the screen; its underside is the bezel
        ((0, 0, 0), (16, y0, 1), "up", [0, v1, 16, v1 + 1]),       # below the screen
        ((0, y0, 0), (x0, y1, 1), "east", [u1, v0, u1 + 1, v1]),   # the post on the west side
        ((x1, y0, 0), (16, y1, 1), "west", [u0 - 1, v0, u0, v1]),  # the post on the east side
    ]
    for frm, to, reveal, reveal_uv in frame:
        faces = {"north": (F, None, "north"), reveal: (F, reveal_uv, None)}
        if frm[0] == 0:
            faces["west"] = (S, None, "west")
        if to[0] == 16:
            faces["east"] = (S, None, "east")
        if frm[1] == 0:
            faces["down"] = (B, None, "down")
        if to[1] == 16:
            faces["up"] = (T, None, "up")
        els.append(element(frm, to, faces))
    # API-CHECK: a flat element (from z == to z) draws its one face, as vanilla's cross models do.
    els.append(element((x0, y0, 1), (x1, y1, 1), {"north": (F, [u0, v0, u1, v1], "north")}, LIGHT_EMISSION))
    textures = {"front": tex_ref(block_id), "side": tex_ref(block_id + "_side"), "top": tex_ref(block_id + "_top")}
    if block_id in SCREEN_BACKS:
        textures["back"] = tex_ref(block_id + "_back")
    textures["particle"] = tex_ref(block_id + "_side")
    return {"parent": "minecraft:block/block", "textures": textures, "elements": els}


def surface_textures(base_tex):
    """Top, bottom and side textures of a stairs or slab cut from base_tex (a trimmed block keeps its plain
    top on the treads)."""
    shape = next(k[1] for k in KIT if k[0] == base_tex)
    top = tex_ref(base_tex + "_top") if shape == "trimmed" else tex_ref(base_tex)
    return {"bottom": top, "side": tex_ref(base_tex), "top": top}


def stairs_variants(model):
    """Vanilla's stairs blockstate: straight, inner and outer models turned by facing, half and shape."""
    variants = {}
    base_y = {"east": 0, "south": 90, "west": 180, "north": 270}
    for facing in ("east", "north", "south", "west"):
        for half in ("bottom", "top"):
            for shape in ("inner_left", "inner_right", "outer_left", "outer_right", "straight"):
                m = model + ("_inner" if shape.startswith("inner") else "_outer" if shape.startswith("outer") else "")
                y = base_y[facing]
                if half == "bottom" and shape.endswith("left"):
                    y -= 90
                if half == "top" and shape.endswith("right"):
                    y += 90
                y %= 360
                v = {"model": m}
                if half == "top":
                    v["x"] = 180
                if y:
                    v["y"] = y
                if half == "top" or y:
                    v["uvlock"] = True
                variants[f"facing={facing},half={half},shape={shape}"] = v
    return {"variants": variants}


def wall_multipart(post, side, tall):
    """Vanilla's wall parts: the post when up, a low or tall side on each connected side."""
    parts = [{"apply": {"model": post}, "when": {"up": "true"}}]
    for height, model in (("low", side), ("tall", tall)):
        for direction, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            apply = {"model": model, "uvlock": True}
            if y:
                apply["y"] = y
            parts.append({"apply": apply, "when": {direction: height}})
    return {"multipart": parts}


def block_files(block_id, shape, base_tex):
    """(blockstate, {model name: model json}, item model ref) of one block."""
    models = {}
    me = model_ref(block_id)
    if shape == "cube":
        models[block_id] = {"parent": "minecraft:block/cube_all", "textures": {"all": tex_ref(block_id)}}
        state = {"variants": {"": {"model": me}}}
    elif shape == "trimmed":
        models[block_id] = {"parent": "minecraft:block/cube_column",
                            "textures": {"end": tex_ref(block_id + "_top"), "side": tex_ref(block_id)}}
        state = {"variants": {"": {"model": me}}}
    elif shape == "light":
        models[block_id] = light_model(block_id)
        state = {"variants": {"": {"model": me}}}
    elif shape == "column":
        models[block_id] = {"parent": "minecraft:block/cube_column",
                            "textures": {"end": tex_ref(block_id + "_top"), "side": tex_ref(block_id + "_side")}}
        state = {"variants": {"": {"model": me}}}
    elif shape == "pillar":
        textures = {"end": tex_ref(block_id + "_end"), "side": tex_ref(block_id + "_side")}
        models[block_id] = {"parent": "minecraft:block/cube_column", "textures": textures}
        models[block_id + "_horizontal"] = {"parent": "minecraft:block/cube_column_horizontal", "textures": dict(textures)}
        state = {"variants": {
            "axis=x": {"model": me + "_horizontal", "x": 90, "y": 90},
            "axis=y": {"model": me},
            "axis=z": {"model": me + "_horizontal", "x": 90},
        }}
    elif shape == "facing":
        models[block_id] = screen_model(block_id, SCREENS[block_id])
        state = {"variants": {
            "facing=east": {"model": me, "y": 90},
            "facing=north": {"model": me},
            "facing=south": {"model": me, "y": 180},
            "facing=west": {"model": me, "y": 270},
        }}
    elif shape == "carpet":
        models[block_id] = {"parent": "minecraft:block/carpet", "textures": {"wool": tex_ref(block_id)}}
        state = {"variants": {"": {"model": me}}}
    elif shape == "stairs":
        t = surface_textures(base_tex)
        models[block_id] = {"parent": "minecraft:block/stairs", "textures": dict(t)}
        models[block_id + "_inner"] = {"parent": "minecraft:block/inner_stairs", "textures": dict(t)}
        models[block_id + "_outer"] = {"parent": "minecraft:block/outer_stairs", "textures": dict(t)}
        state = stairs_variants(me)
    elif shape == "slab":
        t = surface_textures(base_tex)
        models[block_id] = {"parent": "minecraft:block/slab", "textures": dict(t)}
        models[block_id + "_top"] = {"parent": "minecraft:block/slab_top", "textures": dict(t)}
        state = {"variants": {
            "type=bottom": {"model": me},
            "type=double": {"model": model_ref(base_tex)},
            "type=top": {"model": me + "_top"},
        }}
    elif shape == "wall":
        w = {"wall": tex_ref(base_tex)}
        models[block_id + "_post"] = {"parent": "minecraft:block/template_wall_post", "textures": dict(w)}
        models[block_id + "_side"] = {"parent": "minecraft:block/template_wall_side", "textures": dict(w)}
        models[block_id + "_side_tall"] = {"parent": "minecraft:block/template_wall_side_tall", "textures": dict(w)}
        models[block_id + "_inventory"] = {"parent": "minecraft:block/wall_inventory", "textures": dict(w)}
        state = wall_multipart(me + "_post", me + "_side", me + "_side_tall")
        return state, models, me + "_inventory"
    else:
        raise ValueError(shape)
    return state, models, me


def loot(block_id, shape):
    name = f"{NS}:{block_id}"
    if shape == "slab":
        return {
            "type": "minecraft:block",
            "pools": [{
                "bonus_rolls": 0.0,
                "entries": [{
                    "type": "minecraft:item",
                    "functions": [
                        {"conditions": [{"block": name, "condition": "minecraft:block_state_property",
                                         "properties": {"type": "double"}}],
                         "count": 2.0, "function": "minecraft:set_count"},
                        {"function": "minecraft:explosion_decay"},
                    ],
                    "name": name,
                }],
                "rolls": 1.0,
            }],
            "random_sequence": f"{NS}:blocks/{block_id}",
        }
    return {
        "type": "minecraft:block",
        "pools": [{
            "bonus_rolls": 0.0,
            "conditions": [{"condition": "minecraft:survives_explosion"}],
            "entries": [{"type": "minecraft:item", "name": name}],
            "rolls": 1.0,
        }],
        "random_sequence": f"{NS}:blocks/{block_id}",
    }


def write_json(path, data, indent):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(data, indent=indent, ensure_ascii=False) + "\n")


def merge_tag(path, ids):
    """Adds ids to a tag file (creating it), keeping its other values and their order."""
    data = {"replace": False, "values": []}
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
    have = {v["id"] if isinstance(v, dict) else v for v in data["values"]}
    for i in ids:
        if i not in have:
            data["values"].append(i)
    write_json(path, data, 2)


def check_java():
    """The table must match BuildBlocks.java id for id, in order."""
    with open(JAVA, encoding="utf-8") as f:
        java = re.findall(r'(?:block|stairs|slab|wall|pillar|register)\("([a-z_]+)"', f.read())
    ours = [k[0] for k in KIT]
    if java != ours:
        raise SystemExit(f"gen_textures_buildkit: the table does not match BuildBlocks.java\n java: {java}\n here: {ours}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--textures-only", action="store_true", help="write the textures and nothing else")
    args = ap.parse_args()
    check_java()

    tex = textures()
    os.makedirs(TEXTURES, exist_ok=True)
    for name, img in sorted(tex.items()):
        img.save(os.path.join(TEXTURES, name + ".png"))
    print(f"wrote {len(tex)} textures to {os.path.relpath(TEXTURES, REPO)}")
    for fn in sorted(os.listdir(TEXTURES)):
        if fn.endswith(".png") and fn[:-4] not in tex:
            os.remove(os.path.join(TEXTURES, fn))
            print(f"removed {fn}, a texture the kit no longer uses")
    if args.textures_only:
        return

    used = set()
    for block_id, shape, base_tex, _ in KIT:
        state, models, item_model = block_files(block_id, shape, base_tex)
        write_json(os.path.join(ASSETS, "blockstates", block_id + ".json"), state, "\t")
        for name, model in models.items():
            write_json(os.path.join(ASSETS, "models", "block", "build", name + ".json"), model, "\t")
            for t in model["textures"].values():
                used.add(t.rsplit("/", 1)[1])
        write_json(os.path.join(ASSETS, "items", block_id + ".json"),
                   {"model": {"type": "minecraft:model", "model": item_model}}, "\t")
        write_json(os.path.join(DATA, NS, "loot_table", "blocks", block_id + ".json"), loot(block_id, shape), 2)
    missing = used - set(tex)
    unused = set(tex) - used
    if missing or unused:
        raise SystemExit(f"gen_textures_buildkit: textures missing {sorted(missing)}, unused {sorted(unused)}")

    ids = [f"{NS}:{k[0]}" for k in KIT]
    by_shape = {s: [f"{NS}:{k[0]}" for k in KIT if k[1] == s] for s in ("stairs", "slab", "wall")}
    for kind in ("block", "item"):
        merge_tag(os.path.join(DATA, "minecraft", "tags", kind, "stairs.json"), by_shape["stairs"])
        merge_tag(os.path.join(DATA, "minecraft", "tags", kind, "slabs.json"), by_shape["slab"])
        merge_tag(os.path.join(DATA, "minecraft", "tags", kind, "walls.json"), by_shape["wall"])
    merge_tag(os.path.join(DATA, "minecraft", "tags", "block", "mineable", "pickaxe.json"),
              [f"{NS}:{k[0]}" for k in KIT if k[0] not in NO_PICKAXE])
    write_json(os.path.join(DATA, NS, "tags", "block", "build_kit.json"), {"replace": False, "values": ids}, 2)
    merge_tag(os.path.join(DATA, NS, "tags", "block", "built_blocks.json"), [f"#{NS}:build_kit"])

    write_json(LANG_PART, {f"block.{NS}.{k[0]}": k[3] for k in KIT}, 2)
    print(f"wrote {len(KIT)} blocks: blockstates, models, items, loot tables, tags and {os.path.relpath(LANG_PART, REPO)}")


if __name__ == "__main__":
    main()
