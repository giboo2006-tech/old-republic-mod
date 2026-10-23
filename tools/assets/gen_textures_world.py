#!/usr/bin/env python3
"""Generates the world feature textures (PLACEHOLDER art, original, deterministic).

Writes into src/main/resources/assets/oldrepublic/textures/:
  block/kyber_cluster.png                       16x16  pale blue crystal shards (cross model, cutout)
  block/unstable_crystal_cluster.png            16x16  cracked violet shards
  block/unstable_crystal_cluster_charging.png   16x16  the same shards blazing white-violet (the tell)
  block/crystal_veined_stone.png                16x16  grey stone with thin glowing kyber veins
  block/resonant_seal.png                       16x16  translucent cyan shimmer with a rune ring
  block/meditation_stone_top.png                16x16  polished dark stone, inlaid crystal rings
  block/meditation_stone_side.png               16x16  dark stone bands with a carved line and glyph dots
  block/meditation_stone_bottom.png             16x16  plain dark stone
  particle/crystal_mote_0..1.png                8x8    soft round motes (white, tinted in code)
  particle/crystal_glint_0..3.png               8x8    four-point star twinkle frames
  particle/crystal_spark_0..1.png               8x8    hot spark points
  particle/shadow_wisp_0..3.png                 8x8    soft smoke puffs (white, tinted dark in code)
  entity/shadow_echo.png                        64x64  Shadow Echo skin: dim violet figure with rim light (player layout)

Milestone 3 (spec 3.3, 3.5, 13.2), each function seeded on its own so the textures above stay byte-identical:
  block/ruin_stone{,_carved,_cracked,_mossy}.png          16x16  pale dressed ashlar of Watchers' Rest, fluted, cracked, mossy
  block/tomb_stone{,_carved,_cracked}.png                 16x16  dark polished tomb ashlar, zigzag band with old pigment, cracked
  block/wreck_plating{,_scorched}.png, wreck_conduit.png  16x16  riveted durasteel plates, a soot bloom, an exposed conduit
  block/republic_panel.png, imperial_panel.png            16x16  off-white panel with navy band; dark plate with crimson trim
  block/veiled_stone_<skin>_revealed.png                  16x16  the decor face with dust-lit hairline seams (ruin, tomb, wreck)
  block/counterweight_stone_<skin>_{side,top,front}.png   16x16  banded weight stone with a worn push plate
  block/step_stone{,_top}.png                             16x16  ledge stone and its worn tread
  block/echo_urn_side_<0..3>.png, echo_urn_top.png        16x16  fired clay urns with 1 to 4 notches on the shoulder
  block/urn_keeper_{front,front_solved,side,top}.png      16x16  tomb stone with an urn in relief; solved, the cut glows
  block/pull_anchor_front{,_strain,_released}.png, _side  16x16  iron ring on a staple in ruin stone
  block/collapsed_rubble.png                              16x16  jammed rocks with dark voids
  block/puzzle_seal_<skin>.png                            16x16  slab doors of the three skins
  block/holocron_pedestal_<kind>_{side,side_ready,top}    16x16  jedi, sith, wayfarer stone with inlay that lights when ready
  block/tomb_vent_top{,_tell,_puff}.png, tomb_vent_side   16x16  iron grate over a shaft: idle, grit in the bars, haze
  block/lumen_moss.png                                    16x16  teal moss mat with pale lumen points
  block/funerary_urn_{side,top}.png                       16x16  sealed clay urn with a waxed band
  block/wreck_salvage{,_top}.png                          16x16  torn, scratched plating
  block/bounty_terminal_<army>_{front,side}.png, _top     16x16  terminal casing and screen per army (part B's block)
  block/echo_cairn_{stone,satchel}.png                    16x16  stacked stones; canvas satchel (part B's block)
  block/site_spawner.png                                  16x16  dust specks (particle texture only; never rendered)
  particle/light_mote_0..1, leaf_fall_0..2, tomb_dust_0..2, veil_dust_0..1, vent_grit_0..2, miasma_0..3,
  particle/conduit_spark_0..1                             8x8    white with alpha, tinted in code
  entity/banner/republic_chevron.png                      64x64  banner pattern mask (front 1,1 and back 22,1, 20x40)
  entity/shield/republic_chevron.png                      64x64  shield pattern mask (face 1,1, 12x22)

Run from the repository root: python3 tools/assets/gen_textures_world.py
"""
import math
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "assets", "oldrepublic", "textures")


def out(rel, img):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    print("wrote", os.path.normpath(path))


def clamp(v):
    return max(0, min(255, int(round(v))))


def mix(a, b, t):
    return tuple(clamp(x + (y - x) * t) for x, y in zip(a, b))


def noisy(rgb, rnd, amount):
    return tuple(clamp(c + rnd.uniform(-amount, amount)) for c in rgb)


# ---------------------------------------------------------------- crystal shards
# (base x, half width at base, height, lean in pixels over the full height)
SHARDS = [
    (7.5, 2.4, 14, 0.0),
    (4.0, 1.8, 10, -2.2),
    (11.5, 1.9, 11, 2.0),
    (2.0, 1.2, 6, -2.5),
    (13.8, 1.2, 7, 2.2),
    (9.6, 1.3, 8, 1.0),
]


def shards(seed, light, mid, dark, cracks=None, crack_color=None):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    # Draw back to front: small side shards first, the tall centre shard last.
    for (bx, hw, height, lean) in sorted(SHARDS, key=lambda s: s[2]):
        for row in range(height):
            y = 15 - row
            t = row / max(1, height - 1)
            cx = bx + lean * t
            w = hw * (1.0 - t * 0.85)
            if w < 0.35:
                w = 0.35
            x0 = int(math.floor(cx - w + 0.5))
            x1 = int(math.floor(cx + w - 0.5))
            for x in range(x0, x1 + 1):
                if not (0 <= x < 16):
                    continue
                u = (x - (cx - w)) / max(0.01, 2 * w)  # 0 at the left edge, 1 at the right
                if u < 0.3:
                    c = mix(light, mid, u / 0.3)
                elif u < 0.7:
                    c = mid
                else:
                    c = mix(mid, dark, (u - 0.7) / 0.3)
                c = mix(c, light, 0.25 * t)  # tips catch more light
                px[x, y] = noisy(c, rnd, 6) + (255,)
        # Tip highlight
        tip_x = int(round(bx + lean))
        tip_y = 15 - (height - 1)
        if 0 <= tip_x < 16 and 0 <= tip_y < 16:
            px[tip_x, tip_y] = mix(light, (255, 255, 255), 0.6) + (255,)
    if cracks:
        for (x, y) in cracks:
            if px[x, y][3] > 0:
                px[x, y] = crack_color + (255,)
    return img


def kyber_cluster():
    out("block/kyber_cluster.png", shards(11, (205, 245, 255), (110, 200, 240), (45, 115, 175)))


UNSTABLE_CRACKS = [(7, 4), (8, 5), (7, 6), (8, 8), (7, 9), (4, 9), (5, 11), (11, 8), (12, 10), (11, 12), (9, 12), (8, 13)]


def unstable_clusters():
    out("block/unstable_crystal_cluster.png",
        shards(23, (236, 190, 255), (170, 90, 225), (85, 30, 135), UNSTABLE_CRACKS, (255, 236, 255)))
    out("block/unstable_crystal_cluster_charging.png",
        shards(23, (255, 245, 255), (240, 170, 255), (175, 85, 230), UNSTABLE_CRACKS, (255, 255, 255)))


# ---------------------------------------------------------------- stone blocks
def stone_base(seed, base, amount):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = noisy(base, rnd, amount) + (255,)
    # A few darker pits and lighter flecks, like vanilla stone.
    for _ in range(10):
        x, y = rnd.randrange(16), rnd.randrange(16)
        px[x, y] = mix(px[x, y][:3], (0, 0, 0), 0.25) + (255,)
    for _ in range(8):
        x, y = rnd.randrange(16), rnd.randrange(16)
        px[x, y] = mix(px[x, y][:3], (255, 255, 255), 0.15) + (255,)
    return img, px, rnd


def crystal_veined_stone():
    img, px, rnd = stone_base(31, (122, 122, 126), 10)
    vein = (80, 165, 225)
    bright = (185, 238, 255)
    # Two meandering veins that wrap across the tile edges so the texture tiles.
    for start, drift in (((0, 4), 0.55), ((0, 11), -0.45)):
        x, y = float(start[0]), float(start[1])
        for step in range(16):
            ix, iy = int(x) % 16, int(round(y)) % 16
            px[ix, iy] = noisy(vein, rnd, 10) + (255,)
            if rnd.random() < 0.35:
                px[ix, (iy + 1) % 16] = mix(vein, px[ix, (iy + 1) % 16][:3], 0.5) + (255,)
            x += 1
            y += drift + rnd.uniform(-0.6, 0.6)
    for (x, y) in ((3, 4), (12, 7), (7, 12)):
        px[x, y] = bright + (255,)
    out("block/crystal_veined_stone.png", img)


def resonant_seal():
    rnd = random.Random(41)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            streak = 0.5 + 0.5 * math.sin(x * 1.3 + y * 0.35)
            c = mix((70, 175, 215), (150, 230, 250), streak * 0.6)
            a = 95 + int(streak * 40) + rnd.randrange(12)
            px[x, y] = noisy(c, rnd, 6) + (a,)
    # Rune ring in the centre.
    for i in range(48):
        ang = i / 48 * 2 * math.pi
        x = int(round(7.5 + 4.6 * math.cos(ang)))
        y = int(round(7.5 + 4.6 * math.sin(ang)))
        px[x, y] = (205, 250, 255, 205)
    for (x, y) in ((7, 5), (8, 5), (7, 6), (8, 9), (7, 10), (8, 10), (5, 7), (10, 8)):
        px[x, y] = (225, 255, 255, 220)
    out("block/resonant_seal.png", img)


def meditation_stone():
    dark = (52, 54, 63)
    inlay = (95, 205, 240)
    # Top: polished dark stone with two inlaid rings and a centre point.
    img, px, rnd = stone_base(51, dark, 5)
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            if abs(r - 6.0) < 0.5 or abs(r - 3.2) < 0.45:
                px[x, y] = noisy(inlay, rnd, 8) + (255,)
            elif r > 7.2:
                px[x, y] = mix(px[x, y][:3], (0, 0, 0), 0.2) + (255,)
    for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8)):
        px[x, y] = (200, 245, 255, 255)
    out("block/meditation_stone_top.png", img)

    # Side: banded stone, a carved line and small glyph dots.
    img, px, rnd = stone_base(52, dark, 6)
    for x in range(16):
        px[x, 0] = mix(dark, (0, 0, 0), 0.35) + (255,)
        px[x, 9] = mix(dark, (0, 0, 0), 0.4) + (255,)
        px[x, 10] = mix(dark, (255, 255, 255), 0.08) + (255,)
        px[x, 15] = mix(dark, (0, 0, 0), 0.35) + (255,)
    for x in (2, 5, 8, 11, 14):
        px[x, 12] = noisy(inlay, rnd, 10) + (255,)
    for x in (3, 7, 12):
        px[x, 5] = noisy(inlay, rnd, 10) + (255,)
    out("block/meditation_stone_side.png", img)

    img, px, rnd = stone_base(53, mix(dark, (0, 0, 0), 0.15), 5)
    out("block/meditation_stone_bottom.png", img)


# ---------------------------------------------------------------- particles (white, tinted in code)
def soft_blob(radius, softness=1.0, cx=3.5, cy=3.5):
    img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    px = img.load()
    for y in range(8):
        for x in range(8):
            d = math.hypot(x - cx, y - cy)
            a = max(0.0, 1.0 - d / radius) ** softness
            if a > 0:
                px[x, y] = (255, 255, 255, clamp(255 * a))
    return img


def particles():
    out("particle/crystal_mote_0.png", soft_blob(3.2, 1.4))
    out("particle/crystal_mote_1.png", soft_blob(2.4, 1.1))

    for frame, arm in enumerate((1.5, 3.0, 3.8, 2.2)):
        img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
        px = img.load()
        for y in range(8):
            for x in range(8):
                dx, dy = abs(x - 3.5), abs(y - 3.5)
                core = max(0.0, 1.0 - math.hypot(dx, dy) / 1.6)
                cross = 0.0
                if dx < 0.6:
                    cross = max(cross, 1.0 - dy / arm)
                if dy < 0.6:
                    cross = max(cross, 1.0 - dx / arm)
                a = max(core, cross)
                if a > 0:
                    px[x, y] = (255, 255, 255, clamp(255 * min(1.0, a)))
        out(f"particle/crystal_glint_{frame}.png", img)

    out("particle/crystal_spark_0.png", soft_blob(1.8, 0.8))
    img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    px = img.load()
    for y in range(8):
        for x in range(8):
            d = math.hypot((x - 3.5) * 0.6, (y - 3.5) * 1.6)
            a = max(0.0, 1.0 - d / 2.0)
            if a > 0:
                px[x, y] = (255, 255, 255, clamp(255 * a))
    out("particle/crystal_spark_1.png", img)

    for frame in range(4):
        rnd = random.Random(60 + frame)
        img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
        px = img.load()
        lobes = [(3.5 + rnd.uniform(-1.2, 1.2), 3.5 + rnd.uniform(-1.2, 1.2), rnd.uniform(1.6, 2.8)) for _ in range(3)]
        spread = 1.0 + frame * 0.12
        for y in range(8):
            for x in range(8):
                a = 0.0
                for (lx, ly, lr) in lobes:
                    a = max(a, 1.0 - math.hypot(x - lx, y - ly) / (lr * spread))
                a = max(0.0, a) * (0.9 - frame * 0.12) * rnd.uniform(0.8, 1.0)
                if a > 0:
                    px[x, y] = (235, 235, 240, clamp(255 * a))
        out(f"particle/shadow_wisp_{frame}.png", img)


# ---------------------------------------------------------------- entity
def shadow_echo():
    """Player-layout skin of the Shadow Echo (always used): a dim violet figure with a pale violet
    rim light on every vertical edge and on top faces, so it reads against dark stone without any
    tint, and two faint eye points."""
    rnd = random.Random(71)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    body = (84, 66, 116)
    cloth = (66, 52, 96)
    rim = (170, 140, 220)
    # Base-layer cuboids of the 64x64 player skin: (u, v, width, height, depth, colour).
    boxes = [
        (0, 0, 8, 8, 8, body),     # head
        (16, 16, 8, 12, 4, cloth),  # body
        (40, 16, 4, 12, 4, cloth),  # right arm
        (0, 16, 4, 12, 4, cloth),   # right leg
        (16, 48, 4, 12, 4, cloth),  # left leg
        (32, 48, 4, 12, 4, cloth),  # left arm
    ]
    for (u, v, w, h, d, color) in boxes:
        top = (u + d, v, w, d)
        bottom = (u + d + w, v, w, d)
        sides = [(u, v + d, d, h), (u + d, v + d, w, h), (u + d + w, v + d, d, h), (u + 2 * d + w, v + d, w, h)]
        for (x0, y0, fw, fh) in [top, bottom] + sides:
            for y in range(y0, y0 + fh):
                for x in range(x0, x0 + fw):
                    px[x, y] = noisy(color, rnd, 7) + (255,)
        # Light from above: top faces are paler.
        x0, y0, fw, fh = top
        for y in range(y0, y0 + fh):
            for x in range(x0, x0 + fw):
                px[x, y] = mix(px[x, y][:3], rim, 0.45) + (255,)
        # Rim light: the vertical edges of every side face, and a soft top row.
        for (x0, y0, fw, fh) in sides:
            for y in range(y0, y0 + fh):
                for x in (x0, x0 + fw - 1):
                    px[x, y] = mix(px[x, y][:3], rim, 0.7) + (255,)
            for x in range(x0, x0 + fw):
                px[x, y0] = mix(px[x, y0][:3], rim, 0.35) + (255,)
        # Smoky streaks down the front face.
        x0, y0, fw, fh = sides[1]
        for y in range(y0 + 1, y0 + fh):
            for x in range(x0 + 1, x0 + fw - 1):
                if rnd.random() < 0.18:
                    px[x, y] = mix(px[x, y][:3], (40, 30, 60), 0.6) + (255,)
    # Darker hood shadow over the face, then the eyes (front of head is x 8..15, y 8..15).
    for y in range(8, 11):
        for x in range(9, 15):
            px[x, y] = mix(px[x, y][:3], (30, 22, 44), 0.6) + (255,)
    for (x, y) in ((10, 12), (13, 12)):
        px[x, y] = (225, 205, 255, 255)
    out("entity/shadow_echo.png", img)




# ================================================================ Milestone 3: site blocks (spec 3.3)
# Every function below has its own seeds (100 and up), so the M1 textures above stay byte-identical.
RUIN = (184, 170, 144)
RUIN_MORTAR = (132, 118, 96)
TOMB = (54, 49, 56)
TOMB_MORTAR = (28, 25, 31)
WRECK = (116, 121, 127)
WRECK_SEAM = (64, 67, 73)
RUST = (128, 80, 48)
MOSS = (88, 116, 64)
CLAY = (148, 88, 58)
LUMEN = (196, 238, 222)


def put(px, x, y, rgb, a=255):
    if 0 <= x < 16 and 0 <= y < 16:
        px[x, y] = tuple(rgb[:3]) + (a,)


def shade(px, x, y, t, toward=(0, 0, 0)):
    """Mixes the pixel toward a colour (dark by default); wraps around the tile edges."""
    x %= 16
    y %= 16
    px[x, y] = mix(px[x, y][:3], toward, t) + (px[x, y][3],)


def ashlar(seed, base, mortar, amount, joints=((3, 11), (7, 15)), course=8, bevel=0.18):
    """Two courses of dressed stone with mortar lines; joints are the vertical joint columns per course. Tiles."""
    img, px, rnd = stone_base(seed, base, amount)
    for c, cols in enumerate(joints):
        y0 = c * course
        for y in range(y0, y0 + course):
            for x in cols:
                put(px, x, y, noisy(mortar, rnd, 5))
        for x in range(16):
            put(px, x, y0 + course - 1, noisy(mortar, rnd, 5))
            shade(px, x, y0, bevel, (255, 255, 255))
            shade(px, x, y0 + course - 2, bevel * 0.8)
        for x in cols:
            for y in range(y0, y0 + course - 1):
                shade(px, x + 1, y, bevel * 0.7, (255, 255, 255))
                shade(px, x - 1, y, bevel * 0.6)
    # Chipped arrises at a few joint corners.
    for _ in range(5):
        c = rnd.randrange(len(joints))
        x = rnd.choice(joints[c]) + rnd.choice((-1, 1))
        y = c * course + rnd.choice((0, course - 2))
        shade(px, x, y, 0.35)
    return img, px, rnd


def crack(px, rnd, start, steps, dark=0.55, edge=0.2):
    """A meandering crack line with a pale lip; wraps so it tiles."""
    x, y = start
    for _ in range(steps):
        shade(px, x, y, dark)
        shade(px, x + 1, y, edge, (255, 255, 255))
        dx, dy = rnd.choice(((1, 1), (0, 1), (1, 0), (-1, 1), (1, 1)))
        x += dx
        y += dy


def ruin_textures():
    tex = {}
    img, px, rnd = ashlar(101, RUIN, RUIN_MORTAR, 9)
    tex["ruin_stone"] = img

    img, px, rnd = stone_base(102, RUIN, 8)
    for x in range(16):
        put(px, x, 0, mix(RUIN, (255, 255, 255), 0.18))
        put(px, x, 15, RUIN_MORTAR)
        put(px, x, 3, mix(RUIN, (0, 0, 0), 0.25))
        put(px, x, 12, mix(RUIN, (0, 0, 0), 0.25))
        put(px, x, 4, mix(RUIN, (255, 255, 255), 0.12))
    # Fluted band between the two carved fillets: grooves catch shadow on one side, light on the other.
    for x in range(1, 16, 3):
        for y in range(5, 11):
            put(px, x, y, noisy(mix(RUIN, (0, 0, 0), 0.3), rnd, 4))
            put(px, x + 1, y, noisy(mix(RUIN, (255, 255, 255), 0.14), rnd, 4))
    for _ in range(6):
        shade(px, rnd.randrange(16), rnd.randrange(5, 11), 0.2)
    tex["ruin_stone_carved"] = img

    img = tex["ruin_stone"].copy()
    px = img.load()
    rnd = random.Random(103)
    crack(px, rnd, (2, 0), 11)
    crack(px, rnd, (10, 6), 8)
    for _ in range(4):
        shade(px, rnd.randrange(16), rnd.randrange(16), 0.3)
    tex["ruin_stone_cracked"] = img

    img = tex["ruin_stone"].copy()
    px = img.load()
    rnd = random.Random(104)
    # Moss creeps up from the bottom course and along the mortar.
    for y in range(16):
        for x in range(16):
            weight = (y / 15.0) ** 1.6 * 0.9
            if px[x, y][:3] == px[x, y][:3] and rnd.random() < weight:
                put(px, x, y, noisy(mix(MOSS, (0, 0, 0), rnd.uniform(0, 0.3)), rnd, 8))
    for x in range(16):
        if rnd.random() < 0.55:
            put(px, x, 7, noisy(MOSS, rnd, 10))
    tex["ruin_stone_mossy"] = img
    return tex


def tomb_textures():
    tex = {}
    img, px, rnd = ashlar(111, TOMB, TOMB_MORTAR, 5, joints=((5, 13), (1, 9)), bevel=0.12)
    # A faint polish streak across the lower course.
    for x in range(16):
        shade(px, x, 10 + (x // 6), 0.08, (255, 255, 255))
    tex["tomb_stone"] = img

    img, px, rnd = stone_base(112, TOMB, 5)
    for x in range(16):
        put(px, x, 1, mix(TOMB, (0, 0, 0), 0.4))
        put(px, x, 14, mix(TOMB, (0, 0, 0), 0.4))
        put(px, x, 2, mix(TOMB, (255, 255, 255), 0.08))
    # An angular zigzag band, cut deep, with flakes of old dull-red pigment in the cut.
    for x in range(16):
        y = 5 + abs((x % 8) - 4)
        for dy in (0, 1):
            put(px, x, y + dy, noisy(mix(TOMB, (0, 0, 0), 0.55), rnd, 3))
        shade(px, x, y + 2, 0.1, (255, 255, 255))
        if rnd.random() < 0.35:
            put(px, x, y, noisy((96, 32, 30), rnd, 6))
    tex["tomb_stone_carved"] = img

    img = tex["tomb_stone"].copy()
    px = img.load()
    rnd = random.Random(113)
    crack(px, rnd, (4, 0), 12, dark=0.6, edge=0.12)
    crack(px, rnd, (12, 9), 7, dark=0.6, edge=0.12)
    tex["tomb_stone_cracked"] = img
    return tex


def plating(seed, base, amount):
    """Durasteel plates: a horizontal seam, offset vertical seams, rivets beside them, grime streaks."""
    img, px, rnd = stone_base(seed, base, amount)
    for x in range(16):
        put(px, x, 7, noisy(WRECK_SEAM, rnd, 4))
        put(px, x, 15, noisy(WRECK_SEAM, rnd, 4))
        shade(px, x, 8, 0.15, (255, 255, 255))
        shade(px, x, 0, 0.15, (255, 255, 255))
    for (sx, y0, y1) in ((10, 0, 7), (4, 8, 15)):
        for y in range(y0, y1):
            put(px, sx, y, noisy(WRECK_SEAM, rnd, 4))
            shade(px, sx + 1, y, 0.12, (255, 255, 255))
        for y in range(y0 + 1, y1, 3):
            put(px, sx - 2, y, (176, 180, 186))
            shade(px, sx - 2, y + 1, 0.35)
    # Vertical grime streaks under the seam.
    for _ in range(5):
        x = rnd.randrange(16)
        for y in range(8, 8 + rnd.randrange(3, 7)):
            shade(px, x, y, 0.14)
    for _ in range(4):
        put(px, rnd.randrange(16), rnd.randrange(16), noisy(RUST, rnd, 12))
    return img, px, rnd


def wreck_textures():
    tex = {}
    img, px, rnd = plating(121, WRECK, 6)
    tex["wreck_plating"] = img

    img = img.copy()
    px = img.load()
    rnd = random.Random(122)
    # Burn: a soot bloom with heat-tinted margins.
    for y in range(16):
        for x in range(16):
            d = math.hypot((x - 6.5) * 0.9, (y - 9.5) * 1.2) + rnd.uniform(-1.2, 1.2)
            if d < 4.0:
                shade(px, x, y, 0.75, (34, 30, 28))
            elif d < 6.5:
                shade(px, x, y, 0.45, (88, 66, 58))
            elif d < 7.5:
                shade(px, x, y, 0.25, (92, 98, 128))
    tex["wreck_plating_scorched"] = img

    img, px, rnd = plating(123, WRECK, 6)
    # An exposed conduit trench across the middle: dark channel, a copper-cored cable with split sheath.
    for x in range(16):
        for y in (5, 6, 9, 10):
            put(px, x, y, noisy(mix(WRECK_SEAM, (0, 0, 0), 0.3), rnd, 3))
        put(px, x, 7, noisy((176, 104, 56), rnd, 10))
        put(px, x, 8, noisy((118, 64, 34), rnd, 8))
        if x % 5 == 2:
            put(px, x, 7, (60, 58, 56))
            put(px, x, 8, (48, 46, 44))
    put(px, 11, 7, (255, 236, 196))
    put(px, 12, 7, (250, 190, 120))
    tex["wreck_conduit"] = img

    # Republic panel: off-white hull panel, recessed border, navy band and a red marker.
    img, px, rnd = stone_base(124, (206, 202, 192), 4)
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            put(px, x, y, (150, 148, 142))
        shade(px, i, 1, 0.12, (255, 255, 255))
    for x in range(1, 15):
        for y in range(10, 13):
            put(px, x, y, noisy((40, 62, 112), rnd, 5))
    for x in range(11, 14):
        put(px, x, 4, (152, 40, 36))
    for y in range(3, 6):
        put(px, 2, y, (150, 148, 142))
    tex["republic_panel"] = img

    # Imperial panel: dark plate, crimson trim lines, vent slits.
    img, px, rnd = stone_base(125, (46, 46, 51), 4)
    for x in range(16):
        put(px, x, 2, noisy((138, 28, 30), rnd, 6))
        put(px, x, 13, noisy((138, 28, 30), rnd, 6))
        put(px, x, 0, (28, 28, 32))
        put(px, x, 15, (28, 28, 32))
    for y in (6, 8, 10):
        for x in range(4, 12):
            put(px, x, y, (22, 22, 26))
            shade(px, x, y + 1, 0.15, (255, 255, 255))
    tex["imperial_panel"] = img
    return tex


def revealed(base, seed, seam_rows, seam_cols, dust=(236, 228, 208)):
    """A veiled stone shown by Sense: hairline seams catch the light, and fine dust sits in them."""
    img = base.copy()
    px = img.load()
    rnd = random.Random(seed)
    for y in range(16):
        for x in range(16):
            on_row = y in seam_rows
            on_col = any(x == c and r0 <= y < r1 for (c, r0, r1) in seam_cols)
            if on_row or on_col:
                shade(px, x, y, 0.55 if rnd.random() < 0.8 else 0.25, dust)
    for _ in range(14):
        x, y = rnd.randrange(16), rnd.randrange(16)
        shade(px, x, y, 0.3, dust)
    return img


def counterweight(seed, base, mortar, amount, band, worn):
    """Side, top and front of a counterweight: a heavy block bound by a band, with a worn push plate in front."""
    img, px, rnd = stone_base(seed, base, amount)
    for x in range(16):
        put(px, x, 0, mix(base, (255, 255, 255), 0.15))
        put(px, x, 15, mortar)
        for y in (11, 12):
            put(px, x, y, noisy(band, rnd, 6))
        shade(px, x, 13, 0.25)
    for (x, y) in ((2, 11), (7, 12), (13, 11)):
        put(px, x, y, mix(band, (255, 255, 255), 0.3))
    side = img

    img, px, rnd = stone_base(seed + 1, base, amount)
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            put(px, x, y, mortar)
    for y in range(4, 12):
        for x in range(4, 12):
            shade(px, x, y, 0.12, (255, 255, 255))
    top = img

    img = side.copy()
    px = img.load()
    rnd = random.Random(seed + 2)
    for y in range(2, 10):
        for x in range(3, 13):
            if y in (2, 9) or x in (3, 12):
                put(px, x, y, mix(base, (0, 0, 0), 0.35))
            else:
                put(px, x, y, noisy(mix(base, worn, 0.45), rnd, 4))
    for (x, y) in ((6, 5), (7, 5), (9, 6), (8, 6)):
        shade(px, x, y, 0.15, (255, 255, 255))
    return side, top, img


def puzzle_kit(decor):
    # Veiled stone: VEILED and OPEN use the plain decor models; only REVEALED needs its own texture.
    out("block/veiled_stone_ruin_revealed.png", revealed(decor["ruin_stone"], 131, (7, 15), [(3, 0, 7), (11, 0, 7), (7, 8, 15), (15, 8, 15)]))
    out("block/veiled_stone_tomb_revealed.png", revealed(decor["tomb_stone"], 132, (7, 15), [(5, 0, 7), (13, 0, 7), (1, 8, 15), (9, 8, 15)], (214, 200, 190)))
    out("block/veiled_stone_wreck_revealed.png", revealed(decor["wreck_plating"], 133, (7, 15), [(10, 0, 7), (4, 8, 15)], (226, 226, 214)))

    for skin, seed, base, mortar, amount, band, worn in (
            ("ruin", 141, RUIN, RUIN_MORTAR, 8, (120, 106, 86), (226, 214, 190)),
            ("tomb", 144, TOMB, TOMB_MORTAR, 5, (92, 70, 48), (96, 90, 98)),
            ("wreck", 147, WRECK, WRECK_SEAM, 6, (92, 70, 50), (160, 166, 172))):
        side, top, front = counterweight(seed, base, mortar, amount, band, worn)
        out(f"block/counterweight_stone_{skin}_side.png", side)
        out(f"block/counterweight_stone_{skin}_top.png", top)
        out(f"block/counterweight_stone_{skin}_front.png", front)

    # Step stone: a dressed ledge stone with a worn tread.
    img, px, rnd = stone_base(151, mix(RUIN, (0, 0, 0), 0.06), 8)
    for x in range(16):
        put(px, x, 0, mix(RUIN, (255, 255, 255), 0.2))
        put(px, x, 15, RUIN_MORTAR)
        shade(px, x, 14, 0.2)
    out("block/step_stone.png", img)
    img, px, rnd = stone_base(152, mix(RUIN, (255, 255, 255), 0.06), 7)
    for y in range(3, 13):
        for x in range(2, 14):
            shade(px, x, y, 0.1, (255, 255, 255))
    out("block/step_stone_top.png", img)

    # Echo urns: fired clay with dark bands; the shoulder band carries 1 to 4 notches, so each urn can be told apart.
    for index in range(4):
        rnd = random.Random(161 + index)
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
        px = img.load()
        for y in range(16):
            for x in range(16):
                light = 0.18 * math.cos((x - 5.5) / 16 * math.pi * 1.6)
                put(px, x, y, noisy(mix(CLAY, (255, 255, 255) if light > 0 else (0, 0, 0), abs(light)), rnd, 7))
        for y in (3, 4, 12):
            for x in range(16):
                put(px, x, y, noisy((58, 34, 26), rnd, 5))
        for n in range(index + 1):
            x = 3 + n * 3
            for y in range(6, 10):
                put(px, x, y, noisy((52, 30, 22), rnd, 4))
        for _ in range(6):
            shade(px, rnd.randrange(16), rnd.randrange(16), 0.25, (214, 196, 170))
        out(f"block/echo_urn_side_{index}.png", img)
    img, px, rnd = stone_base(165, CLAY, 6)
    for y in range(16):
        for x in range(16):
            r = max(abs(x - 7.5), abs(y - 7.5))
            if r < 3.5:
                put(px, x, y, (24, 16, 14))
            elif r < 4.5:
                put(px, x, y, mix(CLAY, (255, 255, 255), 0.15))
    out("block/echo_urn_top.png", img)

    # Urn keeper: tomb stone with an urn carved in relief and four sockets; solved, the cuts glow warm.
    for solved in (False, True):
        img, px, rnd = stone_base(171, TOMB, 5)
        glow = (236, 168, 84)
        cut = mix(TOMB, (0, 0, 0), 0.5)
        def relief(x, y):
            body = abs(x - 7.5) < 4.2 - abs(y - 9.0) * 0.3 and 5 <= y <= 13
            neck = abs(x - 7.5) < 1.6 and 3 <= y < 5
            lip = abs(x - 7.5) < 2.6 and y == 2
            return body or neck or lip

        for y in range(16):
            for x in range(16):
                if relief(x, y):
                    put(px, x, y, noisy(mix(TOMB, (255, 255, 255), 0.12), rnd, 4))
        # The cut runs round the relief: any stone cell that touches the urn's outline.
        for y in range(1, 15):
            for x in range(1, 15):
                if relief(x, y):
                    continue
                if any(relief(x + dx, y + dy) for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    put(px, x, y, mix(cut, glow, 0.85) if solved else cut)
        # A band across the shoulder, and the four sockets in a row below it (one per urn).
        for x in range(5, 11):
            put(px, x, 6, mix(cut, glow, 0.6) if solved else mix(TOMB, (0, 0, 0), 0.3))
        for x in (5, 7, 8, 10):
            put(px, x, 9, mix(cut, glow, 0.9) if solved else cut)
        for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
            put(px, x, y, glow if solved else cut)
            if solved:
                shade(px, x + 1, y, 0.4, glow)
        out("block/urn_keeper_front_solved.png" if solved else "block/urn_keeper_front.png", img)
    img, px, rnd = ashlar(172, TOMB, TOMB_MORTAR, 5, joints=((4, 12), (0, 8)), bevel=0.1)
    out("block/urn_keeper_side.png", img)
    img, px, rnd = stone_base(173, TOMB, 5)
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            put(px, x, y, TOMB_MORTAR)
    out("block/urn_keeper_top.png", img)

    # Pull anchor: a rusted iron ring on a staple set into dressed stone; strained it stands out, released it hangs.
    iron = (82, 74, 70)
    for name, drop, flakes in (("pull_anchor_front", 0, 3), ("pull_anchor_front_strain", -2, 10), ("pull_anchor_front_released", 2, 5)):
        img, px, rnd = ashlar(181, RUIN, RUIN_MORTAR, 8)
        rnd = random.Random(182 + drop)
        for y in range(3, 6):
            put(px, 7, y, noisy(iron, rnd, 6))
            put(px, 8, y, noisy(iron, rnd, 6))
        cy = 9.5 + drop
        for y in range(16):
            for x in range(16):
                d = math.hypot((x - 7.5) / 3.6, (y - cy) / 3.6)
                if 0.72 < d < 1.08:
                    put(px, x, y, noisy(mix(iron, RUST, rnd.uniform(0.2, 0.7)), rnd, 8))
                    if y > cy:
                        shade(px, x, y + 1, 0.3)
        for _ in range(flakes):
            put(px, rnd.randrange(3, 13), rnd.randrange(5, 16), noisy(RUST, rnd, 14))
        if drop > 0:
            crack(px, rnd, (6, 2), 4)
        out(f"block/{name}.png", img)
    img, px, rnd = ashlar(184, RUIN, RUIN_MORTAR, 8)
    out("block/pull_anchor_side.png", img)

    # Collapsed rubble: jammed blocks and rocks of the three sites' stone, with dark voids between.
    rnd = random.Random(191)
    img = Image.new("RGBA", (16, 16), (26, 24, 22, 255))
    px = img.load()
    rocks = [(rnd.uniform(0, 16), rnd.uniform(0, 16), rnd.uniform(2.0, 4.2), rnd.choice((RUIN, RUIN, (128, 124, 118), (150, 140, 120)))) for _ in range(11)]
    for y in range(16):
        for x in range(16):
            best = None
            for (rx, ry, rr, col) in rocks:
                for ox in (-16, 0, 16):
                    for oy in (-16, 0, 16):
                        d = max(abs(x - rx - ox), abs(y - ry - oy) * 1.2) / rr
                        if d < 1.0 and (best is None or d < best[0]):
                            best = (d, col, y - ry - oy)
            if best is not None:
                d, col, dy = best
                c = mix(col, (255, 255, 255), 0.18) if dy < -best[0] * 1.5 else col
                c = mix(c, (0, 0, 0), max(0.0, d - 0.6))
                put(px, x, y, noisy(c, rnd, 8))
    out("block/collapsed_rubble.png", img)

    # Puzzle seals: a sealed slab door of each skin with its seam.
    img, px, rnd = stone_base(201, mix(RUIN, (255, 255, 255), 0.05), 7)
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            put(px, x, y, RUIN_MORTAR)
        for (x, y) in ((i, 1), (1, i)):
            shade(px, x, y, 0.15, (255, 255, 255))
    for y in range(1, 15):
        put(px, 7, y, mix(RUIN, (0, 0, 0), 0.45))
        shade(px, 8, y, 0.15, (255, 255, 255))
    for x in range(3, 13):
        if x not in (7, 8):
            put(px, x, 4, mix(RUIN, (0, 0, 0), 0.2))
            put(px, x, 11, mix(RUIN, (0, 0, 0), 0.2))
    out("block/puzzle_seal_ruin.png", img)

    img, px, rnd = stone_base(202, TOMB, 5)
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            put(px, x, y, TOMB_MORTAR)
    for y in range(1, 15):
        x = 7 + (1 if (y // 3) % 2 else 0)
        put(px, x, y, mix(TOMB, (0, 0, 0), 0.6))
        shade(px, x + 1, y, 0.08, (255, 255, 255))
    for x in range(2, 14):
        put(px, x, 2, mix(TOMB, (0, 0, 0), 0.35))
        put(px, x, 13, mix(TOMB, (0, 0, 0), 0.35))
    out("block/puzzle_seal_tomb.png", img)

    img, px, rnd = plating(203, mix(WRECK, (0, 0, 0), 0.06), 5)
    for y in range(16):
        put(px, 8, y, WRECK_SEAM)
    for x in range(4, 12):
        if x != 8:
            put(px, x, 4, (150, 154, 160))
            shade(px, x, 5, 0.3)
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        put(px, x, y, (176, 180, 186))
    out("block/puzzle_seal_wreck.png", img)

    # Holocron pedestals: the stone of each order with inlaid lines that carry light when ready.
    for kind, seed, stone, inlay, lit in (
            ("jedi", 211, (198, 192, 176), (170, 140, 72), (255, 222, 140)),
            ("sith", 214, (40, 36, 42), (96, 30, 30), (230, 86, 64)),
            ("wayfarer", 217, (132, 128, 120), (150, 114, 64), (244, 204, 132))):
        for ready in (False, True):
            img, px, rnd = stone_base(seed, stone, 5)
            line = lit if ready else inlay
            for x in range(16):
                put(px, x, 2, noisy(line, rnd, 6))
                put(px, x, 13, noisy(line, rnd, 6))
            for y in range(3, 13):
                put(px, 3, y, noisy(line, rnd, 6))
                put(px, 12, y, noisy(line, rnd, 6))
            for x in range(16):
                put(px, x, 0, mix(stone, (0, 0, 0), 0.3))
                put(px, x, 15, mix(stone, (0, 0, 0), 0.3))
            if ready:
                for y in range(3, 13):
                    shade(px, 4, y, 0.2, lit)
                    shade(px, 11, y, 0.2, lit)
            out(f"block/holocron_pedestal_{kind}_side{'_ready' if ready else ''}.png", img)
        img, px, rnd = stone_base(seed + 1, stone, 5)
        for y in range(16):
            for x in range(16):
                r = max(abs(x - 7.5), abs(y - 7.5))
                if r < 3.5:
                    shade(px, x, y, 0.35)
                elif r < 4.5:
                    put(px, x, y, noisy(inlay, rnd, 6))
        out(f"block/holocron_pedestal_{kind}_top.png", img)

    # Tomb vent: an iron grate over a dark shaft; the tell shows grit in the bars, the puff a grey-green haze.
    for phase in ("", "_tell", "_puff"):
        rnd = random.Random(221)
        img = Image.new("RGBA", (16, 16), (12, 11, 12, 255))
        px = img.load()
        for y in range(16):
            for x in range(16):
                if x in (0, 15) or y in (0, 15):
                    put(px, x, y, noisy(TOMB, rnd, 5))
                elif x % 3 == 1:
                    put(px, x, y, noisy((70, 64, 62), rnd, 6))
                    if y % 4 == 0:
                        shade(px, x, y, 0.25, (255, 255, 255))
                else:
                    put(px, x, y, noisy((16, 14, 15), rnd, 3))
        if phase == "_tell":
            for _ in range(16):
                x, y = rnd.randrange(1, 15), rnd.randrange(1, 15)
                put(px, x, y, noisy((150, 140, 124), rnd, 16))
        if phase == "_puff":
            for y in range(1, 15):
                for x in range(1, 15):
                    shade(px, x, y, 0.45 + rnd.uniform(-0.1, 0.1), (104, 120, 90))
        out(f"block/tomb_vent_top{phase}.png", img)
    img, px, rnd = ashlar(224, TOMB, TOMB_MORTAR, 5, joints=((3, 11), (7, 15)), bevel=0.1)
    out("block/tomb_vent_side.png", img)


def resources():
    # Lumen moss: a dark teal-green mat with pale points of lumen.
    rnd = random.Random(231)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    px = img.load()
    for y in range(16):
        for x in range(16):
            tuft = 0.5 + 0.5 * math.sin(x * 1.7 + y * 0.9) * math.cos(y * 1.3 - x * 0.4)
            put(px, x, y, noisy(mix((40, 70, 64), (78, 118, 96), tuft), rnd, 8))
    for _ in range(12):
        x, y = rnd.randrange(16), rnd.randrange(16)
        put(px, x, y, noisy(LUMEN, rnd, 10))
        shade(px, x + 1, y, 0.35, LUMEN)
    out("block/lumen_moss.png", img)

    # Funerary urn: plain sealed clay with a waxed lid band.
    rnd = random.Random(232)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    px = img.load()
    urn = (124, 92, 70)
    for y in range(16):
        for x in range(16):
            light = 0.16 * math.cos((x - 6.0) / 16 * math.pi * 1.6)
            put(px, x, y, noisy(mix(urn, (255, 255, 255) if light > 0 else (0, 0, 0), abs(light)), rnd, 6))
    for x in range(16):
        put(px, x, 2, noisy((70, 50, 40), rnd, 5))
        put(px, x, 3, noisy((92, 34, 30), rnd, 6))
        put(px, x, 4, noisy((70, 50, 40), rnd, 5))
    for _ in range(5):
        shade(px, rnd.randrange(16), rnd.randrange(5, 16), 0.3, (200, 186, 160))
    out("block/funerary_urn_side.png", img)
    img, px, rnd = stone_base(233, (104, 76, 58), 6)
    for y in range(16):
        for x in range(16):
            if max(abs(x - 7.5), abs(y - 7.5)) < 2.5:
                put(px, x, y, noisy((96, 32, 28), rnd, 6))
    out("block/funerary_urn_top.png", img)

    # Wreck salvage: torn plates, scratched and rusted along the bent edges.
    img, px, rnd = plating(234, mix(WRECK, (0, 0, 0), 0.05), 8)
    for _ in range(9):
        x, y = rnd.randrange(16), rnd.randrange(16)
        for k in range(rnd.randrange(2, 5)):
            shade(px, x + k, y, 0.3, (220, 222, 226))
    for x in range(16):
        if rnd.random() < 0.5:
            put(px, x, 15 - rnd.randrange(2), noisy(RUST, rnd, 12))
    out("block/wreck_salvage.png", img)
    img, px, rnd = plating(235, WRECK, 7)
    for _ in range(6):
        put(px, rnd.randrange(16), rnd.randrange(16), noisy(RUST, rnd, 14))
    out("block/wreck_salvage_top.png", img)


def site_blocks():
    """B's three site blocks (bounty terminal, echo cairn, site spawner), drawn here with the rest of the world art."""
    for army, seed, casing, trim, screen, glow in (
            ("republic", 241, (204, 200, 190), (40, 62, 112), (66, 92, 120), (178, 214, 236)),
            ("imperial", 244, (44, 44, 49), (138, 28, 30), (60, 30, 26), (226, 140, 92))):
        img, px, rnd = stone_base(seed, casing, 4)
        for i in range(16):
            for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
                put(px, x, y, trim)
        for y in range(2, 10):
            for x in range(2, 14):
                put(px, x, y, noisy(screen, rnd, 4))
        for y in (3, 5, 7):
            for x in range(3, 3 + rnd.randrange(5, 10)):
                put(px, x, y, noisy(glow, rnd, 8))
        for x in range(2, 14):
            shade(px, x, 10, 0.3)
        for y in (12, 13):
            for x in range(3, 13, 2):
                put(px, x, y, mix(casing, (0, 0, 0), 0.35))
        out(f"block/bounty_terminal_{army}_front.png", img)
        img, px, rnd = stone_base(seed + 1, casing, 4)
        for i in range(16):
            for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
                put(px, x, y, trim)
        for y in range(4, 12, 2):
            for x in range(4, 12):
                put(px, x, y, mix(casing, (0, 0, 0), 0.3))
        out(f"block/bounty_terminal_{army}_side.png", img)
    img, px, rnd = stone_base(247, (92, 94, 100), 4)
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            put(px, x, y, (60, 62, 68))
    out("block/bounty_terminal_top.png", img)

    # Echo cairn: weathered stacked stones, and a half-buried canvas satchel with a leather strap.
    rnd = random.Random(251)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    px = img.load()
    for y in range(16):
        for x in range(16):
            band = (y // 4 + (x // 6)) % 3
            put(px, x, y, noisy(((150, 144, 132), (128, 124, 116), (170, 162, 146))[band], rnd, 9))
            if y % 4 == 3:
                shade(px, x, y, 0.35)
    out("block/echo_cairn_stone.png", img)
    rnd = random.Random(252)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    px = img.load()
    for y in range(16):
        for x in range(16):
            weave = 0.08 if (x + y) % 2 else -0.04
            put(px, x, y, noisy(mix((120, 98, 66), (255, 255, 255), weave) if weave > 0 else mix((120, 98, 66), (0, 0, 0), -weave), rnd, 5))
    for y in range(16):
        put(px, 6, y, (78, 50, 32))
        put(px, 7, y, (90, 60, 38))
    for x in range(5, 9):
        put(px, x, 9, (160, 150, 120))
    out("block/echo_cairn_satchel.png", img)

    # Site spawner: never rendered; its particle texture is a little grey dust.
    rnd = random.Random(253)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for _ in range(18):
        put(px, rnd.randrange(16), rnd.randrange(16), noisy((120, 116, 110), rnd, 10), 200)
    out("block/site_spawner.png", img)


def m3_particles():
    """The seven M3 world particles, white with alpha (tinted in code)."""
    out("particle/light_mote_0.png", soft_blob(2.8, 1.6))
    out("particle/light_mote_1.png", soft_blob(2.0, 1.2))

    # Leaves: small lanceolate shapes with a darker midrib, three tilts.
    for frame, angle in enumerate((0.5, 1.4, 2.4)):
        img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
        px = img.load()
        ca, sa = math.cos(angle), math.sin(angle)
        for y in range(8):
            for x in range(8):
                u = (x - 3.5) * ca + (y - 3.5) * sa
                v = -(x - 3.5) * sa + (y - 3.5) * ca
                if (u / 3.4) ** 2 + (v / 1.5) ** 2 <= 1.0:
                    rib = abs(v) < 0.45
                    px[x, y] = (200, 200, 200, 255) if rib else (255, 255, 255, 255)
        out(f"particle/leaf_fall_{frame}.png", img)

    # Tomb dust: irregular soft puffs.
    for frame in range(3):
        rnd = random.Random(261 + frame)
        img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
        px = img.load()
        lobes = [(3.5 + rnd.uniform(-1.3, 1.3), 3.5 + rnd.uniform(-1.3, 1.3), rnd.uniform(1.4, 2.4)) for _ in range(4)]
        for y in range(8):
            for x in range(8):
                a = max(max(0.0, 1.0 - math.hypot(x - lx, y - ly) / lr) for (lx, ly, lr) in lobes)
                a *= rnd.uniform(0.75, 1.0)
                if a > 0.05:
                    px[x, y] = (255, 255, 255, clamp(230 * a))
        out(f"particle/tomb_dust_{frame}.png", img)

    # Veil dust: a few fine specks.
    for frame in range(2):
        rnd = random.Random(265 + frame)
        img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
        px = img.load()
        for _ in range(3 + frame):
            x, y = rnd.randrange(2, 6), rnd.randrange(2, 6)
            px[x, y] = (255, 255, 255, 255)
        px[3, 3] = (255, 255, 255, 220)
        out(f"particle/veil_dust_{frame}.png", img)

    # Grit: small angular chips with a lit top edge.
    shapes = (((3, 3), (4, 3), (3, 4), (4, 4)), ((2, 3), (3, 3), (4, 3), (3, 4)), ((3, 2), (3, 3), (4, 3), (4, 4), (5, 4)))
    for frame, cells in enumerate(shapes):
        img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
        px = img.load()
        top = min(y for (_, y) in cells)
        for (x, y) in cells:
            px[x, y] = (255, 255, 255, 255) if y == top else (196, 196, 196, 255)
        out(f"particle/vent_grit_{frame}.png", img)

    # Miasma: lobed cloud frames that grow and thin (setSpriteFromAge).
    for frame in range(4):
        rnd = random.Random(271 + frame)
        img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
        px = img.load()
        lobes = [(3.5 + rnd.uniform(-1.0, 1.0), 3.5 + rnd.uniform(-1.0, 1.0), rnd.uniform(1.8, 2.8)) for _ in range(4)]
        spread = 1.0 + frame * 0.15
        for y in range(8):
            for x in range(8):
                a = max(max(0.0, 1.0 - math.hypot(x - lx, y - ly) / (lr * spread)) for (lx, ly, lr) in lobes)
                a *= (0.85 - frame * 0.15) * rnd.uniform(0.8, 1.0)
                if a > 0.03:
                    px[x, y] = (255, 255, 255, clamp(255 * a))
        out(f"particle/miasma_{frame}.png", img)

    out("particle/conduit_spark_0.png", soft_blob(1.5, 0.7))
    img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    px = img.load()
    for y in range(8):
        for x in range(8):
            d = math.hypot((x - 3.5) * 1.7, (y - 3.5) * 0.55)
            a = max(0.0, 1.0 - d / 2.0)
            if a > 0:
                px[x, y] = (255, 255, 255, clamp(255 * a))
    out("particle/conduit_spark_1.png", img)


def chevron_mask(width, height, ox, oy, mirror=False):
    """A broad downward chevron across a face: white where the pattern is, transparent elsewhere."""
    cells = []
    for y in range(height):
        for x in range(width):
            u = (width - 1 - x) if mirror else x
            centre = (width - 1) / 2.0
            v = y - (height * 0.32 + abs(u - centre) * -0.9 + width * 0.45)
            if 0.0 <= v < height * 0.2:
                cells.append((ox + x, oy + y))
    return cells


def banner_pattern():
    # Banner: front face at (1, 1) and back face at (22, 1), each 20 x 40.
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    for (x, y) in chevron_mask(20, 40, 1, 1) + chevron_mask(20, 40, 22, 1, mirror=True):
        px[x, y] = (255, 255, 255, 255)
    out("entity/banner/republic_chevron.png", img)
    # Shield: the face at (1, 1), 12 x 22.
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    for (x, y) in chevron_mask(12, 22, 1, 1):
        px[x, y] = (255, 255, 255, 255)
    out("entity/shield/republic_chevron.png", img)


def m3_world():
    decor = {}
    decor.update(ruin_textures())
    decor.update(tomb_textures())
    decor.update(wreck_textures())
    for name, img in decor.items():
        out(f"block/{name}.png", img)
    puzzle_kit(decor)
    resources()
    site_blocks()
    m3_particles()
    banner_pattern()


def main():
    kyber_cluster()
    unstable_clusters()
    crystal_veined_stone()
    resonant_seal()
    meditation_stone()
    particles()
    shadow_echo()
    m3_world()


if __name__ == "__main__":
    main()
