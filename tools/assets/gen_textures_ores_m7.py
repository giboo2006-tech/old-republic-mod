#!/usr/bin/env python3
"""Generates Milestone 7's ores and hilt materials (docs/MILESTONE_7.md 4): their textures, block states, models and
item definitions, the blocks' loot tables, the mining tags, the smelting, blasting and hymnsteel recipes and the deep
starfall veins' worldgen. Owned by OR. Original art, drawn here from shapes and seeded noise; nothing is copied or
traced. Deterministic: every run writes the same bytes.

Textures (src/main/resources/assets/oldrepublic/textures/):
  block/starfall_ore.png               16x16  stone with dark violet-grey metal nuggets and a faint glitter
  block/deepslate_starfall_ore.png     16x16  banded deepslate with the same nuggets, a little lighter
  block/starfall_rock.png              16x16  the meteorite's charred fusion crust: thumbprint dents, cracks, ash, a few
                                              violet metal glints
  block/clan_iron_ore.png, _top.png    16x16  Tyrvann basalt (streaked sides, a cracked column end) with blued metal seams
  item/raw_starfall.png                16x16  a lumpy chunk of raw violet-grey metal with glitter
  item/starfall_ingot.png              16x16  an ingot of starfall iron (the hilt tint 8C8496)
  item/hymnsteel_ingot.png             16x16  an ingot of hymnsteel, pale lilac-white (CCC2E8), with a polished sheen

JSON (src/main/resources/assets/oldrepublic/):
  blockstates/<block>.json, models/block/<block>.json, items/<block>.json for starfall_ore, deepslate_starfall_ore,
  starfall_rock (sixteen turned variants, so a meteorite's shell does not tile) and clan_iron_ore (a column, like basalt);
  items/<item>.json and models/item/<item>.json for raw_starfall, starfall_ingot and hymnsteel_ingot.

Data (src/main/resources/data/):
  oldrepublic/loot_table/blocks/<block>.json   the ores: Silk Touch gives the block, else one raw starfall or clan iron
                                                with Fortune (ore_drops) and explosion decay, as vanilla's deepslate iron
                                                ore; the rock drops itself
  minecraft/tags/block/{mineable/pickaxe,needs_iron_tool,needs_diamond_tool}.json   merged: their other values stay
  oldrepublic/recipe/starfall_ingot_from_{smelting,blasting}_<input>.json   raw starfall and both ores: 200 or 100
                                                ticks, 0.7 experience (as vanilla's iron)
  oldrepublic/recipe/hymnsteel_ingot.json      shapeless: hymnstone shard, clan iron, sunstone, starfall ingot -> 2
  oldrepublic/worldgen/{configured,placed}_feature/ore_starfall_deep.json   veins of 4 in deepslate, 2 a chunk from
                                                y -64 to -8 (added to every overworld biome by world.ore.OreFeature)

Tyrvann's clan iron ore feature is written by gen_structures_m5.py --planets; the meteor craters by
tools/assets/structures/or/build_all.py.

Run from anywhere: python3 tools/assets/gen_textures_ores_m7.py [--out DIR] [--preview DIR]
  --out DIR      write everything under DIR (as src/main/resources/...) instead of the repository: a dry run to diff
  --preview DIR  also write enlarged previews of every texture into DIR
"""
import argparse
import json
import math
import os
import random

from PIL import Image

REPO = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
NS = "oldrepublic"

BLOCKS = ("starfall_ore", "deepslate_starfall_ore", "starfall_rock", "clan_iron_ore")
ITEMS = ("raw_starfall", "starfall_ingot", "hymnsteel_ingot")


# ---------------------------------------------------------------- output

class Out:
	"""Where files go: the repository's resources, or the same tree under --out. Reads always come from the repository
	(a tag merged under --out starts from the repository's file)."""

	def __init__(self, root=None):
		self.root = os.path.abspath(root) if root else REPO
		self.written = []

	def path(self, rel):
		return os.path.join(self.root, rel)

	def write_text(self, rel, text):
		path = self.path(rel)
		os.makedirs(os.path.dirname(path), exist_ok=True)
		with open(path, "w", encoding="utf-8", newline="\n") as f:
			f.write(text)
		self.written.append(rel)

	def write_json(self, rel, data, indent=2, sort_keys=False):
		self.write_text(rel, json.dumps(data, indent=indent, sort_keys=sort_keys) + "\n")

	def write_png(self, rel, img):
		path = self.path(rel)
		os.makedirs(os.path.dirname(path), exist_ok=True)
		img.save(path, optimize=False)
		self.written.append(rel)


ASSETS = "src/main/resources/assets/" + NS
DATA = "src/main/resources/data"


# ---------------------------------------------------------------- colour helpers

def clamp(v):
	return max(0, min(255, int(round(v))))


def mix(a, b, t):
	return tuple(clamp(x + (y - x) * t) for x, y in zip(a, b))


def noisy(rgb, rnd, amount):
	"""Grain: the same random lift or drop on every channel, so greys stay grey."""
	d = rnd.uniform(-amount, amount)
	return tuple(clamp(c + d) for c in rgb)


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


def new(w=16, h=16, fill=(0, 0, 0, 0)):
	return Image.new("RGBA", (w, h), fill)


def get(px, x, y):
	return px[x % 16, y % 16][:3]


def put(px, x, y, rgb):
	px[x % 16, y % 16] = tuple(rgb[:3]) + (255,)


def toward(px, x, y, rgb, t):
	put(px, x, y, mix(get(px, x, y), rgb, t))


# ---------------------------------------------------------------- stone grounds (tiling)

def stone(seed, base, amount, specks=(10, 8)):
	"""Vanilla-like stone: per-pixel noise over a soft mottle, a few darker and lighter specks."""
	rnd = random.Random(seed)
	img = new()
	px = img.load()
	for y in range(16):
		for x in range(16):
			m = value2(seed, x, y, 5) - 0.5
			put(px, x, y, noisy(tuple(c * (1 + m * 0.12) for c in base), rnd, amount))
	for _ in range(specks[0]):
		toward(px, rnd.randrange(16), rnd.randrange(16), (0, 0, 0), 0.22)
	for _ in range(specks[1]):
		toward(px, rnd.randrange(16), rnd.randrange(16), (255, 255, 255), 0.14)
	return img, px, rnd


def deepslate(seed):
	"""Dark banded deepslate: horizontal partings and short streaks."""
	img, px, rnd = stone(seed, (76, 75, 82), 6, specks=(6, 6))
	for y in (2, 7, 11, 14):
		for x in range(16):
			if value2(seed + y, x, y, 3) > 0.32:
				toward(px, x, y, (30, 30, 36), 0.30)
	for _ in range(7):
		x, y = rnd.randrange(16), rnd.randrange(16)
		for k in range(rnd.randint(2, 4)):
			toward(px, x + k, y, (118, 118, 126), 0.18)
	return img, px, rnd


# ---------------------------------------------------------------- relief shading

LIGHT = (-0.55, -0.65, 0.95)   # from the upper left and in front, as Minecraft's icons are lit


def unit(v):
	n = math.sqrt(sum(c * c for c in v))
	return tuple(c / n for c in v)


def torus_delta(a, b):
	"""The shortest signed distance from b to a on a 16-pixel loop."""
	return (a - b + 8) % 16 - 8


def relief(height, strength=1.0):
	"""Lighting of a 16 x 16 heightfield on a torus (so it tiles), relative to a flat face: about -1 (facing away) to
	+0.3 (facing the light)."""
	lx, ly, lz = unit(LIGHT)
	flat = lz
	out = {}
	for y in range(16):
		for x in range(16):
			dx = (height[((x + 1) % 16, y)] - height[((x - 1) % 16, y)]) / 2 * strength
			dy = (height[(x, (y + 1) % 16)] - height[(x, (y - 1) % 16)]) / 2 * strength
			nx, ny, nz = unit((-dx, -dy, 1.0))
			out[(x, y)] = nx * lx + ny * ly + nz * lz - flat
	return out


def shade(rgb, s, lit, shadow):
	"""Moves a colour toward `lit` for positive light s and toward `shadow` for negative."""
	return mix(rgb, lit, min(1.0, s * 2.2)) if s > 0 else mix(rgb, shadow, min(1.0, -s * 1.6))


# ---------------------------------------------------------------- starfall iron nuggets

# Ore clusters, drawn by hand ('#' is metal), at irregular anchors so a wall of ore does not read as a grid. None
# touches the tile's edge.
CLUSTERS = [
	((5, 6), [".##..",
			  "####.",
			  "#####",
			  ".###.",
			  "..#.."]),
	((1, 1), ["##.",
			  "###",
			  ".##"]),
	((11, 11), [".##",
				"###",
				"##."]),
	((12, 2), ["##",
			   "##",
			   ".#"]),
	((1, 12), ["#.",
			   "##",
			   ".#"]),
]
STONE_GLITTER = ((14, 7), (8, 13), (8, 2))   # faint specks in the rock around the nuggets
GLITTER = (236, 230, 255)


def cluster_cells(clusters, mirror=False):
	cells = []
	for (ax, ay), rows in clusters:
		group = set()
		for dy, row in enumerate(rows):
			for dx, ch in enumerate(row):
				if ch == "#":
					x = ax + dx
					group.add((15 - x if mirror else x, ay + dy))
		cells.append(group)
	return cells


def nuggets(px, rnd, tones, shadow, mirror=False):
	"""Metal nuggets shaded as little domes lit from the upper left (tones: dark, body, light, highlight), each casting a
	soft shadow on the rock to its lower right; the largest pixel of light in each is a glint, and faint glitter
	specks sit in the rock between them."""
	dark, body, light, high = tones
	groups = cluster_cells(CLUSTERS, mirror)
	metal = set().union(*groups)
	for (x, y) in sorted(metal):
		for (sx, sy) in ((x + 1, y), (x, y + 1), (x + 1, y + 1)):
			if (sx, sy) not in metal:
				toward(px, sx, sy, shadow, 0.38)
	for group in groups:
		for (x, y) in sorted(group):
			up_left = ((x - 1, y) not in group) + ((x, y - 1) not in group)
			down_right = ((x + 1, y) not in group) + ((x, y + 1) not in group)
			if up_left and not down_right:
				c = light
			elif down_right and not up_left:
				c = dark
			elif up_left and down_right:
				c = body if up_left >= down_right else mix(body, dark, 0.5)
			else:
				c = body
			put(px, x, y, noisy(c, rnd, 4))
		# The glint: the top-left-most lit pixel of a nugget of four or more.
		if len(group) >= 4:
			gx, gy = min(group, key=lambda c: (c[0] + c[1], c[1]))
			put(px, gx + (1 if (gx + 1, gy) in group else 0), gy, high)
	for (x, y) in STONE_GLITTER:
		toward(px, 15 - x if mirror else x, y, GLITTER, 0.45)
	# One bright point in the biggest nugget: the glitter of starfall iron.
	big = max(groups, key=len)
	cx = sorted(big)[len(big) // 2]
	put(px, cx[0], cx[1], GLITTER)


STARFALL_TONES = ((58, 50, 72), (100, 90, 118), (140, 132, 150), (192, 184, 210))   # light is the hilt tint 8C8496


def starfall_ore():
	img, px, rnd = stone(7101, (125, 125, 126), 8)
	nuggets(px, rnd, STARFALL_TONES, (52, 50, 58))
	return img


def deepslate_starfall_ore():
	img, px, rnd = deepslate(7111)
	nuggets(px, rnd, ((70, 62, 86), (118, 108, 138), (156, 148, 170), (204, 196, 222)), (24, 22, 30), mirror=True)
	return img


# ---------------------------------------------------------------- the meteorite's shell

# Thumbprint dents (regmaglypts) in the fusion crust: (centre x, centre y, radius), on the torus.
DENTS = ((3.5, 3.0, 3.3), (11.0, 2.5, 2.7), (7.5, 9.0, 3.5), (14.2, 9.8, 2.5), (2.0, 12.8, 2.8), (10.8, 14.6, 2.4))


def starfall_rock():
	"""Charred fusion crust: near-black with a violet cast, pressed into shallow thumbprint dents that the light picks
	out (their far walls lit, their near walls in shade, the ridges between them bright); two short cracks with ash
	in them; a few specks of violet metal showing through."""
	seed = 7121
	rnd = random.Random(seed)
	height = {}
	for y in range(16):
		for x in range(16):
			h = 0.0
			for (cx, cy, r) in DENTS:
				s2 = (torus_delta(x + 0.5, cx) ** 2 + torus_delta(y + 0.5, cy) ** 2) / (r * r)
				if s2 < 1:
					h -= 2.2 * (1 - s2) ** 2
			height[(x, y)] = h + (value2(seed + 5, x, y, 2.5) - 0.5) * 0.35
	light = relief(height)
	img = new()
	px = img.load()
	for y in range(16):
		for x in range(16):
			m = 0.6 * value2(seed, x, y, 6) + 0.4 * value2(seed + 3, x, y, 3)
			c = mix((38, 34, 42), (70, 64, 76), m)
			c = shade(c, light[(x, y)], (146, 138, 152), (12, 10, 14))
			put(px, x, y, noisy(c, rnd, 3))
	# Two short cracks, ash caught along their upper lips.
	for (x, y), steps in (((1, 7), ((1, 0), (1, 1), (1, 0), (1, 0), (1, -1))), ((12, 5), ((0, 1), (-1, 1), (0, 1), (1, 1)))):
		for dx, dy in steps:
			put(px, x, y, (14, 12, 16))
			toward(px, x, y - 1, (132, 126, 128), 0.32)
			x, y = x + dx, y + dy
	for _ in range(5):
		toward(px, rnd.randrange(16), rnd.randrange(16), (140, 134, 138), 0.3)
	# Metal showing through the crust, and two glints.
	for (x, y) in ((6, 1), (13, 11), (4, 9), (9, 14)):
		put(px, x, y, (112, 102, 134))
	put(px, 6, 0, (198, 188, 224))
	put(px, 13, 12, (164, 154, 194))
	return img


# ---------------------------------------------------------------- clan iron ore (Tyrvann basalt)

BLUED = (62, 80, 128)          # tempered blue steel
BLUED_LIGHT = (106, 128, 178)
BLUED_DARK = (26, 30, 44)
BLUED_FLECK = (168, 186, 228)
TEMPER = (108, 86, 130)        # the purple of the temper colours, here and there


def basalt_side(seed):
	"""Basalt's side: vertical streaks, darker joints, a little grain."""
	rnd = random.Random(seed)
	img = new()
	px = img.load()
	shift = [rnd.uniform(-10, 10) for _ in range(16)]
	for x in range(16):
		for y in range(16):
			streak = value2(seed, x * 3, y * 0.6, 3)
			c = mix((60, 61, 67), (104, 105, 112), 0.55 * streak + 0.45 * value2(seed + 1, x, y, 4))
			put(px, x, y, noisy(tuple(v + shift[x] for v in c), rnd, 4))
	for x in (3, 11):
		for y in range(16):
			if value2(seed + x, x, y, 3) > 0.25:
				toward(px, x, y, (30, 30, 36), 0.45)
	return img, px, rnd


def seam(px, rnd, points, widths):
	"""A blued metal seam along the polyline `points` (wrapping), `widths` pixels wide at each point in turn: a dark
	border, a blued core lit on its upper-left edge, temper colour here and there and a few bright flecks."""
	core = []
	for k, ((x0, y0), (x1, y1)) in enumerate(zip(points, points[1:])):
		n = max(abs(x1 - x0), abs(y1 - y0))
		for i in range(n + (1 if k == len(points) - 2 else 0)):
			x, y = round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n)
			for w in range(widths[k]):
				core.append((x + w, y))
	have = {(x % 16, y % 16) for x, y in core}
	for (x, y) in core:
		for (bx, by) in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
			if (bx % 16, by % 16) not in have:
				toward(px, bx, by, BLUED_DARK, 0.5)
	for i, (x, y) in enumerate(core):
		c = noisy(BLUED, rnd, 6)
		if ((x - 1) % 16, y % 16) not in have or (x % 16, (y - 1) % 16) not in have and i % 2:
			c = mix(c, BLUED_LIGHT, 0.6)
		elif ((x + 1) % 16, y % 16) not in have and i % 3 == 0:
			c = mix(c, BLUED_DARK, 0.35)
		if i % 7 == 3:
			c = mix(c, TEMPER, 0.55)
		put(px, x, y, c)
	for (x, y) in core[2::9]:
		put(px, x, y, BLUED_FLECK)


def clan_iron_ore_side():
	img, px, rnd = basalt_side(7131)
	seam(px, rnd, [(1, 1), (2, 4), (5, 7), (6, 11), (5, 14)], (1, 2, 2, 1))
	seam(px, rnd, [(13, 2), (12, 5), (10, 9), (11, 12)], (1, 1, 2))
	seam(px, rnd, [(8, 2), (9, 3)], (1,))
	return img


def clan_iron_ore_top():
	"""The column's end: grey stone with a few polygon cracks (basalt's joints), crossed by a seam."""
	seed = 7141
	rnd = random.Random(seed)
	img = new()
	px = img.load()
	for y in range(16):
		for x in range(16):
			c = mix((70, 70, 77), (100, 100, 108), value2(seed, x, y, 5))
			put(px, x, y, noisy(c, rnd, 5))
	for points in ([(0, 5), (4, 4), (7, 6), (11, 5), (15, 6)], [(4, 4), (3, 9), (5, 13), (4, 15)], [(11, 5), (12, 10), (10, 15)],
			[(3, 9), (8, 10), (12, 10)]):
		for (x0, y0), (x1, y1) in zip(points, points[1:]):
			n = max(abs(x1 - x0), abs(y1 - y0))
			for i in range(n + 1):
				toward(px, round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n), (36, 36, 42), 0.55)
	seam(px, rnd, [(1, 12), (5, 11), (9, 13), (14, 12)], (1, 2, 1))
	seam(px, rnd, [(8, 1), (9, 3)], (1,))
	return img


# ---------------------------------------------------------------- items

def outline(img, rgb):
	"""A dark one-pixel outline around the opaque shape, like the mod's other item icons."""
	src = img.copy()
	sp = src.load()
	px = img.load()
	for y in range(16):
		for x in range(16):
			if sp[x, y][3]:
				continue
			for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
				nx, ny = x + dx, y + dy
				if 0 <= nx < 16 and 0 <= ny < 16 and sp[nx, ny][3]:
					px[x, y] = tuple(rgb) + (255,)
					break
	return img


def raw_starfall():
	"""A rough chunk of raw meteoric iron: two fused lumps and a knob with ragged edges, shaded from the upper left in
	five tones, pitted, with glitter where the light falls."""
	rnd = random.Random(7151)
	lumps = [(6.4, 9.2, 4.6), (10.6, 7.2, 3.9), (4.4, 5.0, 2.2)]
	tones = [(36, 30, 46), (60, 52, 74), (90, 80, 106), (124, 116, 138), (170, 162, 190)]
	lx, ly, lz = unit(LIGHT)
	img = new()
	px = img.load()
	for y in range(16):
		for x in range(16):
			cx, cy = x + 0.5, y + 0.5
			best = None
			for k, (bx, by, r) in enumerate(lumps):
				angle = math.atan2(cy - by, cx - bx)
				r = r * (1 + 0.13 * math.sin(3 * angle + k) + 0.08 * math.sin(5 * angle + 2 * k))
				d2 = (cx - bx) ** 2 + (cy - by) ** 2
				if d2 < r * r:
					h = math.sqrt(r * r - d2)
					if best is None or h > best[0]:
						best = (h, (cx - bx) / r, (cy - by) / r, h / r)
			if best is None:
				continue
			_, nx, ny, nz = best
			lam = nx * lx + ny * ly + nz * lz + (value2(7151, x, y, 3) - 0.5) * 0.3
			k = 0 if lam < 0.1 else 1 if lam < 0.36 else 2 if lam < 0.64 else 3 if lam < 0.88 else 4
			px[x, y] = noisy(tones[k], rnd, 3) + (255,)
	# No lone pixel sticks out of the outline.
	for _ in range(2):
		for (x, y) in [(x, y) for y in range(16) for x in range(16)]:
			if px[x, y][3] and sum(1 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))
					if 0 <= x + dx < 16 and 0 <= y + dy < 16 and px[x + dx, y + dy][3]) <= 1:
				px[x, y] = (0, 0, 0, 0)
	# Pits and the crevice where the lumps meet.
	for (x, y) in ((9, 9), (9, 10), (8, 11), (5, 7), (12, 9), (6, 12)):
		if px[x, y][3]:
			px[x, y] = tones[0] + (255,)
	for (x, y) in ((4, 8), (9, 5), (3, 4)):
		if px[x, y][3]:
			px[x, y] = GLITTER + (255,)
	return outline(img, (26, 22, 32))


def ingot(palette, seed, glitter=(), sheen=False):
	"""An ingot lying diagonally across the icon (the long axis rising to the right, two pixels across for each one up),
	a frustum seen from above: top face light, the long side mid, the left end dark; bevelled edges, an outline."""
	dark, mid, light, high, edge = palette
	length, width, height, taper = 8.6, 5.0, 2.4, 0.7
	ox, oy = 1.2, 11.0

	def screen(x, y, z):
		return (ox + x + z, oy - 0.5 * x + 0.5 * z - y)

	b = [screen(0, 0, 0), screen(length, 0, 0), screen(length, 0, width), screen(0, 0, width)]
	t = [screen(taper, height, taper), screen(length - taper, height, taper), screen(length - taper, height, width - taper),
		 screen(taper, height, width - taper)]
	faces = [("end", [b[0], b[3], t[3], t[0]]), ("front", [b[3], b[2], t[2], t[3]]), ("top", t)]

	def inside(poly, x, y):
		sign = 0
		for i in range(len(poly)):
			x1, y1 = poly[i]
			x2, y2 = poly[(i + 1) % len(poly)]
			c = (x2 - x1) * (y - y1) - (y2 - y1) * (x - x1)
			if c:
				s = 1 if c > 0 else -1
				if sign == 0:
					sign = s
				elif s != sign:
					return False
		return True

	rnd = random.Random(seed)
	img = new()
	px = img.load()
	label = {}
	for y in range(16):
		for x in range(16):
			votes = {}
			for sy in range(4):
				for sx in range(4):
					fx, fy = x + (sx + 0.5) / 4, y + (sy + 0.5) / 4
					for name, poly in faces:
						if inside(poly, fx, fy):
							votes[name] = votes.get(name, 0) + 1
							break
			if sum(votes.values()) >= 7:
				name = max(sorted(votes), key=lambda n: votes[n])
				label[(x, y)] = name
				px[x, y] = noisy({"top": light, "front": mid, "end": dark}[name], rnd, 2) + (255,)
	for (x, y), name in label.items():
		below, left, above = label.get((x, y + 1)), label.get((x - 1, y)), label.get((x, y - 1))
		if name == "top" and below == "front":
			px[x, y] = high + (255,)                          # the top's near edge catches the light
		elif name == "top" and (above is None or left is None):
			px[x, y] = mix(light, high, 0.4) + (255,)
		elif name == "front" and below is None:
			px[x, y] = mix(mid, dark, 0.5) + (255,)           # the foot in shade
		elif name == "end" and left is None:
			px[x, y] = mix(dark, mid, 0.35) + (255,)
	if sheen:
		for (x, y), name in sorted(label.items()):
			if name == "top" and (x - 2 * y) in (-6, -5):
				px[x, y] = high + (255,)
			elif name == "front" and (x - 2 * y) in (-11,):
				px[x, y] = mix(mid, high, 0.6) + (255,)
	for (x, y) in glitter:
		if (x, y) in label:
			px[x, y] = edge + (255,)
	return outline(img, (28, 24, 34))


def starfall_ingot():
	# The hilt tint 8C8496 is the long side's colour.
	return ingot(((84, 76, 98), (140, 132, 150), (172, 164, 186), (206, 200, 220), GLITTER), 7161,
				 glitter=((7, 7), (11, 5), (5, 9)))


def hymnsteel_ingot():
	# The hilt tint CCC2E8 is the long side's colour; polished, so a sheen runs across it.
	return ingot(((150, 138, 186), (204, 194, 232), (230, 224, 246), (252, 250, 255), (255, 255, 255)), 7171, sheen=True)


TEXTURES = {
	"block/starfall_ore": starfall_ore,
	"block/deepslate_starfall_ore": deepslate_starfall_ore,
	"block/starfall_rock": starfall_rock,
	"block/clan_iron_ore": clan_iron_ore_side,
	"block/clan_iron_ore_top": clan_iron_ore_top,
	"item/raw_starfall": raw_starfall,
	"item/starfall_ingot": starfall_ingot,
	"item/hymnsteel_ingot": hymnsteel_ingot,
}


# ---------------------------------------------------------------- asset JSON

def asset_json(out):
	for name in ("starfall_ore", "deepslate_starfall_ore"):
		out.write_json(f"{ASSETS}/models/block/{name}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{name}"}})
		out.write_json(f"{ASSETS}/blockstates/{name}.json", {"variants": {"": {"model": f"{NS}:block/{name}"}}})
	# The shell turns at random about both axes (as vanilla's netherrack does), so a meteorite's faces do not repeat.
	out.write_json(f"{ASSETS}/models/block/starfall_rock.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/starfall_rock"}})
	turns = []
	for y in (0, 90, 180, 270):
		for x in (0, 90, 180, 270):
			variant = {"model": f"{NS}:block/starfall_rock"}
			if x:
				variant["x"] = x
			if y:
				variant["y"] = y
			turns.append(variant)
	out.write_json(f"{ASSETS}/blockstates/starfall_rock.json", {"variants": {"": turns}})
	# Clan iron ore stands like the ridges' basalt (axis y): its column end up and down.
	out.write_json(f"{ASSETS}/models/block/clan_iron_ore.json", {"parent": "minecraft:block/cube_column", "textures": {
		"end": f"{NS}:block/clan_iron_ore_top", "side": f"{NS}:block/clan_iron_ore"}})
	out.write_json(f"{ASSETS}/blockstates/clan_iron_ore.json", {"variants": {"": {"model": f"{NS}:block/clan_iron_ore"}}})
	for name in BLOCKS:
		out.write_json(f"{ASSETS}/items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{name}"}})
	for name in ITEMS:
		out.write_json(f"{ASSETS}/models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
		out.write_json(f"{ASSETS}/items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})


# ---------------------------------------------------------------- data: loot, tags, recipes, worldgen

SILK_TOUCH = {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
	{"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}


def ore_loot(block, drop):
	"""Vanilla's deepslate iron ore table: Silk Touch gives the block, else one drop with Fortune and explosion decay."""
	return {
		"type": "minecraft:block",
		"pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:alternatives", "children": [
			{"type": "minecraft:item", "name": f"{NS}:{block}", "conditions": [SILK_TOUCH]},
			{"type": "minecraft:item", "name": drop, "functions": [
				{"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
				{"function": "minecraft:explosion_decay"}]}]}]}],
		"random_sequence": f"{NS}:blocks/{block}",
	}


def self_loot(block):
	"""The block itself, unless an explosion destroys it."""
	return {
		"type": "minecraft:block",
		"pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": f"{NS}:{block}"}],
				   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
		"random_sequence": f"{NS}:blocks/{block}",
	}


LOOT = {
	"starfall_ore": ore_loot("starfall_ore", f"{NS}:raw_starfall"),
	"deepslate_starfall_ore": ore_loot("deepslate_starfall_ore", f"{NS}:raw_starfall"),
	"starfall_rock": self_loot("starfall_rock"),
	"clan_iron_ore": ore_loot("clan_iron_ore", f"{NS}:clan_iron"),
}

# Block tags (merged into the files the mod already has): every M7 block is a pickaxe block; the starfall ores need
# iron (4.1), clan iron ore diamond (4.2); the rock any pickaxe.
TAGS = {
	"minecraft/tags/block/mineable/pickaxe.json": [f"{NS}:{b}" for b in BLOCKS],
	"minecraft/tags/block/needs_iron_tool.json": [f"{NS}:starfall_ore", f"{NS}:deepslate_starfall_ore"],
	"minecraft/tags/block/needs_diamond_tool.json": [f"{NS}:clan_iron_ore"],
}

# Smelting and blasting (4.1): vanilla's iron numbers.
SMELT_TICKS = 200
BLAST_TICKS = 100
SMELT_XP = 0.7
SMELTABLE = ("raw_starfall", "starfall_ore", "deepslate_starfall_ore")


def cooking(kind, ingredient, ticks):
	return {
		"type": f"minecraft:{kind}",
		"category": "misc",
		"group": "starfall_ingot",
		"ingredient": f"{NS}:{ingredient}",
		"result": {"id": f"{NS}:starfall_ingot"},
		"experience": SMELT_XP,
		"cookingtime": ticks,
	}


def recipes():
	out = {}
	for ingredient in SMELTABLE:
		out[f"starfall_ingot_from_smelting_{ingredient}"] = cooking("smelting", ingredient, SMELT_TICKS)
		out[f"starfall_ingot_from_blasting_{ingredient}"] = cooking("blasting", ingredient, BLAST_TICKS)
	# Hymnsteel (4.3): one of each, two ingots, so a full hilt's seven units take four hymnstone shards.
	out["hymnsteel_ingot"] = {
		"type": "minecraft:crafting_shapeless",
		"category": "misc",
		"ingredients": [f"{NS}:hymnstone_shard", f"{NS}:clan_iron", f"{NS}:sunstone", f"{NS}:starfall_ingot"],
		"result": {"count": 2, "id": f"{NS}:hymnsteel_ingot"},
	}
	return out


# The deep veins (4.1): veins of 4 in deepslate (and in stone, as vanilla's ores list both), 2 a chunk, y -64 to -8.
DEEP_VEIN_SIZE = 4
DEEP_VEIN_COUNT = 2
DEEP_VEIN_MIN_Y = -64
DEEP_VEIN_MAX_Y = -8


def deep_veins():
	configured = {"type": "minecraft:ore", "config": {"discard_chance_on_air_exposure": 0.0, "size": DEEP_VEIN_SIZE, "targets": [
		{"state": {"Name": f"{NS}:starfall_ore"}, "target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"}},
		{"state": {"Name": f"{NS}:deepslate_starfall_ore"},
		 "target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"}},
	]}}
	placed = {"feature": f"{NS}:ore_starfall_deep", "placement": [
		{"type": "minecraft:count", "count": DEEP_VEIN_COUNT},
		{"type": "minecraft:in_square"},
		{"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "max_inclusive": {"absolute": DEEP_VEIN_MAX_Y},
													   "min_inclusive": {"absolute": DEEP_VEIN_MIN_Y}}},
		{"type": "minecraft:biome"},
	]}
	return configured, placed


def merge_tag(out, rel, ids):
	"""Adds ids to a tag file, keeping its other values and their order (read from the repository)."""
	src = os.path.join(REPO, rel)
	data = {"replace": False, "values": []}
	if os.path.exists(src):
		with open(src, encoding="utf-8") as f:
			data = json.load(f)
	have = {v["id"] if isinstance(v, dict) else v for v in data["values"]}
	for i in ids:
		if i not in have:
			data["values"].append(i)
	out.write_text(rel, json.dumps(data, indent=2) + "\n")


def data_json(out):
	# Each kind of file keeps the layout of its neighbours: loot tables and tags two spaces, recipes tabs, worldgen two
	# spaces with sorted keys (as gen_structures_m5.py writes the other ore features).
	for block, table in LOOT.items():
		out.write_json(f"{DATA}/{NS}/loot_table/blocks/{block}.json", table)
	for rel, ids in TAGS.items():
		merge_tag(out, f"{DATA}/{rel}", ids)
	for name, recipe in recipes().items():
		out.write_json(f"{DATA}/{NS}/recipe/{name}.json", recipe, indent="\t")
	configured, placed = deep_veins()
	out.write_json(f"{DATA}/{NS}/worldgen/configured_feature/ore_starfall_deep.json", configured, sort_keys=True)
	out.write_json(f"{DATA}/{NS}/worldgen/placed_feature/ore_starfall_deep.json", placed, sort_keys=True)


# ---------------------------------------------------------------- previews

def previews(textures, folder):
	"""Each texture eight times as large, and a 3 x 3 tiling of each block texture to check the seams."""
	os.makedirs(folder, exist_ok=True)
	for rel, img in textures.items():
		name = rel.split("/")[-1]
		img.resize((128, 128), Image.NEAREST).save(os.path.join(folder, name + "_x8.png"))
		if rel.startswith("block/"):
			tile = new(48, 48)
			for i in range(3):
				for j in range(3):
					tile.paste(img, (i * 16, j * 16))
			tile.resize((288, 288), Image.NEAREST).save(os.path.join(folder, name + "_tiled.png"))


def main():
	ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
	ap.add_argument("--out", metavar="DIR", help="write under DIR instead of the repository (a dry run)")
	ap.add_argument("--preview", metavar="DIR", help="also write enlarged previews into DIR")
	args = ap.parse_args()
	out = Out(args.out)
	textures = {rel: make() for rel, make in TEXTURES.items()}
	for rel, img in textures.items():
		out.write_png(f"{ASSETS}/textures/{rel}.png", img)
	asset_json(out)
	data_json(out)
	if args.preview:
		previews(textures, args.preview)
	print(f"gen_textures_ores_m7: wrote {len(out.written)} files under {os.path.relpath(out.root, REPO) if out.root != REPO else 'the repository'}")


if __name__ == "__main__":
	main()
