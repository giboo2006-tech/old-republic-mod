#!/usr/bin/env python3
"""Generates the Forge tab's GUI sprites (docs/MILESTONE_7.md 5.3; PLACEHOLDER art, original, deterministic).

The same kit as tools/assets/gen_textures_ui.py (weathered durasteel and brass around dark glass, holo
accents in Force blue-white), with the forge's own warmth: a brass anvil with a hot seam, and ingot plates
the screen tints in each material's colour. Every sprite is drawn at its own size, so none is nine-sliced.

GUI sprites (auto-stitched into the vanilla GUI atlas, drawn with blitSprite; ids oldrepublic:forge/...)
under src/main/resources/assets/oldrepublic/textures/gui/sprites/forge/:
  tile.png                  18x18  a design tile (a recess with a steel rim; tiles 17 px apart share rims)
  tile_selected.png         18x18  the chosen design: brass rim, holo inner ring, lit glass
  tile_locked.png           18x18  a design not known yet: dull rim, dark hatched glass
  lock.png                  6x7    padlock over a locked tile (white, tinted in code)
  socket_mark.png           5x5    brass flag on the tile of the part sitting in its socket
  chip.png                  14x14  a material chip (bevelled steel frame round a recess)
  chip_selected.png         14x14  the chosen material's chip: brass frame, holo ring
  plate.png                 10x6   the ingot plate inside a chip (light grey, tinted in the material's colour)
  anvil.png                 44x6   the forge well's anvil under the chosen part (brass face, hot seam)
  stat_<stat>.png           7x7    the stat change's icons: damage, speed, reach, guard, parry (white, tinted)

Run from the repository root: python3 tools/assets/gen_textures_forge.py [--preview DIR]
(--preview also writes an 8x contact sheet of every sprite to DIR.)
"""
import argparse
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "assets", "oldrepublic", "textures", "gui")
SPRITES = os.path.join(ROOT, "sprites", "forge")

# Palette (RGB), as gen_textures_ui.py
OUTLINE = (10, 13, 17)
STEEL_DK = (34, 40, 47)
STEEL = (62, 71, 81)
STEEL_LT = (104, 114, 126)
STEEL_HI = (150, 160, 170)
BRASS_DK = (104, 80, 46)
BRASS = (160, 126, 70)
BRASS_LT = (214, 180, 116)
HOLO = (143, 216, 255)
HOLO_DIM = (62, 126, 166)
# The forge's own: the rim the tiles share, their glass, and the anvil's hot seam
RIM = (22, 27, 33)
TILE_GLASS = (24, 30, 37)
HOT = (255, 154, 60)
HOT_CORE = (255, 226, 166)

WRITTEN = []


def save(path, img):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    print("wrote", os.path.normpath(path))


def sprite(name, img):
    """Writes a GUI sprite drawn at its own size (a stale nine-slice .mcmeta beside it is removed)."""
    path = os.path.join(SPRITES, name + ".png")
    save(path, img)
    meta = path + ".mcmeta"
    if os.path.exists(meta):
        os.remove(meta)
    WRITTEN.append((name, img))


def clamp(v):
    return max(0, min(255, int(round(v))))


def noisy(rgb, rnd, amount):
    return tuple(clamp(c + rnd.uniform(-amount, amount)) for c in rgb)


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def new(w, h):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))


def inside_chamfer(x, y, w, h, cut):
    """True when (x, y) lies inside a w x h rectangle whose corners are cut diagonally by `cut`."""
    for cx, cy in ((x, y), (w - 1 - x, y), (x, h - 1 - y), (w - 1 - x, h - 1 - y)):
        if cx + cy < cut:
            return False
    return True


def ring_depth(x, y, w, h, cut):
    """Distance in pixels from the chamfered outline (0 = outline pixel)."""
    d = min(x, y, w - 1 - x, h - 1 - y)
    for cx, cy in ((x, y), (w - 1 - x, y), (x, h - 1 - y), (w - 1 - x, h - 1 - y)):
        d = min(d, cx + cy - cut)
    return d


def lit_side(x, y, w, h):
    """+1 on the top/left (lit) half of a frame, -1 on the bottom/right (shadow) half."""
    return 1 if x + y < (w + h) / 2 - 1 else -1


def glyph(rows, shade=None):
    """A white glyph from '#' rows ('+' a grey detail pixel), for sprites tinted in code."""
    h, w = len(rows), len(rows[0])
    img = new(w, h)
    px = img.load()
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c == "#":
                px[x, y] = (255, 255, 255, 255)
            elif c == "+":
                px[x, y] = (shade or (176, 176, 176)) + (255,)
    return img


# ---------------------------------------------------------------- design tiles
def tile(name, rim, bevel_lit, bevel_shadow, glass, glass_alpha, seed, glow=None, hatch=None, corners=None):
    """18x18: the shared rim, a 1 px bevel (dark on the lit top-left, a lip of light bottom-right) and glass."""
    rnd = random.Random(seed)
    w = h = 18
    img = new(w, h)
    px = img.load()
    for y in range(h):
        for x in range(w):
            if not inside_chamfer(x, y, w, h, 1):
                continue
            d = ring_depth(x, y, w, h, 1)
            side = lit_side(x, y, w, h)
            if d == 0:
                px[x, y] = noisy(rim, rnd, 3) + (255,)
            elif d == 1:
                if glow is not None:
                    px[x, y] = glow + (170 if side > 0 else 120,)
                else:
                    px[x, y] = noisy(bevel_lit if side > 0 else bevel_shadow, rnd, 3) + (255,)
            else:
                # The glass warms a touch toward the bottom, where the part sits.
                base = mix(glass, glow if glow is not None else STEEL, 0.10 * (y - 2) / (h - 4))
                if hatch is not None and (x + y) % 4 == 0:
                    base = hatch
                px[x, y] = noisy(base, rnd, 2) + (glass_alpha,)
    for (x, y) in corners or ():
        px[x, y] = BRASS_LT + (255,)
    sprite(name, img)


def tiles():
    tile("tile", RIM, (8, 11, 15), (70, 80, 92), TILE_GLASS, 235, 701)
    tile("tile_selected", BRASS_LT, None, None, (28, 40, 52), 240, 702, glow=HOLO,
         corners=[(1, 1), (16, 1), (1, 16), (16, 16)])
    tile("tile_locked", RIM, (6, 8, 11), (44, 50, 58), (14, 18, 23), 235, 703, hatch=(24, 29, 35))


def lock():
    sprite("lock", glyph([
        ".####.",
        ".#..#.",
        ".#..#.",
        "######",
        "##..##",
        "######",
        "++++++",
    ]))


def socket_mark():
    """5x5: a brass corner flag, lit along its two outer edges, with a dark hem along the diagonal."""
    img = new(5, 5)
    px = img.load()
    for y in range(5):
        for x in range(5):
            s = x + y
            if s <= 3:
                px[x, y] = (BRASS_LT if x == 0 or y == 0 else BRASS) + (255,)
            elif s == 4:
                px[x, y] = BRASS_DK + (230,)
    sprite("socket_mark", img)


# ---------------------------------------------------------------- material chips
def chip(name, seed, selected):
    """14x14: a chamfered frame round a recess; the plate sits at (2, 2), the tier pips on row 10."""
    rnd = random.Random(seed)
    w = h = 14
    img = new(w, h)
    px = img.load()
    for y in range(h):
        for x in range(w):
            if not inside_chamfer(x, y, w, h, 2):
                continue
            d = ring_depth(x, y, w, h, 2)
            side = lit_side(x, y, w, h)
            if d == 0:
                px[x, y] = (BRASS_LT if selected else OUTLINE) + (255,)
            elif d == 1:
                if selected:
                    px[x, y] = noisy(BRASS if side > 0 else BRASS_DK, rnd, 5) + (255,)
                else:
                    px[x, y] = noisy(STEEL_LT if side > 0 else STEEL_DK, rnd, 5) + (255,)
            elif d == 2 and selected:
                px[x, y] = HOLO + (120,)
            else:
                px[x, y] = noisy((20, 30, 40) if selected else (12, 17, 23), rnd, 2) + (240,)
    sprite(name, img)


def plate():
    """10x6: an ingot, lit top face to shadowed foot, light grey so the material's tint shows true."""
    rows = [
        "..AAAAAA..",
        ".ABBBBBBC.",
        "DBBEBBBBBF",
        "DBEBBBBBBF",
        "DBBBBBBBBF",
        "GGGGGGGGGG",
    ]
    shades = {"A": 255, "B": 222, "C": 200, "D": 190, "E": 255, "F": 168, "G": 132}
    img = new(10, 6)
    px = img.load()
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c in shades:
                v = shades[c]
                px[x, y] = (v, v, v, 255)
    sprite("plate", img)


# ---------------------------------------------------------------- the well
def anvil():
    """44x6: the anvil the chosen part stands on: a lit brass face with a hot seam, a vented steel body."""
    rnd = random.Random(704)
    w, h = 44, 6
    img = new(w, h)
    px = img.load()
    for y in range(h):
        for x in range(w):
            if not inside_chamfer(x, y, w, h, 2):
                continue
            d = ring_depth(x, y, w, h, 2)
            if d == 0:
                px[x, y] = OUTLINE + (255,)
            elif y == 1:
                px[x, y] = noisy(BRASS_LT, rnd, 6) + (255,)
            elif y == 2:
                px[x, y] = noisy(BRASS, rnd, 6) + (255,)
            else:
                px[x, y] = noisy(STEEL_DK if (x // 3) % 2 else STEEL, rnd, 5) + (255,)
    # The hot seam across the face, white-hot in the middle and cooling to forge orange.
    mid = (w - 1) / 2
    for x in range(12, w - 12):
        t = abs(x - mid) / (mid - 12)
        px[x, 1] = mix(HOT_CORE, HOT, t) + (255,)
        px[x, 2] = mix(HOT, BRASS, min(1.0, 0.3 + t)) + (255,)
    # Rivets at the ends of the body.
    for x in (4, w - 5):
        px[x, 3] = STEEL_HI + (255,)
        px[x, 4] = OUTLINE + (255,)
    sprite("anvil", img)


# ---------------------------------------------------------------- stat icons (screen order)
STATS = {
    "damage": [  # a blade, its crossguard and its grip
        "......#",
        ".....#.",
        "....#..",
        ".#.#...",
        "..#....",
        ".#.#...",
        "#......",
    ],
    "speed": [  # two chevrons
        ".......",
        "#..#...",
        ".#..#..",
        "..#..#.",
        ".#..#..",
        "#..#...",
        ".......",
    ],
    "reach": [  # a bar and an arrow away from it
        ".......",
        "#...#..",
        "#....#.",
        "#######",
        "#....#.",
        "#...#..",
        ".......",
    ],
    "guard": [  # a shield with a centre rib
        ".#####.",
        "###+###",
        "###+###",
        "###+###",
        ".##+##.",
        "..###..",
        "...#...",
    ],
    "parry": [  # two crossed blades, their hilts at the foot
        "#.....#",
        ".#...#.",
        "..#.#..",
        "...#...",
        "..#.#..",
        ".##.##.",
        "##...##",
    ],
}


def stat_icons():
    for name, rows in STATS.items():
        sprite("stat_" + name, glyph(rows, shade=(150, 150, 150)))


def preview(out_dir, scale=8):
    """A contact sheet of every sprite at `scale`, on the panel's glass, the tinted ones in a sample tint."""
    pad = 6
    cells = []
    for name, img in WRITTEN:
        shown = img
        if name in ("lock", "plate") or name.startswith("stat_"):
            tint = (224, 200, 120) if name == "plate" else (162, 176, 188)
            r, g, b, a = img.split()
            shown = Image.merge("RGBA", (r.point(lambda v: v * tint[0] // 255), g.point(lambda v: v * tint[1] // 255),
                                         b.point(lambda v: v * tint[2] // 255), a))
        cells.append(shown.resize((img.width * scale, img.height * scale), Image.NEAREST))
    w = sum(c.width for c in cells) + pad * (len(cells) + 1)
    h = max(c.height for c in cells) + pad * 2
    sheet = Image.new("RGBA", (w, h), (14, 20, 27, 255))
    x = pad
    for c in cells:
        sheet.alpha_composite(c, (x, pad))
        x += c.width + pad
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, "forge_sprites.png")
    sheet.save(path)
    print("wrote", path)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--preview", metavar="DIR", help="also write a contact sheet of the sprites to DIR")
    args = parser.parse_args()
    tiles()
    lock()
    socket_mark()
    chip("chip", 705, False)
    chip("chip_selected", 706, True)
    plate()
    anvil()
    stat_icons()
    if args.preview:
        preview(args.preview)


if __name__ == "__main__":
    main()
