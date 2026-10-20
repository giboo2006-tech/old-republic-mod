#!/usr/bin/env python3
"""Generates the planets' textures and asset JSON (PLACEHOLDER art, original, deterministic). Owned by W1 worlds.

Everything is drawn here with PIL from seeded noise; nothing is copied from any game or film. The art rule of
the mod holds: no glowing runes, sigils or rings; the waystone's face is a plain carved groove, its corruption
is veins in the rock.

Textures (src/main/resources/assets/oldrepublic/textures/):
  block/silverleaf.png                          16x16  pale silver-green fronds with white buds (cross, cutout)
  block/ashroot.png                             16x16  twisted rust-dark stalks with ash-grey tips (cross, cutout)
  block/aurelite_ore.png                        16x16  stone with amber-gold veins and bright flecks
  block/deepslate_aurelite_ore.png              16x16  banded deepslate with the same veins
  block/duskglass_cluster.png                   16x16  black glass shards with a dull red edge light (cross, cutout)
  block/budding_duskglass.png                   16x16  faceted black glass with dark red seams
  block/waystone_{front,side,top}.png           16x16  pale weathered standing stone, lichen, a carved groove on the face
  block/waystone_corrupted_{front,side,top}.png 16x16  the same stone with dark veins and dead brown lichen
  block/heartstone_{side,top}.png               16x16  a great dark stone, deep fissures, black veins
  block/sealed_plating.png                      16x16  riveted durasteel plates with a heavy weld seam
  block/hub_spawner.png                         16x16  dust specks (particle texture only; never rendered)
  item/aurelite_shard.png, duskglass_shard.png  16x16  a gold crystal shard; a black glass sliver with a red edge
  particle/ash_flake_{0..2}.png                 8x8    irregular grey-white flakes (tinted in code)
  particle/aurelite_glint_{0..3}.png            8x8    a four-point twinkle growing and shrinking (white, tinted in code)
  particle/duskglass_shard_{0..2}.png           8x8    black glass slivers with a dull red edge (drawn in colour)

Milestone 5 (Ghessar and Tyrvann, MILESTONE_5.md 3.2, 3.3, 15.2):
  block/salt_crust_{top,side}.png               16x16  white salt crust split into polygons; its side a crust over sandy salt
  block/sunstone_ore.png                        16x16  layered terracotta with hot amber seams and bright flecks
  block/stormglass_cluster.png                  16x16  smoky blue-grey glass shards with a pale electric edge (cross, cutout)
  block/peat.png                                16x16  dark compressed peat, fibrous layers, root threads, wet glints
  block/verse_stone_{side,top}.png              16x16  near-black tooled stone, fine fissures holding a faint pale warmth (awake)
  block/verse_stone_silent_{side,top}.png       16x16  the same stone with its fissures dark (silenced)
  block/holdfast_gate.png                       16x16  dark clan-iron plates, two riveted straps, rust runs
  item/sunstone.png, stormglass_shard.png       16x16  a rough amber stone glowing at its heart; a smoky glass shard
  particle/sand_gust_{0..3}.png                 8x8    soft wind-drawn streaks of sand (white, tinted in code)
  particle/verse_mote_{0..2}.png                8x8    a soft round mote, small to large (white, tinted in code)
  JSON: the six blocks' blockstates and models (verse_stone by awake, holdfast_gate by open: the open gate has
  only a particle texture, the plates have slid into the wall), items of the four block items and the two stones,
  particles/{sand_gust,verse_mote}.json

Run from anywhere: python3 tools/assets/gen_textures_planet.py         (everything)
                   python3 tools/assets/gen_textures_planet.py --m5    (the Milestone 5 files only)
"""
import json
import math
import os
import random
import sys

from PIL import Image

ASSETS = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "assets", "oldrepublic"))
NS = "oldrepublic"


# ---------------------------------------------------------------- output helpers

def write_png(rel, img):
	path = os.path.join(ASSETS, "textures", rel)
	os.makedirs(os.path.dirname(path), exist_ok=True)
	img.save(path)
	print("wrote", os.path.relpath(path))


def write_json(rel, data):
	path = os.path.join(ASSETS, rel)
	os.makedirs(os.path.dirname(path), exist_ok=True)
	with open(path, "w", encoding="utf-8", newline="\n") as f:
		json.dump(data, f, indent="\t")
		f.write("\n")
	print("wrote", os.path.relpath(path))


def clamp(v):
	return max(0, min(255, int(round(v))))


def mix(a, b, t):
	return tuple(clamp(x + (y - x) * t) for x, y in zip(a, b))


def noisy(rgb, rnd, amount):
	return tuple(clamp(c + rnd.uniform(-amount, amount)) for c in rgb)


def put(px, x, y, rgb, a=255):
	if 0 <= x < 16 and 0 <= y < 16:
		px[x, y] = tuple(rgb[:3]) + (a,)


def shade(px, x, y, t, toward=(0, 0, 0)):
	"""Mixes a pixel toward a colour (dark by default); wraps so the tile still tiles."""
	x %= 16
	y %= 16
	px[x, y] = mix(px[x, y][:3], toward, t) + (px[x, y][3],)


def value2(seed, x, y, scale):
	"""Smooth tiling value noise in [0, 1) on a 16-pixel torus."""
	cells = max(1, int(round(16 / scale)))

	def lattice(i, j):
		return random.Random(seed * 7919 + (i % cells) * 104729 + (j % cells) * 1299709).random()

	fx, fy = x / 16 * cells, y / 16 * cells
	i, j = int(math.floor(fx)), int(math.floor(fy))
	u, v = fx - i, fy - j
	u, v = u * u * (3 - 2 * u), v * v * (3 - 2 * v)
	top = lattice(i, j) * (1 - u) + lattice(i + 1, j) * u
	bottom = lattice(i, j + 1) * (1 - u) + lattice(i + 1, j + 1) * u
	return top * (1 - v) + bottom * v


def stone_base(seed, base, amount, mottle=0.0):
	rnd = random.Random(seed)
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
	px = img.load()
	for y in range(16):
		for x in range(16):
			c = noisy(base, rnd, amount)
			if mottle:
				c = mix(c, (0, 0, 0), (value2(seed, x, y, 5) - 0.5) * mottle) if value2(seed, x, y, 5) > 0.5 else \
					mix(c, (255, 255, 255), (0.5 - value2(seed, x, y, 5)) * mottle * 0.6)
			px[x, y] = c + (255,)
	for _ in range(10):
		x, y = rnd.randrange(16), rnd.randrange(16)
		shade(px, x, y, 0.25)
	for _ in range(8):
		x, y = rnd.randrange(16), rnd.randrange(16)
		shade(px, x, y, 0.15, (255, 255, 255))
	return img, px, rnd


def vein(px, rnd, start, steps, color, core, wobble=((1, 0), (1, 1), (0, 1), (1, -1), (1, 0))):
	"""A meandering vein that wraps across the tile: a core line with a softer edge either side."""
	x, y = start
	for _ in range(steps):
		put(px, x % 16, y % 16, mix(px[x % 16, y % 16][:3], core, 0.85))
		shade(px, x, y + 1, 0.45, color)
		shade(px, x, y - 1, 0.25, color)
		dx, dy = rnd.choice(wobble)
		x += dx
		y += dy


# ---------------------------------------------------------------- plants (cross models, cutout)

def blade(px, rnd, base_x, height, lean, width, light, dark, tip=None):
	"""One frond or stalk from the bottom row up, leaning `lean` pixels over its height."""
	for row in range(height):
		y = 15 - row
		t = row / max(1, height - 1)
		cx = base_x + lean * t * t
		w = max(0.5, width * (1 - t * 0.7))
		for x in range(int(math.floor(cx - w + 0.5)), int(math.floor(cx + w - 0.5)) + 1):
			if 0 <= x < 16:
				u = (x - (cx - w)) / max(0.01, 2 * w)
				c = mix(light, dark, u)
				if tip is not None and t > 0.82:
					c = mix(c, tip, (t - 0.82) / 0.18)
				px[x, y] = noisy(c, rnd, 5) + (255,)


def silverleaf():
	rnd = random.Random(401)
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	px = img.load()
	light, dark = (214, 226, 220), (118, 146, 132)
	for (bx, h, lean, w) in ((3.0, 9, -2.0, 0.9), (12.5, 10, 2.2, 0.9), (5.5, 12, -1.0, 1.1), (10.0, 13, 1.2, 1.1), (7.8, 14, 0.2, 1.2)):
		blade(px, rnd, bx, h, lean, w, light, dark)
	# Silver sheen down the midribs and two small white buds.
	for (x, y) in ((7, 4), (8, 6), (10, 5), (5, 7)):
		shade(px, x, y, 0.35, (255, 255, 255))
	for (x, y) in ((8, 1), (11, 3)):
		put(px, x, y, (246, 248, 244))
		put(px, x, y + 1, (222, 230, 224))
	return img


def ashroot():
	rnd = random.Random(411)
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	px = img.load()
	light, dark, ash = (134, 70, 58), (62, 30, 28), (176, 170, 164)
	for (bx, h, lean, w) in ((2.5, 7, 1.5, 0.7), (13.5, 8, -1.8, 0.7), (6.0, 11, 2.4, 0.8), (10.0, 12, -2.6, 0.8), (8.0, 10, 0.6, 0.9)):
		blade(px, rnd, bx, h, lean, w, light, dark, tip=ash)
	# Ash caught in the crooks of the stalks.
	for _ in range(7):
		x, y = rnd.randrange(16), rnd.randrange(6, 16)
		if px[x, y][3]:
			put(px, x, y, noisy(ash, rnd, 8))
	return img


# ---------------------------------------------------------------- ores and glass

AURELITE_CORE = (255, 212, 112)
AURELITE_EDGE = (196, 132, 40)


def aurelite_veins(px, rnd):
	for start, steps in (((1, 3), 6), ((9, 9), 6), ((4, 12), 4), ((12, 2), 3)):
		x, y = start
		for _ in range(steps):
			put(px, x, y, noisy(AURELITE_EDGE, rnd, 10))
			if rnd.random() < 0.6:
				put(px, x, y - 1, noisy(mix(AURELITE_EDGE, AURELITE_CORE, 0.6), rnd, 8))
			dx, dy = rnd.choice(((1, 0), (1, 1), (1, -1), (0, 1)))
			x, y = min(15, x + dx), max(0, min(15, y + dy))
	for (x, y) in ((3, 2), (10, 8), (5, 11), (13, 2), (11, 10)):
		put(px, x, y, AURELITE_CORE)


def aurelite_ore():
	img, px, rnd = stone_base(421, (125, 125, 127), 9)
	aurelite_veins(px, rnd)
	return img


def deepslate_aurelite_ore():
	img, px, rnd = stone_base(431, (74, 72, 80), 7)
	for y in (4, 9, 13):
		for x in range(16):
			shade(px, x, y, 0.18)
	aurelite_veins(px, rnd)
	return img


# Shards: (base x, half width at base, height, lean over the full height), back to front by height.
SHARDS = [
	(7.5, 2.3, 14, 0.4),
	(4.0, 1.7, 10, -2.4),
	(11.6, 1.8, 11, 2.2),
	(1.8, 1.1, 6, -2.0),
	(14.0, 1.1, 7, 1.8),
	(9.4, 1.2, 8, 1.2),
]


def shards(seed, light, mid, dark, edge, edge_side=0.85):
	rnd = random.Random(seed)
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	px = img.load()
	for (bx, hw, height, lean) in sorted(SHARDS, key=lambda s: s[2]):
		for row in range(height):
			y = 15 - row
			t = row / max(1, height - 1)
			cx = bx + lean * t
			w = max(0.35, hw * (1 - t * 0.85))
			for x in range(int(math.floor(cx - w + 0.5)), int(math.floor(cx + w - 0.5)) + 1):
				if 0 <= x < 16:
					u = (x - (cx - w)) / max(0.01, 2 * w)
					c = mix(light, mid, u / 0.3) if u < 0.3 else (mid if u < 0.7 else mix(mid, dark, (u - 0.7) / 0.3))
					if u > edge_side:
						c = mix(c, edge, 0.7)
					px[x, y] = noisy(c, rnd, 4) + (255,)
		tx, ty = int(round(bx + lean)), 15 - (height - 1)
		if 0 <= tx < 16 and 0 <= ty < 16:
			px[tx, ty] = mix(light, edge, 0.5) + (255,)
	return img


DUSK_LIGHT = (92, 80, 104)
DUSK_MID = (28, 22, 34)
DUSK_DARK = (10, 8, 14)
DUSK_EDGE = (150, 46, 40)


def duskglass_cluster():
	return shards(441, DUSK_LIGHT, DUSK_MID, DUSK_DARK, DUSK_EDGE)


def budding_duskglass():
	img, px, rnd = stone_base(451, (24, 20, 30), 5)
	# Facets: lighter planes split by thin dark seams with a dull red light in them.
	for y in range(16):
		for x in range(16):
			f = value2(453, x, y, 4)
			if f > 0.62:
				shade(px, x, y, (f - 0.62) * 1.2, DUSK_LIGHT)
	for start, steps in (((0, 5), 9), ((6, 0), 7), ((3, 13), 8)):
		x, y = start
		for _ in range(steps):
			put(px, x % 16, y % 16, (14, 10, 16))
			shade(px, x + 1, y, 0.3, DUSK_EDGE)
			dx, dy = rnd.choice(((1, 1), (1, 0), (0, 1)))
			x += dx
			y += dy
	for (x, y) in ((4, 4), (11, 9), (8, 14)):
		put(px, x, y, (184, 70, 58))
	return img


# ---------------------------------------------------------------- the stones

WAY_STONE = (176, 178, 170)
LICHEN = (148, 168, 112)
DEAD_LICHEN = (120, 96, 70)
VEIN_DARK = (34, 22, 40)
VEIN_EDGE = (96, 40, 44)


def weathered(seed, base, lichen):
	img, px, rnd = stone_base(seed, base, 7, mottle=0.35)
	# Rain streaks down the faces, lichen blooms low on the stone.
	for x in range(16):
		if rnd.random() < 0.3:
			for y in range(rnd.randrange(4), rnd.randrange(8, 16)):
				shade(px, x, y, 0.08)
	for _ in range(5):
		cx, cy = rnd.randrange(16), rnd.randrange(8, 16)
		for _ in range(6):
			x, y = cx + rnd.randrange(-1, 2), cy + rnd.randrange(-1, 2)
			if 0 <= x < 16 and 0 <= y < 16:
				put(px, x, y, noisy(lichen, rnd, 10))
	return img, px, rnd


def carved_face(px, rnd):
	"""A worn carved groove down the face, rounded at the top: a shallow cut, lit from above, never lit itself."""
	for y in range(3, 14):
		shade(px, 7, y, 0.42)
		shade(px, 8, y, 0.32)
		shade(px, 6, y, 0.12, (255, 255, 255))
		shade(px, 9, y, 0.22)
	for x in (6, 7, 8, 9):
		shade(px, x, 2, 0.25 if x in (7, 8) else 0.1)
	# Chips off the arris.
	for _ in range(4):
		shade(px, rnd.choice((0, 15)), rnd.randrange(16), 0.3)


def corrupt(px, rnd):
	for start, steps in (((0, 4), 10), ((5, 15), 9), ((10, 0), 8)):
		vein(px, rnd, start, steps, VEIN_EDGE, VEIN_DARK, wobble=((1, 0), (1, 1), (0, 1), (1, -1), (0, -1)))


def waystones():
	out = {}
	for corrupted in (False, True):
		name = "waystone_corrupted" if corrupted else "waystone"
		lichen = DEAD_LICHEN if corrupted else LICHEN
		base = mix(WAY_STONE, (110, 104, 112), 0.25) if corrupted else WAY_STONE
		front, fpx, rnd = weathered(461, base, lichen)
		carved_face(fpx, rnd)
		side, spx, srnd = weathered(463, base, lichen)
		top, tpx, trnd = stone_base(467, mix(base, (255, 255, 255), 0.08), 6, mottle=0.3)
		for x in range(16):
			shade(tpx, x, 0, 0.15)
			shade(tpx, 0, x, 0.15)
		if corrupted:
			corrupt(fpx, random.Random(471))
			corrupt(spx, random.Random(473))
			corrupt(tpx, random.Random(479))
		out[name + "_front"] = front
		out[name + "_side"] = side
		out[name + "_top"] = top
	return out


def heartstone():
	side, px, rnd = stone_base(481, (84, 82, 90), 7, mottle=0.45)
	for start, steps in (((2, 0), 16), ((11, 3), 13)):
		x, y = start
		for _ in range(steps):
			put(px, x % 16, y % 16, (22, 18, 26))
			shade(px, x + 1, y, 0.25, (150, 146, 156))
			x += rnd.choice((0, 0, 1, -1))
			y += 1
	corrupt(px, random.Random(483))
	top, tpx, trnd = stone_base(487, (92, 90, 98), 6, mottle=0.4)
	# A jagged fissure across the top, wrapping so the column tiles.
	y = 7
	for x in range(16):
		put(tpx, x, y, (24, 20, 28))
		shade(tpx, x, y + 1, 0.3, (150, 146, 156))
		y = max(4, min(11, y + trnd.choice((-1, 0, 0, 1))))
	corrupt(tpx, random.Random(489))
	return side, top


def sealed_plating():
	img, px, rnd = stone_base(491, (112, 118, 124), 6)
	# Two plates meeting at a heavy horizontal weld seam; rivets along the edges.
	for x in range(16):
		put(px, x, 0, (70, 74, 80))
		put(px, x, 15, (70, 74, 80))
		put(px, x, 7, noisy((150, 140, 128), rnd, 12))
		put(px, x, 8, noisy((92, 84, 76), rnd, 10))
		shade(px, x, 6, 0.3)
		shade(px, x, 9, 0.15, (255, 255, 255))
	for y in range(16):
		shade(px, 0, y, 0.25)
		shade(px, 15, y, 0.2)
	for (x, y) in ((2, 2), (13, 2), (2, 12), (13, 12), (7, 3), (7, 12)):
		put(px, x, y, (164, 170, 176))
		put(px, x + 1, y + 1, (60, 62, 68))
	# A soot bloom off the seam where it was welded shut.
	for _ in range(12):
		x, y = rnd.randrange(16), 6 + rnd.randrange(5)
		shade(px, x, y, 0.2, (40, 34, 30))
	return img


def hub_spawner():
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	px = img.load()
	rnd = random.Random(497)
	for _ in range(14):
		put(px, rnd.randrange(16), rnd.randrange(16), (168, 160, 148), 200)
	return img


# ---------------------------------------------------------------- items

def shard_item(seed, light, mid, dark, edge, glint):
	"""A single faceted shard lying corner to corner: light facet on the upper left, dark on the lower right."""
	rnd = random.Random(seed)
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	px = img.load()
	# The shard's outline in a frame along its axis (u from the base, v across), then rasterised.
	outline = [(0.0, 0.0), (2.5, -2.2), (10.5, -1.6), (13.5, 0.0), (10.5, 1.6), (2.5, 2.2)]
	axis = (1 / math.sqrt(2), -1 / math.sqrt(2))
	across = (1 / math.sqrt(2), 1 / math.sqrt(2))
	for y in range(16):
		for x in range(16):
			rx, ry = x + 0.5 - 2.5, y + 0.5 - 13.5
			u = rx * axis[0] + ry * axis[1]
			v = rx * across[0] + ry * across[1]
			inside = False
			for i in range(len(outline)):
				(u1, v1), (u2, v2) = outline[i], outline[(i + 1) % len(outline)]
				if (v1 > v) != (v2 > v) and u < (u2 - u1) * (v - v1) / (v2 - v1) + u1:
					inside = not inside
			if inside:
				c = mix(light, mid, (v + 2.2) / 2.2) if v < 0 else mix(mid, dark, v / 2.2)
				if v > 1.2:
					c = mix(c, edge, 0.55)
				px[x, y] = noisy(c, rnd, 4) + (255,)
	for (x, y) in glint:
		put(px, x, y, mix(light, (255, 255, 255), 0.6))
	return img


# ---------------------------------------------------------------- particles

def particles():
	frames = {}
	for frame in range(3):
		rnd = random.Random(501 + frame)
		img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
		px = img.load()
		pts = [(3.5 + rnd.uniform(-1.4, 1.4), 3.5 + rnd.uniform(-1.4, 1.4)) for _ in range(4 + frame)]
		for y in range(8):
			for x in range(8):
				a = 0.0
				for (cx, cy) in pts:
					a = max(a, 1 - math.hypot((x - cx) * 1.2, (y - cy) * 0.8) / 1.8)
				if a > 0.15:
					g = clamp(220 + rnd.uniform(-20, 20))
					px[x, y] = (g, g, g, clamp(255 * min(1.0, a * 1.4)))
		frames[f"ash_flake_{frame}"] = img
	for frame, arm in enumerate((1.4, 2.8, 3.6, 2.0)):
		img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
		px = img.load()
		for y in range(8):
			for x in range(8):
				dx, dy = abs(x - 3.5), abs(y - 3.5)
				core = max(0.0, 1 - math.hypot(dx, dy) / 1.5)
				cross = 0.0
				if dx < 0.6:
					cross = max(cross, 1 - dy / arm)
				if dy < 0.6:
					cross = max(cross, 1 - dx / arm)
				a = max(core, cross)
				if a > 0:
					px[x, y] = (255, 255, 255, clamp(255 * min(1.0, a)))
		frames[f"aurelite_glint_{frame}"] = img
	for frame in range(3):
		rnd = random.Random(511 + frame)
		img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
		px = img.load()
		angle = 0.6 + frame * 0.7
		ca, sa = math.cos(angle), math.sin(angle)
		for y in range(8):
			for x in range(8):
				u = (x - 3.5) * ca + (y - 3.5) * sa
				v = -(x - 3.5) * sa + (y - 3.5) * ca
				half = 0.9 * (1 - abs(u) / 3.4)
				if abs(u) < 3.4 and abs(v) < half + 0.15:
					c = DUSK_MID if v < half * 0.4 else DUSK_EDGE
					if abs(u) > 2.6:
						c = mix(c, DUSK_LIGHT, 0.5)
					px[x, y] = noisy(c, rnd, 6) + (255,)
		frames[f"duskglass_shard_{frame}"] = img
	return frames


# ---------------------------------------------------------------- Milestone 5: Ghessar and Tyrvann

SALT = (236, 232, 222)
SALT_RIM = (250, 248, 242)
SALT_CRACK = (178, 162, 138)


def torus_distance(ax, ay, bx, by):
	dx = abs(ax - bx)
	dy = abs(ay - by)
	return math.hypot(min(dx, 16 - dx), min(dy, 16 - dy))


def salt_crust():
	"""Top: the dried pan splits into polygons (a tiling Voronoi pattern) whose edges curl up white; side: crust over sandy salt."""
	rnd = random.Random(601)
	cells = [(rnd.uniform(0, 16), rnd.uniform(0, 16)) for _ in range(7)]
	top = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
	px = top.load()
	for y in range(16):
		for x in range(16):
			d = sorted(torus_distance(x + 0.5, y + 0.5, cx, cy) for (cx, cy) in cells)
			gap = d[1] - d[0]
			if gap < 0.8:
				c = noisy(SALT_CRACK, rnd, 8)
			elif gap < 1.8:
				c = noisy(SALT_RIM, rnd, 4)
			else:
				c = noisy(mix(SALT, (226, 214, 196), value2(603, x, y, 6) * 0.6), rnd, 5)
			px[x, y] = c + (255,)
	side, spx, srnd = stone_base(607, (214, 196, 156), 8, mottle=0.25)
	for y in range(5):
		for x in range(16):
			band = SALT_RIM if y in (0, 3) else SALT
			put(spx, x, y, noisy(band, srnd, 5))
	for x in range(16):
		if srnd.random() < 0.45:
			put(spx, x, 5, noisy(SALT, srnd, 8))
	# Salt grains wicked up through the sand below.
	for _ in range(22):
		x, y = srnd.randrange(16), srnd.randrange(6, 16)
		shade(spx, x, y, 0.55 - (y - 6) * 0.04, SALT_RIM)
	return top, side


SUN_CORE = (255, 204, 96)
SUN_EDGE = (214, 116, 38)


def sunstone_ore():
	img, px, rnd = stone_base(611, (164, 92, 58), 8, mottle=0.3)
	# Canyon strata: darker red bands with a paler parting under each.
	for y, t in ((3, 0.22), (8, 0.16), (12, 0.24)):
		for x in range(16):
			shade(px, x, y, t, (110, 44, 30))
			shade(px, x, y + 1, 0.08, (236, 196, 150))
	for start, steps in (((0, 5), 10), ((9, 11), 8), ((4, 14), 6)):
		vein(px, rnd, start, steps, SUN_EDGE, SUN_CORE, wobble=((1, 0), (1, -1), (1, 0), (1, 1), (0, 1)))
	for (x, y) in ((3, 6), (12, 12), (8, 4), (14, 2)):
		put(px, x, y, (255, 238, 176))
	return img


STORM_LIGHT = (168, 186, 214)
STORM_MID = (70, 82, 106)
STORM_DARK = (32, 36, 50)
STORM_EDGE = (150, 196, 255)


def stormglass_cluster():
	return shards(621, STORM_LIGHT, STORM_MID, STORM_DARK, STORM_EDGE, edge_side=0.75)


def peat():
	img, px, rnd = stone_base(631, (60, 42, 30), 6, mottle=0.3)
	# Compressed layers: fibrous strands mostly along the bedding.
	for _ in range(16):
		x, y = rnd.randrange(16), rnd.randrange(16)
		for _ in range(rnd.randrange(3, 7)):
			put(px, x % 16, y % 16, noisy((96, 72, 48), rnd, 10))
			x += 1
			y += rnd.choice((0, 0, 0, 1, -1))
	# Pale root threads and a few wet glints.
	for start, steps in (((2, 2), 6), ((10, 9), 5)):
		x, y = start
		for _ in range(steps):
			put(px, x % 16, y % 16, (128, 104, 72))
			x += rnd.choice((0, 1))
			y += 1
	for (x, y) in ((5, 11), (13, 4), (8, 7)):
		put(px, x, y, (104, 96, 88))
	return img


VERSE_STONE = (40, 40, 46)
VERSE_WARM = (218, 204, 164)


def verse_stone(awake):
	"""A near-black tooled monolith; its fine fissures hold a faint pale warmth while the verse is awake. No runes."""
	side, px, rnd = stone_base(641, VERSE_STONE, 5, mottle=0.4)
	# Worn tooling: shallow horizontal chisel courses.
	for y in (2, 6, 10, 14):
		for x in range(16):
			if rnd.random() < 0.7:
				shade(px, x, y, 0.18)
				shade(px, x, y - 1, 0.06, (255, 255, 255))
	top, tpx, trnd = stone_base(643, mix(VERSE_STONE, (255, 255, 255), 0.05), 5, mottle=0.35)
	glow = VERSE_WARM if awake else (18, 18, 22)
	strength = 0.5 if awake else 0.9
	frnd = random.Random(647)
	for start, steps in (((4, 0), 16), ((12, 5), 11)):
		x, y = start
		for _ in range(steps):
			shade(px, x, y, strength, glow)
			if awake:
				shade(px, x + 1, y, 0.12, glow)
			x += frnd.choice((0, 0, 1, -1))
			y += 1
	x = 0
	y = 6
	for _ in range(16):
		shade(tpx, x, y, strength, glow)
		x += 1
		y = max(3, min(12, y + frnd.choice((-1, 0, 0, 1))))
	return side, top


def holdfast_gate():
	img, px, rnd = stone_base(651, (70, 68, 66), 6, mottle=0.25)
	# Two plates per block: dark seams down the edge and the middle.
	for y in range(16):
		put(px, 0, y, (36, 34, 34))
		put(px, 8, y, (40, 38, 38))
		shade(px, 1, y, 0.12, (255, 255, 255))
		shade(px, 9, y, 0.1, (255, 255, 255))
	# Riveted straps across, with rust running down from the rivets.
	for top in (3, 11):
		for x in range(16):
			put(px, x, top, noisy((92, 88, 84), rnd, 6))
			put(px, x, top + 1, noisy((58, 54, 52), rnd, 6))
		for x in (3, 12):
			put(px, x, top, (150, 146, 140))
			put(px, x, top + 1, (40, 36, 34))
			for run in range(2, rnd.randrange(4, 7)):
				shade(px, x, top + run, 0.35 - run * 0.04, (128, 70, 40))
	return img


def sunstone_item():
	"""A rough amber stone: an irregular rounded outline, lit from the upper left, hottest at its heart."""
	rnd = random.Random(661)
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	px = img.load()
	outline = [(8.0, 2.2), (12.4, 4.0), (13.8, 8.6), (11.6, 13.0), (6.8, 13.8), (2.8, 11.0), (2.4, 6.2), (4.8, 3.0)]
	for y in range(16):
		for x in range(16):
			cx, cy = x + 0.5, y + 0.5
			inside = False
			for i in range(len(outline)):
				(x1, y1), (x2, y2) = outline[i], outline[(i + 1) % len(outline)]
				if (y1 > cy) != (y2 > cy) and cx < (x2 - x1) * (cy - y1) / (y2 - y1) + x1:
					inside = not inside
			if not inside:
				continue
			heart = max(0.0, 1 - math.hypot(cx - 7.6, cy - 8.4) / 4.2)
			light = max(0.0, 1 - math.hypot(cx - 5.0, cy - 5.0) / 6.0)
			c = mix((176, 82, 24), (240, 150, 52), light)
			c = mix(c, (255, 226, 140), heart * 0.8)
			if cx + cy > 19.5:
				c = mix(c, (120, 50, 18), 0.45)
			px[x, y] = noisy(c, rnd, 5) + (255,)
	for (x, y) in ((5, 5), (6, 4)):
		put(px, x, y, (255, 246, 214))
	return img


def m5_particles():
	frames = {}
	for frame in range(4):
		rnd = random.Random(671 + frame)
		img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
		px = img.load()
		length = 2.6 + frame * 0.5
		for y in range(8):
			for x in range(8):
				# A soft streak along the wind (left to right), thinning at its tail.
				u = (x - 3.5) / length
				v = (y - 3.5 + (x - 3.5) * 0.18) / (1.1 - 0.12 * frame)
				a = max(0.0, 1 - u * u - v * v)
				a *= 0.6 + 0.4 * rnd.random()
				if a > 0.08:
					g = clamp(236 + rnd.uniform(-14, 10))
					px[x, y] = (g, g, g, clamp(240 * min(1.0, a * 1.2)))
		frames[f"sand_gust_{frame}"] = img
	for frame, radius in enumerate((1.4, 2.1, 2.8)):
		img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
		px = img.load()
		for y in range(8):
			for x in range(8):
				a = max(0.0, 1 - math.hypot(x - 3.5, y - 3.5) / radius)
				if a > 0:
					px[x, y] = (255, 255, 255, clamp(255 * min(1.0, a * 1.3)))
		frames[f"verse_mote_{frame}"] = img
	return frames


# ---------------------------------------------------------------- JSON

def block_model(name, parent, textures):
	write_json(f"models/block/{name}.json", {"parent": parent, "textures": textures})


def single_variant(name, model=None):
	write_json(f"blockstates/{name}.json", {"variants": {"": {"model": f"{NS}:block/{model or name}"}}})


def item_definition(name, model=None):
	write_json(f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:{model or 'item/' + name}"}})


def generated_item(name, texture):
	write_json(f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": texture}})


FACING6 = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "south": {"x": 90, "y": 180}, "east": {"x": 90, "y": 90}, "west": {"x": 90, "y": 270}}
FACING4 = {"north": 0, "east": 90, "south": 180, "west": 270}


def json_files():
	for plant in ("silverleaf", "ashroot"):
		block_model(plant, "minecraft:block/cross", {"cross": f"{NS}:block/{plant}"})
		single_variant(plant)
		generated_item(plant, f"{NS}:block/{plant}")
		item_definition(plant)
	for ore in ("aurelite_ore", "deepslate_aurelite_ore", "budding_duskglass", "sealed_plating"):
		block_model(ore, "minecraft:block/cube_all", {"all": f"{NS}:block/{ore}"})
		single_variant(ore)
		write_json(f"models/item/{ore}.json", {"parent": f"{NS}:block/{ore}"})
		item_definition(ore)

	block_model("duskglass_cluster", "minecraft:block/cross", {"cross": f"{NS}:block/duskglass_cluster"})
	write_json("blockstates/duskglass_cluster.json", {"variants": {
		f"facing={facing}": dict({"model": f"{NS}:block/duskglass_cluster"}, **rot) for facing, rot in FACING6.items()}})
	generated_item("duskglass_cluster", f"{NS}:block/duskglass_cluster")
	item_definition("duskglass_cluster")

	for stone in ("waystone", "waystone_corrupted"):
		block_model(stone, "minecraft:block/orientable", {
			"front": f"{NS}:block/{stone}_front", "side": f"{NS}:block/{stone}_side", "top": f"{NS}:block/{stone}_top",
			"particle": f"{NS}:block/{stone}_side"})
		write_json(f"blockstates/{stone}.json", {"variants": {
			f"facing={facing}": ({"model": f"{NS}:block/{stone}", "y": y} if y else {"model": f"{NS}:block/{stone}"})
			for facing, y in FACING4.items()}})
		write_json(f"models/item/{stone}.json", {"parent": f"{NS}:block/{stone}"})
		item_definition(stone)

	block_model("heartstone", "minecraft:block/cube_column", {"end": f"{NS}:block/heartstone_top", "side": f"{NS}:block/heartstone_side"})
	single_variant("heartstone")
	write_json("models/item/heartstone.json", {"parent": f"{NS}:block/heartstone"})
	item_definition("heartstone")

	# The hub spawner has no item and never renders: its model only names a particle texture.
	write_json("models/block/hub_spawner.json", {"textures": {"particle": f"{NS}:block/hub_spawner"}})
	single_variant("hub_spawner")

	for shard in ("aurelite_shard", "duskglass_shard"):
		generated_item(shard, f"{NS}:item/{shard}")
		item_definition(shard)

	write_json("particles/ash_flake.json", {"textures": [f"{NS}:ash_flake_{i}" for i in range(3)]})
	write_json("particles/aurelite_glint.json", {"textures": [f"{NS}:aurelite_glint_{i}" for i in range(4)]})
	write_json("particles/duskglass_shard.json", {"textures": [f"{NS}:duskglass_shard_{i}" for i in range(3)]})


def m5_json_files():
	block_model("salt_crust", "minecraft:block/cube_column", {"end": f"{NS}:block/salt_crust_top", "side": f"{NS}:block/salt_crust_side"})
	for name in ("sunstone_ore", "peat"):
		block_model(name, "minecraft:block/cube_all", {"all": f"{NS}:block/{name}"})
	for name in ("salt_crust", "sunstone_ore", "peat"):
		single_variant(name)
		write_json(f"models/item/{name}.json", {"parent": f"{NS}:block/{name}"})
		item_definition(name)

	block_model("stormglass_cluster", "minecraft:block/cross", {"cross": f"{NS}:block/stormglass_cluster"})
	write_json("blockstates/stormglass_cluster.json", {"variants": {
		f"facing={facing}": dict({"model": f"{NS}:block/stormglass_cluster"}, **rot) for facing, rot in FACING6.items()}})
	generated_item("stormglass_cluster", f"{NS}:block/stormglass_cluster")
	item_definition("stormglass_cluster")

	# The verse stone and the gate have no items (ModRegistry.blockOnly).
	block_model("verse_stone", "minecraft:block/cube_column", {"end": f"{NS}:block/verse_stone_top", "side": f"{NS}:block/verse_stone_side"})
	block_model("verse_stone_silent", "minecraft:block/cube_column",
		{"end": f"{NS}:block/verse_stone_silent_top", "side": f"{NS}:block/verse_stone_silent_side"})
	write_json("blockstates/verse_stone.json", {"variants": {
		"awake=true": {"model": f"{NS}:block/verse_stone"}, "awake=false": {"model": f"{NS}:block/verse_stone_silent"}}})
	block_model("holdfast_gate", "minecraft:block/cube_all", {"all": f"{NS}:block/holdfast_gate"})
	write_json("models/block/holdfast_gate_open.json", {"textures": {"particle": f"{NS}:block/holdfast_gate"}})
	write_json("blockstates/holdfast_gate.json", {"variants": {
		"open=false": {"model": f"{NS}:block/holdfast_gate"}, "open=true": {"model": f"{NS}:block/holdfast_gate_open"}}})

	for item in ("sunstone", "stormglass_shard"):
		generated_item(item, f"{NS}:item/{item}")
		item_definition(item)

	write_json("particles/sand_gust.json", {"textures": [f"{NS}:sand_gust_{i}" for i in range(4)]})
	write_json("particles/verse_mote.json", {"textures": [f"{NS}:verse_mote_{i}" for i in range(3)]})


def m5_main():
	top, side = salt_crust()
	write_png("block/salt_crust_top.png", top)
	write_png("block/salt_crust_side.png", side)
	write_png("block/sunstone_ore.png", sunstone_ore())
	write_png("block/stormglass_cluster.png", stormglass_cluster())
	write_png("block/peat.png", peat())
	for awake in (True, False):
		side, top = verse_stone(awake)
		name = "verse_stone" if awake else "verse_stone_silent"
		write_png(f"block/{name}_side.png", side)
		write_png(f"block/{name}_top.png", top)
	write_png("block/holdfast_gate.png", holdfast_gate())
	write_png("item/sunstone.png", sunstone_item())
	write_png("item/stormglass_shard.png", shard_item(663, STORM_LIGHT, STORM_MID, STORM_DARK, STORM_EDGE, ((7, 7), (10, 4))))
	for name, img in m5_particles().items():
		write_png(f"particle/{name}.png", img)
	m5_json_files()


def main():
	if "--m5" in sys.argv[1:]:
		m5_main()
		return
	write_png("block/silverleaf.png", silverleaf())
	write_png("block/ashroot.png", ashroot())
	write_png("block/aurelite_ore.png", aurelite_ore())
	write_png("block/deepslate_aurelite_ore.png", deepslate_aurelite_ore())
	write_png("block/duskglass_cluster.png", duskglass_cluster())
	write_png("block/budding_duskglass.png", budding_duskglass())
	for name, img in waystones().items():
		write_png(f"block/{name}.png", img)
	side, top = heartstone()
	write_png("block/heartstone_side.png", side)
	write_png("block/heartstone_top.png", top)
	write_png("block/sealed_plating.png", sealed_plating())
	write_png("block/hub_spawner.png", hub_spawner())
	write_png("item/aurelite_shard.png", shard_item(521, (255, 236, 170), (232, 170, 70), (150, 92, 30), (255, 214, 120), ((6, 8), (9, 5))))
	write_png("item/duskglass_shard.png", shard_item(523, DUSK_LIGHT, DUSK_MID, DUSK_DARK, DUSK_EDGE, ((7, 7),)))
	for name, img in particles().items():
		write_png(f"particle/{name}.png", img)
	json_files()
	m5_main()


if __name__ == "__main__":
	main()
