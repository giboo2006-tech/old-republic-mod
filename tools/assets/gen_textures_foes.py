#!/usr/bin/env python3
"""Generates the Milestone 4 "Force and foes" textures (PLACEHOLDER art, original, deterministic).

Everything here is drawn by this script from simple shapes and seeded noise; nothing is copied or
traced from any film, game or other existing asset. Run from the repository root:

    python3 tools/assets/gen_textures_foes.py            # everything
    python3 tools/assets/gen_textures_foes.py --part skill

Parts:
  particle  textures/particle/held_dust_{0,1,2}.png (32x32 soft grey specks, tinted in code)
  skill     textures/gui/sprites/skill/<node>.png for the Milestone 4 nodes (16x16, the
            gen_textures_skill.py style and palettes)
  entity    textures/entity/{mandalorian,kaast,droid,creature}/*.png: the hunters' four paints,
            Kaast (and cracked), the rocket, the net, the mine, the security droid (crew, Imperial,
            eye layer), the seeker and Rattle, the hollowhorn (coat, bull, corrupted), the ash hound
  item      textures/item/<id>.png: the creature drops, the droid actuator, the meat, four spawn eggs
  block     textures/block/camp_gate.png

Milestone 5 parts (W8, the five foes of MILESTONE_5.md 12.1):
  m5_entity    textures/entity/hymn/hymnsworn_{chanter,blade,caller}.png, cartel/cartel_{gunman,bruiser,captain}.png
               (64x64), hask/hask_{rifle,blade,champion}.png (128x64, the hunter layout plus crest and shield),
               scour/sand_scour{,_matriarch}.png (128x32), stormback/stormback{,_charged}.png (128x64)
  m5_particle  textures/particle/sand_burst_{0..3}.png (8x8, natural sand) and particles/sand_burst.json
  m5_item      textures/item/<foe>_spawn_egg.png with their items/ and models/item/ JSON
"""
import argparse
import json
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
    """Three soft 32x32 specks: a clump, a flake and a pair of grains. Grey, so code tints them.

    Each is a few soft-edged blobs that fade out to nothing, so a mote reads as a puff of dust at any size. Until
    October 2026 they were 8x8 pixel shapes, which looked blocky riding a Force Barrier close to the camera.
    """
    size = 32
    # Per speck: blobs as (centre x, centre y, radius across, radius down).
    shapes = [
        [(15.5, 16.5, 9.5, 8.5), (19.0, 12.5, 5.5, 5.0), (11.5, 19.5, 5.0, 4.5)],
        [(13.0, 15.0, 9.0, 4.0), (20.5, 18.0, 6.5, 3.5)],
        [(11.0, 12.0, 5.0, 5.0), (21.0, 20.5, 4.5, 4.0), (16.0, 16.5, 2.5, 2.5)],
    ]
    rng = random.Random(4061)
    for i, blobs in enumerate(shapes):
        img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        px = img.load()
        for y in range(size):
            for x in range(size):
                cover = 0.0
                for cx, cy, rx, ry in blobs:
                    d = math.hypot((x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry)
                    cover = max(cover, max(0.0, 1.0 - d) ** 1.4)
                if cover <= 0.0:
                    continue
                v = 205 + rng.randint(-18, 22)
                px[x, y] = (v, v, v, int(round(235 * min(1.0, cover * 1.6))))
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


# ================================================================ Milestone 5 foes (W8)
#
# The five foes of MILESTONE_5.md 12.1. The UV layouts follow the Java models (named in each
# function); the upper half of every 64x64 humanoid skin is the classic layout.

CLEAR = (0, 0, 0, 0)


def m5_head(px, rnd, skin, hair, eyes, brow=None, beard=None, scar=False):
    """The classic 8x8x8 head at 0,0: a face, cropped hair on the crown and the back."""
    def head(f, fx, fy, fw, fh):
        c = noisy(skin, rnd, 4)
        if f == "front":
            if fy == 3 and fx in (2, 5):
                return eyes
            if fy == 3 and fx in (1, 6):
                return shade(skin, 0.82)
            if fy == 2 and fx in (1, 2, 5, 6):
                return brow or shade(hair, 1.1)
            if fy == 5 and fx in (3, 4):
                return shade(skin, 0.72)
            if fy == 0:
                return noisy(hair, rnd, 5)
            if beard and fy >= 5:
                return noisy(beard, rnd, 6)
            if scar and (fx, fy) in ((5, 4), (6, 5), (6, 6)):
                return mix(skin, (236, 210, 196), 0.6)
            return c
        if f in ("top", "back"):
            return noisy(hair, rnd, 5)
        if f in ("east", "west"):
            back = fx < 3 if f == "east" else fx > 4
            if fy < 2 or (back and fy < 6):
                return noisy(hair, rnd, 5)
            if beard and fy >= 5 and not back:
                return noisy(beard, rnd, 6)
        return c
    paint_box(px, 0, 0, 8, 8, 8, head)


def m5_limbs(px, rnd, body, arm, legs, boots, hands=None):
    """The classic body (16,16), arms (40,16) and legs (0,16) under the clothes; hands at the arms' ends."""
    paint_box(px, 16, 16, 8, 12, 4, flat(body, rnd, 4))

    def arm_paint(f, fx, fy, fw, fh):
        if hands and fy >= 10 and f != "top":
            return noisy(hands, rnd, 4)
        return noisy(arm, rnd, 4)
    paint_box(px, 40, 16, 4, 12, 4, arm_paint)

    def leg(f, fx, fy, fw, fh):
        if fy >= 8 or f == "bottom":
            return noisy(boots, rnd, 4) if fy != 8 else shade(boots, 1.2)
        return noisy(legs, rnd, 4)
    paint_box(px, 0, 16, 4, 12, 4, leg)


def cloth(base, rnd, fold=3, amount=5, hem=None, hem_rows=(), clear_bottom=False):
    """A painter for hanging cloth: vertical folds every few texels, an optional hem colour."""
    def paint(f, fx, fy, fw, fh):
        if f == "bottom" and clear_bottom:
            return CLEAR
        c = noisy(base, rnd, amount)
        if f not in ("top", "bottom") and fold and fx % fold == fold - 1:
            c = shade(c, 0.84)
        if hem is not None and f not in ("top", "bottom") and fy in hem_rows:
            c = noisy(hem, rnd, 4)
        return c
    return paint


# ---------------------------------------------------------------- the Hymnsworn (HymnswornModel)

HYMN_PALETTES = {
    # The chanter: night-blue robes, a bone-white stole with violet verses.
    "chanter": {"robe": (54, 62, 82), "dark": (34, 38, 52), "trim": (150, 132, 96), "stole": (196, 188, 168),
                "rune": (104, 74, 150), "veil": (118, 114, 110)},
    # The blade: road-brown robes, a rust-red stole with gold verses, leather bracers.
    "blade": {"robe": (70, 56, 48), "dark": (44, 36, 32), "trim": (134, 88, 58), "stole": (128, 56, 46),
              "rune": (206, 176, 118), "veil": (92, 86, 80)},
    # The storm-caller: storm-grey violet robes, a pale stole with lightning-blue verses.
    "caller": {"robe": (72, 68, 96), "dark": (46, 44, 64), "trim": (162, 170, 196), "stole": (176, 182, 202),
               "rune": (110, 150, 226), "veil": (128, 130, 144)},
}


def hymnsworn_skin(variant, seed):
    p = HYMN_PALETTES[variant]
    rnd = random.Random(seed)
    img = canvas(64, 64)
    px = Px(img)
    skin = (176, 154, 140)
    m5_head(px, rnd, skin, (42, 36, 34), (26, 24, 32), brow=shade(skin, 0.62))

    def hood(f, fx, fy, fw, fh):
        c = noisy(p["robe"], rnd, 5)
        if f == "bottom":
            return CLEAR
        if f == "front":
            if 1 <= fx <= 6 and fy >= 2:
                return CLEAR  # the face opening
            return noisy(p["trim"], rnd, 4) if fy == 1 and 1 <= fx <= 6 else noisy(p["dark"], rnd, 4)
        if rnd.random() < 0.06:
            return shade(c, 0.86)
        return c
    paint_box(px, 32, 0, 8, 8, 8, hood)

    hands = skin if variant != "blade" else (88, 64, 44)  # the blade's leather bracers and gloves
    m5_limbs(px, rnd, p["dark"], p["dark"], (50, 44, 40), (46, 38, 32), hands=hands)

    def robe(f, fx, fy, fw, fh):
        c = noisy(p["robe"], rnd, 5)
        if f in ("top", "bottom"):
            return c
        if fy == 8:
            return noisy(p["trim"], rnd, 4)  # the cord at the waist
        if fx % 3 == 2:
            c = shade(c, 0.85)
        return c
    paint_box(px, 0, 32, 8, 12, 4, robe)

    def sleeve(f, fx, fy, fw, fh):
        if f == "bottom" or (fy >= 10 and f != "top"):
            return CLEAR  # open cuffs: the hands show
        c = noisy(p["robe"], rnd, 5)
        if fy == 9 and f != "top":
            return noisy(p["trim"], rnd, 4)
        return shade(c, 0.88) if fx == 0 else c
    paint_box(px, 24, 32, 4, 12, 4, sleeve)

    def stole(f, fx, fy, fw, fh):
        c = noisy(p["stole"], rnd, 5)
        if f == "front" and fy % 3 == 1 and 1 <= fy <= 12:
            # A verse: one glyph a line, alternating sides.
            return noisy(p["rune"], rnd, 6) if (fy // 3 + fx) % 2 == 0 else c
        if f == "front" and fy == fh - 1:
            return noisy(p["trim"], rnd, 4)
        return c
    paint_box(px, 40, 32, 2, 14, 1, stole)

    def veil(f, fx, fy, fw, fh):
        if f != "front":
            return noisy(shade(p["veil"], 0.8), rnd, 4)
        if fy == fh - 1 and fx % 2 == 1:
            return CLEAR  # a fringed hem
        c = noisy(p["veil"], rnd, 5)
        return shade(c, 0.78) if (fx + fy) % 2 == 0 else c
    paint_box(px, 46, 32, 8, 4, 1, veil)

    paint_box(px, 0, 48, 8, 8, 4, cloth(p["robe"], rnd, hem=p["trim"], hem_rows=(7,)))

    def mantle(f, fx, fy, fw, fh):
        c = noisy(p["dark"], rnd, 5)
        if f == "front" and fx in (4,) and fy <= 1:
            return noisy(p["trim"], rnd, 3)  # the clasp
        if f not in ("top", "bottom") and fy == fh - 1:
            return noisy(p["trim"], rnd, 4)
        return c
    paint_box(px, 24, 48, 9, 3, 5, mantle)
    return img


# ---------------------------------------------------------------- the cartel enforcers (CartelModel)

KAJIDIC_GOLD = (217, 164, 65)
CARTEL_PALETTES = {
    # The gunman: an ochre duster over a wine shirt, a wine head-wrap, gloves.
    "gunman": {"skin": (176, 136, 108), "hair": (54, 40, 30), "coat": (166, 124, 68), "coat_dark": (122, 88, 48),
               "shirt": (96, 42, 40), "trim": (140, 104, 56), "wrap": (110, 46, 44), "trousers": (84, 70, 56),
               "boots": (54, 42, 32), "hands": (70, 54, 40), "beard": None, "scar": False},
    # The bruiser: a wine leather vest, bare forearms, an ochre wrap, a short beard.
    "bruiser": {"skin": (134, 100, 78), "hair": (30, 24, 20), "coat": (98, 42, 40), "coat_dark": (66, 28, 28),
                "shirt": (72, 62, 52), "trim": (172, 128, 64), "wrap": (172, 128, 64), "trousers": (62, 52, 44),
                "boots": (44, 34, 26), "hands": (134, 100, 78), "beard": (40, 30, 24), "scar": False},
    # The captain: a long deep-wine coat with gold trim, an ochre sash, a scar, one plated shoulder.
    "captain": {"skin": (196, 160, 130), "hair": (96, 86, 78), "coat": (84, 32, 34), "coat_dark": (56, 22, 24),
                "shirt": (172, 128, 64), "trim": KAJIDIC_GOLD, "wrap": (60, 30, 30), "trousers": (52, 42, 36),
                "boots": (36, 28, 22), "hands": (60, 44, 34), "beard": None, "scar": True},
}


def cartel_skin(variant, seed):
    p = CARTEL_PALETTES[variant]
    rnd = random.Random(seed)
    img = canvas(64, 64)
    px = Px(img)
    m5_head(px, rnd, p["skin"], p["hair"], (40, 34, 30), beard=p["beard"], scar=p["scar"])

    def wrap(f, fx, fy, fw, fh):
        if f == "bottom":
            return CLEAR
        c = noisy(p["wrap"], rnd, 5)
        if f == "front" and fy >= 2:
            return CLEAR  # the face stays open
        if (fx + fy * 2) % 5 == 0:
            c = shade(c, 0.82)  # the wrap's turns
        return c
    paint_box(px, 32, 0, 8, 8, 8, wrap)

    def goggles(f, fx, fy, fw, fh):
        if f == "front":
            if fx in (1, 2, 3) or fx in (5, 6, 7):
                lens = (52, 70, 72) if (fx, fy) not in ((2, 0), (6, 0)) else (150, 176, 172)
                return lens if fy <= 1 else noisy(KAJIDIC_GOLD, rnd, 6)
            return noisy(shade(KAJIDIC_GOLD, 0.8), rnd, 6)
        return noisy((50, 40, 30), rnd, 4)  # the strap
    paint_box(px, 40, 40, 9, 2, 1, goggles)

    arm = p["hands"] if variant == "bruiser" else p["shirt"]
    m5_limbs(px, rnd, p["shirt"], arm, p["trousers"], p["boots"], hands=p["hands"])

    def jacket(f, fx, fy, fw, fh):
        c = noisy(p["coat"], rnd, 5)
        if f in ("top", "bottom"):
            return c if f == "top" else CLEAR
        if fy == 9:
            if f == "front" and fx in (3, 4):
                return noisy(KAJIDIC_GOLD, rnd, 5)  # the buckle
            return noisy((48, 36, 26), rnd, 4)  # the belt
        if f == "front":
            open_front = (2 <= fx <= 5) if variant == "bruiser" else (fx in (3, 4))
            if open_front and fy < 9:
                return CLEAR  # the coat hangs open over the shirt
            if fx in (2, 5) and variant != "bruiser":
                return noisy(p["trim"], rnd, 4)  # the lapels
        if variant == "captain" and f == "front" and fy in (4, 5) and fx in (0, 1, 6, 7):
            return noisy(p["shirt"], rnd, 4)  # the sash's ends
        if rnd.random() < 0.05:
            return mix(c, (196, 176, 140), 0.35)  # sand worn into the cloth
        return c
    paint_box(px, 0, 32, 8, 12, 4, jacket)

    def sleeve(f, fx, fy, fw, fh):
        if variant == "bruiser" and fy >= 4:
            return CLEAR  # rolled to the elbow
        if f == "bottom" or (fy >= 11 and f != "top"):
            return CLEAR
        c = noisy(p["coat"], rnd, 5)
        if fy in (9, 10) and f != "top":
            return noisy(p["trim"] if variant == "captain" else p["coat_dark"], rnd, 4)  # the cuff
        return c
    paint_box(px, 24, 32, 4, 12, 4, sleeve)

    def tail(f, fx, fy, fw, fh):
        c = noisy(p["coat"], rnd, 5)
        if f in ("front", "back"):
            if fy == fh - 1 and variant == "gunman" and fx % 3 == 1:
                return CLEAR  # a ragged hem
            if fy == fh - 1 and variant == "captain":
                return noisy(KAJIDIC_GOLD, rnd, 5)
            if fx in (3, 4):
                return shade(c, 0.8)  # the split
        return c
    paint_box(px, 40, 32, 8, 6, 1, tail)

    if variant == "captain":
        def pauldron(f, fx, fy, fw, fh):
            c = noisy((122, 126, 130), rnd, 7)
            if f not in ("top", "bottom") and fy == 1:
                return noisy(p["coat"], rnd, 4)  # a wine stripe
            if (fx, fy) in ((0, 0), (fw - 1, 0)) and f != "bottom":
                return noisy(KAJIDIC_GOLD, rnd, 4)  # rivets
            if fx == 0 or fx == fw - 1:
                return shade(c, 0.8)
            return c
        paint_box(px, 0, 48, 5, 3, 5, pauldron)
    return img


# ---------------------------------------------------------------- Clan Hask's warriors (HaskModel)

# Clan Hask's colours, shared with W2's Hask fighter: clan iron, ember and bone.
HASK_IRON = (76, 82, 92)
HASK_EMBER = (194, 72, 40)
HASK_BONE = (214, 204, 180)

HASK_PAINTS = {
    # Clan iron plate on every warrior; the rifle's trim is darker iron, the blade's ember, the champion's bone.
    "rifle": {"paint": HASK_IRON, "trim": (48, 52, 60)},
    "blade": {"paint": HASK_IRON, "trim": HASK_EMBER},
    "champion": {"paint": (68, 74, 84), "trim": HASK_BONE},
}


def hask_skin(variant, seed):
    colours = HASK_PAINTS[variant]
    img = hunter_skin(colours, seed)
    rnd = random.Random(seed + 50)
    px = Px(img)
    crest = HASK_BONE if variant == "champion" else HASK_EMBER

    # The crest along the helmet's crown: stiff bristles, darker at their roots.
    paint_box(px, 114, 0, 1, 2, 6, lambda f, fx, fy, fw, fh:
              noisy(shade(crest, 0.7) if fy == fh - 1 else crest, rnd, 8) if f != "bottom" else noisy(shade(crest, 0.6), rnd, 4))

    # The champion's shield: clan iron with a bone rim and the clan's mark in ember (a bar and chevron).
    def shield(f, fx, fy, fw, fh):
        iron = noisy(shade(HASK_IRON, 0.85), rnd, 6)
        if f not in ("east", "west"):
            return iron
        if fx in (0, fw - 1) or fy in (0, fh - 1):
            return noisy(shade(HASK_BONE, 0.9), rnd, 5)
        c = noisy(HASK_IRON, rnd, 7)
        mid = (fw - 1) / 2
        if f == "west":
            if abs(fx - mid) < 0.6 and 2 <= fy <= 9:
                return noisy(HASK_EMBER, rnd, 6)  # the bar
            if fy - 6 == int(abs(fx - mid)) and fy <= 9:
                return noisy(HASK_EMBER, rnd, 6)  # the chevron
        if rnd.random() < 0.06:
            return mix(c, (188, 190, 194), 0.5)  # scrapes to bare metal
        return c
    paint_box(px, 88, 0, 1, 12, 8, shield)
    paint_box(px, 106, 0, 1, 3, 3, lambda f, fx, fy, fw, fh: noisy((176, 140, 72) if (fx + fy) % 3 else (214, 186, 120), rnd, 6))
    return img


# ---------------------------------------------------------------- the sand scour (ScourModel)

def scour_skin(matriarch, seed):
    rnd = random.Random(seed)
    img = canvas(128, 32)
    px = Px(img)
    if matriarch:
        shell, ridge, belly, leg = (178, 132, 92), (124, 84, 56), (204, 174, 132), (128, 92, 64)
    else:
        shell, ridge, belly, leg = (198, 172, 122), (150, 122, 80), (218, 200, 158), (146, 118, 80)

    def carapace(f, fx, fy, fw, fh):
        c = noisy(shell, rnd, 7)
        if f == "bottom":
            return noisy(belly, rnd, 5)
        if f in ("east", "west", "front", "back") and fy >= fh - 2:
            return noisy(belly, rnd, 5)  # the pale underside shows low on the flanks
        if f == "top":
            if fy % 3 == 0:
                return noisy(ridge, rnd, 6)  # the segment ridges
            if abs(fx - (fw - 1) / 2) < 1:
                return noisy(mix(shell, (236, 220, 180), 0.25), rnd, 5)
        if matriarch and rnd.random() < 0.05:
            return mix(c, (226, 212, 186), 0.5)  # old scars
        return c
    paint_box(px, 0, 0, 10, 6, 14, carapace)

    def head(f, fx, fy, fw, fh):
        c = noisy(shell, rnd, 6)
        if f == "front":
            if fy == 1 and fx in (1, 6):
                return (22, 18, 16)  # eyes
            if fy == 1 and fx in (2, 5):
                return (176, 120, 40)  # the eye's amber glint
            if fy >= 3:
                return noisy(ridge, rnd, 6)  # the upper mandible
        if f == "top" and fy % 2 == 0:
            return noisy(ridge, rnd, 6)
        return c
    paint_box(px, 48, 0, 8, 5, 6, head)

    def jaw(f, fx, fy, fw, fh):
        if f == "front" and fy == 0:
            return (232, 224, 202) if fx % 2 == 0 else (120, 80, 60)  # teeth
        if f == "top":
            return noisy((140, 70, 60), rnd, 6)  # the mouth's inside
        return noisy(belly, rnd, 5)
    paint_box(px, 76, 0, 7, 2, 5, jaw)

    def plate(f, fx, fy, fw, fh):
        c = noisy(shade(shell, 0.86), rnd, 7)
        if fx == 0 or fx == fw - 1 or fy == 0:
            return noisy(mix(shell, (236, 222, 186), 0.35), rnd, 5)  # sand-polished edges
        return c
    paint_box(px, 100, 0, 6, 2, 3, plate)

    paint_box(px, 0, 20, 2, 6, 2, lambda f, fx, fy, fw, fh: noisy((52, 40, 30), rnd, 4) if fy >= fh - 1 else noisy(leg, rnd, 6))

    def segment(f, fx, fy, fw, fh):
        c = noisy(shell, rnd, 6)
        if f == "top" and fy % 2 == 1:
            return noisy(ridge, rnd, 6)
        if f in ("east", "west") and fx % 3 == 2:
            return noisy(ridge, rnd, 6)
        return c
    paint_box(px, 8, 20, 6, 4, 6, segment)
    paint_box(px, 32, 20, 4, 3, 6, segment)
    paint_box(px, 52, 20, 2, 2, 5, segment)
    paint_box(px, 66, 20, 4, 4, 3, lambda f, fx, fy, fw, fh:
              noisy((176, 168, 152), rnd, 9) if (fx + fy) % 2 else noisy((132, 122, 108), rnd, 7))  # the club: bone knobs
    return img


# ---------------------------------------------------------------- the stormback (StormbackModel)

STORM_HIDE = (92, 96, 104)
STORM_BELLY = (132, 134, 138)
STORM_BASALT = (52, 54, 60)
STORM_VEIN = (214, 214, 255)


def stormback_skin(seed):
    rnd = random.Random(seed)
    img = canvas(128, 64)
    px = Px(img)

    def body(f, fx, fy, fw, fh):
        c = noisy(STORM_HIDE, rnd, 6)
        if f == "bottom":
            return noisy(STORM_BELLY, rnd, 5)
        if f == "top" and abs(fx - (fw - 1) / 2) < 2:
            return noisy(shade(STORM_HIDE, 0.7), rnd, 5)  # the dark stripe down the spine
        if f in ("east", "west") and fy >= fh - 3:
            return noisy(mix(STORM_HIDE, STORM_BELLY, 0.6), rnd, 5)
        if rnd.random() < 0.08:
            return shade(c, 0.85)  # mottling
        return c
    paint_box(px, 0, 0, 14, 12, 22, body)

    def head(f, fx, fy, fw, fh):
        c = noisy(shade(STORM_HIDE, 0.95), rnd, 6)
        if f == "front":
            if fy >= 5:
                return noisy(STORM_BELLY, rnd, 5)  # the muzzle
            if fy == 6 and fx in (2, 5):
                return (30, 30, 34)
        if f in ("east", "west") and fy == 2 and fx in (fw - 3, 2):
            return (190, 140, 50)  # amber eyes
        return c
    paint_box(px, 72, 0, 8, 8, 10, head)

    def brow(f, fx, fy, fw, fh):
        c = noisy((150, 146, 136), rnd, 7)
        if f == "front" and fy == fh - 1:
            return shade(c, 0.75)
        if rnd.random() < 0.08:
            return shade(c, 0.8)  # pitted bone
        return c
    paint_box(px, 72, 18, 10, 4, 2, brow)
    paint_box(px, 96, 18, 2, 2, 5, lambda f, fx, fy, fw, fh: noisy((70, 66, 60) if f == "front" else (200, 192, 172), rnd, 6))

    def basalt(f, fx, fy, fw, fh):
        c = noisy(STORM_BASALT, rnd, 6)
        if (fx + fy) % 4 == 0:
            return noisy((86, 90, 98), rnd, 5)  # split facets catching the light
        return c
    paint_box(px, 0, 34, 2, 6, 7, basalt)

    def leg(f, fx, fy, fw, fh):
        if fy >= fh - 2 or f == "bottom":
            return noisy((40, 38, 36), rnd, 4)  # hooves
        return noisy(shade(STORM_HIDE, 0.85), rnd, 6)
    paint_box(px, 18, 34, 5, 10, 5, leg)
    paint_box(px, 38, 34, 2, 8, 2, lambda f, fx, fy, fw, fh: noisy((34, 34, 38) if fy >= fh - 3 else STORM_HIDE, rnd, 5))
    paint_box(px, 46, 34, 6, 3, 4, lambda f, fx, fy, fw, fh: noisy((150, 70, 66) if f == "top" else STORM_BELLY, rnd, 5))
    return img


def stormback_charged(seed):
    """The plates' veins of light only (drawn additive over the hide while charged); the rest is clear."""
    rnd = random.Random(seed)
    img = canvas(128, 64)
    px = Px(img)

    def veins(f, fx, fy, fw, fh):
        # A jagged crack climbing each face, with a glint or two beside it.
        line = (fy * 2 + (fx * 3) % 5) % 7
        if line == 0 or (line == 1 and rnd.random() < 0.3):
            return STORM_VEIN + (255,)
        if rnd.random() < 0.06:
            return shade(STORM_VEIN, 0.7) + (200,)
        return CLEAR
    paint_box(px, 0, 34, 2, 6, 7, veins)

    def spine(f, fx, fy, fw, fh):
        # A faint line of light down the spine between the plates.
        if f == "top" and abs(fx - (fw - 1) / 2) < 1 and fy % 3 != 2:
            return shade(STORM_VEIN, 0.6) + (170,)
        return None
    paint_box(px, 0, 0, 14, 12, 22, spine)
    return img


# ---------------------------------------------------------------- particles and eggs

def sand_burst(frame):
    """A clump of sand thrown up, breaking into grains frame by frame (natural sand colour)."""
    rnd = random.Random(8300 + frame)
    img = canvas(8, 8)
    px = img.load()
    # Frame 0 a dense clump; 1 cracking; 2 and 3 the grains flying apart.
    radius = [2.2, 2.6, 3.4, 3.8][frame]
    keep = [0.9, 0.62, 0.3, 0.16][frame]
    for y in range(8):
        for x in range(8):
            r = math.hypot(x - 3.5, y - 3.5)
            if r > radius:
                continue
            # The clump's heart holds longest; its edge goes first.
            chance = keep * (1.15 - 0.35 * r / radius)
            if rnd.random() < chance:
                c = noisy((222, 196, 140), rnd, 14)
                if r > radius * 0.7:
                    c = shade(c, 0.86)
                px[x, y] = c + (240 if frame < 3 else 200,)
    return img


M5_EGGS = {
    "cartel_enforcer_spawn_egg": ((166, 124, 68), (96, 42, 40)),
    "hask_warrior_spawn_egg": (HASK_IRON, HASK_EMBER),
    "hymnsworn_spawn_egg": ((54, 62, 82), (176, 150, 214)),
    "sand_scour_spawn_egg": ((198, 172, 122), (120, 92, 60)),
    "stormback_spawn_egg": ((92, 96, 104), (200, 200, 246)),
}


def m5_entities():
    for i, variant in enumerate(("chanter", "blade", "caller")):
        save(hymnsworn_skin(variant, 8100 + i), "entity", "hymn", f"hymnsworn_{variant}.png")
    for i, variant in enumerate(("gunman", "bruiser", "captain")):
        save(cartel_skin(variant, 8110 + i), "entity", "cartel", f"cartel_{variant}.png")
    for i, variant in enumerate(("rifle", "blade", "champion")):
        save(hask_skin(variant, 8120 + i), "entity", "hask", f"hask_{variant}.png")
    save(scour_skin(False, 8130), "entity", "scour", "sand_scour.png")
    save(scour_skin(True, 8131), "entity", "scour", "sand_scour_matriarch.png")
    save(stormback_skin(8140), "entity", "stormback", "stormback.png")
    save(stormback_charged(8141), "entity", "stormback", "stormback_charged.png")


def write_asset_json(rel, data):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent="\t")
        f.write("\n")
    print("wrote", os.path.normpath(path))


def m5_particles():
    for frame in range(4):
        save(sand_burst(frame), "particle", f"sand_burst_{frame}.png")
    write_asset_json("particles/sand_burst.json", {"textures": [f"oldrepublic:sand_burst_{i}" for i in range(4)]})


def m5_items():
    for i, (name, (base, spots)) in enumerate(sorted(M5_EGGS.items())):
        save(spawn_egg(base, spots, 8200 + i), "item", name + ".png")
        write_asset_json(f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"oldrepublic:item/{name}"}})
        write_asset_json(f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"oldrepublic:item/{name}"}})


PARTS = {
    "particle": held_dust,
    "skill": skill_icons,
    "entity": entities,
    "item": items,
    "block": camp_gate,
    "m5_entity": m5_entities,
    "m5_particle": m5_particles,
    "m5_item": m5_items,
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
