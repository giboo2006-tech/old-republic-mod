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
every earlier file, stays byte-identical.
"""
import json
import os

import numpy as np
from PIL import Image

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


def particle_json(name, textures):
    with open(os.path.join(PARTICLES, name + ".json"), "w", encoding="utf-8") as fh:
        json.dump({"textures": [f"oldrepublic:{t}" for t in textures]}, fh, indent=2)
        fh.write("\n")


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
    print("Force particle sprites written to", TEXTURES)


if __name__ == "__main__":
    main()
