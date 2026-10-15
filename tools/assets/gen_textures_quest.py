#!/usr/bin/env python3
"""Generates the quest and HUD textures (PLACEHOLDER art, original, deterministic).

Writes into src/main/resources/assets/oldrepublic/textures/:
  item/wanderers_journal.png          16x16  worn leather journal with a durasteel clasp
  gui/journal.png                     320x200 open journal: leather cover, durasteel corner plates, two pages
  gui/hud/resonance.png               9x9    small pale crystal glyph (Resonance)
  gui/hud/force_push.png              16x16  open palm with bent-air arcs (Force Push cooldown)
  particle/force_mote_0..3.png        8x8    soft pale motes (meditation)
  particle/resonance_shimmer_0..3.png 8x8    soft crystal glints (the Calling)

Run from the repository root: python3 tools/assets/gen_textures_quest.py
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "assets", "oldrepublic", "textures")


def out(rel, img):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    print("wrote", os.path.normpath(path))


def clamp(v):
    return max(0, min(255, int(round(v))))


def noisy(rgb, rnd, amount):
    return tuple(clamp(c + rnd.uniform(-amount, amount)) for c in rgb)


# ---------------------------------------------------------------- item
def journal_item():
    rnd = random.Random(1)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    leather = (92, 58, 38)
    leather_dark = (58, 36, 24)
    pages = (214, 200, 168)
    steel = (150, 156, 162)
    steel_dark = (88, 94, 102)
    # Book body, slightly tilted silhouette: cover from x=2..13, y=2..14
    for y in range(2, 15):
        for x in range(2, 14):
            edge = x in (2, 13) or y in (2, 14)
            c = leather_dark if edge else noisy(leather, rnd, 10)
            px[x, y] = c + (255,)
    # Page block visible on the right edge and bottom
    for y in range(3, 14):
        px[13, y] = noisy(pages, rnd, 8) + (255,)
    for x in range(3, 13):
        px[x, 14] = noisy(pages, rnd, 8) + (255,)
    px[13, 14] = leather_dark + (255,)
    # Spine
    for y in range(2, 15):
        px[2, y] = (44, 26, 18, 255)
        px[3, y] = noisy((70, 44, 30), rnd, 6) + (255,)
    # Durasteel corner caps
    for (x, y) in [(12, 2), (12, 3), (11, 2), (12, 13), (11, 13), (12, 12)]:
        px[x, y] = steel + (255,)
    # Clasp strap with a small steel buckle
    for x in range(8, 14):
        px[x, 8] = leather_dark + (255,)
        px[x, 9] = (70, 44, 30, 255)
    px[10, 8] = steel + (255,)
    px[11, 8] = steel_dark + (255,)
    px[10, 9] = steel_dark + (255,)
    px[11, 9] = steel + (255,)
    # Worn embossed mark on the cover (abstract, not a faction symbol)
    for (x, y) in [(6, 5), (7, 5), (8, 5), (7, 6), (7, 7), (6, 11), (7, 11), (8, 11), (7, 10)]:
        px[x, y] = (122, 88, 58, 255)
    # Scuffs
    for _ in range(6):
        x, y = rnd.randint(4, 11), rnd.randint(3, 13)
        r, g, b, a = px[x, y]
        px[x, y] = (clamp(r + 18), clamp(g + 14), clamp(b + 10), a)
    out("item/wanderers_journal.png", img)


# ---------------------------------------------------------------- journal gui
def journal_gui():
    rnd = random.Random(2)
    w, h = 320, 200
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # Leather cover with darker edge
    d.rounded_rectangle([0, 0, w - 1, h - 1], radius=10, fill=(74, 46, 30, 255))
    px = img.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            n = rnd.uniform(-9, 9)
            edge = min(x, y, w - 1 - x, h - 1 - y)
            shade = -28 if edge < 3 else (-12 if edge < 6 else 0)
            px[x, y] = (clamp(r + n + shade), clamp(g + n * 0.7 + shade), clamp(b + n * 0.5 + shade), a)
    # Stitching around the cover
    for x in range(14, w - 14, 6):
        d.line([(x, 5), (x + 2, 5)], fill=(150, 118, 80, 255))
        d.line([(x, h - 6), (x + 2, h - 6)], fill=(150, 118, 80, 255))
    for y in range(14, h - 14, 6):
        d.line([(5, y), (5, y + 2)], fill=(150, 118, 80, 255))
        d.line([(w - 6, y), (w - 6, y + 2)], fill=(150, 118, 80, 255))

    # Pages
    def page(x0, x1):
        for y in range(10, h - 10):
            for x in range(x0, x1):
                base = (226, 214, 184)
                # Darker toward the edges and the gutter, a little foxing.
                dist_edge = min(x - x0, x1 - 1 - x, y - 10, h - 11 - y)
                age = max(0, 10 - dist_edge) * 2.2
                n = rnd.uniform(-5, 5)
                px[x, y] = (clamp(base[0] - age + n), clamp(base[1] - age * 1.15 + n), clamp(base[2] - age * 1.5 + n), 255)
        for _ in range(26):
            cx, cy = rnd.randint(x0 + 6, x1 - 7), rnd.randint(16, h - 17)
            rad = rnd.randint(1, 3)
            for yy in range(cy - rad, cy + rad + 1):
                for xx in range(cx - rad, cx + rad + 1):
                    if (xx - cx) ** 2 + (yy - cy) ** 2 <= rad * rad:
                        r, g, b, a = px[xx, yy]
                        px[xx, yy] = (clamp(r - 10), clamp(g - 14), clamp(b - 18), a)

    page(12, 158)
    page(162, 308)
    # Gutter shadow
    for x in range(150, 170):
        t = 1 - abs(x - 160) / 10.0
        for y in range(10, h - 10):
            r, g, b, a = px[x, y]
            k = 40 * t
            px[x, y] = (clamp(r - k), clamp(g - k), clamp(b - k * 0.9), a)
    d.line([(160, 8), (160, h - 9)], fill=(60, 38, 24, 255))

    # Durasteel corner plates with rivets
    def plate(cx, cy, fx, fy):
        pts = [(cx, cy), (cx + 22 * fx, cy), (cx, cy + 22 * fy)]
        d.polygon(pts, fill=(118, 124, 130, 255))
        d.line([pts[1], pts[2]], fill=(70, 76, 84, 255))
        for (dx, dy) in [(5, 5), (13, 4), (4, 13)]:
            rx, ry = cx + dx * fx, cy + dy * fy
            d.point((rx, ry), fill=(190, 196, 202, 255))
            d.point((rx + fx, ry + fy), fill=(60, 64, 70, 255))

    plate(2, 2, 1, 1)
    plate(w - 3, 2, -1, 1)
    plate(2, h - 3, 1, -1)
    plate(w - 3, h - 3, -1, -1)
    # Ribbon bookmark
    for y in range(h - 14, h):
        for x in range(232, 237):
            if y < h - 2 or x in (233, 234, 235):
                px[x, y] = (110, 30, 26, 255)
    out("gui/journal.png", img)


# ---------------------------------------------------------------- hud icons
def resonance_icon():
    img = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    px = img.load()
    shape = [
        "....#....",
        "...###...",
        "...#o#...",
        "..##o##..",
        "..#ooo#..",
        "..##o##..",
        "...#o#...",
        "...###...",
        "....#....",
    ]
    for y, row in enumerate(shape):
        for x, ch in enumerate(row):
            if ch == "#":
                px[x, y] = (120, 176, 214, 255)
            elif ch == "o":
                px[x, y] = (226, 244, 255, 255)
    out("gui/hud/resonance.png", img)


def push_icon():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    skin = (214, 200, 176, 255)
    # Open palm seen from the side, fingers forward (to the right)
    d.rounded_rectangle([10, 24, 30, 44], radius=5, fill=skin)
    for i, y in enumerate([24, 29, 34, 39]):
        d.rounded_rectangle([26, y, 40 - i, y + 4], radius=2, fill=skin)
    d.rounded_rectangle([14, 16, 22, 28], radius=3, fill=skin)
    # Bent-air arcs
    for i, r in enumerate([12, 19, 26]):
        alpha = 230 - i * 55
        d.arc([30 - r, 32 - r, 30 + r, 32 + r], start=-40, end=40, fill=(170, 210, 240, alpha), width=3)
    img = img.filter(ImageFilter.SMOOTH)
    img = img.resize((16, 16), Image.LANCZOS)
    out("gui/hud/force_push.png", img)


# ---------------------------------------------------------------- particles
def soft_dot(size, core, glow, radius, rnd, sparkle=False):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    c = (size - 1) / 2.0
    for y in range(size):
        for x in range(size):
            dist = math.hypot(x - c, y - c)
            t = max(0.0, 1.0 - dist / radius)
            if sparkle:
                cross = max(0.0, 1.0 - min(abs(x - c), abs(y - c)) / 0.8) * max(0.0, 1.0 - dist / (radius + 1.5))
                t = max(t * t, cross * 0.8)
            else:
                t = t * t
            if t <= 0:
                continue
            col = tuple(core[i] * t + glow[i] * (1 - t) for i in range(3))
            px[x, y] = (clamp(col[0]), clamp(col[1]), clamp(col[2]), clamp(255 * t))
    return img


def particles():
    rnd = random.Random(3)
    for i in range(4):
        out(f"particle/force_mote_{i}.png", soft_dot(8, (250, 252, 255), (196, 214, 236), 2.2 + i * 0.5, rnd))
    for i in range(4):
        out(f"particle/resonance_shimmer_{i}.png", soft_dot(8, (240, 252, 255), (150, 210, 236), 1.6 + (i % 2) * 0.6, rnd, sparkle=i >= 2))


if __name__ == "__main__":
    journal_item()
    journal_gui()
    resonance_icon()
    push_icon()
    particles()
