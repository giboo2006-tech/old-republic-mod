#!/usr/bin/env python3
"""Generates the Force power particle sprites (original placeholder art) and their particle JSONs.

Writes into src/main/resources/assets/oldrepublic/{textures/particle,particles}/. Deterministic:
the same input always produces the same files. Run from anywhere:

    python3 tools/assets/gen_textures_force.py

Art direction: bent air, dust and pressure. Sprites are near-white greyscale with alpha so the
game can tint them (dust takes the colour of the ground); nothing glows, no rings on the ground.
The exception is Force Lightning (Milestone 2): its bolt segments and sparks are their own light,
a white core with pale violet-blue edges baked into the sprite.

New sprites are generated after the Milestone 1 ones so the shared random sequence, and with it
every earlier file, stays byte-identical. Milestone 3 adds Force Sense's motes and stream specks
(soft, with a faintly brighter heart, so a cluster reads in a dark tomb without any outline) and
Telekinetic Lift's bent-air shimmer wisps (tall, broken and faint; never a disc).

Milestone 5 (W6 powers) adds Force Stasis's rime motes (small frosted flakes with uneven facets),
Force Drain's motes (soft blobs with a dense heart, tinted dark red in game) and Force Valor's dust
(soft specks with a small bright heart, tinted warm in game), plus the six status effect icons
(18 x 18, textures/mob_effect/<id>.png: stasis, fear, pacified, valor, rage, drained), drawn as
small pictograms with a dark outline. They use their own random sequence (M5_RNG), so they never
shift an earlier file. Write only them with:

    python3 tools/assets/gen_textures_force.py --m5
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "oldrepublic")
TEXTURES = os.path.join(ASSETS, "textures", "particle")
PARTICLES = os.path.join(ASSETS, "particles")
RNG = np.random.default_rng(20260215)


def save(name, rgba):
    rgba = np.clip(rgba, 0, 255).astype(np.uint8)
    Image.fromarray(rgba, "RGBA").save(os.path.join(TEXTURES, name + ".png"))


def grid(size):
    c = (size - 1) / 2.0
    y, x = np.mgrid[0:size, 0:size]
    return (x - c) / c, (y - c) / c


def value_noise(size, cells, rng):
    """Smooth value noise in 0..1."""
    coarse = rng.random((cells + 1, cells + 1))
    img = Image.fromarray((coarse * 255).astype(np.uint8), "L").resize((size, size), Image.BICUBIC)
    return np.asarray(img, dtype=np.float32) / 255.0


def rgba(lum, alpha, tint=(1.0, 1.0, 1.0)):
    out = np.zeros(lum.shape + (4,), dtype=np.float32)
    for i in range(3):
        out[..., i] = lum * tint[i] * 255
    out[..., 3] = alpha * 255
    return out


def air_ripple(frames=4, size=32):
    """A refraction-like rim: thin bright edge, faint darker inner band, transparent centre."""
    x, y = grid(size)
    r = np.sqrt(x * x + y * y)
    names = []
    for f in range(frames):
        t = f / (frames - 1)
        radius = 0.62 + 0.28 * t
        width = 0.16 - 0.07 * t
        # Broken, wavering rim so it reads as disturbed air rather than a drawn circle.
        noise = value_noise(size, 6, RNG) * 0.6 + 0.4
        rim = np.exp(-((r - radius) / (width * 1.4)) ** 2) * noise * 0.75
        inner = np.exp(-((r - (radius - width * 1.6)) / (width * 1.2)) ** 2) * 0.35
        alpha = np.clip(rim * 0.85 + inner * 0.4, 0, 1) * (1 - 0.35 * t)
        lum = np.clip(0.8 + 0.2 * rim - 0.25 * inner, 0, 1)
        name = f"air_ripple_{f}"
        save(name, rgba(lum, alpha))
        names.append(name)
    return names


def push_streak(frames=3, size=16):
    """Horizontal tapered streak of dust, brightest at the leading (right) end."""
    names = []
    for f in range(frames):
        img = np.zeros((size, size), dtype=np.float32)
        lum = np.zeros((size, size), dtype=np.float32)
        thickness = [1.0, 1.4, 0.8][f]
        cy = (size - 1) / 2.0 + RNG.uniform(-0.6, 0.6)
        for xi in range(size):
            u = xi / (size - 1)
            head = u ** 1.6
            for yi in range(size):
                d = abs(yi - cy) / thickness
                a = np.exp(-d * d) * head * (0.75 + 0.25 * RNG.random())
                img[yi, xi] = a
                lum[yi, xi] = 0.85 + 0.15 * head
        name = f"push_streak_{f}"
        save(name, rgba(lum, np.clip(img, 0, 1)))
        names.append(name)
    return names


def push_impact(frames=4, size=32):
    """Pressure puff: dense noisy dust ball that opens up and thins frame by frame."""
    x, y = grid(size)
    r = np.sqrt(x * x + y * y)
    names = []
    for f in range(frames):
        t = f / (frames - 1)
        noise = value_noise(size, 5, RNG) * 0.6 + value_noise(size, 11, RNG) * 0.4
        body = np.clip(1 - r / (0.8 + 0.2 * t), 0, 1) ** (0.9 + 0.6 * t)
        hole = np.clip(1 - r / (0.1 + 0.45 * t), 0, 1) * t * 0.6
        alpha = np.clip((body - hole) * (0.65 + 0.6 * noise) * (1 - 0.3 * t), 0, 1)
        lum = 0.78 + 0.22 * noise
        name = f"push_impact_{f}"
        save(name, rgba(lum, alpha, (1.0, 0.97, 0.92)))
        names.append(name)
    return names


def motes(prefix, frames, size, lum_base, hardness):
    """Small irregular blobs; greyscale so the game can tint them."""
    names = []
    for f in range(frames):
        x, y = grid(size)
        r = np.sqrt((x * RNG.uniform(0.8, 1.2)) ** 2 + (y * RNG.uniform(0.8, 1.2)) ** 2)
        noise = value_noise(size, 3, RNG)
        alpha = np.clip((1 - r) * hardness, 0, 1) * (0.7 + 0.3 * noise)
        lum = lum_base + (1 - lum_base) * noise * 0.5
        name = f"{prefix}_{f}"
        save(name, rgba(lum, alpha))
        names.append(name)
    return names


def pull_streak(frames=3, size=16):
    """Grainy dust streak for Force Pull: broken into grains, brightest at the leading (right) end."""
    names = []
    for f in range(frames):
        alpha = np.zeros((size, size), dtype=np.float32)
        cy = (size - 1) / 2.0 + RNG.uniform(-0.8, 0.8)
        thickness = [0.9, 1.2, 0.7][f]
        grains = RNG.random(size) * 0.6 + 0.4
        for xi in range(size):
            u = xi / (size - 1)
            head = u ** 1.3
            for yi in range(size):
                d = abs(yi - cy) / thickness
                alpha[yi, xi] = np.exp(-d * d) * head * grains[xi]
        lum = np.full((size, size), 0.9, dtype=np.float32)
        name = f"pull_streak_{f}"
        save(name, rgba(lum, np.clip(alpha, 0, 1)))
        names.append(name)
    return names


def lightning_segment(frames=4, size=16):
    """One straight piece of a bolt, spanning the full width: white core, pale violet-blue halo.

    The ends stay at full strength so consecutive segments join without gaps; a slight vertical
    wobble per frame keeps neighbouring pieces from looking identical.
    """
    names = []
    for f in range(frames):
        out = np.zeros((size, size, 4), dtype=np.float32)
        wobble = RNG.uniform(-0.6, 0.6, size)
        cy = (size - 1) / 2.0
        for xi in range(size):
            centre = cy + wobble[xi] * 0.6
            for yi in range(size):
                d = abs(yi - centre)
                core = np.exp(-(d / 0.7) ** 2)
                halo = np.exp(-(d / 2.4) ** 2)
                a = np.clip(core + halo * 0.55, 0, 1)
                # White in the core, shading to violet-blue in the halo.
                mix = np.clip(core * 1.3, 0, 1)
                r = 0.72 + 0.28 * mix
                g = 0.70 + 0.30 * mix
                b = 1.0
                out[yi, xi] = (r * 255, g * 255, b * 255, a * 255)
        name = f"lightning_segment_{f}"
        save(name, out)
        names.append(name)
    return names


def lightning_spark(frames=2, size=8):
    """Tiny bright speck with a hint of a cross; tinted in game (blue-white, or warm for metal sparks)."""
    x, y = grid(size)
    names = []
    for f in range(frames):
        r = np.sqrt(x * x + y * y)
        cross = np.exp(-(np.abs(x) / 0.18) ** 2) + np.exp(-(np.abs(y) / 0.18) ** 2)
        alpha = np.clip(np.exp(-(r / (0.45 + 0.1 * f)) ** 2) + cross * 0.35 * np.clip(1 - r, 0, 1), 0, 1)
        lum = np.clip(0.85 + 0.15 * alpha, 0, 1)
        name = f"lightning_spark_{f}"
        save(name, rgba(lum, alpha))
        names.append(name)
    return names


def ward_glint(frames=4, size=8):
    """Four-point twinkle that opens and closes over the frames; tinted pale gold in game."""
    x, y = grid(size)
    names = []
    for f in range(frames):
        t = [0.5, 1.0, 0.75, 0.35][f]
        r = np.sqrt(x * x + y * y)
        arms = np.exp(-(np.abs(x) / 0.12) ** 2) * np.clip(1 - np.abs(y) / t, 0, 1) + np.exp(-(np.abs(y) / 0.12) ** 2) * np.clip(1 - np.abs(x) / t, 0, 1)
        alpha = np.clip(arms * 0.8 + np.exp(-(r / 0.25) ** 2) * t, 0, 1)
        name = f"ward_glint_{f}"
        save(name, rgba(np.ones_like(alpha), alpha))
        names.append(name)
    return names


def sense_mote(frames=4, size=8):
    """Force Sense cluster mote: a soft round speck with a slightly brighter heart; tinted in game."""
    names = []
    for f in range(frames):
        x, y = grid(size)
        r = np.sqrt((x * RNG.uniform(0.85, 1.15)) ** 2 + (y * RNG.uniform(0.85, 1.15)) ** 2)
        noise = value_noise(size, 3, RNG)
        body = np.clip(1 - r, 0, 1) ** (1.3 + 0.2 * f)
        heart = np.exp(-(r / 0.35) ** 2)
        alpha = np.clip(body * (0.75 + 0.25 * noise) + heart * 0.25, 0, 1)
        lum = np.clip(0.88 + 0.12 * heart, 0, 1)
        name = f"sense_mote_{f}"
        save(name, rgba(lum, alpha))
        names.append(name)
    return names


def sense_stream(frames=2, size=8):
    """Force Sense stream speck: a tiny hard grain of dust, a little elongated; tinted in game."""
    x, y = grid(size)
    names = []
    for f in range(frames):
        stretch = 1.0 + 0.5 * f
        r = np.sqrt((x / (0.55 * stretch)) ** 2 + (y / 0.55) ** 2)
        grain = RNG.random((size, size)) * 0.3 + 0.7
        alpha = np.clip(1 - r, 0, 1) ** 0.8 * grain
        lum = np.full((size, size), 0.95, dtype=np.float32)
        name = f"sense_stream_{f}"
        save(name, rgba(lum, np.clip(alpha, 0, 1)))
        names.append(name)
    return names


def lift_shimmer(frames=4, size=16):
    """Telekinetic Lift: a tall, faint wisp of bent air whose broken edges waver upward frame by frame.

    Colourless and mostly transparent: a soft vertical band with a brighter, broken rim on each side
    (the "refraction" look of the air ripple, as a column piece rather than a ring).
    """
    x, y = grid(size)
    names = []
    phase = RNG.uniform(0, np.pi * 2)
    for f in range(frames):
        t = f / frames
        sway = 0.12 * np.sin((y * 2.2 + t * 2.0) * np.pi + phase)
        half = 0.42 + 0.06 * np.sin((y * 1.4 - t * 2.0) * np.pi)
        d = np.abs(x - sway) / half
        noise = value_noise(size, 5, RNG) * 0.6 + 0.4
        rim = np.exp(-((d - 0.85) / 0.22) ** 2) * noise
        core = np.clip(1 - d, 0, 1) * 0.25
        fade = np.clip(1 - np.abs(y) ** 2, 0, 1)
        alpha = np.clip((rim * 0.7 + core) * fade, 0, 1) * 0.8
        lum = np.clip(0.86 + 0.14 * rim, 0, 1)
        name = f"lift_shimmer_{f}"
        save(name, rgba(lum, alpha))
        names.append(name)
    return names


def particle_json(name, textures):
    with open(os.path.join(PARTICLES, name + ".json"), "w", encoding="utf-8") as fh:
        json.dump({"textures": [f"oldrepublic:{t}" for t in textures]}, fh, indent=2)
        fh.write("\n")


# ---------------------------------------------------------------- Milestone 5 (W6 powers)
# Their own random sequence: the M1 to M3 files above stay byte-identical whatever is added here.
M5_RNG = np.random.default_rng(20261019)
MOB_EFFECTS = os.path.join(ASSETS, "textures", "mob_effect")


def stasis_rime(frames=3, size=8):
    """Force Stasis rime: a small frosted flake, three thin crossing facets of uneven length round a soft core.

    Near-white so the game tints it (pale ice blue while held, grey grit when it falls). Never a ring.
    """
    x, y = grid(size)
    r = np.sqrt(x * x + y * y)
    names = []
    for f in range(frames):
        alpha = np.zeros((size, size), dtype=np.float32)
        base = M5_RNG.uniform(0, np.pi)
        for k in range(3):
            a = base + k * np.pi / 3 + M5_RNG.uniform(-0.25, 0.25)
            length = M5_RNG.uniform(0.55, 1.0)
            across = np.abs(-np.sin(a) * x + np.cos(a) * y)
            along = np.abs(np.cos(a) * x + np.sin(a) * y)
            alpha = np.maximum(alpha, np.clip(1 - across / 0.24, 0, 1) * np.clip(1 - along / length, 0, 1))
        noise = value_noise(size, 3, M5_RNG)
        core = np.clip(1 - r / 0.45, 0, 1)
        alpha = np.clip(np.maximum(alpha * 0.85, core * 0.9), 0, 1) * (0.8 + 0.2 * noise)
        lum = np.clip(0.88 + 0.12 * noise, 0, 1)
        name = f"stasis_rime_{f}"
        save(name, rgba(lum, alpha))
        names.append(name)
    return names


def soft_mote(prefix, frames, size, heart, softness):
    """A soft, slightly irregular blob with a denser heart; greyscale so the game tints it."""
    names = []
    for f in range(frames):
        x, y = grid(size)
        r = np.sqrt((x * M5_RNG.uniform(0.85, 1.2)) ** 2 + (y * M5_RNG.uniform(0.85, 1.2)) ** 2)
        noise = value_noise(size, 3, M5_RNG)
        body = np.clip(1 - r, 0, 1) ** softness
        core = np.exp(-(r / 0.3) ** 2)
        alpha = np.clip(body * (0.7 + 0.3 * noise) + core * heart, 0, 1)
        lum = np.clip(0.86 + 0.14 * core, 0, 1)
        name = f"{prefix}_{f}"
        save(name, rgba(lum, alpha))
        names.append(name)
    return names


class EffectIcon:
    """Draws an 18 x 18 status effect icon at 4x and finishes it with a dark outline (as the skill icons)."""

    N = 18
    S = 4
    OUTLINE = (12, 12, 16)

    def __init__(self):
        self.img = Image.new("RGBA", (self.N * self.S, self.N * self.S), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)

    def p(self, pts):
        return [(x * self.S, y * self.S) for x, y in pts]

    def line(self, pts, color, w=1.0):
        self.d.line(self.p(pts), fill=color + (255,), width=max(1, int(w * self.S)), joint="curve")

    def poly(self, pts, color, alpha=255):
        self.d.polygon(self.p(pts), fill=color + (alpha,))

    def dot(self, x, y, r, color, alpha=255):
        s = self.S
        self.d.ellipse([(x - r) * s, (y - r) * s, (x + r) * s, (y + r) * s], fill=color + (alpha,))

    def arc(self, cx, cy, r, start, end, color, w=1.0, ry=None):
        ry = r if ry is None else ry
        s = self.S
        self.d.arc([(cx - r) * s, (cy - ry) * s, (cx + r) * s, (cy + ry) * s], start, end, fill=color + (255,), width=max(1, int(w * s)))

    def done(self):
        n = self.N
        small = self.img.resize((n, n), Image.LANCZOS)
        px = small.load()
        for yy in range(n):
            for xx in range(n):
                r, g, b, a = px[xx, yy]
                px[xx, yy] = (r, g, b, 0 if a < 60 else 255 if a > 150 else a)
        out = small.copy()
        opx = out.load()
        for yy in range(n):
            for xx in range(n):
                if px[xx, yy][3] == 0 and any(0 <= xx + dx < n and 0 <= yy + dy < n and px[xx + dx, yy + dy][3] > 150
                                              for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    opx[xx, yy] = self.OUTLINE + (200,)
        return out


def icon_stasis(i):
    """A pale ice crystal with rime motes hanging still beside it."""
    ice, frost, deep = (198, 228, 238), (242, 250, 255), (120, 160, 178)
    pts = [(9, 2), (13.5, 5.5), (13.5, 12), (9, 15.5), (4.5, 12), (4.5, 5.5)]
    i.poly(pts, ice)
    i.poly([(9, 2), (13.5, 5.5), (9, 8.5), (4.5, 5.5)], frost)
    i.line([(9, 8.5), (9, 15.5)], deep, 0.6)
    i.line([(4.5, 5.5), (9, 8.5), (13.5, 5.5)], deep, 0.5)
    for x, y in ((2.2, 9), (15.8, 10.5), (15, 3.5)):
        i.dot(x, y, 0.75, frost)


def icon_fear(i):
    """Dark smoke curling up in three uneven wisps."""
    smoke, edge = (92, 74, 118), (170, 150, 196)
    for k, (x0, sway, top) in enumerate(((5.5, 1.4, 4.0), (9.0, -1.6, 2.0), (12.5, 1.2, 5.0))):
        pts = [(x0 + math.sin(t * 0.9 + k) * sway * (t / 12), 16 - t) for t in [j * 0.5 for j in range(0, int((16 - top) * 2) + 1)]]
        i.line(pts, smoke, 1.8)
        i.line(pts, edge, 0.5)
    i.poly([(3, 16.5), (15, 16.5), (13, 14.5), (5, 14.5)], smoke)


def icon_pacified(i):
    """A closed eye, the lid curved down and the lashes hanging from it: calm."""
    lid, pale = (168, 184, 196), (228, 236, 242)
    cx, cy, r = 9, 3.5, 6.5
    i.arc(cx, cy, r, 15, 165, lid, 1.5)
    i.arc(cx, cy, r, 30, 150, pale, 0.5)
    for deg in (55, 90, 125):
        a = math.radians(deg)
        x0, y0 = cx + math.cos(a) * (r + 0.4), cy + math.sin(a) * (r + 0.4)
        x1, y1 = cx + math.cos(a) * (r + 3.2), cy + math.sin(a) * (r + 3.2)
        i.line([(x0, y0), (x1, y1)], lid, 0.9)


def icon_valor(i):
    """A warm upward flame of courage with motes rising from it."""
    gold, bright, deep = (240, 212, 140), (255, 246, 214), (176, 132, 58)
    i.poly([(9, 1.5), (13.5, 9), (12.5, 14), (9, 16.5), (5.5, 14), (4.5, 9)], deep)
    i.poly([(9, 3.5), (12, 9.5), (11, 13.5), (9, 15), (7, 13.5), (6, 9.5)], gold)
    i.poly([(9, 7.5), (10.5, 11), (9, 13.5), (7.5, 11)], bright)
    for x, y in ((3, 4.5), (15, 6)):
        i.dot(x, y, 0.8, bright)


def icon_rage(i):
    """Three ragged red slashes with embers."""
    red, hot, dark = (200, 50, 58), (255, 170, 120), (110, 20, 26)
    for k in range(3):
        x = 3.5 + k * 4.0
        pts = [(x, 2.5 + k * 0.6), (x + 2.2, 7), (x + 1.2, 9.5), (x + 3.4, 15.5 - k * 0.5)]
        i.line(pts, dark, 2.2)
        i.line(pts, red, 1.3)
        i.line(pts, hot, 0.4)
    i.dot(15.5, 3, 0.8, hot)
    i.dot(2.5, 14.5, 0.7, hot)


def icon_drained(i):
    """A dark-red drop with a thread of motes drawn off it."""
    red, pale, dark = (164, 48, 60), (236, 170, 170), (92, 24, 32)
    i.poly([(7, 4), (11, 10), (11.2, 13), (9.5, 15.5), (7, 16), (4.5, 15.5), (2.8, 13), (3, 10)], dark)
    i.poly([(7, 6), (10, 10.5), (10, 13), (8.8, 14.6), (7, 15), (5.2, 14.6), (4, 13), (4, 10.5)], red)
    i.dot(5.6, 12.4, 0.8, pale)
    for k, (x, y) in enumerate(((12.5, 8), (14, 5.5), (15.5, 3))):
        i.dot(x, y, 1.0 - k * 0.15, red)


EFFECT_ICONS = {
    "stasis": icon_stasis,
    "fear": icon_fear,
    "pacified": icon_pacified,
    "valor": icon_valor,
    "rage": icon_rage,
    "drained": icon_drained,
}


def main_m5():
    os.makedirs(TEXTURES, exist_ok=True)
    os.makedirs(PARTICLES, exist_ok=True)
    os.makedirs(MOB_EFFECTS, exist_ok=True)
    particle_json("stasis_rime", stasis_rime())
    particle_json("drain_mote", soft_mote("drain_mote", 3, 8, 0.35, 1.2))
    particle_json("valor_dust", soft_mote("valor_dust", 3, 8, 0.25, 1.6))
    for name, draw in EFFECT_ICONS.items():
        icon = EffectIcon()
        draw(icon)
        icon.done().save(os.path.join(MOB_EFFECTS, name + ".png"))
    print("Milestone 5 force sprites written to", TEXTURES, "and", MOB_EFFECTS)


def main():
    os.makedirs(TEXTURES, exist_ok=True)
    os.makedirs(PARTICLES, exist_ok=True)
    particle_json("air_ripple", air_ripple())
    particle_json("push_streak", push_streak())
    particle_json("push_impact", push_impact())
    particle_json("gather_dust", motes("gather_dust", 3, 8, 0.92, 2.2))
    particle_json("fail_mote", motes("fail_mote", 2, 8, 0.7, 3.0))
    # Milestone 2 powers (appended: see the module docstring).
    particle_json("pull_streak", pull_streak())
    particle_json("jump_trail", motes("jump_trail", 2, 8, 0.95, 1.8))
    particle_json("lightning_segment", lightning_segment())
    particle_json("lightning_spark", lightning_spark())
    particle_json("ward_glint", ward_glint())
    # Milestone 3 powers (appended: see the module docstring).
    particle_json("sense_mote", sense_mote())
    particle_json("sense_stream", sense_stream())
    particle_json("lift_shimmer", lift_shimmer())
    print("Force particle sprites written to", TEXTURES)
    # Milestone 5 (its own random sequence).
    main_m5()


if __name__ == "__main__":
    if "--m5" in sys.argv[1:]:
        main_m5()
    else:
        main()
