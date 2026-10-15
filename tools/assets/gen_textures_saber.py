#!/usr/bin/env python3
"""Generates the saber feature's textures and model JSON (PLACEHOLDER art, original, deterministic).

Textures (src/main/resources/assets/oldrepublic/textures/):
  item/lightsaber_icon_hilt.png     16x16 inventory hilt (diagonal)
  item/lightsaber_icon_blade.png    16x16 inventory blade, white (tinted with the blade colour)
  item/lightsaber_hilt.png          16x16 material swatches for the 3D hilt models
  item/lightsaber_blade.png         16x16 blade core (opaque white) and two glow bands (partial alpha)
  item/kyber_crystal.png            16x16 crystal body, light greys (tinted with its natural colour)
  item/kyber_crystal_glint.png      16x16 white highlight layer (untinted)
  item/emitter_guardian.png ... pommel_balanced.png   16x16 part icons
  block/lightsaber_workbench_*.png  16x16 workbench faces (top, side, front, metal, lamp)
  gui/container/lightsaber_workbench.png  230x200 workbench screen
  particle/saber_spark_0..1.png, particle/contact_flash.png  8x8, white (tinted in code)

JSON (src/main/resources/assets/oldrepublic/):
  items/lightsaber.json, items/kyber_crystal.json, items/<part>.json, items/lightsaber_workbench.json
  models/item/lightsaber/*.json (base, hilt parts, blade stages), models/item/*.json (icons, parts, crystal)
  models/block/lightsaber_workbench.json, blockstates/lightsaber_workbench.json
  particles/saber_spark.json, particles/contact_flash.json

Run from anywhere: python3 tools/assets/gen_textures_saber.py
"""
import json
import math
import os
import random

from PIL import Image, ImageDraw

ASSETS = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "assets", "oldrepublic"))
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


# ---------------------------------------------------------------- output helpers

def write_png(rel, img):
	path = os.path.join(ASSETS, "textures", rel)
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
	path = os.path.join(ASSETS, rel)
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

def hilt_swatches():
	"""Material bands used by the 3D hilt elements (see MATERIALS)."""
	rnd = random.Random(11)
	img = new()
	for x in range(16):
		for y in range(16):
			if y < 4:  # brushed steel
				c = noisy(shade(STEEL, 1.08 - 0.05 * (y % 2)), rnd, 6)
			elif y < 8:  # leather wrap with diagonal seams
				c = noisy(LEATHER if (x + y) % 4 else shade(LEATHER, 0.6), rnd, 5)
			elif y < 12:  # ridged dark metal
				c = noisy(STEEL_DARK if x % 3 else shade(STEEL_DARK, 0.6), rnd, 4)
			elif x < 8:  # brass accent
				c = noisy(BRASS, rnd, 6)
			else:  # near-black grip
				c = noisy(GRIP_DARK, rnd, 4)
			put(img, x, y, c)
	return img


MATERIALS = {
	"steel": [0, 0, 16, 4],
	"wrap": [0, 4, 16, 8],
	"ridge": [0, 8, 16, 12],
	"brass": [0, 12, 8, 16],
	"dark": [8, 12, 16, 16],
}


def blade_texture():
	"""Core (opaque white) in u 0-4, inner glow (alpha 150) in u 4-8, outer glow (alpha 64) in u 8-12."""
	img = new()
	for y in range(16):
		for x in range(4):
			put(img, x, y, (255, 255, 255), 255)
		for x in range(4, 8):
			put(img, x, y, (255, 255, 255), 150)
		for x in range(8, 12):
			put(img, x, y, (255, 255, 255), 64)
	return img


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


def crystal_glint():
	img = new()
	for x, y, a in [(6, 4, 255), (6, 5, 200), (7, 3, 160), (10, 9, 120), (5, 10, 90)]:
		put(img, x, y, (255, 255, 255), a)
	return img


def part_icon(kind):
	rnd = random.Random(sum(ord(c) for c in kind))
	img = new()
	d = ImageDraw.Draw(img)
	if kind == "emitter_guardian":
		d.rectangle([4, 6, 11, 12], fill=(*STEEL, 255))
		d.rectangle([3, 3, 12, 6], fill=(*STEEL_LIGHT, 255))
		d.rectangle([6, 3, 9, 5], fill=(*STEEL_DARK, 255))
		d.line([(4, 9), (11, 9)], fill=(*ACCENT_BLUE, 255))
	elif kind == "emitter_duelist":
		d.rectangle([6, 5, 9, 13], fill=(*STEEL, 255))
		d.polygon([(6, 5), (4, 2), (5, 2), (7, 5)], fill=(*STEEL_LIGHT, 255))
		d.rectangle([7, 3, 8, 5], fill=(*STEEL_DARK, 255))
		d.line([(6, 10), (9, 10)], fill=(*ACCENT_RED, 255))
	elif kind == "grip_wrapped":
		d.rectangle([5, 2, 10, 13], fill=(*LEATHER, 255))
		for y in range(2, 14, 2):
			d.line([(5, y), (10, y + 1)], fill=(*shade(LEATHER, 0.55), 255))
	elif kind == "grip_ridged":
		d.rectangle([5, 2, 10, 13], fill=(*STEEL_DARK, 255))
		for y in range(3, 13, 3):
			d.rectangle([4, y, 11, y], fill=(*STEEL, 255))
	elif kind == "pommel_weighted":
		d.rectangle([4, 4, 11, 12], fill=(*STEEL_DARK, 255))
		d.rectangle([5, 12, 10, 13], fill=(*BRASS, 255))
		d.rectangle([6, 2, 9, 4], fill=(*STEEL, 255))
	elif kind == "pommel_balanced":
		d.rectangle([6, 3, 9, 11], fill=(*STEEL, 255))
		d.ellipse([5, 10, 10, 14], fill=(*BRASS, 255))
	# Wear: speckle a few pixels darker.
	for _ in range(6):
		x, y = rnd.randrange(16), rnd.randrange(16)
		r, g, b, a = img.getpixel((x, y))
		if a:
			put(img, x, y, shade((r, g, b), 0.8))
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


# ---------------------------------------------------------------- 3D models

def face(uv, texture="#hilt", tint=None):
	f = {"uv": uv, "texture": texture}
	if tint is not None:
		f["tintindex"] = tint
	return f


def scaled_uv(region, w, h):
	"""Uses the top-left part of a material band, sized to the face, for a crisp look."""
	u0, v0, u1, v1 = region
	return [u0, v0, min(u1, u0 + max(1, round(w))), min(v1, v0 + max(1, round(h)))]


def box(frm, to, material, emissive=False):
	frm = [frm[0], hilt_y(frm[1]), frm[2]]
	to = [to[0], hilt_y(to[1]), to[2]]
	sx, sy, sz = (to[i] - frm[i] for i in range(3))
	region = MATERIALS[material]
	el = {
		"from": frm,
		"to": to,
		"faces": {
			"north": face(scaled_uv(region, sx, sy)),
			"south": face(scaled_uv(region, sx, sy)),
			"east": face(scaled_uv(region, sz, sy)),
			"west": face(scaled_uv(region, sz, sy)),
			"up": face(scaled_uv(region, sx, sz)),
			"down": face(scaled_uv(region, sx, sz)),
		},
	}
	if emissive:
		el["light_emission"] = 15
	return el


# Model elements must stay within -16..32 on every axis. Hilt parts are authored with y 0-10.5
# (pommel 0-2.5, grip 2.5-7.5, emitter 7.5-10.5) and remapped by hilt_y() to y -2..6, so the grip
# sits near the model centre like the vanilla spyglass; the blade runs from y 6 to 31 (outer glow to
# 31.7). The display scale in base.json sets the in-hand size (about 1.7 blocks in third person).
HILT_SCALE = 8.0 / 10.5
HILT_OFFSET = -2.0
BLADE_BASE = 6.0
BLADE_LENGTH = 25.0
MODEL_MIN, MODEL_MAX = -16.0, 32.0


def hilt_y(y):
	return round(y * HILT_SCALE + HILT_OFFSET, 3)
BLADE_STAGES = 6


def hilt_models():
	return {
		"emitter_guardian": [
			box([7, 7.5, 7], [9, 9.5, 9], "steel"),
			box([6.5, 9.5, 6.5], [9.5, 10.5, 9.5], "steel"),
			box([6.25, 8.25, 7.5], [6.75, 10.25, 8.5], "dark"),
		],
		"emitter_duelist": [
			box([7.15, 7.5, 7.15], [8.85, 10.5, 8.85], "steel"),
			box([8.85, 9, 7.6], [9.6, 10.75, 8.4], "steel"),
			box([7.1, 8.25, 7.1], [8.9, 8.6, 8.9], "brass"),
		],
		"grip_wrapped": [
			box([6.9, 2.5, 6.9], [9.1, 7.5, 9.1], "wrap"),
		],
		"grip_ridged": [
			box([7.1, 2.5, 7.1], [8.9, 7.5, 8.9], "ridge"),
			box([6.85, 3.25, 6.85], [9.15, 3.75, 9.15], "steel"),
			box([6.85, 5.0, 6.85], [9.15, 5.5, 9.15], "steel"),
			box([6.85, 6.75, 6.85], [9.15, 7.25, 9.15], "steel"),
		],
		"pommel_weighted": [
			box([6.6, 0, 6.6], [9.4, 2.5, 9.4], "dark"),
			box([6.9, 0, 6.9], [9.1, 0.5, 9.1], "brass"),
		],
		"pommel_balanced": [
			box([7.2, 0.75, 7.2], [8.8, 2.5, 8.8], "steel"),
			box([7.5, 0, 7.5], [8.5, 0.75, 8.5], "brass"),
		],
	}


def blade_layer(inset, length, u0, tint):
	lo, hi = 8 - inset, 8 + inset
	top = BLADE_BASE + length
	el = {
		"from": [lo, BLADE_BASE, lo],
		"to": [hi, top, hi],
		"shade": False,
		"light_emission": 15,
		"faces": {},
	}
	for side in ("north", "south", "east", "west"):
		el["faces"][side] = face([u0, 0, u0 + 4, 16], "#blade", tint)
	el["faces"]["up"] = face([u0, 0, u0 + 4, 4], "#blade", tint)
	return el


def blade_model(stage):
	length = BLADE_LENGTH * stage / BLADE_STAGES
	elements = []
	if stage > 0:
		elements = [
			blade_layer(0.45, length, 0, None),  # white core, untinted
			blade_layer(0.85, length + 0.35, 4, 0),  # inner glow, blade colour
			blade_layer(1.35, length + 0.7, 8, 0),  # outer glow, blade colour
		]
	return {"parent": f"{NS}:item/lightsaber/base", "textures": {"blade": f"{NS}:item/lightsaber_blade", "particle": f"{NS}:item/lightsaber_blade"}, "elements": elements}


def saber_base_model():
	# The blade runs along model +Y; the grip centre is 6.5 units below the model centre that
	# rotation and scale pivot on, so each translation moves the grip back into the fist after the
	# rotation (grip offset rotated, then subtracted from the spyglass-like hold point).
	# Third person: item +Y points forward out of the fist and -Z points up, so X -45 raises the
	# blade to a forward-and-up idle angle; 33.7 units x 0.85 = about 1.8 blocks.
	# First person: Z +15 leans the tip toward the screen centre, X -45 tilts it away from the
	# camera, scale 0.6 and the translation keep the hilt low in the bottom-right corner with the
	# tip near mid-screen.
	return {
		"gui_light": "front",
		"textures": {"particle": f"{NS}:item/lightsaber_hilt"},
		"display": {
			"thirdperson_righthand": {"rotation": [-45, 0, 0], "translation": [0, -0.6, -3.9], "scale": [0.85, 0.85, 0.85]},
			"thirdperson_lefthand": {"rotation": [-45, 0, 0], "translation": [0, -0.6, -3.9], "scale": [0.85, 0.85, 0.85]},
			"firstperson_righthand": {"rotation": [-45, 0, 15], "translation": [-1, -1.2, -3.7], "scale": [0.6, 0.6, 0.6]},
			"firstperson_lefthand": {"rotation": [-45, 0, -15], "translation": [1, -1.2, -3.7], "scale": [0.6, 0.6, 0.6]},
			"ground": {"rotation": [0, 0, 90], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
			"head": {"rotation": [0, 0, 0], "translation": [0, 10, 0], "scale": [0.8, 0.8, 0.8]},
		},
	}


def write_models():
	write_json("models/item/lightsaber/base.json", saber_base_model())
	for name, elements in hilt_models().items():
		write_json(f"models/item/lightsaber/{name}.json", {
			"parent": f"{NS}:item/lightsaber/base",
			"textures": {"hilt": f"{NS}:item/lightsaber_hilt", "particle": f"{NS}:item/lightsaber_hilt"},
			"elements": elements,
		})
	for stage in range(BLADE_STAGES + 1):
		write_json(f"models/item/lightsaber/blade_{stage}.json", blade_model(stage))

	write_json("models/item/lightsaber_icon.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/lightsaber_icon_hilt"}})
	write_json("models/item/lightsaber_icon_lit.json", {"parent": "minecraft:item/generated", "textures": {
		"layer0": f"{NS}:item/lightsaber_icon_hilt", "layer1": f"{NS}:item/lightsaber_icon_blade"}})

	for part in PARTS:
		write_json(f"models/item/{part}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{part}"}})
		write_json(f"items/{part}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{part}"}})

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

	write_json("items/kyber_crystal.json", {"model": {
		"type": "minecraft:select",
		"property": "minecraft:display_context",
		"cases": [{"when": ["gui", "fixed", "on_shelf"], "model": {
			"type": "minecraft:model", "model": f"{NS}:item/kyber_crystal",
			"tints": [{"type": f"{NS}:crystal_color"}, {"type": "minecraft:constant", "value": -1}]}}],
		"fallback": {"type": "minecraft:model", "model": f"{NS}:item/kyber_crystal_held", "tints": [{"type": f"{NS}:crystal_color"}]},
	}})

	blade_tints = [{"type": f"{NS}:blade_color"}]
	thresholds = [(k - 0.5) / BLADE_STAGES for k in range(1, BLADE_STAGES + 1)]
	blade = {
		"type": "minecraft:range_dispatch",
		"property": f"{NS}:blade_extension",
		"entries": [{"threshold": round(t, 4), "model": {"type": "minecraft:model", "model": f"{NS}:item/lightsaber/blade_{k}", "tints": blade_tints}}
				for k, t in zip(range(1, BLADE_STAGES + 1), thresholds)],
		"fallback": {"type": "minecraft:model", "model": f"{NS}:item/lightsaber/blade_0"},
	}

	def part_select(slot, variants):
		return {
			"type": "minecraft:select",
			"property": f"{NS}:hilt_part",
			"part": slot,
			"cases": [{"when": v, "model": {"type": "minecraft:model", "model": f"{NS}:item/lightsaber/{slot}_{v}"}} for v in variants],
			"fallback": {"type": "minecraft:model", "model": f"{NS}:item/lightsaber/{slot}_{variants[0]}"},
		}

	icon_tints = [{"type": "minecraft:constant", "value": -1}, {"type": f"{NS}:blade_color"}]
	write_json("items/lightsaber.json", {"model": {
		"type": "minecraft:select",
		"property": "minecraft:display_context",
		"cases": [{"when": ["gui", "fixed", "on_shelf"], "model": {
			"type": "minecraft:range_dispatch",
			"property": f"{NS}:blade_extension",
			"entries": [{"threshold": 0.5, "model": {"type": "minecraft:model", "model": f"{NS}:item/lightsaber_icon_lit", "tints": icon_tints}}],
			"fallback": {"type": "minecraft:model", "model": f"{NS}:item/lightsaber_icon"},
		}}],
		"fallback": {"type": "minecraft:composite", "models": [
			part_select("pommel", ["weighted", "balanced"]),
			part_select("grip", ["wrapped", "ridged"]),
			part_select("emitter", ["guardian", "duelist"]),
			blade,
		]},
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


PARTS = ["emitter_guardian", "emitter_duelist", "grip_wrapped", "grip_ridged", "pommel_weighted", "pommel_balanced"]


def main():
	write_png("item/lightsaber_hilt.png", hilt_swatches())
	write_png("item/lightsaber_blade.png", blade_texture())
	write_png("item/lightsaber_icon_hilt.png", icon_hilt())
	write_png("item/lightsaber_icon_blade.png", icon_blade())
	write_png("item/kyber_crystal.png", crystal_body())
	write_png("item/kyber_crystal_glint.png", crystal_glint())
	for part in PARTS:
		write_png(f"item/{part}.png", part_icon(part))
	for f in ("top", "side", "front", "metal", "lamp"):
		write_png(f"block/lightsaber_workbench_{f}.png", bench_texture(f))
	write_png("gui/container/lightsaber_workbench.png", gui_background())
	write_png("particle/saber_spark_0.png", spark(0))
	write_png("particle/saber_spark_1.png", spark(1))
	write_png("particle/contact_flash.png", flash())
	write_json("particles/saber_spark.json", {"textures": [f"{NS}:saber_spark_0", f"{NS}:saber_spark_1"]})
	write_json("particles/contact_flash.json", {"textures": [f"{NS}:contact_flash"]})
	write_models()
	write_workbench()


if __name__ == "__main__":
	main()
