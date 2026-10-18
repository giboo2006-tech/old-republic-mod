#!/usr/bin/env python3
"""Generates the Milestone 4 "Force and foes" textures (PLACEHOLDER art, original, deterministic).

Everything here is drawn by this script from simple shapes and seeded noise; nothing is copied or
traced from any film, game or other existing asset. Run from the repository root:

    python3 tools/assets/gen_textures_foes.py            # everything
    python3 tools/assets/gen_textures_foes.py --part skill

Parts:
  particle  textures/particle/held_dust_{0,1,2}.png (8x8 grey specks, tinted in code)
  skill     textures/gui/sprites/skill/<node>.png for the Milestone 4 nodes (16x16, the
            gen_textures_skill.py style and palettes)
  entity    textures/entity/{mandalorian,kaast,droid,creature}/*.png: the hunters' four paints,
            Kaast (and cracked), the rocket, the net, the mine, the security droid (crew, Imperial,
            eye layer), the seeker and Rattle, the hollowhorn (coat, bull, corrupted), the ash hound
  item      textures/item/<id>.png: the creature drops, the droid actuator, the meat, four spawn eggs
  block     textures/block/camp_gate.png
"""
import argparse
import math
import os
import random
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gen_textures_skill as sk  # noqa: E402  (the shared skill icon pen and palettes)

ASSETS = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "oldrepublic")
TEX = os.path.join(ASSETS, "textures")


def save(img, *parts):
    path = os.path.join(TEX, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    print("wrote", os.path.normpath(path))


# ---------------------------------------------------------------- particles

def held_dust():
    """Three irregular 8x8 specks: a clump, a flake and a pair of grains. Grey, so code tints them."""
    shapes = [
        ["...##...", "..####..", ".#####..", ".######.", "..#####.", "...###..", "........", "........"],
        ["........", ".##.....", ".####...", "..#####.", "...####.", ".....##.", "........", "........"],
        ["........", "..##....", "..###...", "...#....", "....##..", "....###.", ".....#..", "........"],
    ]
    rng = random.Random(4061)
    for i, rows in enumerate(shapes):
        img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
        px = img.load()
        for y, row in enumerate(rows):
            for x, c in enumerate(row):
                if c == "#":
                    v = 200 + rng.randint(-25, 30)
                    edge = any(not (0 <= x + dx < 8 and 0 <= y + dy < 8) or rows[y + dy][x + dx] != "#"
                               for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                    px[x, y] = (v, v, v, 170 if edge else 235)
        save(img, "particle", f"held_dust_{i}.png")


# ---------------------------------------------------------------- skill icons

ICONS = {}


def icon(name, palette):
    def wrap(fn):
        ICONS[name] = (palette, fn)
        return fn
    return wrap


DUST = (176, 160, 132)


@icon("force_speed", sk.UNIVERSAL)
def force_speed(p):
    # A striding figure leaning forward, with bent-air streaks trailing behind.
    p.dot(10.5, 3, 1.3, sk.HAND)
    p.line([(10, 4.5), (8, 9)], sk.HAND, 1.3)
    p.line([(8, 9), (10.5, 12), (12.5, 14.5)], sk.HAND, 1.1)
    p.line([(8, 9), (6, 12), (3.5, 13)], sk.HAND, 1.1)
    p.line([(9.5, 5.5), (12.5, 7.5)], sk.HAND, 1.0)
    p.line([(9.2, 5.8), (6.5, 7.5)], sk.HAND, 1.0)
    for y, x0, x1 in ((5, 1, 5), (8, 0.5, 4.5), (11, 1.5, 4)):
        p.line([(x0, y), (x1, y)], p.main, 0.8)
    for x in (2, 4, 5.5):
        p.dot(x, 15, 0.55, DUST)


@icon("speed_burst", sk.UNIVERSAL)
def speed_burst(p):
    # A short arrow of compressed air leaping forward.
    for y, x0 in ((5, 1), (8, 0), (11, 1)):
        p.line([(x0, y), (x0 + 5, y)], p.dim, 0.8)
    p.poly([(5, 4.5), (12, 4.5), (15, 8), (12, 11.5), (5, 11.5), (8, 8)], p.main)
    p.poly([(7.5, 6.5), (11.5, 6.5), (13, 8), (11.5, 9.5), (7.5, 9.5), (9, 8)], p.core)


@icon("speed_long_road", sk.UNIVERSAL)
def speed_long_road(p):
    # A road vanishing toward the horizon, with a faint streak along it.
    p.poly([(1, 15), (7, 5), (9, 5), (15, 15)], sk.ROCK)
    p.line([(8, 6), (8, 7.5)], p.core, 0.6)
    p.line([(8, 9.5), (8, 11.5)], p.core, 0.7)
    p.line([(8, 13), (8, 15)], p.core, 0.8)
    p.arc(8, 5, 6, 200, 340, p.main, 0.8, ry=3)
    p.dot(8, 2.5, 1.0, p.core)


@icon("speed_wayfarer", sk.EXPLORATION)
def speed_wayfarer(p):
    # A walking staff, and a bolt bending away from it.
    p.line([(5, 2), (4, 15)], sk.ROCK, 1.2)
    p.dot(5, 2, 1.0, p.main)
    p.line([(15, 4), (10.5, 7)], (255, 150, 100), 0.9)
    p.arc(10.5, 11, 4, 200, 280, (255, 150, 100), 0.9)
    p.arc(7, 9, 5.5, -40, 40, p.dim, 0.8)


@icon("force_barrier", sk.JEDI)
def force_barrier(p):
    # Dust held on a sphere (never a drawn shell): a ring of specks around two raised palms.
    for i in range(12):
        a = i / 12 * math.tau + 0.2
        r = 6.6 + (0.6 if i % 3 == 0 else 0)
        p.dot(8 + math.cos(a) * r, 8 + math.sin(a) * r * 0.95, 0.55 if i % 2 else 0.75, DUST if i % 4 else p.main)
    p.palm(7, 7, -1)
    p.palm(9, 9, 1)


@icon("barrier_shelter", sk.JEDI)
def barrier_shelter(p):
    # Two figures under one wide spread of held dust.
    for i in range(11):
        a = math.pi + i / 10 * math.pi
        p.dot(8 + math.cos(a) * 7, 10 + math.sin(a) * 7.5, 0.6, DUST if i % 3 else p.main)
    for x in (5, 11):
        p.dot(x, 8.5, 1.2, sk.HAND)
        p.line([(x, 10), (x, 15)], sk.HAND, 1.4)


@icon("barrier_return", sk.JEDI)
def barrier_return(p):
    # A bolt meeting the held dust and turning back.
    for i in range(7):
        a = -math.pi / 2 + i / 6 * math.pi
        p.dot(4 + math.cos(a) * 6, 8 + math.sin(a) * 6, 0.6, DUST if i % 2 else p.main)
    p.line([(15, 5.5), (10.5, 7.5)], (255, 140, 90), 1.0)
    p.line([(10.5, 8.5), (15, 11)], (255, 196, 150), 1.0)
    p.poly([(15.5, 11.3), (13.2, 11.6), (14.3, 9.7)], (255, 196, 150))


@icon("guardians_patience", sk.JEDI)
def guardians_patience(p):
    # A saber held level and a slow trickle of light rising into the palm.
    p.blade(3, 11, 14, 11)
    p.palm(4, 5, 1)
    for i, y in enumerate((9, 7.5, 6)):
        p.dot(12 - i * 0.6, y, 0.55 + i * 0.1, p.core)


@icon("lift_learned", sk.UNIVERSAL)
def lift_learned(p):
    # A stone rising above an upturned palm, with the bent air between them.
    p.d.rounded_rectangle(p.box(9.5, 13.5, 3.5, 11), radius=sk.S, fill=sk.HAND + (235,))
    for i, fx in enumerate((4, 5.6, 7.2, 8.8)):
        p.d.rounded_rectangle(p.box(fx, 11.5, fx + 1, 9.5 + i * 0.2), radius=sk.S // 2, fill=sk.HAND + (235,))
    p.poly([(5, 4.5), (8, 2), (11.5, 3.5), (11, 6.5), (6.5, 7)], sk.ROCK)
    p.arc(8, 8.5, 3.5, 200, 340, p.main, 0.7, ry=1.2)
    p.dot(13, 9, 0.5, p.dim)
    p.dot(3, 8, 0.5, p.dim)


@icon("force_choke", sk.SITH)
def force_choke(p):
    # A clawed hand closing, and a small figure lifted off the ground beneath it.
    for i, fx in enumerate((3, 5, 7, 9)):
        p.line([(fx, 6), (fx + 0.4, 3), (fx + 1.6, 1.6)], sk.HAND, 0.9)
    p.d.rounded_rectangle(p.box(2.5, 5.5, 11, 8.5), radius=sk.S, fill=sk.HAND + (235,))
    p.dot(9.5, 10, 1.1, p.main)
    p.line([(9.5, 11.2), (9.5, 14)], p.main, 1.1)
    p.line([(9.5, 14), (8.6, 15.3)], p.main, 0.7)
    p.line([(9.5, 14), (10.4, 15.3)], p.main, 0.7)
    for x in (6, 12.5, 13.5):
        p.dot(x, 15.3, 0.45, DUST)


@icon("choke_crush", sk.SITH)
def choke_crush(p):
    # A fist closed hard, with cracks of pressure around it.
    p.d.rounded_rectangle(p.box(4, 4, 12, 11), radius=2 * sk.S, fill=sk.HAND + (235,))
    for fy in (5.5, 7.3, 9.1):
        p.line([(5, fy), (11, fy)], sk.OUTLINE, 0.4)
    for a in (200, 250, 290, 340):
        r = math.radians(a)
        p.line([(8 + math.cos(r) * 6, 8 + math.sin(r) * 6), (8 + math.cos(r) * 7.8, 8 + math.sin(r) * 7.8)], p.main, 0.8)
    p.line([(4, 13.5), (12, 13.5)], p.dim, 0.8)


@icon("choke_dread", sk.SITH)
def choke_dread(p):
    # A held figure in the middle, two smaller ones backing away.
    p.dot(8, 5, 1.2, p.main)
    p.line([(8, 6.4), (8, 10)], p.main, 1.1)
    for x, d in ((3, -1), (13, 1)):
        p.dot(x, 9.5, 0.9, p.dim)
        p.line([(x, 10.5), (x - d * 0.8, 14)], p.dim, 0.9)
        p.line([(x + d * 1.5, 12), (x + d * 3, 12)], p.dim, 0.6)


@icon("cruel_focus", sk.SITH)
def cruel_focus(p):
    # A claw letting go, and a fork of lightning following the drop.
    for fx in (2.5, 4.3, 6.1):
        p.line([(fx, 6), (fx + 0.3, 3), (fx + 1.3, 1.8)], sk.HAND, 0.8)
    p.d.rounded_rectangle(p.box(2, 5.5, 8, 7.8), radius=sk.S, fill=sk.HAND + (235,))
    p.bolt([(9, 4), (11, 7), (9.5, 9), (13, 12.5), (12, 14.5)], 0.8)
    p.bolt([(11, 7), (14, 7.5)], 0.6)


@icon("staff_whirl", sk.SABER)
def staff_whirl(p):
    # A double-bladed staff mid-spin, with the arc of its turn.
    p.line([(6.5, 9.5), (9.5, 6.5)], sk.STEEL, 1.4)
    p.blade(6.5, 9.5, 2, 14, hilt=False)
    p.blade(9.5, 6.5, 14, 2, hilt=False)
    p.arc(8, 8, 7, 120, 200, p.dim, 0.8)
    p.arc(8, 8, 7, 300, 20, p.dim, 0.8)


@icon("lens_mastery", sk.SABER)
def lens_mastery(p):
    # A cut lens catching light, set in a steel ring.
    p.d.ellipse(p.box(3, 3, 13, 13), outline=sk.STEEL + (255,), width=int(1.2 * sk.S))
    p.poly([(8, 4.5), (11.5, 8), (8, 11.5), (4.5, 8)], p.main)
    p.poly([(8, 5.8), (10, 8), (8, 8.6)], p.core)
    p.line([(12.5, 2.5), (15, 1)], p.core, 0.6)


def skill_icons():
    for name, (palette, fn) in sorted(ICONS.items()):
        pen = sk.Pen(palette)
        fn(pen)
        save(pen.done(), "gui", "sprites", "skill", name + ".png")


# ---------------------------------------------------------------- shared painting helpers (entities, items, block)

def clamp(v):
    return max(0, min(255, int(round(v))))


def shade(rgb, f):
    return tuple(clamp(c * f) for c in rgb[:3])


def mix(a, b, t):
    return tuple(clamp(a[i] * (1 - t) + b[i] * t) for i in range(3))


def noisy(rgb, rnd, amount):
    return tuple(clamp(c + rnd.uniform(-amount, amount)) for c in rgb[:3])


def canvas(w, h):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))


def face_rects(u, v, w, h, d):
    """The vanilla cube layout: texture rectangles (x, y, width, height) of a model box's faces."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "east": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


def paint_box(px, u, v, w, h, d, painter):
    """Calls painter(face, fx, fy, fw, fh) for every texel of the box; RGB, RGBA or None (left as is)."""
    for face, (x0, y0, fw, fh) in face_rects(u, v, w, h, d).items():
        for fy in range(fh):
            for fx in range(fw):
                x, y = x0 + fx, y0 + fy
                if 0 <= x < px.size[0] and 0 <= y < px.size[1]:
                    c = painter(face, fx, fy, fw, fh)
                    if c is not None:
                        px.img[x, y] = c if len(c) == 4 else tuple(c) + (255,)


class Px:
    """A pixel access with its size, for paint_box."""

    def __init__(self, img):
        self.img = img.load()
        self.size = img.size


def plate(base, rnd, wear=8, edge=0.78):
    """A painter for worn plate: noise, darker rims, a few scratches."""
    def paint(face, fx, fy, fw, fh):
        c = noisy(base, rnd, wear)
        if fx == 0 or fy == 0 or fx == fw - 1 or fy == fh - 1:
            c = shade(c, edge)
        if rnd.random() < 0.04:
            c = mix(c, (200, 196, 186), 0.45)
        return c
    return paint


def flat(base, rnd, amount=5):
    return lambda face, fx, fy, fw, fh: noisy(base, rnd, amount)


# ---------------------------------------------------------------- Mandalorian hunters and Kaast

HUNTER_PAINTS = [
    # Worn colours, chipped to bare metal at the edges (seeded per hunter at spawn).
    {"paint": (172, 136, 72), "trim": (96, 78, 52)},    # ochre
    {"paint": (82, 102, 128), "trim": (58, 62, 70)},    # slate blue
    {"paint": (100, 112, 72), "trim": (70, 66, 52)},    # olive
    {"paint": (124, 62, 56), "trim": (74, 58, 50)},     # dusty maroon
]
KAAST_PAINT = {"paint": (74, 76, 80), "trim": (206, 196, 172)}  # charcoal with bone-white trim
UNDERSUIT = (46, 46, 50)
METAL = (128, 132, 138)
GUNMETAL = (52, 54, 58)
VISOR = (18, 20, 24)
LEATHER = (98, 72, 48)
SKIN = (176, 136, 108)


def hunter_skin(colours, seed, kaast=False, cracked=False):
    rnd = random.Random(seed)
    img = canvas(128, 64)
    px = Px(img)
    paint, trim = colours["paint"], colours["trim"]

    # Head (the face under the helmet: Kaast's shows when he takes it off).
    def face(f, fx, fy, fw, fh):
        c = noisy(SKIN, rnd, 5)
        if f == "front":
            if fy == 3 and fx in (2, 5):
                return (54, 60, 66)
            if fy == 2 and 1 <= fx <= 6:
                return shade(SKIN, 0.75)
            if kaast and fy >= 5:
                return noisy((104, 96, 90), rnd, 8)  # a short grey beard
        if f in ("top", "back") or (f in ("east", "west") and fy < 2):
            return noisy((70, 64, 58) if kaast else (60, 48, 40), rnd, 6)
        return c
    paint_box(px, 0, 0, 8, 8, 8, face)

    # The helmet shell (hat layer), with its T-visor.
    def helmet(f, fx, fy, fw, fh):
        c = noisy(paint, rnd, 7)
        if f == "front":
            if fy == 3 and 1 <= fx <= 6:
                return VISOR
            if 3 <= fy <= 6 and fx in (3, 4):
                return VISOR
            if fy == 7:
                return shade(c, 0.8)
        if f in ("east", "west") and fy == 4 and 2 <= fx <= 5:
            return trim
        if f == "top" and fx in (3, 4):
            return trim if kaast else shade(c, 0.85)
        if rnd.random() < 0.05:
            return mix(c, METAL, 0.6)
        if cracked and f == "front" and (fx, fy) in ((1, 1), (2, 2), (2, 3), (3, 4), (6, 0), (6, 1), (5, 2), (7, 4)):
            return (26, 24, 24)
        return c
    paint_box(px, 32, 0, 8, 8, 8, helmet)

    # Body, arms and legs: the undersuit with plates on the shins and thighs.
    paint_box(px, 16, 16, 8, 12, 4, flat(UNDERSUIT, rnd, 5))
    paint_box(px, 40, 16, 4, 12, 4, flat(UNDERSUIT, rnd, 5))

    def leg(f, fx, fy, fw, fh):
        if f in ("front", "east", "west") and (2 <= fy <= 4 or 7 <= fy <= 10):
            return noisy(paint, rnd, 7) if fy != 2 and fy != 7 else shade(paint, 0.8)
        if fy >= 10:
            return noisy((40, 34, 30), rnd, 4)  # boots
        return noisy(UNDERSUIT, rnd, 5)
    paint_box(px, 0, 16, 4, 12, 4, leg)

    # Cuirass: a chest plate in two halves, a belt line.
    def cuirass(f, fx, fy, fw, fh):
        c = noisy(paint, rnd, 7)
        if fy >= 10:
            return noisy(LEATHER, rnd, 6) if fy == 10 else noisy(UNDERSUIT, rnd, 4)
        if f == "front":
            if fx in (3, 4) and fy < 6:
                return shade(c, 0.7)
            if fy == 6:
                return shade(c, 0.75)
            if kaast and fy in (1, 2) and fx in (1, 6):
                return trim
        if fx == 0 or fx == fw - 1:
            return shade(c, 0.82)
        return c if rnd.random() > 0.04 else mix(c, METAL, 0.6)
    paint_box(px, 0, 32, 8, 12, 4, cuirass)

    # Jet pack: metal tanks with a painted stripe; nozzles dark and scorched.
    def pack(f, fx, fy, fw, fh):
        c = noisy(METAL, rnd, 8)
        if fy in (2, 3):
            return noisy(paint if not kaast else trim, rnd, 6)
        if f == "back" and fx == fw // 2:
            return shade(c, 0.7)
        return c
    paint_box(px, 24, 32, 7, 8, 3, pack)
    paint_box(px, 44, 39, 2, 2, 2, lambda f, fx, fy, fw, fh: noisy((34, 30, 28), rnd, 6))
    paint_box(px, 44, 32, 7, 6, 1, lambda f, fx, fy, fw, fh: noisy(shade(LEATHER, 0.9 if fx % 2 else 1.0), rnd, 6))
    paint_box(px, 52, 39, 1, 4, 1, lambda f, fx, fy, fw, fh: (210, 160, 70) if fy == 0 else noisy(GUNMETAL, rnd, 5))

    # Gauntlets with their launch ports; shoulder plates.
    def gauntlet(f, fx, fy, fw, fh):
        c = noisy(shade(paint, 0.85), rnd, 7)
        if f == "front" and fy == 1 and fx in (1, 2):
            return (24, 24, 26)
        if f == "east" and fy == 2:
            return trim
        return c
    paint_box(px, 0, 48, 4, 5, 4, gauntlet)
    paint_box(px, 16, 48, 5, 3, 5, plate(paint, rnd))
    paint_box(px, 36, 48, 1, 4, 1, flat((22, 22, 24), rnd, 3))

    # The rifle: a wooden stock near the hand, a long dark barrel; Kaast's axe.
    def rifle(f, fx, fy, fw, fh):
        if fy < 4:
            return noisy((92, 66, 44), rnd, 6)
        return noisy(GUNMETAL, rnd, 5) if fy % 5 else noisy(METAL, rnd, 5)
    paint_box(px, 64, 0, 2, 14, 2, rifle)
    paint_box(px, 72, 0, 1, 16, 1, lambda f, fx, fy, fw, fh: noisy((62, 46, 34) if fy % 3 else (40, 30, 24), rnd, 4))

    def blade(f, fx, fy, fw, fh):
        c = noisy((92, 100, 112), rnd, 6)  # dark, faintly blue iron
        if f in ("front", "back") and fx == 0:
            return (196, 204, 214)  # the honed edge
        return c
    paint_box(px, 76, 0, 1, 5, 4, blade)
    return img


# ---------------------------------------------------------------- the security droid and the seekers

def droid_skin(imperial):
    rnd = random.Random(7202 + (1 if imperial else 0))
    body = (74, 76, 80) if imperial else (118, 104, 74)
    dark = (40, 42, 46) if imperial else (70, 60, 44)
    rust = (120, 72, 40)
    img = canvas(64, 64)
    px = Px(img)

    def worn(base):
        def paint(f, fx, fy, fw, fh):
            c = noisy(base, rnd, 7)
            if not imperial and rnd.random() < 0.08:
                c = mix(c, rust, 0.6)
            if fx == 0 or fx == fw - 1:
                c = shade(c, 0.8)
            return c
        return paint

    def head(f, fx, fy, fw, fh):
        c = noisy(body, rnd, 6)
        if f == "front" and fy in (3, 4):
            return (14, 14, 16)  # the eye slit (the eye itself is the eye layer)
        if f == "front" and fy == 6:
            return shade(c, 0.7)
        return c
    paint_box(px, 0, 0, 8, 8, 8, head)
    paint_box(px, 16, 16, 8, 12, 4, worn(dark))
    paint_box(px, 40, 16, 4, 12, 4, worn(dark))
    paint_box(px, 0, 16, 4, 12, 4, worn(dark))
    paint_box(px, 0, 32, 1, 9, 1, flat((34, 34, 36), rnd, 4))
    paint_box(px, 8, 32, 1, 3, 1, flat((150, 150, 150), rnd, 6))

    def shield(f, fx, fy, fw, fh):
        c = noisy(shade(body, 1.1), rnd, 6)
        if fx in (0, fw - 1) or fy in (0, fh - 1):
            return shade(c, 0.7)
        if fy % 4 == 0:
            return shade(c, 0.85)
        return c
    paint_box(px, 16, 32, 10, 13, 1, shield)
    paint_box(px, 38, 32, 5, 2, 5, worn(body))

    def chest(f, fx, fy, fw, fh):
        c = noisy(body, rnd, 6)
        if f == "front" and fy in (4, 5) and 2 <= fx <= 5:
            return dark
        if f == "front" and fx in (0, 7):
            return shade(c, 0.75)
        if not imperial and rnd.random() < 0.07:
            return mix(c, rust, 0.55)
        return c
    paint_box(px, 0, 46, 8, 12, 4, chest)
    return img


def droid_eye():
    img = canvas(64, 64)
    px = img.load()
    # The front face of the head is at (8, 8), 8x8: a short bright slit in the middle of rows 3 and 4.
    for x in range(10, 14):
        for y in (11, 12):
            px[x, y] = (255, 226, 150, 255)
    px[11, 11] = px[12, 11] = (255, 246, 220, 255)
    return img


def seeker_skin(rattle):
    rnd = random.Random(7310 + (1 if rattle else 0))
    base = (74, 76, 80) if rattle else (112, 114, 118)
    stripe = (206, 196, 172) if rattle else (84, 86, 90)
    img = canvas(32, 32)
    px = Px(img)

    def ball(f, fx, fy, fw, fh):
        c = noisy(base, rnd, 7)
        if f in ("east", "west", "back", "front") and fy == 2:
            return noisy(stripe, rnd, 5)
        if (fx + fy) % 3 == 0 and f == "top":
            return shade(c, 0.85)
        if fx in (0, fw - 1) or fy in (0, fh - 1):
            return shade(c, 0.8)
        return c
    paint_box(px, 0, 0, 6, 6, 6, ball)
    paint_box(px, 24, 0, 2, 2, 1, lambda f, fx, fy, fw, fh: (40, 24, 20) if f != "front" else (150, 40, 34))
    paint_box(px, 24, 4, 1, 4, 1, lambda f, fx, fy, fw, fh: noisy((150, 150, 154), rnd, 6))
    paint_box(px, 28, 4, 1, 3, 1, lambda f, fx, fy, fw, fh: noisy((60, 60, 64), rnd, 4))
    return img


# ---------------------------------------------------------------- the beasts

def hollowhorn_skin(kind):
    rnd = random.Random({"normal": 7401, "bull": 7402, "corrupted": 7403}[kind])
    coat = {"normal": (112, 84, 58), "bull": (88, 64, 46), "corrupted": (54, 48, 52)}[kind]
    belly = mix(coat, (196, 176, 140), 0.35 if kind != "corrupted" else 0.1)
    mane = {"normal": (138, 116, 88), "bull": (150, 144, 136), "corrupted": (40, 34, 40)}[kind]
    vein = (70, 34, 84)
    img = canvas(128, 64)
    px = Px(img)

    def shag(base, belly_rows=0):
        def paint(f, fx, fy, fw, fh):
            # Vertical streaks of longer hair; a lighter belly underneath.
            streak = 0.9 + 0.12 * math.sin(fx * 1.7 + (fx * 7 % 5))
            c = noisy(shade(base, streak), rnd, 8)
            if f == "bottom" or (belly_rows and f in ("east", "west") and fy >= fh - belly_rows):
                c = noisy(belly, rnd, 6)
            if kind == "corrupted" and rnd.random() < 0.09:
                c = vein
            return c
        return paint
    paint_box(px, 0, 0, 12, 10, 22, shag(coat, 3))
    paint_box(px, 68, 0, 5, 9, 5, shag(coat))

    def head(f, fx, fy, fw, fh):
        c = noisy(shade(coat, 1.05), rnd, 6)
        if f == "front":
            return noisy(shade(coat, 0.8), rnd, 5)
        if f in ("east", "west") and fy == 2 and fx in (1, 2):
            return (214, 206, 186) if kind == "corrupted" else (24, 20, 18)  # the eye
        if kind == "corrupted" and rnd.random() < 0.08:
            return vein
        return c
    paint_box(px, 88, 0, 6, 6, 8, head)
    paint_box(px, 116, 0, 4, 3, 2, lambda f, fx, fy, fw, fh: noisy((58, 46, 40), rnd, 5) if f != "front" else (30, 24, 22))
    horn = (214, 202, 172) if kind != "corrupted" else (90, 80, 86)
    ridge = lambda f, fx, fy, fw, fh: noisy(shade(horn, 0.85 if (fx + fy) % 2 else 1.0), rnd, 5)
    paint_box(px, 68, 14, 2, 2, 2, ridge)
    paint_box(px, 76, 14, 2, 2, 4, ridge)
    paint_box(px, 88, 14, 1, 1, 3, lambda f, fx, fy, fw, fh: noisy(shade(horn, 1.1), rnd, 4))

    def leg(f, fx, fy, fw, fh):
        if fy >= 10:
            return noisy((36, 30, 26), rnd, 4)  # hooves
        return shag(coat)(f, fx, fy, fw, fh)
    paint_box(px, 0, 32, 4, 12, 4, leg)
    paint_box(px, 16, 32, 2, 6, 2, shag(mane))
    paint_box(px, 24, 32, 8, 6, 4, shag(mane))
    return img


def ash_hound_skin():
    rnd = random.Random(7501)
    hide = (126, 122, 118)
    img = canvas(64, 64)
    px = Px(img)

    def skin(f, fx, fy, fw, fh):
        c = noisy(hide, rnd, 6)
        if f == "top" or (f in ("east", "west") and fy < 2):
            c = shade(c, 0.88)  # darker along the spine
        if rnd.random() < 0.12:
            c = mix(c, (200, 196, 190), 0.5)  # ash dust
        if f in ("east", "west") and fh > 4 and fy == fh // 2 and fx % 3 == 0:
            c = shade(c, 0.78)  # ribs showing through
        return c
    paint_box(px, 0, 0, 6, 6, 14, skin)

    def head(f, fx, fy, fw, fh):
        c = skin(f, fx, fy, fw, fh)
        if f == "front" and fy == 1 and fx in (0, 4):
            return (214, 226, 220)  # pale eyes
        return c
    paint_box(px, 40, 0, 5, 5, 5, head)
    paint_box(px, 40, 10, 3, 2, 3, lambda f, fx, fy, fw, fh: (30, 28, 28) if f == "front" and fy == 0 else skin(f, fx, fy, fw, fh))
    paint_box(px, 52, 10, 1, 2, 1, lambda f, fx, fy, fw, fh: noisy((96, 88, 86), rnd, 5))
    paint_box(px, 0, 20, 2, 7, 2, lambda f, fx, fy, fw, fh: noisy((60, 56, 54), rnd, 4) if fy >= 6 else skin(f, fx, fy, fw, fh))
    paint_box(px, 8, 20, 1, 1, 7, skin)
    return img


# ---------------------------------------------------------------- rocket, net, mine

def rocket_skin():
    rnd = random.Random(7601)
    img = canvas(32, 16)
    px = Px(img)

    def body(f, fx, fy, fw, fh):
        if f == "front" or (f in ("east", "west", "top", "bottom") and fx < 2 and fw == 8):
            return noisy((176, 150, 80), rnd, 6)  # the painted warhead
        return noisy((92, 96, 100), rnd, 6)
    paint_box(px, 0, 0, 2, 2, 8, body)
    paint_box(px, 20, 0, 1, 5, 2, lambda f, fx, fy, fw, fh: noisy((64, 66, 70), rnd, 5))
    return img


def net_skin():
    """Cords every four texels, knotted where they cross; everything else is see-through."""
    rnd = random.Random(7602)
    img = canvas(64, 64)
    px = img.load()
    for y in range(64):
        for x in range(64):
            on_x, on_y = x % 4 == 1, y % 4 == 1
            if on_x or on_y:
                c = noisy((150, 128, 92) if on_x and on_y else (126, 104, 74), rnd, 10)
                px[x, y] = c + (255,)
    return img


def mine_skin():
    rnd = random.Random(7603)
    img = canvas(32, 16)
    px = Px(img)

    def disc(f, fx, fy, fw, fh):
        c = noisy((92, 96, 70), rnd, 7)  # olive drab
        if f == "top" and (fx + fy) % 5 == 0:
            return shade(c, 0.75)
        return c
    paint_box(px, 0, 0, 6, 2, 6, disc)
    paint_box(px, 24, 0, 1, 2, 1, lambda f, fx, fy, fw, fh: noisy((150, 150, 150), rnd, 6))
    return img


def entities():
    for i, colours in enumerate(HUNTER_PAINTS):
        save(hunter_skin(colours, 7100 + i), "entity", "mandalorian", f"hunter_{i}.png")
    save(rocket_skin(), "entity", "mandalorian", "hunter_rocket.png")
    save(net_skin(), "entity", "mandalorian", "hunter_net.png")
    save(hunter_skin(KAAST_PAINT, 7190, kaast=True), "entity", "kaast", "torvin_kaast.png")
    save(hunter_skin(KAAST_PAINT, 7190, kaast=True, cracked=True), "entity", "kaast", "torvin_kaast_cracked.png")
    save(seeker_skin(True), "entity", "kaast", "kaast_droid.png")
    save(mine_skin(), "entity", "kaast", "camp_mine.png")
    save(droid_skin(False), "entity", "droid", "security_droid.png")
    save(droid_skin(True), "entity", "droid", "security_droid_imperial.png")
    save(droid_eye(), "entity", "droid", "security_droid_eye.png")
    save(seeker_skin(False), "entity", "droid", "seeker_droid.png")
    for kind in ("normal", "bull", "corrupted"):
        save(hollowhorn_skin(kind), "entity", "creature", "hollowhorn.png" if kind == "normal" else f"hollowhorn_{kind}.png")
    save(ash_hound_skin(), "entity", "creature", "ash_hound.png")


# ---------------------------------------------------------------- items (16x16)

def sprite(rows, palette, seed, amount=6):
    """Draws a 16x16 item from a character map; '.' is clear, other characters index the palette."""
    rnd = random.Random(seed)
    img = canvas(16, 16)
    px = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                px[x, y] = noisy(palette[ch], rnd, amount) + (255,)
    return img


ITEM_ART = {
    "hollowhorn_hide": ([
        "................",
        "...oo......oo...",
        "..obbo....obbo..",
        "..obbbooooobbo..",
        "...obbbbbbbbo...",
        "...obbsbbbbbo...",
        "...obbbbbsbbo...",
        "..obbbbbbbbbbo..",
        "..obbsbbbbbbbo..",
        "..obbbbbbbsbbo..",
        "...obbbbbbbbo...",
        "...obbbsbbbbo...",
        "..obbo....obbo..",
        "..ooo......ooo..",
        "................",
        "................"], {"o": (70, 50, 34), "b": (120, 90, 62), "s": (150, 120, 90)}),
    "hollowhorn_horn": ([
        "................",
        "..........oo....",
        ".........ohho...",
        "........ohhho...",
        "........ohho....",
        ".......ohhho....",
        "......ohhho.....",
        ".....ohhhho.....",
        "....ohhhho......",
        "...ohhhhho......",
        "..ohhhhho.......",
        "..ohhhhoo.......",
        "..obbbbo........",
        "...oooo.........",
        "................",
        "................"], {"o": (110, 98, 78), "h": (220, 208, 178), "b": (170, 150, 118)}),
    "ash_hound_fang": ([
        "................",
        "................",
        "......oooo......",
        ".....orrrro.....",
        ".....owwwwo.....",
        ".....owwwwo.....",
        "......owwwo.....",
        "......owwwo.....",
        ".......owwo.....",
        ".......owwo.....",
        "........owo.....",
        "........owo.....",
        ".........o......",
        "................",
        "................",
        "................"], {"o": (110, 104, 96), "w": (232, 228, 214), "r": (150, 110, 100)}),
    "droid_actuator": ([
        "................",
        "................",
        "...oooooooo.....",
        "..ommmmmmmmo....",
        "..omddmmddmoo...",
        "..ommmmmmmmogo..",
        "..omddmmddmoyo..",
        "..ommmmmmmmogo..",
        "..omddmmddmoo...",
        "..ommmmmmmmo....",
        "...oooooooo.....",
        "....or..ry......",
        "....or...ry.....",
        ".....r....y.....",
        "................",
        "................"], {"o": (40, 42, 46), "m": (140, 144, 150), "d": (90, 94, 100), "g": (170, 140, 70),
                             "y": (200, 170, 60), "r": (150, 60, 50)}),
    "raw_hollowhorn": ([
        "................",
        "................",
        "....oooooo......",
        "...orrrrrro.....",
        "..orrfrrrrro....",
        "..orrrrrfrrro...",
        "..orfrrrrrrro...",
        "..orrrrrfrrro...",
        "..orrfrrrrro....",
        "...orrrrrroboo..",
        "....oooooobbbo..",
        "..........obbo..",
        "...........oo...",
        "................",
        "................",
        "................"], {"o": (110, 40, 40), "r": (196, 70, 70), "f": (236, 200, 190), "b": (226, 220, 204)}),
    "cooked_hollowhorn": ([
        "................",
        "................",
        "....oooooo......",
        "...occcccco.....",
        "..occfccccco....",
        "..occccccfcco...",
        "..ocfccccccco...",
        "..occccccfcco...",
        "..occfccccco....",
        "...occccccoboo..",
        "....oooooobbbo..",
        "..........obbo..",
        "...........oo...",
        "................",
        "................",
        "................"], {"o": (74, 40, 22), "c": (150, 90, 50), "f": (214, 170, 120), "b": (226, 220, 204)}),
}


def spawn_egg(base, spots, seed):
    rnd = random.Random(seed)
    img = canvas(16, 16)
    px = img.load()
    cx, cy = 7.5, 8.6
    for y in range(16):
        for x in range(16):
            dx = (x - cx) / 5.2
            dy = (y - cy) / (6.6 if y < cy else 5.6)
            r = dx * dx + dy * dy
            if r > 1.0:
                continue
            c = shade(base, 1.0 + (x - cx) * -0.04 + (y - cy) * -0.05)
            if r > 0.78:
                c = shade(c, 0.7)
            px[x, y] = noisy(c, rnd, 5) + (255,)
    for x, y in ((5, 5), (10, 7), (5, 11), (10, 12), (8, 4), (7, 9)):
        px[x, y] = noisy(spots, rnd, 4) + (255,)
    return img


EGGS = {
    "mandalorian_hunter_spawn_egg": ((150, 124, 72), (40, 42, 46)),
    "security_droid_spawn_egg": ((118, 104, 74), (255, 220, 140)),
    "hollowhorn_spawn_egg": ((112, 84, 58), (214, 202, 172)),
    "ash_hound_spawn_egg": ((126, 122, 118), (214, 226, 220)),
}


def items():
    for i, (name, (rows, palette)) in enumerate(sorted(ITEM_ART.items())):
        save(sprite(rows, palette, 7700 + i), "item", name + ".png")
    for i, (name, (base, spots)) in enumerate(sorted(EGGS.items())):
        save(spawn_egg(base, spots, 7800 + i), "item", name + ".png")


# ---------------------------------------------------------------- the camp gate (block)

def camp_gate():
    """Scrap plates riveted together, streaked with rust, a dark seam where the door slides."""
    rnd = random.Random(7901)
    img = canvas(16, 16)
    px = img.load()
    for y in range(16):
        for x in range(16):
            c = noisy((104, 100, 92), rnd, 8)
            if y in (0, 7, 8, 15):
                c = shade(c, 0.7)
            if x in (0, 15):
                c = shade(c, 0.75)
            if rnd.random() < 0.06 or (x in (4, 11) and rnd.random() < 0.3):
                c = mix(c, (126, 74, 42), 0.6)
            px[x, y] = c + (255,)
    for x, y in ((2, 2), (13, 2), (2, 5), (13, 5), (2, 10), (13, 10), (2, 13), (13, 13)):
        px[x, y] = (150, 146, 136, 255)
    save(img, "block", "camp_gate.png")


PARTS = {
    "particle": held_dust,
    "skill": skill_icons,
    "entity": entities,
    "item": items,
    "block": camp_gate,
}


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--part", choices=sorted(PARTS), help="only this part")
    args = ap.parse_args()
    for name, fn in PARTS.items():
        if args.part is None or args.part == name:
            fn()


if __name__ == "__main__":
    main()
