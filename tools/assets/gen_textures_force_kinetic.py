#!/usr/bin/env python3
"""Generates the kinetic Force powers' particle sprites (original placeholder art) and their particle JSONs.

Writes into src/main/resources/assets/oldrepublic/{textures/particle,particles}/. Python 3 standard library only, and
deterministic: each sprite set has its own seeded random sequence, so the same script always writes the same pixels.
Run from anywhere:

    python3 tools/assets/gen_textures_force_kinetic.py

Art direction: bent air, dust, debris and pressure, never a glyph or a glow. Every sprite is near-white greyscale with
alpha, so the game tints it (dust and grit take the colour of the ground they came from; the frost and the frozen air
of Stasis are tinted pale in code):

- kinetic_dust: lumpy, billowing dust clouds with a denser heart and a ragged edge, lit from above (six shapes);
- kinetic_pebble: small faceted chips of stone with a dark rim, so a thrown or floating pebble reads against the sky;
- kinetic_streak: a thin line of bent air, a pale core with a dark fringe either side (the way refraction shows), faint
  at the tail and gone at the head, straight, wavering or broken;
- kinetic_shard: a sliver of frozen air for Force Stasis, a clear face with one bright and one dark edge;
- kinetic_rime: rime for a creature held in Stasis, small clusters of frost needles round a bright heart.
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


# ---------------------------------------------------------------------------------------------------------- output

def write_png(name, size, pixel):
    """Writes textures/particle/<name>.png, size x size, from pixel(x, y) -> (lum 0..1, alpha 0..1)."""
    rows = []
    for y in range(size):
        row = bytearray([0])
        for x in range(size):
            lum, alpha = pixel(x, y)
            v = max(0, min(255, int(round(lum * 255))))
            a = max(0, min(255, int(round(alpha * 255))))
            row += bytes((v, v, v, a if v or a else 0))
        rows.append(bytes(row))

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(b"".join(rows), 9)) + chunk(b"IEND", b"")
    with open(os.path.join(TEXTURES, name + ".png"), "wb") as f:
        f.write(png)


def write_json(particle, frames):
    with open(os.path.join(PARTICLES, particle + ".json"), "w") as f:
        json.dump({"textures": ["oldrepublic:%s_%d" % (particle, i) for i in range(frames)]}, f, indent=2)
        f.write("\n")


# ---------------------------------------------------------------------------------------------------------- helpers

def clamp(v, lo=0.0, hi=1.0):
    return lo if v < lo else hi if v > hi else v


def smoothstep(e0, e1, v):
    t = clamp((v - e0) / (e1 - e0))
    return t * t * (3 - 2 * t)


class Noise:
    """Smooth value noise on a wrapping grid of cells, sampled in 0..1 coordinates."""

    def __init__(self, rng, cells):
        self.cells = cells
        self.grid = [[rng.random() for _ in range(cells)] for _ in range(cells)]

    def at(self, u, v):
        x = u * self.cells
        y = v * self.cells
        x0 = int(math.floor(x))
        y0 = int(math.floor(y))
        fx = x - x0
        fy = y - y0
        sx = fx * fx * (3 - 2 * fx)
        sy = fy * fy * (3 - 2 * fy)
        c = self.cells
        g = self.grid
        a = g[y0 % c][x0 % c]
        b = g[y0 % c][(x0 + 1) % c]
        d = g[(y0 + 1) % c][x0 % c]
        e = g[(y0 + 1) % c][(x0 + 1) % c]
        top = a + (b - a) * sx
        bottom = d + (e - d) * sx
        return top + (bottom - top) * sy


def fbm(noises, u, v):
    total = 0.0
    weight = 0.0
    amp = 1.0
    for n in noises:
        total += n.at(u, v) * amp
        weight += amp
        amp *= 0.5
    return total / weight


def supersample(size, fn, n=3):
    """pixel(x, y) averaging fn(u, v) -> (lum, alpha) over n x n points of the pixel (u, v in -1..1), alpha weighted."""

    def pixel(x, y):
        lum = 0.0
        alpha = 0.0
        for j in range(n):
            for i in range(n):
                u = ((x + (i + 0.5) / n) / size) * 2 - 1
                v = ((y + (j + 0.5) / n) / size) * 2 - 1
                l, a = fn(u, v)
                lum += l * a
                alpha += a
        if alpha <= 1e-6:
            return 0.0, 0.0
        return lum / alpha, alpha / (n * n)

    return pixel


# ---------------------------------------------------------------------------------------------------------- sprites

def dust(frames=6, size=32):
    """Billowing dust clouds: clusters of soft lobes, eaten at the edge by noise and thin in places, lit from above.

    Frames 0 and 1 are round puffs, 2 and 3 are flattened (dust rolling along the ground), 4 and 5 are torn wisps."""
    rng = random.Random(26_2_01)
    for f in range(frames):
        squash = (1.0, 0.9, 0.6, 0.65, 0.85, 0.75)[f]
        spread = (0.38, 0.42, 0.52, 0.5, 0.48, 0.5)[f]
        lobes = []
        for _ in range(rng.randint(6, 9)):
            angle = rng.random() * math.tau
            reach = math.sqrt(rng.random()) * spread
            radius = 0.22 + rng.random() * 0.2
            lobes.append((math.cos(angle) * reach, math.sin(angle) * reach * squash, radius, 0.55 + rng.random() * 0.6))
        coarse = Noise(rng, 3)
        fine = Noise(rng, 7)
        grain = Noise(rng, 15)
        torn = f >= 4

        def fn(u, v, lobes=lobes, coarse=coarse, fine=fine, grain=grain, torn=torn):
            density = 0.0
            shade = 0.0
            for cx, cy, r, w in lobes:
                d2 = (u - cx) ** 2 + (v - cy) ** 2
                g = w * math.exp(-d2 / (r * r))
                density += g
                # Lit from above: a lobe is paler on its upper side.
                shade += g * clamp(0.5 - (v - cy) / (r * 2.2))
            if density <= 1e-4:
                return 0.0, 0.0
            lit = clamp(shade / density)
            uu = u * 0.5 + 0.5
            vv = v * 0.5 + 0.5
            n = fbm((coarse, fine, grain), uu, vv)
            density *= 0.35 + 1.3 * n
            edge = 1 - smoothstep(0.8, 0.99, math.hypot(u, v))
            alpha = smoothstep(0.18, 1.1, density) * edge
            if torn:
                alpha *= 0.25 + 0.75 * smoothstep(0.3, 0.6, fine.at(uu * 1.3, vv * 0.7))
            # Thin in places: the inside of the cloud is not a solid sheet.
            alpha *= 0.62 + 0.38 * grain.at(uu, vv)
            lum = 0.68 + 0.3 * lit + 0.08 * (grain.at(uu * 2 % 1, vv * 2 % 1) - 0.5)
            return clamp(lum), clamp(alpha * 0.95)

        write_png("kinetic_dust_%d" % f, size, supersample(size, fn))
    write_json("kinetic_dust", frames)


def pebble(frames=6, size=16):
    """Faceted stone chips: an irregular outline, flat facets lit from the upper left, a dark rim."""
    rng = random.Random(26_2_02)
    for f in range(frames):
        corners = rng.randint(5, 8)
        start = rng.random() * math.tau
        points = []
        for k in range(corners):
            angle = start + math.tau * (k + rng.random() * 0.5) / corners
            reach = 0.5 + rng.random() * 0.4
            points.append((math.cos(angle) * reach, math.sin(angle) * reach * (0.7 + rng.random() * 0.3)))
        facet_light = [0.55 + rng.random() * 0.4 for _ in points]
        speck = Noise(rng, 8)

        def inside(u, v, points=points):
            hit = False
            j = len(points) - 1
            for i in range(len(points)):
                xi, yi = points[i]
                xj, yj = points[j]
                if (yi > v) != (yj > v) and u < (xj - xi) * (v - yi) / (yj - yi) + xi:
                    hit = not hit
                j = i
            return hit

        def fn(u, v, points=points, facet_light=facet_light, speck=speck):
            if not inside(u, v):
                return 0.0, 0.0
            # Distance to the outline, for the dark rim.
            rim = 9.0
            for i in range(len(points)):
                ax, ay = points[i]
                bx, by = points[(i + 1) % len(points)]
                dx, dy = bx - ax, by - ay
                t = clamp(((u - ax) * dx + (v - ay) * dy) / (dx * dx + dy * dy))
                rim = min(rim, math.hypot(u - (ax + dx * t), v - (ay + dy * t)))
            # Which facet: the wedge from the centre toward the nearest corner.
            angle = math.atan2(v, u)
            best = min(range(len(points)), key=lambda i: abs(math.remainder(angle - math.atan2(points[i][1], points[i][0]), math.tau)))
            lum = facet_light[best] * (0.85 + 0.3 * clamp(0.5 - 0.5 * (u + v))) + 0.12 * (speck.at(u * 0.5 + 0.5, v * 0.5 + 0.5) - 0.5)
            lum *= 0.55 + 0.45 * smoothstep(0.02, 0.2, rim)
            return clamp(lum), 1.0

        write_png("kinetic_pebble_%d" % f, size, supersample(size, fn))
    write_json("kinetic_pebble", frames)


def streak(frames=3, size=32):
    """A thin line of bent air along +x: pale core, dark fringe, faint at the tail (left), gone at the head (right)."""
    rng = random.Random(26_2_03)
    for f in range(frames):
        wave_amp = (0.0, 0.035, 0.02)[f]
        wave_freq = 2.0 + rng.random() * 2.0
        phase = rng.random() * math.tau
        breaks = Noise(rng, 6)

        def fn(u, v, wave_amp=wave_amp, wave_freq=wave_freq, phase=phase, breaks=breaks, f=f):
            t = (u + 1) / 2
            taper = smoothstep(0.0, 0.75, t) * (1 - smoothstep(0.9, 1.0, t))
            if f == 2:
                # Broken: gaps along the line.
                taper *= smoothstep(0.3, 0.55, breaks.at(t, 0.5))
            centre = wave_amp * math.sin(t * wave_freq * math.tau + phase)
            d = abs(v - centre) * size / 2  # in pixels
            core = math.exp(-(d / 0.9) ** 2)
            fringe = math.exp(-((d - 2.1) / 0.8) ** 2)
            alpha = taper * max(core * 0.62, fringe * 0.42)
            lum = 1.0 if core * 0.62 >= fringe * 0.42 else 0.0
            return lum, clamp(alpha)

        write_png("kinetic_streak_%d" % f, size, supersample(size, fn, 4))
    write_json("kinetic_streak", frames)


def shard(frames=4, size=16):
    """Slivers of frozen air: a clear, faintly pale face, one bright edge and one darker edge."""
    rng = random.Random(26_2_04)
    for f in range(frames):
        a = (-0.85 + rng.random() * 0.2, -0.55 + rng.random() * 0.5)
        b = (0.8 + rng.random() * 0.15, -0.2 + rng.random() * 0.4)
        c = (-0.3 + rng.random() * 0.5, 0.45 + rng.random() * 0.45)
        tri = (a, b, c)

        def fn(u, v, tri=tri):
            (ax, ay), (bx, by), (cx, cy) = tri
            d = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
            l1 = ((by - cy) * (u - cx) + (cx - bx) * (v - cy)) / d
            l2 = ((cy - ay) * (u - cx) + (ax - cx) * (v - cy)) / d
            l3 = 1 - l1 - l2
            if min(l1, l2, l3) < 0:
                return 0.0, 0.0
            # l3 small: near the edge a-b (bright); l1 small: near the edge b-c (dark).
            bright = 1 - smoothstep(0.0, 0.2, l3)
            dark = 1 - smoothstep(0.0, 0.14, l1)
            if bright > dark and bright > 0.05:
                return 1.0, clamp(0.22 + 0.75 * bright)
            if dark > 0.05:
                return 0.2, clamp(0.2 + 0.5 * dark)
            return 0.95, 0.2

        write_png("kinetic_shard_%d" % f, size, supersample(size, fn, 4))
    write_json("kinetic_shard", frames)


def rime(frames=4, size=16):
    """Rime: a small cluster of frost needles grown from a bright heart at uneven angles, with fine side twigs."""
    rng = random.Random(26_2_05)
    for f in range(frames):
        needles = []
        base = rng.random() * math.tau
        for k in range(rng.randint(4, 6)):
            angle = base + k * math.tau / 5 + (rng.random() - 0.5) * 0.9
            needles.append((angle, 0.45 + rng.random() * 0.5, rng.random() < 0.6))
        cx = (rng.random() - 0.5) * 0.2
        cy = (rng.random() - 0.5) * 0.2

        def fn(u, v, needles=needles, cx=cx, cy=cy):
            x = u - cx
            y = v - cy
            r = math.hypot(x, y)
            best = 0.0
            for angle, length, twigs in needles:
                dx, dy = math.cos(angle), math.sin(angle)
                along = x * dx + y * dy
                across = abs(-x * dy + y * dx)
                if 0 < along < length:
                    width = 0.075 * (1 - along / length * 0.6)
                    best = max(best, math.exp(-(across / width) ** 2) * (1 - smoothstep(length * 0.7, length, along)))
                    if twigs:
                        for at in (0.4, 0.65):
                            s0 = along - length * at
                            if 0 < s0 < length * 0.22:
                                twig_d = abs(across - s0 * 0.9)
                                best = max(best, math.exp(-(twig_d / 0.055) ** 2) * 0.75)
            heart = math.exp(-(r / 0.2) ** 2)
            alpha = max(best, heart)
            return clamp(0.82 + 0.18 * heart), clamp(alpha)

        write_png("kinetic_rime_%d" % f, size, supersample(size, fn, 4))
    write_json("kinetic_rime", frames)


def main():
    os.makedirs(TEXTURES, exist_ok=True)
    os.makedirs(PARTICLES, exist_ok=True)
    dust()
    pebble()
    streak()
    shard()
    rime()


if __name__ == "__main__":
    main()
