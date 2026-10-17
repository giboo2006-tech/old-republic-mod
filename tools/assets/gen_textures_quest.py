#!/usr/bin/env python3
"""Generates the quest and HUD textures (PLACEHOLDER art, original, deterministic).

Writes into src/main/resources/assets/oldrepublic/textures/:
  item/wanderers_journal.png          16x16  worn leather journal with a durasteel clasp
  gui/journal.png                     320x200 open journal: leather cover, durasteel corner plates, two pages
  gui/hud/resonance.png               9x9    small pale crystal glyph (Resonance)
  gui/hud/force_push.png              16x16  open palm with bent-air arcs (Force Push cooldown)
  particle/force_mote_0..3.png        8x8    soft pale motes (meditation)
  particle/resonance_shimmer_0..3.png 8x8    soft crystal glints (the Calling)
  item/archive_record.png             16x16  Milestone 3, R2: a sealed data slate from the Watch Archive's vault,
                                             dark casing, pale-blue ledger lines, an amber vault seal
  item/fence_book.png                 16x16  Milestone 3, I1: the fence's slim cloth-bound notebook, ink-stained
                                             page edges, a cord wound round it and a red ribbon marker
  gui/sprites/portrait/officer_republic.png 32x32 Milestone 3: a Republic outpost officer, head and shoulders,
                                             navy uniform, peaked cap with a muted gold band, gold shoulder stripe
  gui/sprites/portrait/officer_imperial.png 32x32 Milestone 3: an Imperial outpost officer, head and shoulders,
                                             grey uniform with crimson piping, black-banded grey peaked cap

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


# ---------------------------------------------------------------- Milestone 3: mission items and officer portraits
# Each has its own seed, so the textures above stay byte-identical.

def archive_record_item():
    """R2's archive record: a sealed data slate, its vault seal amber (the Watch Archive's colour)."""
    rnd = random.Random(41)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    casing = (58, 62, 70)
    casing_dark = (32, 34, 40)
    casing_light = (104, 110, 120)
    screen = (22, 34, 44)
    line = (124, 182, 214)
    line_dim = (70, 112, 138)
    seal = (214, 160, 64)
    seal_dark = (150, 98, 34)
    # Slate body, tilted a step: rows 3..13, columns 2..13 with a bevelled outline.
    for y in range(3, 14):
        for x in range(2, 14):
            edge = x in (2, 13) or y in (3, 13)
            corner = (x, y) in ((2, 3), (13, 3), (2, 13), (13, 13))
            if corner:
                continue
            px[x, y] = (casing_dark if edge else noisy(casing, rnd, 6)) + (255,)
    for x in range(3, 13):
        px[x, 4] = casing_light + (255,)
    # Display window with ledger rows.
    for y in range(5, 11):
        for x in range(4, 12):
            px[x, y] = noisy(screen, rnd, 3) + (255,)
    for y, length in ((6, 6), (8, 5), (10, 4)):
        for x in range(5, 5 + length):
            px[x, y] = (line if x < 5 + length - 1 else line_dim) + (255,)
    # A column of figures on the right of the rows.
    for y in (6, 8):
        px[11, y] = line_dim + (255,)
    # The amber vault seal over the lower right corner, a round stamp with a dark rim.
    for y in range(10, 15):
        for x in range(9, 14):
            d = math.hypot(x - 11, y - 12)
            if d <= 2.3:
                px[x, y] = (seal_dark if d > 1.5 else noisy(seal, rnd, 8)) + (255,)
    px[11, 12] = (246, 214, 140, 255)
    # Clip notch and a worn scratch.
    px[7, 13] = casing_light + (255,)
    px[8, 13] = casing_light + (255,)
    for _ in range(3):
        x, y = rnd.randint(3, 8), rnd.randint(11, 12)
        r, g, b, a = px[x, y]
        px[x, y] = (clamp(r + 22), clamp(g + 22), clamp(b + 24), a)
    out("item/archive_record.png", img)


def fence_book_item():
    """I1's fence book: a slim dark-green cloth notebook, a cord wound round it, a red ribbon."""
    rnd = random.Random(42)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    cloth = (52, 78, 58)
    cloth_dark = (30, 46, 34)
    pages = (206, 194, 160)
    ink = (60, 56, 70)
    cord = (168, 138, 92)
    cord_dark = (112, 88, 56)
    ribbon = (168, 40, 36)
    # Narrow book standing slightly upright: cover columns 4..11, rows 1..14.
    for y in range(1, 15):
        for x in range(4, 12):
            edge = x in (4, 11) or y in (1, 14)
            px[x, y] = (cloth_dark if edge else noisy(cloth, rnd, 8)) + (255,)
    # Spine on the left, page block peeking out on the right and bottom, ink-stained.
    for y in range(1, 15):
        px[4, y] = (22, 34, 26, 255)
        px[5, y] = noisy((40, 62, 46), rnd, 5) + (255,)
    for y in range(2, 14):
        stain = rnd.random() < 0.3
        px[12, y] = (ink if stain else noisy(pages, rnd, 8)) + (255,)
    for x in range(5, 12):
        px[x, 15] = noisy(pages, rnd, 8) + (255,)
    px[12, 14] = cloth_dark + (255,)
    # The cord, wound twice round the middle and knotted at the edge.
    for x in range(4, 13):
        px[x, 7] = (cord if x % 2 else cord_dark) + (255,)
        px[x, 9] = (cord_dark if x % 2 else cord) + (255,)
    px[13, 8] = cord + (255,)
    px[13, 9] = cord_dark + (255,)
    px[14, 10] = cord + (255,)
    # Red ribbon marker hanging from the top.
    for y in range(0, 4):
        px[9, y] = ribbon + (255,)
    px[10, 0] = ribbon + (255,)
    # A faded tally scratched on the cover.
    for (x, y) in [(7, 3), (7, 4), (8, 3), (8, 4), (9, 4), (7, 11), (8, 11), (9, 11), (10, 11)]:
        px[x, y] = (96, 118, 98, 255)
    out("item/fence_book.png", img)


def officer_portrait(navy, seed):
    """Head and shoulders of an outpost officer on a dim background, 32x32, in the mentor portraits' style."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 255))
    px = img.load()
    if navy:
        bg_top, bg_bottom = (24, 30, 40), (46, 58, 76)
        uniform, uniform_dark = (40, 56, 92), (26, 36, 62)
        cap, band = (34, 48, 82), (176, 150, 86)
        trim, rim = (176, 150, 86), (143, 184, 232)
        skin, hair, eyes = (198, 156, 124), (84, 62, 44), (70, 96, 120)
    else:
        bg_top, bg_bottom = (20, 20, 24), (44, 32, 36)
        uniform, uniform_dark = (88, 90, 96), (58, 60, 66)
        cap, band = (82, 84, 90), (24, 24, 28)
        trim, rim = (176, 38, 42), (208, 70, 58)
        skin, hair, eyes = (214, 182, 160), (46, 40, 38), (96, 90, 84)

    for y in range(32):
        t = y / 31
        row = tuple(clamp(bg_top[i] * (1 - t) + bg_bottom[i] * t) for i in range(3))
        for x in range(32):
            px[x, y] = noisy(row, rnd, 3) + (255,)
    background = img.copy().load()

    # Shoulders: a fitted uniform jacket with a high collar.
    for y in range(23, 32):
        half = 10 + (y - 23)
        for x in range(16 - half, 16 + half):
            if 0 <= x < 32:
                px[x, y] = noisy(uniform, rnd, 5) + (255,)
    for y in range(22, 26):
        for x in range(12, 20):
            px[x, y] = noisy(uniform_dark, rnd, 4) + (255,)
    # Collar piping and the shoulder stripe (gold for the Republic, crimson piping for the Empire).
    for x in range(12, 20):
        px[x, 22] = trim + (255,)
    for y in range(24, 32):
        px[16, y] = uniform_dark + (255,)
    for x in range(4, 10):
        px[x, 26] = trim + (255,)
    for x in range(22, 28):
        px[x, 26] = trim + (255,)
    # Rank plaque on the chest.
    for x in range(20, 24):
        px[x, 29] = (trim if navy else (150, 150, 156)) + (255,)
        px[x, 30] = ((52, 72, 110) if navy else (176, 38, 42)) + (255,)

    # Neck and face.
    for y in range(18, 22):
        for x in range(14, 18):
            px[x, y] = noisy(tuple(clamp(c * 0.85) for c in skin), rnd, 3) + (255,)
    for y in range(8, 20):
        for x in range(10, 22):
            dx = (x - 15.5) / 5.8
            dy = (y - 13.0) / 6.8
            if dx * dx + dy * dy <= 1.0:
                px[x, y] = noisy(skin, rnd, 4) + (255,)
    # Short hair at the temples under the cap.
    for y in range(9, 13):
        for x in (10, 11, 20, 21):
            if px[x, y][:3] != px[0, y][:3]:
                px[x, y] = noisy(hair, rnd, 5) + (255,)

    # Peaked cap: crown, band and a dark visor shading the brow.
    for y in range(3, 9):
        for x in range(8, 24):
            dx = (x - 15.5) / 8.0
            dy = (y - 8.0) / 5.0
            if dx * dx + dy * dy <= 1.0:
                px[x, y] = noisy(cap, rnd, 5) + (255,)
    for x in range(9, 23):
        px[x, 8] = band + (255,)
    for x in range(10, 22):
        px[x, 9] = (18, 18, 22, 255)
    for x in range(11, 21):
        px[x, 10] = tuple(clamp(c * 0.7) for c in skin) + (255,)
    # Cap badge.
    px[15, 6] = (rim if navy else (200, 200, 206)) + (255,)
    px[16, 6] = (rim if navy else (200, 200, 206)) + (255,)
    px[15, 5] = trim + (255,)
    px[16, 5] = trim + (255,)

    # Brows, eyes, mouth: a level, tired stare.
    for x in (12, 13, 18, 19):
        px[x, 11] = tuple(clamp(c * 0.9) for c in hair) + (255,)
    px[13, 12] = eyes + (255,)
    px[18, 12] = eyes + (255,)
    px[12, 12] = (226, 222, 214, 255)
    px[19, 12] = (226, 222, 214, 255)
    for x in range(14, 18):
        px[x, 16] = tuple(clamp(c * 0.72) for c in skin) + (255,)
    if not navy:
        # A thin old scar along the jaw.
        for (x, y) in [(19, 15), (19, 16), (18, 17)]:
            px[x, y] = (232, 204, 190, 255)

    # A thin rim light in the army's colour on the right edge of the silhouette (cap, face, shoulder).
    for y in range(3, 32):
        for x in range(30, 15, -1):
            if px[x, y] != background[x, y]:
                px[x, y] = tuple(clamp(px[x, y][i] * 0.55 + rim[i] * 0.45) for i in range(3)) + (255,)
                break
    return img


def officer_portraits():
    out("gui/sprites/portrait/officer_republic.png", officer_portrait(True, 43))
    out("gui/sprites/portrait/officer_imperial.png", officer_portrait(False, 44))


if __name__ == "__main__":
    journal_item()
    journal_gui()
    resonance_icon()
    push_icon()
    particles()
    archive_record_item()
    fence_book_item()
    officer_portraits()
