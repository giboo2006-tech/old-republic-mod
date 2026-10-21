#!/usr/bin/env python3
"""Generates the saber feature's textures and model JSON (PLACEHOLDER art, original, deterministic).

Textures (src/main/resources/assets/oldrepublic/textures/):
  item/lightsaber_icon_hilt.png     16x16 inventory hilt (diagonal)
  item/lightsaber_icon_blade.png    16x16 inventory blade, white (tinted with the blade colour)
  item/lightsaber_hilt.png          16x16 material swatches for the 3D hilt models
  item/kyber_crystal.png            16x16 crystal body, light greys (tinted with its natural colour)
  item/kyber_crystal_glint.png      16x16 white highlight layer (untinted)
  item/emitter_guardian.png ... pommel_balanced.png   16x16 part icons
  item/emitter_reliquary.png, item/pommel_archivist.png  16x16 Milestone 3 part icons (W5)
  item/singing_crystal.png          16x16 the singing crystal, amber (final art for M3; untinted)
  block/lightsaber_workbench_*.png  16x16 workbench faces (top, side, front, metal, lamp)
  gui/container/lightsaber_workbench.png  230x200 workbench screen
  particle/saber_spark_0..1.png, particle/contact_flash.png  8x8, white (tinted in code)
  particle/blade_glint_0..1.png, particle/lunge_streak.png  8x8, white (tinted in code)
  Milestone 4 (W5, spec MILESTONE_4.md 5.5-5.7):
  item/grip_staff.png               16x16 the saberstaff's long grip
  item/lightsaber_staff_icon_hilt.png, item/lightsaber_staff_icon_blade.png  16x16 staff icon (blade white, tinted)
  item/training_saber_icon_hilt.png 16x16 the training saber's hilt icon (blade: lightsaber_icon_blade)
  item/lens_honed.png, lens_steady.png, lens_resonant.png  16x16 saber lenses
  gui/sprites/bench/hint_lens.png   16x16 ghost silhouette for the workbench's empty lens socket
  Milestone 5 (W7, spec MILESTONE_5.md 11.2):
  item/emitter_cantor.png, item/grip_sunforged.png, item/pommel_hask.png  16x16 legendary part icons
  item/lens_stormglass.png          16x16 the Stormglass Lens
  item/lightsaber_hilt_legendary.png  16x16 legendary material swatches (sunstone, clan iron,
                                    stormglass, hymnstone, ember) for the legendary parts' 3D models

JSON (src/main/resources/assets/oldrepublic/):
  items/lightsaber.json, items/kyber_crystal.json, items/<part>.json, items/lightsaber_workbench.json,
  items/singing_crystal.json (flat icon in GUIs, a small glowing amber prism elsewhere)
  models/item/lightsaber/*.json (display parents and hilt parts), models/item/*.json (icons, parts, crystal)
  models/block/lightsaber_workbench.json, blockstates/lightsaber_workbench.json
  particles/saber_spark.json, particles/contact_flash.json, particles/blade_glint.json,
  particles/lunge_streak.json
  Milestone 4: items/lightsaber_staff.json, items/training_saber.json, items/training_saber_red.json,
  items/grip_staff.json, items/lens_*.json; models/item/lightsaber/staff/*.json and staff_guard/*.json
  (the staff: grip and an emitter at each end), models/item/{lightsaber_staff,training_saber}_icon*.json
  Milestone 5: items/{emitter_cantor,grip_sunforged,pommel_hask,lens_stormglass}.json and their
  models; the legendary parts in every hilt select (saber, staff, training).

Every blade (saber, staff, training saber; any length, colour or extension) is drawn in code: the item
models end in the special model oldrepublic:saber_blade (SaberBladeRenderer), which reads the stack. The
lightsaber's in-hand model is empty while the stack carries oldrepublic:saber_thrown (the flying blade
is drawn by the client); the inventory icon stays.

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
# Milestone 5 legendary materials (MILESTONE_5.md 11.3): warm sunstone, blued clan iron, pale
# stormglass, violet-grey hymnstone and a dull ember red.
SUNSTONE = (206, 146, 64)
CLAN_IRON = (70, 76, 88)
STORMGLASS = (148, 184, 202)
HYMNSTONE = (170, 156, 198)
EMBER = (128, 58, 40)


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


def legend_swatches():
	"""Material bands of the legendary parts (M5), on their own texture (see LEGEND_MATERIALS)."""
	rnd = random.Random(53)
	img = new()
	for x in range(16):
		for y in range(16):
			if y < 4:  # sunstone: warm amber with brighter veins
				c = noisy(shade(SUNSTONE, 1.18) if (x * 3 + y * 5) % 7 == 0 else SUNSTONE, rnd, 7)
			elif y < 8:  # clan iron: blued, hammered, a rust edge on the last row
				c = noisy(shade(EMBER, 0.9) if y == 7 and x % 3 == 0 else shade(CLAN_IRON, 1.0 + 0.08 * ((x + y) % 2)), rnd, 5)
			elif y < 12:  # stormglass: pale glass with a white streak
				c = noisy((224, 236, 244) if x == y - 3 or x == y - 2 else STORMGLASS, rnd, 5)
			elif x < 8:  # hymnstone: violet-grey with faint pale flecks
				c = noisy((214, 204, 236) if (x + y) % 5 == 0 else HYMNSTONE, rnd, 5)
			else:  # ember: dull red-orange inlay
				c = noisy(EMBER, rnd, 6)
			put(img, x, y, c)
	return img


LEGEND_MATERIALS = {
	"sunstone": [0, 0, 16, 4],
	"clan_iron": [0, 4, 16, 8],
	"stormglass": [0, 8, 16, 12],
	"hymnstone": [0, 12, 8, 16],
	"ember": [8, 12, 16, 16],
}


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
	elif kind == "pommel_archivist":
		# Vasch's squared, archival pommel: a brass collar, a squared body with an engraved band, a dark cap.
		d.rectangle([4, 2, 11, 3], fill=(*BRASS, 255))
		d.rectangle([5, 4, 10, 11], fill=(*STEEL, 255))
		d.line([(5, 7), (10, 7)], fill=(*STEEL_DARK, 255))
		d.line([(10, 4), (10, 11)], fill=(*shade(STEEL, 0.75), 255))
		d.rectangle([6, 12, 9, 13], fill=(*GRIP_DARK, 255))
	elif kind == "grip_staff":
		# The saberstaff's long grip (M4): ridged dark metal, a wrap at each hand, a brass centre ring.
		d.rectangle([6, 0, 9, 15], fill=(*STEEL_DARK, 255))
		for y in range(1, 15, 2):
			d.line([(6, y), (9, y)], fill=(*shade(STEEL_DARK, 0.65), 255))
		d.rectangle([5, 2, 10, 4], fill=(*LEATHER, 255))
		d.rectangle([5, 11, 10, 13], fill=(*LEATHER, 255))
		d.rectangle([5, 7, 10, 8], fill=(*BRASS, 255))
		d.line([(9, 0), (9, 15)], fill=(*shade(STEEL_DARK, 0.8), 255))
	elif kind == "emitter_reliquary":
		# A squared emitter shroud with a brass collar and a slit of amber light.
		d.rectangle([5, 6, 10, 13], fill=(*STEEL, 255))
		d.rectangle([4, 2, 11, 5], fill=(*STEEL_LIGHT, 255))
		d.rectangle([6, 2, 9, 3], fill=(*STEEL_DARK, 255))
		d.rectangle([5, 10, 10, 11], fill=(*BRASS, 255))
		d.line([(7, 7), (8, 7)], fill=(255, 176, 42, 255))
		d.line([(10, 6), (10, 13)], fill=(*shade(STEEL, 0.75), 255))
	elif kind == "emitter_cantor":
		# Legendary (M5): a slim steel throat, an ember band, a flared hymnstone crown and a side fin.
		d.rectangle([6, 6, 9, 13], fill=(*STEEL, 255))
		d.line([(6, 9), (9, 9)], fill=(*EMBER, 255))
		d.rectangle([4, 3, 11, 5], fill=(*HYMNSTONE, 255))
		d.line([(4, 3), (11, 3)], fill=(*shade(HYMNSTONE, 1.2), 255))
		d.rectangle([5, 2, 10, 2], fill=(*GRIP_DARK, 255))
		d.rectangle([10, 6, 11, 10], fill=(*shade(HYMNSTONE, 0.85), 255))
		d.line([(9, 6), (9, 13)], fill=(*shade(STEEL, 0.75), 255))
	elif kind == "grip_sunforged":
		# Legendary (M5): a dark core banded three times with warm sunstone and an inlaid strip.
		d.rectangle([5, 2, 10, 13], fill=(*GRIP_DARK, 255))
		for y in (3, 7, 11):
			d.rectangle([4, y, 11, y + 1], fill=(*SUNSTONE, 255))
			d.line([(4, y), (11, y)], fill=(*shade(SUNSTONE, 1.2), 255))
		d.line([(9, 4), (9, 10)], fill=(*shade(SUNSTONE, 0.8), 255))
	elif kind == "pommel_hask":
		# Legendary (M5): a heavy block of blued clan iron, a cap and an ember notch each side.
		d.rectangle([3, 3, 12, 11], fill=(*CLAN_IRON, 255))
		d.line([(3, 3), (12, 3)], fill=(*shade(CLAN_IRON, 1.3), 255))
		d.rectangle([6, 12, 9, 13], fill=(*shade(CLAN_IRON, 0.8), 255))
		d.rectangle([2, 6, 3, 8], fill=(*EMBER, 255))
		d.rectangle([12, 6, 13, 8], fill=(*EMBER, 255))
		d.line([(12, 4), (12, 11)], fill=(*shade(CLAN_IRON, 0.7), 255))
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


def box(frm, to, material, emissive=False, ymap=None):
	ymap = ymap or hilt_y
	frm = [frm[0], ymap(frm[1]), frm[2]]
	to = [to[0], ymap(to[1]), to[2]]
	sx, sy, sz = (to[i] - frm[i] for i in range(3))
	legend = material in LEGEND_MATERIALS
	region = LEGEND_MATERIALS[material] if legend else MATERIALS[material]
	tex = "#legend" if legend else "#hilt"
	el = {
		"from": frm,
		"to": to,
		"faces": {
			"north": face(scaled_uv(region, sx, sy), tex),
			"south": face(scaled_uv(region, sx, sy), tex),
			"east": face(scaled_uv(region, sz, sy), tex),
			"west": face(scaled_uv(region, sz, sy), tex),
			"up": face(scaled_uv(region, sx, sz), tex),
			"down": face(scaled_uv(region, sx, sz), tex),
		},
	}
	if emissive:
		el["light_emission"] = 15
	return el


# Model elements must stay within -16..32 on every axis. Hilt parts are authored with y 0-10.5
# (pommel 0-2.5, grip 2.5-7.5, emitter 7.5-10.5) and remapped by hilt_y() to y -2..6, so the grip
# sits near the model centre like the vanilla spyglass. The blade, drawn in code (SaberBladeRenderer),
# starts at the emitter's top, y 6, and runs 25 units (x0.7 short, x1.15 long; MILESTONE_5.md 10.3).
# The display scale in base.json sets the in-hand size.
HILT_SCALE = 8.0 / 10.5
HILT_OFFSET = -2.0
MODEL_MIN, MODEL_MAX = -16.0, 32.0


def hilt_y(y):
	return round(y * HILT_SCALE + HILT_OFFSET, 3)


HILT_RAW = {
	"emitter_guardian": [
		([7, 7.5, 7], [9, 9.5, 9], "steel"),
		([6.5, 9.5, 6.5], [9.5, 10.5, 9.5], "steel"),
		([6.25, 8.25, 7.5], [6.75, 10.25, 8.5], "dark"),
	],
	"emitter_duelist": [
		([7.15, 7.5, 7.15], [8.85, 10.5, 8.85], "steel"),
		([8.85, 9, 7.6], [9.6, 10.75, 8.4], "steel"),
		([7.1, 8.25, 7.1], [8.9, 8.6, 8.9], "brass"),
	],
	"grip_wrapped": [
		([6.9, 2.5, 6.9], [9.1, 7.5, 9.1], "wrap"),
	],
	"grip_ridged": [
		([7.1, 2.5, 7.1], [8.9, 7.5, 8.9], "ridge"),
		([6.85, 3.25, 6.85], [9.15, 3.75, 9.15], "steel"),
		([6.85, 5.0, 6.85], [9.15, 5.5, 9.15], "steel"),
		([6.85, 6.75, 6.85], [9.15, 7.25, 9.15], "steel"),
	],
	"pommel_weighted": [
		([6.6, 0, 6.6], [9.4, 2.5, 9.4], "dark"),
		([6.9, 0, 6.9], [9.1, 0.5, 9.1], "brass"),
	],
	"pommel_balanced": [
		([7.2, 0.75, 7.2], [8.8, 2.5, 8.8], "steel"),
		([7.5, 0, 7.5], [8.5, 0.75, 8.5], "brass"),
	],
	# Milestone 3 (W5): Vasch's hilt parts. Squared and archival, with brass collars.
	"emitter_reliquary": [
		([7.0, 7.5, 7.0], [9.0, 9.75, 9.0], "steel"),
		([6.6, 9.75, 6.6], [9.4, 10.5, 9.4], "dark"),
		([6.85, 8.0, 6.85], [9.15, 8.5, 9.15], "brass"),
		([9.0, 8.75, 7.6], [9.3, 9.5, 8.4], "ridge"),
	],
	"pommel_archivist": [
		([6.75, 0.5, 6.75], [9.25, 2.0, 9.25], "steel"),
		([6.9, 2.0, 6.9], [9.1, 2.5, 9.1], "brass"),
		([7.2, 0, 7.2], [8.8, 0.5, 8.8], "dark"),
	],
	# Milestone 5 (W7): the legendary parts. The Cantor's Emitter: a slim steel throat under a flared
	# hymnstone crown with a resonator fin; the Sunforged Grip: a dark core banded with sunstone; the
	# Hask Pommel: a heavy block of clan iron with an ember notch.
	"emitter_cantor": [
		([7.05, 7.5, 7.05], [8.95, 9.2, 8.95], "steel"),
		([7.0, 8.15, 7.0], [9.0, 8.45, 9.0], "ember"),
		([6.6, 9.2, 6.6], [9.4, 9.95, 9.4], "hymnstone"),
		([6.85, 9.95, 6.85], [9.15, 10.5, 9.15], "dark"),
		([8.95, 7.85, 7.7], [9.4, 9.7, 8.3], "hymnstone"),
	],
	"grip_sunforged": [
		([7.05, 2.5, 7.05], [8.95, 7.5, 8.95], "dark"),
		([6.8, 2.9, 6.8], [9.2, 3.4, 9.2], "sunstone"),
		([6.8, 4.75, 6.8], [9.2, 5.25, 9.2], "sunstone"),
		([6.8, 6.6, 6.8], [9.2, 7.1, 9.2], "sunstone"),
		([8.95, 3.6, 7.65], [9.1, 6.4, 8.35], "sunstone"),
	],
	"pommel_hask": [
		([6.5, 0.45, 6.5], [9.5, 2.5, 9.5], "clan_iron"),
		([7.2, 0, 7.2], [8.8, 0.45, 8.8], "clan_iron"),
		([9.5, 1.0, 7.6], [9.7, 1.95, 8.4], "ember"),
		([6.3, 1.0, 7.6], [6.5, 1.95, 8.4], "ember"),
	],
}

LEGENDARY_PARTS = ("emitter_cantor", "grip_sunforged", "pommel_hask")


def hilt_textures(name, parent):
	"""A hilt part model's parent and textures; the legendary parts also bind their own swatches."""
	t = {"hilt": f"{NS}:item/lightsaber_hilt", "particle": f"{NS}:item/lightsaber_hilt"}
	if any(m in LEGEND_MATERIALS for _, _, m in HILT_RAW.get(name, [])):
		t["legend"] = f"{NS}:item/lightsaber_hilt_legendary"
	return {"parent": f"{NS}:item/lightsaber/{parent}", "textures": t}


def hilt_models():
	return {name: [box(f, t, m) for f, t, m in parts] for name, parts in HILT_RAW.items()}


# Where the hand closes on the hilt: the grip's centre (model y 1.8) is this many units below the model centre (8, 8, 8)
# that display rotations and scales pivot on.
GRIP_BELOW_CENTRE = 6.2
# Third person, in the hand's display space (+Y forward out of the fist, +Z up the arm toward the shoulder, X lateral; the
# vanilla handheld sword's [0, -90, 55] maps the sprite's up-right diagonal onto +Y): where the fist holds the grip
# (between the vanilla sword's handle, (0, -3.1, 1.75), and the spyglass grip, (0, -4.5, 0)), how far the blade is tilted
# up from straight forward, and the hold's scale. With the arm's vanilla held-item lift (18 degrees) the blade rises about
# 40 degrees at rest; the ready stance and the forms' stances (SaberStance) raise the arm further. 33.7 units x 0.68 is
# about 1.4 blocks, hilt and standard blade.
THIRD_PERSON_FIST = [0.0, -3.8, 0.9]
THIRD_PERSON_TILT = 22.0
THIRD_PERSON_SCALE = 0.68
# First person: the identity. The saber is posed and swung in code (SaberFirstPerson, through ItemInHandRendererSaberMixin),
# which places the grip itself.
FIRST_PERSON = {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}


def held_third_person(tilt, scale):
	"""A third-person hand transform tilting the blade `tilt` degrees up from straight forward, the grip in the fist: the
	translation moves the grip (GRIP_BELOW_CENTRE x scale below the pivot, rotated with the model) onto THIRD_PERSON_FIST."""
	t = math.radians(tilt)
	below = GRIP_BELOW_CENTRE * scale
	grip = [0.0, -below * math.cos(t), -below * math.sin(t)]
	translation = [round(THIRD_PERSON_FIST[i] - grip[i], 2) for i in range(3)]
	return {"rotation": [tilt, 0, 0], "translation": translation, "scale": [scale, scale, scale]}


def saber_base_model():
	# The blade runs along model +Y from the emitter. Third person: see THIRD_PERSON_FIST. First person: posed in code.
	third = held_third_person(THIRD_PERSON_TILT, THIRD_PERSON_SCALE)
	return {
		"gui_light": "front",
		"textures": {"particle": f"{NS}:item/lightsaber_hilt"},
		"display": {
			"thirdperson_righthand": dict(third),
			"thirdperson_lefthand": dict(third),
			"firstperson_righthand": dict(FIRST_PERSON),
			"firstperson_lefthand": dict(FIRST_PERSON),
			"ground": {"rotation": [0, 0, 90], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
			"head": {"rotation": [0, 0, 0], "translation": [0, 10, 0], "scale": [0.8, 0.8, 0.8]},
		},
	}


def guard_base_model():
	"""The hold while guarding: the same as the idle hold. In third person the vanilla guard pose raises the arm across the
	body; in first person the code brings the blade across (SaberFirstPerson)."""
	return saber_base_model()


# Model folders: idle hold and guard hold (same geometry, different display parent).
VARIANTS = {"lightsaber": "base", "lightsaber/guard": "guard_base"}


def special_blade(base, kind):
	"""The blade, drawn in code (SaberBladeRenderer) with the display transforms of the hilt's parent `base`: told when it
	is in the first-person hand (its motion trail is then kept relative to the view). Its length, extension and colour come
	from the stack, so one entry serves every stage, length and colour."""
	def special(first_person):
		model = {"type": f"{NS}:saber_blade", "kind": kind}
		if first_person:
			model["first_person"] = True
		return {"type": "minecraft:special", "base": f"{NS}:item/lightsaber/{base}", "model": model}
	return {
		"type": "minecraft:select",
		"property": "minecraft:display_context",
		"cases": [{"when": ["firstperson_righthand", "firstperson_lefthand"], "model": special(True)}],
		"fallback": special(False),
	}


def write_models():
	write_json("models/item/lightsaber/base.json", saber_base_model())
	write_json("models/item/lightsaber/guard_base.json", guard_base_model())
	# The blade itself is drawn in code (SaberBladeRenderer, the special model oldrepublic:saber_blade below), so only the
	# hilt parts are baked models.
	for folder, parent in VARIANTS.items():
		for name, elements in hilt_models().items():
			write_json(f"models/item/{folder}/{name}.json", dict(hilt_textures(name, parent), elements=elements))

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

	def part_select(folder, slot, variants):
		return {
			"type": "minecraft:select",
			"property": f"{NS}:hilt_part",
			"part": slot,
			"cases": [{"when": v, "model": {"type": "minecraft:model", "model": f"{NS}:item/{folder}/{slot}_{v}"}} for v in variants],
			"fallback": {"type": "minecraft:model", "model": f"{NS}:item/{folder}/{slot}_{variants[0]}"},
		}

	def saber_3d(folder):
		return {"type": "minecraft:composite", "models": [
			part_select(folder, "pommel", POMMELS),
			part_select(folder, "grip", GRIPS),
			part_select(folder, "emitter", EMITTERS),
			special_blade(VARIANTS[folder], "single"),
		]}

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
		# In the hand, nothing while the saber is in flight (Saber Throw draws the flying blade).
		"fallback": {
			"type": "minecraft:condition",
			"property": "minecraft:has_component",
			"component": f"{NS}:saber_thrown",
			"on_true": {"type": "minecraft:empty"},
			"on_false": {
				"type": "minecraft:condition",
				"property": "minecraft:using_item",
				"on_true": saber_3d("lightsaber/guard"),
				"on_false": saber_3d("lightsaber"),
			},
		},
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


# ---------------------------------------------------------------- Milestone 4 (W5): saberstaff, training saber, lenses

# The staff (spec MILESTONE_4.md 5.6) is authored on its own y axis: lower emitter 0-3 (blade end at
# 0), the long grip 3-12, upper emitter 12-15. staff_y() maps it at the saber's hilt scale with the
# grip's centre on the model centre (y 8), so the hilt spans y 2.29..13.71 (SaberBladeRenderer draws
# a blade out of each end from there).
STAFF_OFFSET = 8.0 - 7.5 * HILT_SCALE


def staff_y(y):
	return round(y * HILT_SCALE + STAFF_OFFSET, 3)


STAFF_TOP = staff_y(15)
STAFF_BOTTOM = staff_y(0)


def grip_staff_elements():
	"""A long ridged grip with a wrap at each hand position and a brass ring at its centre."""
	return [
		box([7.1, 3, 7.1], [8.9, 12, 8.9], "ridge", ymap=staff_y),
		box([6.9, 3.6, 6.9], [9.1, 5.6, 9.1], "wrap", ymap=staff_y),
		box([6.9, 9.4, 6.9], [9.1, 11.4, 9.1], "wrap", ymap=staff_y),
		box([6.85, 7.2, 6.85], [9.15, 7.8, 9.15], "brass", ymap=staff_y),
		box([6.95, 3, 6.95], [9.05, 3.3, 9.05], "steel", ymap=staff_y),
		box([6.95, 11.7, 6.95], [9.05, 12, 9.05], "steel", ymap=staff_y),
	]


def staff_emitter(raw, upper):
	"""A saber emitter (authored y 7.5-10.5, blade end up) moved to the staff's upper end, or
	flipped onto its lower end with the blade end down."""
	out = []
	for frm, to, material in raw:
		if upper:
			f, t = [frm[0], frm[1] + 4.5, frm[2]], [to[0], to[1] + 4.5, to[2]]
		else:
			f, t = [frm[0], 10.5 - to[1], frm[2]], [to[0], 10.5 - frm[1], to[2]]
		out.append(box(f, t, material, ymap=staff_y))
	return out


def _rot(deg):
	"""ItemTransform's rotation (Euler X, then Y, then Z, as Quaternionf.rotationXYZ) as a 3x3 matrix."""
	x, y, z = (math.radians(d) for d in deg)
	rx = [[1, 0, 0], [0, math.cos(x), -math.sin(x)], [0, math.sin(x), math.cos(x)]]
	ry = [[math.cos(y), 0, math.sin(y)], [0, 1, 0], [-math.sin(y), 0, math.cos(y)]]
	rz = [[math.cos(z), -math.sin(z), 0], [math.sin(z), math.cos(z), 0], [0, 0, 1]]

	def mul(a, b):
		return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]

	return mul(mul(rx, ry), rz)


def _apply(m, v):
	return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


def fist(transform, grip_offset):
	"""Where the saber's grip ends up for a display transform: translation + R * scale * grip."""
	r = _rot(transform["rotation"])
	g = [c * transform["scale"][0] for c in grip_offset]
	rg = _apply(r, g)
	return [transform["translation"][i] + rg[i] for i in range(3)]


def staff_display(base, scales):
	"""The saber's display, with each third-person hand transform re-aimed so the staff's grip (on the model centre) sits
	where the saber's grip sits, at the staff's own (smaller) scale. First person stays the identity (posed in code)."""
	saber_grip = [0.0, -GRIP_BELOW_CENTRE, 0.0]  # the saber grip's centre relative to the pivot
	display = json.loads(json.dumps(base["display"]))
	for key, scale in scales.items():
		t = display[key]
		target = fist(t, saber_grip)
		display[key] = {"rotation": t["rotation"], "translation": [round(c, 2) for c in target], "scale": [scale, scale, scale]}
	return display


# 36 units x 0.62 is about 1.4 blocks, hilt and both blades.
STAFF_SCALES = {"thirdperson_righthand": 0.62, "thirdperson_lefthand": 0.62}


def write_m4_models():
	base = saber_base_model()
	guard = guard_base_model()
	staff_base = dict(base, display=staff_display(base, STAFF_SCALES))
	staff_base["display"]["ground"] = {"rotation": [0, 0, 90], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]}
	staff_guard = dict(guard, display=staff_display(guard, STAFF_SCALES))
	write_json("models/item/lightsaber/staff_base.json", staff_base)
	write_json("models/item/lightsaber/staff_guard_base.json", staff_guard)

	raw = HILT_RAW
	for folder, parent in (("lightsaber/staff", "staff_base"), ("lightsaber/staff_guard", "staff_guard_base")):
		hilt = {"parent": f"{NS}:item/lightsaber/{parent}", "textures": {"hilt": f"{NS}:item/lightsaber_hilt", "particle": f"{NS}:item/lightsaber_hilt"}}
		write_json(f"models/item/{folder}/grip_staff.json", dict(hilt, elements=grip_staff_elements()))
		for name in (f"emitter_{e}" for e in EMITTERS):
			textures = hilt_textures(name, parent)
			write_json(f"models/item/{folder}/{name}_upper.json", dict(textures, elements=staff_emitter(raw[name], True)))
			write_json(f"models/item/{folder}/{name}_lower.json", dict(textures, elements=staff_emitter(raw[name], False)))

	write_json("models/item/lightsaber_staff_icon.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/lightsaber_staff_icon_hilt"}})
	write_json("models/item/lightsaber_staff_icon_lit.json", {"parent": "minecraft:item/generated", "textures": {
		"layer0": f"{NS}:item/lightsaber_staff_icon_hilt", "layer1": f"{NS}:item/lightsaber_staff_icon_blade"}})
	write_json("models/item/training_saber_icon.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/training_saber_icon_hilt"}})
	write_json("models/item/training_saber_icon_lit.json", {"parent": "minecraft:item/generated", "textures": {
		"layer0": f"{NS}:item/training_saber_icon_hilt", "layer1": f"{NS}:item/lightsaber_icon_blade"}})
	for lens in LENSES:
		write_json(f"models/item/lens_{lens}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/lens_{lens}"}})
		write_json(f"items/lens_{lens}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/lens_{lens}"}})

	icon_tints = [{"type": "minecraft:constant", "value": -1}, {"type": f"{NS}:blade_color"}]

	def emitter_select(folder, end):
		variants = EMITTERS
		return {
			"type": "minecraft:select",
			"property": f"{NS}:hilt_part",
			"part": "emitter",
			"cases": [{"when": v, "model": {"type": "minecraft:model", "model": f"{NS}:item/{folder}/emitter_{v}_{end}"}} for v in variants],
			"fallback": {"type": "minecraft:model", "model": f"{NS}:item/{folder}/emitter_guardian_{end}"},
		}

	def staff_3d(folder, parent):
		# No pommel: the config keeps a placeholder pommel the staff never renders (5.6). Both blades are one special model.
		return {"type": "minecraft:composite", "models": [
			{"type": "minecraft:model", "model": f"{NS}:item/{folder}/grip_staff"},
			emitter_select(folder, "upper"),
			emitter_select(folder, "lower"),
			special_blade(parent, "staff"),
		]}

	def gui_icon(icon):
		return {
			"type": "minecraft:range_dispatch",
			"property": f"{NS}:blade_extension",
			"entries": [{"threshold": 0.5, "model": {"type": "minecraft:model", "model": f"{NS}:item/{icon}_lit", "tints": icon_tints}}],
			"fallback": {"type": "minecraft:model", "model": f"{NS}:item/{icon}"},
		}

	def in_hand(idle, guard):
		return {
			"type": "minecraft:condition",
			"property": "minecraft:has_component",
			"component": f"{NS}:saber_thrown",
			"on_true": {"type": "minecraft:empty"},
			"on_false": {"type": "minecraft:condition", "property": "minecraft:using_item", "on_true": guard, "on_false": idle},
		}

	write_json("items/lightsaber_staff.json", {"model": {
		"type": "minecraft:select",
		"property": "minecraft:display_context",
		"cases": [{"when": ["gui", "fixed", "on_shelf"], "model": gui_icon("lightsaber_staff_icon")}],
		"fallback": in_hand(staff_3d("lightsaber/staff", "staff_base"), staff_3d("lightsaber/staff_guard", "staff_guard_base")),
	}})

	def part_select(folder, slot, variants):
		return {
			"type": "minecraft:select",
			"property": f"{NS}:hilt_part",
			"part": slot,
			"cases": [{"when": v, "model": {"type": "minecraft:model", "model": f"{NS}:item/{folder}/{slot}_{v}"}} for v in variants],
			"fallback": {"type": "minecraft:model", "model": f"{NS}:item/{folder}/{slot}_{variants[0]}"},
		}

	def training_3d(hilt_folder):
		return {"type": "minecraft:composite", "models": [
			part_select(hilt_folder, "pommel", POMMELS),
			part_select(hilt_folder, "grip", GRIPS),
			part_select(hilt_folder, "emitter", EMITTERS),
			special_blade(VARIANTS[hilt_folder], "training"),
		]}

	training = {"model": {
		"type": "minecraft:select",
		"property": "minecraft:display_context",
		"cases": [{"when": ["gui", "fixed", "on_shelf"], "model": gui_icon("training_saber_icon")}],
		"fallback": in_hand(training_3d("lightsaber"), training_3d("lightsaber/guard")),
	}}
	write_json("items/training_saber.json", training)
	write_json("items/training_saber_red.json", training)


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
	path = os.path.join(ASSETS, "textures", "gui", "sprites", "bench", "hint_lens.png")
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


PARTS = ["emitter_guardian", "emitter_duelist", "grip_wrapped", "grip_ridged", "pommel_weighted", "pommel_balanced",
		"emitter_reliquary", "pommel_archivist", "grip_staff", "emitter_cantor", "grip_sunforged", "pommel_hask"]

# Every hilt variant the in-hand models select, in HiltParts order (the first is each select's fallback).
EMITTERS = ["guardian", "duelist", "reliquary", "cantor"]
GRIPS = ["wrapped", "ridged", "sunforged"]
POMMELS = ["weighted", "balanced", "archivist", "hask"]


def main():
	write_png("item/lightsaber_hilt.png", hilt_swatches())
	write_png("item/lightsaber_hilt_legendary.png", legend_swatches())
	write_png("item/lightsaber_icon_hilt.png", icon_hilt())
	write_png("item/lightsaber_icon_blade.png", icon_blade())
	write_png("item/kyber_crystal.png", crystal_body())
	write_png("item/kyber_crystal_glint.png", crystal_glint())
	write_png("item/singing_crystal.png", singing_crystal())
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
