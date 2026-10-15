#!/usr/bin/env python3
"""Generates the shared UI kit textures (PLACEHOLDER art, original, deterministic).

Old Republic look: weathered durasteel and brass frames with chamfered corners, rivets and
scratches around dark translucent glass, with holo-projector accents in Force blue-white. The
decorative "notch script" on the glyph strip is invented for this mod (stems and notches on a
baseline, closer to tally marks than to any real alphabet); it imitates no real Star Wars font.

GUI sprites (auto-stitched into the vanilla GUI atlas, drawn with blitSprite; ids oldrepublic:ui/...)
under src/main/resources/assets/oldrepublic/textures/gui/sprites/ui/:
  panel.png (+.mcmeta)            32x32  nine-slice 10: main screen frame
  panel_inset.png (+.mcmeta)      16x16  nine-slice 4: recessed well for stat blocks
  hud_plate.png (+.mcmeta)        16x16  nine-slice 5: light HUD frame on translucent glass
  button*.png (+.mcmeta)          64x16  nine-slice 4: normal / hover / pressed / disabled
  card*.png (+.mcmeta)            32x32  nine-slice 10: ability card normal / selected / locked
  socket.png                      20x20  workbench part socket (bevelled ring, brass corner ticks)
  slot.png                        18x18  inventory slot recess
  emblem.png / emblem_large.png   20x20 / 28x28  attunement badge (octagonal brass rim)
  hint_<part>.png                 16x16  ghost silhouettes for empty sockets (tinted in code)
  branch_<branch>.png             32x32  Force Push branch illustrations (holo line art)
  journal_seal.png                9x9    "new" marker for the journal (wax seal)
  journal_ornament.png            64x5   ink flourish divider for the journal
  projector.png                   40x8   holo projector base under the workbench preview
Plain textures (drawn with blit and explicit UVs):
  textures/gui/ui/glyph_strip.png 128x7  decorative notch-script strip (white, tinted in code)

Run from the repository root: python3 tools/assets/gen_textures_ui.py
"""
import json
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "assets", "oldrepublic", "textures", "gui")
SPRITES = os.path.join(ROOT, "sprites", "ui")

# Palette (RGB)
OUTLINE = (10, 13, 17)
STEEL_DK = (34, 40, 47)
STEEL = (62, 71, 81)
STEEL_LT = (104, 114, 126)
STEEL_HI = (150, 160, 170)
BRASS_DK = (104, 80, 46)
BRASS = (160, 126, 70)
BRASS_LT = (214, 180, 116)
GLASS = (14, 20, 27)
HOLO = (143, 216, 255)
HOLO_DIM = (62, 126, 166)
AMBER = (255, 179, 71)


def save(path, img):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    print("wrote", os.path.normpath(path))


def sprite(name, img, nine_slice=None):
    """Writes a GUI sprite; nine_slice=(border) also writes the .mcmeta scaling metadata."""
    path = os.path.join(SPRITES, name + ".png")
    save(path, img)
    meta = path + ".mcmeta"
    if nine_slice is not None:
        data = {"gui": {"scaling": {"type": "nine_slice", "width": img.width, "height": img.height, "border": nine_slice}}}
        with open(meta, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2)
            f.write("\n")
        print("wrote", os.path.normpath(meta))
    elif os.path.exists(meta):
        os.remove(meta)


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


def framed(w, h, cut, frame, glass, glass_alpha, seed, brass_trim=None, rivets=(), scratches=0, noise=7, corner=None):
    """A chamfered durasteel frame of `frame` px around tinted glass.

    Scratches are kept inside the `corner` x `corner` corner regions (the parts a nine-slice never
    repeats), so they do not turn into a visible pattern along tiled edges.
    """
    rnd = random.Random(seed)
    img = new(w, h)
    px = img.load()
    for y in range(h):
        for x in range(w):
            if not inside_chamfer(x, y, w, h, cut):
                continue
            d = ring_depth(x, y, w, h, cut)
            if d == 0:
                px[x, y] = OUTLINE + (255,)
            elif d < frame:
                side = lit_side(x, y, w, h)
                base = STEEL_LT if (d == 1 and side > 0) else STEEL_DK if (d == frame - 1 or side < 0 and d == 1) else STEEL
                px[x, y] = noisy(base, rnd, noise) + (255,)
            elif brass_trim is not None and d == frame:
                px[x, y] = noisy(brass_trim, rnd, 6) + (255,)
            elif brass_trim is not None and d == frame + 1:
                px[x, y] = OUTLINE + (230,)
            else:
                px[x, y] = noisy(glass, rnd, 2) + (glass_alpha,)
    def in_corner(x, y):
        return corner is None or ((x < corner or x >= w - corner) and (y < corner or y >= h - corner))

    for _ in range(scratches):
        x0, y0 = rnd.randrange(1, w - 3), rnd.randrange(1, h - 3)
        length = rnd.randint(2, 4)
        for i in range(length):
            x, y = x0 + i, y0 + i
            if x < w and y < h and in_corner(x, y) and inside_chamfer(x, y, w, h, cut) and 0 < ring_depth(x, y, w, h, cut) < frame:
                px[x, y] = noisy(STEEL_HI, rnd, 8) + (255,)
    for rx, ry in rivets:
        rivet(px, rx, ry)
    return img


def rivet(px, x, y):
    px[x, y] = STEEL_HI + (255,)
    px[x + 1, y] = STEEL_LT + (255,)
    px[x, y + 1] = STEEL_LT + (255,)
    px[x + 1, y + 1] = OUTLINE + (255,)


def holo_bracket(px, x, y, dx, dy, length=3, color=HOLO, alpha=200):
    """A small L-shaped holo-light bracket starting at (x, y) and running along dx and dy."""
    for i in range(length):
        px[x + dx * i, y] = color + (alpha - i * 40,)
        px[x, y + dy * i] = color + (alpha - i * 40,)


# ---------------------------------------------------------------- frames
def panel():
    w = h = 32
    img = framed(w, h, cut=5, frame=4, glass=GLASS, glass_alpha=228, seed=11, brass_trim=BRASS,
                 rivets=[(3, 6), (6, 3), (27, 6), (24, 3), (3, 24), (6, 27), (27, 24), (24, 27)], scratches=24, corner=10)
    px = img.load()
    # Holo-light corner brackets just inside the trim (inside the 10 px corners, so they never tile)
    holo_bracket(px, 7, 7, 1, 1)
    holo_bracket(px, 24, 7, -1, 1)
    holo_bracket(px, 7, 24, 1, -1)
    holo_bracket(px, 24, 24, -1, -1)
    sprite("panel", img, nine_slice=10)


def panel_inset():
    rnd = random.Random(12)
    w = h = 16
    img = new(w, h)
    px = img.load()
    for y in range(h):
        for x in range(w):
            if not inside_chamfer(x, y, w, h, 2):
                continue
            d = ring_depth(x, y, w, h, 2)
            side = lit_side(x, y, w, h)
            if d == 0:
                # Recessed: shadow on the top/left rim, light catching the bottom/right rim
                px[x, y] = (STEEL_LT if side < 0 else OUTLINE) + (255,)
            elif d == 1 and side > 0:
                px[x, y] = (6, 9, 12, 235)
            else:
                px[x, y] = noisy((10, 15, 21), rnd, 2) + (215,)
    sprite("panel_inset", img, nine_slice=4)


def hud_plate():
    w = h = 16
    img = framed(w, h, cut=3, frame=2, glass=(12, 18, 25), glass_alpha=150, seed=13, noise=5)
    px = img.load()
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px[x, y] = BRASS_LT + (255,)
    sprite("hud_plate", img, nine_slice=5)


def button(name, face_top, face_bottom, outline, strip, strip_alpha, seed, pressed=False, alpha=255):
    rnd = random.Random(seed)
    w, h = 64, 16
    img = new(w, h)
    px = img.load()
    for y in range(h):
        for x in range(w):
            if not inside_chamfer(x, y, w, h, 3):
                continue
            d = ring_depth(x, y, w, h, 3)
            t = y / (h - 1)
            if d == 0:
                px[x, y] = outline + (alpha,)
                continue
            face = mix(face_top, face_bottom, t)
            if pressed:
                face = mix(face_bottom, face_top, t * 0.6)
            if d == 1:
                side = lit_side(x, y, w, h)
                if pressed:
                    side = -side
                face = mix(face, (255, 255, 255), 0.18) if side > 0 else mix(face, (0, 0, 0), 0.35)
            px[x, y] = noisy(face, rnd, 3) + (alpha,)
    if strip is not None:
        # Holo indicator strip two rows above the bottom edge (tiles cleanly along the width)
        for x in range(4, w - 4):
            px[x, h - 3] = strip + (strip_alpha,)
    sprite(name, img, nine_slice=4)


def buttons():
    button("button", (78, 88, 99), (44, 51, 59), OUTLINE, HOLO_DIM, 170, 21)
    button("button_hover", (92, 104, 116), (52, 61, 70), (120, 200, 240), HOLO, 255, 22)
    button("button_pressed", (70, 80, 90), (36, 42, 49), (170, 228, 255), (220, 244, 255), 255, 23, pressed=True)
    button("button_disabled", (46, 50, 55), (34, 37, 41), (22, 25, 28), None, 0, 24, alpha=225)


def card(name, frame_color, trim, glow, hazard, seed):
    w = h = 32
    img = framed(w, h, cut=4, frame=3, glass=(13, 19, 26), glass_alpha=215, seed=seed, brass_trim=trim,
                 rivets=[(3, 5), (27, 5), (3, 25), (27, 25)], scratches=16, corner=10)
    px = img.load()
    if frame_color is not None:
        # Recolour the outline for state (selected brass, locked dull)
        for y in range(h):
            for x in range(w):
                if inside_chamfer(x, y, w, h, 4) and ring_depth(x, y, w, h, 4) == 0:
                    px[x, y] = frame_color + (255,)
    if glow is not None:
        for y in range(h):
            for x in range(w):
                if inside_chamfer(x, y, w, h, 4) and ring_depth(x, y, w, h, 4) == 5:
                    px[x, y] = glow + (110,)
    if hazard:
        # Dim amber hazard notches in the bottom-right corner (x, y >= 22: inside the corner, never tiles)
        for i in range(2):
            for k in range(3):
                x, y = 25 - i * 3 + k, 24 + k
                if 22 <= x < w and inside_chamfer(x, y, w, h, 4):
                    px[x, y] = (150, 104, 40, 230)
    sprite(name, img, nine_slice=10)


def cards():
    card("card", None, None, None, False, 31)
    card("card_selected", BRASS_LT, BRASS_LT, HOLO, False, 32)
    card("card_locked", (24, 27, 31), None, None, True, 33)


def socket():
    """20x20: fits around a 16x16 slot with a 2 px rim, so sockets 22 px apart keep a 2 px gap."""
    rnd = random.Random(41)
    w = h = 20
    img = new(w, h)
    px = img.load()
    for y in range(h):
        for x in range(w):
            if not inside_chamfer(x, y, w, h, 3):
                continue
            d = ring_depth(x, y, w, h, 3)
            side = lit_side(x, y, w, h)
            if d == 0:
                px[x, y] = OUTLINE + (255,)
            elif d == 1:
                px[x, y] = noisy(STEEL_LT if side > 0 else STEEL, rnd, 6) + (255,)
            elif d == 2:
                px[x, y] = ((96, 106, 118) if side < 0 else (4, 6, 9)) + (255,)
            else:
                px[x, y] = noisy((12, 17, 23), rnd, 2) + (240,)
    # Brass corner ticks on the rim
    for (x, y) in ((1, 3), (3, 1), (18, 3), (16, 1), (1, 16), (3, 18), (18, 16), (16, 18)):
        px[x, y] = BRASS_LT + (255,)
    sprite("socket", img)


def slot():
    rnd = random.Random(42)
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
                px[x, y] = ((96, 106, 118) if side < 0 else (8, 11, 15)) + (255,)
            else:
                px[x, y] = noisy((24, 30, 37), rnd, 3) + (235,)
    sprite("slot", img)


def emblem(name, size, seed):
    rnd = random.Random(seed)
    img = new(size, size)
    px = img.load()
    cut = size // 3
    rim = max(2, size // 8)
    for y in range(size):
        for x in range(size):
            if not inside_chamfer(x, y, size, size, cut):
                continue
            d = ring_depth(x, y, size, size, cut)
            side = lit_side(x, y, size, size)
            if d == 0:
                px[x, y] = OUTLINE + (255,)
            elif d <= rim:
                base = BRASS_LT if side > 0 and d == 1 else BRASS_DK if d == rim else BRASS
                px[x, y] = noisy(base, rnd, 8) + (255,)
            elif d == rim + 1:
                px[x, y] = OUTLINE + (255,)
            else:
                # Deep blue enamel field, lighter toward the top
                t = y / size
                px[x, y] = noisy(mix((30, 52, 74), (12, 20, 30), t), rnd, 3) + (255,)
    # Notches at the four compass points of the rim
    c = size // 2
    for x, y in ((c - 1, 1), (c, 1), (c - 1, size - 2), (c, size - 2), (1, c - 1), (1, c), (size - 2, c - 1), (size - 2, c)):
        px[x, y] = OUTLINE + (255,)
    sprite(name, img)


# ---------------------------------------------------------------- icons
def supersampled(size, scale, draw_fn, blur=0.0):
    big = Image.new("RGBA", (size * scale, size * scale), (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(big), scale)
    if blur:
        glow = big.filter(ImageFilter.GaussianBlur(blur * scale))
        big = Image.alpha_composite(glow, big)
    return big.resize((size, size), Image.LANCZOS)


def hint(name, polys):
    def draw(d, s):
        for kind, pts in polys:
            scaled = [(x * s, y * s) for x, y in pts]
            if kind == "poly":
                d.polygon(scaled, fill=(255, 255, 255, 255))
            elif kind == "rect":
                d.rectangle(scaled, fill=(255, 255, 255, 255))
            elif kind == "line":
                d.line(scaled, fill=(255, 255, 255, 255), width=s)
    sprite("hint_" + name, supersampled(16, 4, draw))


def hints():
    hint("crystal", [("poly", [(8, 1.5), (11, 5), (10.5, 12), (8, 14.5), (5.5, 12), (5, 5)])])
    hint("emitter", [("poly", [(4.5, 2), (11.5, 2), (10.5, 6), (10.5, 14), (5.5, 14), (5.5, 6)])])
    hint("grip", [("rect", [(5.5, 1.5), (10.5, 14.5)])] + [("line", [(4.5, y), (11.5, y)]) for y in (3.5, 6.5, 9.5, 12.5)])
    hint("pommel", [("rect", [(5.5, 1.5), (10.5, 9)]), ("poly", [(4.5, 9), (11.5, 9), (10.5, 13.5), (8, 14.5), (5.5, 13.5)])])
    hint("saber", [("line", [(3, 13), (7, 9)]), ("line", [(3.5, 13.5), (7.5, 9.5)]), ("line", [(7, 9), (13.5, 2.5)])])


def branch_icons():
    holo = HOLO + (255,)
    core = (226, 246, 255, 255)
    dim = HOLO_DIM + (200,)

    def palm(d, s, x, y):
        # A stylised open palm in profile, fingers to the right
        d.rounded_rectangle([x * s, (y - 3) * s, (x + 5) * s, (y + 3) * s], radius=2 * s, fill=(200, 222, 236, 235))
        for i, fy in enumerate((-3, -1.5, 0, 1.5)):
            d.rounded_rectangle([(x + 4) * s, (y + fy) * s, (x + 8 - i * 0.6) * s, (y + fy + 1.2) * s], radius=s // 2, fill=(200, 222, 236, 235))

    def repulse(d, s):
        palm(d, s, 2, 16)
        # Wide, low bent-air arcs fanning out
        for i, r in enumerate((7, 12, 17, 22)):
            box = [(10 - r) * s, (16 - r * 0.8) * s, (10 + r) * s, (16 + r * 0.8) * s]
            d.arc(box, start=-58, end=58, fill=holo if i < 3 else dim, width=max(1, int(s * (1.6 - i * 0.25))))
        # Dust thrown low along the ground line
        for i, x in enumerate((15, 19, 23, 27, 29)):
            d.ellipse([x * s, (26 + (i % 2)) * s, (x + 1.2) * s, (27.2 + (i % 2)) * s], fill=dim)

    def focused(d, s):
        palm(d, s, 2, 16)
        # A tight spear of compressed air converging on one point
        for off, a in ((-2.2, dim), (2.2, dim), (-1.1, holo), (1.1, holo)):
            d.line([(10 * s, (16 + off) * s), (25 * s, 16 * s)], fill=a, width=s)
        d.line([(10 * s, 16 * s), (26 * s, 16 * s)], fill=core, width=int(s * 1.5))
        # The wall it slams into, with debris kicked out
        d.rectangle([27 * s, 6 * s, 30 * s, 26 * s], fill=(110, 120, 132, 230))
        for x, y in ((24, 10), (23, 21), (25, 7), (22, 13)):
            d.rectangle([x * s, y * s, (x + 1.4) * s, (y + 1.4) * s], fill=(170, 180, 190, 230))

    def deflect(d, s):
        # The caster at the centre and a broken shell of bent air all around
        d.ellipse([13 * s, 13 * s, 19 * s, 19 * s], fill=(200, 222, 236, 235))
        for i, r in enumerate((9, 12.5)):
            for start in (20, 110, 200, 290):
                d.arc([(16 - r) * s, (16 - r) * s, (16 + r) * s, (16 + r) * s], start=start + i * 25, end=start + 55 + i * 25,
                      fill=holo if i == 0 else dim, width=int(s * (1.5 - i * 0.4)))
        # Two bolts turned back outward
        for (x0, y0, x1, y1) in ((26, 5, 30, 1), (4, 25, 1, 30)):
            d.line([(x0 * s, y0 * s), (x1 * s, y1 * s)], fill=(255, 120, 90, 255), width=int(s * 1.4))

    for name, fn in (("repulse_wave", repulse), ("focused_blast", focused), ("deflecting_pulse", deflect)):
        sprite("branch_" + name, supersampled(32, 4, fn, blur=0.8))


# ---------------------------------------------------------------- journal and projector
def journal_seal():
    def draw(d, s):
        d.ellipse([0.5 * s, 0.8 * s, 8.5 * s, 8.6 * s], fill=(92, 22, 18, 255))
        d.ellipse([1.2 * s, 1.2 * s, 7.8 * s, 7.8 * s], fill=(140, 38, 28, 255))
        d.ellipse([2.6 * s, 2.6 * s, 6.4 * s, 6.4 * s], outline=(96, 24, 18, 255), width=s)
        d.line([(4.5 * s, 3 * s), (4.5 * s, 6 * s)], fill=(200, 92, 70, 255), width=s)
    sprite("journal_seal", supersampled(9, 4, draw))


def journal_ornament():
    w, h = 64, 5
    img = new(w, h)
    px = img.load()
    ink = (92, 42, 23)
    for x in range(w):
        dist = abs(x - (w - 1) / 2) / ((w - 1) / 2)
        alpha = clamp(200 * (1 - dist ** 2.2))
        px[x, 2] = ink + (alpha,)
    c = w // 2
    for dx, dy in ((0, 0), (-1, 1), (1, 1), (0, 2), (-1, 3), (1, 3), (0, 4), (-2, 2), (2, 2)):
        px[c + dx - 1, dy] = ink + (235,)
    for x in (c - 12, c + 10):
        px[x, 1] = ink + (150,)
        px[x + 1, 3] = ink + (150,)
    sprite("journal_ornament", img)


def projector():
    rnd = random.Random(61)
    w, h = 40, 8
    img = new(w, h)
    px = img.load()
    for y in range(h):
        for x in range(w):
            if not inside_chamfer(x, y, w, h, 3):
                continue
            d = ring_depth(x, y, w, h, 3)
            if d == 0:
                px[x, y] = OUTLINE + (255,)
            elif y <= 2:
                px[x, y] = noisy(BRASS if d > 1 else BRASS_LT, rnd, 8) + (255,)
            else:
                px[x, y] = noisy(STEEL_DK if (x // 3) % 2 else STEEL, rnd, 5) + (255,)
    # Emitter lens slit
    for x in range(14, 26):
        px[x, 1] = (220, 244, 255, 255) if 16 <= x < 24 else HOLO + (255,)
    sprite("projector", img)


def glyph_strip():
    """Invented notch script: stems and notches on a shared baseline, tinted in code."""
    rnd = random.Random(71)
    w, h = 128, 7
    img = new(w, h)
    px = img.load()
    base = 3
    for x in range(w):
        px[x, base] = (255, 255, 255, 120)
    x = 1
    while x < w - 4:
        kind = rnd.randrange(6)
        up = rnd.random() < 0.5
        sign = -1 if up else 1
        if kind == 0:  # tall stem crossing the line
            for y in range(0, h):
                px[x, y] = (255, 255, 255, 255)
            x += 3
        elif kind == 1:  # stem with a side notch
            for k in range(1, 4):
                px[x, base + sign * k] = (255, 255, 255, 255)
            px[x + 1, base + sign * 3] = (255, 255, 255, 255)
            x += 4
        elif kind == 2:  # paired short notches
            for k in range(1, 3):
                px[x, base + sign * k] = (255, 255, 255, 255)
                px[x + 2, base - sign * k] = (255, 255, 255, 255)
            x += 5
        elif kind == 3:  # dot above and below
            px[x, base - 2] = (255, 255, 255, 255)
            px[x, base + 2] = (255, 255, 255, 255)
            x += 3
        elif kind == 4:  # slanted notch
            for k in range(1, 4):
                px[x + k - 1, base + sign * k] = (255, 255, 255, 255)
            x += 5
        else:  # word gap: a break in the line
            for k in range(3):
                if x + k < w:
                    px[x + k, base] = (0, 0, 0, 0)
            x += 4
    save(os.path.join(ROOT, "ui", "glyph_strip.png"), img)


def main():
    panel()
    panel_inset()
    hud_plate()
    buttons()
    cards()
    socket()
    slot()
    emblem("emblem", 20, 51)
    emblem("emblem_large", 28, 52)
    hints()
    branch_icons()
    journal_seal()
    journal_ornament()
    projector()
    glyph_strip()


if __name__ == "__main__":
    main()
