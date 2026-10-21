#!/usr/bin/env python3
"""Generates the kinetic Force powers' particle sprites (original placeholder art) and their particle JSONs.

Writes into src/main/resources/assets/oldrepublic/{textures/particle,particles}/. Python 3 standard library only, and
deterministic: each sprite set has its own seeded random sequence, so the same script always writes the same pixels.
Run from anywhere:

    python3 tools/assets/gen_textures_force_kinetic.py

Art direction: bent air, dust, debris and pressure, never a glyph or a glow. Every sprite is near-white greyscale with
alpha, so the game tints it (dust and grit take the colour of the ground they came from; the frost and the frozen air
of Stasis are tinted pale in code):

- kinetic_dust: billowing dust, a dense lumpy heart thinning softly through ragged lobes to nothing, lit from above;
- kinetic_pebble: small faceted chips of stone with a dark rim, so a thrown or floating pebble reads against the sky;
- kinetic_streak: a thin line of bent air, a pale core with a dark fringe either side (the way refraction shows), faint
  at the tail and gone at the head, whole, thin or broken;
- kinetic_shard: splinters of frost broken off a body held in Stasis, long thin slivers with a bright and a dark edge;
- kinetic_rime: rime for a creature held in Stasis, a lumpy crust of hoarfrost with needles bristling out of one side;
- kinetic_leaf: leaves torn off the ground and carried by the air (Speed, Barrier, a push over grass).
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
    """Billowing dust: a dense, lumpy heart that thins out softly through ragged lobes to nothing, lit from above.

    No hard outline anywhere, so a thin cloud never reads as a disc or a bubble. Frames 0 to 2 are puffs, 3 is a low cloud
    rolling along the ground, 4 and 5 are torn wisps of settling dust."""
    rng = random.Random(26_2_11)
    for f in range(frames):
        squash = (1.0, 0.85, 0.9, 0.55, 0.5, 0.6)[f]
        wisp = f >= 4
        lobes = []
        if wisp:
            # A torn wisp: many small lobes strung out along a curving line, thinner toward its ends.
            slant = (rng.random() - 0.5) * 0.5
            sag = (rng.random() - 0.5) * 0.5
            for k in range(rng.randint(12, 16)):
                along = -0.62 + 1.24 * rng.random()
                lobes.append((along, along * slant + sag * (along * along - 0.2) + (rng.random() - 0.5) * 0.22, 0.12 + rng.random() * 0.1,
                              (0.3 + rng.random() * 0.35) * (1 - abs(along) * 0.7)))
        else:
            # A heart of a few big lobes and a ragged fringe of small ones reaching well out.
            for k in range(rng.randint(11, 15)):
                heart = k < 4
                angle = rng.random() * math.tau
                reach = (0.05 + rng.random() * 0.2) if heart else (0.3 + rng.random() * 0.32)
                radius = (0.3 + rng.random() * 0.12) if heart else (0.12 + rng.random() * 0.14)
                weight = (0.7 + rng.random() * 0.3) if heart else (0.3 + rng.random() * 0.35)
                lobes.append((math.cos(angle) * reach, math.sin(angle) * reach * squash, radius, weight))
        warp_u = Noise(rng, 4)
        warp_v = Noise(rng, 4)
        coarse = Noise(rng, 5)
        fine = Noise(rng, 11)
        grain = Noise(rng, 19)

        def fn(u, v, lobes=lobes, warp_u=warp_u, warp_v=warp_v, coarse=coarse, fine=fine, grain=grain, wisp=wisp):
            uu = u * 0.5 + 0.5
            vv = v * 0.5 + 0.5
            # A little domain warp, so the lobes curl instead of sitting round.
            wu = u + (warp_u.at(uu, vv) - 0.5) * 0.28
            wv = v + (warp_v.at(uu, vv) - 0.5) * 0.28
            density = 0.0
            shade = 0.0
            for cx, cy, r, w in lobes:
                d2 = (wu - cx) ** 2 + (wv - cy) ** 2
                # A broad falloff, so lobes melt into each other and into the air.
                g = w * math.exp(-d2 / (1.7 * r * r))
                density += g
                # Lit from above: a lobe is paler on its upper side.
                shade += g * clamp(0.55 - (wv - cy) / (r * 2.4))
            if density <= 1e-4:
                return 0.0, 0.0
            lit = clamp(shade / density)
            n = fbm((coarse, fine, grain), uu, vv)
            density *= 0.45 + 1.1 * n
            if wisp:
                density *= 0.4 + 0.8 * smoothstep(0.3, 0.7, fine.at(uu * 0.7 % 1, vv * 1.6 % 1))
            # Soft all the way out: the alpha climbs slowly from the edge to the heart.
            alpha = smoothstep(0.03, 1.15, density)
            alpha *= 1 - smoothstep(0.7, 0.98, math.hypot(u, v))
            lum = 0.6 + 0.36 * lit + 0.1 * (grain.at(uu * 2 % 1, vv * 2 % 1) - 0.5)
            return clamp(lum), clamp(alpha * 0.92)

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
    """A thin line of bent air along +x: pale core, dark fringe, faint at the tail (left), gone at the head (right).

    Frame 0 is whole, frame 1 thinner and longer-tailed, frame 2 broken by gaps. All are straight: a wavering line reads as
    a drawn squiggle, not as moving air."""
    rng = random.Random(26_2_03)
    for f in range(frames):
        breaks = Noise(rng, 6)
        core_w = (0.9, 0.7, 0.85)[f]
        tail = (0.75, 0.9, 0.7)[f]

        def fn(u, v, breaks=breaks, f=f, core_w=core_w, tail=tail):
            t = (u + 1) / 2
            taper = smoothstep(0.0, tail, t) * (1 - smoothstep(0.9, 1.0, t))
            if f == 2:
                # Broken: gaps along the line.
                taper *= smoothstep(0.3, 0.55, breaks.at(t, 0.5))
            d = abs(v) * size / 2  # in pixels
            core = math.exp(-(d / core_w) ** 2)
            fringe = math.exp(-((d - 2.1) / 0.8) ** 2)
            alpha = taper * max(core * 0.62, fringe * 0.4)
            lum = 1.0 if core * 0.62 >= fringe * 0.4 else 0.0
            return lum, clamp(alpha)

        write_png("kinetic_streak_%d" % f, size, supersample(size, fn, 4))
    write_json("kinetic_streak", frames)


def shard(frames=4, size=16):
    """Splinters of frost broken off a body held in Stasis: long, thin, uneven slivers with a bright edge and a darker one."""
    rng = random.Random(26_2_14)
    for f in range(frames):
        # A long, thin, uneven quadrilateral along a random slant.
        angle = rng.random() * math.pi
        length = 0.75 + rng.random() * 0.2
        width = 0.12 + rng.random() * 0.1
        bend = (rng.random() - 0.5) * 0.12
        ca, sa = math.cos(angle), math.sin(angle)
        tip_a = 0.2 + rng.random() * 0.6
        tip_b = 0.2 + rng.random() * 0.6

        def fn(u, v, ca=ca, sa=sa, length=length, width=width, bend=bend, tip_a=tip_a, tip_b=tip_b):
            along = u * ca + v * sa
            across = -u * sa + v * ca - bend * (1 - (along / length) ** 2)
            if abs(along) > length:
                return 0.0, 0.0
            # Pointed at both ends, each end at its own slant.
            k = along / length
            half = width * (1 - abs(k) ** (1.6 if k > 0 else 1.2)) * (1 + (tip_a if k > 0 else tip_b) * 0.3 * k)
            if half <= 0 or abs(across) > half:
                return 0.0, 0.0
            side = across / half
            if side < -0.55:
                return 1.0, 0.95
            if side > 0.6:
                return 0.45, 0.85
            return 0.92 - 0.08 * side, 0.55

        write_png("kinetic_shard_%d" % f, size, supersample(size, fn, 4))
    write_json("kinetic_shard", frames)


def rime(frames=4, size=16):
    """Rime: a crust of hoarfrost, a lumpy pale patch with short frost needles bristling out of one side, a few glints.

    Asymmetric on purpose (frost feathers grow into the wind), so it never reads as a snowflake or a star."""
    rng = random.Random(26_2_15)
    for f in range(frames):
        grow = rng.random() * math.tau
        blobs = []
        for _ in range(rng.randint(3, 5)):
            angle = rng.random() * math.tau
            reach = rng.random() * 0.32
            blobs.append((math.cos(angle) * reach, math.sin(angle) * reach, 0.18 + rng.random() * 0.14))
        needles = []
        for _ in range(rng.randint(5, 8)):
            angle = grow + (rng.random() - 0.5) * 1.6
            base = rng.choice(blobs)
            needles.append((base[0] + math.cos(angle) * base[2] * 0.6, base[1] + math.sin(angle) * base[2] * 0.6, angle,
                            0.18 + rng.random() * 0.32))
        glints = [(rng.random() - 0.5) * 0.6 for _ in range(4)]
        speck = Noise(rng, 9)

        def fn(u, v, blobs=blobs, needles=needles, glints=glints, speck=speck):
            crust = 0.0
            for cx, cy, r in blobs:
                crust = max(crust, 1 - smoothstep(r * 0.55, r, math.hypot(u - cx, v - cy)))
            crust *= 0.65 + 0.7 * speck.at(u * 0.5 + 0.5, v * 0.5 + 0.5)
            spike = 0.0
            for sx, sy, angle, length in needles:
                dx, dy = math.cos(angle), math.sin(angle)
                along = (u - sx) * dx + (v - sy) * dy
                across = abs(-(u - sx) * dy + (v - sy) * dx)
                if 0 < along < length:
                    width = 0.06 * (1 - along / length)
                    if width > 0:
                        spike = max(spike, math.exp(-(across / width) ** 2) * (1 - smoothstep(length * 0.6, length, along)))
            glint = 0.0
            for i in range(0, 4, 2):
                glint = max(glint, math.exp(-((u - glints[i]) ** 2 + (v - glints[i + 1]) ** 2) / 0.006))
            alpha = clamp(max(crust * 0.92, spike * 0.85, glint))
            lum = clamp(0.78 + 0.16 * crust + 0.25 * glint - 0.06 * spike)
            return lum, alpha

        write_png("kinetic_rime_%d" % f, size, supersample(size, fn, 4))
    write_json("kinetic_rime", frames)


def leaf(frames=4, size=16):
    """A torn-off leaf: a pointed oval with a darker midrib and a lighter edge, at its own slant (the game tints it)."""
    rng = random.Random(26_2_16)
    for f in range(frames):
        angle = rng.random() * math.pi
        length = 0.7 + rng.random() * 0.2
        width = 0.3 + rng.random() * 0.15
        curl = (rng.random() - 0.5) * 0.25
        ca, sa = math.cos(angle), math.sin(angle)
        speck = Noise(rng, 6)

        def fn(u, v, ca=ca, sa=sa, length=length, width=width, curl=curl, speck=speck):
            along = u * ca + v * sa
            across = -u * sa + v * ca - curl * (along / length) ** 2
            k = along / length
            if abs(k) >= 1:
                return 0.0, 0.0
            half = width * math.sqrt(max(0.0, 1 - k * k)) * (1 - 0.35 * max(0.0, k))
            if abs(across) > half:
                return 0.0, 0.0
            rib = math.exp(-(across / 0.035) ** 2) * (1 - abs(k))
            edge = smoothstep(0.7, 1.0, abs(across) / half)
            lum = 0.72 + 0.18 * edge - 0.28 * rib + 0.1 * (speck.at(u * 0.5 + 0.5, v * 0.5 + 0.5) - 0.5)
            return clamp(lum), 1.0

        write_png("kinetic_leaf_%d" % f, size, supersample(size, fn, 4))
    write_json("kinetic_leaf", frames)


def main():
    os.makedirs(TEXTURES, exist_ok=True)
    os.makedirs(PARTICLES, exist_ok=True)
    dust()
    pebble()
    streak()
    shard()
    rime()
    leaf()


if __name__ == "__main__":
    main()
