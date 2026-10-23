#!/usr/bin/env python3
"""Generates the saber feature's flat icons, crystals, lenses, workbench art and particles (PLACEHOLDER art, original,
deterministic).

Since Milestone 7 the hilts are tools/assets/hilts/build.py's (docs/MILESTONE_7.md 10.2): every hilt part model
(models/item/lightsaber/**), the base models (lightsaber/base.json, staff_base.json), the item definitions of the sabers
(lightsaber, lightsaber_staff, training_saber, training_saber_red) and of every hilt part, the part items' models and
icons (models/item/<part>.json, textures/item/<part>.png). This script no longer writes any of them, nor the old hilt
swatches (item/lightsaber_hilt.png, item/lightsaber_hilt_legendary.png: build.py --prune deletes them once no model
uses them).

Textures (src/main/resources/assets/oldrepublic/textures/):
  item/lightsaber_icon_hilt.png     16x16 flat inventory hilt (diagonal), build.py --flat-icons
  item/lightsaber_icon_blade.png    16x16 flat inventory blade, white (tinted with the blade colour)
  item/kyber_crystal.png            16x16 crystal body, light greys (tinted with its natural colour)
  item/kyber_crystal_glint.png      16x16 white highlight layer (untinted)
  item/singing_crystal.png          16x16 the singing crystal, amber (final art for M3; untinted)
  block/lightsaber_workbench_*.png  16x16 workbench faces (top, side, front, metal, lamp)
  gui/container/lightsaber_workbench.png  230x200 workbench screen
  particle/saber_spark_0..1.png, particle/contact_flash.png  8x8, white (tinted in code)
  particle/blade_glint_0..1.png, particle/lunge_streak.png  8x8, white (tinted in code)
  Milestone 4 (W5, spec MILESTONE_4.md 5.5-5.7):
  item/lightsaber_staff_icon_hilt.png, item/lightsaber_staff_icon_blade.png  16x16 flat staff icon (blade white, tinted)
  item/training_saber_icon_hilt.png 16x16 the training saber's flat hilt icon (blade: lightsaber_icon_blade)
  item/lens_honed.png, lens_steady.png, lens_resonant.png  16x16 saber lenses
  gui/sprites/bench/hint_lens.png   16x16 ghost silhouette for the workbench's empty lens socket
  Milestone 5 (W7, spec MILESTONE_5.md 11.2):
  item/lens_stormglass.png          16x16 the Stormglass Lens

JSON (src/main/resources/assets/oldrepublic/):
  items/kyber_crystal.json, items/singing_crystal.json (flat icon in GUIs, a small glowing prism elsewhere),
  items/lightsaber_workbench.json, items/lens_*.json
  models/item/{lightsaber,lightsaber_staff,training_saber}_icon{,_lit}.json (the flat icons build.py --flat-icons
  uses), models/item/kyber_crystal*.json, singing_crystal*.json, lens_*.json
  models/block/lightsaber_workbench.json, blockstates/lightsaber_workbench.json
  particles/saber_spark.json, particles/contact_flash.json, particles/blade_glint.json,
  particles/lunge_streak.json

Run from anywhere: python3 tools/assets/gen_textures_saber.py [--out DIR]
With --out the files go under DIR (laid out like the assets folder) instead, for a dry run or a comparison.
"""
import argparse
import json
import math
import os
import random

from PIL import Image, ImageDraw

ASSETS = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "assets", "oldrepublic"))
# Where the files go: ASSETS, or the folder --out names.
OUT = ASSETS
NS = "oldrepublic"

# Muted, worn Old Republic palette.
STEEL = (150, 156, 162)
STEEL_DARK = (86, 92, 99)
STEEL_LIGHT = (196, 202, 208)
GRIP_DARK = (44, 40, 38)
LEATHER = (92, 66, 46)
BRASS = (170, 140, 86)
ACCENT_RED = (140, 52, 44)
ACCENT_BLUE = (52, 84, 132)
SAND = (178, 160, 124)
# Milestone 5 (MILESTONE_5.md 11.3): the Stormglass Lens's blued clan iron bezel and pale glass.
CLAN_IRON = (70, 76, 88)
STORMGLASS = (148, 184, 202)


# ---------------------------------------------------------------- output helpers

def write_png(rel, img):
	path = os.path.join(OUT, "textures", rel)
	os.makedirs(os.path.dirname(path), exist_ok=True)
	img.save(path)
	print("wrote", os.path.relpath(path))


def check_bounds(rel, data):
	"""Fails loudly if a model element leaves the -16..32 cube Minecraft accepts."""
	for el in data.get("elements", []) if isinstance(data, dict) else []:
		for corner in (el["from"], el["to"]):
			if any(c < MODEL_MIN or c > MODEL_MAX for c in corner):
				raise SystemExit(f"{rel}: element corner {corner} outside {MODEL_MIN}..{MODEL_MAX}")


def write_json(rel, data):
	check_bounds(rel, data)
	path = os.path.join(OUT, rel)
	os.makedirs(os.path.dirname(path), exist_ok=True)
	with open(path, "w", encoding="utf-8") as f:
		json.dump(data, f, indent="\t")
		f.write("\n")
	print("wrote", os.path.relpath(path))


def clamp(v):
	return max(0, min(255, int(round(v))))


def noisy(rgb, rnd, amount):
	return tuple(clamp(c + rnd.uniform(-amount, amount)) for c in rgb)


def shade(rgb, factor):
	return tuple(clamp(c * factor) for c in rgb)


def new(w=16, h=16):
	return Image.new("RGBA", (w, h), (0, 0, 0, 0))


def put(img, x, y, rgb, a=255):
	if 0 <= x < img.width and 0 <= y < img.height:
		img.putpixel((x, y), (*rgb, a))


# ---------------------------------------------------------------- item textures

def icon_hilt():
	rnd = random.Random(21)
	img = new()
	# Diagonal from bottom-left (pommel) toward the centre (emitter).
	for i in range(8):
		x, y = 2 + i, 13 - i
		mat = BRASS if i < 1 else (GRIP_DARK if i < 5 else STEEL)
		if i == 4 or i == 2:
			mat = STEEL_DARK
		put(img, x, y, noisy(mat, rnd, 6))
		put(img, x + 1, y, noisy(shade(mat, 0.7), rnd, 6))
		put(img, x, y - 1, noisy(shade(mat, 1.15), rnd, 6))
	put(img, 9, 5, STEEL_LIGHT)
	put(img, 10, 5, ACCENT_RED)
	return img


def icon_blade():
	img = new()
	for i in range(7):
		x, y = 10 + i, 5 - i
		put(img, x - 1, y, (255, 255, 255), 255)
		put(img, x, y, (255, 255, 255), 255)
		put(img, x - 1, y - 1, (255, 255, 255), 140)
		put(img, x, y + 1, (255, 255, 255), 140)
	return img


def crystal_body():
	img = new()
	pts = [(8, 1), (12, 6), (11, 13), (8, 15), (5, 13), (4, 6)]
	draw = ImageDraw.Draw(img)
	draw.polygon(pts, fill=(205, 212, 220, 255))
	# Facets: lighter left, darker right.
	draw.polygon([(8, 1), (4, 6), (5, 13), (8, 15)], fill=(236, 240, 245, 255))
	draw.line([(8, 1), (8, 15)], fill=(170, 178, 188, 255))
	draw.line([(4, 6), (12, 6)], fill=(180, 188, 198, 255))
	return img


def singing_crystal():
	"""The singing crystal (spec 8.8): the kyber silhouette in warm amber, its own colour (never tinted),
	with a white-hot face, darker right facets and faint growth bands where it sang in the pylons."""
	img = new()
	draw = ImageDraw.Draw(img)
	pts = [(8, 1), (12, 6), (11, 13), (8, 15), (5, 13), (4, 6)]
	draw.polygon(pts, fill=(222, 150, 52, 255))
	draw.polygon([(8, 1), (4, 6), (5, 13), (8, 15)], fill=(246, 190, 96, 255))
	draw.polygon([(8, 1), (12, 6), (11, 13), (8, 15)], fill=(196, 122, 40, 255))
	for y in (5, 9, 12):
		for x in range(4, 13):
			r, g, b, a = img.getpixel((x, y))
			if a and x != 8:
				put(img, x, y, shade((r, g, b), 1.12))
	draw.line([(8, 1), (8, 15)], fill=(170, 100, 34, 255))
	for x, y, a in [(6, 4, 255), (6, 5, 220), (7, 3, 180), (5, 8, 140), (10, 9, 110)]:
		r, g, b, _ = img.getpixel((x, y))
		put(img, x, y, tuple(clamp(c + (h - c) * a / 255) for c, h in zip((r, g, b), (255, 246, 220))))
	return img


def crystal_glint():
	img = new()
	for x, y, a in [(6, 4, 255), (6, 5, 200), (7, 3, 160), (10, 9, 120), (5, 10, 90)]:
		put(img, x, y, (255, 255, 255), a)
	return img


# ---------------------------------------------------------------- block textures

def bench_texture(face):
	rnd = random.Random({"top": 1, "side": 2, "front": 3, "metal": 4, "lamp": 5}[face])
	img = new()
	for x in range(16):
		for y in range(16):
			if face == "top":
				c = noisy(shade(SAND, 0.9) if (x // 4 + y // 4) % 2 else SAND, rnd, 7)
				if x in (0, 15) or y in (0, 15):
					c = noisy(STEEL_DARK, rnd, 5)
			elif face == "side":
				c = noisy(STEEL_DARK, rnd, 6)
				if y < 3:
					c = noisy(SAND, rnd, 6)
				elif x in (0, 15):
					c = noisy(shade(STEEL_DARK, 0.75), rnd, 4)
			elif face == "front":
				c = noisy(STEEL_DARK, rnd, 6)
				if y < 3:
					c = noisy(SAND, rnd, 6)
				elif 4 <= y <= 8 and 2 <= x <= 13:
					c = noisy(shade(STEEL, 0.9), rnd, 5)
					if y in (4, 8) or x in (2, 13):
						c = noisy(shade(STEEL_DARK, 0.6), rnd, 3)
				if y == 6 and x in (7, 8):
					c = BRASS
			elif face == "metal":
				c = noisy(STEEL if (x + y) % 5 else STEEL_LIGHT, rnd, 6)
			else:  # lamp glass
				c = noisy((220, 236, 250), rnd, 8)
			put(img, x, y, c)
	if face == "side":
		put(img, 3, 9, ACCENT_BLUE)
		put(img, 4, 9, ACCENT_BLUE)
	return img


# ---------------------------------------------------------------- GUI

GUI_W, GUI_H = 230, 200
PANEL = (198, 198, 196)
PANEL_LIGHT = (240, 240, 238)
PANEL_DARK = (110, 112, 116)


def slot(d, x, y):
	"""18x18 vanilla-style slot frame whose 16x16 inside starts at (x, y)."""
	d.rectangle([x - 1, y - 1, x + 16, y + 16], fill=(139, 139, 139, 255))
	d.line([(x - 1, y - 1), (x + 15, y - 1)], fill=(55, 55, 55, 255))
	d.line([(x - 1, y - 1), (x - 1, y + 15)], fill=(55, 55, 55, 255))
	d.line([(x, y + 16), (x + 16, y + 16)], fill=(255, 255, 255, 255))
	d.line([(x + 16, y), (x + 16, y + 16)], fill=(255, 255, 255, 255))


def slot_hint(img, x, y, kind):
	"""Faint outline drawn inside an empty part slot so players know what goes where."""
	d = ImageDraw.Draw(img)
	col = (112, 112, 112, 255)
	if kind == "crystal":
		d.polygon([(x + 8, y + 2), (x + 12, y + 7), (x + 8, y + 14), (x + 4, y + 7)], outline=col)
	elif kind == "emitter":
		d.rectangle([x + 5, y + 6, x + 10, y + 13], outline=col)
		d.rectangle([x + 4, y + 3, x + 11, y + 6], outline=col)
	elif kind == "grip":
		d.rectangle([x + 5, y + 2, x + 10, y + 13], outline=col)
		for yy in range(y + 4, y + 13, 3):
			d.line([(x + 5, yy), (x + 10, yy)], fill=col)
	elif kind == "pommel":
		d.rectangle([x + 5, y + 3, x + 10, y + 11], outline=col)
		d.line([(x + 6, y + 12), (x + 9, y + 12)], fill=col)
	elif kind == "saber":
		d.line([(x + 3, y + 13), (x + 13, y + 3)], fill=col)
		d.line([(x + 3, y + 12), (x + 6, y + 9)], fill=col)


def gui_background():
	img = Image.new("RGBA", (GUI_W, GUI_H), (0, 0, 0, 0))
	d = ImageDraw.Draw(img)
	# Panel with the vanilla bevel.
	d.rectangle([0, 0, GUI_W - 1, GUI_H - 1], fill=(*PANEL, 255))
	d.rectangle([0, 0, GUI_W - 1, GUI_H - 1], outline=(0, 0, 0, 255))
	d.line([(1, 1), (GUI_W - 3, 1)], fill=(*PANEL_LIGHT, 255))
	d.line([(1, 1), (1, GUI_H - 3)], fill=(*PANEL_LIGHT, 255))
	d.line([(2, GUI_H - 2), (GUI_W - 2, GUI_H - 2)], fill=(*PANEL_DARK, 255))
	d.line([(GUI_W - 2, 2), (GUI_W - 2, GUI_H - 2)], fill=(*PANEL_DARK, 255))
	# Recessed stat display (dark durasteel screen).
	d.rectangle([132, 17, 223, 104], fill=(30, 34, 38, 255))
	d.rectangle([132, 17, 223, 104], outline=(70, 76, 82, 255))
	d.line([(132, 17), (223, 17)], fill=(55, 55, 55, 255))
	# Swatch tray.
	d.rectangle([63, 17, 117, 45], fill=(150, 152, 154, 255), outline=(110, 112, 116, 255))
	# Part slots and the saber slot (positions match LightsaberWorkbenchMenu).
	for (x, y), kind in [((10, 20), "crystal"), ((10, 42), "emitter"), ((10, 64), "grip"), ((10, 86), "pommel"), ((40, 53), "saber")]:
		slot(d, x, y)
		slot_hint(img, x, y, kind)
	# Assembly rail from the part slots to the saber slot.
	for y in (28, 50, 72, 94):
		d.line([(27, y), (32, y)], fill=(130, 132, 136, 255))
	d.line([(32, 28), (32, 94)], fill=(130, 132, 136, 255))
	d.line([(32, 61), (38, 61)], fill=(130, 132, 136, 255))
	# Player inventory: 3 rows + hotbar at x 34, y 118 (hotbar 4 px lower).
	for row in range(3):
		for col in range(9):
			slot(d, 34 + col * 18, 118 + row * 18)
	for col in range(9):
		slot(d, 34 + col * 18, 176)
	return img


# ---------------------------------------------------------------- particles

def spark(seed):
	img = new(8, 8)
	if seed == 0:
		for x, y, a in [(3, 3, 255), (4, 3, 255), (3, 4, 255), (4, 4, 255), (2, 3, 120), (5, 4, 120)]:
			put(img, x, y, (255, 255, 255), a)
	else:
		for x, y, a in [(3, 3, 255), (4, 4, 255), (2, 2, 140), (5, 5, 140)]:
			put(img, x, y, (255, 255, 255), a)
	return img


def flash():
	img = new(8, 8)
	for x in range(8):
		for y in range(8):
			r = math.hypot(x - 3.5, y - 3.5) / 3.6
			a = max(0.0, 1.0 - r) ** 1.6
			if a > 0.02:
				put(img, x, y, (255, 255, 255), clamp(a * 255))
	return img


def blade_glint(seed):
	"""The NPC attack tell: a four-pointed star, white (tinted in code)."""
	img = new(8, 8)
	arm = 3 if seed == 0 else 2
	for d in range(-arm, arm + 1):
		a = clamp(255 * (1.0 - abs(d) / (arm + 1)) ** 0.8)
		for x, y in ((3 + d, 3), (4 + d, 4), (3, 3 + d), (4, 4 + d)):
			if 0 <= x < 8 and 0 <= y < 8:
				put(img, x, y, (255, 255, 255), max(a, img.getpixel((x, y))[3]))
	for x, y in ((3, 3), (4, 3), (3, 4), (4, 4)):
		put(img, x, y, (255, 255, 255), 255)
	return img


def lunge_streak():
	"""A soft horizontal smear, white (tinted in code)."""
	img = new(8, 8)
	for x in range(8):
		for y in range(8):
			fx = 1.0 - abs(x - 3.5) / 4.0
			fy = max(0.0, 1.0 - abs(y - 3.5) / 1.6)
			a = fx * fy ** 1.5
			if a > 0.03:
				put(img, x, y, (255, 255, 255), clamp(a * 230))
	return img


# ---------------------------------------------------------------- models (the hilts are tools/assets/hilts/build.py's)

def face(uv, texture="#crystal", tint=None):
	f = {"uv": uv, "texture": texture}
	if tint is not None:
		f["tintindex"] = tint
	return f


# Model elements must stay within -16..32 on every axis.
MODEL_MIN, MODEL_MAX = -16.0, 32.0


def write_models():
	"""The flat saber icons (build.py --flat-icons uses them) and the crystals' models and item definitions."""
	write_json("models/item/lightsaber_icon.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/lightsaber_icon_hilt"}})
	write_json("models/item/lightsaber_icon_lit.json", {"parent": "minecraft:item/generated", "textures": {
		"layer0": f"{NS}:item/lightsaber_icon_hilt", "layer1": f"{NS}:item/lightsaber_icon_blade"}})

	# Kyber crystal: flat tinted icon in GUIs, a small glowing prism elsewhere.
	write_json("models/item/kyber_crystal.json", {"parent": "minecraft:item/generated", "textures": {
		"layer0": f"{NS}:item/kyber_crystal", "layer1": f"{NS}:item/kyber_crystal_glint"}})
	prism = {
		"from": [7, 5, 7], "to": [9, 11, 9],
		"rotation": {"angle": 45, "axis": "y", "origin": [8, 8, 8]},
		"shade": False,
		"light_emission": 12,
		"faces": {s: face([5, 2, 11, 14] if s not in ("up", "down") else [6, 2, 10, 6], "#crystal", 0)
				for s in ("north", "south", "east", "west", "up", "down")},
	}
	write_json("models/item/kyber_crystal_held.json", {
		"gui_light": "front",
		"textures": {"crystal": f"{NS}:item/kyber_crystal", "particle": f"{NS}:item/kyber_crystal"},
		"elements": [prism],
		"display": {
			"thirdperson_righthand": {"translation": [0, 0, 1], "scale": [0.8, 0.8, 0.8]},
			"firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 2, 1.13], "scale": [0.8, 0.8, 0.8]},
			"ground": {"translation": [0, 2, 0], "scale": [0.8, 0.8, 0.8]},
			"fixed": {"scale": [1.2, 1.2, 1.2]},
		},
	})

	# The singing crystal: its own amber, no tint; a flat icon in GUIs, a glowing amber prism elsewhere.
	write_json("models/item/singing_crystal.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/singing_crystal"}})
	singing_prism = dict(prism, faces={s: face([5, 2, 11, 14] if s not in ("up", "down") else [6, 2, 10, 6], "#crystal")
			for s in ("north", "south", "east", "west", "up", "down")})
	write_json("models/item/singing_crystal_held.json", {
		"gui_light": "front",
		"textures": {"crystal": f"{NS}:item/singing_crystal", "particle": f"{NS}:item/singing_crystal"},
		"elements": [singing_prism],
		"display": {
			"thirdperson_righthand": {"translation": [0, 0, 1], "scale": [0.8, 0.8, 0.8]},
			"firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 2, 1.13], "scale": [0.8, 0.8, 0.8]},
			"ground": {"translation": [0, 2, 0], "scale": [0.8, 0.8, 0.8]},
			"fixed": {"scale": [1.2, 1.2, 1.2]},
		},
	})
	write_json("items/singing_crystal.json", {"model": {
		"type": "minecraft:select",
		"property": "minecraft:display_context",
		"cases": [{"when": ["gui", "fixed", "on_shelf"], "model": {"type": "minecraft:model", "model": f"{NS}:item/singing_crystal"}}],
		"fallback": {"type": "minecraft:model", "model": f"{NS}:item/singing_crystal_held"},
	}})

	write_json("items/kyber_crystal.json", {"model": {
		"type": "minecraft:select",
		"property": "minecraft:display_context",
		"cases": [{"when": ["gui", "fixed", "on_shelf"], "model": {
			"type": "minecraft:model", "model": f"{NS}:item/kyber_crystal",
			"tints": [{"type": f"{NS}:crystal_color"}, {"type": "minecraft:constant", "value": -1}]}}],
		"fallback": {"type": "minecraft:model", "model": f"{NS}:item/kyber_crystal_held", "tints": [{"type": f"{NS}:crystal_color"}]},
	}})


def write_workbench():
	t = {k: f"{NS}:block/lightsaber_workbench_{k}" for k in ("top", "side", "front", "metal", "lamp")}
	t["particle"] = t["metal"]

	def cube(frm, to, faces, emissive=False):
		el = {"from": frm, "to": to, "faces": {}}
		for side, (tex, uv) in faces.items():
			el["faces"][side] = {"uv": uv, "texture": f"#{tex}"}
		if emissive:
			el["light_emission"] = 10
		return el

	def all_faces(tex, frm, to):
		sx, sy, sz = (min(16, to[i] - frm[i]) for i in range(3))
		return {
			"north": (tex, [0, 0, sx, sy]),
			"south": (tex, [0, 0, sx, sy]),
			"east": (tex, [0, 0, sz, sy]),
			"west": (tex, [0, 0, sz, sy]),
			"up": (tex, [0, 0, sx, sz]),
			"down": (tex, [0, 0, sx, sz]),
		}

	elements = []
	# Work surface: top texture above, framed body around.
	top_from, top_to = [0, 10, 0], [16, 14, 16]
	elements.append(cube(top_from, top_to, {
		"up": ("top", [0, 0, 16, 16]),
		"down": ("metal", [0, 0, 16, 16]),
		"north": ("front", [0, 2, 16, 6]),
		"south": ("side", [0, 2, 16, 6]),
		"east": ("side", [0, 2, 16, 6]),
		"west": ("side", [0, 2, 16, 6]),
	}))
	# Drawer cabinet under the front half.
	cab_from, cab_to = [1, 2, 1], [15, 10, 8]
	elements.append(cube(cab_from, cab_to, {
		"north": ("front", [1, 6, 15, 14]),
		"south": ("side", [1, 6, 15, 14]),
		"east": ("side", [1, 6, 8, 14]),
		"west": ("side", [8, 6, 15, 14]),
		"down": ("metal", [1, 1, 15, 8]),
	}))
	# Legs.
	for x, z in ((1, 13), (13, 13), (1, 1), (13, 1)):
		frm, to = [x, 0, z], [x + 2, 10, z + 2]
		elements.append(cube(frm, to, all_faces("metal", frm, to)))
	# Hilt vice on the work surface.
	for frm, to in (([5, 14, 7], [11, 15, 9]), ([5, 15, 6.5], [6, 17, 9.5]), ([10, 15, 6.5], [11, 17, 9.5])):
		elements.append(cube(frm, to, all_faces("metal", frm, to)))
	# Lamp arm and head (back right).
	for frm, to in (([13, 14, 12], [14, 21, 13]), ([10, 20, 11], [14, 21, 14])):
		elements.append(cube(frm, to, all_faces("metal", frm, to)))
	lamp_from, lamp_to = [10.5, 19.5, 11.5], [13.5, 20, 13.5]
	elements.append(cube(lamp_from, lamp_to, {"down": ("lamp", [2, 2, 14, 10])}, emissive=True))

	write_json("models/block/lightsaber_workbench.json", {"parent": "minecraft:block/block", "textures": t, "elements": elements})
	write_json("blockstates/lightsaber_workbench.json", {"variants": {
		"facing=north": {"model": f"{NS}:block/lightsaber_workbench"},
		"facing=east": {"model": f"{NS}:block/lightsaber_workbench", "y": 90},
		"facing=south": {"model": f"{NS}:block/lightsaber_workbench", "y": 180},
		"facing=west": {"model": f"{NS}:block/lightsaber_workbench", "y": 270},
	}})
	write_json("items/lightsaber_workbench.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/lightsaber_workbench"}})


# ---------------------------------------------------------------- Milestone 4 (W5): the staff's and training saber's flat icons, lenses

def write_m4_models():
	"""The staff's and the training saber's flat icon models (build.py --flat-icons) and the lenses' models and items."""
	write_json("models/item/lightsaber_staff_icon.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/lightsaber_staff_icon_hilt"}})
	write_json("models/item/lightsaber_staff_icon_lit.json", {"parent": "minecraft:item/generated", "textures": {
		"layer0": f"{NS}:item/lightsaber_staff_icon_hilt", "layer1": f"{NS}:item/lightsaber_staff_icon_blade"}})
	write_json("models/item/training_saber_icon.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/training_saber_icon_hilt"}})
	write_json("models/item/training_saber_icon_lit.json", {"parent": "minecraft:item/generated", "textures": {
		"layer0": f"{NS}:item/training_saber_icon_hilt", "layer1": f"{NS}:item/lightsaber_icon_blade"}})
	for lens in LENSES:
		write_json(f"models/item/lens_{lens}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/lens_{lens}"}})
		write_json(f"items/lens_{lens}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/lens_{lens}"}})


def staff_icon_hilt():
	"""The staff's long hilt on the diagonal through the icon's centre."""
	rnd = random.Random(31)
	img = new()
	for i in range(8):
		x, y = 4 + i, 11 - i
		mat = STEEL if i in (0, 7) else (BRASS if i in (3, 4) else (LEATHER if i in (1, 6) else STEEL_DARK))
		put(img, x, y, noisy(mat, rnd, 6))
		put(img, x + 1, y, noisy(shade(mat, 0.7), rnd, 6))
		put(img, x, y - 1, noisy(shade(mat, 1.15), rnd, 6))
	put(img, 3, 12, STEEL_LIGHT)
	put(img, 12, 3, STEEL_LIGHT)
	return img


def staff_icon_blade():
	"""Both blades, white (tinted with the blade colour): up-right and down-left from the hilt."""
	img = new()
	for i in range(3):
		for x, y in ((13 + i, 2 - i), (2 - i, 13 + i)):
			put(img, x - 1, y, (255, 255, 255), 255)
			put(img, x, y, (255, 255, 255), 255)
			put(img, x - 1, y - 1, (255, 255, 255), 140)
			put(img, x, y + 1, (255, 255, 255), 140)
	return img


def training_icon_hilt():
	"""The training hilt: the saber's diagonal in plain steel with an amber safety band."""
	rnd = random.Random(41)
	img = new()
	for i in range(8):
		x, y = 2 + i, 13 - i
		mat = STEEL_LIGHT if i < 1 else (STEEL if i < 5 else STEEL_DARK)
		if i == 3:
			mat = (214, 150, 52)
		put(img, x, y, noisy(mat, rnd, 6))
		put(img, x + 1, y, noisy(shade(mat, 0.7), rnd, 6))
		put(img, x, y - 1, noisy(shade(mat, 1.15), rnd, 6))
	put(img, 9, 5, STEEL_LIGHT)
	put(img, 10, 5, (214, 150, 52))
	return img


LENSES = ("honed", "steady", "resonant", "stormglass")
LENS_COLORS = {"honed": (120, 190, 235), "steady": (120, 214, 150), "resonant": (176, 128, 232), "stormglass": STORMGLASS}


def lens_icon(kind):
	"""A small round lens in a brass bezel; the glass in its kind's colour with a highlight."""
	img = new()
	d = ImageDraw.Draw(img)
	glass = LENS_COLORS[kind]
	d.ellipse([2, 2, 13, 13], fill=(*shade(BRASS, 0.75), 255))
	d.ellipse([3, 3, 12, 12], fill=(*BRASS, 255))
	d.ellipse([4, 4, 11, 11], fill=(*shade(glass, 0.75), 255))
	d.ellipse([5, 5, 10, 10], fill=(*glass, 255))
	for x, y, a in [(6, 6, 255), (7, 6, 200), (6, 7, 200), (9, 9, 120)]:
		put(img, x, y, tuple(clamp(c + (255 - c) * a / 255) for c in glass))
	if kind == "honed":  # a fine edge ring
		d.arc([4, 4, 11, 11], 200, 340, fill=(*STEEL_LIGHT, 255))
	elif kind == "steady":  # a level line
		d.line([(5, 8), (10, 8)], fill=(*shade(glass, 0.6), 255))
	elif kind == "stormglass":  # legendary (M5): a clan-iron bezel and a forked white streak in pale glass
		d.ellipse([2, 2, 13, 13], outline=(*CLAN_IRON, 255))
		for x, y in [(9, 4), (8, 5), (8, 6), (7, 7), (8, 8), (7, 9), (6, 10)]:
			put(img, x, y, (236, 244, 250), 255)
		put(img, 9, 8, (206, 224, 236), 255)
		put(img, 10, 9, (190, 212, 228), 255)
	else:  # resonant: a small spark
		put(img, 8, 7, (255, 255, 255), 255)
		put(img, 9, 6, (236, 220, 255), 255)
	put(img, 13, 6, shade(BRASS, 0.6))
	put(img, 2, 9, shade(BRASS, 0.6))
	return img


def hint_lens():
	"""Ghost silhouette for the empty lens socket (white; the screen tints and fades it)."""
	img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
	d = ImageDraw.Draw(img)
	d.ellipse([10, 10, 54, 54], outline=(255, 255, 255, 255), width=6)
	d.ellipse([20, 20, 44, 44], fill=(255, 255, 255, 120))
	d.ellipse([24, 24, 34, 34], fill=(255, 255, 255, 255))
	img = img.resize((16, 16), Image.LANCZOS)
	path = os.path.join(OUT, "textures", "gui", "sprites", "bench", "hint_lens.png")
	os.makedirs(os.path.dirname(path), exist_ok=True)
	img.save(path)
	print("wrote", os.path.relpath(path))


def write_m4():
	write_png("item/lightsaber_staff_icon_hilt.png", staff_icon_hilt())
	write_png("item/lightsaber_staff_icon_blade.png", staff_icon_blade())
	write_png("item/training_saber_icon_hilt.png", training_icon_hilt())
	for lens in LENSES:
		write_png(f"item/lens_{lens}.png", lens_icon(lens))
	hint_lens()
	write_m4_models()


def main():
	global OUT
	parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
	parser.add_argument("--out", metavar="DIR", help="write under DIR (laid out like the assets folder) instead of the assets folder")
	OUT = os.path.abspath(parser.parse_args().out or ASSETS)
	write_png("item/lightsaber_icon_hilt.png", icon_hilt())
	write_png("item/lightsaber_icon_blade.png", icon_blade())
	write_png("item/kyber_crystal.png", crystal_body())
	write_png("item/kyber_crystal_glint.png", crystal_glint())
	write_png("item/singing_crystal.png", singing_crystal())
	for f in ("top", "side", "front", "metal", "lamp"):
		write_png(f"block/lightsaber_workbench_{f}.png", bench_texture(f))
	write_png("gui/container/lightsaber_workbench.png", gui_background())
	write_png("particle/saber_spark_0.png", spark(0))
	write_png("particle/saber_spark_1.png", spark(1))
	write_png("particle/contact_flash.png", flash())
	write_json("particles/saber_spark.json", {"textures": [f"{NS}:saber_spark_0", f"{NS}:saber_spark_1"]})
	write_json("particles/contact_flash.json", {"textures": [f"{NS}:contact_flash"]})
	write_png("particle/blade_glint_0.png", blade_glint(0))
	write_png("particle/blade_glint_1.png", blade_glint(1))
	write_png("particle/lunge_streak.png", lunge_streak())
	write_json("particles/blade_glint.json", {"textures": [f"{NS}:blade_glint_0", f"{NS}:blade_glint_1"]})
	write_json("particles/lunge_streak.json", {"textures": [f"{NS}:lunge_streak"]})
	write_models()
	write_workbench()
	write_m4()


if __name__ == "__main__":
	main()
