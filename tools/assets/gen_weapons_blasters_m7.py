#!/usr/bin/env python3
"""The Milestone 7 blaster art (docs/MILESTONE_7.md 9.1 to 9.3), WB's: the 3D in-hand models of the blaster pistol, the
scatter blaster and the blaster carbine, and the rebuilt models of the blaster rifle, the heavy repeater and Kaast's
Longshot, each with its 64x64 sheet and item definition (the flat icon in the inventory, on the ground, in frames and on
shelves; the model everywhere else, in the hand of the player, a trooper or a stand-in); the 16x16 icons of the scatter
blaster and the carbine, and the icons of the Aurelite Barrel and the Duskglass Cell.

The guns are original, used-universe machinery in the manner of the Milestone 1 and 4 rifle and repeater models, at the
new models' detail: gunmetal worn at the edges, rubber and composite furniture, exposed power cells with a lit window,
vents and fins. The rifle, repeater and longshot keep their old grips, stocks, display transforms and muzzle points
(and their colours: the rifle's red cell and brown furniture, the repeater's three barrels and red canister, the
longshot's crimson furniture), so troopers' and enforcers' poses and the glow layers still fit. Geometry and paint come
from the hilt model library (tools/assets/hilts/lib.py: box, octo, transform, Sheet), imported and never changed.

Frames (the display transforms keep rotation 0)
  A model point m (model units) sits in the hand at s * (m / 16 - 0.5) blocks, s the display scale: +y up when the gun
  is aimed, -z forward along the arm, the origin on the top edge of the fist at the arm's end (the vanilla held-item
  frame, which MuzzleGlowLayer, TrooperGlowLayer, CartelGlowLayer and PistolGlowLayer also use). The fist fills
  s * (m - 8) in x -2..2, y -4..0 and z 0..4 pixels, so every grip is at z 7 to 12 and y 2 to 8.5 and every receiver sits
  on y 8 or above. The muzzles:
    pistol    scale 0.64, muzzle (8, 11, 0)        -> (0, 0.12, -0.32), the glow layers' pistol point, unchanged;
    rifle     scale 0.6,  muzzle (8, 9.75, -11)    -> (0, 0.0656, -0.7125), unchanged (the glow layers use 0.07, -0.72);
    repeater  scale 0.6,  cluster (8, 9.875, -12)  -> (0, 0.0703, -0.75), unchanged;
    longshot  scale 0.6,  muzzle (8, 9.75, -16)    -> (0, 0.0656, -0.9), unchanged;
    scatter   scale 0.6,  mouth (8, 10, -9)        -> (0, 0.075, -0.6375);
    carbine   scale 0.6,  muzzle (8, 9.75, -9.5)   -> (0, 0.0656, -0.6563).
  The run prints them; BlasterM7Client's muzzle constants copy them.

Item definitions select minecraft:display_context: gui, ground, fixed and on_shelf show the flat icon
(oldrepublic:item/<id>, parent item/generated); everything else the model (oldrepublic:item/<id>_in_hand). The pistol,
rifle, repeater and longshot keep their old icons. Old generators that once wrote some of these files
(gen_textures_enemy.py the pistol's and rifle's models and sheets, gen_textures_equipment.py --m4 the repeater's and
longshot's) must not be re-run over them.

Usage: python3 tools/assets/gen_weapons_blasters_m7.py [--out DIR] [--check]
  --out DIR  writes the files under DIR (an assets/oldrepublic tree) instead of the repository, for a look first;
  --check    builds everything and checks it (element counts, the 64x64 bounds, tinted regions, the muzzles) without
             writing.
"""
import argparse
import json
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "hilts"))

import lib  # noqa: E402
from lib import ASSETS, NS, Sheet, box, octo, rot_x, rot_z, transform, translate  # noqa: E402

MAX_ELEMENTS = 72
# Octagonal tube sides after tube(): octo side k faces these ways along a barrel.
EAST, DOWN, WEST, UP = 0, 2, 4, 6

# ------------------------------------------------------------------ palette

OUTLINE = (24, 26, 30)
GUNMETAL = (66, 70, 78)
GUNMETAL_DARK = (50, 53, 60)
STEEL = (122, 128, 136)
STEEL_LIGHT = (164, 170, 178)
BLACK = (32, 33, 38)
RUBBER = (42, 42, 47)
BROWN = (104, 68, 44)
BROWN_DARK = (78, 50, 32)
OLIVE = (94, 98, 70)
OLIVE_DARK = (72, 76, 54)
BRASS = (178, 146, 82)
RED = (178, 52, 36)
ORANGE = (190, 104, 46)
GREEN = (96, 236, 132)
GREEN_DARK = (40, 120, 70)
GLASS = (96, 160, 206)
AMBER = (255, 176, 70)
AMBER_DARK = (170, 80, 30)
CRIMSON = (116, 32, 38)
CRIMSON_DARK = (84, 22, 28)
RED_PAINT = (150, 46, 36)


# ------------------------------------------------------------------ geometry helpers

def tube(cx, cy, z0, z1, r, paint, *, front=False, back=False, cap=None, sides=None, skip=(), name="tube"):
	"""An octagonal tube along z through (cx, cy), from z0 (the front, -z) to z1: lib.octo turned onto the barrel axis.
	front and back add the end caps (one flat colour, cap or the paint's own); sides maps EAST, DOWN, WEST, UP (and the
	diagonals between) to their own paints."""
	els = octo(16.0 - z1, 16.0 - z0, r, paint, top=front, bottom=back, cap=cap, sides=sides, skip=skip, name=name)
	els = transform(els, rot_x(-90.0), (8.0, 8.0, 8.0))
	return translate(els, (cx - 8.0, cy - 8.0, 0.0))


def drum(cy, cz, x0, x1, r, paint, *, ends=True, cap=None, name="drum"):
	"""An octagonal drum along x through (cy, cz), from x0 to x1, its round ends facing the sides."""
	els = octo(x0, x1, r, paint, top=ends, bottom=ends, cap=cap, name=name)
	els = transform(els, rot_z(-90.0), (8.0, 8.0, 8.0))
	return translate(els, (0.0, cy - 8.0, cz - 8.0))


def raked(elements, degrees, pivot):
	"""The elements turned about the x axis through pivot: a positive angle swings their lower ends back (+z)."""
	return transform(elements, rot_x(-degrees), pivot)


def tilted(elements, degrees, pivot):
	"""The elements turned about the x axis through pivot: a positive angle swings their lower ends forward (-z)."""
	return transform(elements, rot_x(degrees), pivot)


# ------------------------------------------------------------------ sheet helpers

def metal(sh, name, base, spread=6, along=True, pits=4, scratches=3, seed=None, bevel=True):
	"""Brushed metal (streaks along a gun's length on its sides when along) with a few pits and scratches and a lit top
	edge over a darker bottom one."""
	sh.brushed(name, base, spread, vertical=not along, seed=seed)
	sh.wear(name, pits=pits, scratches=scratches, amount=18, seed=seed)
	if bevel:
		sh.bevel(name, light=24, dark=20)


def seams(sh, name, columns, dark=24, light=12):
	"""Panel seams: a darker texel column at each of columns (region-relative) with a lit column after it."""
	reg = sh._reg(name)
	for c in columns:
		for y in range(reg.y, reg.y + reg.h):
			x = reg.x + c
			sh.px(x, y, [v - dark for v in sh.get(x, y)])
			if c + 1 < reg.w:
				sh.px(x + 1, y, [v + light for v in sh.get(x + 1, y)])


def chips(sh, name, under, count=10, seed=0):
	"""Paint chipped off to the metal under it: small patches of under across the region."""
	rng = random.Random(seed)
	reg = sh._reg(name)
	for _ in range(count):
		x, y = reg.x + rng.randrange(reg.w), reg.y + rng.randrange(reg.h)
		for dx, dy in ((0, 0), (1, 0), (0, 1)) if rng.random() < 0.6 else ((0, 0),):
			if reg.x <= x + dx < reg.x + reg.w and reg.y <= y + dy < reg.y + reg.h:
				sh.px(x + dx, y + dy, [c + rng.uniform(-8, 8) for c in under])


def energy(sh, name, core=GREEN, rim=GREEN_DARK, hot=(225, 255, 232)):
	"""A lit cell window: a bright core fading to a darker rim, one white-hot texel."""
	reg = sh._reg(name)
	cx, cy = (reg.w - 1) / 2.0, (reg.h - 1) / 2.0
	for x, y, lx, ly in sh._pixels(reg):
		d = max(abs(lx - cx) / (cx + 0.5), abs(ly - cy) / (cy + 0.5))
		t = min(1.0, d * 1.15)
		sh.px(x, y, [core[i] * (1 - t) + rim[i] * t for i in range(3)])
	sh.px(reg.x + reg.w // 2, reg.y + reg.h // 2 - 1, hot)


def slots(sh, name, base, slot=BLACK, period=4, slot_w=2, slot_h=1, seed=None):
	"""A slotted shroud: brushed base metal with rows of dark slots (slot_w by slot_h texels, every period texels, rows
	staggered), each with a lit lower lip."""
	metal(sh, name, base, spread=4, pits=2, scratches=1, seed=seed, bevel=False)
	reg = sh._reg(name)
	for row, gy in enumerate(range(1, reg.h - slot_h, period // 2 + 1)):
		for gx in range(1 + (row % 2) * (period // 2), reg.w - slot_w + 1, period):
			for dx in range(slot_w):
				for dy in range(slot_h):
					sh.px(reg.x + gx + dx, reg.y + gy + dy, slot)
				lip_y = reg.y + gy + slot_h
				if lip_y < reg.y + reg.h:
					sh.px(reg.x + gx + dx, lip_y, [c + 22 for c in sh.get(reg.x + gx + dx, lip_y)])


def stripes(sh, name, paint_rgb, stripe_rgb, period=4, width=2):
	"""Diagonal hazard stripes, worn: stripe_rgb bands across paint_rgb."""
	for x, y, lx, ly in sh._pixels(name):
		sh.px(x, y, stripe_rgb if (lx + ly) % period < width else paint_rgb)
	sh.noise(name, 5, seed=7)


def solid(sh, name, rgb, noise=4, seed=None):
	sh.fill(name, rgb)
	sh.noise(name, noise, seed=seed)


def bore(sh, name, rim=(70, 74, 82), hole=(14, 14, 17)):
	"""A barrel's mouth seen from in front (a fit paint): a metal rim, the dark bore in the middle, one lit texel."""
	reg = sh._reg(name)
	for x, y, lx, ly in sh._pixels(name):
		edge = min(lx, ly, reg.w - 1 - lx, reg.h - 1 - ly)
		sh.px(x, y, hole if edge >= max(1, min(reg.w, reg.h) // 4) else rim)
	sh.px(reg.x, reg.y, [c + 30 for c in rim])


def grain(sh, name, base, dark, seed=None):
	"""Composite furniture: streaks along the length, a few darker grain lines, light wear and a bevel."""
	sh.brushed(name, base, 7, vertical=False, seed=seed)
	rng = random.Random(seed)
	reg = sh._reg(name)
	for _ in range(max(1, reg.h // 4)):
		y = reg.y + rng.randrange(reg.h)
		x0 = reg.x + rng.randrange(reg.w)
		for x in range(x0, min(reg.x + reg.w, x0 + rng.randrange(4, 12))):
			sh.px(x, y, [c + rng.uniform(-4, 4) for c in dark])
	sh.wear(name, pits=2, scratches=2, amount=14, seed=seed)
	sh.bevel(name, light=12, dark=14)


# ------------------------------------------------------------------ the guns

class Gun:
	"""One blaster's in-hand model: its elements, its sheet, its display scales and first-person nudge, its muzzle."""

	def __init__(self, id, scale, fp_scale, fp_translation, muzzle, sheet, elements):
		self.id = id
		self.scale = scale
		self.fp_scale = fp_scale
		self.fp_translation = fp_translation
		self.muzzle = muzzle
		self.sheet = sheet
		self.elements = lib.flatten([elements])

	def frame_muzzle(self):
		"""The muzzle in the held-item frame, in blocks (third person: display translation 0, rotation 0)."""
		return tuple(self.scale * (c / 16.0 - 0.5) for c in self.muzzle)

	def display(self):
		third = {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [self.scale] * 3}
		first = {"rotation": [0, 0, 0], "translation": list(self.fp_translation), "scale": [self.fp_scale] * 3}
		return {
			"thirdperson_righthand": third,
			"thirdperson_lefthand": dict(third),
			"firstperson_righthand": first,
			"firstperson_lefthand": dict(first),
			"head": {"rotation": [0, 0, 0], "translation": [0, 10, 0], "scale": [0.8, 0.8, 0.8]},
		}

	def model_json(self):
		elements = []
		for el in self.elements:
			out = {"name": el.name} if el.name else {}
			out.update(el.json())
			elements.append(out)
		return {
			"textures": {"gun": f"{NS}:item/{self.id}_model", "particle": f"{NS}:item/{self.id}"},
			"gui_light": "front",
			"display": self.display(),
			"elements": elements,
		}

	def problems(self):
		out = list(self.sheet.problems())
		if len(self.elements) > MAX_ELEMENTS:
			out.append(f"{self.id}: {len(self.elements)} elements (at most {MAX_ELEMENTS})")
		for el in self.elements:
			for c in el.corners():
				if any(v < lib.MODEL_MIN - 1e-6 or v > lib.MODEL_MAX + 1e-6 for v in c):
					out.append(f"{self.id}: {el.name} leaves the model bounds at {[round(v, 3) for v in c]}")
					break
			for d, f in el.faces.items():
				if f.texture != "#gun":
					out.append(f"{self.id}: {el.name} {d} names {f.texture}")
		return out


def pistol():
	"""The blaster pistol: a slab-sided slide over a short shrouded barrel with a vented emitter, the power cell on the
	left (the side first person sees) with its lit window and a ready lamp, a raked brown composite grip on a brass
	base, a compact finned heat sink at the back."""
	sh = Sheet("#gun", seed=71)
	sh.region("slide_side", 0, 0, 40, 14)
	sh.region("slide_top", 40, 0, 12, 36)
	sh.region("barrel", 52, 0, 12, 12)
	sh.region("dark", 52, 12, 12, 8)
	sh.region("frame", 0, 14, 40, 6)
	sh.region("grip", 0, 20, 12, 28)
	sh.region("grip_edge", 12, 20, 8, 28)
	sh.region("shroud", 20, 20, 20, 12)
	sh.region("cell", 20, 32, 20, 8)
	sh.region("serration", 20, 40, 20, 8)
	sh.region("energy", 52, 20, 12, 8)
	sh.region("brass", 52, 28, 12, 8)
	sh.region("accent", 40, 36, 12, 4)
	sh.region("lamp", 40, 40, 12, 4)
	sh.region("steel", 52, 36, 12, 8)
	sh.region("emitter", 0, 48, 24, 8)
	sh.region("sink", 24, 48, 24, 8)

	metal(sh, "slide_side", GUNMETAL, seed=1)
	seams(sh, "slide_side", (13, 29))
	sh.rivets("slide_side", ((4, 7), (35, 7)))
	sh.rect(2, 11, 37, 11, (52, 55, 62))
	metal(sh, "slide_top", (90, 95, 103), along=False, seed=2)
	sh.vents("slide_top", (40, 42, 47), 2, slot_w=1, margin=4, vertical=True)
	metal(sh, "barrel", STEEL, along=False, pits=2, scratches=1, seed=3, bevel=False)
	solid(sh, "dark", BLACK, seed=4)
	metal(sh, "frame", GUNMETAL_DARK, seed=5, bevel=False)
	seams(sh, "frame", (20,))
	sh.knurl("grip", BROWN, BROWN_DARK, period=3)
	sh.noise("grip", 4, seed=6)
	sh.bevel("grip", light=10, dark=14)
	sh.brushed("grip_edge", BROWN_DARK, 5, vertical=True, seed=7)
	slots(sh, "shroud", (92, 96, 104), period=4, slot_w=2, slot_h=1, seed=8)
	sh.bevel("shroud", light=20, dark=16)
	metal(sh, "cell", (84, 104, 96), along=True, pits=2, scratches=1, seed=9)
	seams(sh, "cell", (6, 13), dark=20, light=8)
	sh.ridges("serration", (62, 66, 73), (30, 32, 36), period=2, groove_w=1, horizontal=False)
	energy(sh, "energy")
	metal(sh, "brass", BRASS, spread=8, pits=2, scratches=1, along=False, seed=10, bevel=False)
	solid(sh, "accent", RED, seed=11)
	energy(sh, "lamp", core=(255, 176, 70), rim=(170, 80, 30), hot=(255, 236, 200))
	metal(sh, "steel", STEEL_LIGHT, pits=1, scratches=1, seed=12, bevel=False)
	metal(sh, "emitter", (78, 82, 90), along=False, pits=2, scratches=1, seed=13, bevel=False)
	sh.vents("emitter", BLACK, 6, slot_w=1, margin=1, vertical=True)
	metal(sh, "sink", (58, 62, 69), along=False, pits=1, scratches=1, seed=14, bevel=True)

	side, top, barrel, dark = sh.paint("slide_side"), sh.paint("slide_top"), sh.paint("barrel"), sh.paint("dark")
	frame, grip, edge, shroud = sh.paint("frame"), sh.paint("grip"), sh.paint("grip_edge"), sh.paint("shroud")
	cell, serr, glow, brass = sh.paint("cell"), sh.paint("serration"), sh.paint("energy", "fit"), sh.paint("brass")
	red, lamp, steel = sh.paint("accent", "fit"), sh.paint("lamp", "fit"), sh.paint("steel")
	emitter, sink = sh.paint("emitter"), sh.paint("sink")

	els = []
	# The vented emitter with its dark bore, the short barrel, and the slotted shroud it runs into.
	els += tube(8, 11, 0.0, 1.3, 1.18, emitter, front=True, back=True, cap=dark, name="emitter")
	els += tube(8, 11, 1.25, 1.95, 0.78, barrel, name="barrel")
	els.append(box(6.8, 9.8, 1.9, 9.2, 12.2, 4.25, shroud, paints={"north": dark}, skip=("south", "down"), name="shroud"))
	# The slide, its rear serrations (proud plates on both sides), the sights and the ready lamp (left, first person).
	els.append(box(6.6, 9.6, 4.2, 9.4, 13.3, 12.6, side, paints={"up": top}, skip=("down",), name="slide"))
	els.append(box(6.52, 10.1, 10.4, 6.6, 12.9, 12.3, serr, faces=("west",), name="serration_left"))
	els.append(box(9.4, 10.1, 10.4, 9.48, 12.9, 12.3, serr, faces=("east",), name="serration_right"))
	els.append(box(7.7, 13.3, 4.5, 8.3, 13.95, 5.3, steel, skip=("down",), name="front_sight"))
	els.append(box(7.0, 13.3, 11.6, 7.65, 13.9, 12.5, steel, skip=("down",), name="rear_sight_left"))
	els.append(box(8.35, 13.3, 11.6, 9.0, 13.9, 12.5, steel, skip=("down",), name="rear_sight_right"))
	els.append(box(6.54, 12.45, 9.2, 6.6, 12.95, 9.9, lamp, faces=("west",), light=10, name="ready_lamp"))
	# The frame under the slide, the trigger guard and trigger, the selector (red, left side).
	els.append(box(7.0, 8.65, 3.4, 9.0, 9.75, 12.0, frame, skip=("up",), name="frame"))
	els.append(box(7.6, 7.0, 6.0, 8.4, 7.4, 9.4, frame, name="guard"))
	els.append(box(7.6, 7.4, 6.0, 8.4, 8.65, 6.4, frame, skip=("up",), name="guard_front"))
	els.append(box(7.78, 7.5, 7.5, 8.22, 8.65, 7.95, steel, skip=("up",), name="trigger"))
	els.append(box(6.9, 9.0, 9.4, 7.0, 9.5, 10.6, red, faces=("west",), name="selector"))
	# The power cell on the left: a tube with brass caps and a lit window facing out.
	els += tube(6.0, 11.35, 5.5, 10.0, 0.72, cell, name="cell")
	els += tube(6.0, 11.35, 5.1, 5.55, 0.8, brass, front=True, back=True, name="cell_cap_front")
	els += tube(6.0, 11.35, 9.95, 10.4, 0.8, brass, front=True, back=True, name="cell_cap_back")
	els.append(box(5.2, 11.0, 6.2, 5.3, 11.7, 9.3, glow, faces=("west",), light=12, name="cell_window"))
	# The heat sink at the back: a block between two thin fins.
	els.append(box(7.3, 10.3, 12.6, 8.7, 12.6, 13.5, sink, skip=("north",), name="sink"))
	for i, z in enumerate((12.75, 13.25)):
		els.append(box(6.9, 10.05, z, 9.1, 12.9, z + 0.22, sink, name=f"sink_fin_{i}"))
	# The grip, raked back, with a brass base plate.
	grip_els = [
		box(7.05, 2.7, 9.0, 8.95, 9.0, 12.1, grip, paints={"north": edge, "south": edge}, skip=("up",), name="grip"),
		box(6.95, 2.2, 8.85, 9.05, 2.75, 12.3, brass, name="grip_base"),
	]
	els += raked(grip_els, 16.0, (8.0, 9.0, 10.5))
	return Gun("blaster_pistol", 0.64, 0.75, (0, 3, -2.5), (8.0, 11.0, 0.0), sh, els)


def scatter_blaster():
	"""The scatter blaster: a door-breacher. A wide ribbed emitter bell in chipped orange, flaring to its mouth, on a
	short neck; a heavy receiver with a ribbed fore-end, fins on top and a hazard plate on each side; a drum cell slung
	under it; a black rubber grip and a short skeletal stock; a power cable along the left side to the bell."""
	sh = Sheet("#gun", seed=83)
	sh.region("body_side", 0, 0, 48, 16)
	sh.region("body_top", 48, 0, 16, 48)
	sh.region("bell", 0, 16, 24, 12)
	sh.region("neck", 24, 16, 12, 8)
	sh.region("pump", 0, 28, 24, 12)
	sh.region("drum", 24, 24, 24, 12)
	sh.region("grip", 0, 40, 12, 20)
	sh.region("grip_edge", 12, 40, 8, 20)
	sh.region("dark", 36, 16, 12, 8)
	sh.region("steel", 20, 40, 12, 8)
	sh.region("energy", 32, 36, 8, 8)
	sh.region("brass", 40, 36, 8, 8)
	sh.region("rail", 20, 48, 28, 8)
	sh.region("cable", 32, 44, 16, 4)
	sh.region("mouth", 48, 48, 16, 16)
	sh.region("hazard", 20, 56, 28, 8)

	metal(sh, "body_side", (60, 63, 70), seed=21)
	seams(sh, "body_side", (11, 30, 41))
	sh.rivets("body_side", ((3, 4), (3, 11), (44, 4), (44, 11), (22, 12)))
	metal(sh, "body_top", (76, 80, 88), along=False, seed=22)
	sh.brushed("bell", ORANGE, 6, vertical=False, seed=23)
	chips(sh, "bell", (96, 100, 108), count=12, seed=23)
	sh.bevel("bell", light=28, dark=26)
	metal(sh, "neck", STEEL, along=False, pits=2, scratches=1, seed=24, bevel=False)
	sh.ridges("pump", (54, 54, 60), (26, 26, 30), period=3, groove_w=1, highlight=(74, 74, 80), horizontal=False)
	sh.noise("pump", 3, seed=25)
	metal(sh, "drum", (88, 94, 80), along=True, pits=3, scratches=2, seed=26)
	seams(sh, "drum", (7, 16), dark=22, light=10)
	sh.ridges("grip", RUBBER, (28, 28, 32), period=3, groove_w=1, highlight=(58, 58, 64), horizontal=True)
	sh.noise("grip", 3, seed=27)
	sh.brushed("grip_edge", (36, 36, 41), 4, vertical=True, seed=28)
	solid(sh, "dark", BLACK, seed=29)
	metal(sh, "steel", STEEL_LIGHT, pits=1, scratches=1, seed=30, bevel=False)
	energy(sh, "energy")
	metal(sh, "brass", BRASS, spread=8, pits=2, scratches=1, along=False, seed=31, bevel=False)
	metal(sh, "rail", (48, 51, 57), pits=2, scratches=1, seed=32, bevel=True)
	sh.ridges("cable", (36, 36, 40), (22, 22, 26), period=2, groove_w=1, horizontal=False)
	stripes(sh, "hazard", ORANGE, (36, 36, 40), period=6, width=3)
	sh.bevel("hazard", light=20, dark=20)
	# The bell's mouth seen from in front: a dark throat, the glowing emitter grid deep inside.
	sh.fill("mouth", (20, 20, 24))
	for x, y, lx, ly in sh._pixels("mouth"):
		if lx % 3 == 1 and ly % 3 == 1:
			sh.px(x, y, (52, 150, 86))
		elif lx in (0, 15) or ly in (0, 15):
			sh.px(x, y, (34, 34, 38))

	side, top, bell, neck = sh.paint("body_side"), sh.paint("body_top"), sh.paint("bell"), sh.paint("neck")
	pump, drum_paint, grip, edge = sh.paint("pump"), sh.paint("drum"), sh.paint("grip"), sh.paint("grip_edge")
	dark, steel, glow, brass = sh.paint("dark"), sh.paint("steel"), sh.paint("energy", "fit"), sh.paint("brass")
	rail, cable, mouth, hazard = sh.paint("rail"), sh.paint("cable"), sh.paint("mouth", "fit"), sh.paint("hazard", "fit")

	els = []
	# The emitter bell: ribs flaring out to the mouth, dark grooves between, on a steel neck.
	els += tube(8, 10, -9.0, -8.35, 2.75, bell, front=True, back=True, cap=dark, name="bell_lip")
	els += tube(8, 10, -8.35, -8.0, 2.2, dark, name="bell_groove_front")
	els += tube(8, 10, -8.0, -7.25, 2.4, bell, back=True, name="bell_rib")
	els += tube(8, 10, -7.25, -6.95, 1.85, dark, name="bell_groove_back")
	els += tube(8, 10, -6.95, -6.3, 2.0, bell, back=True, name="bell_base")
	els.append(box(6.2, 8.2, -9.06, 9.8, 11.8, -9.0, mouth, faces=("north",), light=6, name="mouth"))
	els += tube(8, 10, -6.35, -4.15, 1.3, neck, name="neck")
	# The receiver with its ribbed fore-end, top fins, rail and hazard plates.
	els.append(box(6.2, 7.8, -4.2, 9.8, 12.0, 10.0, side, paints={"up": top, "down": dark}, name="receiver"))
	els.append(box(5.9, 6.9, -4.5, 10.1, 9.4, -0.6, pump, paints={"up": dark, "down": dark}, name="fore_end"))
	for i, z in enumerate((0.0, 1.2, 2.4, 3.6)):
		els.append(box(6.6, 12.0, z, 9.4, 12.9, z + 0.45, steel, skip=("down",), name=f"top_fin_{i}"))
	els.append(box(7.35, 12.0, 5.2, 8.65, 12.55, 9.6, rail, skip=("down",), name="top_rail"))
	els.append(box(6.14, 9.7, -3.8, 6.2, 11.6, -1.2, hazard, faces=("west",), name="hazard_left"))
	els.append(box(9.8, 9.7, -3.8, 9.86, 11.6, -1.2, hazard, faces=("east",), name="hazard_right"))
	# The drum cell under the receiver: brass ends, a dark band, a lit window on the left end.
	els += drum(6.0, 2.4, 6.55, 9.45, 1.85, drum_paint, cap=brass, name="drum")
	els += drum(6.0, 2.4, 7.7, 8.3, 1.95, dark, ends=False, name="drum_band")
	els += drum(6.0, 2.4, 6.35, 6.6, 1.0, dark, name="drum_hub_left")
	els += drum(6.0, 2.4, 9.4, 9.65, 1.0, dark, name="drum_hub_right")
	els.append(box(6.29, 5.55, 1.95, 6.35, 6.45, 2.85, glow, faces=("west",), light=12, name="drum_window"))
	# The charging handle and the cable to the bell, on the left (first person sees them).
	els.append(box(5.65, 10.2, 6.6, 6.2, 10.95, 8.2, steel, skip=("east",), name="charging_handle"))
	els.append(box(5.85, 9.55, -5.9, 6.2, 9.95, 1.2, cable, skip=("east",), name="cable"))
	els.append(box(5.75, 9.4, 1.0, 6.2, 10.1, 1.9, brass, skip=("east",), name="cable_plug"))
	# Trigger and guard, the raked grip.
	els.append(box(7.6, 6.9, 5.2, 8.4, 7.3, 7.6, dark, name="guard"))
	els.append(box(7.78, 7.3, 6.2, 8.22, 7.8, 6.6, steel, skip=("up",), name="trigger"))
	grip_els = [
		box(7.1, 3.6, 7.2, 8.9, 7.9, 9.9, grip, paints={"north": edge, "south": edge}, skip=("up",), name="grip"),
		box(7.0, 3.1, 7.05, 9.0, 3.65, 10.1, dark, name="grip_base"),
	]
	els += raked(grip_els, 14.0, (8.0, 7.9, 8.6))
	# The skeletal stock: two rails, a brace and a rubber butt.
	els.append(box(7.4, 10.4, 10.0, 8.6, 11.2, 14.6, rail, name="stock_upper"))
	els.append(box(7.4, 8.0, 10.0, 8.6, 8.8, 14.2, rail, name="stock_lower"))
	els.append(box(7.55, 8.8, 12.2, 8.45, 10.4, 12.8, rail, skip=("up", "down"), name="stock_brace"))
	els.append(box(6.9, 7.5, 14.4, 9.1, 11.7, 15.5, grip, paints={"north": edge, "south": edge}, name="stock_butt"))
	return Gun("scatter_blaster", 0.6, 0.6, (0, 1, -2), (8.0, 10.0, -9.0), sh, els)


def blaster_carbine():
	"""The blaster carbine: compact and military. A slotted compensator on a short barrel inside a perforated shroud,
	a vertical foregrip, a gunmetal receiver with a top rail and a short scope, a forward-raked power pack, a side cell
	with a lit strip on the left, the ejection port on the right, olive furniture and a folding skeleton stock,
	extended."""
	sh = Sheet("#gun", seed=97)
	sh.region("body_side", 0, 0, 48, 12)
	sh.region("body_top", 48, 0, 12, 48)
	sh.region("shroud", 0, 12, 24, 12)
	sh.region("barrel", 24, 12, 12, 8)
	sh.region("comp", 36, 12, 12, 8)
	sh.region("olive", 0, 24, 16, 28)
	sh.region("olive_edge", 16, 24, 8, 28)
	sh.region("rail", 24, 20, 24, 8)
	sh.region("pack", 24, 28, 16, 16)
	sh.region("dark", 40, 28, 8, 8)
	sh.region("energy", 40, 36, 8, 8)
	sh.region("brass", 24, 44, 8, 8)
	sh.region("steel", 32, 44, 16, 8)
	sh.region("scope", 0, 52, 24, 8)
	sh.region("glass", 24, 52, 8, 8)
	sh.region("cell", 32, 52, 16, 8)
	sh.region("port", 48, 48, 12, 8)
	sh.region("accent", 48, 56, 12, 4)

	metal(sh, "body_side", GUNMETAL, seed=41)
	seams(sh, "body_side", (9, 26, 38))
	sh.rivets("body_side", ((3, 7), (44, 7)))
	metal(sh, "body_top", (84, 89, 97), along=False, seed=42)
	slots(sh, "shroud", (78, 82, 90), period=4, slot_w=2, slot_h=1, seed=43)
	sh.bevel("shroud", light=18, dark=14)
	metal(sh, "barrel", STEEL, along=False, pits=2, scratches=1, seed=44, bevel=False)
	metal(sh, "comp", (70, 74, 82), along=False, pits=1, scratches=1, seed=45, bevel=False)
	sh.vents("comp", BLACK, 3, slot_w=1, margin=2, vertical=True)
	sh.ridges("olive", OLIVE, OLIVE_DARK, period=4, groove_w=1, highlight=(114, 118, 88), horizontal=True)
	sh.noise("olive", 4, seed=46)
	sh.brushed("olive_edge", OLIVE_DARK, 5, vertical=True, seed=47)
	metal(sh, "rail", (46, 49, 55), pits=1, scratches=1, seed=48, bevel=False)
	sh.vents("rail", (26, 27, 31), 8, slot_w=1, margin=0, vertical=True)
	metal(sh, "pack", (62, 66, 72), pits=2, scratches=2, seed=49)
	seams(sh, "pack", (7,), dark=20, light=10)
	solid(sh, "dark", BLACK, seed=50)
	energy(sh, "energy")
	metal(sh, "brass", BRASS, spread=8, pits=2, scratches=1, along=False, seed=51, bevel=False)
	metal(sh, "steel", STEEL_LIGHT, pits=1, scratches=1, seed=52, bevel=False)
	metal(sh, "scope", (42, 44, 49), along=True, pits=2, scratches=2, seed=53)
	sh.glass("glass", GLASS, highlight=70)
	metal(sh, "cell", (84, 104, 96), along=True, pits=2, scratches=1, seed=54)
	solid(sh, "port", (22, 22, 26), seed=55)
	sh.outline("port", (104, 110, 118))
	solid(sh, "accent", RED, seed=56)

	side, top, shroud, barrel = sh.paint("body_side"), sh.paint("body_top"), sh.paint("shroud"), sh.paint("barrel")
	comp, olive, olive_edge, rail = sh.paint("comp"), sh.paint("olive"), sh.paint("olive_edge"), sh.paint("rail")
	pack, dark, glow, brass = sh.paint("pack"), sh.paint("dark"), sh.paint("energy", "fit"), sh.paint("brass")
	steel, scope, glass, cell = sh.paint("steel"), sh.paint("scope"), sh.paint("glass", "fit"), sh.paint("cell")
	port, red = sh.paint("port", "fit"), sh.paint("accent", "fit")

	els = []
	# Compensator, barrel, the perforated shroud with a darker front ring.
	els += tube(8, 9.75, -9.5, -8.2, 0.95, comp, front=True, back=True, cap=dark, name="compensator")
	els += tube(8, 9.75, -8.25, -5.9, 0.62, barrel, name="barrel")
	els += tube(8, 9.75, -6.3, -5.85, 1.55, dark, front=True, back=True, name="shroud_ring")
	els += tube(8, 9.75, -5.9, -0.6, 1.42, shroud, name="shroud")
	# The receiver, the rail on top, the scope on two mounts, the port (right) and the selector (left, red).
	els.append(box(6.8, 8.3, -0.8, 9.2, 11.3, 11.0, side, paints={"up": top, "down": dark}, name="receiver"))
	els.append(box(7.3, 11.3, -0.4, 8.7, 11.75, 10.2, rail, skip=("down",), name="top_rail"))
	els.append(box(7.6, 11.75, 2.2, 8.4, 12.3, 2.9, steel, skip=("down",), name="scope_mount_front"))
	els.append(box(7.6, 11.75, 5.3, 8.4, 12.3, 6.0, steel, skip=("down",), name="scope_mount_back"))
	els += tube(8, 13.05, 1.4, 6.8, 0.82, scope, front=True, back=True, cap=dark, name="scope")
	els.append(box(7.35, 12.4, 1.34, 8.65, 13.7, 1.4, glass, faces=("north",), light=4, name="scope_lens"))
	els += tube(8, 13.05, 0.9, 1.45, 0.98, scope, back=True, name="scope_hood")
	els.append(box(9.2, 9.55, 1.6, 9.27, 10.75, 4.6, port, faces=("east",), name="ejection_port"))
	els.append(box(6.73, 8.9, 9.8, 6.8, 9.4, 10.6, red, faces=("west",), name="selector"))
	# The vertical foregrip under the shroud.
	els.append(box(7.3, 5.6, -3.4, 8.7, 8.4, -1.8, olive, paints={"north": olive_edge, "south": olive_edge}, name="foregrip"))
	els.append(box(7.2, 8.3, -3.6, 8.8, 8.45, -1.6, dark, skip=("up",), name="foregrip_mount"))
	# The power pack, raked forward, with a brass foot.
	pack_els = [
		box(7.25, 4.5, 3.0, 8.75, 8.35, 5.4, pack, skip=("up",), name="pack"),
		box(7.15, 4.0, 2.9, 8.85, 4.55, 5.5, brass, name="pack_foot"),
	]
	els += tilted(pack_els, 10.0, (8.0, 8.3, 4.2))
	# The side cell on the left with its lit strip.
	els.append(box(6.2, 8.75, 5.8, 6.85, 10.65, 9.6, cell, skip=("east",), name="side_cell"))
	els.append(box(6.12, 9.35, 6.3, 6.22, 10.05, 9.1, glow, faces=("west",), light=12, name="side_cell_strip"))
	# Trigger, guard and the raked olive grip.
	els.append(box(7.6, 6.9, 5.6, 8.4, 7.3, 7.6, dark, name="guard"))
	els.append(box(7.78, 7.3, 6.4, 8.22, 8.35, 6.8, steel, skip=("up",), name="trigger"))
	grip_els = [box(7.1, 4.0, 7.2, 8.9, 8.35, 9.8, olive, paints={"north": olive_edge, "south": olive_edge}, skip=("up",), name="grip")]
	els += raked(grip_els, 15.0, (8.0, 8.3, 8.5))
	# The folding stock, extended: a hinge block, a top bar and a lower strut to an olive butt.
	els.append(box(7.0, 8.6, 11.0, 9.0, 10.9, 12.0, dark, name="stock_hinge"))
	els.append(box(7.5, 9.6, 12.0, 8.5, 10.5, 16.7, steel, name="stock_bar"))
	els.append(box(7.55, 7.9, 12.0, 8.45, 8.6, 16.2, steel, name="stock_strut"))
	els.append(box(7.0, 7.2, 16.6, 9.0, 11.1, 17.6, olive, paints={"north": olive_edge, "south": dark}, name="stock_butt"))
	return Gun("blaster_carbine", 0.6, 0.6, (0, 1, -2), (8.0, 9.75, -9.5), sh, els)


def blaster_rifle():
	"""The blaster rifle (Milestone 1, rebuilt): the service rifle troopers carry. A vented muzzle and a finned barrel
	ahead of a slotted shroud, a long receiver with a top rail and a short scope, the red power cell clamped on the right,
	a lit charge strip, vent grille, selector and charging knob on the left (the side first person sees), brown composite
	furniture: a handguard, a raked knurled grip and a solid stock with a raised comb and a rubber butt. The grip, stock,
	scale and muzzle are the Milestone 1 model's, so troopers' poses and the glow layers still fit."""
	sh = Sheet("#gun", seed=59)
	sh.region("body_side", 0, 0, 48, 12)
	sh.region("body_top", 48, 0, 16, 40)
	sh.region("shroud", 0, 12, 20, 10)
	sh.region("barrel", 20, 12, 12, 8)
	sh.region("muzzle", 32, 12, 16, 8)
	sh.region("wood", 0, 22, 24, 14)
	sh.region("wood_edge", 24, 20, 8, 16)
	sh.region("grip", 32, 20, 12, 16)
	sh.region("dark", 44, 20, 4, 8)
	sh.region("energy", 44, 28, 4, 8)
	sh.region("cell", 0, 36, 20, 10)
	sh.region("rail", 20, 36, 24, 6)
	sh.region("brass", 44, 36, 4, 6)
	sh.region("steel", 48, 40, 8, 8)
	sh.region("accent", 56, 40, 8, 4)
	sh.region("bore", 56, 44, 4, 4)
	sh.region("scope", 0, 46, 24, 8)
	sh.region("glass", 24, 46, 8, 8)
	sh.region("butt", 32, 46, 16, 6)
	sh.region("vent", 48, 48, 16, 8)
	sh.region("fins", 0, 54, 16, 6)

	metal(sh, "body_side", (58, 62, 70), seed=61)
	seams(sh, "body_side", (10, 27, 40))
	sh.rivets("body_side", ((3, 6), (44, 6), (24, 9)))
	metal(sh, "body_top", (82, 87, 95), along=False, seed=62)
	slots(sh, "shroud", (84, 88, 96), period=4, slot_w=2, slot_h=1, seed=63)
	sh.bevel("shroud", light=18, dark=14)
	metal(sh, "barrel", STEEL, along=False, pits=2, scratches=1, seed=64, bevel=False)
	metal(sh, "muzzle", (74, 78, 86), along=False, pits=1, scratches=1, seed=65, bevel=False)
	sh.vents("muzzle", BLACK, 4, slot_w=1, margin=1, vertical=True)
	grain(sh, "wood", BROWN, BROWN_DARK, seed=66)
	sh.brushed("wood_edge", BROWN_DARK, 5, vertical=True, seed=67)
	sh.knurl("grip", BROWN, BROWN_DARK, period=3)
	sh.noise("grip", 4, seed=68)
	sh.bevel("grip", light=10, dark=14)
	solid(sh, "dark", BLACK, seed=69)
	energy(sh, "energy")
	sh.brushed("cell", RED_PAINT, 6, vertical=False, seed=70)
	chips(sh, "cell", (104, 108, 116), count=9, seed=70)
	seams(sh, "cell", (5, 14), dark=26, light=10)
	sh.bevel("cell", light=26, dark=24)
	metal(sh, "rail", (46, 49, 55), pits=1, scratches=1, seed=71, bevel=False)
	sh.vents("rail", (26, 27, 31), 6, slot_w=1, margin=0, vertical=True)
	metal(sh, "brass", BRASS, spread=8, pits=1, scratches=1, along=False, seed=72, bevel=False)
	metal(sh, "steel", STEEL_LIGHT, pits=1, scratches=1, seed=73, bevel=False)
	solid(sh, "accent", RED, seed=74)
	bore(sh, "bore")
	metal(sh, "scope", (42, 44, 49), along=True, pits=2, scratches=2, seed=75)
	sh.glass("glass", GLASS, highlight=70)
	sh.ridges("butt", RUBBER, (26, 26, 30), period=2, groove_w=1, horizontal=True)
	sh.fill("vent", (50, 53, 60))
	sh.vents("vent", (22, 22, 26), 5, slot_w=1, margin=1, vertical=True)
	sh.outline("vent", (78, 82, 90))
	metal(sh, "fins", (112, 118, 126), along=False, pits=1, scratches=0, seed=76, bevel=False)

	side, top, shroud, barrel = sh.paint("body_side"), sh.paint("body_top"), sh.paint("shroud"), sh.paint("barrel")
	muzzle, wood, edge, grip = sh.paint("muzzle"), sh.paint("wood"), sh.paint("wood_edge"), sh.paint("grip")
	dark, glow, cell, rail = sh.paint("dark"), sh.paint("energy", "fit"), sh.paint("cell"), sh.paint("rail")
	brass, steel, red, mouth = sh.paint("brass"), sh.paint("steel"), sh.paint("accent", "fit"), sh.paint("bore", "fit")
	scope, glass, butt, vent = sh.paint("scope"), sh.paint("glass", "fit"), sh.paint("butt"), sh.paint("vent", "fit")
	fins = sh.paint("fins")

	els = []
	# The vented muzzle with its bore, the barrel with three cooling fins, the slotted shroud.
	els += tube(8, 9.75, -11.0, -9.9, 1.0, muzzle, front=True, back=True, cap=dark, name="muzzle")
	els.append(box(7.55, 9.3, -11.06, 8.45, 10.2, -11.0, mouth, faces=("north",), name="muzzle_bore"))
	els += tube(8, 9.75, -9.95, -6.15, 0.62, barrel, name="barrel")
	for i, z in enumerate((-9.2, -8.5, -7.8)):
		els.append(box(7.12, 8.87, z, 8.88, 10.63, z + 0.3, fins, name=f"barrel_fin_{i}"))
	els.append(box(6.9, 8.6, -6.2, 9.1, 10.9, -1.95, shroud, paints={"north": dark}, skip=("south",), name="shroud"))
	# The receiver, its rail, the scope on two mounts.
	els.append(box(6.75, 8.4, -2.0, 9.25, 11.1, 12.6, side, paints={"up": top, "down": dark}, name="receiver"))
	els.append(box(7.3, 11.1, -1.6, 8.7, 11.5, 10.6, rail, skip=("down",), name="top_rail"))
	els.append(box(7.55, 11.5, 1.6, 8.45, 12.05, 2.3, steel, skip=("down",), name="scope_mount_front"))
	els.append(box(7.55, 11.5, 5.0, 8.45, 12.05, 5.7, steel, skip=("down",), name="scope_mount_back"))
	els += tube(8, 12.7, 0.8, 6.6, 0.7, scope, front=True, back=True, cap=dark, name="scope")
	els.append(box(7.5, 12.2, 0.74, 8.5, 13.2, 0.8, glass, faces=("north",), light=4, name="scope_lens"))
	els += tube(8, 12.7, 0.3, 0.85, 0.84, scope, back=True, name="scope_hood")
	# The red power cell on the right between brass clamps, its lit window facing out.
	els.append(box(9.25, 8.75, 1.6, 10.1, 10.75, 6.4, cell, skip=("west",), name="cell"))
	els.append(box(9.25, 8.65, 1.2, 10.2, 10.85, 1.65, brass, skip=("west",), name="cell_clamp_front"))
	els.append(box(9.25, 8.65, 6.35, 10.2, 10.85, 6.8, brass, skip=("west",), name="cell_clamp_back"))
	els.append(box(10.1, 9.35, 2.4, 10.16, 10.15, 5.6, glow, faces=("east",), light=12, name="cell_window"))
	# The left side: the lit charge strip, a vent grille, the selector and the charging knob.
	els.append(box(6.69, 10.35, 0.6, 6.75, 10.75, 4.6, glow, faces=("west",), light=12, name="charge_strip"))
	els.append(box(6.69, 8.9, 5.6, 6.75, 10.1, 8.8, vent, faces=("west",), name="vent_grille"))
	els.append(box(6.65, 8.75, 9.9, 6.75, 9.25, 10.8, red, faces=("west",), name="selector"))
	els.append(box(6.2, 9.85, 10.9, 6.75, 10.45, 11.7, steel, skip=("east",), name="charging_knob"))
	# The handguard, the trigger guard and trigger, the raked grip with a dark base.
	els.append(box(7.1, 7.3, -1.8, 8.9, 8.45, 2.6, wood, paints={"north": edge, "south": edge}, skip=("up",), name="handguard"))
	els.append(box(7.6, 6.9, 5.4, 8.4, 7.3, 7.6, dark, name="guard"))
	els.append(box(7.6, 7.3, 5.4, 8.4, 8.4, 5.8, dark, skip=("up",), name="guard_front"))
	els.append(box(7.78, 7.3, 6.3, 8.22, 8.4, 6.75, steel, skip=("up",), name="trigger"))
	grip_els = [
		box(7.15, 4.45, 7.05, 8.85, 8.45, 9.65, grip, paints={"north": edge, "south": edge}, skip=("up",), name="grip"),
		box(7.05, 4.0, 6.9, 8.95, 4.5, 9.8, dark, name="grip_base"),
	]
	els += raked(grip_els, 12.0, (8.0, 8.4, 8.4))
	# The stock: a solid body with a raised comb and a ribbed rubber butt.
	els.append(box(7.05, 7.6, 12.55, 8.95, 10.65, 18.3, wood, paints={"south": edge}, skip=("north",), name="stock"))
	els.append(box(7.15, 10.65, 13.6, 8.85, 11.05, 17.4, edge, skip=("down",), name="stock_comb"))
	els.append(box(6.95, 7.3, 18.25, 9.05, 10.95, 19.0, butt, name="butt"))
	return Gun("blaster_rifle", 0.6, 0.6, (0, 1, -2), (8.0, 9.75, -11.0), sh, els)


def heavy_repeater():
	"""The heavy repeater (Milestone 4, rebuilt): a rotary blaster. Three barrels in a cluster, held by a dark front
	clamp and a brass middle one, running back into a slotted bearing housing on the motor receiver; a loop carry
	handle; the red power canister clamped on the right with a lit gauge; a heat strip, vent and selector on the left;
	a folded bipod under the barrels; a rubber foregrip, a raked brown grip and a heavy stock with a rubber butt. The
	grip, stock, scale and muzzle (the cluster's centre) are the Milestone 4 model's."""
	sh = Sheet("#gun", seed=89)
	sh.region("body_side", 0, 0, 48, 14)
	sh.region("body_top", 48, 0, 16, 48)
	sh.region("barrel", 0, 14, 24, 6)
	sh.region("housing", 24, 14, 24, 10)
	sh.region("clamp", 0, 20, 12, 6)
	sh.region("brass", 12, 20, 12, 6)
	sh.region("cell", 0, 26, 24, 14)
	sh.region("wood", 24, 24, 20, 14)
	sh.region("wood_edge", 44, 24, 4, 14)
	sh.region("grip", 0, 40, 12, 16)
	sh.region("rubber", 12, 40, 12, 12)
	sh.region("vent", 24, 38, 16, 10)
	sh.region("dark", 40, 38, 8, 4)
	sh.region("energy", 40, 42, 4, 8)
	sh.region("lamp", 44, 42, 4, 8)
	sh.region("steel", 24, 48, 16, 6)
	sh.region("accent", 40, 50, 8, 4)
	sh.region("bore", 48, 48, 4, 4)
	sh.region("butt", 48, 52, 16, 6)
	sh.region("handle", 0, 56, 24, 4)

	metal(sh, "body_side", (60, 63, 70), seed=91)
	seams(sh, "body_side", (12, 30, 41))
	sh.rivets("body_side", ((3, 4), (3, 10), (44, 4), (44, 10), (21, 11)))
	metal(sh, "body_top", (78, 82, 90), along=False, seed=92)
	sh.vents("body_top", (40, 42, 47), 3, slot_w=1, margin=5, vertical=False)
	metal(sh, "barrel", STEEL, along=False, pits=2, scratches=1, seed=93, bevel=False)
	slots(sh, "housing", (72, 76, 84), period=4, slot_w=2, slot_h=1, seed=94)
	sh.bevel("housing", light=18, dark=16)
	metal(sh, "clamp", (54, 57, 64), along=False, pits=1, scratches=1, seed=95, bevel=False)
	metal(sh, "brass", BRASS, spread=8, pits=2, scratches=1, along=False, seed=96, bevel=False)
	sh.brushed("cell", RED_PAINT, 6, vertical=False, seed=97)
	chips(sh, "cell", (104, 108, 116), count=12, seed=97)
	sh.ridges("cell", RED_PAINT, (112, 32, 26), period=4, groove_w=1, horizontal=True)
	sh.noise("cell", 5, seed=98)
	sh.bevel("cell", light=26, dark=24)
	grain(sh, "wood", BROWN, BROWN_DARK, seed=99)
	sh.brushed("wood_edge", BROWN_DARK, 5, vertical=True, seed=100)
	sh.knurl("grip", BROWN, BROWN_DARK, period=3)
	sh.noise("grip", 4, seed=101)
	sh.bevel("grip", light=10, dark=14)
	sh.ridges("rubber", RUBBER, (28, 28, 32), period=3, groove_w=1, highlight=(58, 58, 64), horizontal=True)
	sh.noise("rubber", 3, seed=102)
	sh.fill("vent", (48, 51, 58))
	sh.vents("vent", (20, 20, 24), 5, slot_w=1, margin=1, vertical=True)
	sh.outline("vent", (78, 82, 90))
	solid(sh, "dark", BLACK, seed=103)
	energy(sh, "energy")
	energy(sh, "lamp", core=AMBER, rim=AMBER_DARK, hot=(255, 236, 200))
	metal(sh, "steel", STEEL_LIGHT, pits=1, scratches=1, seed=104, bevel=False)
	solid(sh, "accent", RED, seed=105)
	bore(sh, "bore", rim=(96, 100, 108))
	sh.ridges("butt", RUBBER, (26, 26, 30), period=2, groove_w=1, horizontal=True)
	sh.ridges("handle", RUBBER, (28, 28, 32), period=2, groove_w=1, horizontal=False)

	side, top, barrel, housing = sh.paint("body_side"), sh.paint("body_top"), sh.paint("barrel"), sh.paint("housing")
	clamp, brass, cell, wood = sh.paint("clamp"), sh.paint("brass"), sh.paint("cell"), sh.paint("wood")
	edge, grip, rubber, vent = sh.paint("wood_edge"), sh.paint("grip"), sh.paint("rubber"), sh.paint("vent", "fit")
	dark, glow, lamp, steel = sh.paint("dark"), sh.paint("energy", "fit"), sh.paint("lamp", "fit"), sh.paint("steel")
	red, mouth, butt, handle = sh.paint("accent", "fit"), sh.paint("bore", "fit"), sh.paint("butt"), sh.paint("handle")

	axis_y = 9.875
	els = []
	# The barrel cluster round the axis (8, 9.875): one barrel on top, two below; their mouths are the muzzle.
	for name, (cx, cy) in (("barrel_top", (8.0, axis_y + 1.0)), ("barrel_left", (7.134, axis_y - 0.5)),
			("barrel_right", (8.866, axis_y - 0.5))):
		els.append(box(cx - 0.45, cy - 0.45, -12.0, cx + 0.45, cy + 0.45, -2.95, barrel, paints={"north": mouth},
				skip=("south",), name=name))
	els += tube(8, axis_y, -11.25, -10.65, 1.72, clamp, front=True, back=True, name="clamp_front")
	els += tube(8, axis_y, -7.5, -6.9, 1.72, brass, front=True, back=True, name="clamp_middle")
	els += tube(8, axis_y, -3.0, -1.0, 1.95, housing, front=True, cap=dark, name="bearing_housing")
	# The folded bipod under the barrels.
	els.append(box(6.85, 7.5, -3.7, 9.15, 8.2, -3.0, dark, skip=("south",), name="bipod_hinge"))
	els.append(box(6.95, 7.55, -10.3, 7.45, 7.95, -3.7, steel, skip=("south",), name="bipod_leg_left"))
	els.append(box(8.55, 7.55, -10.3, 9.05, 7.95, -3.7, steel, skip=("south",), name="bipod_leg_right"))
	# The motor receiver, its vents, the loop carry handle.
	els.append(box(6.3, 7.9, -1.05, 9.7, 11.6, 11.8, side, paints={"up": top, "down": dark}, skip=("north",), name="receiver"))
	els.append(box(6.24, 8.5, -0.4, 6.3, 11.0, 3.4, vent, faces=("west",), name="vent_left"))
	els.append(box(9.7, 8.5, -0.6, 9.76, 11.0, 1.9, vent, faces=("east",), name="vent_right"))
	els.append(box(7.55, 11.6, 1.3, 8.45, 12.9, 2.0, steel, skip=("down", "up"), name="handle_post_front"))
	els.append(box(7.55, 11.6, 7.0, 8.45, 12.9, 7.7, steel, skip=("down", "up"), name="handle_post_back"))
	els.append(box(7.45, 12.9, 0.9, 8.55, 13.5, 8.1, handle, paints={"north": steel, "south": steel}, name="handle_bar"))
	# The red power canister on the right between brass clamps, its lit gauge and the hose into the receiver.
	els.append(box(9.7, 7.3, 2.4, 11.2, 10.7, 7.6, cell, skip=("west",), name="canister"))
	els.append(box(9.7, 7.2, 3.1, 11.3, 10.8, 3.6, brass, skip=("west",), name="canister_clamp_front"))
	els.append(box(9.7, 7.2, 6.4, 11.3, 10.8, 6.9, brass, skip=("west",), name="canister_clamp_back"))
	els.append(box(11.2, 8.0, 4.0, 11.26, 10.0, 6.0, glow, faces=("east",), light=12, name="canister_gauge"))
	els.append(box(9.7, 10.7, 4.4, 10.3, 11.3, 5.4, rubber, skip=("west", "down"), name="canister_hose"))
	# The left side: the heat strip and the selector.
	els.append(box(6.24, 10.35, 4.4, 6.3, 10.9, 8.6, lamp, faces=("west",), light=12, name="heat_strip"))
	els.append(box(6.2, 8.4, 9.9, 6.3, 8.9, 10.8, red, faces=("west",), name="selector"))
	# The rubber foregrip, the trigger guard and trigger, the raked grip.
	els.append(box(7.25, 5.3, -0.2, 8.75, 7.95, 2.0, rubber, paints={"down": dark}, skip=("up",), name="foregrip"))
	els.append(box(7.6, 6.85, 5.6, 8.4, 7.25, 7.8, dark, name="guard"))
	els.append(box(7.6, 7.25, 5.6, 8.4, 7.95, 6.0, dark, skip=("up",), name="guard_front"))
	els.append(box(7.78, 7.25, 6.5, 8.22, 7.95, 6.95, steel, skip=("up",), name="trigger"))
	grip_els = [
		box(7.15, 4.1, 7.85, 8.85, 7.95, 10.4, grip, paints={"north": edge, "south": edge}, skip=("up",), name="grip"),
		box(7.05, 3.65, 7.7, 8.95, 4.15, 10.55, dark, name="grip_base"),
	]
	els += raked(grip_els, 12.0, (8.0, 7.9, 9.1))
	# The heavy stock and its butt.
	els.append(box(6.9, 7.5, 11.75, 9.1, 10.9, 16.2, wood, paints={"south": edge}, skip=("north",), name="stock"))
	els.append(box(6.8, 7.3, 16.15, 9.2, 11.1, 16.9, butt, name="butt"))
	return Gun("heavy_repeater", 0.6, 0.6, (0, 1, -2), (8.0, axis_y, -12.0), sh, els)


def kaast_longshot():
	"""Kaast's Longshot (Milestone 4, rebuilt): a named masterwork rifle with a fixed heavy barrel. A ported muzzle
	brake, a long fluted barrel with a brass band and a slotted trunnion sleeve, a warm grey receiver, a long scope
	in brass rings with turrets and a red-tinted objective, a polished brass bolt knob on the right, an amber charge
	lamp on the left, and lacquered crimson furniture with brass inlays: a fore-end, a raked grip and a thumbhole stock
	with a cheek rest. The grip, stock, scale and muzzle are the Milestone 4 model's."""
	sh = Sheet("#gun", seed=113)
	sh.region("body_side", 0, 0, 48, 12)
	sh.region("body_top", 48, 0, 16, 40)
	sh.region("barrel", 0, 12, 24, 8)
	sh.region("brake", 24, 12, 12, 10)
	sh.region("muzzle_face", 36, 12, 8, 8)
	sh.region("sleeve", 0, 20, 24, 8)
	sh.region("crimson", 0, 28, 24, 14)
	sh.region("crimson_edge", 24, 22, 8, 16)
	sh.region("grip", 32, 22, 12, 16)
	sh.region("brass", 44, 20, 4, 10)
	sh.region("dark", 44, 30, 4, 6)
	sh.region("lamp", 44, 36, 4, 6)
	sh.region("scope", 0, 42, 24, 8)
	sh.region("glass", 24, 38, 8, 8)
	sh.region("steel", 32, 38, 12, 6)
	sh.region("rail", 48, 40, 16, 6)
	sh.region("butt", 24, 46, 24, 6)
	sh.region("rubber", 0, 50, 12, 6)

	metal(sh, "body_side", (84, 80, 74), seed=115)
	seams(sh, "body_side", (11, 29))
	sh.rivets("body_side", ((3, 6), (44, 6)))
	metal(sh, "body_top", (104, 100, 93), along=False, seed=116)
	metal(sh, "barrel", (60, 63, 70), along=False, pits=1, scratches=1, seed=117, bevel=False)
	sh.ridges("barrel", (66, 69, 76), (40, 42, 47), period=3, groove_w=1, highlight=(92, 96, 104), horizontal=False)
	metal(sh, "brake", (70, 74, 82), along=False, pits=1, scratches=1, seed=118, bevel=True)
	sh.vents("brake", BLACK, 3, slot_w=1, margin=2, vertical=False)
	sh.fill("muzzle_face", (64, 68, 76))
	sh.outline("muzzle_face", (88, 92, 100))
	sh.rect(38, 14, 41, 17, (30, 30, 34))
	sh.rect(39, 15, 40, 16, (12, 12, 15))
	slots(sh, "sleeve", (76, 72, 66), period=4, slot_w=2, slot_h=1, seed=119)
	sh.brushed("crimson", CRIMSON, 6, vertical=False, seed=120)
	sh.wear("crimson", pits=2, scratches=2, amount=14, seed=120)
	sh.bevel("crimson", light=22, dark=18)
	sh.brushed("crimson_edge", CRIMSON_DARK, 5, vertical=True, seed=121)
	sh.knurl("grip", CRIMSON, CRIMSON_DARK, period=3)
	sh.noise("grip", 4, seed=122)
	sh.bevel("grip", light=12, dark=14)
	metal(sh, "brass", (196, 162, 92), spread=8, pits=1, scratches=1, along=False, seed=123, bevel=False)
	solid(sh, "dark", BLACK, seed=124)
	energy(sh, "lamp", core=AMBER, rim=AMBER_DARK, hot=(255, 236, 200))
	metal(sh, "scope", (40, 40, 44), along=True, pits=2, scratches=2, seed=125)
	sh.glass("glass", (176, 64, 72), highlight=70)
	metal(sh, "steel", STEEL_LIGHT, pits=1, scratches=1, seed=126, bevel=False)
	metal(sh, "rail", (46, 49, 55), pits=1, scratches=1, seed=127, bevel=False)
	sh.vents("rail", (26, 27, 31), 5, slot_w=1, margin=0, vertical=True)
	sh.ridges("butt", RUBBER, (26, 26, 30), period=2, groove_w=1, horizontal=True)
	solid(sh, "rubber", RUBBER, seed=128)

	side, top, barrel, brake = sh.paint("body_side"), sh.paint("body_top"), sh.paint("barrel"), sh.paint("brake")
	face, sleeve, crimson, edge = sh.paint("muzzle_face", "fit"), sh.paint("sleeve"), sh.paint("crimson"), sh.paint("crimson_edge")
	grip, brass, dark, lamp = sh.paint("grip"), sh.paint("brass"), sh.paint("dark"), sh.paint("lamp", "fit")
	scope, glass, steel, rail = sh.paint("scope"), sh.paint("glass", "fit"), sh.paint("steel"), sh.paint("rail")
	butt, rubber = sh.paint("butt"), sh.paint("rubber")

	els = []
	# The ported brake, the fluted barrel with its brass band, the slotted trunnion sleeve.
	els.append(box(6.85, 8.6, -16.0, 9.15, 10.9, -13.7, brake, paints={"north": face, "up": dark, "down": dark}, name="brake"))
	els += tube(8, 9.75, -13.75, -4.4, 0.78, barrel, name="barrel")
	els += tube(8, 9.75, -9.3, -8.6, 0.98, brass, front=True, back=True, name="barrel_band")
	els += tube(8, 9.75, -4.45, -1.95, 1.08, sleeve, front=True, cap=dark, name="sleeve")
	# The receiver and its rail; the scope: objective bell with its lens, tube, eyepiece, brass rings, turrets.
	els.append(box(6.75, 8.4, -2.0, 9.25, 11.0, 12.4, side, paints={"up": top, "down": dark}, name="receiver"))
	els.append(box(7.3, 11.0, -1.6, 8.7, 11.4, 9.8, rail, skip=("down",), name="top_rail"))
	els += tube(8, 12.75, -2.0, -0.4, 1.12, scope, front=True, back=True, cap=dark, name="scope_objective")
	els.append(box(7.22, 11.97, -2.06, 8.78, 13.53, -2.0, glass, faces=("north",), light=6, name="scope_lens"))
	els += tube(8, 12.75, -0.45, 6.6, 0.8, scope, name="scope_tube")
	els += tube(8, 12.75, 6.55, 8.4, 0.95, scope, front=True, back=True, cap=dark, name="scope_eyepiece")
	for i, z in enumerate((0.6, 5.0)):
		els.append(box(7.1, 11.85, z, 8.9, 13.65, z + 0.55, brass, name=f"scope_ring_{i}"))
		els.append(box(7.6, 11.4, z, 8.4, 11.9, z + 0.55, steel, skip=("up", "down"), name=f"scope_mount_{i}"))
	els.append(box(7.55, 13.5, 2.6, 8.45, 14.1, 3.5, steel, skip=("down",), name="turret_top"))
	els.append(box(6.6, 12.3, 2.6, 7.25, 13.2, 3.5, steel, skip=("east",), name="turret_left"))
	# The amber charge lamp on the left, the brass bolt knob on the right.
	els.append(box(6.69, 10.2, 0.6, 6.75, 10.6, 4.6, lamp, faces=("west",), light=12, name="charge_lamp"))
	els.append(box(9.25, 9.6, 8.6, 9.95, 10.1, 9.2, steel, skip=("west",), name="bolt_handle"))
	els.append(box(9.95, 9.45, 8.5, 10.45, 10.25, 9.3, brass, name="bolt_knob"))
	# The crimson fore-end with a brass tip, the trigger guard and the brass trigger, the raked grip.
	els.append(box(7.05, 7.6, -2.0, 8.95, 8.45, 3.2, crimson, paints={"south": edge}, skip=("up", "north"), name="fore_end"))
	els.append(box(7.05, 7.6, -3.7, 8.95, 8.45, -2.0, crimson, paints={"north": brass}, skip=("south",), name="fore_end_tip"))
	els.append(box(7.6, 6.9, 5.3, 8.4, 7.3, 7.5, dark, name="guard"))
	els.append(box(7.6, 7.3, 5.3, 8.4, 8.4, 5.7, dark, skip=("up",), name="guard_front"))
	els.append(box(7.78, 7.3, 6.2, 8.22, 8.4, 6.65, brass, skip=("up",), name="trigger"))
	grip_els = [
		box(7.15, 4.3, 7.0, 8.85, 8.45, 9.6, grip, paints={"north": edge, "south": edge}, skip=("up",), name="grip"),
		box(7.05, 3.85, 6.85, 8.95, 4.35, 9.75, brass, name="grip_base"),
	]
	els += raked(grip_els, 14.0, (8.0, 8.4, 8.5))
	# The thumbhole stock: the comb, the lower stock and the link back from the grip; the cheek rest, the inlays, the butt.
	els.append(box(7.05, 9.3, 12.35, 8.95, 10.95, 18.4, crimson, skip=("north",), name="stock_comb"))
	els.append(box(7.05, 6.9, 14.6, 8.95, 9.35, 18.4, crimson, paints={"north": edge}, skip=("up",), name="stock_lower"))
	els.append(box(7.2, 6.9, 9.85, 8.8, 7.5, 14.65, edge, skip=("south",), name="stock_link"))
	els.append(box(7.1, 10.95, 13.2, 8.9, 11.5, 17.2, crimson, paints={"south": brass}, skip=("down",), name="cheek_rest"))
	els.append(box(6.99, 9.9, 13.0, 7.05, 10.2, 17.8, brass, faces=("west",), name="inlay_left"))
	els.append(box(8.95, 9.9, 13.0, 9.01, 10.2, 17.8, brass, faces=("east",), name="inlay_right"))
	els.append(box(6.95, 6.7, 18.35, 9.05, 11.15, 19.0, butt, paints={"north": rubber}, name="butt"))
	return Gun("kaast_longshot", 0.6, 0.6, (0, 1, -2), (8.0, 9.75, -16.0), sh, els)


GUNS = (pistol, blaster_rifle, heavy_repeater, kaast_longshot, scatter_blaster, blaster_carbine)


# ------------------------------------------------------------------ the 16x16 icons

ICON_PALETTE = {
	"k": BLACK,
	"d": (46, 49, 56),
	"m": (74, 79, 87),
	"l": (110, 116, 124),
	"h": (152, 158, 166),
	"w": (40, 40, 45),
	"W": (66, 66, 72),
	"r": (178, 92, 40),
	"R": (222, 142, 72),
	"b": (176, 146, 82),
	"B": (214, 186, 120),
	"g": (92, 232, 128),
	"v": (94, 98, 70),
	"V": (124, 128, 92),
	"s": (110, 176, 220),
	"a": (232, 170, 72),
	"A": (249, 214, 134),
	"n": (205, 143, 58),
	"N": (176, 116, 44),
	"Y": (255, 247, 221),
	"p": (28, 22, 34),
	"P": (50, 40, 58),
	"q": (78, 66, 92),
	"Q": (116, 102, 138),
	"x": (112, 36, 42),
	"X": (204, 196, 212),
}
# Palette keys drawn without noise: lit or glass texels stay clean.
ICON_CLEAN = "gsYX"


class Pixels:
	"""A 16x16 fill map drawn with primitives (palette keys); icon() colours it and outlines it."""

	def __init__(self):
		self.cells = {}

	def px(self, x, y, k):
		self.cells[(x, y)] = k

	def rect(self, x0, y0, x1, y1, k):
		"""Fills the inclusive rectangle."""
		for y in range(y0, y1 + 1):
			for x in range(x0, x1 + 1):
				self.px(x, y, k)

	def hline(self, x0, x1, y, k):
		self.rect(x0, y, x1, y, k)

	def vline(self, x, y0, y1, k):
		self.rect(x, y0, x, y1, k)

	def rows(self):
		return tuple("".join(self.cells.get((x, y), ".") for x in range(16)) for y in range(16))


def scatter_icon():
	"""The scatter blaster side on, muzzle right: rubber butt and skeletal stock, the heavy receiver with its top fins,
	lit cell and ribbed fore-end, the drum slung under it, the raked grip, and the orange bell flaring to its mouth."""
	p = Pixels()
	p.vline(1, 5, 10, "w")                 # the butt
	p.hline(2, 3, 6, "d")                  # the stock's two rails
	p.hline(2, 3, 9, "d")
	p.rect(4, 5, 10, 9, "m")               # the receiver
	p.hline(4, 10, 5, "l")
	p.hline(4, 10, 9, "d")
	for x in (5, 7, 9):                    # the top fins
		p.px(x, 4, "h")
	p.px(5, 7, "d")                        # the charging handle's slot
	p.px(8, 7, "g")                        # the cell lamp
	p.rect(9, 8, 11, 10, "W")              # the ribbed fore-end
	p.px(10, 8, "w")
	p.px(10, 10, "w")
	p.rect(6, 10, 8, 11, "v")              # the drum, its brass hub
	p.hline(6, 8, 12, "v")
	p.px(7, 11, "b")
	p.vline(5, 10, 11, "w")                # the grip, raked back
	p.vline(4, 12, 13, "w")
	p.hline(11, 11, 6, "l")                # the neck
	p.hline(11, 11, 7, "h")
	p.vline(12, 5, 8, "r")                 # the bell: base, rib, lip
	p.vline(13, 4, 9, "R")
	p.vline(14, 3, 10, "r")
	p.px(14, 3, "R")
	p.px(13, 6, "k")                       # a groove
	return p.rows()


def carbine_icon():
	"""The carbine side on, muzzle right: olive butt on a two-bar stock, the receiver with its scope, lit side cell and
	red selector, the perforated shroud and compensator, the forward-raked pack, the olive grip and foregrip."""
	p = Pixels()
	p.vline(1, 6, 10, "V")                 # the butt
	p.hline(2, 3, 7, "l")                  # the stock's bar and strut
	p.hline(2, 3, 9, "l")
	p.rect(4, 6, 9, 8, "m")                # the receiver
	p.hline(4, 9, 6, "l")
	p.hline(4, 9, 8, "d")
	p.hline(5, 8, 4, "k")                  # the scope on its mounts, lens forward
	p.px(9, 4, "s")
	p.px(6, 5, "d")
	p.px(8, 5, "d")
	p.px(7, 7, "g")                        # the side cell's lit strip
	p.rect(10, 6, 12, 8, "d")              # the shroud with its holes
	p.px(10, 7, "k")
	p.px(12, 7, "k")
	p.px(11, 6, "m")
	p.px(11, 8, "m")
	p.px(13, 7, "l")                       # barrel and compensator
	p.vline(14, 6, 8, "m")
	p.px(14, 7, "k")
	p.rect(7, 9, 8, 10, "d")               # the power pack, a brass foot
	p.hline(8, 9, 11, "d")
	p.hline(8, 9, 12, "b")
	p.vline(5, 9, 10, "v")                 # the grip, raked back
	p.vline(4, 11, 12, "v")
	p.vline(11, 9, 10, "v")                # the foregrip
	return p.rows()


def aurelite_icon():
	"""The Aurelite Barrel: a barrel of golden aurelite crystal between brass collars, a bright lens at its muzzle."""
	p = Pixels()
	p.rect(2, 5, 3, 9, "b")                # the rear collar
	p.px(3, 5, "B")
	p.rect(4, 6, 10, 8, "a")               # the crystal
	p.hline(4, 10, 6, "A")
	p.hline(4, 10, 8, "N")
	for x in (5, 8):                       # facets
		p.px(x, 7, "n")
	p.px(6, 6, "Y")
	p.rect(11, 5, 12, 9, "b")              # the front collar
	p.px(12, 5, "B")
	p.vline(13, 6, 8, "s")                 # the lens
	p.px(13, 7, "Y")
	return p.rows()


def duskglass_icon():
	"""The Duskglass Cell: a brass-capped cell of dusky violet glass, a lit facet running across it, red glints."""
	p = Pixels()
	p.hline(6, 9, 3, "b")                  # the cap
	p.px(7, 3, "B")
	p.rect(4, 5, 11, 11, "P")              # the glass
	p.hline(4, 11, 11, "p")
	p.vline(11, 5, 11, "p")
	for i in range(7):                     # a facet running down to the right
		x, y = 4 + i, 5 + i
		for dx, k in ((-1, "p"), (0, "q"), (1, "Q"), (2, "q")):
			if 4 <= x + dx <= 11:
				p.px(x + dx, y, k)
	p.px(10, 6, "x")                       # glints
	p.px(5, 9, "x")
	p.px(9, 10, "x")
	p.px(6, 6, "X")
	return p.rows()


def icon(rows, seed):
	"""A 16x16 RGBA icon from a fill map: every texel coloured from ICON_PALETTE with a little noise, then a one-texel
	dark outline around the filled shape (as the Milestone 4 mod and repeater icons have)."""
	from PIL import Image
	rng = random.Random(seed)
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	filled = set()
	for y, row in enumerate(rows):
		for x, ch in enumerate(row):
			if ch == ".":
				continue
			base = ICON_PALETTE[ch]
			n = 0 if ch in ICON_CLEAN else rng.uniform(-5, 5)
			img.putpixel((x, y), tuple(max(0, min(255, int(round(c + n)))) for c in base) + (255,))
			filled.add((x, y))
	for x, y in list(filled):
		for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
			nx, ny = x + dx, y + dy
			if 0 <= nx < 16 and 0 <= ny < 16 and (nx, ny) not in filled:
				img.putpixel((nx, ny), OUTLINE + (255,))
	return img


ICONS = {
	"scatter_blaster": (scatter_icon, 101),
	"blaster_carbine": (carbine_icon, 102),
	"mod_aurelite_barrel": (aurelite_icon, 103),
	"mod_duskglass_cell": (duskglass_icon, 104),
}


# ------------------------------------------------------------------ item definitions and models

def held_definition(id):
	"""The flat icon in the inventory, on the ground, in frames and on shelves; the 3D model everywhere else."""
	return {
		"model": {
			"type": "minecraft:select",
			"property": "minecraft:display_context",
			"cases": [{"when": ["gui", "ground", "fixed", "on_shelf"], "model": {"type": "minecraft:model", "model": f"{NS}:item/{id}"}}],
			"fallback": {"type": "minecraft:model", "model": f"{NS}:item/{id}_in_hand"},
		}
	}


def flat_definition(id):
	return {"model": {"type": "minecraft:model", "model": f"{NS}:item/{id}"}}


def flat_model(id):
	return {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{id}"}}


def write_json(path, data, written):
	os.makedirs(os.path.dirname(path), exist_ok=True)
	with open(path, "w", encoding="utf-8") as f:
		json.dump(data, f, indent=2)
		f.write("\n")
	written.append(path)


def build():
	"""Every gun built (elements and painted sheet), and the icons."""
	guns = [make() for make in GUNS]
	icons = {id: icon(draw(), seed) for id, (draw, seed) in ICONS.items()}
	return guns, icons


def write(out, guns, icons):
	written = []
	for gun in guns:
		write_json(os.path.join(out, "models", "item", f"{gun.id}_in_hand.json"), gun.model_json(), written)
		path = os.path.join(out, "textures", "item", f"{gun.id}_model.png")
		gun.sheet.save(path)
		written.append(path)
		write_json(os.path.join(out, "items", f"{gun.id}.json"), held_definition(gun.id), written)
	for id, img in icons.items():
		path = os.path.join(out, "textures", "item", f"{id}.png")
		os.makedirs(os.path.dirname(path), exist_ok=True)
		img.save(path)
		written.append(path)
		write_json(os.path.join(out, "models", "item", f"{id}.json"), flat_model(id), written)
		if id.startswith("mod_"):
			write_json(os.path.join(out, "items", f"{id}.json"), flat_definition(id), written)
	return written


def main():
	parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
	parser.add_argument("--out", help="an assets/oldrepublic tree to write into instead of the repository")
	parser.add_argument("--check", action="store_true", help="build and check without writing")
	a = parser.parse_args()

	guns, icons = build()
	problems = [p for gun in guns for p in gun.problems()]
	for gun in guns:
		x, y, z = gun.frame_muzzle()
		print(f"{gun.id}: {len(gun.elements)} elements, scale {gun.scale}, muzzle {gun.muzzle} -> held frame "
				f"new Vec3({x:.4f}, {y:.4f}, {z:.4f})")
	if problems:
		for p in problems:
			print("PROBLEM:", p)
		sys.exit(1)
	if a.check:
		return
	written = write(a.out or ASSETS, guns, icons)
	print(f"wrote {len(written)} files under {a.out or ASSETS}")


if __name__ == "__main__":
	main()
