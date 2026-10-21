#!/usr/bin/env python3
"""Generates the energy and mind Force powers' particle sprites (original placeholder art) and their particle JSONs.

Writes into src/main/resources/assets/oldrepublic/{textures/particle,particles}/. Python 3 standard library only, and
deterministic: each sprite set has its own seeded random sequence, so the same script always writes the same pixels.
Run from anywhere:

    python3 tools/assets/gen_textures_force_energy.py

The light of these powers (lightning, sparks, glowing motes) is drawn in code; these sprites are what that light leaves
in the air. Every sprite is near-white greyscale with alpha, so the game tints it (scorch smoke dark, dust the colour of
the ground it came from, the mist of Fear cold and dark):

- energy_smoke: soft, billowing puffs of smoke with a denser heart and a torn edge, lit a little from above;
- energy_dust: grainy dust kicked off the ground, clumped and speckled, with a ragged edge;
- energy_mist: long, thin wisps of mist or dark vapour that drift and curl, faint at both ends.
"""
import json
import math
import os
import random
import struct
import zlib

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "oldrepublic")
TEXTURES = os.path.join(ASSETS, "textures", "particle")
PARTICLES = os.path.join(ASSETS, "particles")
SIZE = 32


# ---------------------------------------------------------------------------------------------------------- output

def write_png(name, pixel):
    """Writes textures/particle/<name>.png, SIZE x SIZE, from pixel(x, y) -> (lum 0..1, alpha 0..1)."""
    rows = []
    for y in range(SIZE):
        row = bytearray([0])
        for x in range(SIZE):
            lum, alpha = pixel(x, y)
            a = max(0, min(255, int(round(alpha * 255))))
            v = max(0, min(255, int(round(lum * 255)))) if a else 0
            row += bytes((v, v, v, a))
        rows.append(bytes(row))

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(b"".join(rows), 9)) + chunk(b"IEND", b"")
    with open(os.path.join(TEXTURES, name + ".png"), "wb") as f:
        f.write(png)


def write_json(particle, frames):
    with open(os.path.join(PARTICLES, particle + ".json"), "w") as f:
        json.dump({"textures": ["oldrepublic:%s_%d" % (particle, i) for i in range(frames)]}, f, indent=2)
        f.write("\n")


# ---------------------------------------------------------------------------------------------------------- noise

class Noise:
    """Smooth value noise on a lattice of seeded random values, summed over octaves (fractal noise), about 0 to 1."""

    def __init__(self, rng, cells=8):
        self.cells = cells
        self.values = [[rng.random() for _ in range(cells * 16 + 1)] for _ in range(cells * 16 + 1)]

    def value(self, x, y):
        ix = int(math.floor(x))
        iy = int(math.floor(y))
        fx = x - ix
        fy = y - iy
        sx = fx * fx * (3 - 2 * fx)
        sy = fy * fy * (3 - 2 * fy)
        n = len(self.values)
        v00 = self.values[iy % n][ix % n]
        v10 = self.values[iy % n][(ix + 1) % n]
        v01 = self.values[(iy + 1) % n][ix % n]
        v11 = self.values[(iy + 1) % n][(ix + 1) % n]
        top = v00 + (v10 - v00) * sx
        bottom = v01 + (v11 - v01) * sx
        return top + (bottom - top) * sy

    def fractal(self, x, y, octaves=4):
        total = 0.0
        weight = 0.0
        amplitude = 1.0
        frequency = 1.0
        for _ in range(octaves):
            total += self.value(x * frequency, y * frequency) * amplitude
            weight += amplitude
            amplitude *= 0.5
            frequency *= 2.0
        return total / weight


def smoothstep(edge0, edge1, x):
    t = max(0.0, min(1.0, (x - edge0) / (edge1 - edge0)))
    return t * t * (3 - 2 * t)


# ---------------------------------------------------------------------------------------------------------- sprites

def smoke(index, rng):
    """A puff of smoke: a few soft lobes round a denser heart, thinned and torn by noise, lit a little from above."""
    noise = Noise(rng)
    offset = rng.random() * 50
    lobes = []
    for _ in range(4 + rng.randrange(3)):
        angle = rng.random() * math.tau
        distance = 0.1 + rng.random() * 0.18
        lobes.append((0.5 + math.cos(angle) * distance, 0.5 + math.sin(angle) * distance, 0.14 + rng.random() * 0.09))
    lobes.append((0.5, 0.52, 0.22))

    def pixel(x, y):
        u = (x + 0.5) / SIZE
        v = (y + 0.5) / SIZE
        density = 0.0
        for cx, cy, r in lobes:
            d2 = ((u - cx) ** 2 + (v - cy) ** 2) / (r * r)
            density += math.exp(-d2 * 1.3) * 0.55
        swirl = noise.fractal(u * 4 + offset, v * 4 + offset, 5)
        density *= 0.25 + 1.15 * swirl * swirl
        rim = min(1.0, math.hypot(u - 0.5, v - 0.5) / 0.5)
        alpha = smoothstep(0.04, 0.85, density) * (1 - smoothstep(0.7, 1.0, rim)) * 0.72
        lum = 0.78 + 0.18 * (1 - v) + 0.1 * (swirl - 0.5)
        return lum, alpha

    return pixel


def dust(index, rng):
    """Dust thrown off the ground: a clumped, speckled cloud with grains at its ragged edge."""
    noise = Noise(rng)
    offset = rng.random() * 50
    stretch = 0.85 + rng.random() * 0.35
    grains = [(rng.random(), rng.random(), 0.6 + rng.random() * 0.4) for _ in range(26)]

    def pixel(x, y):
        u = (x + 0.5) / SIZE
        v = (y + 0.5) / SIZE
        d = math.hypot((u - 0.5) * stretch, (v - 0.5) / stretch) / 0.42
        clump = noise.fractal(u * 7 + offset, v * 7 + offset)
        density = max(0.0, 1 - d) * (0.35 + 0.95 * clump)
        alpha = smoothstep(0.18, 0.62, density) * 0.8
        for gx, gy, g in grains:
            gd = math.hypot((u - (0.18 + gx * 0.64)) * SIZE, (v - (0.18 + gy * 0.64)) * SIZE)
            if gd < 1.1:
                alpha = max(alpha, g * (1 - gd / 1.1) * (1 - min(1.0, d * 0.9)) + 0.25 * g * (1 - gd / 1.1))
        lum = 0.86 + 0.14 * clump - 0.1 * (v - 0.5)
        return lum, min(0.92, alpha)

    return pixel


def mist(index, rng):
    """A thin wisp of mist: long and curling, densest along a wavering line, faint at both ends."""
    noise = Noise(rng)
    offset = rng.random() * 50
    bend = (rng.random() - 0.5) * 0.3
    wave = 1.5 + rng.random() * 1.5
    phase = rng.random() * math.tau
    thickness = 0.1 + rng.random() * 0.05

    def pixel(x, y):
        u = (x + 0.5) / SIZE
        v = (y + 0.5) / SIZE
        centre = 0.5 + bend * (u - 0.5) * 2 + 0.06 * math.sin(u * wave * math.pi + phase)
        warp = noise.fractal(u * 4 + offset, v * 4 + offset)
        across = abs(v - centre + (warp - 0.5) * 0.12) / (thickness * (0.75 + 0.6 * warp))
        along = smoothstep(0.0, 0.3, u) * smoothstep(1.0, 0.7, u)
        alpha = math.exp(-across * across * 1.4) * along * (0.45 + 0.7 * warp) * 0.75
        lum = 0.9 + 0.1 * warp
        return lum, min(0.8, alpha)

    return pixel


SETS = [
    ("energy_smoke", 4, smoke, 7101),
    ("energy_dust", 4, dust, 7102),
    ("energy_mist", 4, mist, 7103),
]


def main():
    os.makedirs(TEXTURES, exist_ok=True)
    os.makedirs(PARTICLES, exist_ok=True)
    for name, frames, maker, seed in SETS:
        rng = random.Random(seed)
        for i in range(frames):
            write_png("%s_%d" % (name, i), maker(i, rng))
        write_json(name, frames)
        print("wrote %s (%d frames)" % (name, frames))


if __name__ == "__main__":
    main()
