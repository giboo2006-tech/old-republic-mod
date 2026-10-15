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


def main():
    kyber_cluster()
    unstable_clusters()
    crystal_veined_stone()
    resonant_seal()
    meditation_stone()
    particles()
    shadow_echo()


if __name__ == "__main__":
    main()
