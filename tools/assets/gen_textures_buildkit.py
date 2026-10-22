#!/usr/bin/env python3
"""Generates the build kit of the structures overhaul (docs/MILESTONE_6.md section 4): 64 plain
building blocks registered by com.oldrepublic.world.build.BuildBlocks.

Textures are PLACEHOLDER art (original, deterministic, each function seeded on its own); part B of
Milestone 6 replaces them with final art in this same script. Everything else is final.

Writes (paths under src/main/resources unless noted):
  assets/oldrepublic/textures/block/build/*.png     16x16 textures (cutout or translucent where the alpha says so)
  assets/oldrepublic/blockstates/<id>.json          one per block: cube, stairs, slab, wall, pillar axis, facing, carpet
  assets/oldrepublic/models/block/build/*.json      block models (stairs inner/outer, slab top, wall parts, pillar horizontal)
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
# Shapes: cube (cube_all), column (cube_column: <id>_side, <id>_top), pillar (axis: <id>_side, <id>_end),
# facing (orientable: <id>_front, <id>_side, <id>_top), stairs, slab, wall, carpet.
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
    ("republic_plating", "cube", None, "Republic Plating"),
    ("republic_plating_stairs", "stairs", "republic_plating", "Republic Plating Stairs"),
    ("republic_plating_slab", "slab", "republic_plating", "Republic Plating Slab"),
    ("imperial_plating", "cube", None, "Imperial Plating"),
    ("imperial_plating_stairs", "stairs", "imperial_plating", "Imperial Plating Stairs"),
    ("imperial_plating_slab", "slab", "imperial_plating", "Imperial Plating Slab"),
    ("light_panel", "cube", None, "Light Panel"),
    ("light_panel_amber", "cube", None, "Amber Light Panel"),
    ("light_panel_red", "cube", None, "Red Light Panel"),
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


def base(seed, rgb, amount, pits=10, flecks=8):
    """Noisy fill with a few darker pits and lighter flecks, like vanilla stone."""
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


def metal(seed, rgb, amount=4):
    """Smooth metal: faint horizontal brushing and very few pits."""
    img, px, rnd = base(seed, rgb, amount, pits=3, flecks=3)
    for y in range(16):
        if rnd.random() < 0.35:
            t = rnd.uniform(0.03, 0.07)
            x0 = rnd.randrange(16)
            for x in range(x0, x0 + rnd.randrange(4, 12)):
                light(px, x, y, t)
    return img, px, rnd


def courses(seed, rgb, mortar, amount, rows, bevel=0.16, pits=10, flecks=8):
    """Masonry: rows is a list of (top y, height, joint columns). Mortar under each course and in each joint,
    a lit upper arris and a shaded lower one. Tiles."""
    img, px, rnd = base(seed, rgb, amount, pits, flecks)
    for (y0, h, joints) in rows:
        for x in range(16):
            put(px, x, y0 + h - 1, noisy(mortar, rnd, 4))
            light(px, x, y0, bevel)
            shade(px, x, y0 + h - 2, bevel * 0.8)
        for jx in joints:
            for y in range(y0, y0 + h - 1):
                put(px, jx, y, noisy(mortar, rnd, 4))
                light(px, jx + 1, y, bevel * 0.7)
                shade(px, jx - 1, y, bevel * 0.6)
    # Chipped arrises at a few joint corners.
    for _ in range(4):
        y0, h, joints = rows[rnd.randrange(len(rows))]
        if joints:
            shade(px, rnd.choice(joints) + rnd.choice((-1, 1)), y0 + rnd.choice((0, h - 2)), 0.3)
    return img, px, rnd


def crack(px, rnd, start, steps, dark=0.5, edge=0.18):
    """A meandering hairline crack with a pale lip; wraps so it tiles."""
    x, y = start
    for _ in range(steps):
        shade(px, x, y, dark)
        light(px, x + 1, y, edge)
        dx, dy = rnd.choice(((1, 1), (0, 1), (1, 0), (-1, 1), (1, 1)))
        x += dx
        y += dy


def cylinder(px, t=0.28):
    """Rounds a column face: darker toward the left and right edges."""
    for x in range(16):
        k = ((x - 7.5) / 7.5) ** 2
        for y in range(16):
            shade(px, x, y, t * k)


def disc(px, cx, cy, r, rgb, rnd=None, amount=0, ring=None):
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d <= r:
                put(px, x, y, noisy(rgb, rnd, amount) if rnd else rgb)
                if ring and d > r - 1.0:
                    shade(px, x, y, ring)


# ---------------------------------------------------------------- metal

STEEL = (128, 132, 138)
SEAM = (82, 85, 91)
RUST = (150, 86, 46)


def plating(seed, rgb, seam, rivet, amount=4):
    """Hull panels: a horizontal seam, offset vertical seams, rivets beside them."""
    img, px, rnd = metal(seed, rgb, amount)
    for x in range(16):
        put(px, x, 7, noisy(seam, rnd, 3))
        put(px, x, 15, noisy(seam, rnd, 3))
        light(px, x, 8, 0.14)
        light(px, x, 0, 0.14)
    for (sx, y0, y1) in ((10, 0, 7), (4, 8, 15)):
        for y in range(y0, y1):
            put(px, sx, y, noisy(seam, rnd, 3))
            light(px, sx + 1, y, 0.1)
        for y in range(y0 + 1, y1, 3):
            put(px, sx - 2, y, rivet)
            shade(px, sx - 2, y + 1, 0.3)
            put(px, sx + 2, y + 1, rivet)
            shade(px, sx + 2, y + 2, 0.3)
    return img, px, rnd


def durasteel_plating():
    img, px, rnd = plating(601, STEEL, SEAM, (176, 180, 186))
    for _ in range(4):
        x = rnd.randrange(16)
        for y in range(8, 8 + rnd.randrange(2, 6)):
            shade(px, x, y, 0.1)
    return img


def durasteel_deck():
    """Darker deck plate with a raised tread of short diagonal ridges and a plate seam at the edges."""
    img, px, rnd = metal(602, (88, 92, 98), 3)
    for j in range(4):
        for i in range(4):
            cx, cy = 1 + 4 * i, 1 + 4 * j
            if (i + j) % 2 == 0:
                cells = ((cx, cy + 1), (cx + 1, cy))
                low = (cx + 1, cy + 1)
            else:
                cells = ((cx, cy), (cx + 1, cy + 1))
                low = (cx, cy + 1)
            for (x, y) in cells:
                light(px, x, y, 0.28)
            shade(px, *low, 0.18)
    for i in range(16):
        put(px, i, 15, noisy((54, 56, 60), rnd, 3))
        put(px, 15, i, noisy((54, 56, 60), rnd, 3))
        light(px, i, 0, 0.1)
        light(px, 0, i, 0.1)
    return img


def durasteel_grate():
    """Heavy grating: a frame and crossing bars, see-through between them (cutout)."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    rnd = random.Random(603)
    bar = (66, 69, 74)
    rows = {0, 5, 10, 15}
    cols = {0, 5, 10, 15}
    for y in range(16):
        for x in range(16):
            if y in rows or x in cols:
                c = noisy(bar, rnd, 4)
                if y in rows and x not in cols:
                    c = mix(c, (255, 255, 255), 0.12) if y != 15 else c
                if x in cols and y not in rows:
                    c = mix(c, (0, 0, 0), 0.12)
                put(px, x, y, c)
    for y in (0, 5, 10):
        for x in (0, 5, 10):
            put(px, x, y, (96, 100, 106))
    return img


def durasteel_beam_side():
    """An I-beam along its axis: lit flanges at the edges, the recessed web between with lightening holes."""
    img, px, rnd = metal(604, (120, 124, 130), 4)
    for y in range(16):
        for x in range(3, 13):
            shade(px, x, y, 0.3)
        shade(px, 3, y, 0.25)
        light(px, 12, y, 0.08)
        light(px, 0, y, 0.12)
        shade(px, 2, y, 0.12)
        light(px, 13, y, 0.12)
        shade(px, 15, y, 0.12)
    for cy in (3.5, 11.5):
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - cy)
                if d < 2.3:
                    put(px, x, y, (34, 36, 40))
                elif d < 3.0 and y > cy:
                    light(px, x, y, 0.18)
                elif d < 3.0:
                    shade(px, x, y, 0.2)
    for y in (1, 5, 9, 13):
        put(px, 1, y, (176, 180, 186))
        put(px, 14, y, (176, 180, 186))
    return img


def durasteel_beam_end():
    """The beam's cut end: an I in bright cut metal on the dark of its flanks."""
    img, px, rnd = base(605, (44, 46, 50), 3, pits=0, flecks=0)
    cut = (150, 154, 160)
    for y in range(16):
        for x in range(16):
            if y <= 2 or y >= 13 or 6 <= x <= 9:
                put(px, x, y, noisy(cut, rnd, 5))
    for x in range(16):
        light(px, x, 0, 0.15)
        shade(px, x, 2, 0.18)
        light(px, x, 13, 0.12)
        shade(px, x, 15, 0.2)
    for y in range(3, 13):
        light(px, 6, y, 0.1)
        shade(px, 9, y, 0.15)
        shade(px, 5, y, 0.3)
    return img


PIPES = ((1, (142, 146, 152)), (6, (168, 108, 66)), (11, (116, 126, 140)))


def durasteel_pipes_side():
    """Three conduits along the axis (steel, copper, blued steel) with clamp bands."""
    img, px, rnd = base(606, (36, 38, 42), 3, pits=0, flecks=0)
    profile = (0.55, 1.0, 1.18, 0.75)
    for (x0, rgb) in PIPES:
        for i, k in enumerate(profile):
            for y in range(16):
                put(px, x0 + i, y, noisy(tuple(c * k for c in rgb), rnd, 3))
    for y0 in (3, 11):
        for x in range(16):
            put(px, x, y0, noisy((72, 74, 80), rnd, 3))
            put(px, x, y0 + 1, noisy((58, 60, 66), rnd, 3))
            light(px, x, y0, 0.12)
        for bx in (0, 5, 10, 15):
            put(px, bx, y0, (150, 152, 158))
    for y in range(6, 9):
        put(px, 13, y, (214, 176, 52))
    return img


def durasteel_pipes_end():
    """The bundle's end: three hollow pipe mouths and four small conduits between them."""
    img, px, rnd = base(607, (34, 36, 40), 3, pits=0, flecks=0)
    for (x0, rgb) in PIPES:
        cx = x0 + 1.5
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - cx, y - 7.5)
                if d <= 2.4:
                    put(px, x, y, noisy(rgb, rnd, 4))
                    if d <= 1.1:
                        put(px, x, y, (16, 16, 18))
                    elif y < 7.5:
                        light(px, x, y, 0.15)
    for (cx, cy) in ((4.5, 2.5), (10.5, 2.5), (4.5, 12.5), (10.5, 12.5)):
        for y in range(16):
            for x in range(16):
                if math.hypot(x - cx, y - cy) <= 1.3:
                    put(px, x, y, noisy((100, 104, 110), rnd, 4))
    return img


def durasteel_vent():
    """A plating frame around angled vent slats."""
    img, px, rnd = metal(608, STEEL, 4)
    for i in range(16):
        put(px, i, 0, SEAM)
        put(px, 0, i, SEAM)
        light(px, i, 1, 0.14)
        light(px, 1, i, 0.14)
        shade(px, i, 15, 0.2)
        shade(px, 15, i, 0.2)
    for y in range(3, 14):
        for x in range(3, 14):
            if y % 2 == 1:
                put(px, x, y, noisy((150, 154, 160), rnd, 3))
            else:
                put(px, x, y, noisy((30, 32, 36), rnd, 3))
    for (x, y) in ((2, 2), (14, 2), (2, 14), (14, 14)):
        put(px, x, y, (180, 184, 190))
    return img


def rusted_plating():
    """Scavenged plating gone to rust: patches round the rivets and streaks running down."""
    img, px, rnd = plating(609, (118, 108, 100), (74, 68, 64), (160, 150, 140), 6)
    blobs = [(rnd.uniform(0, 16), rnd.uniform(0, 16), rnd.uniform(2.0, 4.0)) for _ in range(5)]
    for y in range(16):
        for x in range(16):
            for (bx, by, r) in blobs:
                dx = min(abs(x - bx), 16 - abs(x - bx))
                dy = min(abs(y - by), 16 - abs(y - by))
                d = math.hypot(dx, dy) + rnd.uniform(-0.8, 0.8)
                if d < r:
                    shade(px, x, y, 0.55 if d < r - 1.2 else 0.3, RUST)
    for _ in range(7):
        x, y = rnd.randrange(16), rnd.randrange(16)
        for k in range(rnd.randrange(3, 7)):
            shade(px, x, y + k, 0.4 - k * 0.05, (110, 60, 34))
    return img


def republic_plating():
    """Old Republic hull: off-white panels, a recessed access hatch, a navy trim line and a red marker."""
    img, px, rnd = metal(610, (210, 206, 196), 4)
    for i in range(16):
        put(px, i, 0, (156, 154, 148))
        put(px, 0, i, (156, 154, 148))
        light(px, i, 1, 0.12)
        light(px, 1, i, 0.12)
    for x in range(16):
        put(px, x, 11, noisy((40, 62, 112), rnd, 4))
        put(px, x, 12, noisy((48, 72, 126), rnd, 4))
        shade(px, x, 13, 0.12)
        light(px, x, 10, 0.08)
    for x in range(3, 9):
        shade(px, x, 3, 0.2)
        light(px, x, 8, 0.1)
    for y in range(3, 9):
        shade(px, 3, y, 0.2)
        light(px, 8, y, 0.1)
    for x in (11, 12, 13):
        put(px, x, 4, (156, 42, 38))
    return img


def imperial_plating():
    """Imperial gunmetal: dark panels, vent slits and one thin red line."""
    img, px, rnd = metal(611, (52, 53, 58), 3)
    for i in range(16):
        put(px, i, 0, (30, 30, 34))
        put(px, 0, i, (30, 30, 34))
        light(px, i, 1, 0.07)
        light(px, 1, i, 0.07)
    for x in range(16):
        put(px, x, 12, noisy((152, 30, 32), rnd, 5))
        shade(px, x, 13, 0.15)
    for y in (4, 6, 8):
        for x in range(4, 12):
            put(px, x, y, (22, 22, 26))
            light(px, x, y + 1, 0.08)
    for (x, y) in ((2, 2), (13, 2), (2, 14), (13, 14)):
        put(px, x, y, (92, 94, 100))
    return img


def light_panel(seed, glow):
    """A recessed fitting: a bevelled metal frame round a diffuser with two bright strips."""
    img, px, rnd = metal(seed, (96, 100, 106), 3)
    for i in range(16):
        light(px, i, 0, 0.16)
        light(px, 0, i, 0.16)
        shade(px, i, 15, 0.2)
        shade(px, 15, i, 0.2)
    dim = tuple(c * 0.82 for c in glow)
    for y in range(2, 14):
        for x in range(2, 14):
            put(px, x, y, noisy(dim, rnd, 3))
    for y in (4, 5, 6, 9, 10, 11):
        core = 0.55 if y in (5, 10) else 0.28
        for x in range(3, 13):
            put(px, x, y, mix(glow, (255, 255, 255), core))
    for i in range(2, 14):
        shade(px, i, 2, 0.18)
        shade(px, 2, i, 0.18)
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        put(px, x, y, (150, 154, 160))
    return img


def console_front():
    """A control console: a lit readout screen, a row of coloured keys and a grille."""
    img, px, rnd = metal(612, (68, 72, 80), 3)
    for y in range(2, 9):
        for x in range(2, 14):
            put(px, x, y, noisy((12, 30, 36), rnd, 3))
    wave = [6, 6, 5, 4, 4, 5, 6, 7, 7, 6, 5, 4]
    for i, y in enumerate(wave):
        put(px, 2 + i, y, (90, 226, 196))
    for x in range(3, 8):
        put(px, x, 3, (150, 236, 170))
    for x in range(9, 12):
        put(px, x, 3, (240, 190, 90))
    put(px, 12, 3, (240, 190, 90))
    for i in range(1, 15):
        shade(px, i, 1, 0.25)
        light(px, i, 9, 0.12)
    for (x0, rgb) in ((2, (212, 62, 52)), (5, (240, 182, 72)), (8, (84, 204, 112)), (11, (84, 152, 242))):
        for x in (x0, x0 + 1):
            put(px, x, 11, rgb)
            put(px, x, 12, mix(rgb, (0, 0, 0), 0.3))
    for x in range(3, 13):
        if x % 2 == 0:
            put(px, x, 14, (30, 32, 36))
    return img


def console_side():
    img, px, rnd = metal(613, (68, 72, 80), 3)
    for y in range(16):
        put(px, 7, y, (46, 48, 54))
        light(px, 8, y, 0.08)
    for y in (10, 12):
        for x in range(2, 6):
            put(px, x, y, (30, 32, 36))
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        put(px, x, y, (120, 124, 130))
    return img


def console_top():
    """The desk of the console: a keyboard of small keys, two of them lit."""
    img, px, rnd = metal(614, (68, 72, 80), 3)
    for y in range(3, 13):
        for x in range(2, 14):
            if y % 2 == 1 and x % 3 != 1:
                put(px, x, y, noisy((112, 116, 124), rnd, 4))
            elif y % 2 == 0:
                put(px, x, y, (40, 42, 48))
    put(px, 3, 5, (240, 190, 90))
    put(px, 4, 5, (240, 190, 90))
    put(px, 12, 9, (90, 226, 196))
    put(px, 11, 9, (90, 226, 196))
    return img


def holo_screen_front():
    """A wall display: a dark frame round a blue holo glow with scan lines and a planet outline."""
    img, px, rnd = metal(615, (38, 42, 50), 3)
    for y in range(2, 14):
        for x in range(2, 14):
            t = (y - 2) / 11.0
            c = mix((22, 54, 100), (16, 38, 74), t)
            if y % 2 == 0:
                c = mix(c, (90, 170, 255), 0.12)
            put(px, x, y, noisy(c, rnd, 3))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if 3.4 <= d <= 4.3 and 2 <= x <= 13 and 2 <= y <= 13:
                put(px, x, y, (140, 206, 255))
    for x in range(4, 12):
        put(px, x, 8, mix(px[x, 8][:3], (120, 190, 255), 0.5))
    for i in range(2, 14):
        light(px, i, 2, 0.1)
        put(px, i, 1, (24, 26, 32))
    put(px, 12, 12, (255, 255, 255))
    return img


def holo_screen_side():
    img, px, rnd = metal(616, (44, 48, 56), 3)
    for y in range(16):
        put(px, 0, y, (30, 32, 38))
    put(px, 12, 3, (90, 170, 255))
    return img


def holo_screen_top():
    img, px, rnd = metal(617, (44, 48, 56), 3)
    for x in range(3, 13):
        if x % 2 == 1:
            put(px, x, 7, (24, 26, 32))
            put(px, x, 8, (24, 26, 32))
    return img


def crate_side(seed, body, frame, accent, mode):
    """A ribbed cargo container: a frame, vertical ribs and the livery (hazard corners or a band)."""
    img, px, rnd = metal(seed, body, 4)
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            put(px, x, y, noisy(frame, rnd, 3))
        light(px, i, 1, 0.1)
        shade(px, i, 14, 0.15)
    for x in range(1, 15):
        for y in range(2, 14):
            if x % 3 == 0:
                shade(px, x, y, 0.22)
            elif x % 3 == 1:
                light(px, x, y, 0.1)
    if mode == "hazard":
        for y in range(16):
            for x in range(16):
                for (cx, cy) in ((0, 0), (15, 0), (0, 15), (15, 15)):
                    if abs(x - cx) + abs(y - cy) <= 4:
                        put(px, x, y, accent if ((x + y) // 2) % 2 == 0 else (32, 32, 34))
        for y in range(6, 10):
            for x in range(5, 11):
                put(px, x, y, (204, 204, 198))
        for x in (6, 7, 9):
            put(px, x, 7, (60, 60, 62))
    else:
        rows = (6, 7, 8, 9) if mode == "band" else (7, 8)
        for y in rows:
            for x in range(1, 15):
                put(px, x, y, noisy(accent, rnd, 4))
        for x in range(1, 15):
            shade(px, x, rows[-1] + 1, 0.15)
    return img


def crate_top(seed, body, frame, accent, mode):
    img, px, rnd = metal(seed, body, 4)
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            put(px, x, y, noisy(frame, rnd, 3))
    for i in range(2, 14):
        shade(px, i, 2, 0.12)
        shade(px, 2, i, 0.12)
        light(px, i, 13, 0.08)
        light(px, 13, i, 0.08)
    if mode == "hazard":
        for (cx, cy) in ((1, 1), (14, 1), (1, 14), (14, 14)):
            put(px, cx, cy, accent)
        for y in range(6, 10):
            for x in range(6, 10):
                put(px, x, y, accent if (x + y) % 2 == 0 else (32, 32, 34))
    else:
        for y in range(5, 11):
            for x in range(5, 11):
                put(px, x, y, noisy(accent, rnd, 4))
        for i in range(5, 11):
            light(px, i, 5, 0.12)
    return img


CRATES = {
    "cargo_crate": ((118, 122, 128), (72, 74, 80), (220, 180, 40), "hazard"),
    "cargo_crate_republic": ((212, 210, 202), (150, 150, 146), (44, 70, 130), "band"),
    "cargo_crate_imperial": ((48, 48, 52), (26, 26, 30), (152, 32, 34), "line"),
}


# ---------------------------------------------------------------- masonry

def ruin_bricks():
    """Sand dressed stone of the ruin_stone family, laid in smaller courses, one hairline crack."""
    rows = [(0, 4, (2, 10)), (4, 4, (6, 14)), (8, 4, (0, 9)), (12, 4, (4, 12))]
    img, px, rnd = courses(620, (198, 184, 150), (150, 138, 110), 7, rows)
    crack(px, rnd, (11, 5), 5, dark=0.35, edge=0.12)
    return img


def ruin_pillar_side():
    """A fluted drum: shallow flutes, a bedding joint at the foot and rounded flanks."""
    img, px, rnd = base(621, (200, 188, 156), 6, pits=6, flecks=6)
    for x in range(16):
        for y in range(16):
            if x % 3 == 0:
                shade(px, x, y, 0.2)
            elif x % 3 == 1:
                light(px, x, y, 0.1)
        put(px, x, 15, noisy((160, 148, 120), rnd, 4))
        light(px, x, 0, 0.15)
    cylinder(px, 0.25)
    return img


def ruin_pillar_end():
    img, px, rnd = base(622, (176, 164, 134), 6, pits=4, flecks=4)
    disc(px, 7.5, 7.5, 7.4, (200, 188, 156), rnd, 6, ring=0.25)
    for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8)):
        put(px, x, y, (110, 100, 80))
    return img


def tomb_bricks():
    """Black basalt blocks with faint red-brown seams."""
    rows = [(0, 8, (5, 13)), (8, 8, (1, 9))]
    img, px, rnd = courses(623, (46, 42, 45), (90, 56, 48), 5, rows, bevel=0.12, pits=6, flecks=10)
    for _ in range(5):
        shade(px, rnd.randrange(16), rnd.randrange(16), 0.25, (120, 64, 50))
    return img


def tomb_pillar_side():
    """A carved black column: three grooves and a zigzag band with old red pigment."""
    img, px, rnd = base(624, (46, 42, 46), 5, pits=4, flecks=8)
    for y in range(16):
        for gx in (3, 7, 11):
            shade(px, gx, y, 0.35)
            light(px, gx + 1, y, 0.1)
        put(px, 0, y, mix(px[0, y][:3], (0, 0, 0), 0.2))
    for x in range(16):
        light(px, x, 5, 0.12)
        shade(px, x, 10, 0.25)
        zy = 7 + (1 if (x // 2) % 2 else 0) - (1 if x % 4 == 0 else 0)
        put(px, x, zy, (124, 52, 42))
        put(px, x, zy + 1, (78, 36, 32))
    cylinder(px, 0.2)
    return img


def tomb_pillar_end():
    img, px, rnd = base(625, (32, 29, 32), 4, pits=2, flecks=4)
    disc(px, 7.5, 7.5, 7.4, (52, 48, 52), rnd, 4, ring=0.3)
    crack(px, rnd, (5, 4), 4, dark=0.3, edge=0.08)
    return img


def watch_stone():
    """Selvaren's watch stone: big pale grey-green ashlar with a few lichen flecks."""
    rows = [(0, 8, (4, 12)), (8, 8, (0, 8))]
    img, px, rnd = courses(626, (152, 162, 150), (112, 122, 112), 6, rows)
    for _ in range(6):
        put(px, rnd.randrange(16), rnd.randrange(16), (176, 186, 138))
    return img


def ash_bricks():
    """Varkhast's ash brick: small grey bricks in rust-red mortar, some sooted."""
    rows = [(0, 4, (3, 11)), (4, 4, (7, 15)), (8, 4, (3, 11)), (12, 4, (7, 15))]
    img, px, rnd = courses(627, (114, 112, 110), (130, 64, 46), 6, rows, bevel=0.12)
    for _ in range(2):
        y0 = rnd.choice((0, 4, 8, 12))
        x0 = rnd.randrange(16)
        for y in range(y0, y0 + 3):
            for x in range(x0, x0 + 6):
                shade(px, x, y, 0.25)
    return img


def adobe():
    """Ghessar's sand-plaster render: smooth, trowel strokes, a hairline crack and a damp foot."""
    img, px, rnd = base(628, (206, 176, 126), 4, pits=4, flecks=6)
    for y in range(16):
        if rnd.random() < 0.45:
            x0 = rnd.randrange(16)
            t = rnd.uniform(0.04, 0.09)
            for x in range(x0, x0 + rnd.randrange(5, 12)):
                (light if rnd.random() < 0.5 else shade)(px, x, y, t)
    x, y = 4, 3
    for _ in range(7):
        shade(px, x, y, 0.22)
        x += rnd.choice((0, 1, 1))
        y += 1
    for x in range(16):
        for y in (14, 15):
            shade(px, x, y, 0.08 if y == 14 else 0.12, (120, 90, 60))
    return img


def adobe_bricks():
    """Rough mud brick: uneven courses, deep joints and straw flecks."""
    rows = [(0, 5, (4, 12)), (5, 6, (0, 8)), (11, 5, (5, 13))]
    img, px, rnd = courses(629, (154, 116, 80), (118, 88, 60), 9, rows, bevel=0.1)
    for _ in range(8):
        x, y = rnd.randrange(16), rnd.randrange(16)
        put(px, x, y, (204, 178, 118))
    return img


def salt_bricks():
    """White crystalline blocks with faint blue-grey seams and sparkles."""
    rows = [(0, 8, (6, 14)), (8, 8, (2, 10))]
    img, px, rnd = courses(630, (232, 232, 226), (190, 196, 206), 6, rows, bevel=0.08, pits=4, flecks=0)
    for _ in range(4):
        x, y = rnd.randrange(16), rnd.randrange(16)
        for k in range(3):
            shade(px, x + k, y + k, 0.08, (150, 170, 200))
    for _ in range(7):
        put(px, rnd.randrange(16), rnd.randrange(16), (255, 255, 255))
    return img


def stormstone():
    """Tyrvann basalt: dark blue-grey, mottled, with pale wet glints."""
    img, px, rnd = base(631, (64, 70, 84), 7, pits=14, flecks=6)
    for _ in range(3):
        x, y = rnd.randrange(16), rnd.randrange(16)
        for k in range(rnd.randrange(4, 8)):
            light(px, x + k, y + k // 2, 0.1)
    for _ in range(4):
        put(px, rnd.randrange(16), rnd.randrange(16), (150, 164, 186))
    return img


def stormstone_bricks():
    rows = [(0, 8, (2, 10)), (8, 8, (6, 14))]
    img, px, rnd = courses(632, (70, 76, 90), (40, 44, 54), 6, rows)
    for _ in range(3):
        put(px, rnd.randrange(16), rnd.randrange(16), (150, 164, 186))
    return img


def pale_tiles():
    """Clean white-grey floor tiles of Republic interiors: four tiles with grout and a few scratches."""
    img, px, rnd = base(633, (216, 216, 212), 3, pits=2, flecks=2)
    for (tx, ty) in ((0, 0), (8, 0), (0, 8), (8, 8)):
        tone = rnd.uniform(-6, 6)
        for y in range(ty, ty + 8):
            for x in range(tx, tx + 8):
                put(px, x, y, tuple(clamp(c + tone) for c in px[x, y][:3]))
        for i in range(7):
            light(px, tx + i, ty, 0.12)
            light(px, tx, ty + i, 0.12)
    for i in range(16):
        put(px, i, 7, (170, 170, 166))
        put(px, i, 15, (170, 170, 166))
        put(px, 7, i, (170, 170, 166))
        put(px, 15, i, (170, 170, 166))
    for _ in range(2):
        x, y = rnd.randrange(16), rnd.randrange(16)
        for k in range(3):
            shade(px, x + k, y, 0.1)
    return img


def canvas(seed, rgb):
    """Tent cloth: a plain weave with a darker thread every four rows and a stitched seam."""
    img, px, rnd = base(seed, rgb, 3, pits=0, flecks=0)
    for y in range(16):
        for x in range(16):
            if (x + y) % 2 == 0:
                light(px, x, y, 0.06)
            else:
                shade(px, x, y, 0.06)
        if y % 4 == 3:
            for x in range(16):
                shade(px, x, y, 0.08)
    for y in range(16):
        shade(px, 15, y, 0.12)
        if y % 3 == 0:
            put(px, 14, y, mix(rgb, (255, 255, 255), 0.25))
    return img


# ---------------------------------------------------------------- decals

def scorch_mark():
    """A blast scorch on the ground: black at the heart, soft transparent edges, flecks of soot."""
    rnd = random.Random(640)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5) + rnd.uniform(-0.9, 0.9)
            if d < 3.0:
                px[x, y] = noisy((22, 20, 18), rnd, 4) + (240,)
            elif d < 6.8:
                t = (d - 3.0) / 3.8
                px[x, y] = noisy(mix((22, 20, 18), (64, 54, 44), t), rnd, 4) + (clamp(240 * (1 - t) ** 1.4),)
    for _ in range(10):
        a = rnd.uniform(0, 2 * math.pi)
        r = rnd.uniform(5.0, 7.2)
        x, y = int(7.5 + r * math.cos(a)), int(7.5 + r * math.sin(a))
        if 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = (30, 28, 26, 200)
    return img


def debris():
    """Scattered scrap and grit on transparent ground (cutout)."""
    rnd = random.Random(641)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    pieces = [((2, 3), (3, 1), (124, 128, 134)), ((10, 2), (2, 2), (100, 104, 110)), ((5, 10), (1, 3), (140, 82, 46)),
              ((12, 11), (3, 2), (118, 122, 128)), ((8, 6), (2, 1), (88, 92, 98))]
    for ((x0, y0), (w, h), rgb) in pieces:
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                put(px, x, y, noisy(rgb, rnd, 6))
        for x in range(x0, x0 + w):
            put(px, x, y0 + h, mix(rgb, (0, 0, 0), 0.5))
    for k in range(4):
        put(px, 2 + k, 13 - k, (84, 88, 94))
    for _ in range(18):
        x, y = rnd.randrange(16), rnd.randrange(16)
        if px[x, y][3] == 0:
            put(px, x, y, rnd.choice(((92, 88, 84), (150, 146, 140), (70, 66, 62))))
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
        "imperial_plating": imperial_plating(),
        "light_panel": light_panel(618, (226, 238, 252)),
        "light_panel_amber": light_panel(619, (255, 196, 104)),
        "light_panel_red": light_panel(650, (228, 66, 54)),
        "console_front": console_front(),
        "console_side": console_side(),
        "console_top": console_top(),
        "holo_screen_front": holo_screen_front(),
        "holo_screen_side": holo_screen_side(),
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

def tex_ref(name):
    return f"{NS}:block/build/{name}"


def model_ref(name):
    return f"{NS}:block/build/{name}"


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
        models[block_id] = {"parent": "minecraft:block/orientable", "textures": {
            "front": tex_ref(block_id + "_front"), "side": tex_ref(block_id + "_side"), "top": tex_ref(block_id + "_top")}}
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
        t = {"bottom": tex_ref(base_tex), "side": tex_ref(base_tex), "top": tex_ref(base_tex)}
        models[block_id] = {"parent": "minecraft:block/stairs", "textures": dict(t)}
        models[block_id + "_inner"] = {"parent": "minecraft:block/inner_stairs", "textures": dict(t)}
        models[block_id + "_outer"] = {"parent": "minecraft:block/outer_stairs", "textures": dict(t)}
        state = stairs_variants(me)
    elif shape == "slab":
        t = {"bottom": tex_ref(base_tex), "side": tex_ref(base_tex), "top": tex_ref(base_tex)}
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
