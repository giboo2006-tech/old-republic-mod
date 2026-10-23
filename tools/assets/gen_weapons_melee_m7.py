#!/usr/bin/env python3
"""The melee weapons' art for Milestone 7 (docs/MILESTONE_7.md 9.1 to 9.3; worker WM): 3D models in the hand for the
vibroblade, the vibrosword, the electrostaff, the vibro-axe and the force pike, the new flat icons, and the item
definitions that show the flat icon in the inventory, on the ground, in item frames and on shelves and the 3D model
everywhere else (both hands in first and third person, so troopers and other NPCs holding one show it too), the way the
rifle's do. Original designs: used-universe machinery (vibro emitters, generator housings, power cells, wrapped grips,
honed edges), no copied props.

Writes into src/main/resources/assets/oldrepublic/:
  models/item/<weapon>_in_hand.json   the 3D model: lib.py elements on the weapon's own 64x64 sheet ("#part")
  textures/item/<weapon>_model.png    that sheet
  items/<weapon>.json                 select on display_context: gui, ground, fixed, on_shelf -> the flat model,
                                      anything else -> <weapon>_in_hand
  models/item/<weapon>.json           the flat model (minecraft:item/handheld, as before)
  textures/item/{vibro_axe,force_pike}.png                      16x16 icons (new)
  textures/item/mod_{starfall,clan_iron}_edge.png               16x16 icons (new)
  models/item/mod_{starfall,clan_iron}_edge.json, items/mod_{starfall,clan_iron}_edge.json
The older weapons' flat icons (vibroblade.png, vibrosword.png, electrostaff.png) stay as their generators drew them.

Model conventions (tools/assets/hilts/lib.py): units are 1/16 block; a weapon's axis is x 8, z 8 and runs along +y from
the butt to the point. A blade's flats face +-x (+x is the side first person shows), its edge faces +z: the axe's bit
and the edge of a single-edged blade point +z, the axe's back spike -z. Every from/to stays within -16..32.

Holds (the display entries; the game draws model point p at T + R s (p - 8), R = rot_x(x) rot_y(y) rot_z(z)):
  * the vibroblade, the vibrosword and the vibro-axe are held exactly as the game holds a sword or an axe: the model is
    laid along the flat handheld sprite's diagonal (+y along the sprite from handle to tip, +x out of the sprite's face,
    +z toward the side an axe's head faces), its grip on the sprite's grip, and that frame then takes vanilla's
    handheld transform for each context (third person [0,-90,55] [0,4,0.5] 0.85, first person [0,-90,25]
    [1.13,3.2,1.13] 0.68). One number per weapon scales it against the sprite.
  * the electrostaff stands upright at the side in third person, held by its middle as the force pike is held by its
    grip (below): the saberstaff's crosswise hold needs the two-handed stance only a lit saberstaff gets, and with the
    arm at rest it ran the staff through the body. In first person it is held like a sword by its middle.
  * the force pike stands upright in third person (a guard at rest): the game raises an arm that holds anything by 18
    degrees, so the shaft leans that much forward of the arm, gripped in the front of the fist so it never runs
    through the forearm; in first person it points up and away past the crosshair, its head in view.
  The left-hand entries copy the right-hand ones, as every model in the mod does (the game mirrors them).

Run from the repository root:
  python3 tools/assets/gen_weapons_melee_m7.py                  write the assets
  python3 tools/assets/gen_weapons_melee_m7.py --check          check the models and icons (bounds, sheets, element
                                                                counts, masks), write nothing
  python3 tools/assets/gen_weapons_melee_m7.py --preview DIR    render model views and the new icons into DIR (a
                                                                scratch folder), write nothing
Imports tools/assets/hilts/lib.py (and preview.py for --preview) without changing them.
"""
import argparse
import json
import math
import os
import random
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "hilts"))

import lib  # noqa: E402
from lib import Sheet, box, grey, octo, rot_x, rot_y, rot_z  # noqa: E402

ASSETS = lib.ASSETS
NS = lib.NS
WEAPONS = ("vibroblade", "vibrosword", "electrostaff", "vibro_axe", "force_pike")
# The contexts that keep the flat icon (as the rifle's item definition does); every other context shows the 3D model.
FLAT_CONTEXTS = ["gui", "ground", "fixed", "on_shelf"]
MAX_ELEMENTS = 64


def r(v, places=3):
	v = round(float(v), places)
	return 0.0 if v == 0 else v


# ------------------------------------------------------------------ geometry helpers

def plate(cy, cz, angle, hy, hz, x0, x1, paint, **kw):
	"""A box in the blade's plane (y, z), centred on (cy, cz): hy each way along the direction turned `angle` degrees
	from +y toward +z, hz each way across it, x from x0 to x1. An angle on an exact diagonal is nudged 0.1 degree
	(lib.nudged), so the game lights its faces one way."""
	el = box(x0, cy - hy, cz - hz, x1, cy + hy, cz + hz, paint, **kw)
	if abs(angle) < 1e-9:
		return [el]
	return lib.transform([el], rot_x(lib.nudged(angle)), (8.0, cy, cz))


def point(E, S, apex, x0, x1, paint, toward=(1.0, 0.0), **kw):
	"""A blade's point: one box whose corner is the point and whose two sides run back to E (the edge side's corner,
	(y, z)) and S (the spine side's). The corner is a right angle, so the point sits on the circle through E and S,
	`apex` degrees round from E (90: the middle; less leans it toward the edge side, more toward the spine), on the
	side of E and S that `toward` (y, z) points to (+y: a blade's point; -z: the axe's back spike). The box's far
	corner lies inside the blade behind E and S. Returns (elements, the point (y, z))."""
	E, S = np.array(E, dtype=float), np.array(S, dtype=float)
	c = (E + S) / 2
	radius = np.linalg.norm(E - S) / 2
	b = (E - S) / (2 * radius)
	m = np.array([b[1], -b[0]])
	if m @ np.array(toward, dtype=float) < 0:
		m = -m
	a = math.radians(apex)
	p = c + radius * (math.cos(a) * b + math.sin(a) * m)
	u = (p - S) / np.linalg.norm(p - S)
	angle = math.degrees(math.atan2(u[1], u[0]))
	hy = np.linalg.norm(p - S) / 2
	hz = np.linalg.norm(p - E) / 2
	return plate(c[0], c[1], angle, hy, hz, x0, x1, paint, **kw), (float(p[0]), float(p[1]))


def taper(lo, hi, depth, x0, x1, paint, inward=None, **kw):
	"""A long box whose outer face runs from lo to hi ((y, z) points on an outline) and which reaches depth inward,
	toward the point `inward` (default the blade's middle line, z 8): one side of a tapering blade or a flaring axe
	bit. Choose depth so its inner corners stay inside the outline (checked in the previews); its ends hide under the
	point or inside the body."""
	lo, hi = np.array(lo, dtype=float), np.array(hi, dtype=float)
	d = hi - lo
	length = np.linalg.norm(d)
	u = d / length
	n = np.array([-u[1], u[0]])
	mid = (lo + hi) / 2
	toward = np.array(inward, dtype=float) - mid if inward is not None else np.array([0.0, 8.0 - mid[1]])
	if n @ toward < 0:
		n = -n
	c = mid + n * depth / 2
	return plate(c[0], c[1], math.degrees(math.atan2(u[1], u[0])), length / 2, depth / 2, x0, x1, paint, **kw)


def mirror_u(elements, *dirs):
	"""Mirror the texture across the named faces (u reversed), so a painted bevel on a blade's flat lies on the edge
	side on both flats (an east face's u runs toward -z, a west face's toward +z)."""
	for e in lib.flatten([elements]):
		for d in dirs:
			if d in e.faces:
				u0, v0, u1, v1 = e.faces[d].uv
				e.faces[d].uv = [u1, v0, u0, v1]
	return elements


def flip_end(elements):
	"""The elements turned half round the model's centre (z axis): the lower end of a point-symmetric staff."""
	return lib.transform(elements, rot_z(180.0), (8.0, 8.0, 8.0))


# ------------------------------------------------------------------ the sheet painter

def _rows(rng, n, amount, smooth=0.55):
	"""A gently varying value per row or column: a smoothed random walk around 0 (as common.py's)."""
	out, v = [], 0.0
	for _ in range(n):
		v = v * smooth + rng.uniform(-amount, amount)
		out.append(v)
	return out


def _rgb(c):
	return grey(c) if isinstance(c, (int, float)) else tuple(c)


def _add(c, d):
	return tuple(v + d for v in c)


class Painter:
	"""A weapon's 64x64 sheet: named regions, each painted as it is made."""

	def __init__(self, seed):
		self.sh = Sheet(texture="#part", seed=seed)
		self.seed = seed

	def add(self, name, x, y, w, h, fn, mode="scale", flat=None, solid_px=None):
		self.sh.region(name, x, y, w, h, flat=flat, solid_px=solid_px, mode=mode)
		fn(self.sh, name, random.Random(self.seed * 31 + len(self.sh.regions)))
		return self

	def paint(self, name, mode=None):
		return self.sh.paint(name, mode)


def blade_steel(base, spread=7.0, pits=5, tone=(0, 2, 6)):
	"""Brushed blade steel: streaks along the blade (down the texture's columns), a few pits and scratches."""
	def fn(sh, name, rng):
		reg = sh.regions[name][0]
		cols = _rows(rng, reg.w, spread, smooth=0.35)
		for x, y, lx, ly in sh._pixels(reg):
			n = cols[lx] + rng.uniform(-1.6, 1.6)
			sh.px(x, y, [base + tone[i] + n for i in range(3)])
		for _ in range(pits):
			px, py = reg.x + rng.randrange(reg.w), reg.y + rng.randrange(reg.h - 1)
			sh.px(px, py, _add(sh.get(px, py), -46))
			sh.px(px, py + 1, _add(sh.get(px, py + 1), 12))
		for _ in range(max(1, pits // 2)):
			px, py = reg.x + rng.randrange(reg.w), reg.y + rng.randrange(max(1, reg.h - 4))
			for k in range(rng.randint(2, 4)):
				if py + k < reg.y + reg.h:
					sh.px(px, py + k, _add(sh.get(px, py + k), 22))
	return fn


def blade_flat(base, bevel=3, tone=(0, 2, 6), pits=4, fuller=None):
	"""A blade's flat, for "fit" mode: brushed steel along the blade, the honed bevel in the first `bevel` columns
	(the edge side: mirror_u the west face) above a darker grind line; or, with fuller=(column, width), a dark groove
	down the middle with a lit lower lip instead (a double-edged blade)."""
	def fn(sh, name, rng):
		reg = sh.regions[name][0]
		cols = _rows(rng, reg.w, 5.0, smooth=0.35)
		for x, y, lx, ly in sh._pixels(reg):
			n = cols[lx] + rng.uniform(-1.5, 1.5)
			v = base + n
			if fuller is not None:
				c0, cw = fuller
				if c0 <= lx < c0 + cw:
					v = base - 34 + n * 0.5
				elif lx == c0 + cw:
					v = base + 18 + n * 0.5
			elif lx < bevel:
				v = base + 22 + (bevel - lx) * 5 + n * 0.5
			elif lx == bevel:
				v = base - 20 + n * 0.5
			sh.px(x, y, [v + tone[i] for i in range(3)])
		for _ in range(pits):
			px, py = reg.x + bevel + 1 + rng.randrange(max(1, reg.w - bevel - 1)), reg.y + rng.randrange(reg.h - 1)
			sh.px(px, py, _add(sh.get(px, py), -40))
			sh.px(px, py + 1, _add(sh.get(px, py + 1), 10))
	return fn


def honed(base, tone=(0, 3, 7)):
	"""A honed edge: bright, finer streaks, the outer columns brightest."""
	def fn(sh, name, rng):
		reg = sh.regions[name][0]
		for x, y, lx, ly in sh._pixels(reg):
			edge = 10 if lx in (0, reg.w - 1) else 0
			v = base + edge + rng.uniform(-2.5, 2.5)
			sh.px(x, y, [v + tone[i] for i in range(3)])
	return fn


def dark_metal(base, rows_amount=3.0, seams=0, tone=(0, 3, 9), wear=0):
	"""Gunmetal: column variation, optional panel seams every `seams` texels, a little wear."""
	def fn(sh, name, rng):
		reg = sh.regions[name][0]
		cols = _rows(rng, reg.w, rows_amount, smooth=0.3)
		for x, y, lx, ly in sh._pixels(reg):
			n = cols[lx] + rng.uniform(-1.5, 1.5)
			v = [base + tone[i] + n for i in range(3)]
			if seams and ly % seams == seams - 1:
				v = [c - 22 for c in v]
			elif seams and ly % seams == 0:
				v = [c + 9 for c in v]
			sh.px(x, y, v)
		if wear:
			sh.wear(reg, pits=wear, scratches=max(1, wear // 2), amount=34, seed=rng.randrange(1 << 30))
	return fn


def wrap(base, seam, lit, period=4):
	"""A spiral grip wrap: diagonal strips `period` texels apart, a dark seam and a lit upper edge (common.py's
	leather). In "wrap" mode the region's 8 columns go round an octagon side by side."""
	def fn(sh, name, rng):
		reg = sh.regions[name][0]
		for x, y, lx, ly in sh._pixels(reg):
			t = (lx + ly) % period
			c = seam if t == 0 else lit if t == 1 else base
			n = rng.uniform(-3, 3)
			sh.px(x, y, [v + n for v in c])
	return fn


def ridged(base, period=4, depth=26):
	"""Machined rings across a band: a lit ridge, its face and a dark groove, every `period` texels."""
	def fn(sh, name, rng):
		reg = sh.regions[name][0]
		b = _rgb(base)
		for x, y, lx, ly in sh._pixels(reg):
			t = ly % period
			d = 14 if t == 0 else -depth if t == period - 1 else 0
			n = rng.uniform(-1.5, 1.5)
			sh.px(x, y, [c + d + n for c in b])
	return fn


def solid(rgb, noise=2.0):
	def fn(sh, name, rng):
		reg = sh.regions[name][0]
		c = _rgb(rgb)
		for x, y, lx, ly in sh._pixels(reg):
			n = rng.uniform(-noise, noise)
			sh.px(x, y, [v + n for v in c])
	return fn


def glow(core, rim):
	"""A lit emitter or cell window: a hot core fading to its rim colour."""
	def fn(sh, name, rng):
		reg = sh.regions[name][0]
		cx, cy = (reg.w - 1) / 2, (reg.h - 1) / 2
		for x, y, lx, ly in sh._pixels(reg):
			d = max(abs(lx - cx) / max(cx, 0.5), abs(ly - cy) / max(cy, 0.5))
			t = min(1.0, d)
			sh.px(x, y, [core[i] + (rim[i] - core[i]) * t for i in range(3)])
	return fn


def panel(base, slot=(18, 19, 22), rivet=True, vents=3, tone=(0, 3, 9)):
	"""A housing's side panel: gunmetal, a darker border line, vertical vent slots with lit lower lips, corner rivets."""
	def fn(sh, name, rng):
		reg = sh.regions[name][0]
		dark_metal(base, tone=tone)(sh, name, rng)
		sh.outline(reg, [base + tone[i] - 30 for i in range(3)])
		if vents:
			span = reg.w - 4
			for i in range(vents):
				col = reg.x + 2 + int(round((i + 0.5) * span / vents))
				for row in range(reg.y + 3, reg.y + reg.h - 3):
					sh.px(col, row, slot)
				sh.px(col, reg.y + reg.h - 3, [base + tone[i] + 28 for i in range(3)])
		if rivet:
			for px, py in ((1, 1), (reg.w - 2, 1), (1, reg.h - 2), (reg.w - 2, reg.h - 2)):
				sh.px(reg.x + px, reg.y + py, [base + tone[i] + 46 for i in range(3)])
	return fn


def hazard(a, b, period=4, chips=10):
	"""Chipped hazard paint: diagonal stripes of a and b, a few chips showing bare metal."""
	def fn(sh, name, rng):
		reg = sh.regions[name][0]
		for x, y, lx, ly in sh._pixels(reg):
			c = a if ((lx + ly) // (period // 2)) % 2 == 0 else b
			n = rng.uniform(-4, 4)
			sh.px(x, y, [v + n for v in c])
		for _ in range(chips):
			px, py = reg.x + rng.randrange(reg.w), reg.y + rng.randrange(reg.h)
			sh.px(px, py, (122, 126, 132))
	return fn


# ------------------------------------------------------------------ palettes

STEEL = 176          # blade steel (grey level)
EDGE = 222
RAIL = 92
GUNMETAL = 72
EMITTER = ((226, 246, 255), (120, 186, 222))      # the vibro emitters' pale blue (the flat icons' "v")
CELL_BLUE = ((236, 250, 255), (90, 160, 230))
CELL_AMBER = ((255, 236, 170), (226, 140, 50))
STUN = ((248, 252, 255), (150, 196, 255))
LEATHER = ((82, 54, 34), (44, 28, 18), (110, 76, 50))
DARK_WRAP = ((58, 52, 50), (30, 27, 26), (84, 76, 72))
BRASS = (186, 148, 78)
HAZARD = ((196, 150, 52), (40, 38, 36))


# ------------------------------------------------------------------ the new flat icons (16x16, the mod's mask style)

# Each icon is a mask of letters, a colour per letter and a seed for the per-pixel noise (gen_textures_equipment.py's
# GEAR_ICONS style: a dark outline, steel in three tones, accents). Letters in `plain` are drawn without noise. The two
# weapons lie on the diagonal as the sword and the staff do (handle lower left); the two edges are the keen edge's
# shape in their metals.
ICONS = {
	# a fan-shaped bit with three emitters near its honed edge, a dark socket with a hazard clamp and an amber cell, a
	# back spike, a gunmetal haft wrapped at the butt
	"vibro_axe": ([
		".......ooo......",
		"......olppo.....",
		".....olpppo.....",
		"....olpvppo.....",
		"...olpvppdo.....",
		"..olpvppddhoo...",
		"..olpppddhssco..",
		".olpppddhsesso..",
		"..ooooohsessso..",
		".......ossssxo..",
		"......obcssxxo..",
		".....obcooooo...",
		"....okmo........",
		"..ookmo.........",
		".oakmo..........",
		"oaaao...........",
	], {"o": (24, 26, 30), "b": (92, 96, 104), "c": (56, 60, 66), "k": (74, 66, 60), "m": (46, 42, 40), "a": (128, 134, 142),
		"s": (66, 70, 78), "h": (196, 150, 52), "e": (255, 196, 96), "d": (104, 112, 120), "p": (156, 164, 172),
		"l": (214, 222, 230), "v": (150, 200, 230), "x": (150, 158, 166)}, 7, "ogvel"),
	# a leaf blade, the stun module (its window and two contacts glow), a brass collar, two wrapped grips, a butt cap
	"force_pike": ([
		"............ooo.",
		"...........ollo.",
		"..........ollpo.",
		".........olppdo.",
		"........olpdoo..",
		"......ovsgssvo..",
		"......osgssoo...",
		".....oaaaao.....",
		".....obcoo......",
		"....okmo........",
		"...okmo.........",
		"..obco..........",
		".okmo...........",
		"okmo............",
		"bco.............",
		"wo..............",
	], {"o": (24, 26, 30), "b": (84, 88, 98), "c": (52, 56, 64), "k": (70, 64, 60), "m": (42, 38, 36), "a": (186, 148, 78),
		"s": (70, 74, 82), "g": (220, 244, 255), "v": (130, 196, 255), "p": (164, 172, 180), "l": (220, 228, 236),
		"d": (112, 120, 128), "w": (200, 206, 214)}, 11, "ogvel"),
	# the keen edge in starfall: lilac grey (the starfall ingot's colours) with two sparkles
	"mod_starfall_edge": ([
		"................",
		"................",
		"................",
		"................",
		"...........ooo..",
		".........oolgo..",
		".......oollgpo..",
		".....oollgpso...",
		"...oollgppdo....",
		"..olgpsppdo.....",
		"..oopppddo......",
		"...oaaoo........",
		"...oaao.........",
		"....oo..........",
		"................",
		"................",
	], {"o": (28, 24, 34), "p": (150, 142, 166), "l": (206, 200, 220), "d": (104, 96, 118), "g": (255, 255, 255),
		"s": (240, 236, 255), "a": (84, 76, 98)}, 21, "ogs"),
	# the keen edge in clan iron: dark iron, a red stripe down its spine (clan iron's colours)
	"mod_clan_iron_edge": ([
		"................",
		"................",
		"................",
		"................",
		"...........ooo..",
		".........oolgo..",
		".......oollgco..",
		".....oollgpcro..",
		"...oollgppcro...",
		"..olgpppcro.....",
		"..oopccrro......",
		"...oaaoo........",
		"...oaao.........",
		"....oo..........",
		"................",
		"................",
	], {"o": (20, 20, 24), "p": (88, 90, 96), "l": (132, 134, 140), "d": (56, 56, 62), "g": (196, 200, 208),
		"c": (140, 48, 40), "r": (100, 34, 30), "a": (44, 40, 40)}, 22, "og"),
}
# The edges are mods (flat, minecraft:item/generated); the weapons' flat models are handheld, as before.
MOD_ICONS = ("mod_starfall_edge", "mod_clan_iron_edge")


def mask_icon(mask, colours, seed, plain, noise=5.0):
	rng = random.Random(seed)
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	px = img.load()
	for y, row in enumerate(mask):
		for x, ch in enumerate(row):
			if ch == ".":
				continue
			c = colours[ch]
			if ch not in plain:
				c = tuple(max(0, min(255, int(round(v + rng.uniform(-noise, noise))))) for v in c)
			px[x, y] = tuple(c) + (255,)
	return img


def check_icon(id, mask, colours):
	probs = []
	if len(mask) != 16 or any(len(row) != 16 for row in mask):
		probs.append(f"{id}: the mask is not 16 rows of 16")
	missing = {ch for row in mask for ch in row if ch != "." and ch not in colours}
	if missing:
		probs.append(f"{id}: no colour for {sorted(missing)}")
	return probs



# ------------------------------------------------------------------ the weapons

class Weapon:
	def __init__(self, id, elements, painter, grip, display, notes):
		self.id = id
		self.elements = lib.flatten([elements])
		self.painter = painter
		self.grip = grip            # the model point the hand holds
		self.display = display
		self.notes = notes          # points of interest (for checks and previews)


def vibroblade():
	"""The heavy knife-sword (M2's vibroblade): a wrapped grip, a squat generator housing with a status light, a small
	guard, a straight single-edged blade with three emitters along its spine and a chisel point."""
	p = Painter(7101)
	p.add("flat", 0, 0, 8, 28, blade_flat(STEEL), mode="fit")
	p.add("blade", 8, 0, 8, 28, blade_steel(STEEL))
	p.add("edge", 16, 0, 4, 28, honed(EDGE))
	p.add("rail", 20, 0, 4, 28, dark_metal(RAIL, seams=10))
	p.add("wrap", 24, 0, 16, 20, wrap(*LEATHER), mode="wrap")
	p.add("pommel", 40, 0, 8, 8, dark_metal(GUNMETAL, wear=2))
	p.add("band", 40, 8, 8, 8, ridged(150))
	p.add("housing", 48, 0, 12, 8, panel(GUNMETAL, vents=3))
	p.add("metal", 48, 8, 12, 8, dark_metal(GUNMETAL + 6, wear=2))
	p.add("guard", 40, 16, 12, 8, dark_metal(104, wear=3))
	p.add("glow", 60, 0, 4, 4, glow(*EMITTER), mode="fit")
	p.add("light", 60, 4, 4, 4, glow(*CELL_AMBER), mode="fit")
	flat, blade, edge, rail, wrap_ = (p.paint(n) for n in ("flat", "blade", "edge", "rail", "wrap"))
	pommel, band, housing, metal, guard = (p.paint(n) for n in ("pommel", "band", "housing", "metal", "guard"))
	emit, light = p.paint("glow"), p.paint("light")
	rim = {d: edge for d in ("up", "down", "north", "south")}

	els = []
	els += lib.lathe([(-1.1, -0.35, 0.82, pommel), (-0.35, 4.35, 0.64, wrap_), (4.35, 4.75, 0.8, band)], top=False, name="handle")
	els.append(box(7.18, 4.75, 6.55, 8.82, 6.15, 9.2, metal, paints={"east": housing, "west": housing}, name="housing"))
	els.append(box(8.77, 5.2, 8.25, 8.92, 5.7, 8.75, light, skip=("west",), light=11, name="status"))
	els.append(box(7.36, 6.15, 5.95, 8.64, 6.6, 10.05, guard, name="guard"))
	# the blade: body, a thinner honed edge, a thicker spine rail carrying the emitters, a chisel point
	els += mirror_u([box(7.72, 6.6, 7.05, 8.28, 13.2, 8.85, flat, paints={"north": rail, "south": edge}, skip=("down", "up"),
			name="blade")], "west")
	els.append(box(7.84, 6.6, 8.8, 8.16, 13.0, 9.3, edge, skip=("down", "north"), name="edge"))
	els.append(box(7.6, 6.6, 6.7, 8.4, 13.0, 7.1, rail, skip=("down", "south"), name="spine"))
	for y in (7.5, 9.3, 11.1):
		els.append(box(7.55, y, 6.6, 8.45, y + 0.5, 6.95, emit, skip=("south",), light=10, name="emitter"))
	tip, at = point((13.0, 9.3), (13.0, 6.7), 118, 7.77, 8.23, blade, paints=dict(rim), name="point")
	els += tip
	return Weapon("vibroblade", els, p, grip=(8.0, 2.0, 8.0), display=None, notes={"point": at, "k": 1.1})


def vibrosword():
	"""The vibrosword: a brass pommel and guard, a leather grip, a generator housing whose cell glows through a window
	on both flats, a long double-edged blade with a fuller and three emitter studs, tapering to its point."""
	p = Painter(7102)
	p.add("core", 0, 0, 8, 48, blade_flat(STEEL + 4, fuller=(3, 2)), mode="fit")
	p.add("blade", 8, 0, 8, 24, blade_steel(STEEL + 4))
	p.add("edge", 16, 0, 4, 48, honed(EDGE))
	p.add("wrap", 20, 0, 16, 24, wrap(*LEATHER), mode="wrap")
	p.add("brass", 36, 0, 8, 8, dark_metal(BRASS[0] - 6, tone=(6, -30, -102), wear=2))
	p.add("brass_band", 36, 8, 8, 8, ridged(BRASS))
	p.add("steel", 44, 0, 8, 8, dark_metal(150, wear=1))
	p.add("housing", 44, 8, 12, 8, panel(GUNMETAL, vents=2))
	p.add("metal", 36, 16, 12, 8, dark_metal(GUNMETAL + 4, wear=2))
	p.add("guard", 20, 24, 16, 8, dark_metal(BRASS[0] - 10, tone=(10, -28, -100), wear=4))
	p.add("glow", 56, 0, 4, 4, glow(*EMITTER), mode="fit")
	p.add("cell", 60, 0, 4, 4, glow(*CELL_BLUE), mode="fit")
	core, blade, edge, wrap_ = (p.paint(n) for n in ("core", "blade", "edge", "wrap"))
	brass, brass_band, steel, housing, metal, guard = (p.paint(n) for n in ("brass", "brass_band", "steel", "housing", "metal", "guard"))
	emit, cell = p.paint("glow"), p.paint("cell")
	rim = {d: edge for d in ("up", "down", "north", "south")}

	els = []
	els += lib.lathe([(-2.0, -1.5, 0.5, steel), (-1.5, -0.5, 0.95, brass), (-0.5, 5.3, 0.7, wrap_), (5.3, 5.7, 0.9, brass_band)],
			top=False, name="handle")
	els.append(box(7.25, 5.7, 5.1, 8.75, 6.45, 10.9, guard, name="guard"))
	els.append(box(7.15, 5.55, 4.55, 8.85, 6.6, 5.15, brass, skip=("south",), name="guard_end"))
	els.append(box(7.15, 5.55, 10.85, 8.85, 6.6, 11.45, brass, skip=("north",), name="guard_end"))
	els.append(box(7.05, 6.45, 6.55, 8.95, 8.25, 9.45, metal, paints={"east": housing, "west": housing}, name="housing"))
	els.append(box(6.98, 6.95, 7.35, 9.02, 7.75, 8.65, cell, faces=("east", "west"), light=12, name="cell"))
	# the blade: the fullered core, two honed edges, two tapers and the point
	els.append(box(7.7, 8.25, 7.1, 8.3, 19.0, 8.9, core, paints={"north": edge, "south": edge}, skip=("down", "up"), name="blade"))
	els.append(box(7.84, 8.25, 6.7, 8.16, 18.0, 7.15, edge, skip=("down", "south"), name="edge"))
	els.append(box(7.84, 8.25, 8.85, 8.16, 18.0, 9.3, edge, skip=("down", "north"), name="edge"))
	els += taper((18.0, 6.7), (21.0, 7.4), 1.2, 7.77, 8.23, blade, paints=dict(rim), name="taper")
	els += taper((18.0, 9.3), (21.0, 8.6), 1.2, 7.77, 8.23, blade, paints=dict(rim), name="taper")
	tip, at = point((21.0, 8.6), (21.0, 7.4), 90, 7.82, 8.18, blade, paints=dict(rim), name="point")
	els += tip
	for y in (9.0, 10.3, 11.6):
		els.append(box(7.64, y, 7.82, 8.36, y + 0.45, 8.18, emit, faces=("east", "west", "up", "down"), light=10, name="emitter"))
	return Weapon("vibrosword", els, p, grip=(8.0, 2.4, 8.0), display=None, notes={"point": at, "k": 0.88})


def electrostaff():
	"""The electrostaff: a dark shaft with a wrapped grip at its middle, and at each end a collar, a vented head, a
	glowing core caged by four prongs. Point-symmetric about the model's centre, where the hand holds it."""
	p = Painter(7103)
	p.add("shaft", 0, 0, 8, 48, dark_metal(84, tone=(0, 3, 9), seams=12, wear=4))
	p.add("wrap", 8, 0, 16, 32, wrap(*DARK_WRAP), mode="wrap")
	p.add("band", 24, 0, 8, 8, ridged(140))
	p.add("collar", 24, 8, 8, 8, ridged(104, period=2, depth=20))
	p.add("head", 32, 0, 12, 8, panel(GUNMETAL + 6, vents=0))
	p.add("metal", 32, 8, 12, 8, dark_metal(GUNMETAL + 10, wear=2))
	p.add("prong", 44, 0, 4, 12, honed(184))
	p.add("glow", 48, 0, 4, 4, glow(*STUN), mode="fit")
	p.add("core", 52, 0, 4, 4, glow((255, 255, 255), (170, 210, 255)), mode="fit")
	p.add("cap", 56, 0, 8, 8, dark_metal(150, wear=1))
	shaft, wrap_, band, collar, head, metal = (p.paint(n) for n in ("shaft", "wrap", "band", "collar", "head", "metal"))
	prong, glw, core, cap = (p.paint(n) for n in ("prong", "glow", "core", "cap"))

	els = []
	els += octo(-6.3, 22.3, 0.5, shaft, name="shaft")     # its ends hide in the heads, under the cores
	els += lib.lathe([(3.2, 3.6, 0.78, band), (3.6, 12.4, 0.66, wrap_), (12.4, 12.8, 0.78, band)], name="grip")
	upper = []
	upper += octo(19.2, 20.4, 0.8, collar, bottom=True, name="collar")
	upper.append(box(7.22, 20.4, 7.22, 8.78, 22.4, 8.78, metal, paints={d: head for d in ("east", "west", "north", "south")},
			skip=("down",), name="head"))
	upper.append(box(7.15, 20.9, 7.62, 8.85, 21.9, 8.38, glw, faces=("east", "west"), light=13, name="vent"))
	upper.append(box(7.62, 20.9, 7.15, 8.38, 21.9, 8.85, glw, faces=("north", "south"), light=13, name="vent"))
	# the core faces the four sides, the prongs stand on the diagonals, so every side shows the core between two prongs
	upper.append(box(7.58, 22.4, 7.58, 8.42, 24.0, 8.42, core, skip=("down",), light=15, shade=False, name="core"))
	one = lib.transform([box(8.62, 22.2, 7.86, 8.9, 24.7, 8.14, prong, skip=("down",), name="prong")], rot_z(-10.0), (8.76, 22.2, 8.0))
	upper += lib.around(one, 4, start=45.0)
	upper.append(box(7.78, 24.0, 7.78, 8.22, 24.45, 8.22, cap, skip=("down",), name="cap"))
	els += upper + flip_end(upper)
	return Weapon("electrostaff", els, p, grip=(8.0, 8.0, 8.0), display=None, notes={"head": (8.0, 23.2, 8.0), "k": 0.8})


def vibro_axe():
	"""The vibro-axe: a long haft wrapped at the butt, a heavy socket with a hazard-striped clamp and a power cell
	glowing through it, a flared bit with a wide honed edge and three emitters, and a back spike."""
	p = Painter(7104)
	p.add("bit", 0, 0, 12, 32, blade_flat(STEEL - 6, bevel=4), mode="fit")
	p.add("blade", 12, 0, 8, 24, blade_steel(STEEL - 6))
	p.add("edge", 20, 0, 4, 32, honed(EDGE - 4))
	p.add("rail", 24, 0, 4, 32, dark_metal(RAIL - 6, seams=8))
	p.add("haft", 28, 0, 8, 48, dark_metal(70, tone=(4, 2, 0), wear=5))
	p.add("wrap", 36, 0, 16, 28, wrap(*DARK_WRAP), mode="wrap")
	p.add("socket", 52, 0, 12, 12, panel(GUNMETAL - 4, vents=2))
	p.add("metal", 52, 12, 12, 8, dark_metal(GUNMETAL, wear=3))
	p.add("hazard", 36, 28, 16, 4, hazard(*HAZARD))
	p.add("cell", 52, 20, 4, 4, glow(*CELL_AMBER), mode="fit")
	p.add("glow", 56, 20, 4, 4, glow(*EMITTER), mode="fit")
	p.add("band", 0, 32, 8, 8, ridged(132))
	p.add("cap", 8, 32, 8, 8, dark_metal(60, wear=2))
	bit, blade, edge, rail, haft, wrap_ = (p.paint(n) for n in ("bit", "blade", "edge", "rail", "haft", "wrap"))
	socket, metal, haz, cell, emit, band, cap = (p.paint(n) for n in ("socket", "metal", "hazard", "cell", "glow", "band", "cap"))
	rim = {d: edge for d in ("up", "down", "north", "south")}

	els = []
	els += lib.lathe([(-1.8, -1.0, 0.82, cap), (-1.0, 6.4, 0.7, wrap_), (6.4, 6.9, 0.82, band), (6.9, 18.8, 0.58, haft)], top=False,
			name="haft")
	els.append(box(7.1, 13.6, 6.6, 8.9, 18.8, 9.6, metal, paints={"east": socket, "west": socket}, name="socket"))
	els.append(box(7.04, 17.4, 6.54, 8.96, 18.1, 9.66, haz, skip=("up", "down"), name="clamp"))
	els.append(box(6.96, 14.4, 7.3, 9.04, 16.2, 8.5, cell, faces=("east", "west"), light=12, name="cell"))
	els.append(box(7.25, 18.8, 6.85, 8.75, 19.35, 9.35, cap, name="socket_cap"))
	els.append(box(7.62, 13.9, 9.5, 8.38, 18.5, 9.95, rail, skip=("north",), name="rib"))
	# the bit: a body, two horns flaring to the edge, the wide honed edge, three emitters near it
	els += mirror_u([box(7.7, 14.0, 9.9, 8.3, 18.4, 12.65, bit, paints={"up": blade, "down": blade}, skip=("north",), name="bit")], "west")
	els += taper((14.0, 9.9), (12.4, 12.6), 1.5, 7.75, 8.25, blade, inward=(16.2, 11.3), paints=dict(rim), name="horn")
	els += taper((18.4, 9.9), (20.0, 12.6), 1.5, 7.75, 8.25, blade, inward=(16.2, 11.3), paints=dict(rim), name="horn")
	els.append(box(7.8, 12.0, 12.6, 8.2, 20.4, 13.4, edge, skip=("north",), name="edge"))
	for y in (14.4, 15.95, 17.5):
		els.append(box(7.64, y, 11.75, 8.36, y + 0.5, 12.15, emit, faces=("east", "west", "up", "down"), light=10, name="emitter"))
	# the back spike
	els.append(box(7.75, 15.45, 5.4, 8.25, 17.05, 6.65, blade, paints={"north": edge}, skip=("south",), name="spike"))
	spike, at = point((15.45, 5.4), (17.05, 5.4), 90, 7.78, 8.22, blade, toward=(0.0, -1.0), paints=dict(rim), name="spike_point")
	els += spike
	return Weapon("vibro_axe", els, p, grip=(8.0, 2.7, 8.0), display=None, notes={"spike": at, "k": 0.92})


def force_pike():
	"""The force pike: a long shaft with a butt cap and two wrapped grips, a brass collar under the stun module (its
	windows glow), two stun contacts and a leaf blade with honed edges."""
	p = Painter(7105)
	p.add("shaft", 0, 0, 8, 64, dark_metal(64, tone=(2, 4, 10), seams=16, wear=6))
	p.add("wrap", 8, 0, 16, 24, wrap(*DARK_WRAP), mode="wrap")
	p.add("band", 24, 0, 8, 8, ridged(138))
	p.add("brass", 24, 8, 8, 8, ridged(BRASS, period=2, depth=24))
	p.add("module", 32, 0, 12, 12, panel(GUNMETAL + 2, vents=2))
	p.add("metal", 32, 12, 12, 8, dark_metal(GUNMETAL + 6, wear=2))
	p.add("blade", 44, 0, 8, 24, blade_steel(STEEL + 8))
	p.add("core", 52, 0, 8, 24, blade_flat(STEEL + 8, fuller=(3, 2)), mode="fit")
	p.add("edge", 60, 0, 4, 24, honed(EDGE + 4))
	p.add("glow", 44, 24, 4, 4, glow(*STUN), mode="fit")
	p.add("cap", 8, 24, 8, 8, dark_metal(140, wear=2))
	shaft, wrap_, band, brass, module, metal = (p.paint(n) for n in ("shaft", "wrap", "band", "brass", "module", "metal"))
	blade, core, edge, glw, cap = (p.paint(n) for n in ("blade", "core", "edge", "glow", "cap"))
	rim = {d: edge for d in ("up", "down", "north", "south")}

	els = []
	els += octo(-14.0, -13.0, 0.7, cap, top=True, bottom=True, name="butt")
	els += octo(-13.0, 25.0, 0.46, shaft, name="shaft")
	els += lib.lathe([(-7.0, -6.5, 0.68, band), (-6.5, -1.0, 0.6, wrap_), (-1.0, -0.5, 0.68, band)], name="lower_grip")
	els += lib.lathe([(2.5, 3.0, 0.68, band), (3.0, 8.5, 0.6, wrap_), (8.5, 9.0, 0.68, band)], name="upper_grip")
	els += octo(21.4, 22.4, 0.74, brass, bottom=True, name="collar")
	els.append(box(7.22, 22.4, 7.22, 8.78, 25.2, 8.78, metal, paints={d: module for d in ("east", "west", "north", "south")},
			skip=("down",), name="module"))
	els.append(box(7.15, 23.0, 7.62, 8.85, 24.6, 8.38, glw, faces=("east", "west"), light=13, name="window"))
	els.append(box(7.62, 23.0, 7.15, 8.38, 24.6, 8.85, glw, faces=("north", "south"), light=13, name="window"))
	els += octo(25.2, 26.2, 0.45, band, top=True, name="neck")
	for z0, z1 in ((6.5, 6.9), (9.1, 9.5)):
		els.append(box(7.84, 25.4, z0, 8.16, 26.6, z1, glw, light=12, name="contact"))
	# the leaf blade: a fullered core, its narrow upper part, honed edges, two tapers and the point
	els.append(box(7.75, 26.2, 7.1, 8.25, 28.6, 8.9, core, paints={"north": edge, "south": edge}, skip=("down", "up"), name="blade"))
	els.append(box(7.79, 28.6, 7.6, 8.21, 30.2, 8.4, blade, paints={"north": edge, "south": edge}, skip=("down", "up"),
			name="blade_upper"))
	els.append(box(7.84, 26.4, 6.8, 8.16, 28.4, 7.15, edge, skip=("south",), name="edge"))
	els.append(box(7.84, 26.4, 8.85, 8.16, 28.4, 9.2, edge, skip=("north",), name="edge"))
	els += taper((28.4, 6.8), (30.8, 7.6), 0.9, 7.8, 8.2, blade, paints=dict(rim), name="taper")
	els += taper((28.4, 9.2), (30.8, 8.4), 0.9, 7.8, 8.2, blade, paints=dict(rim), name="taper")
	tip, at = point((30.8, 8.4), (30.8, 7.6), 90, 7.84, 8.16, blade, paints=dict(rim), name="point")
	els += tip
	return Weapon("force_pike", els, p, grip=(8.0, 5.75, 8.0), display=None, notes={"point": at, "head": (8.0, 28.5, 8.0)})


def build_all():
	out = []
	for make in (vibroblade, vibrosword, vibro_axe):
		w = make()
		w.display = sword_display(w, w.notes["k"])
		out.append(w)
	staff = electrostaff()
	staff.display = staff_display(staff)
	pike = force_pike()
	pike.display = pike_display(pike)
	return out[:2] + [staff, out[2], pike]


# ------------------------------------------------------------------ display

def matrix_xyz(rotation):
	return rot_x(rotation[0]) @ rot_y(rotation[1]) @ rot_z(rotation[2])


def euler_xyz(m):
	"""[x, y, z] degrees with rot_x(x) @ rot_y(y) @ rot_z(z) == m (Quaternionf.rotationXYZ), as build.py's."""
	sy = max(-1.0, min(1.0, m[0, 2]))
	y = math.asin(sy)
	if abs(math.cos(y)) > 1e-9:
		x = math.atan2(-m[1, 2], m[2, 2])
		z = math.atan2(-m[0, 1], m[0, 0])
	else:
		x = math.atan2(m[2, 1], m[1, 1])
		z = 0.0
	out = [math.degrees(v) for v in (x, y, z)]
	assert np.allclose(matrix_xyz(out), m, atol=1e-9)
	return out


def entry(R, s, pivot, at):
	"""The display entry turning by R and scaling by s that puts model point `pivot` at `at` (sixteenths of a block),
	solved against the rounded angles the game will read."""
	rotation = [r(v) for v in euler_xyz(R)]
	t = np.array(at, dtype=float) - s * (matrix_xyz(rotation) @ (np.array(pivot, dtype=float) - 8.0))
	return {"rotation": rotation, "translation": [r(v) for v in t], "scale": [r(s, 4)] * 3}


def apply_entry(e, p):
	"""Where a display entry puts model point p (sixteenths of a block)."""
	return np.array(e["translation"], dtype=float) + e["scale"][0] * (matrix_xyz(e["rotation"]) @ (np.array(p, dtype=float) - 8.0))


# vanilla's minecraft:item/handheld (swords, axes)
HANDHELD = {
	"third": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85] * 3},
	"first": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68] * 3},
}
# Where the hand holds a handheld sprite: its handle's middle, a little up the diagonal from the lower left corner.
SPRITE_GRIP = (3.0, 3.0, 8.0)
_S = 1.0 / math.sqrt(2.0)
# The model's frame laid on the sprite: model +y along the sprite's diagonal (handle to tip), +x out of the sprite's
# face (+z), +z toward the sprite's upper left (where an axe sprite's head faces).
ON_SPRITE = np.array([[0.0, _S, -_S], [0.0, _S, _S], [1.0, 0.0, 0.0]])
STAFF_THIRD_PERSON_SCALE = 0.62
HEAD = {"rotation": [0, 0, 0], "translation": [0, 10, 0], "scale": [0.8, 0.8, 0.8]}


def _sprite_grip(context):
	"""Where vanilla's handheld transform puts the sprite's grip in `context` (sixteenths of a block)."""
	v = HANDHELD[context]
	return np.array(v["translation"], dtype=float) + v["scale"][0] * (matrix_xyz(v["rotation"]) @ (np.array(SPRITE_GRIP) - 8.0))


def handheld(context, pivot, k, extra=None):
	"""The display entry that holds the model as vanilla holds a handheld sprite in `context` ("third" or "first"):
	the model laid on the sprite (ON_SPRITE, then `extra`, a turn in the model's own frame), its `pivot` on the sprite's
	grip, scaled k against the sprite."""
	v = HANDHELD[context]
	R = matrix_xyz(v["rotation"]) @ ON_SPRITE @ (extra if extra is not None else np.eye(3))
	return entry(R, v["scale"][0] * k, pivot, _sprite_grip(context))


def both_hands(third, first):
	return {"thirdperson_righthand": third, "thirdperson_lefthand": json.loads(json.dumps(third)),
			"firstperson_righthand": first, "firstperson_lefthand": json.loads(json.dumps(first)), "head": dict(HEAD)}


def sword_display(w, k, extra_third=None, extra_first=None):
	return both_hands(handheld("third", w.grip, k, extra_third), handheld("first", w.grip, k, extra_first))


def staff_display(w):
	"""Third person: upright at the side as the force pike stands (pike_display), its middle, where the grip is, in the
	front of the fist, so neither end runs through the body or the forearm. First person: held by its middle like a
	sword, the upper half where a sword's blade is."""
	lean = math.radians(PIKE_FROM_ARM)
	up = (math.sin(math.radians(PIKE_OUT)), math.sin(lean), math.cos(lean))
	third = entry(frame(up, (1.0, 0.0, 0.0)), STAFF_THIRD_PERSON_SCALE, w.grip, PIKE_FIST)
	return both_hands(third, handheld("first", w.grip, w.notes["k"]))


# The force pike's holds. Third person: upright at the guard's rest. The game raises an arm that holds anything by 18
# degrees, so the shaft leans PIKE_FROM_ARM degrees forward of the arm's line to stand upright, and PIKE_OUT degrees out
# from the body; its grip sits in the front of the fist (PIKE_FIST, display units: the arm is x -2..2, y -4..0 with
# its front at 0, z 0..12 up the arm), so the shaft leaves the fist's front by the fist's top and never runs through
# the forearm. First person: pointing up and away past the crosshair, its head in view to the right of it.
PIKE_SCALE = 0.72
PIKE_FROM_ARM = 18.0
PIKE_OUT = 4.0
PIKE_FIST = (0.0, -0.35, 1.6)
PIKE_FIRST_PERSON = {"scale": 0.78, "at": (1.6, -2.4, -3.2), "dir": (-0.16, 0.74, -1.0)}


def frame(y_axis, x_hint):
	"""The rotation taking model +y to y_axis and model +x as near x_hint as it can (a right-handed frame)."""
	y = np.array(y_axis, dtype=float)
	y /= np.linalg.norm(y)
	x = np.array(x_hint, dtype=float)
	x = x - (x @ y) * y
	x /= np.linalg.norm(x)
	z = np.cross(x, y)
	return np.column_stack([x, y, z])


def pike_display(w):
	lean = math.radians(PIKE_FROM_ARM)
	up = (math.sin(math.radians(PIKE_OUT)), math.sin(lean), math.cos(lean))
	third = entry(frame(up, (1.0, 0.0, 0.0)), PIKE_SCALE, w.grip, PIKE_FIST)
	f = PIKE_FIRST_PERSON
	first = entry(frame(f["dir"], (-0.6, 0.0, 0.8)), f["scale"], w.grip, f["at"])
	return both_hands(third, first)


# ------------------------------------------------------------------ output

def model_json(w):
	return {
		"textures": {"part": f"{NS}:item/{w.id}_model", "particle": f"{NS}:item/{w.id}"},
		"gui_light": "front",
		"elements": [e.json() for e in w.elements],
		"display": w.display,
	}


def item_definition(id):
	"""The weapon's item definition: the flat model in FLAT_CONTEXTS, the 3D model everywhere else (the rifle's)."""
	return {"model": {
		"type": "minecraft:select", "property": "minecraft:display_context",
		"cases": [{"when": list(FLAT_CONTEXTS), "model": {"type": "minecraft:model", "model": f"{NS}:item/{id}"}}],
		"fallback": {"type": "minecraft:model", "model": f"{NS}:item/{id}_in_hand"}}}


def flat_model(id, parent):
	return {"parent": parent, "textures": {"layer0": f"{NS}:item/{id}"}}


def plain_item(id):
	return {"model": {"type": "minecraft:model", "model": f"{NS}:item/{id}"}}


def write_json(rel, data):
	path = os.path.join(ASSETS, rel)
	os.makedirs(os.path.dirname(path), exist_ok=True)
	with open(path, "w", encoding="utf-8") as f:
		json.dump(data, f, indent=2)
		f.write("\n")
	print("wrote", os.path.relpath(path, lib.ROOT))


def write_png(rel, img):
	path = os.path.join(ASSETS, rel)
	os.makedirs(os.path.dirname(path), exist_ok=True)
	img.save(path)
	print("wrote", os.path.relpath(path, lib.ROOT))


# ------------------------------------------------------------------ checks

def check(w):
	probs = []
	if len(w.elements) > MAX_ELEMENTS:
		probs.append(f"{w.id}: {len(w.elements)} elements (at most {MAX_ELEMENTS})")
	for e in w.elements:
		for v in list(e.lo) + list(e.hi):
			if v < lib.MODEL_MIN - 1e-6 or v > lib.MODEL_MAX + 1e-6:
				probs.append(f"{w.id}: element {e.name} reaches {v:.3f}, outside {lib.MODEL_MIN}..{lib.MODEL_MAX}")
				break
		if not e.faces:
			probs.append(f"{w.id}: element {e.name} has no faces")
	probs += [f"{w.id}: {m}" for m in w.painter.sh.problems()]
	return probs


def preview(weapons, out):
	"""Model views (the +x flat, the -z side, three quarters) of each weapon and the new icons at 8x, into out (a
	scratch folder). The holds in the hand were checked with a scratch renderer of the display transforms."""
	import preview as P
	os.makedirs(out, exist_ok=True)
	for w in weapons:
		ys = [v for e in w.elements for v in (e.lo[1], e.hi[1])]
		half = max(abs(v - 8.0) for e in w.elements for v in (e.lo[0], e.hi[0], e.lo[2], e.hi[2])) + 1.0
		frame = ((8.0, (min(ys) + max(ys)) / 2, 8.0), max(half, 3.0), (max(ys) - min(ys)) / 2 + 1.0)
		group = P.Group(w.elements, {"#part": w.painter.sh.img})
		cells = [P.label(P.on_backdrop(P.render([group], view, frame, scale=16)), name)
				for name, view in (("+x flat", (0, 0)), ("-z (spine) side", (90, 0)), ("three quarters", (38, 16)))]
		P.grid(cells, 3).save(os.path.join(out, f"{w.id}_model.png"))
	icons = [P.on_backdrop(mask_icon(m, c, s, pl).resize((128, 128), Image.NEAREST)) for m, c, s, pl in ICONS.values()]
	P.grid([P.label(img, id) for img, id in zip(icons, ICONS)], len(icons)).save(os.path.join(out, "icons.png"))
	print("previews in", out)


def main():
	ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
	ap.add_argument("--check", action="store_true", help="check the models and icons without writing anything")
	ap.add_argument("--preview", metavar="DIR", help="render model views and the icons into DIR instead of writing assets")
	a = ap.parse_args()
	weapons = build_all()
	probs = [m for w in weapons for m in check(w)]
	probs += [m for id, (mask, colours, _, _) in ICONS.items() for m in check_icon(id, mask, colours)]
	if probs:
		raise SystemExit("\n".join(probs))
	if a.check:
		for w in weapons:
			print(f"{w.id}: {len(w.elements)} elements, ok")
		print(f"icons: {', '.join(ICONS)}, ok")
		return
	if a.preview:
		preview(weapons, a.preview)
		return
	for w in weapons:
		write_json(f"models/item/{w.id}_in_hand.json", model_json(w))
		write_png(f"textures/item/{w.id}_model.png", w.painter.sh.img)
		write_json(f"models/item/{w.id}.json", flat_model(w.id, "minecraft:item/handheld"))
		write_json(f"items/{w.id}.json", item_definition(w.id))
	for id, (mask, colours, seed, plain) in ICONS.items():
		write_png(f"textures/item/{id}.png", mask_icon(mask, colours, seed, plain))
	for id in MOD_ICONS:
		write_json(f"models/item/{id}.json", flat_model(id, "minecraft:item/generated"))
		write_json(f"items/{id}.json", plain_item(id))


if __name__ == "__main__":
	main()
